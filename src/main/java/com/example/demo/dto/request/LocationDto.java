package com.example.demo.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Đại diện cho kho hàng trung tâm (Depot) trong bài toán CVRP.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationDto {

    /**
     * ID số nguyên của depot (thường = 0 theo quy ước CVRPLib).
     */
    @NotNull(message = "ID depot không được để trống")
    @Min(value = 0, message = "ID depot phải >= 0")
    @Schema(description = "ID kho hàng (luôn là 0)", example = "0")
    private Integer id;

    /** Vĩ độ (latitude) của depot, phạm vi [-90, 90]. */
    @NotNull(message = "Vĩ độ depot không được để trống")
    @DecimalMin(value = "-90.0", message = "Vĩ độ tối thiểu là -90")
    @DecimalMax(value = "90.0", message = "Vĩ độ tối đa là 90")
    @JsonAlias({"latitude", "lat"})
    @Schema(description = "Vĩ độ kho hàng", example = "10.762622")
    private Double latitude;

    /** Kinh độ (longitude) của depot, phạm vi [-180, 180]. */
    @NotNull(message = "Kinh độ depot không được để trống")
    @DecimalMin(value = "-180.0", message = "Kinh độ tối thiểu là -180")
    @DecimalMax(value = "180.0", message = "Kinh độ tối đa là 180")
    @JsonAlias({"longitude", "lng"})
    @Schema(description = "Kinh độ kho hàng", example = "106.660172")
    private Double longitude;
}
