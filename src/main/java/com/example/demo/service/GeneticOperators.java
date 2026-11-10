package com.example.demo.service;

import com.example.demo.model.Individual;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * GeneticOperators – Giai đoạn 3
 *
 * Chứa các toán tử di truyền hoạt động TRỰC TIẾP trên mảng int[].
 * Tất cả phương thức đều stateless, không cấp phát Object trong hot-path.
 *
 * Nguồn: Mục 2.3 – Tài liệu "dacn remake.pdf"
 */
@Component
public class GeneticOperators {

    // Buffer tối đa cho visited[]; lớn hơn số khách hàng thực tế trong mọi dataset
    private static final int MAX_NODES = 1001;

    // =========================================================================
    // 2.3.1  Tournament Selection (k = 2 hoặc tuỳ chỉnh)
    // =========================================================================

    /**
     * Chọn lọc giải đấu (Tournament Selection).
     *
     * Rút ngẫu nhiên {@code k} cá thể từ quần thể, trả về CHỈ SỐ của
     * cá thể có fitness THẤP NHẤT (vì fitness = cost, cần minimize).
     *
     * Độ phức tạp: O(k) – thường k = 2 (binary tournament).
     *
     * @param population mảng quần thể hiện tại
     * @param popSize    kích thước quần thể đang được sử dụng
     * @param k          kích thước giải đấu (thường = 2)
     * @param rng        nguồn số ngẫu nhiên của luồng hiện tại
     * @return chỉ số (index) của cá thể chiến thắng trong mảng population
     */
    public int tournamentSelect(Individual[] population, int popSize, int k, Random rng) {
        // Chọn cá thể đầu tiên ngẫu nhiên
        int bestIdx = rng.nextInt(popSize);
        double bestFitness = population[bestIdx].getFitness();

        // So sánh với k-1 cá thể ngẫu nhiên tiếp theo
        for (int i = 1; i < k; i++) {
            int competitorIdx = rng.nextInt(popSize);
            double competitorFitness = population[competitorIdx].getFitness();
            // fitness = 1/(cost+penalty) → MAXIMIZE (cao hơn = tốt hơn) [mục 2.4.1.3]
            if (competitorFitness > bestFitness) {
                bestFitness = competitorFitness;
                bestIdx = competitorIdx;
            }
        }

        return bestIdx;
    }

    // =========================================================================
    // 2.3.2  Modified Order Crossover (OX)
    // =========================================================================

    /**
     * Lai ghép theo thứ tự có cải tiến (Modified Order Crossover – OX).
     *
     * Thuật toán 3 bước (mục 2.3.2.2):
     *   1. Chọn ngẫu nhiên đoạn [cut1..cut2], copy từ parent1 sang offspring.
     *   2. Quét parent2 từ vị trí cut2+1 (xoay vòng), điền các customer
     *      chưa có vào các ô trống của offspring (bỏ qua khu vực [cut1..cut2]).
     *   3. Các ô vẫn còn -1 (placeholder) được gán 0 (ký hiệu depot).
     *
     * Không cấp phát Object: dùng visited[] được truyền vào từ caller,
     * hoặc nếu null thì dùng buffer tĩnh cục bộ.
     *
     * @param parent1    mảng nhiễm sắc thể cha
     * @param parent2    mảng nhiễm sắc thể mẹ
     * @param offspring  mảng con (pre-allocated bởi caller) – sẽ bị ghi đè hoàn toàn
     * @param length     activeLength của nhiễm sắc thể (= N + K + 1)
     * @param visited    buffer boolean tái sử dụng [0..MAX_NODES], caller cấp phát một lần
     * @param rng        nguồn số ngẫu nhiên
     */
    public void modifiedOX(int[] parent1, int[] parent2, int[] offspring,
                           int length, boolean[] visited, Random rng) {

        // --- Chọn điểm cắt ngẫu nhiên [1 .. length-2] ---
        int cut1 = 1 + rng.nextInt(length - 2); // [1, length-2]
        int cut2 = 1 + rng.nextInt(length - 2);
        if (cut1 > cut2) {
            int tmp = cut1;
            cut1 = cut2;
            cut2 = tmp;
        }

        // --- Reset visited buffer ---
        // Chỉ reset các phần tử thực sự cần thiết (tránh fill toàn bộ 1001)
        for (int i = 0; i < length; i++) {
            int gene = parent1[i];
            if (gene > 0 && gene < visited.length) visited[gene] = false;
            gene = parent2[i];
            if (gene > 0 && gene < visited.length) visited[gene] = false;
        }

        // --- Khởi tạo offspring với sentinel -1 (ô trống) ---
        for (int i = 0; i < length; i++) {
            offspring[i] = -1;
        }

        // --- Bước 1: Copy đoạn [cut1..cut2] từ parent1 ---
        for (int i = cut1; i <= cut2; i++) {
            offspring[i] = parent1[i];
            int gene = parent1[i];
            if (gene != 0) {
                visited[gene] = true; // đánh dấu customer đã có mặt
            }
        }

        // --- Bước 2: Điền customer còn thiếu từ parent2 (xoay vòng từ cut2+1) ---
        int outIdx = (cut2 + 1) % length;

        for (int i = 0; i < length; i++) {
            int p2Idx = (cut2 + 1 + i) % length;
            int customer = parent2[p2Idx];

            // Chỉ điền customer thực (≠ 0) chưa được đánh dấu
            if (customer != 0 && !visited[customer]) {
                // Bỏ qua các vị trí trong đoạn đã copy [cut1..cut2]
                while (outIdx >= cut1 && outIdx <= cut2) {
                    outIdx = (outIdx + 1) % length;
                }
                offspring[outIdx] = customer;
                visited[customer] = true;
                outIdx = (outIdx + 1) % length;
            }
        }

        // --- Bước 3: Các ô còn -1 → gán 0 (depot separator) ---
        for (int i = 0; i < length; i++) {
            if (offspring[i] == -1) {
                offspring[i] = 0;
            }
        }
    }

