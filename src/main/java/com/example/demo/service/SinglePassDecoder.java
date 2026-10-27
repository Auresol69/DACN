package com.example.demo.service;

import com.example.demo.model.DecodingResult;
import com.example.demo.model.Individual;
import com.example.demo.model.ProblemContext;
import org.springframework.stereotype.Component;

/**
 * Thuật toán giải mã nhiễm sắc thể tuyến tính Single-Pass O(N).
 *
 * <h2>Nguyên lý hoạt động</h2>
 * <p>Duyệt mảng {@code chromosome[]} <b>đúng một lần</b> từ trái sang phải.
 * Mỗi phần tử gene được xử lý theo quy tắc:</p>
 * <ul>
 *   <li><b>gene = 0</b> (vách ngăn Depot): Chốt tuyến xe hiện tại —
 *       cộng khoảng cách quay về kho, kiểm tra vi phạm tải trọng,
 *       reset bộ đếm tuyến mới.</li>
 *   <li><b>gene > 0</b> (ID khách hàng): Cộng dồn khoảng cách
 *       {@code costMatrix[prevNode][gene]} và tải trọng {@code demands[gene]}.</li>
 * </ul>
 *
 * <h2>Ví dụ minh họa (mục 2.2.1.2 tài liệu)</h2>
 * <pre>
 *   chromosome = [ 0, 3, 5, 1, 0, 8, 2, 0, 4, 7, 6, 0 ]
 *                  ^                                    ^
 *                  |___ depot đầu              depot cuối ___|
 *
 *   → Route 1: depot(0) → 3 → 5 → 1 → depot(0)
 *   → Route 2: depot(0) → 8 → 2 → depot(0)
 *   → Route 3: depot(0) → 4 → 7 → 6 → depot(0)
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>Lớp này <b>stateless</b> hoàn toàn — mọi biến trung gian đều là local
 * trên stack của từng luồng gọi. An toàn để share một instance duy nhất
 * qua toàn bộ Worker Thread Pool mà không cần synchronize.</p>
 *
 * <h2>Độ phức tạp</h2>
 * <ul>
 *   <li><b>Thời gian</b>: O(L) = O(N + K + 1) ≈ O(N) — tuyến tính</li>
 *   <li><b>Bộ nhớ</b>: O(1) — chỉ dùng biến local trên stack, không cấp phát heap</li>
 * </ul>
 */
@Component
public class SinglePassDecoder {

    // ─────────────────────────────────────────────────────────────────────────
    // Core decode method — hot path, gọi hàng triệu lần trong GA loop
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Giải mã nhiễm sắc thể và tính toán fitness components.
     *
     * <p>Đây là <b>hot path</b> của toàn bộ hệ thống — được gọi
     * {@code populationSize × maxGenerations} lần. Không được cấp phát
     * object mới bên trong hàm này.</p>
     *
     * @param chromosome     mảng gene, gene[i] ∈ {0, 1..N}; số 0 = vách ngăn depot
     * @param activeLength   số phần tử hợp lệ trong chromosome (≤ chromosome.length)
     * @param ctx            ngữ cảnh bài toán (costMatrix, demands, vehicleCapacity)
     * @return {@link DecodingResult} chứa totalDistance, totalCapacityPenalty, vehiclesUsed
     */
    public DecodingResult decode(final int[] chromosome,
                                 final int activeLength,
                                 final ProblemContext ctx) {

        final double[][] costMatrix     = ctx.costMatrix;
        final double[]   demands        = ctx.demands;
        final double     vehicleCapacity = ctx.vehicleCapacity;

        // ── Biến tích lũy toàn bài ─────────────────────────────────────────
        double totalDistance       = 0.0;
        double totalCapacityPenalty = 0.0;
        int    vehiclesUsed        = 0;

        // ── Biến trạng thái tuyến hiện tại ───────────────────────────────────
        int    prevNode      = 0;   // Bắt đầu từ depot (node 0)
        double routeDistance = 0.0;
        double routeLoad     = 0.0;

        // ── Single-Pass O(N) ─────────────────────────────────────────────────
        for (int i = 0; i < activeLength; i++) {
            final int gene = chromosome[i];

            if (gene == 0) {
                // ── Gặp vách ngăn 0 → Chốt tuyến hiện tại ──────────────────
                if (prevNode != 0) {
                    // Tuyến không rỗng: thêm khoảng cách quay về depot
                    routeDistance += costMatrix[prevNode][0];
                    totalDistance += routeDistance;

                    // Kiểm tra vi phạm sức chứa
                    if (routeLoad > vehicleCapacity) {
                        totalCapacityPenalty += (routeLoad - vehicleCapacity);
                    }
                    vehiclesUsed++;

                    // Reset cho tuyến mới
                    prevNode      = 0;
                    routeDistance = 0.0;
                    routeLoad     = 0.0;
                }
                // Nếu prevNode == 0: hai số 0 liên tiếp = tuyến rỗng, bỏ qua

            } else {
                // ── Gặp khách hàng → Cộng dồn khoảng cách và tải trọng ──────
                routeDistance += costMatrix[prevNode][gene];
                routeLoad     += demands[gene];
                prevNode       = gene;
            }
        }

        return new DecodingResult(totalDistance, totalCapacityPenalty, vehiclesUsed);
    }

    /**
     * Overload tiện ích — nhận trực tiếp một {@link Individual}.
     * Dùng trong GaEngine khi Worker Thread xử lý từng cá thể.
     *
     * @param individual cá thể cần đánh giá
     * @param ctx        ngữ cảnh bài toán
     * @return kết quả giải mã
     */
    public DecodingResult decode(final Individual individual,
                                 final ProblemContext ctx) {
        return decode(individual.getChromosome(), individual.getActiveLength(), ctx);
    }

    /**
     * Giải mã VÀ ghi kết quả trực tiếp vào Individual (tránh tạo object trung gian
     * khi chỉ cần cập nhật fitness, không cần giữ DecodingResult).
     *
     * <p>Ghi thẳng vào các field của {@link Individual}:
     * {@code totalDistance}, {@code capacityPenalty}, {@code fitness}.</p>
     *
     * @param individual       cá thể cần đánh giá (sẽ bị mutate)
     * @param ctx              ngữ cảnh bài toán
     * @param penaltyCoeffAlpha hệ số nhân hình phạt sức chứa α(t)
     */
    public void decodeAndSetFitness(final Individual individual,
                                    final ProblemContext ctx,
                                    final double penaltyCoeffAlpha) {

        final DecodingResult result = decode(individual, ctx);

        individual.setTotalDistance(result.totalDistance());
        individual.setCapacityPenalty(result.totalCapacityPenalty());

        // Fitness = totalDistance + α(t) × capacityPenalty (tối thiểu hóa)
        // Theo công thức mục 2.1.2.2: Fitness(X) = Z + α·Penalty_Cap
        // (phiên bản đơn giản chưa có penalty thời gian β)
        final double fitness = result.totalDistance()
                             + penaltyCoeffAlpha * result.totalCapacityPenalty();
        individual.setFitness(fitness);
    }
}
