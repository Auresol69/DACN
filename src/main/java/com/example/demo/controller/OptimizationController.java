package com.example.demo.controller;

import com.example.demo.dto.request.GaConfig;
import com.example.demo.dto.request.OptimizationRequest;
import com.example.demo.dto.response.*;
import com.example.demo.model.Individual;
import com.example.demo.model.OrToolsSolution;
import com.example.demo.model.ProblemContext;
import com.example.demo.service.DistanceMatrixService;
import com.example.demo.service.GaOptimizationEngine;
import com.example.demo.service.OrToolsSolverService;
import com.example.demo.service.RouteBuilderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller – Giai đoạn 5
 *
 * Endpoint: POST /api/v1/optimize/cvrp
 *
 * Luồng xử lý bất đồng bộ:
 *   1. Validate request (@Valid)
 *   2. Kiểm tra tính khả thi sơ bộ (feasibility pre-check)
 *   3. Build ProblemContext (Haversine costMatrix) – O(N²)
 *   4. Chạy GA (GaOptimizationEngine.runOptimization) trên luồng riêng
 *   5. Map Individual → OptimizationResponse
 *   6. Trả về JSON cho client
 *
 * @Async + CompletableFuture: không block Tomcat I/O thread,
 * GA chạy trên Spring's async task executor (cấu hình trong application.properties).
 *
 * Nguồn: Mục 2.4.3 – "dacn remake.pdf"
 */
@RestController
@RequestMapping("/api/v1/optimize")
public class OptimizationController {

    private static final Logger log = LoggerFactory.getLogger(OptimizationController.class);

    private final DistanceMatrixService distanceMatrixService;
    private final GaOptimizationEngine  gaEngine;
    private final RouteBuilderService   routeBuilder;
    private final OrToolsSolverService  orToolsSolver;

    public OptimizationController(DistanceMatrixService distanceMatrixService,
                                   GaOptimizationEngine gaEngine,
                                   RouteBuilderService routeBuilder,
                                   OrToolsSolverService orToolsSolver) {
        this.distanceMatrixService = distanceMatrixService;
        this.gaEngine              = gaEngine;
        this.routeBuilder          = routeBuilder;
        this.orToolsSolver         = orToolsSolver;
    }

    // =========================================================================
    // POST /api/v1/optimize/cvrp
    // =========================================================================

    /**
     * Nhận yêu cầu tối ưu hóa CVRP và trả về lời giải tốt nhất tìm được.
     *
     * <p>Phương thức này chạy ASYNC – Tomcat thread trả về ngay sau khi
     * submit task, GA chạy trên Spring async thread pool.</p>
     *
     * @param request JSON body đã validate (batchId, config, depot, customers)
     * @return {@link CompletableFuture} chứa {@link OptimizationResponse}
     *         (Spring MVC tự unwrap và serialize sang JSON khi future hoàn tất)
     */
    @PostMapping("/cvrp")
    @Async("gaTaskExecutor")
    public CompletableFuture<ResponseEntity<OptimizationResponse>> optimizeCvrp(
            @Valid @RequestBody OptimizationRequest request) {

        final String batchId   = request.getBatchId();
        final GaConfig config  = request.getConfig();
        final long startTimeMs = System.currentTimeMillis();

        log.info("[{}] Nhận request: N={} customers, K={} vehicles, timeBudget={}ms",
                batchId,
                request.getCustomers().size(),
                config.getMaxVehicles(),
                config.getTimeBudgetMs());

        // ── 1. Kiểm tra tính khả thi sơ bộ ──────────────────────────────────
        String feasibilityError = checkFeasibility(request);
        if (feasibilityError != null) {
            log.warn("[{}] INFEASIBLE: {}", batchId, feasibilityError);
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(
                            OptimizationResponse.builder()
                                    .batchId(batchId)
                                    .status("INFEASIBLE")
                                    .executionTimeMs(System.currentTimeMillis() - startTimeMs)
                                    .build()
                    )
            );
        }

