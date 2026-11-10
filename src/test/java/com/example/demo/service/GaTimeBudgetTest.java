package com.example.demo.service;

import com.example.demo.dto.request.CustomerDto;
import com.example.demo.dto.request.GaConfig;
import com.example.demo.dto.request.LocationDto;
import com.example.demo.dto.request.OptimizationRequest;
import com.example.demo.model.Individual;
import com.example.demo.model.ProblemContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit Test – GaOptimizationEngine: Time Budget Enforcement
 *
 * Kiểm tra rằng Engine DỪNG lại trong ngân sách thời gian (time_budget_ms)
 * ngay cả khi max_generations rất lớn (1,000,000).
 *
 * Tiêu chí pass:
 *   - executionTime <= timeBudgetMs + margin (margin = 500ms để tính overhead JVM)
 *   - Phải trả về kết quả hợp lệ (không null, distance > 0)
 */
@DisplayName("GA Engine – Time Budget Tests")
class GaTimeBudgetTest {

    private GaOptimizationEngine engine;
    private ProblemContext       ctx;

    // ── Bài toán nhỏ: 10 khách hàng, 3 xe ───────────────────────────────────
    // Đủ nhỏ để khởi tạo nhanh, đủ để GA chạy nhiều thế hệ
    private static final int    NUM_CUSTOMERS = 10;
    private static final int    MAX_VEHICLES  = 3;
    private static final double CAPACITY      = 100.0;

    @BeforeEach
    void setUp() {
        // Tạo ProblemContext bằng ma trận Euclidean đơn giản
        // 11 node: depot(0) + 10 customers(1..10)
        int n = NUM_CUSTOMERS;
        double[][] cost    = new double[n + 1][n + 1];
        double[]   demands = new double[n + 1];

        // Tọa độ giả: node i tại (i*10, 0) → khoảng cách đơn giản
        double[] x = new double[n + 1];
        for (int i = 0; i <= n; i++) x[i] = i * 10.0;

        for (int i = 0; i <= n; i++) {
            demands[i] = (i == 0) ? 0.0 : 8.0; // mỗi khách cần 8 đơn vị
            for (int j = 0; j <= n; j++) {
                cost[i][j] = Math.abs(x[i] - x[j]);
            }
        }

        ctx = new ProblemContext(n, MAX_VEHICLES, CAPACITY, cost, demands);

        // Khởi tạo engine với dependencies thực (không mock – unit test thuần)
        SinglePassDecoder decoder   = new SinglePassDecoder();
        GeneticOperators  operators = new GeneticOperators();
        engine = new GaOptimizationEngine(decoder, operators);
    }

    // =========================================================================
    // Test chính: Time Budget 1000ms với 1,000,000 thế hệ
    // =========================================================================

    @Test
    @DisplayName("Engine phải dừng trong ~1 giây dù max_generations = 1,000,000")
    void shouldStopWithinTimeBudget() throws InterruptedException {
        // Arrange
        final long timeBudgetMs  = 1000L;   // 1 giây
        final int  maxGenerations = 1_000_000; // con số rất lớn → không bao giờ hoàn thành tự nhiên
        final int  margin        = 500;     // dung sai JVM overhead (ms)

        GaConfig config = GaConfig.builder()
                .vehicleCapacity(CAPACITY)
                .maxVehicles(MAX_VEHICLES)
                .timeBudgetMs(timeBudgetMs)
                .populationSize(50)         // nhỏ để nhanh khởi tạo
                .maxGenerations(maxGenerations)
                .crossoverRate(0.8)
                .mutationRate(0.1)
                .build();

        // Act
        long startMs = System.currentTimeMillis();
        Individual best = engine.runOptimization(ctx, config);
        long elapsedMs = System.currentTimeMillis() - startMs;

        // Assert 1: Thời gian thực thi phải nằm trong ngân sách + margin
        assertThat(elapsedMs)
                .as("Engine phải dừng trong %dms + %dms margin, nhưng chạy %dms",
                        timeBudgetMs, margin, elapsedMs)
                .isLessThanOrEqualTo(timeBudgetMs + margin);

        // Assert 2: Phải trả về kết quả hợp lệ (không null)
        assertThat(best)
                .as("Engine phải trả về Individual hợp lệ dù bị dừng sớm")
                .isNotNull();

        // Assert 3: Khoảng cách phải dương (ít nhất 1 thế hệ đã được đánh giá)
        assertThat(best.getTotalDistance())
                .as("totalDistance phải > 0 (ít nhất 1 thế hệ đã chạy)")
                .isPositive();

        // Log thông tin để debug dễ hơn
        System.out.printf("[TimeBudgetTest] elapsed=%dms (budget=%dms, maxGen=%,d)%n",
                elapsedMs, timeBudgetMs, maxGenerations);
        System.out.printf("[TimeBudgetTest] bestDist=%.4f, penalty=%.4f%n",
                best.getTotalDistance(), best.getCapacityPenalty());
    }

    @Test
    @DisplayName("Engine phải dừng trong ~500ms với budget = 500ms")
    void shouldStopWithin500msBudget() throws InterruptedException {
        final long timeBudgetMs = 500L;
        final int  margin       = 500;

        GaConfig config = GaConfig.builder()
                .vehicleCapacity(CAPACITY)
                .maxVehicles(MAX_VEHICLES)
                .timeBudgetMs(timeBudgetMs)
                .populationSize(30)
                .maxGenerations(1_000_000)
                .crossoverRate(0.8)
                .mutationRate(0.1)
                .build();

        long startMs  = System.currentTimeMillis();
        Individual best = engine.runOptimization(ctx, config);
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertThat(elapsedMs)
                .as("Engine phải dừng trong %dms + %dms margin", timeBudgetMs, margin)
                .isLessThanOrEqualTo(timeBudgetMs + margin);

        assertThat(best).isNotNull();
    }

    @Test
    @DisplayName("Engine hoàn thành trước budget nếu max_generations nhỏ")
    void shouldFinishBeforeBudgetWhenGenerationsAreFew() throws InterruptedException {
        final long timeBudgetMs  = 10_000L; // 10 giây – rất thoải mái
        final int  maxGenerations = 5;      // chỉ 5 thế hệ → kết thúc rất nhanh

        GaConfig config = GaConfig.builder()
                .vehicleCapacity(CAPACITY)
                .maxVehicles(MAX_VEHICLES)
                .timeBudgetMs(timeBudgetMs)
                .populationSize(20)
                .maxGenerations(maxGenerations)
                .crossoverRate(0.8)
                .mutationRate(0.1)
                .build();

        long startMs  = System.currentTimeMillis();
        Individual best = engine.runOptimization(ctx, config);
        long elapsedMs = System.currentTimeMillis() - startMs;

        // Với chỉ 5 thế hệ, phải hoàn thành rất nhanh (< 2 giây)
        assertThat(elapsedMs)
                .as("Chỉ %d thế hệ phải hoàn thành trong < 2000ms", maxGenerations)
                .isLessThan(2_000L);

        assertThat(best).isNotNull();
        assertThat(best.getTotalDistance()).isPositive();
    }
}
