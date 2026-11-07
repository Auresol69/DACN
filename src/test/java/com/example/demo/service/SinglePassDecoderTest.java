package com.example.demo.service;

import com.example.demo.model.DecodingResult;
import com.example.demo.model.ProblemContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit Test – SinglePassDecoder
 *
 * Kiểm tra 3 luồng logic chính:
 *   1. Tính tổng khoảng cách đúng
 *   2. Nhận diện đúng số xe được sử dụng
 *   3. Phát hiện và tính phạt đúng khi vượt tải
 *
 * Không dùng Spring Context – pure unit test, tốc độ nhanh.
 */
@DisplayName("SinglePassDecoder Tests")
class SinglePassDecoderTest {

    private SinglePassDecoder decoder;

    // ── costMatrix nhỏ cho test: 4 node (depot=0, C1=1, C2=2, C3=3) ──────────
    // Ma trận đối xứng:
    //          0     1     2     3
    //   0  [  0.0, 10.0,  5.0, 20.0 ]   depot
    //   1  [ 10.0,  0.0,  3.0, 15.0 ]   C1
    //   2  [  5.0,  3.0,  0.0,  8.0 ]   C2
    //   3  [ 20.0, 15.0,  8.0,  0.0 ]   C3
    private static final double[][] COST = {
        {  0.0, 10.0,  5.0, 20.0 },
        { 10.0,  0.0,  3.0, 15.0 },
        {  5.0,  3.0,  0.0,  8.0 },
        { 20.0, 15.0,  8.0,  0.0 }
    };

    // demands: [depot=0, C1=30, C2=40, C3=50]
    private static final double[] DEMANDS = { 0.0, 30.0, 40.0, 50.0 };

    @BeforeEach
    void setUp() {
        decoder = new SinglePassDecoder();
    }

    // =========================================================================
    // Nhóm 1: Tính khoảng cách và số xe
    // =========================================================================
    @Nested
    @DisplayName("Khoảng cách và số xe sử dụng")
    class DistanceAndVehicles {

        /**
         * chromosome = [0, 1, 2, 0, 3, 0]
         *   Route 1: depot → C1 → C2 → depot   = 10 + 3 + 5 = 18.0
         *   Route 2: depot → C3 → depot          = 20 + 20  = 40.0
         *   Total                                            = 58.0
         *   vehiclesUsed = 2
         */
        @Test
        @DisplayName("Tính đúng total_distance với 2 tuyến")
        void shouldCalculateTotalDistanceCorrectly() {
            // Arrange
            ProblemContext ctx = makeCtx(100.0); // capacity đủ lớn, không vi phạm
            int[] chromosome   = { 0, 1, 2, 0, 3, 0 };

            // Act
            DecodingResult result = decoder.decode(chromosome, chromosome.length, ctx);

            // Assert
            // Route 1: 0→1 (10) + 1→2 (3) + 2→0 (5)  = 18
            // Route 2: 0→3 (20) + 3→0 (20)             = 40
            assertThat(result.totalDistance())
                    .as("Tổng khoảng cách phải = 58.0")
                    .isEqualTo(58.0);
        }

