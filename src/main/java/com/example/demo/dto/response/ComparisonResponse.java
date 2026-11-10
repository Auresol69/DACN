package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO phản hồi của endpoint đối sánh: POST /api/v1/optimize/compare
 *
 * <p>Cung cấp kết quả giải song song từ cả Thuật toán Di truyền (GA)
 * và Google OR-Tools trên cùng một bộ dữ liệu đầu vào.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonResponse {

    /** Mã định danh lô đơn hàng. */
    private String batchId;

    /** Bảng chỉ số so sánh định lượng. */
    private ComparisonMetrics metrics;

    /** Chi tiết lời giải từ Thuật toán Di truyền (GA). */
    private OptimizationResponse gaSolution;

    /** Chi tiết lời giải từ Google OR-Tools CP-SAT Baseline. */
    private OptimizationResponse orToolsSolution;
}
