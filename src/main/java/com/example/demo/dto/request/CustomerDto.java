package com.example.demo.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thông tin của một khách hàng trong bài toán CVRP.
 * Mỗi khách hàng có nhu cầu (demand) phải được phục vụ bởi đúng một xe.
 *
 * <p>JSON schema theo tài liệu mục 2.4.3.1:</p>
 * <pre>
 * {"id": 1, "demand": 19.0, "lat": 10.77123, "lng": 106.6721}
 * </pre>
 *
 * <p>Lưu ý: id là số nguyên (int), ánh xạ trực tiếp sang gene trong chromosome[].
 * Giá trị id phải nằm trong khoảng [1, N] để phân biệt với depot (id=0).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDto {

    /**
     * Định danh số nguyên của khách hàng (1-indexed).
     * Giá trị này = chỉ số gene trong mảng chromosome[].
     * Depot luôn là 0, khách hàng từ 1 đến N.
     */
    @Min(value = 1, message = "ID khách hàng phải >= 1 (0 là depot)")
    private int id;

    /**
     * Nhu cầu hàng hóa của khách hàng (q_i).
     * Kiểu double để tương thích với CVRPLib (ví dụ: A-n32-k5 có demand dạng số thực).
     * Ràng buộc: demand <= vehicleCapacity của xe.
     */
    @Positive(message = "Nhu cầu khách hàng phải là số dương")
    private double demand;

    /** Vĩ độ (latitude) của khách hàng, phạm vi [-90, 90]. */
    @DecimalMin(value = "-90.0", message = "Vĩ độ tối thiểu là -90")
    @DecimalMax(value = "90.0", message = "Vĩ độ tối đa là 90")
    private double lat;

    /** Kinh độ (longitude) của khách hàng, phạm vi [-180, 180]. */
    @DecimalMin(value = "-180.0", message = "Kinh độ tối thiểu là -180")
    @DecimalMax(value = "180.0", message = "Kinh độ tối đa là 180")
    private double lng;
}
