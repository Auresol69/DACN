package com.example.demo.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cấu hình tham số cho Thuật toán Di truyền (GA).
 * Mỗi trường được validate chặt chẽ để đảm bảo GA hội tụ ổn định.
 *
 * <p>Tương ứng với JSON schema mục 2.4.3.1 trong tài liệu:</p>
 * <pre>
 * "config": {
 *   "vehicle_capacity": 100.0,
 *   "max_vehicles": 5,
 *   "time_budget_ms": 3000,
 *   "population_size": 200,
 *   "max_generations": 500,
 *   "crossover_rate": 0.85,
 *   "mutation_rate": 0.10
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GaConfig {

    /**
     * Sức chứa tối đa của mỗi xe (C_cap).
     * Dùng làm ràng buộc cứng khi giải mã nhiễm sắc thể.
     * Kiểu double để tương thích với CVRPLib (ví dụ: sức chứa tính theo kg/thể tích).
     */
    @Positive(message = "Sức chứa xe phải là số dương")
    private double vehicleCapacity;

    /**
     * Số lượng xe tối đa có sẵn trong đội xe (K).
     * Ảnh hưởng trực tiếp đến kích thước chromosome: L = N + K + 1.
     */
    @Positive(message = "Số xe tối đa phải là số dương")
    @Max(value = 200, message = "Số xe tối đa là 200")
    private int maxVehicles;

    /**
     * Ngân sách thời gian tối đa cho một lần tối ưu hóa (ms).
     * GA sẽ dừng sớm nếu vượt quá giới hạn này, trả về kết quả tốt nhất hiện tại.
     * Mục tiêu: timeBudgetMs < 3000 ms (3 giây) theo yêu cầu Batching Window.
     */
    @Positive(message = "Ngân sách thời gian phải là số dương")
    private long timeBudgetMs;

    /**
     * Kích thước quần thể: số lượng cá thể (nhiễm sắc thể) trong mỗi thế hệ.
     * Giá trị điển hình từ tài liệu: 200. Quá nhỏ → hội tụ sớm; quá lớn → chậm.
     */
    @Min(value = 10, message = "Kích thước quần thể tối thiểu là 10")
    @Max(value = 1000, message = "Kích thước quần thể tối đa là 1000")
    private int populationSize;

    /**
     * Số thế hệ tối đa GA sẽ chạy.
     * Giá trị điển hình từ tài liệu: 500.
     * Là điều kiện dừng thứ hai sau timeBudgetMs.
     */
    @Positive(message = "Số thế hệ phải là số dương")
    private int maxGenerations;

    /**
     * Xác suất lai ghép (crossover). Giá trị trong khoảng [0.0, 1.0].
     * Giá trị điển hình từ tài liệu: 0.85 (p_c = 0.8 – 0.9).
     */
    @Min(value = 0, message = "Tỷ lệ lai ghép không được âm")
    @Max(value = 1, message = "Tỷ lệ lai ghép không được vượt quá 1.0")
    private double crossoverRate;

    /**
     * Xác suất đột biến (mutation). Giá trị trong khoảng [0.0, 1.0].
     * Giá trị điển hình từ tài liệu: 0.10 (p_m = 0.05 – 0.15).
     */
    @Min(value = 0, message = "Tỷ lệ đột biến không được âm")
    @Max(value = 1, message = "Tỷ lệ đột biến không được vượt quá 1.0")
    private double mutationRate;
}