        try {
            // ── 2. Build ProblemContext (tính ma trận khoảng cách O(N²)) ─────
            ProblemContext ctx = distanceMatrixService.build(request);
            log.info("[{}] ProblemContext built: {}", batchId, ctx);

            // ── 3. Chạy GA ───────────────────────────────────────────────────
            Individual best = gaEngine.runOptimization(ctx, config);
            long executionMs = System.currentTimeMillis() - startTimeMs;

            log.info("[{}] GA hoàn tất: dist={:.4f}, penalty={:.4f}, time={}ms",
                    batchId, best.getTotalDistance(), best.getCapacityPenalty(), executionMs);

            // ── 4. Map kết quả → Response ─────────────────────────────────────
            OptimizationResponse response = buildResponse(batchId, best, ctx, executionMs);
            return CompletableFuture.completedFuture(ResponseEntity.ok(response));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[{}] GA bị gián đoạn", batchId, e);
            return CompletableFuture.completedFuture(
                    ResponseEntity.internalServerError().body(
                            OptimizationResponse.builder()
                                    .batchId(batchId)
                                    .status("FAILED")
                                    .executionTimeMs(System.currentTimeMillis() - startTimeMs)
                                    .build()
                    )
            );
        } catch (Exception e) {
            log.error("[{}] Lỗi không mong đợi", batchId, e);
            return CompletableFuture.completedFuture(
                    ResponseEntity.internalServerError().body(
                            OptimizationResponse.builder()
                                    .batchId(batchId)
                                    .status("FAILED")
                                    .executionTimeMs(System.currentTimeMillis() - startTimeMs)
                                    .build()
                    )
            );
        }
    }

    // =========================================================================
    // POST /api/v1/optimize/ortools
    // =========================================================================

    /**
     * Tối ưu hóa CVRP bằng Google OR-Tools CP-SAT Routing Engine (Baseline).
     */
    @PostMapping("/ortools")
    @Async("gaTaskExecutor")
    public CompletableFuture<ResponseEntity<OptimizationResponse>> optimizeOrTools(
            @Valid @RequestBody OptimizationRequest request) {

        final String batchId = request.getBatchId();
        log.info("[OR-Tools][{}] Nhận request: N={} customers, K={} vehicles",
                batchId, request.getCustomers().size(), request.getConfig().getMaxVehicles());

        OptimizationResponse response = orToolsSolver.solve(request);
        return CompletableFuture.completedFuture(ResponseEntity.ok(response));
    }

    // =========================================================================
    // POST /api/v1/optimize/compare
    // =========================================================================

    /**
     * Giải song song bằng cả Thuật toán Di truyền (GA) và Google OR-Tools,
     * trả về phân tích đối sánh chi tiết về khoảng cách, thời gian và số xe.
     */
    @PostMapping("/compare")
    @Async("gaTaskExecutor")
    public CompletableFuture<ResponseEntity<ComparisonResponse>> compareAlgorithms(
            @Valid @RequestBody OptimizationRequest request) {

        final String batchId   = request.getBatchId();
        final GaConfig config  = request.getConfig();
        final long startTimeMs = System.currentTimeMillis();

        log.info("[COMPARE][{}] Bắt đầu đối sánh GA vs OR-Tools (timeBudget={}ms)",
                batchId, config.getTimeBudgetMs());

        String feasibilityError = checkFeasibility(request);
        if (feasibilityError != null) {
            log.warn("[COMPARE][{}] INFEASIBLE: {}", batchId, feasibilityError);
            OptimizationResponse infeasibleResp = OptimizationResponse.builder()
                    .batchId(batchId)
                    .status("INFEASIBLE")
                    .executionTimeMs(System.currentTimeMillis() - startTimeMs)
                    .build();

            ComparisonResponse errorResponse = ComparisonResponse.builder()
                    .batchId(batchId)
                    .gaSolution(infeasibleResp)
                    .orToolsSolution(infeasibleResp)
                    .metrics(ComparisonMetrics.builder()
                            .winner("NONE")
                            .recommendation("Bài toán không khả thi: " + feasibilityError)
                            .build())
                    .build();
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body(errorResponse));
        }

        try {
            // Build chung ProblemContext
            ProblemContext ctx = distanceMatrixService.build(request);

            // 1. Chạy GA
            long gaStart = System.currentTimeMillis();
            Individual gaBest = gaEngine.runOptimization(ctx, config);
            long gaTime = System.currentTimeMillis() - gaStart;
            OptimizationResponse gaResponse = buildResponse(batchId, gaBest, ctx, gaTime);

            // 2. Chạy OR-Tools
            OrToolsSolution ortSol = orToolsSolver.solve(ctx, config.getTimeBudgetMs());
            SolutionSummary ortSummary = SolutionSummary.builder()
                    .totalDistanceKm(ortSol.totalDistance())
                    .vehiclesUsed(ortSol.vehiclesUsed())
                    .gapToBksPercent(-1.0)
                    .build();
            OptimizationResponse ortResponse = OptimizationResponse.builder()
                    .batchId(batchId)
                    .status(ortSol.status())
                    .executionTimeMs(ortSol.executionTimeMs())
                    .summary(ortSummary)
                    .routes(ortSol.routes())
                    .build();

            // 3. Tính toán các chỉ số so sánh
            double gaDist  = gaResponse.getSummary().getTotalDistanceKm();
            double ortDist = ortSol.totalDistance();
            double diffDist = gaDist - ortDist;
            double gapPct   = ortDist > 0 ? (diffDist / ortDist) * 100.0 : 0.0;
            long   diffTime = gaTime - ortSol.executionTimeMs();
            double speedup  = gaTime > 0 ? (double) ortSol.executionTimeMs() / gaTime : 1.0;

            String winner;
            String rec;
            if (gaDist < ortDist) {
                winner = "GA";
                rec = String.format("GA tìm được quãng đường ngắn hơn %.2f km (%+.2f%%) so với OR-Tools.",
                        Math.abs(diffDist), gapPct);
            } else if (ortDist < gaDist) {
                winner = "OR-TOOLS";
                rec = String.format("Google OR-Tools tìm được quãng đường ngắn hơn %.2f km (%+.2f%%) so với GA.",
                        Math.abs(diffDist), -gapPct);
            } else {
                winner = "TIE";
                rec = "Cả hai thuật toán đạt cùng chất lượng quãng đường.";
            }

            ComparisonMetrics metrics = ComparisonMetrics.builder()
                    .gaDistance(gaDist)
                    .orToolsDistance(ortDist)
                    .distanceDiff(diffDist)
                    .distanceGapPercent(gapPct)
                    .gaExecutionTimeMs(gaTime)
                    .orToolsExecutionTimeMs(ortSol.executionTimeMs())
                    .timeDiffMs(diffTime)
                    .speedup(speedup)
                    .gaVehicles(gaResponse.getSummary().getVehiclesUsed())
                    .orToolsVehicles(ortSol.vehiclesUsed())
                    .winner(winner)
                    .recommendation(rec)
                    .build();

            ComparisonResponse response = ComparisonResponse.builder()
                    .batchId(batchId)
                    .metrics(metrics)
                    .gaSolution(gaResponse)
                    .orToolsSolution(ortResponse)
                    .build();

            return CompletableFuture.completedFuture(ResponseEntity.ok(response));

        } catch (Exception e) {
            log.error("[COMPARE][{}] Lỗi thực thi đối sánh", batchId, e);
            return CompletableFuture.completedFuture(
                    ResponseEntity.internalServerError().build()
            );
        }
    }

    // =========================================================================
    // Validation sơ bộ (trước khi GA chạy)
    // =========================================================================

    /**
     * Kiểm tra tính khả thi cơ bản của bài toán.
     *
     * @return thông báo lỗi nếu không khả thi, {@code null} nếu OK
     */
    private String checkFeasibility(OptimizationRequest request) {
        GaConfig config = request.getConfig();
        List<?> customers = request.getCustomers();

        // Phải có ít nhất 1 khách hàng
        if (customers == null || customers.isEmpty()) {
            return "Danh sách khách hàng trống";
        }

        // Phải có ít nhất 1 xe
        if (config.getMaxVehicles() < 1) {
            return "Số xe tối đa phải >= 1 (nhận: " + config.getMaxVehicles() + ")";
        }

        // Phải có ít nhất N xe để phục vụ N khách hàng (tối thiểu hóa cứng)
        // → Thực tế: N xe là đủ nếu mỗi xe chở 1 khách hàng
        // Không giới hạn N <= K nghiêm ngặt vì GA có thể tìm giải pháp tốt hơn

        // Tổng demand không được vượt K × capacity
        double totalDemand = request.getCustomers().stream()
                .mapToDouble(c -> ((com.example.demo.dto.request.CustomerDto) c).getDemand())
                .sum();
        double totalCapacity = (double) config.getMaxVehicles() * config.getVehicleCapacity();
        if (totalDemand > totalCapacity) {
            return String.format(
                "Tổng nhu cầu (%.1f) vượt quá tổng sức chứa (K=%d × C=%.1f = %.1f)",
                totalDemand, config.getMaxVehicles(), config.getVehicleCapacity(), totalCapacity);
        }

        // timeBudgetMs tối thiểu 100ms
        if (config.getTimeBudgetMs() < 100) {
            return "timeBudgetMs phải >= 100ms";
        }

        return null; // OK
    }

    // =========================================================================
    // Map Individual → OptimizationResponse
    // =========================================================================

    /**
     * Chuyển đổi {@link Individual} tốt nhất và {@link ProblemContext} thành
     * {@link OptimizationResponse} đầy đủ để trả về client.
     *
     * <p>Giải mã chromosome thêm một lần nữa để lấy chi tiết từng tuyến.
     * Thao tác này O(N) và chỉ diễn ra một lần sau khi GA hoàn tất.</p>
     */
    private OptimizationResponse buildResponse(String batchId, Individual best,
                                               ProblemContext ctx, long executionMs) {
        // Giải mã chromosome → danh sách tuyến
        List<RouteDetail> routes = routeBuilder.buildRoutes(best, ctx);

        // Tính tổng khoảng cách thực tế từ routes (nhất quán với RouteDetail)
        double totalDistKm = routes.stream()
                .mapToDouble(RouteDetail::getDistanceKm)
                .sum();

        // Số xe thực tế được dùng
        int vehiclesUsed = routes.size();

        // Xác định trạng thái: INFEASIBLE nếu vẫn còn penalty > 0
        String status = best.getCapacityPenalty() > 0 ? "INFEASIBLE" : "SUCCESS";

        SolutionSummary summary = SolutionSummary.builder()
                .totalDistanceKm(totalDistKm)
                .vehiclesUsed(vehiclesUsed)
                .gapToBksPercent(-1.0) // -1 = không có BKS để so sánh (API thực tế)
                .build();

        return OptimizationResponse.builder()
                .batchId(batchId)
                .status(status)
                .executionTimeMs(executionMs)
                .summary(summary)
                .routes(routes)
                .build();
    }
}
