package com.example.demo.benchmark;

import com.example.demo.dto.request.GaConfig;
import com.example.demo.model.Individual;
import com.example.demo.model.OrToolsSolution;
import com.example.demo.model.ProblemContext;
import com.example.demo.service.GaOptimizationEngine;
import com.example.demo.service.OrToolsSolverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * BenchmarkComparisonRunner – Giai đoạn 8 (OR-Tools Baseline Comparison)
 *
 * <p>Thực hiện đối sánh hiệu năng trực tiếp giữa Thuật toán Di truyền (GA)
 * và Google OR-Tools trên các bộ dữ liệu chuẩn CVRPLib Augerat (A).</p>
 *
 * <h2>Tiêu chí đối sánh theo đề tài (Chương 3)</h2>
 * <ul>
 *   <li><b>Chất lượng lộ trình (Z):</b> Khoảng cách Euclidean tìm được so với BKS (%Gap).</li>
 *   <li><b>Thời gian tính toán (T_CPU):</b> Thời gian phản hồi trong cùng một ngân sách.</li>
 *   <li><b>Số xe sử dụng (K_used):</b> Hiệu quả gom cụm tải trọng.</li>
 * </ul>
 */
@Service
public class BenchmarkComparisonRunner {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkComparisonRunner.class);

    private static final int    POP_SIZE    = 200;
    private static final int    MAX_GEN     = 1_000_000;
    private static final long   TIME_BUDGET = 3_000L;   // 3 giây chuẩn
    private static final double CROSSOVER   = 0.85;
    private static final double MUTATION    = 0.10;

    private final VrpFileParser        parser;
    private final GaOptimizationEngine  gaEngine;
    private final OrToolsSolverService orToolsSolver;

    public BenchmarkComparisonRunner(VrpFileParser parser,
                                     GaOptimizationEngine gaEngine,
                                     OrToolsSolverService orToolsSolver) {
        this.parser        = parser;
        this.gaEngine      = gaEngine;
        this.orToolsSolver = orToolsSolver;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Chạy đối sánh GA vs OR-Tools trên toàn bộ file .vrp trong thư mục dataset.
     *
     * @param datasetDir   thư mục chứa dataset (ví dụ: "E:/dacn/demo/A")
     * @param timeBudgetMs ngân sách thời gian cho mỗi thuật toán (mặc định 3000ms nếu <= 0)
     * @return danh sách kết quả đối sánh chi tiết
     */
    public List<BenchmarkComparisonResult> compareAll(Path datasetDir, long timeBudgetMs) {
        long budget = timeBudgetMs > 0 ? timeBudgetMs : TIME_BUDGET;
        List<Path> vrpFiles = collectVrpFiles(datasetDir);
        log.info("Bắt đầu đối sánh GA vs OR-Tools: {} instances trong {}", vrpFiles.size(), datasetDir);

        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println(  "║                   CVRP ALGORITHM BENCHMARK COMPARISON: GA vs GOOGLE OR-TOOLS  ·  " + Instant.now() + "                 ║");
        System.out.println(  "╠════════════════╦═════╦═════╦═══════╦═════════════════════════╦═════════════════════════╦═════════════╦════════════╣");
        System.out.println(  "║                ║     ║     ║       ║  Genetic Algorithm (GA) ║     Google OR-Tools     ║             ║            ║");
        System.out.println(  "║ Instance       ║  N  ║  K  ║  BKS  ║ Found   %Gap   Time(ms) ║ Found   %Gap   Time(ms) ║ Diff(GA-ORT)║ Winner     ║");
        System.out.println(  "╠════════════════╬═════╬═════╬═══════╬═════════════════════════╬═════════════════════════╬═════════════╬════════════╣");

        List<BenchmarkComparisonResult> results = new ArrayList<>();
        for (Path vrpFile : vrpFiles) {
            BenchmarkComparisonResult r = compareSingle(vrpFile, budget);
            results.add(r);
            printRow(r);
        }

        System.out.println("╠════════════════╩═════╩═════╩═══════╩═════════════════════════╩═════════════════════════╩═════════════╩════════════╣");
        printSummary(results);
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════════════════════════════════════╝\n");

        return results;
    }

    /**
     * Chạy đối sánh cho một instance .vrp đơn lẻ.
     */
    public BenchmarkComparisonResult compareSingle(Path vrpPath, long timeBudgetMs) {
        String name = vrpPath.getFileName().toString().replace(".vrp", "");
        long budget = timeBudgetMs > 0 ? timeBudgetMs : TIME_BUDGET;

        try {
            // 1. Parse VRP & BKS
            VrpInstance inst = parser.parse(vrpPath);
            int bks = inst.bks();

            // 2. Build Euclidean ProblemContext (chuẩn nint của CVRPLib)
            ProblemContext ctx = buildContext(inst);

            // 3. Thực thi GA
            GaConfig gaConfig = GaConfig.builder()
                    .vehicleCapacity(inst.capacity())
                    .maxVehicles(inst.maxVehicles())
                    .timeBudgetMs(budget)
                    .populationSize(POP_SIZE)
                    .maxGenerations(MAX_GEN)
                    .crossoverRate(CROSSOVER)
                    .mutationRate(MUTATION)
                    .build();

            long gaStart = System.currentTimeMillis();
            Individual gaBest = gaEngine.runOptimization(ctx, gaConfig);
            long gaTime = System.currentTimeMillis() - gaStart;

            int gaFound = (int) Math.round(gaBest.getTotalDistance());
            double gaGap = (bks != Integer.MAX_VALUE) ? (gaBest.getTotalDistance() - bks) / bks * 100.0 : Double.NaN;
            int gaVehicles = countVehiclesUsed(gaBest.getChromosome(), gaBest.getActiveLength());
            String gaStatus = gaBest.getCapacityPenalty() > 0 ? "INFEASIBLE" : "SUCCESS";

            // 4. Thực thi Google OR-Tools
            OrToolsSolution ortSol = orToolsSolver.solve(ctx, budget);
            int ortFound = (int) Math.round(ortSol.totalDistance());
            double ortGap = (bks != Integer.MAX_VALUE && !Double.isNaN(ortSol.totalDistance()) && ortSol.totalDistance() > 0)
                    ? (ortSol.totalDistance() - bks) / bks * 100.0 : Double.NaN;
            int ortVehicles = ortSol.vehiclesUsed();
            String ortStatus = ortSol.status();
            long ortTime = ortSol.executionTimeMs();

            // 5. Tính toán chênh lệch và xác định người thắng cuộc
            int diff = gaFound - ortFound;
            String winner = determineWinner(gaStatus, gaFound, gaTime, ortStatus, ortFound, ortTime);

            return new BenchmarkComparisonResult(
                    name, inst.numCustomers(), inst.maxVehicles(), bks,
                    gaFound, gaGap, gaTime, gaVehicles, gaStatus,
                    ortFound, ortGap, ortTime, ortVehicles, ortStatus,
                    diff, winner
            );

        } catch (IOException e) {
            log.warn("Lỗi đọc file {}: {}", name, e.getMessage());
            return BenchmarkComparisonResult.error(name, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Bị gián đoạn trên {}", name);
            return BenchmarkComparisonResult.error(name, "Interrupted");
        } catch (Exception e) {
            log.error("Lỗi đối sánh trên {}", name, e);
            return BenchmarkComparisonResult.error(name, e.getMessage());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String determineWinner(String gaStatus, int gaDist, long gaTime,
                                   String ortStatus, int ortDist, long ortTime) {
        boolean gaOk = "SUCCESS".equals(gaStatus);
        boolean ortOk = "SUCCESS".equals(ortStatus);

        if (gaOk && !ortOk) return "GA";
        if (!gaOk && ortOk) return "OR-TOOLS";
        if (!gaOk && !ortOk) return "NONE";

        if (gaDist < ortDist) {
            return "GA";
        } else if (ortDist < gaDist) {
            return "OR-TOOLS";
        } else {
            // Hòa khoảng cách -> so sánh thời gian thực thi
            if (gaTime < ortTime) return "GA (faster)";
            if (ortTime < gaTime) return "OR-TOOLS (faster)";
            return "TIE";
        }
    }

    private ProblemContext buildContext(VrpInstance inst) {
        int n = inst.numCustomers();
        double[][] cost = new double[n + 1][n + 1];

        for (int i = 0; i <= n; i++) {
            for (int j = i + 1; j <= n; j++) {
                double dx = inst.xs()[i] - inst.xs()[j];
                double dy = inst.ys()[i] - inst.ys()[j];
                double dist = Math.round(Math.sqrt(dx * dx + dy * dy)); // CVRPLib nint
                cost[i][j] = dist;
                cost[j][i] = dist;
            }
        }

        return new ProblemContext(n, inst.maxVehicles(), inst.capacity(), cost, inst.demands());
    }

    private int countVehiclesUsed(int[] chr, int len) {
        int count = 0;
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

    private List<Path> collectVrpFiles(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".vrp"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            log.error("Không thể liệt kê file trong {}", dir, e);
            return List.of();
        }
    }

    private void printRow(BenchmarkComparisonResult r) {
        String bksStr = r.bks() == Integer.MAX_VALUE ? "  N/A " : String.format("%5d", r.bks());
        String gaGapStr = Double.isNaN(r.gaGapPercent()) ? "  N/A " : String.format("%+6.2f%%", r.gaGapPercent());
        String ortGapStr = Double.isNaN(r.ortGapPercent()) ? "  N/A " : String.format("%+6.2f%%", r.ortGapPercent());
        String diffStr = String.format("%+5d", r.distanceDiff());

        System.out.printf("║ %-14s ║ %3d ║ %3d ║ %s ║ %5d  %s  %5d ║ %5d  %s  %5d ║ %11s ║ %-10s ║%n",
                r.instanceName(),
                r.numCustomers(),
                r.maxVehicles(),
                bksStr,
                r.gaFound(), gaGapStr, r.gaTimeMs(),
                r.ortFound(), ortGapStr, r.ortTimeMs(),
                diffStr,
                r.winner());
    }

    private void printSummary(List<BenchmarkComparisonResult> results) {
        long gaWins = results.stream().filter(r -> r.winner().startsWith("GA")).count();
        long ortWins = results.stream().filter(r -> r.winner().startsWith("OR-TOOLS")).count();
        long ties = results.stream().filter(r -> "TIE".equals(r.winner())).count();

        OptionalDouble avgGaGap = results.stream()
                .filter(r -> !Double.isNaN(r.gaGapPercent()) && "SUCCESS".equals(r.gaStatus()))
                .mapToDouble(BenchmarkComparisonResult::gaGapPercent)
                .average();

        OptionalDouble avgOrtGap = results.stream()
                .filter(r -> !Double.isNaN(r.ortGapPercent()) && "SUCCESS".equals(r.ortStatus()))
                .mapToDouble(BenchmarkComparisonResult::ortGapPercent)
                .average();

        long avgGaTime = (long) results.stream()
                .mapToLong(BenchmarkComparisonResult::gaTimeMs)
                .average().orElse(0);

        long avgOrtTime = (long) results.stream()
                .mapToLong(BenchmarkComparisonResult::ortTimeMs)
                .average().orElse(0);

        System.out.printf("║  TỔNG KẾT: Tỉ số thắng GA: %d | OR-Tools: %d | Hòa: %d%n",
                gaWins, ortWins, ties);
        System.out.printf("║  Avg Gap: GA = %s vs OR-Tools = %s | Avg Time: GA = %dms vs OR-Tools = %dms%n",
                avgGaGap.isPresent() ? String.format("%+.2f%%", avgGaGap.getAsDouble()) : "N/A",
                avgOrtGap.isPresent() ? String.format("%+.2f%%", avgOrtGap.getAsDouble()) : "N/A",
                avgGaTime, avgOrtTime);
    }
}
