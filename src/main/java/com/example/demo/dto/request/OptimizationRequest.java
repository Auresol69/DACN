package com.example.demo.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO đầu vào chính của API tối ưu hóa CVRP.
 *
 * <p>Cấu trúc JSON tương ứng:</p>
 * <pre>
 * {
 *   "batch_id": "batch-001",
 *   "config": { "capacity": 100, "time_budget_ms": 30000, ... },
 *   "depot":   { "id": "depot", "lat": 10.762622, "lng": 106.660172 },
 *   "customers": [
 *     { "id": "c1", "demand": 10, "lat": 10.77, "lng": 106.68 },
 *     ...
 *   ]
 * }
 * </pre>
 *
 * <p>Ánh xạ nội bộ: ID chuỗi của khách hàng sẽ được đánh chỉ số nguyên
 * (index 1..N) khi xây dựng quần thể GA. Kho hàng luôn là chỉ số 0.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizationRequest {

    /**
     * Định danh lô yêu cầu, dùng để tương quan log và response.
     * Nên là UUID hoặc chuỗi duy nhất từ phía client.
     */
    @NotBlank(message = "batch_id không được để trống")
    private String batchId;

    /** Tham số cấu hình thuật toán GA. */
    @Valid
    @NotNull(message = "config không được null")
    private GaConfig config;

    /** Vị trí kho hàng (điểm xuất phát và kết thúc của mọi tuyến đường). */
    @Valid
    @NotNull(message = "depot không được null")
    private LocationDto depot;

    /**
     * Danh sách khách hàng cần phục vụ.
     * Tối thiểu 1 khách hàng để bài toán có nghĩa.
     */
    @Valid
    @NotEmpty(message = "Danh sách khách hàng không được rỗng")
    @Size(max = 1000, message = "Số lượng khách hàng tối đa là 1000")
    private List<CustomerDto> customers;
}
