package com.example.demo.service;

import com.example.demo.dto.request.CustomerDto;
import com.example.demo.dto.request.LocationDto;
import com.example.demo.dto.request.OptimizationRequest;
import com.example.demo.model.ProblemContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Pre-computes the cost matrix and demand array from an {@link OptimizationRequest}.
 *
 * <h2>Nhiệm vụ</h2>
 * <p>Đây là bước tiền xử lý quan trọng nhất trước khi GA chạy.
 * Thực hiện duy nhất <b>một lần</b> để tính Ma trận chi phí C_{ij}
 * kích thước (N+1)×(N+1), rồi lưu vào bộ nhớ RAM tĩnh để GA lookup O(1).</p>
 *
 * <h2>Công thức khoảng cách</h2>
 * <ul>
 *   <li><b>Haversine</b> (mặc định): dùng cho tọa độ GPS thực (lat/lng), kết quả = km.
 *       Áp dụng khi triển khai hệ thống logistics thực tế.</li>
 *   <li><b>Euclidean</b>: dùng cho benchmark CVRPLib (tọa độ phẳng 2D).
 *       Có thể kích hoạt bằng cách set {@code useHaversine = false}.</li>
 * </ul>
 *
 * <h2>Indexing Convention</h2>
 * <pre>
 *   Node 0   → Depot  (LocationDto)
 *   Node i   → Customer với CustomerDto.id == i  (1-indexed)
 * </pre>
 */
@Service
public class DistanceMatrixService {

    private static final Logger log = LoggerFactory.getLogger(DistanceMatrixService.class);

    /** Bán kính Trái Đất trung bình (km) – dùng cho công thức Haversine. */
    private static final double EARTH_RADIUS_KM = 6371.0;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xây dựng {@link ProblemContext} từ request:
     * tính ma trận chi phí và mảng nhu cầu, đóng gói vào một object bất biến.
     *
     * <p>Độ phức tạp: O(N²) – chỉ gọi một lần trước khi GA khởi động.</p>
     *
     * @param request  dữ liệu đầu vào từ API (đã validate)
     * @return {@link ProblemContext} chứa toàn bộ dữ liệu tĩnh bài toán
     */
    public ProblemContext build(OptimizationRequest request) {
        final List<CustomerDto> customers = request.getCustomers();
        final int N = customers.size();
        final int K = request.getConfig().getMaxVehicles();
        final double capacity = request.getConfig().getVehicleCapacity();

        log.info("[DistanceMatrix] Building context: N={} customers, K={} vehicles, C_cap={}",
                 N, K, capacity);

        // ── 1. Thu thập tọa độ tất cả các node (0 = depot, 1..N = customers) ──
        final double[] lats = new double[N + 1];
        final double[] lngs = new double[N + 1];
        final double[] demands = new double[N + 1];

        LocationDto depot = request.getDepot();
        lats[0] = depot.getLatitude();
        lngs[0] = depot.getLongitude();
        demands[0] = 0.0; // depot không có nhu cầu

        for (CustomerDto c : customers) {
            int idx = c.getId(); // id là 1-indexed, khớp với chỉ số chromosome gene
            if (idx < 1 || idx > N) {
                throw new IllegalArgumentException(
                    "Customer ID phải trong khoảng [1, N]. Nhận: " + idx + " (N=" + N + ")");
            }
            lats[idx]    = c.getLat();
            lngs[idx]    = c.getLng();
            demands[idx] = c.getDemand();
        }

        // ── 2. Tính Ma trận chi phí (N+1)×(N+1) — O(N²) ──────────────────────
        final double[][] costMatrix = new double[N + 1][N + 1];

        for (int i = 0; i <= N; i++) {
            costMatrix[i][i] = 0.0; // khoảng cách tự đến mình = 0
            for (int j = i + 1; j <= N; j++) {
                double dist = haversineKm(lats[i], lngs[i], lats[j], lngs[j]);
                costMatrix[i][j] = dist;
                costMatrix[j][i] = dist; // Ma trận đối xứng (symmetric)
            }
        }

        log.info("[DistanceMatrix] Cost matrix built: {}x{}, chromosome length L={}",
                 N + 1, N + 1, N + K + 1);

        return new ProblemContext(N, K, capacity, costMatrix, demands);
    }

    /**
     * Phiên bản tính Euclidean – dùng cho benchmark CVRPLib (tọa độ phẳng 2D).
     * Không nhân với bán kính Trái Đất, kết quả là đơn vị tọa độ thuần túy.
     *
     * @param request  dữ liệu đầu vào (lat/lng hiểu là tọa độ phẳng x/y)
     * @return {@link ProblemContext} với ma trận khoảng cách Euclidean
     */
    public ProblemContext buildEuclidean(OptimizationRequest request) {
        final List<CustomerDto> customers = request.getCustomers();
        final int N = customers.size();
        final int K = request.getConfig().getMaxVehicles();
        final double capacity = request.getConfig().getVehicleCapacity();

        log.info("[DistanceMatrix-Euclid] Building context: N={} customers", N);

        final double[] xs     = new double[N + 1];
        final double[] ys     = new double[N + 1];
        final double[] demands = new double[N + 1];

        LocationDto depot = request.getDepot();
        xs[0] = depot.getLatitude();  // lat dùng như tọa độ x
        ys[0] = depot.getLongitude(); // lng dùng như tọa độ y
        demands[0] = 0.0;

        for (CustomerDto c : customers) {
            int idx = c.getId();
            xs[idx]      = c.getLat();
            ys[idx]      = c.getLng();
            demands[idx] = c.getDemand();
        }

        final double[][] costMatrix = new double[N + 1][N + 1];
        for (int i = 0; i <= N; i++) {
            for (int j = i + 1; j <= N; j++) {
                double dx = xs[i] - xs[j];
                double dy = ys[i] - ys[j];
                double dist = Math.sqrt(dx * dx + dy * dy);
                costMatrix[i][j] = dist;
                costMatrix[j][i] = dist;
            }
        }

        return new ProblemContext(N, K, capacity, costMatrix, demands);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private – Haversine Formula
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tính khoảng cách Haversine giữa hai điểm địa lý trên mặt cầu Trái Đất.
     *
     * <p>Công thức:</p>
     * <pre>
     *   a = sin²(Δlat/2) + cos(lat1)·cos(lat2)·sin²(Δlng/2)
     *   c = 2·atan2(√a, √(1−a))
     *   d = R·c   (km)
     * </pre>
     *
     * @param lat1 vĩ độ điểm 1 (độ)
     * @param lng1 kinh độ điểm 1 (độ)
     * @param lat2 vĩ độ điểm 2 (độ)
     * @param lng2 kinh độ điểm 2 (độ)
     * @return khoảng cách (km)
     */
    private static double haversineKm(double lat1, double lng1,
                                      double lat2, double lng2) {
        final double dLat = Math.toRadians(lat2 - lat1);
        final double dLng = Math.toRadians(lng2 - lng1);
        final double rLat1 = Math.toRadians(lat1);
        final double rLat2 = Math.toRadians(lat2);

        final double sinDLat = Math.sin(dLat / 2);
        final double sinDLng = Math.sin(dLng / 2);

        final double a = sinDLat * sinDLat
                       + Math.cos(rLat1) * Math.cos(rLat2) * sinDLng * sinDLng;

        return 2.0 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
    }
}
