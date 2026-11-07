package com.example.demo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit Test – GeneticOperators.modifiedOX (Modified Order Crossover)
 *
 * Kiểm tra các bất biến (invariants) của offspring sinh ra:
 *   1. Không mất ID khách hàng nào (mọi customer ID xuất hiện đúng 1 lần)
 *   2. Không trùng lặp ID khách hàng
 *   3. Số lượng số 0 (Depot) được bảo toàn chính xác = K+1
 *   4. Độ dài offspring bằng độ dài cha mẹ
 *
 * Chạy @RepeatedTest để kiểm tra tính đúng đắn với nhiều điểm cắt ngẫu nhiên.
 */
@DisplayName("Modified Order Crossover (OX) Tests")
class ModifiedOXCrossoverTest {

    private GeneticOperators operators;
    private boolean[]        visited;

    // ── Dữ liệu test: N=8 customers, K=3 vehicles → L = 8+3+1 = 12 ──────────
    //   chromosome = [0, c1..c8 xáo trộn, 0 phân tách, 0]
    // Hai cha mẹ với cùng bộ gene nhưng thứ tự khác nhau:
    private static final int[] PARENT1 = { 0, 3, 5, 1, 0, 8, 2, 0, 4, 7, 6, 0 };
    private static final int[] PARENT2 = { 0, 2, 7, 4, 0, 1, 5, 0, 8, 6, 3, 0 };
    // K+1 = 4 số 0 (depot đầu, depot cuối, 2 vách ngăn giữa)
    private static final int NUM_CUSTOMERS = 8; // 1..8
    private static final int MAX_VEHICLES  = 3;
    private static final int CHROM_LEN     = NUM_CUSTOMERS + MAX_VEHICLES + 1; // = 12

    @BeforeEach
    void setUp() {
        operators = new GeneticOperators();
        visited   = GeneticOperators.createVisitedBuffer();
    }

    // =========================================================================
    // Test chính: bất biến chromosome
    // =========================================================================

