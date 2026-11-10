package com.example.demo.model;

import com.example.demo.dto.response.RouteDetail;

import java.util.List;

/**
 * Kết quả giải bài toán CVRP bằng Google OR-Tools.
 *
 * @param totalDistance   tổng khoảng cách tất cả các tuyến (km hoặc đơn vị tọa độ)
 * @param vehiclesUsed    số lượng xe thực sự được sử dụng (tuyến không rỗng)
 * @param executionTimeMs thời gian thực thi (mili-giây)
 * @param status          trạng thái giải ("SUCCESS", "INFEASIBLE", "NOT_SOLVED", "ERROR")
 * @param routes          danh sách chi tiết lộ trình từng xe
 */
public record OrToolsSolution(
        double totalDistance,
        int vehiclesUsed,
        long executionTimeMs,
        String status,
        List<RouteDetail> routes
) {
    public static OrToolsSolution empty(String status, long timeMs) {
        return new OrToolsSolution(0.0, 0, timeMs, status, List.of());
    }
}
