package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chỉ số đối sánh định lượng giữa Thuật toán Di truyền (GA) và Google OR-Tools.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonMetrics {

    /** Quãng đường tìm được bởi GA (km). */
    private double gaDistance;

    /** Quãng đường tìm được bởi Google OR-Tools (km). */
    private double orToolsDistance;

    /** Chênh lệch khoảng cách: GA - OR-Tools (km). Giá trị âm: GA ngắn hơn. */
    private double distanceDiff;

    /**
     * Tỷ lệ chênh lệch phần trăm: (GA - OR-Tools) / OR-Tools * 100 (%).
     * <p>Giá trị âm: GA tốt hơn OR-Tools.</p>
     */
    private double distanceGapPercent;

    /** Thời gian thực thi của GA (mili-giây). */
    private long gaExecutionTimeMs;

    /** Thời gian thực thi của Google OR-Tools (mili-giây). */
    private long orToolsExecutionTimeMs;

    /** Chênh lệch thời gian: GA - OR-Tools (ms). Giá trị âm: GA nhanh hơn. */
    private long timeDiffMs;

    /** Tỷ số tăng tốc thực thi: orToolsExecutionTimeMs / gaExecutionTimeMs. */
    private double speedup;

    /** Số xe sử dụng bởi GA. */
    private int gaVehicles;

    /** Số xe sử dụng bởi OR-Tools. */
    private int orToolsVehicles;

    /** Thuật toán chiến thắng về chất lượng lộ trình ("GA", "OR-TOOLS", hoặc "TIE"). */
    private String winner;

    /** Đánh giá / Khuyến nghị lựa chọn thuật toán phù hợp với bài toán. */
    private String recommendation;
}
