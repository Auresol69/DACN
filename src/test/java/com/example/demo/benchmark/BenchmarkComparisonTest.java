package com.example.demo.benchmark;

import com.example.demo.service.DistanceMatrixService;
import com.example.demo.service.GaOptimizationEngine;
import com.example.demo.service.OrToolsSolverService;
import com.example.demo.service.SinglePassDecoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BenchmarkComparisonRunner – GA vs OR-Tools Comparison Tests")
class BenchmarkComparisonTest {

    private BenchmarkComparisonRunner comparisonRunner;

    @BeforeEach
    void setUp() {
        VrpFileParser parser = new VrpFileParser();
        SinglePassDecoder decoder = new SinglePassDecoder();
        com.example.demo.service.GeneticOperators operators = new com.example.demo.service.GeneticOperators();
        GaOptimizationEngine gaEngine = new GaOptimizationEngine(decoder, operators);
        DistanceMatrixService distanceMatrixService = new DistanceMatrixService();
        OrToolsSolverService orToolsSolver = new OrToolsSolverService(distanceMatrixService);

        comparisonRunner = new BenchmarkComparisonRunner(parser, gaEngine, orToolsSolver);
    }

    @Test
    @DisplayName("Đối sánh GA vs OR-Tools trên instance Augerat A-n32-k5 (3000ms)")
    void testCompareSingle_An32k5() {
        Path vrpPath = Paths.get("E:/dacn/demo/A/A-n32-k5.vrp");
        BenchmarkComparisonResult result = comparisonRunner.compareSingle(vrpPath, 3000);

        assertNotNull(result);
        assertEquals("A-n32-k5", result.instanceName());
        assertEquals(31, result.numCustomers());
        assertEquals(5, result.maxVehicles());
        assertEquals(784, result.bks());

        // Cả 2 thuật toán đều phải tìm được nghiệm hợp lệ (SUCCESS)
        assertEquals("SUCCESS", result.gaStatus());
        assertEquals("SUCCESS", result.ortStatus());

        assertTrue(result.gaFound() > 0);
        assertTrue(result.ortFound() > 0);
        assertNotEquals("NONE", result.winner());

        System.out.printf("Test Result: GA=%d (gap=%+.2f%%) vs ORT=%d (gap=%+.2f%%) | Winner=%s%n",
                result.gaFound(), result.gaGapPercent(),
                result.ortFound(), result.ortGapPercent(),
                result.winner());
    }

    @Test
    @DisplayName("File không tồn tại: trả về kết quả lỗi ERROR")
    void testCompareSingle_FileNotFound() {
        Path nonExistent = Paths.get("E:/dacn/demo/A/NON_EXISTENT.vrp");
        BenchmarkComparisonResult result = comparisonRunner.compareSingle(nonExistent, 1000);

        assertNotNull(result);
        assertTrue(result.gaStatus().contains("ERROR"));
        assertTrue(result.ortStatus().contains("ERROR"));
        assertEquals("NONE", result.winner());
    }
}
