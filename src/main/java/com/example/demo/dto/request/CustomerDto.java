package com.example.demo.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thông tin của một khách hàng trong bài toán CVRP.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDto {

    /**
     * Định danh số nguyên của khách hàng (1-indexed).
     */
    @NotNull(message = "ID khách hàng không được để trống")
    @Min(value = 1, message = "ID khách hàng phải >= 1 (0 là depot)")
    @Schema(description = "ID khách hàng (bắt đầu từ 1)", example = "1")
    private Integer id;

    /**
     * Nhu cầu hàng hóa của khách hàng (q_i).
     */
    @NotNull(message = "Nhu cầu khách hàng không được để trống")
    @Positive(message = "Nhu cầu khách hàng phải là số dương")
    @Schema(description = "Nhu cầu hàng hóa", example = "19.0")
    private Double demand;

    /** Vĩ độ (latitude) của khách hàng, phạm vi [-90, 90]. */
    @NotNull(message = "Vĩ độ khách hàng không được để trống")
    @DecimalMin(value = "-90.0", message = "Vĩ độ tối thiểu là -90")
    @DecimalMax(value = "90.0", message = "Vĩ độ tối đa là 90")
    @JsonAlias({"lat", "latitude"})
    @Schema(description = "Vĩ độ khách hàng", example = "10.77123")
    private Double lat;

    /** Kinh độ (longitude) của khách hàng, phạm vi [-180, 180]. */
    @NotNull(message = "Kinh độ khách hàng không được để trống")
    @DecimalMin(value = "-180.0", message = "Kinh độ tối thiểu là -180")
    @DecimalMax(value = "180.0", message = "Kinh độ tối đa là 180")
    @JsonAlias({"lng", "longitude"})
    @Schema(description = "Kinh độ khách hàng", example = "106.6721")
    private Double lng;
}
