package com.example.demo.benchmark;

import java.util.List;

/**
 * Data class chứa toàn bộ dữ liệu của một CVRPLib instance đã được parse.
 *
 * <h2>Quy ước chỉ số (Indexing Convention)</h2>
 * <pre>
 *   vrpNodeId = 1          → depot   → systemIndex = 0
 *   vrpNodeId = 2..N+1     → customer → systemIndex = 1..N
 *   customerRelativeId     = vrpNodeId - 1  (dùng trong file .sol)
 * </pre>
 *
 * @param instanceName  tên instance (ví dụ: "A-n32-k5")
 * @param numCustomers  số lượng khách hàng N (không bao gồm depot)
 * @param maxVehicles   số xe K
 * @param capacity      sức chứa mỗi xe C
 * @param xs            tọa độ X của tất cả nodes: xs[0]=depot, xs[1..N]=customers
 * @param ys            tọa độ Y của tất cả nodes
 * @param demands       nhu cầu hàng hóa: demands[0]=0, demands[1..N]=q_i
 * @param bks           Best Known Solution từ file .sol (Integer.MAX_VALUE nếu không có)
 */
public record VrpInstance(
        String instanceName,
        int    numCustomers,
        int    maxVehicles,
        double capacity,
        double[] xs,
        double[] ys,
        double[] demands,
        int    bks
) {
    /** Trả về OptimizationRequest-compatible LocationDto (depot tại index 0). */
    public double depotX() { return xs[0]; }
    public double depotY() { return ys[0]; }

    /** Độ dài chromosome = N + K + 1. */
    public int chromosomeLength() { return numCustomers + maxVehicles + 1; }
}