        @Test
        @DisplayName("Nhận diện đúng 2 xe được sử dụng")
        void shouldCountVehiclesUsedCorrectly() {
            ProblemContext ctx = makeCtx(100.0);
            int[] chromosome   = { 0, 1, 2, 0, 3, 0 };

            DecodingResult result = decoder.decode(chromosome, chromosome.length, ctx);

            assertThat(result.vehiclesUsed())
                    .as("Phải có đúng 2 xe được sử dụng")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("Tuyến rỗng (hai số 0 liên tiếp) không được đếm là xe")
        void shouldIgnoreEmptyRoutes() {
            // chromosome = [0, 0, 1, 2, 0, 3, 0] – có tuyến rỗng ở đầu
            ProblemContext ctx = makeCtx(100.0);
            int[] chromosome   = { 0, 0, 1, 2, 0, 3, 0 };

            DecodingResult result = decoder.decode(chromosome, chromosome.length, ctx);

            // Tuyến rỗng bị bỏ qua → vẫn chỉ 2 xe
            assertThat(result.vehiclesUsed())
                    .as("Tuyến rỗng không được đếm là xe sử dụng")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("Chromosome chỉ có 1 tuyến")
        void shouldHandleSingleRoute() {
            // chromosome = [0, 1, 2, 3, 0] – 1 xe chở tất cả
            ProblemContext ctx = makeCtx(999.0);
            int[] chromosome   = { 0, 1, 2, 3, 0 };

            DecodingResult result = decoder.decode(chromosome, chromosome.length, ctx);

            // Route: 0→1(10) + 1→2(3) + 2→3(8) + 3→0(20) = 41
            assertThat(result.totalDistance()).isEqualTo(41.0);
            assertThat(result.vehiclesUsed()).isEqualTo(1);
        }
    }

    // =========================================================================
    // Nhóm 2: Capacity Penalty
    // =========================================================================
    @Nested
    @DisplayName("Phạt vi phạm sức chứa (capacity penalty)")
    class CapacityPenalty {

        /**
         * chromosome = [0, 1, 2, 0, 3, 0]
         * capacity = 60.0
         *   Route 1: load = 30 + 40 = 70  → vi phạm: 70 - 60 = 10
         *   Route 2: load = 50            → OK
         *   totalCapacityPenalty = 10.0
         */
        @Test
        @DisplayName("Tính đúng penalty khi Route 1 vượt tải")
        void shouldCalculatePenaltyWhenRouteExceedsCapacity() {
            // Arrange: capacity = 60, Route 1 load = 70 → penalty = 10
            ProblemContext ctx = makeCtx(60.0);
            int[] chromosome   = { 0, 1, 2, 0, 3, 0 };

            // Act
            DecodingResult result = decoder.decode(chromosome, chromosome.length, ctx);

            // Assert
            assertThat(result.totalCapacityPenalty())
                    .as("Penalty = 70 - 60 = 10.0")
                    .isEqualTo(10.0);
        }

        @Test
        @DisplayName("Không có penalty khi tất cả tuyến đều trong capacity")
        void shouldHaveZeroPenaltyWhenWithinCapacity() {
            ProblemContext ctx = makeCtx(100.0); // demands max = 50 → không vi phạm
            int[] chromosome   = { 0, 1, 2, 0, 3, 0 };

            DecodingResult result = decoder.decode(chromosome, chromosome.length, ctx);

            assertThat(result.totalCapacityPenalty())
                    .as("Không có vi phạm capacity → penalty = 0")
                    .isZero();
        }

        @Test
        @DisplayName("Tích lũy penalty từ nhiều tuyến vi phạm")
        void shouldAccumulatePenaltyFromMultipleRoutes() {
            // capacity = 25: cả 3 route đều vi phạm
            // Route 1 (C1+C2): load=70, pen=45
            // Route 2 (C3):    load=50, pen=25
            // total pen = 70
            ProblemContext ctx = makeCtx(25.0);
            int[] chromosome   = { 0, 1, 2, 0, 3, 0 };

            DecodingResult result = decoder.decode(chromosome, chromosome.length, ctx);

            assertThat(result.totalCapacityPenalty())
                    .as("Penalty tích lũy từ 2 route vi phạm")
                    .isEqualTo(70.0);
        }

        @Test
        @DisplayName("Chromosome hợp lệ hoàn toàn: penalty = 0 và distance > 0")
        void shouldBeFullyFeasible() {
            ProblemContext ctx = makeCtx(100.0);
            int[] chromosome   = { 0, 1, 2, 0, 3, 0 };

            DecodingResult result = decoder.decode(chromosome, chromosome.length, ctx);

            assertThat(result.totalCapacityPenalty()).isZero();
            assertThat(result.totalDistance()).isPositive();
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /**
     * Tạo ProblemContext với capacity tùy chỉnh, dùng costMatrix và demands cố định.
     * N=3 khách hàng (1,2,3), K=2 xe → chromosomeLength = 3+2+1 = 6
     */
    private ProblemContext makeCtx(double capacity) {
        return new ProblemContext(3, 2, capacity, COST, DEMANDS);
    }
}
