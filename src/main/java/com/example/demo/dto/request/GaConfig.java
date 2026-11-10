package com.example.demo.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cấu hình tham số cho Thuật toán Di truyền (GA).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GaConfig {

    /**
     * Sức chứa tối đa của mỗi xe (C_cap).
     */
    @NotNull(message = "Sức chứa xe không được để trống")
    @Positive(message = "Sức chứa xe phải là số dương")
    @JsonAlias({"vehicle_capacity", "vehicleCapacity"})
    @Schema(description = "Sức chứa tối đa của mỗi xe", example = "100.0")
    private Double vehicleCapacity;

    /**
     * Số lượng xe tối đa có sẵn trong đội xe (K).
     */
    @NotNull(message = "Số xe tối đa không được để trống")
    @Positive(message = "Số xe tối đa phải là số dương")
    @Max(value = 200, message = "Số xe tối đa là 200")
    @JsonAlias({"max_vehicles", "maxVehicles"})
    @Schema(description = "Số lượng xe tối đa trong đội", example = "5")
    private Integer maxVehicles;

    /**
     * Ngân sách thời gian tối đa cho một lần tối ưu hóa (ms).
     */
    @NotNull(message = "Ngân sách thời gian không được để trống")
    @Positive(message = "Ngân sách thời gian phải là số dương")
    @JsonAlias({"time_budget_ms", "timeBudgetMs"})
    @Schema(description = "Ngân sách thời gian chạy (ms)", example = "3000")
    private Long timeBudgetMs;

    /**
     * Kích thước quần thể: số lượng cá thể trong mỗi thế hệ.
     */
    @NotNull(message = "Kích thước quần thể không được để trống")
    @Min(value = 10, message = "Kích thước quần thể tối thiểu là 10")
    @Max(value = 1000, message = "Kích thước quần thể tối đa là 1000")
    @JsonAlias({"population_size", "populationSize"})
    @Schema(description = "Kích thước quần thể GA", example = "200")
    private Integer populationSize;

    /**
     * Số thế hệ tối đa GA sẽ chạy.
     */
    @NotNull(message = "Số thế hệ không được để trống")
    @Positive(message = "Số thế hệ phải là số dương")
    @JsonAlias({"max_generations", "maxGenerations"})
    @Schema(description = "Số thế hệ tối đa", example = "500")
    private Integer maxGenerations;

    /**
     * Xác suất lai ghép (crossover). Giá trị trong khoảng [0.0, 1.0].
     */
    @NotNull(message = "Tỷ lệ lai ghép không được để trống")
    @DecimalMin(value = "0.0", message = "Tỷ lệ lai ghép không được âm")
    @DecimalMax(value = "1.0", message = "Tỷ lệ lai ghép không được vượt quá 1.0")
    @JsonAlias({"crossover_rate", "crossoverRate"})
    @Schema(description = "Tỷ lệ lai ghép (0.0 - 1.0)", example = "0.85")
    private Double crossoverRate;

    /**
     * Xác suất đột biến (mutation). Giá trị trong khoảng [0.0, 1.0].
     */
    @NotNull(message = "Tỷ lệ đột biến không được để trống")
    @DecimalMin(value = "0.0", message = "Tỷ lệ đột biến không được âm")
    @DecimalMax(value = "1.0", message = "Tỷ lệ đột biến không được vượt quá 1.0")
    @JsonAlias({"mutation_rate", "mutationRate"})
    @Schema(description = "Tỷ lệ đột biến (0.0 - 1.0)", example = "0.10")
    private Double mutationRate;
}
