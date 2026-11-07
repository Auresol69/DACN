package com.example.demo.service;

import com.example.demo.dto.response.RouteDetail;
import com.example.demo.model.Individual;
import com.example.demo.model.ProblemContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RouteBuilderService – Chuyển đổi Individual tốt nhất → danh sách RouteDetail
 *
 * Duyệt chromosome[] một lượt O(N), tách tuyến tại mỗi số 0,
 * tính tổng load và khoảng cách mỗi tuyến từ costMatrix (O(1) lookup).
 *
 * Nguồn: Mục 2.2.1 & 2.4.3.2 – "dacn remake.pdf"
 */
@Service
public class RouteBuilderService {

    /**
     * Giải mã chromosome của {@code best} thành danh sách {@link RouteDetail}.
     * Chỉ bao gồm tuyến có ít nhất 1 khách hàng (loại bỏ tuyến rỗng).
     *
     * @param best cá thể tốt nhất trả về từ GA
     * @param ctx  ngữ cảnh bài toán (demands, costMatrix)
     * @return danh sách tuyến đường được sắp xếp theo vehicleId tăng dần
     */
    public List<RouteDetail> buildRoutes(Individual best, ProblemContext ctx) {
        int[]      chr      = best.getChromosome();
        int        len      = best.getActiveLength();
        double[][] cost     = ctx.costMatrix;
        double[]   demands  = ctx.demands;

        List<RouteDetail> routes   = new ArrayList<>();
        List<Integer>     current  = new ArrayList<>();
        int    vehicleId   = 1;
        double routeLoad   = 0.0;
        double routeDist   = 0.0;
        int    prevNode    = 0;      // bắt đầu từ depot

        for (int i = 0; i < len; i++) {
            int gene = chr[i];

            if (gene == 0) {
                // Gặp số 0 → chốt tuyến hiện tại (nếu không rỗng)
                if (!current.isEmpty()) {
                    routeDist += cost[prevNode][0]; // quay về depot
                    routes.add(RouteDetail.builder()
                            .vehicleId(vehicleId++)
                            .routeSequence(new ArrayList<>(current))
                            .totalLoad(routeLoad)
                            .distanceKm(routeDist)
                            .build());
                    // Reset cho tuyến mới
                    current.clear();
                    routeLoad = 0.0;
                    routeDist = 0.0;
                }
                prevNode = 0; // quay về depot
            } else {
                // Gene là customer → thêm vào tuyến hiện tại
                routeDist += cost[prevNode][gene];
                routeLoad += demands[gene];
                current.add(gene);
                prevNode = gene;
            }
        }

        // Xử lý tuyến cuối (nếu chromosome không kết thúc bằng 0 rõ ràng)
        if (!current.isEmpty()) {
            routeDist += cost[prevNode][0];
            routes.add(RouteDetail.builder()
                    .vehicleId(vehicleId)
                    .routeSequence(new ArrayList<>(current))
                    .totalLoad(routeLoad)
                    .distanceKm(routeDist)
                    .build());
        }

        return routes;
    }

    /**
     * Đếm số tuyến không rỗng (xe thực sự được sử dụng).
     */
    public int countActiveRoutes(Individual best, ProblemContext ctx) {
        return buildRoutes(best, ctx).size();
    }
}
