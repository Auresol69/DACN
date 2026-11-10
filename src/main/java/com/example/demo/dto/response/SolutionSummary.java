package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thống kê tổng quan của toàn bộ lời giải CVRP tối ưu được tìm thấy.
 *
 * <p>JSON schema theo tài liệu mục 2.4.3.2:</p>
 * <pre>
 * "summary": {
 *   "total_distance_km": 42.85,
 *   "vehicles_used": 3,
 *   "gap_to_bks_percent": 0.42
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolutionSummary {

    /** Tổng quãng đường tất cả các xe di chuyển (km). */
    private double totalDistanceKm;

    /** Số xe thực tế được sử dụng trong lời giải (K_actual <= maxVehicles). */
    private int vehiclesUsed;

    /**
     * Phần trăm lệch so với Best Known Solution (BKS) trên CVRPLib.
     * Công thức: (Z_found - Z_bks) / Z_bks * 100 (%).
     * Mục tiêu của đề tài: gapToBksPercent < 2%.
     * Giá trị -1.0 nếu không có BKS để so sánh.
     */
    private double gapToBksPercent;
}
