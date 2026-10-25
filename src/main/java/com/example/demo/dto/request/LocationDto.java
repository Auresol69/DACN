package com.example.demo.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Đại diện cho kho hàng trung tâm (Depot) trong bài toán CVRP.
 * Tọa độ WGS-84 dùng để tính Ma trận chi phí (Distance Matrix).
 *
 * <p>JSON schema theo tài liệu mục 2.4.3.1:</p>
 * <pre>
 * "depot": { "id": 0, "latitude": 10.762622, "longitude": 106.660172 }
 * </pre>
 *
 * <p>Theo quy ước: depot luôn có chỉ số 0 trong ma trận chi phí,
 * các khách hàng được đánh chỉ số 1..N.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationDto {

    /**
     * ID số nguyên của depot (thường = 0 theo quy ước CVRPLib).
     * Giá trị này được ánh xạ sang chỉ số 0 trong mảng chromosome.
     */
    @Min(value = 0, message = "ID depot phải >= 0")
    private int id;

    /** Vĩ độ (latitude) của depot, phạm vi [-90, 90]. */
    @DecimalMin(value = "-90.0", message = "Vĩ độ tối thiểu là -90")
    @DecimalMax(value = "90.0", message = "Vĩ độ tối đa là 90")
    private double latitude;

    /** Kinh độ (longitude) của depot, phạm vi [-180, 180]. */
    @DecimalMin(value = "-180.0", message = "Kinh độ tối thiểu là -180")
    @DecimalMax(value = "180.0", message = "Kinh độ tối đa là 180")
    private double longitude;
}
