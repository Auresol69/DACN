package com.example.demo.benchmark;

/**
 * Kết quả đối sánh giữa Thuật toán Di truyền (GA) và Google OR-Tools
 * trên một CVRPLib benchmark instance.
 *
 * @param instanceName  tên instance (ví dụ: "A-n32-k5")
 * @param numCustomers  số lượng khách hàng N
 * @param maxVehicles   số lượng xe K
 * @param bks           Best Known Solution (từ file .sol)
 * @param gaFound       quãng đường tìm được bởi GA (đã làm tròn)
 * @param gaGapPercent  %Gap của GA so với BKS: (gaFound - BKS) / BKS * 100
 * @param gaTimeMs      thời gian chạy của GA (ms)
 * @param gaVehicles    số xe GA sử dụng
 * @param gaStatus      trạng thái GA ("SUCCESS", "INFEASIBLE", "ERROR")
 * @param ortFound      quãng đường tìm được bởi OR-Tools (đã làm tròn)
 * @param ortGapPercent %Gap của OR-Tools so với BKS: (ortFound - BKS) / BKS * 100
 * @param ortTimeMs     thời gian chạy của OR-Tools (ms)
 * @param ortVehicles   số xe OR-Tools sử dụng
 * @param ortStatus     trạng thái OR-Tools ("SUCCESS", "INFEASIBLE", "ERROR")
 * @param distanceDiff  chênh lệch khoảng cách: gaFound - ortFound
 * @param winner        thuật toán cho kết quả tốt hơn ("GA", "OR-TOOLS", "TIE")
 */
public record BenchmarkComparisonResult(
        String instanceName,
        int    numCustomers,
        int    maxVehicles,
        int    bks,
        int    gaFound,
        double gaGapPercent,
        long   gaTimeMs,
        int    gaVehicles,
        String gaStatus,
        int    ortFound,
        double ortGapPercent,
        long   ortTimeMs,
        int    ortVehicles,
        String ortStatus,
        int    distanceDiff,
        String winner
) {
    public static BenchmarkComparisonResult error(String name, String reason) {
        return new BenchmarkComparisonResult(
                name, 0, 0, Integer.MAX_VALUE,
                0, Double.NaN, 0, 0, "ERROR: " + reason,
                0, Double.NaN, 0, 0, "ERROR: " + reason,
                0, "NONE"
        );
    }
}