    // =========================================================================
    // 2.3.3  Mutation (Swap / Relocate / Inversion)
    // =========================================================================

    /**
     * Áp dụng đột biến ngẫu nhiên lên nhiễm sắc thể (mục 2.3.3).
     *
     * Chọn ngẫu nhiên 1 trong 3 phép đột biến với xác suất bằng nhau:
     *   - Swap      : đổi chỗ 2 phần tử ≠ 0
     *   - Relocate  : di chuyển 1 phần tử ≠ 0 sang vị trí khác (shift)
     *   - Inversion : đảo ngược 1 đoạn con [i..j]
     *
     * @param chromosome  mảng nhiễm sắc thể cần đột biến (in-place)
     * @param activeLength độ dài hợp lệ của chromosome (= N + K + 1)
     * @param rng         nguồn số ngẫu nhiên
     */
    public void mutate(int[] chromosome, int activeLength, Random rng) {
        int op = rng.nextInt(3); // 0=Swap, 1=Relocate, 2=Inversion
        switch (op) {
            case 0 -> swapMutation(chromosome, activeLength, rng);
            case 1 -> relocateMutation(chromosome, activeLength, rng);
            case 2 -> inversionMutation(chromosome, activeLength, rng);
        }
    }

    // -------------------------------------------------------------------------
    // Swap Mutation: đổi chỗ 2 phần tử ≠ 0 trong khoảng [1 .. activeLength - 2]
    // -------------------------------------------------------------------------
    private void swapMutation(int[] chromosome, int activeLength, Random rng) {
        if (activeLength <= 3) return;
        int i = findNonZeroPos(chromosome, 1, activeLength - 2, -1, rng);
        int j = findNonZeroPos(chromosome, 1, activeLength - 2, i, rng);
        if (i < 0 || j < 0 || i == j) return;

        int tmp = chromosome[i];
        chromosome[i] = chromosome[j];
        chromosome[j] = tmp;
    }

    // -------------------------------------------------------------------------
    // Relocate (Shift) Mutation: lấy 1 phần tử ≠ 0 ra, chèn vào vị trí khác trong [1..activeLength-2]
    // -------------------------------------------------------------------------
    private void relocateMutation(int[] chromosome, int activeLength, Random rng) {
        if (activeLength <= 3) return;
        int innerLen = activeLength - 2;
        int i = findNonZeroPos(chromosome, 1, activeLength - 2, -1, rng);
        if (i < 0) return;

        // Chèn vào vị trí j ngẫu nhiên trong [1 .. activeLength-2] (j ≠ i)
        int j;
        int attempts = 0;
        do {
            j = 1 + rng.nextInt(innerLen);
            attempts++;
        } while (j == i && attempts < 10);
        if (j == i) return;

        int gene = chromosome[i];

        // Dời phần tử: xóa tại i, chèn tại j
        if (i < j) {
            System.arraycopy(chromosome, i + 1, chromosome, i, j - i);
            chromosome[j] = gene;
        } else {
            System.arraycopy(chromosome, j, chromosome, j + 1, i - j);
            chromosome[j] = gene;
        }
        chromosome[0] = 0;
        chromosome[activeLength - 1] = 0;
    }

    // -------------------------------------------------------------------------
    // Inversion Mutation: đảo ngược đoạn [i..j] trong [1 .. activeLength-2]
    // -------------------------------------------------------------------------
    private void inversionMutation(int[] chromosome, int activeLength, Random rng) {
        if (activeLength <= 3) return;
        int innerLen = activeLength - 2;
        int i = 1 + rng.nextInt(innerLen);
        int j = 1 + rng.nextInt(innerLen);
        if (i > j) {
            int tmp = i;
            i = j;
            j = tmp;
        }
        if (i == j) return;

        // Reverse đoạn [i..j] in-place
        while (i < j) {
            int tmp = chromosome[i];
            chromosome[i] = chromosome[j];
            chromosome[j] = tmp;
            i++;
            j--;
        }
        chromosome[0] = 0;
        chromosome[activeLength - 1] = 0;
    }

    // =========================================================================
    // Helper
    // =========================================================================

    /**
     * Tìm ngẫu nhiên một vị trí có chromosome[pos] ≠ 0 (customer thực)
     * trong đoạn [start .. end].
     *
     * @param exclude vị trí cần loại trừ (-1 = không loại trừ)
     * @return vị trí tìm được, hoặc -1 nếu không tìm thấy sau 20 lần thử
     */
    private int findNonZeroPos(int[] chromosome, int start, int end, int exclude, Random rng) {
        int span = end - start + 1;
        for (int attempt = 0; attempt < 20; attempt++) {
            int pos = start + rng.nextInt(span);
            if (chromosome[pos] != 0 && pos != exclude) {
                return pos;
            }
        }
        // Fallback: quét tuần tự
        for (int pos = start; pos <= end; pos++) {
            if (chromosome[pos] != 0 && pos != exclude) {
                return pos;
            }
        }
        return -1; // không có customer nào
    }

    /**
     * Tạo buffer visited[] tái sử dụng (gọi một lần trước vòng lặp GA).
     * Caller nên giữ reference này suốt vòng đời luồng.
     */
    public static boolean[] createVisitedBuffer() {
        return new boolean[MAX_NODES];
    }
}
