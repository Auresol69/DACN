package com.example.demo.service;

import com.example.demo.benchmark.VrpFileParser;
import com.example.demo.benchmark.VrpInstance;
import com.example.demo.dto.request.CustomerDto;
import com.example.demo.dto.request.GaConfig;
import com.example.demo.dto.request.LocationDto;
import com.example.demo.dto.request.OptimizationRequest;
import com.example.demo.dto.response.OptimizationResponse;
import com.example.demo.dto.response.RouteDetail;
import com.example.demo.model.OrToolsSolution;
import com.example.demo.model.ProblemContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OrToolsSolverService – Baseline Solver Tests")
class OrToolsSolverServiceTest {

    private OrToolsSolverService solver;
    private DistanceMatrixService distanceMatrixService;

    @BeforeEach
    void setUp() {
        distanceMatrixService = new DistanceMatrixService();
        solver = new OrToolsSolverService(distanceMatrixService);
    }

    @Test
    @DisplayName("Solve bài toán nhỏ 4 khách hàng, 2 xe: nghiệm hợp lệ và không vượt tải")
    void testSolve_SmallInstance_Feasible() {
        // 4 khách hàng, 2 xe, sức chứa mỗi xe = 25
        int numCustomers = 4;
        int maxVehicles = 2;
        double capacity = 25.0;

        // Ma trận chi phí đối xứng
        double[][] cost = {
                {0, 10, 15, 20, 25},
                {10, 0, 12, 14, 18},
                {15, 12, 0, 10, 16},
                {20, 14, 10, 0, 12},
                {25, 18, 16, 12, 0}
        };

        // Demands: node 0 = 0, khách 1..4 = 10, 12, 8, 11 (tổng = 41 <= 2*25 = 50)
        double[] demands = {0, 10, 12, 8, 11};

        ProblemContext ctx = new ProblemContext(numCustomers, maxVehicles, capacity, cost, demands);

        OrToolsSolution solution = solver.solve(ctx, 1000);

        assertNotNull(solution);
        assertEquals("SUCCESS", solution.status());
        assertTrue(solution.totalDistance() > 0);
        assertTrue(solution.vehiclesUsed() <= maxVehicles);
        assertFalse(solution.routes().isEmpty());

        // Kiểm tra tất cả 4 khách hàng đều được phục vụ đúng 1 lần
        Set<Integer> visited = new HashSet<>();
        for (RouteDetail route : solution.routes()) {
            assertTrue(route.getTotalLoad() <= capacity, "Tải trọng tuyến xe không được vượt quá sức chứa");
            for (int cust : route.getRouteSequence()) {
                assertTrue(cust >= 1 && cust <= numCustomers);
                assertTrue(visited.add(cust), "Khách hàng " + cust + " không được phục vụ trùng lặp");
            }
        }
        assertEquals(numCustomers, visited.size(), "Toàn bộ khách hàng phải được ghé thăm");
    }

    @Test
    @DisplayName("Nhu cầu của một khách hàng vượt quá sức chứa xe: trả về INFEASIBLE")
    void testSolve_DemandExceedsCapacity_Infeasible() {
        int numCustomers = 2;
        int maxVehicles = 1;
        double capacity = 20.0;

        double[][] cost = {
                {0, 5, 5},
                {5, 0, 8},
                {5, 8, 0}
        };
        double[] demands = {0, 25, 5}; // Khách 1 demand=25 > capacity=20

        ProblemContext ctx = new ProblemContext(numCustomers, maxVehicles, capacity, cost, demands);

        OrToolsSolution solution = solver.solve(ctx, 500);

        assertNotNull(solution);
        assertEquals("INFEASIBLE", solution.status());
    }

    @Test
    @DisplayName("Solve thông qua OptimizationRequest DTO với tọa độ GPS")
    void testSolve_OptimizationRequestDto() {
        OptimizationRequest req = OptimizationRequest.builder()
                .batchId("TEST_ORT_01")
                .config(GaConfig.builder()
                        .vehicleCapacity(100.0)
                        .maxVehicles(3)
                        .timeBudgetMs(1000L)
                        .build())
                .depot(LocationDto.builder()
                        .id(0)
                        .latitude(10.762622)
                        .longitude(106.660172)
                        .build())
                .customers(List.of(
                        CustomerDto.builder().id(1).demand(15.0).lat(10.77123).lng(106.6721).build(),
                        CustomerDto.builder().id(2).demand(25.0).lat(10.75412).lng(106.6412).build(),
                        CustomerDto.builder().id(3).demand(20.0).lat(10.78119).lng(106.6915).build()
                ))
                .build();

        OptimizationResponse resp = solver.solve(req);

        assertNotNull(resp);
        assertEquals("TEST_ORT_01", resp.getBatchId());
        assertEquals("SUCCESS", resp.getStatus());
        assertNotNull(resp.getSummary());
        assertTrue(resp.getSummary().getTotalDistanceKm() > 0.0);
        assertFalse(resp.getRoutes().isEmpty());
    }

    @Test
    @DisplayName("Giải instance Augerat A-n32-k5: tiệm cận hoặc bằng BKS=784")
    void testSolve_Augerat_An32k5() throws IOException {
        Path vrpPath = Paths.get("E:/dacn/demo/A/A-n32-k5.vrp");
        VrpFileParser parser = new VrpFileParser();
        VrpInstance inst = parser.parse(vrpPath);

        // Build Euclidean ProblemContext
        int n = inst.numCustomers();
        double[][] cost = new double[n + 1][n + 1];
        for (int i = 0; i <= n; i++) {
            for (int j = i + 1; j <= n; j++) {
                double dx = inst.xs()[i] - inst.xs()[j];
                double dy = inst.ys()[i] - inst.ys()[j];
                double dist = Math.round(Math.sqrt(dx * dx + dy * dy));
                cost[i][j] = dist;
                cost[j][i] = dist;
            }
        }
        ProblemContext ctx = new ProblemContext(n, inst.maxVehicles(), inst.capacity(), cost, inst.demands());

        OrToolsSolution solution = solver.solve(ctx, 3000);

        assertNotNull(solution);
        assertEquals("SUCCESS", solution.status());
        assertTrue(solution.totalDistance() > 0);

        int found = (int) Math.round(solution.totalDistance());
        int bks = inst.bks(); // 784
        double gapPercent = (double) (found - bks) / bks * 100.0;

        System.out.printf("[OR-Tools A-n32-k5] Found: %d | BKS: %d | Gap: %+.2f%% | Time: %dms | Vehicles: %d%n",
                found, bks, gapPercent, solution.executionTimeMs(), solution.vehiclesUsed());

        // OR-Tools với Guided Local Search thường đạt Gap < 2% trên A-n32-k5 trong 3 giây
        assertTrue(gapPercent <= 3.0, "OR-Tools gap trên A-n32-k5 phải <= 3%, thực tế: " + gapPercent + "%");
    }
}
