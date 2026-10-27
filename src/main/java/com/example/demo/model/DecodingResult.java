package com.example.demo.model;

/**
 * Kết quả trả về từ {@link com.example.demo.service.SinglePassDecoder}.
 *
 * <p>Record bất biến (immutable) – thread-safe hoàn toàn,
 * mỗi Worker Thread có instance riêng, không cần synchronize.</p>
 *
 * <p>Theo mã giả mục 2.4.1.3 trong tài liệu:</p>
 * <pre>
 *   double total_cost = result.total_distance + result.total_capacity_penalty;
 *   population[i].total_distance    = result.total_distance;
 *   population[i].capacity_penalty  = result.total_capacity_penalty;
 *   population[i].fitness = 1.0 / (total_cost + 1e-6);
 * </pre>
 */
public record DecodingResult(

        /**
         * Tổng quãng đường tất cả các tuyến xe di chuyển (đơn vị km nếu dùng Haversine,
         * hoặc đơn vị Euclid nếu dùng tọa độ CVRPLib).
         */
        double totalDistance,

        /**
         * Tổng hình phạt vi phạm sức chứa:
         * Σ max(0, routeLoad_k - vehicleCapacity) cho mọi tuyến k.
         * = 0 nếu cá thể hoàn toàn hợp lệ.
         */
        double totalCapacityPenalty,

        /**
         * Số xe thực tế được sử dụng (tuyến không rỗng).
         * Dùng để điền vào {@link com.example.demo.dto.response.SolutionSummary#getVehiclesUsed()}.
         */
        int vehiclesUsed

) {
    /** Singleton cho trường hợp chromosome hoàn toàn rỗng / không hợp lệ. */
    public static final DecodingResult EMPTY = new DecodingResult(0.0, Double.MAX_VALUE, 0);
}
