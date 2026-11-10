package com.example.demo.controller;

import com.example.demo.benchmark.BenchmarkRunner;
import com.example.demo.benchmark.BenchmarkRunner.BenchmarkResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * BenchmarkController – Giai đoạn 7
 *
 * <p>Endpoint kích hoạt benchmark tự động trên dataset CVRPLib Augerat (A).</p>
 *
 * <ul>
 *   <li>GET  /api/v1/benchmark/run?dir=... – chạy toàn bộ dataset</li>
 *   <li>POST /api/v1/benchmark/run-single  – chạy 1 file .vrp cụ thể</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/benchmark")
public class BenchmarkController {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkController.class);

    private final BenchmarkRunner runner;

    /** Thư mục dataset mặc định – có thể override qua application.properties */
    @Value("${benchmark.dataset.dir:E:/dacn/demo/A}")
    private String defaultDatasetDir;

    public BenchmarkController(BenchmarkRunner runner) {
        this.runner = runner;
    }

    // =========================================================================
    // GET /api/v1/benchmark/run
    // =========================================================================

    /**
     * Chạy benchmark toàn bộ dataset Augerat (A).
     *
     * <p>Phương thức bất đồng bộ – không block Tomcat I/O thread.
     * Kết quả in ra console và trả về JSON.</p>
     *
     * @param dir đường dẫn thư mục chứa .vrp files (mặc định: defaultDatasetDir)
     * @return danh sách BenchmarkResult dạng JSON
     */
    @GetMapping("/run")
    @Async("gaTaskExecutor")
    public CompletableFuture<ResponseEntity<List<BenchmarkResult>>> runAll(
            @RequestParam(required = false) String dir) {

        Path datasetDir = Paths.get(dir != null ? dir : defaultDatasetDir);
        log.info("Bắt đầu benchmark toàn bộ dataset: {}", datasetDir);

        try {
            List<BenchmarkResult> results = runner.runAll(datasetDir);
            return CompletableFuture.completedFuture(ResponseEntity.ok(results));
        } catch (Exception e) {
            log.error("Lỗi khi chạy benchmark", e);
            return CompletableFuture.completedFuture(
                    ResponseEntity.internalServerError().build());
        }
    }

    // =========================================================================
    // POST /api/v1/benchmark/run-single
    // =========================================================================

    /**
     * Chạy benchmark cho 1 instance .vrp cụ thể.
     *
     * @param vrpFilePath đường dẫn tuyệt đối đến file .vrp
     * @return BenchmarkResult của instance đó
     */
    @PostMapping("/run-single")
    @Async("gaTaskExecutor")
    public CompletableFuture<ResponseEntity<BenchmarkResult>> runSingle(
            @RequestParam String vrpFilePath) {

        log.info("Chạy benchmark đơn lẻ: {}", vrpFilePath);
        try {
            BenchmarkResult result = runner.runSingle(Paths.get(vrpFilePath));
            return CompletableFuture.completedFuture(ResponseEntity.ok(result));
        } catch (Exception e) {
            log.error("Lỗi khi chạy benchmark đơn lẻ: {}", vrpFilePath, e);
            return CompletableFuture.completedFuture(
                    ResponseEntity.internalServerError().build());
        }
    }
}
