package com.example.demo.model;

/**
 * Đại diện cho một Cá thể (Nhiễm sắc thể) trong Thuật toán Di truyền.
 *
 * <h2>Mã hóa Nhiễm sắc thể – Mảng 1 chiều phân tách số 0</h2>
 * <p>Chromosome được mã hóa dưới dạng một mảng số nguyên 1 chiều duy nhất.
 * Số {@code 0} đóng vai trò ký tự phân tách tuyến đường (đại diện cho kho hàng).</p>
 *
 * <p><b>Ví dụ:</b> Với N=8 khách hàng và K=3 xe (L = 8+3+1 = 12):</p>
 * <pre>
 *   chromosome = [ 0, 3, 5, 1, 0, 8, 2, 0, 4, 7, 6, 0 ]
 *                  ────────────  ────────  ──────────────
 *   Tuyến 1: depot → C3 → C5 → C1 → depot
 *   Tuyến 2: depot → C8 → C2 → depot
 *   Tuyến 3: depot → C4 → C7 → C6 → depot
 * </pre>
 *
 * <p>Kích thước mảng cố định = {@code L = N + K + 1}
 * (N khách hàng + K+1 số 0 gồm: depot đầu, depot cuối, K-1 phân tách giữa).
 * Theo tài liệu mục 2.2.1: "chiều dài chromosome = N + K + 1".</p>
 *
 * <h2>Object Pooling – Triệt tiêu Garbage Collection</h2>
 * <p>Lớp này <b>không bao giờ được khởi tạo bằng {@code new}</b> trong vòng lặp GA.
 * Thay vào đó, hãy dùng {@link IndividualPool#acquire()} và {@link IndividualPool#release(Individual)}.
 * Mảng {@code chromosome} được tái sử dụng hoàn toàn, chỉ ghi đè giá trị.</p>
 */
public final class Individual implements Comparable<Individual> {

    // ─────────────────────────────────────────────────────────────────────────
    // Trường dữ liệu – tất cả package-private để IndividualPool truy cập trực tiếp
    // mà không cần getter/setter (tránh overhead trong vòng lặp hot-path)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Mảng nhiễm sắc thể. Kích thước được cấp phát tĩnh một lần duy nhất
     * khi pool khởi tạo. Không bao giờ thay thế tham chiếu này.
     */
    int[] chromosome;

    /**
     * Độ dài thực tế của nhiễm sắc thể đang dùng trong chromosome[].
     * Giá trị này <= chromosome.length.
     */
    int activeLength;

    /**
     * Tổng khoảng cách của tất cả các tuyến đường trong cá thể này (km).
     * Được tính bởi hàm fitness evaluation.
     */
    double totalDistance;

    /**
     * Tổng hình phạt vi phạm ràng buộc sức chứa.
     * = Σ max(0, load_route_k - capacity) cho tất cả tuyến k.
     * Cá thể hợp lệ sẽ có capacityPenalty = 0.
     */
    double capacityPenalty;

    /**
     * Giá trị fitness tổng hợp của cá thể. Được tối thiểu hóa.
     * Công thức: fitness = totalDistance + PENALTY_COEFF * capacityPenalty
     * (PENALTY_COEFF đủ lớn để phạt nặng vi phạm ràng buộng cứng).
     */
    double fitness;

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor – chỉ gọi bởi IndividualPool khi khởi tạo pool
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Khởi tạo cá thể với mảng chromosome có kích thước cố định.
     * <b>Không gọi trực tiếp.</b> Sử dụng {@link IndividualPool#acquire()}.
     *
     * @param chromosomeCapacity kích thước tối đa của mảng chromosome
     */
    Individual(int chromosomeCapacity) {
        this.chromosome = new int[chromosomeCapacity];
        this.activeLength = 0;
        this.totalDistance = 0.0;
        this.capacityPenalty = 0.0;
        this.fitness = Double.MAX_VALUE;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Đặt lại trạng thái của cá thể về mặc định trước khi tái sử dụng.
     * Được gọi bởi {@link IndividualPool#acquire()} sau khi lấy từ pool.
     * <b>Không xóa nội dung chromosome[]</b> – dữ liệu cũ sẽ bị ghi đè
     * khi khởi tạo chromosome mới.
     *
     * @param activeLength độ dài thực tế sẽ dùng
     */
    public void reset(int activeLength) {
        this.activeLength = activeLength;
        this.totalDistance = 0.0;
        this.capacityPenalty = 0.0;
        this.fitness = Double.MAX_VALUE;
    }

    /**
     * Sao chép nội dung chromosome từ cá thể nguồn vào cá thể này.
     * Dùng trong toán tử lai ghép (crossover) và chọn lọc elitism.
     *
     * @param source cá thể nguồn cần sao chép
     * @throws ArrayIndexOutOfBoundsException nếu chromosome source lớn hơn chromosome đích
     */
    public void copyFrom(Individual source) {
        this.activeLength = source.activeLength;
        System.arraycopy(source.chromosome, 0, this.chromosome, 0, source.activeLength);
        this.totalDistance = source.totalDistance;
        this.capacityPenalty = source.capacityPenalty;
        this.fitness = source.fitness;
    }

    /**
     * So sánh hai cá thể theo giá trị fitness (tối thiểu hóa).
     * Dùng để sắp xếp quần thể và tìm cá thể tốt nhất.
     */
    @Override
    public int compareTo(Individual other) {
        return Double.compare(this.fitness, other.fitness);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Getters – dùng cho lớp ngoài (controller, response builder)
    // ─────────────────────────────────────────────────────────────────────────

    public int[] getChromosome()      { return chromosome; }
    public int   getActiveLength()    { return activeLength; }
    public double getTotalDistance()  { return totalDistance; }
    public double getCapacityPenalty(){ return capacityPenalty; }
    public double getFitness()        { return fitness; }

    // ─────────────────────────────────────────────────────────────────────────
    // Setters nội bộ – dùng bởi FitnessEvaluator và GaEngine
    // ─────────────────────────────────────────────────────────────────────────

    public void setTotalDistance(double totalDistance)   { this.totalDistance = totalDistance; }
    public void setCapacityPenalty(double capacityPenalty){ this.capacityPenalty = capacityPenalty; }
    public void setFitness(double fitness)               { this.fitness = fitness; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Individual{fitness=")
                .append(String.format("%.4f", fitness))
                .append(", distance=")
                .append(String.format("%.4f", totalDistance))
                .append(", penalty=")
                .append(String.format("%.4f", capacityPenalty))
                .append(", chromosome=[");
        for (int i = 0; i < activeLength; i++) {
            if (i > 0) sb.append(',');
            sb.append(chromosome[i]);
        }
        return sb.append("]}").toString();
    }
}
