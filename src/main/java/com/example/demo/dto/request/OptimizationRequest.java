package com.example.demo.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @JsonAlias({"batch_id", "batchId"})
    @Schema(description = "Mã định danh lô yêu cầu", example = "BATCH_20261110_001")
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
    @NotEmpty(message = "Danh sách khách hàng không được rỗng")
    @Size(max = 1000, message = "Số lượng khách hàng tối đa là 1000")
    private List<@Valid CustomerDto> customers;
}
