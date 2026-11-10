package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO đầu ra chính của API tối ưu hóa CVRP.
 *
 * <p>JSON schema theo tài liệu mục 2.4.3.2:</p>
 * <pre>
 * {
 *   "batch_id": "BATCH_20261019_001",
 *   "status": "SUCCESS",
 *   "execution_time_ms": 1420,
 *   "summary": {
 *     "total_distance_km": 42.85,
 *     "vehicles_used": 3,
 *     "gap_to_bks_percent": 0.42
 *   },
 *   "routes": [
 *     { "vehicle_id": 1, "route_sequence": [3,5,1], "total_load": 35.0, "distance_km": 14.2 },
 *     { "vehicle_id": 2, "route_sequence": [8,2],   "total_load": 30.0, "distance_km": 11.5 }
 *   ]
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizationResponse {

    /**
     * Định danh lô, khớp với batchId trong request để client tương quan kết quả.
     */
    private String batchId;

    /**
     * Trạng thái xử lý:
     * <ul>
     *   <li>{@code SUCCESS}    – GA kết thúc bình thường, lời giải hợp lệ được tìm thấy</li>
     *   <li>{@code FAILED}     – Lỗi trong quá trình xử lý (exception, timeout nghiêm trọng)</li>
     *   <li>{@code INFEASIBLE} – Không tìm được lời giải hợp lệ (ví dụ: tổng demand > K * capacity)</li>
     * </ul>
     */
    private String status;

    /** Thời gian thực thi thực tế của thuật toán GA (ms). Mục tiêu: < 3000 ms. */
    private long executionTimeMs;

    /** Thống kê tổng hợp của lời giải tốt nhất tìm được. */
    private SolutionSummary summary;

    /**
     * Danh sách chi tiết tuyến đường của từng xe.
     * Chỉ bao gồm xe thực sự được sử dụng (route_sequence không rỗng).
     * Được sắp xếp theo vehicle_id tăng dần.
     */
    private List<RouteDetail> routes;
}
