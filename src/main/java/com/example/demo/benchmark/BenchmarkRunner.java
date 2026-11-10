package com.example.demo.benchmark;

import com.example.demo.dto.request.GaConfig;
import com.example.demo.model.Individual;
import com.example.demo.model.ProblemContext;
import com.example.demo.service.GaOptimizationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * BenchmarkRunner – Giai đoạn 7
 *
 * <p>Tự động chạy GA trên toàn bộ 27 instances Augerat (A) trong thư mục
 * {@code datasetDir}, ghi kết quả ra console dạng bảng Markdown.</p>
 *
 * <h2>Luồng xử lý cho mỗi instance</h2>
 * <ol>
 *   <li>Parse .vrp → {@link VrpInstance}</li>
 *   <li>Build {@link ProblemContext} bằng Euclidean (nint rounding – khớp CVRPLib BKS)</li>
 *   <li>Chạy {@link GaOptimizationEngine#runOptimization} với config mặc định</li>
 *   <li>Tính %Gap = (found - BKS) / BKS × 100</li>
 *   <li>Lưu vào {@link BenchmarkResult} và in bảng tổng kết</li>
 * </ol>
 *
 * <h2>Tham số GA mặc định cho benchmark</h2>
 * <ul>
 *   <li>populationSize = 200</li>
 *   <li>maxGenerations = 1,000,000 (thực tế bị giới hạn bởi timeBudgetMs)</li>
 *   <li>timeBudgetMs   = 3000 (3 giây / instance)</li>
 *   <li>crossoverRate  = 0.85</li>
 *   <li>mutationRate   = 0.10</li>
 * </ul>
 */
@Service
public class BenchmarkRunner {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);

    // ── Tham số GA cho benchmark (theo tài liệu mục 2.4.3) ───────────────────
    private static final int    POP_SIZE      = 200;
    private static final int    MAX_GEN       = 1_000_000;
    private static final long   TIME_BUDGET   = 3_000L;   // 3 giây
    private static final double CROSSOVER     = 0.85;
    private static final double MUTATION      = 0.10;

    private final VrpFileParser        parser;
    private final GaOptimizationEngine  gaEngine;

    public BenchmarkRunner(VrpFileParser parser,
                           GaOptimizationEngine gaEngine) {
        this.parser   = parser;
        this.gaEngine = gaEngine;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Chạy benchmark toàn bộ file .vrp trong thư mục {@code datasetDir}.
     *
     * @param datasetDir thư mục chứa các file .vrp (ví dụ: "E:\\dacn\\demo\\A")
     * @return danh sách kết quả, mỗi phần tử ứng với 1 instance
     */
    public List<BenchmarkResult> runAll(Path datasetDir) {
        List<Path> vrpFiles = collectVrpFiles(datasetDir);
        log.info("Bắt đầu benchmark: {} instances trong {}", vrpFiles.size(), datasetDir);
        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println(  "║           CVRP GA BENCHMARK  –  Augerat (A) dataset  ·  " + Instant.now() + "           ║");
        System.out.println(  "╠════════════════╦═════╦═════╦═══════╦═══════╦═══════╦══════════╦══════════╦═════════╣");
        System.out.println(  "║ Instance       ║  N  ║  K  ║  BKS  ║ Found ║ %Gap  ║ Vehicles ║ Time(ms) ║ Status  ║");
        System.out.println(  "╠════════════════╬═════╬═════╬═══════╬═══════╬═══════╬══════════╬══════════╬═════════╣");

        List<BenchmarkResult> results = new ArrayList<>();
        for (Path vrpFile : vrpFiles) {
            BenchmarkResult r = runSingle(vrpFile);
            results.add(r);
            printRow(r);
        }

        System.out.println("╠════════════════╩═════╩═════╩═══════╩═══════╩═══════╩══════════╩══════════╩═════════╣");
        printSummary(results);
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════════╝\n");

        return results;
    }

    /**
     * Chạy benchmark cho một instance đơn lẻ.
     */
    public BenchmarkResult runSingle(Path vrpPath) {
        String name = vrpPath.getFileName().toString().replace(".vrp", "");
        try {
            // 1. Parse file VRP
            VrpInstance inst = parser.parse(vrpPath);

            // 2. Build ProblemContext bằng Euclidean (CVRPLib chuẩn)
            ProblemContext ctx = buildContext(inst);

            // 3. Cấu hình GA
            GaConfig config = GaConfig.builder()
                    .vehicleCapacity(inst.capacity())
                    .maxVehicles(inst.maxVehicles())
                    .timeBudgetMs(TIME_BUDGET)
                    .populationSize(POP_SIZE)
                    .maxGenerations(MAX_GEN)
                    .crossoverRate(CROSSOVER)
                    .mutationRate(MUTATION)
                    .build();

            // 4. Chạy GA
            long startMs = System.currentTimeMillis();
            Individual best = gaEngine.runOptimization(ctx, config);
            long elapsedMs = System.currentTimeMillis() - startMs;

            // 5. Tính kết quả
            double foundDist = best.getTotalDistance();
            double penalty   = best.getCapacityPenalty();
            int    found     = (int) Math.round(foundDist);
            int    bks       = inst.bks();
            double gap       = (bks != Integer.MAX_VALUE)
                    ? (foundDist - bks) / bks * 100.0
                    : Double.NaN;

            // 6. Đếm số xe sử dụng (đếm tuyến không rỗng)
            int vehiclesUsed = countVehiclesUsed(best.getChromosome(), best.getActiveLength());

            String status = penalty > 0 ? "INFEASIBLE" : "SUCCESS";

            return new BenchmarkResult(name, inst.numCustomers(), inst.maxVehicles(),
                    bks, found, gap, vehiclesUsed, elapsedMs, status, penalty);

        } catch (IOException e) {
            log.warn("ERROR on {}: {}", name, e.getMessage());
            log.error("Lỗi đọc file: {}", vrpPath, e);
            return BenchmarkResult.error(name, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("ERROR on {}: Interrupted", name);
            return BenchmarkResult.error(name, "Interrupted");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Build ProblemContext với ma trận khoảng cách Euclidean + nint rounding
     * (chuẩn CVRPLib EUC_2D, bắt buộc để kết quả khớp BKS).
     */
    private ProblemContext buildContext(VrpInstance inst) {
        int n = inst.numCustomers();
        double[][] cost = new double[n + 1][n + 1];

        for (int i = 0; i <= n; i++) {
            for (int j = i + 1; j <= n; j++) {
                double dx   = inst.xs()[i] - inst.xs()[j];
                double dy   = inst.ys()[i] - inst.ys()[j];
                double dist = Math.round(Math.sqrt(dx * dx + dy * dy)); // nint
                cost[i][j]  = dist;
                cost[j][i]  = dist;
            }
        }

        return new ProblemContext(n, inst.maxVehicles(), inst.capacity(), cost, inst.demands());
    }

    /** Đếm tuyến không rỗng (xe thực sự được sử dụng). */
    private int countVehiclesUsed(int[] chr, int len) {
        int count   = 0;
        boolean hasCustomer = false;
        for (int i = 0; i < len; i++) {
            if (chr[i] == 0) {
                if (hasCustomer) { count++; hasCustomer = false; }
            } else {
                hasCustomer = true;
            }
        }
        if (hasCustomer) count++;
        return count;
    }

    /** Thu thập tất cả file .vrp trong thư mục, sắp xếp theo tên. */
    private List<Path> collectVrpFiles(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".vrp"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            log.error("Không thể liệt kê file trong: {}", dir, e);
            return List.of();
        }
    }

    /** In một dòng kết quả instance. */
    private void printRow(BenchmarkResult r) {
        String gapStr  = Double.isNaN(r.gapPercent()) ? "  N/A " : String.format("%+6.2f%%", r.gapPercent());
        String bksStr  = r.bks() == Integer.MAX_VALUE ? "  N/A " : String.format("%5d", r.bks());
        System.out.printf("║ %-14s ║ %3d ║ %3d ║ %s ║ %5d ║ %s ║ %8d ║ %8d ║ %-7s ║%n",
                r.instanceName(),
                r.numCustomers(),
                r.maxVehicles(),
                bksStr,
                r.foundDistance(),
                gapStr,
                r.vehiclesUsed(),
                r.elapsedMs(),
                r.status());
    }

    /** In dòng tổng kết ở cuối bảng. */
    private void printSummary(List<BenchmarkResult> results) {
        long successCount = results.stream().filter(r -> "SUCCESS".equals(r.status())).count();
        OptionalDouble avgGap = results.stream()
                .filter(r -> !Double.isNaN(r.gapPercent()) && r.bks() != Integer.MAX_VALUE)
                .mapToDouble(BenchmarkResult::gapPercent)
                .average();
        OptionalDouble maxGap = results.stream()
                .filter(r -> !Double.isNaN(r.gapPercent()) && r.bks() != Integer.MAX_VALUE)
                .mapToDouble(BenchmarkResult::gapPercent)
                .max();
        long avgTime = (long) results.stream()
                .mapToLong(BenchmarkResult::elapsedMs)
                .average()
                .orElse(0);

        System.out.printf("║  %-20s  Feasible: %2d/%2d   AvgGap: %s   MaxGap: %s   AvgTime: %dms  ║%n",
                "TỔNG KẾT",
                successCount, results.size(),
                avgGap.isPresent()  ? String.format("%+.2f%%", avgGap.getAsDouble())  : "N/A",
                maxGap.isPresent()  ? String.format("%+.2f%%", maxGap.getAsDouble())  : "N/A",
                avgTime);
    }

    // =========================================================================
    // Result record
    // =========================================================================

    /**
     * Kết quả benchmark của một instance.
     *
     * @param instanceName  tên instance (ví dụ: "A-n32-k5")
     * @param numCustomers  số khách hàng N
     * @param maxVehicles   số xe K
     * @param bks           Best Known Solution (Integer.MAX_VALUE nếu không có)
     * @param foundDistance khoảng cách tìm được (đã làm tròn)
     * @param gapPercent    %Gap = (found - BKS) / BKS × 100 (NaN nếu không có BKS)
     * @param vehiclesUsed  số xe thực sự sử dụng
     * @param elapsedMs     thời gian chạy (ms)
     * @param status        "SUCCESS" / "INFEASIBLE" / "ERROR"
     * @param penalty       tổng penalty vi phạm capacity
     */
    public record BenchmarkResult(
            String instanceName,
            int    numCustomers,
            int    maxVehicles,
            int    bks,
            int    foundDistance,
            double gapPercent,
            int    vehiclesUsed,
            long   elapsedMs,
            String status,
            double penalty
    ) {
        static BenchmarkResult error(String name, String reason) {
            return new BenchmarkResult(name, 0, 0, Integer.MAX_VALUE,
                    0, Double.NaN, 0, 0, "ERROR", 0);
        }
    }
}
