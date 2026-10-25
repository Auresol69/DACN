package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Chi tiết tuyến đường của một xe trong lời giải CVRP.
 *
 * <p>JSON schema theo tài liệu mục 2.4.3.2:</p>
 * <pre>
 * {
 *   "vehicle_id": 1,
 *   "route_sequence": [3, 5, 1],
 *   "total_load": 35.0,
 *   "distance_km": 14.2
 * }
 * </pre>
 *
 * <p>route_sequence chứa danh sách ID khách hàng (int) theo thứ tự phục vụ.
 * Depot ở hai đầu (xuất phát và trở về) được ngầm định, không liệt kê.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteDetail {

    /**
     * Định danh số nguyên của xe (1-indexed).
     * Tương ứng với vị trí phân đoạn giữa các số 0 trong chromosome.
     */
    private int vehicleId;

    /**
     * Chuỗi ID khách hàng theo thứ tự phục vụ.
     * Giải mã trực tiếp từ chromosome[] – bỏ qua các số 0 phân tách.
     * Không bao gồm depot (id=0) ở đầu và cuối.
     */
    private List<Integer> routeSequence;

    /**
     * Tổng nhu cầu hàng hóa của tuyến này (Σ q_i).
     * Kiểu double đồng bộ với CustomerDto.demand.
     * Bất biến: totalLoad <= vehicleCapacity.
     */
    private double totalLoad;

    /** Tổng quãng đường tuyến này di chuyển (km). */
    private double distanceKm;
}