    @Test
    @DisplayName("Offspring không mất và không trùng bất kỳ customer ID nào")
    void shouldContainAllCustomersExactlyOnce() {
        int[] offspring = new int[CHROM_LEN];
        Random rng = new Random(42); // seed cố định để test deterministic

        operators.modifiedOX(PARENT1, PARENT2, offspring, CHROM_LEN, visited, rng);

        // Đếm số lần xuất hiện mỗi customer ID (1..8)
        int[] count = new int[NUM_CUSTOMERS + 1];
        for (int gene : offspring) {
            if (gene > 0) {
                assertThat(gene)
                        .as("Gene phải là customer ID hợp lệ [1..%d]", NUM_CUSTOMERS)
                        .isBetween(1, NUM_CUSTOMERS);
                count[gene]++;
            }
        }

        // Mỗi customer xuất hiện đúng 1 lần
        for (int c = 1; c <= NUM_CUSTOMERS; c++) {
            assertThat(count[c])
                    .as("Customer %d phải xuất hiện đúng 1 lần trong offspring", c)
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Số lượng depot (0) trong offspring bằng đúng K+1 = 4")
    void shouldPreserveExactDepotCount() {
        int[] offspring = new int[CHROM_LEN];
        Random rng = new Random(99);

        operators.modifiedOX(PARENT1, PARENT2, offspring, CHROM_LEN, visited, rng);

        long zeroCount = 0;
        for (int gene : offspring) {
            if (gene == 0) zeroCount++;
        }

        int expectedZeros = MAX_VEHICLES + 1; // K+1 = 4
        assertThat(zeroCount)
                .as("Số lượng depot (0) phải bằng K+1 = %d", expectedZeros)
                .isEqualTo(expectedZeros);
    }

    @Test
    @DisplayName("Độ dài offspring bằng đúng độ dài cha mẹ (L = N+K+1)")
    void shouldPreserveChromosomeLength() {
        int[] offspring = new int[CHROM_LEN];
        // Điền giá trị sentinel -99 để kiểm tra vị trí chưa gán
        Arrays.fill(offspring, -99);

        operators.modifiedOX(PARENT1, PARENT2, offspring, CHROM_LEN, visited, new Random(7));

        // Không có phần tử -99 nào còn sót
        for (int i = 0; i < CHROM_LEN; i++) {
            assertThat(offspring[i])
                    .as("Vị trí %d không được có giá trị -99 (chưa gán)", i)
                    .isNotEqualTo(-99);
        }
    }

    @Test
    @DisplayName("Offspring chỉ chứa giá trị {0, 1..N} – không có giá trị âm hay ngoài phạm vi")
    void shouldOnlyContainValidGenes() {
        int[] offspring = new int[CHROM_LEN];
        operators.modifiedOX(PARENT1, PARENT2, offspring, CHROM_LEN, visited, new Random(13));

        for (int i = 0; i < CHROM_LEN; i++) {
            assertThat(offspring[i])
                    .as("Gene tại vị trí %d phải nằm trong [0, %d]", i, NUM_CUSTOMERS)
                    .isBetween(0, NUM_CUSTOMERS);
        }
    }

    // =========================================================================
    // Lặp lại với nhiều seed ngẫu nhiên khác nhau (bao phủ nhiều điểm cắt)
    // =========================================================================

    @RepeatedTest(value = 20, name = "OX invariants – lần {currentRepetition}/{totalRepetitions}")
    @DisplayName("Bất biến được giữ với 20 seed ngẫu nhiên khác nhau")
    void shouldMaintainInvariantsWithRandomSeeds(org.junit.jupiter.api.RepetitionInfo info) {
        Random rng      = new Random(info.getCurrentRepetition() * 137L); // seed khác nhau mỗi lần
        int[]  offspring = new int[CHROM_LEN];

        operators.modifiedOX(PARENT1, PARENT2, offspring, CHROM_LEN, visited, rng);

        // Kiểm tra tổng số gene = CHROM_LEN
        int totalGenes = offspring.length;
        assertThat(totalGenes).isEqualTo(CHROM_LEN);

        // Đếm 0s và customers
        int zeroCount     = 0;
        int[] customerCount = new int[NUM_CUSTOMERS + 1];
        for (int gene : offspring) {
            if (gene == 0) {
                zeroCount++;
            } else {
                assertThat(gene).isBetween(1, NUM_CUSTOMERS);
                customerCount[gene]++;
            }
        }

        // Bất biến 1: đúng K+1 số 0
        assertThat(zeroCount)
                .as("[Seed %d] Số depot phải = K+1=%d", info.getCurrentRepetition(), MAX_VEHICLES + 1)
                .isEqualTo(MAX_VEHICLES + 1);

        // Bất biến 2: mỗi customer xuất hiện đúng 1 lần
        for (int c = 1; c <= NUM_CUSTOMERS; c++) {
            assertThat(customerCount[c])
                    .as("[Seed %d] Customer %d phải xuất hiện đúng 1 lần", info.getCurrentRepetition(), c)
                    .isEqualTo(1);
        }
    }

    // =========================================================================
    // Edge case: cha mẹ giống hệt nhau
    // =========================================================================

    @Test
    @DisplayName("Offspring từ 2 cha mẹ giống nhau phải bảo toàn bất biến")
    void shouldHandleIdenticalParents() {
        int[] offspring = new int[CHROM_LEN];
        // Dùng cùng 1 cha làm cả 2 cha mẹ
        operators.modifiedOX(PARENT1, PARENT1, offspring, CHROM_LEN, visited, new Random(0));

        // Vẫn phải có đúng N customers và K+1 zeros
        int zeroCount = 0;
        int customerCount = 0;
        for (int gene : offspring) {
            if (gene == 0) zeroCount++;
            else customerCount++;
        }

        assertThat(zeroCount).isEqualTo(MAX_VEHICLES + 1);
        assertThat(customerCount).isEqualTo(NUM_CUSTOMERS);
    }
}
