package com.example.demo.model;

/**
 * Ngữ cảnh bài toán CVRP được pre-compute một lần duy nhất trước khi GA chạy.
 *
 * <h2>Chiến lược bộ nhớ</h2>
 * <p>Tất cả các mảng ở đây là <b>Read-Only</b> trong suốt quá trình GA:
 * mọi Worker Thread chỉ đọc {@code costMatrix} và {@code demands},
 * không bao giờ ghi, do đó không cần lock hay CAS — zero contention.</p>
 *
 * <h2>Cấu trúc chỉ số (Indexing Convention)</h2>
 * <pre>
 *   Node 0       = Kho hàng (Depot)
 *   Node 1..N    = Khách hàng (Customer IDs 1..N)
 *   demands[0]   = 0.0  (depot không có nhu cầu)
 *   demands[i]   = q_i  (nhu cầu khách hàng i)
 *   costMatrix[i][j] = khoảng cách từ node i đến node j (km)
 * </pre>
 *
 * <h2>Kích thước chromosome</h2>
 * <pre>
 *   L = numCustomers + maxVehicles + 1
 *   (N khách hàng + K+1 số 0: depot đầu, depot cuối, K-1 phân tách giữa)
 * </pre>
 */
public final class ProblemContext {

    // ── Các tham số bài toán ────────────────────────────────────────────────

    /** Số lượng khách hàng (N). */
    public final int numCustomers;

    /** Số xe tối đa trong đội xe (K). */
    public final int maxVehicles;

    /** Sức chứa tối đa của mỗi xe (C_cap). */
    public final double vehicleCapacity;

    // ── Mảng dữ liệu tĩnh (pre-allocated, read-only trong GA) ───────────────

    /**
     * Ma trận chi phí/khoảng cách (N+1) × (N+1).
     * {@code costMatrix[i][j]} = khoảng cách từ node i đến node j.
     * Truy xuất O(1) — không tính toán lại trong vòng lặp GA.
     */
    public final double[][] costMatrix;

    /**
     * Mảng nhu cầu hàng hóa kích thước (N+1).
     * {@code demands[0] = 0} (depot), {@code demands[i] = q_i} (khách hàng i).
     */
    public final double[] demands;

    /**
     * Kích thước chromosome cố định: {@code L = numCustomers + maxVehicles + 1}.
     * Dùng khi pre-allocate IndividualPool.
     */
    public final int chromosomeLength;

    // ── Constructor ─────────────────────────────────────────────────────────

    public ProblemContext(int numCustomers, int maxVehicles, double vehicleCapacity,
                          double[][] costMatrix, double[] demands) {
        this.numCustomers    = numCustomers;
        this.maxVehicles     = maxVehicles;
        this.vehicleCapacity = vehicleCapacity;
        this.costMatrix      = costMatrix;
        this.demands         = demands;
        this.chromosomeLength = numCustomers + maxVehicles + 1;
    }

    @Override
    public String toString() {
        return String.format(
            "ProblemContext{N=%d, K=%d, C_cap=%.1f, L=%d}",
            numCustomers, maxVehicles, vehicleCapacity, chromosomeLength
        );
    }
}
