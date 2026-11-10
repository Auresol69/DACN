package com.example.demo.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * VrpFileParser – Giai đoạn 7
 *
 * <p>Đọc và parse file định dạng CVRPLib (.vrp + .sol) theo chuẩn TSPLIB95.</p>
 *
 * <h2>Quy tắc ánh xạ chỉ số</h2>
 * <pre>
 *   VRP file node 1          → depot   → systemIndex 0
 *   VRP file node i (i >= 2) → customer → systemIndex i-1
 *   .sol file customer id j  → systemIndex j  (sol dùng node id VRP = customer relative id)
 * </pre>
 *
 * <h2>Các section hỗ trợ</h2>
 * <ul>
 *   <li>NAME, DIMENSION, CAPACITY (header)</li>
 *   <li>NODE_COORD_SECTION – tọa độ (x, y)</li>
 *   <li>DEMAND_SECTION – nhu cầu hàng hóa</li>
 *   <li>DEPOT_SECTION – xác nhận depot</li>
 *   <li>EOF</li>
 * </ul>
 */
@Service
public class VrpFileParser {

    private static final Logger log = LoggerFactory.getLogger(VrpFileParser.class);

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Parse file .vrp và (tùy chọn) file .sol cùng thư mục.
     *
     * @param vrpPath đường dẫn tuyệt đối đến file .vrp
     * @return {@link VrpInstance} đã được parse đầy đủ
     * @throws IOException nếu file không tồn tại hoặc sai định dạng
     */
    public VrpInstance parse(Path vrpPath) throws IOException {
        log.debug("Parsing VRP file: {}", vrpPath.getFileName());

        // ── 1. Parse file .vrp ────────────────────────────────────────────────
        ParsedVrp vrp = parseVrpFile(vrpPath);

        // ── 2. Thử parse file .sol cùng thư mục ──────────────────────────────
        int bks = Integer.MAX_VALUE;
        String filename = vrpPath.getFileName().toString();
        String baseName = filename.endsWith(".vrp")
                ? filename.substring(0, filename.length() - 4)
                : filename;
        Path solPath = vrpPath.getParent().resolve(baseName + ".sol");
        if (Files.exists(solPath)) {
            bks = parseBks(solPath);
        }

        return new VrpInstance(
                baseName,
                vrp.numCustomers,
                vrp.maxVehicles,
                vrp.capacity,
                vrp.xs,
                vrp.ys,
                vrp.demands,
                bks
        );
    }

    // =========================================================================
    // Parse .vrp file
    // =========================================================================

    private ParsedVrp parseVrpFile(Path path) throws IOException {
        ParsedVrp result = new ParsedVrp();
        Section section  = Section.HEADER;

        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.equals("EOF")) continue;

                // ── Chuyển section ──────────────────────────────────────────
                if (line.startsWith("NODE_COORD_SECTION")) {
                    section = Section.NODE_COORD;
                    continue;
                }
                if (line.startsWith("DEMAND_SECTION")) {
                    section = Section.DEMAND;
                    continue;
                }
                if (line.startsWith("DEPOT_SECTION")) {
                    section = Section.DEPOT;
                    continue;
                }

                switch (section) {
                    case HEADER  -> parseHeader(line, result);
                    case NODE_COORD -> parseCoord(line, result);
                    case DEMAND  -> parseDemand(line, result);
                    case DEPOT   -> { /* không cần xử lý – depot luôn là node 1 */ }
                }
            }
        }

        if (result.dimension <= 0) {
            throw new IOException("File VRP thiếu DIMENSION: " + path);
        }

        return result;
    }

    private void parseHeader(String line, ParsedVrp r) {
        // Format: "KEY : VALUE"
        int colon = line.indexOf(':');
        if (colon < 0) return;

        String key   = line.substring(0, colon).trim().toUpperCase();
        String value = line.substring(colon + 1).trim();

        switch (key) {
            case "NAME"      -> r.name = value;
            case "DIMENSION" -> {
                r.dimension    = Integer.parseInt(value);
                r.numCustomers = r.dimension - 1;  // node 1 = depot
                // Trích K từ tên instance (ví dụ: A-n32-k5 → k=5)
                r.maxVehicles = extractKFromName(r.name);
                // Khởi tạo mảng sau khi biết DIMENSION
                r.xs      = new double[r.dimension];
                r.ys      = new double[r.dimension];
                r.demands = new double[r.dimension];
            }
            case "CAPACITY"  -> r.capacity = Double.parseDouble(value);
        }
    }

    private void parseCoord(String line, ParsedVrp r) {
        // Format: "nodeId  x  y"
        String[] parts = line.split("\\s+");
        if (parts.length < 3) return;

        int    nodeId = Integer.parseInt(parts[0]);
        double x      = Double.parseDouble(parts[1]);
        double y      = Double.parseDouble(parts[2]);

        // VRP node 1 = depot → systemIndex 0
        // VRP node i (i>=2) → systemIndex i-1
        int idx = nodeId - 1;
        if (idx >= 0 && idx < r.dimension) {
            r.xs[idx] = x;
            r.ys[idx] = y;
        }
    }

    private void parseDemand(String line, ParsedVrp r) {
        // Format: "nodeId  demand"
        String[] parts = line.split("\\s+");
        if (parts.length < 2) return;

        int    nodeId = Integer.parseInt(parts[0]);
        double demand = Double.parseDouble(parts[1]);

        int idx = nodeId - 1;
        if (idx >= 0 && idx < r.dimension) {
            r.demands[idx] = demand;
        }
    }

    // =========================================================================
    // Parse .sol file → BKS
    // =========================================================================

    /**
     * Đọc dòng cuối "Cost XXX" từ file .sol.
     */
    int parseBks(Path solPath) throws IOException {
        int bks = Integer.MAX_VALUE;
        try (BufferedReader br = Files.newBufferedReader(solPath)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("Cost")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        bks = Integer.parseInt(parts[1]);
                    }
                }
            }
        }
        return bks;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Trích số K từ tên instance.
     * Ví dụ: "A-n32-k5" → 5, "A-n63-k10" → 10.
     */
    private int extractKFromName(String name) {
        if (name == null) return 1;
        int kIdx = name.lastIndexOf("-k");
        if (kIdx < 0) return 1;
        try {
            return Integer.parseInt(name.substring(kIdx + 2).replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private enum Section { HEADER, NODE_COORD, DEMAND, DEPOT }

    /** Mutable container dùng trong quá trình parse. */
    private static class ParsedVrp {
        String   name;
        int      dimension    = 0;
        int      numCustomers = 0;
        int      maxVehicles  = 1;
        double   capacity     = 0.0;
        double[] xs;
        double[] ys;
        double[] demands;
    }
}
