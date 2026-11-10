package com.example.demo.service;

import com.example.demo.dto.request.CustomerDto;
import com.example.demo.dto.request.GaConfig;
import com.example.demo.dto.request.OptimizationRequest;
import com.example.demo.dto.response.OptimizationResponse;
import com.example.demo.dto.response.RouteDetail;
import com.example.demo.dto.response.SolutionSummary;
import com.example.demo.model.OrToolsSolution;
import com.example.demo.model.ProblemContext;
import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.*;
import com.google.protobuf.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * OrToolsSolverService – Giai đoạn 8 (OR-Tools Baseline Integration)
 *
 * <p>Giải bài toán Capacitated Vehicle Routing Problem (CVRP) bằng Google OR-Tools
 * Constraint Programming (CP-SAT Routing Engine) làm chuẩn đối sánh (Baseline)
 * với Thuật toán Di truyền (GA).</p>
 *
 * <h2>Cơ chế hoạt động</h2>
 * <ul>
 *   <li><b>Loader:</b> Tải thư viện C++ native qua {@link Loader#loadNativeLibraries()}.</li>
 *   <li><b>RoutingIndexManager:</b> Quản lý chỉ số nút (0 = Depot, 1..N = Khách hàng) và số xe K.</li>
 *   <li><b>Transit Callback:</b> Ma trận khoảng cách từ {@link ProblemContext#costMatrix}.</li>
 *   <li><b>Capacity Dimension:</b> Ràng buộc sức chứa xe từ {@link ProblemContext#demands}.</li>
 *   <li><b>Metaheuristics:</b> Khởi tạo bằng {@code PATH_CHEAPEST_ARC}, cải thiện cục bộ
 *       bằng {@code GUIDED_LOCAL_SEARCH} với ngân sách thời gian giới hạn.</li>
 * </ul>
 */
@Service
public class OrToolsSolverService {

    private static final Logger log = LoggerFactory.getLogger(OrToolsSolverService.class);

    static {
        try {
            Loader.loadNativeLibraries();
            log.info("Google OR-Tools native libraries loaded successfully.");
        } catch (Throwable t) {
            log.error("Failed to load Google OR-Tools native libraries", t);
        }
    }

    private final DistanceMatrixService distanceMatrixService;

    public OrToolsSolverService(DistanceMatrixService distanceMatrixService) {
        this.distanceMatrixService = distanceMatrixService;
    }

    // =========================================================================
    // Public API: Solve with ProblemContext
    // =========================================================================

    /**
     * Giải bài toán CVRP định nghĩa trong {@link ProblemContext} với ngân sách thời gian.
     *
     * @param ctx         ngữ cảnh bài toán đã pre-compute (costMatrix, demands, capacity, ...)
     * @param timeLimitMs thời gian chạy tối đa (mili-giây)
     * @return {@link OrToolsSolution} chứa quãng đường, số xe và chi tiết lộ trình
     */
    public OrToolsSolution solve(ProblemContext ctx, long timeLimitMs) {
        long startTime = System.currentTimeMillis();
        int numNodes = ctx.numCustomers + 1; // 0 là depot, 1..N là customers
        int numVehicles = ctx.maxVehicles;
        int depotIndex = 0;

        if (numNodes <= 1 || numVehicles < 1) {
            return OrToolsSolution.empty("INFEASIBLE", 0);
        }

        try {
            // ── 1. Chuẩn hóa tỷ lệ cho chi phí và tải trọng (scale factor) ─────
            // OR-Tools yêu cầu giá trị long nguyên cho khoảng cách và tải trọng
            long costScale = determineCostScale(ctx.costMatrix, ctx.numCustomers);
            long demandScale = determineDemandScale(ctx.demands, ctx.numCustomers);

            // ── 2. Khởi tạo RoutingIndexManager & RoutingModel ─────────────────
            RoutingIndexManager manager = new RoutingIndexManager(numNodes, numVehicles, depotIndex);
            RoutingModel routing = new RoutingModel(manager);

            // ── 3. Đăng ký Transit Callback (Khoảng cách) ─────────────────────
            int transitCallbackIndex = routing.registerTransitCallback((fromIndex, toIndex) -> {
                int fromNode = manager.indexToNode(fromIndex);
                int toNode = manager.indexToNode(toIndex);
                if (fromNode < 0 || fromNode >= numNodes || toNode < 0 || toNode >= numNodes) {
                    return 0L;
                }
                return (long) Math.round(ctx.costMatrix[fromNode][toNode] * costScale);
            });
            routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);

            // ── 4. Đăng ký Capacity Dimension (Sức chứa phương tiện) ──────────
            int demandCallbackIndex = routing.registerUnaryTransitCallback(fromIndex -> {
                int node = manager.indexToNode(fromIndex);
                if (node <= 0 || node >= numNodes) {
                    return 0L;
                }
                return (long) Math.round(ctx.demands[node] * demandScale);
            });

            long[] vehicleCapacities = new long[numVehicles];
            long vehicleCapacityScaled = (long) Math.round(ctx.vehicleCapacity * demandScale);
            Arrays.fill(vehicleCapacities, vehicleCapacityScaled);

            routing.addDimensionWithVehicleCapacity(
                    demandCallbackIndex,
                    0L,                     // slack_max = 0 (không cho vượt tải)
                    vehicleCapacities,       // capacity mỗi xe
                    true,                   // start_cumul_to_zero = true
                    "Capacity"
            );

            // ── 5. Cấu hình Search Parameters ─────────────────────────────────
            long timeBudget = Math.max(100L, timeLimitMs);
            long seconds = timeBudget / 1000L;
            int nanos = (int) ((timeBudget % 1000L) * 1_000_000L);

            RoutingSearchParameters searchParameters = main.defaultRoutingSearchParameters().toBuilder()
                    .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
                    .setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
                    .setTimeLimit(Duration.newBuilder().setSeconds(seconds).setNanos(nanos).build())
                    .build();

            // ── 6. Thực thi tìm kiếm lời giải ─────────────────────────────────
            Assignment solution = routing.solveWithParameters(searchParameters);
            long elapsedMs = System.currentTimeMillis() - startTime;

            if (solution == null) {
                log.warn("OR-Tools không tìm được lời giải khả thi trong {}ms", elapsedMs);
                return OrToolsSolution.empty("INFEASIBLE", elapsedMs);
            }

            // ── 7. Trích xuất lộ trình chi tiết ──────────────────────────────
            List<RouteDetail> routes = new ArrayList<>();
            double totalDistance = 0.0;
            int vehicleCounter = 1;

            for (int v = 0; v < numVehicles; v++) {
                long index = routing.start(v);
                List<Integer> routeSequence = new ArrayList<>();
                double routeLoad = 0.0;
                double routeDist = 0.0;
                int prevNode = manager.indexToNode(index);

                while (!routing.isEnd(index)) {
                    index = solution.value(routing.nextVar(index));
                    int node = manager.indexToNode(index);
                    if (node != 0) {
                        routeSequence.add(node);
                        routeLoad += ctx.demands[node];
                    }
                    routeDist += ctx.costMatrix[prevNode][node];
                    prevNode = node;
                }

                if (!routeSequence.isEmpty()) {
                    routes.add(RouteDetail.builder()
                            .vehicleId(vehicleCounter++)
                            .routeSequence(routeSequence)
                            .totalLoad(routeLoad)
                            .distanceKm(routeDist)
                            .build());
                    totalDistance += routeDist;
                }
            }

            int vehiclesUsed = routes.size();
            String status = "SUCCESS";

            return new OrToolsSolution(totalDistance, vehiclesUsed, elapsedMs, status, routes);

        } catch (Exception e) {
            long elapsedMs = System.currentTimeMillis() - startTime;
            log.error("Lỗi khi chạy Google OR-Tools solver", e);
            return OrToolsSolution.empty("ERROR", elapsedMs);
        }
    }

    // =========================================================================
    // Public API: Solve with OptimizationRequest
    // =========================================================================

    /**
     * Giải bài toán CVRP từ {@link OptimizationRequest} và trả về {@link OptimizationResponse}
     * chuẩn theo định dạng REST API của hệ thống.
     *
     * @param request JSON request chứa danh sách khách hàng và tham số
     * @return {@link OptimizationResponse} kết quả giải bằng OR-Tools
     */
    public OptimizationResponse solve(OptimizationRequest request) {
        final String batchId = request.getBatchId();
        final GaConfig config = request.getConfig();
        long startTimeMs = System.currentTimeMillis();

        String feasibilityError = checkFeasibility(request);
        if (feasibilityError != null) {
            log.warn("[OR-Tools][{}] INFEASIBLE: {}", batchId, feasibilityError);
            return OptimizationResponse.builder()
                    .batchId(batchId)
                    .status("INFEASIBLE")
                    .executionTimeMs(System.currentTimeMillis() - startTimeMs)
                    .routes(List.of())
                    .build();
        }

        try {
            ProblemContext ctx = distanceMatrixService.build(request);
            OrToolsSolution sol = solve(ctx, config.getTimeBudgetMs());

            SolutionSummary summary = SolutionSummary.builder()
                    .totalDistanceKm(sol.totalDistance())
                    .vehiclesUsed(sol.vehiclesUsed())
                    .gapToBksPercent(-1.0)
                    .build();

            return OptimizationResponse.builder()
                    .batchId(batchId)
                    .status(sol.status())
                    .executionTimeMs(sol.executionTimeMs())
                    .summary(summary)
                    .routes(sol.routes())
                    .build();

        } catch (Exception e) {
            log.error("[OR-Tools][{}] Lỗi giải bài toán", batchId, e);
            return OptimizationResponse.builder()
                    .batchId(batchId)
                    .status("FAILED")
                    .executionTimeMs(System.currentTimeMillis() - startTimeMs)
                    .routes(List.of())
                    .build();
        }
    }

    // =========================================================================
    // Helper scale determinations
    // =========================================================================

    private long determineCostScale(double[][] costMatrix, int n) {
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n; j++) {
                if (costMatrix[i][j] != Math.floor(costMatrix[i][j])) {
                    return 1000L; // có số thập phân -> scale sang mét
                }
            }
        }
        return 1L; // nguyên bản số nguyên (như CVRPLib EUC_2D nint)
    }

    private long determineDemandScale(double[] demands, int n) {
        for (int i = 0; i <= n; i++) {
            if (demands[i] != Math.floor(demands[i])) {
                return 1000L;
            }
        }
        return 1L;
    }

    private String checkFeasibility(OptimizationRequest request) {
        GaConfig config = request.getConfig();
        List<CustomerDto> customers = request.getCustomers();

        if (customers == null || customers.isEmpty()) {
            return "Danh sách khách hàng trống";
        }
        if (config.getMaxVehicles() < 1) {
            return "Số xe tối đa phải >= 1";
        }

        double totalDemand = customers.stream().mapToDouble(CustomerDto::getDemand).sum();
        double totalCapacity = (double) config.getMaxVehicles() * config.getVehicleCapacity();
        if (totalDemand > totalCapacity) {
            return String.format("Tổng nhu cầu (%.1f) > Tổng tải trọng (K=%d * C=%.1f = %.1f)",
                    totalDemand, config.getMaxVehicles(), config.getVehicleCapacity(), totalCapacity);
        }

        return null;
    }
}
