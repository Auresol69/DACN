package com.example.demo.benchmark;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test – VrpFileParser (Giai đoạn 7)
 *
 * <p>Kiểm tra parser đọc đúng file A-n32-k5.vrp + A-n32-k5.sol:
 * <ul>
 *   <li>DIMENSION=32 → numCustomers=31, maxVehicles=5</li>
 *   <li>CAPACITY=100</li>
 *   <li>BKS=784 (từ file .sol)</li>
 *   <li>Depot tại systemIndex 0: xs[0]=82, ys[0]=76</li>
 *   <li>Customer 1 tại systemIndex 1: xs[1]=96, ys[1]=44, demands[1]=19</li>
 *   <li>Mảng xs/ys/demands có độ dài đúng = DIMENSION = 32</li>
 *   <li>demands[0] = 0 (depot không có nhu cầu)</li>
 *   <li>chromosomeLength = N + K + 1 = 31 + 5 + 1 = 37</li>
 * </ul>
 * </p>
 *
 * <p>Yêu cầu: file A-n32-k5.vrp và A-n32-k5.sol phải tồn tại tại
 * {@code E:/dacn/demo/A/}.</p>
 */
@DisplayName("VrpFileParser – A-n32-k5 instance")
class VrpFileParserTest {

    private static VrpInstance instance;

    private static final Path VRP_PATH =
            Paths.get("E:/dacn/demo/A/A-n32-k5.vrp");

    @BeforeAll
    static void parseFile() throws IOException {
        VrpFileParser parser = new VrpFileParser();
        instance = parser.parse(VRP_PATH);
    }

    // ── Metadata ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("instanceName phải là A-n32-k5")
    void test_instanceName() {
        assertEquals("A-n32-k5", instance.instanceName());
    }

    @Test
    @DisplayName("numCustomers phải là 31 (DIMENSION-1 = 32-1)")
    void test_numCustomers() {
        assertEquals(31, instance.numCustomers());
    }

    @Test
    @DisplayName("maxVehicles phải là 5 (trích từ tên A-n32-k5)")
    void test_maxVehicles() {
        assertEquals(5, instance.maxVehicles());
    }

    @Test
    @DisplayName("capacity phải là 100.0")
    void test_capacity() {
        assertEquals(100.0, instance.capacity(), 1e-9);
    }

    @Test
    @DisplayName("BKS phải là 784 (đọc từ file .sol)")
    void test_bks() {
        assertEquals(784, instance.bks());
    }

    // ── Array sizes ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Mảng xs có độ dài bằng DIMENSION = 32")
    void test_xsLength() {
        assertEquals(32, instance.xs().length,
                "xs[] phải chứa cả depot lẫn 31 khách hàng");
    }

    @Test
    @DisplayName("Mảng ys có độ dài bằng DIMENSION = 32")
    void test_ysLength() {
        assertEquals(32, instance.ys().length);
    }

    @Test
    @DisplayName("Mảng demands có độ dài bằng DIMENSION = 32")
    void test_demandsLength() {
        assertEquals(32, instance.demands().length);
    }

    // ── Depot (systemIndex 0) ─────────────────────────────────────────────────

    @Test
    @DisplayName("Depot xs[0] phải là 82 (VRP node 1 coords)")
    void test_depotX() {
        assertEquals(82.0, instance.xs()[0], 1e-9,
                "VRP node 1 (depot) → systemIndex 0 → x=82");
    }

    @Test
    @DisplayName("Depot ys[0] phải là 76 (VRP node 1 coords)")
    void test_depotY() {
        assertEquals(76.0, instance.ys()[0], 1e-9,
                "VRP node 1 (depot) → systemIndex 0 → y=76");
    }

    @Test
    @DisplayName("demands[0] phải là 0 (depot không có nhu cầu)")
    void test_depotDemand() {
        assertEquals(0.0, instance.demands()[0], 1e-9);
    }

    // ── Customer 1 (VRP node 2 → systemIndex 1) ──────────────────────────────

    @Test
    @DisplayName("xs[1] phải là 96 (VRP node 2 → systemIndex 1)")
    void test_customer1X() {
        assertEquals(96.0, instance.xs()[1], 1e-9,
                "VRP node 2 → systemIndex 1 → x=96");
    }

    @Test
    @DisplayName("ys[1] phải là 44 (VRP node 2 → systemIndex 1)")
    void test_customer1Y() {
        assertEquals(44.0, instance.ys()[1], 1e-9,
                "VRP node 2 → systemIndex 1 → y=44");
    }

    @Test
    @DisplayName("demands[1] phải là 19 (VRP node 2 demand)")
    void test_customer1Demand() {
        // Kiểm tra file: VRP node 2 có demand=19
        assertEquals(19.0, instance.demands()[1], 1e-9);
    }

    // ── Helper method ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("chromosomeLength() phải là N+K+1 = 31+5+1 = 37")
    void test_chromosomeLength() {
        assertEquals(37, instance.chromosomeLength(),
                "Chromosome length L = numCustomers + maxVehicles + 1");
    }

    // ── parseBks standalone ───────────────────────────────────────────────────

    @Test
    @DisplayName("parseBks trực tiếp trả về 784")
    void test_parseBks() throws IOException {
        VrpFileParser parser = new VrpFileParser();
        Path solPath = Paths.get("E:/dacn/demo/A/A-n32-k5.sol");
        int bks = parser.parseBks(solPath);
        assertEquals(784, bks);
    }

    // ── depotX / depotY helpers ───────────────────────────────────────────────

    @Test
    @DisplayName("depotX() helper method trả về 82")
    void test_depotXHelper() {
        assertEquals(82.0, instance.depotX(), 1e-9);
    }

    @Test
    @DisplayName("depotY() helper method trả về 76")
    void test_depotYHelper() {
        assertEquals(76.0, instance.depotY(), 1e-9);
    }
}
