# Optimization Backend Engine – CVRP Solver (GA)

> **Đề tài:** Nghiên cứu Thuật toán Di truyền giải Bài toán Định tuyến Xe trong Giao vận Đô thị và Triển khai Lõi Tối ưu hóa Backend (Optimization Backend Engine)
> **Công nghệ:** Java 17 · Spring Boot 4.1.2 · Lombok · Springdoc OpenAPI · Google OR-Tools

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Kiến trúc hệ thống](#2-kiến-trúc-hệ-thống)
3. [Cấu trúc dự án](#3-cấu-trúc-dự-án)
4. [API Specification](#4-api-specification)
5. [Dataset CVRPLib – Bộ Augerat (A)](#5-dataset-cvrplib--bộ-augerat-a)
6. [Tiến độ triển khai](#6-tiến-độ-triển-khai)
7. [Hướng dẫn chạy](#7-hướng-dẫn-chạy)

---

## 1. Tổng quan

Hệ thống giải bài toán **Capacitated Vehicle Routing Problem (CVRP)** bằng **Thuật toán Di truyền (GA)** song song, đóng gói thành một **RESTful API Microservice** sẵn sàng tích hợp vào hệ thống logistics thực tế.

### Mục tiêu kỹ thuật

| Chỉ số | Mục tiêu |
|---|---|
| Chất lượng lời giải (%Gap vs BKS) | < 2% |
| Thời gian phản hồi API | < 3 giây / lô đơn hàng |
| GC Pauses | = 0 ms (Object Pooling) |
| Tăng tốc đa luồng (Speedup) | ≥ N_cores × 0.85 (Amdahl) |

### Vấn đề cốt lõi cần giải quyết

```
Dòng đơn hàng liên tục (dynamic)
        ↓ Batching Window (30-60s)
Lô đơn hàng tĩnh tương đối
        ↓ Optimization Backend Engine (GA, < 3s)
Phương án phân công lộ trình tối ưu
        ↓ Driver Dispatch
Ứng dụng tài xế
```

---

## 2. Kiến trúc hệ thống

### Mô hình Master-Slave Parallel GA

```
┌─────────────────────────────────────────────────────┐
│              MASTER THREAD                          │
│  1. Khởi tạo Population P_t trên RAM chia sẻ       │
│  2. k-Tournament Selection → chọn cặp Bố/Mẹ        │
│  3. Chia Population thành N_threads Chunks          │
└──────────────────────┬──────────────────────────────┘
                       │ ExecutorService (FixedThreadPool)
          ┌────────────┼────────────┐
          ↓            ↓            ↓
   ┌──────────┐ ┌──────────┐ ┌──────────┐
   │ WORKER 1 │ │ WORKER 2 │ │ WORKER N │
   │ Decode   │ │ Decode   │ │ Decode   │
   │ O(N)     │ │ O(N)     │ │ O(N)     │
   │ Fitness  │ │ Fitness  │ │ Fitness  │
   └────┬─────┘ └────┬─────┘ └────┬─────┘
        └────────────┼────────────┘
                     ↓ CountDownLatch / Phaser
┌─────────────────────────────────────────────────────┐
│  MASTER: Cập nhật Fitness, Tái sắp xếp P_{t+1}     │
└─────────────────────────────────────────────────────┘
```

### Mã hóa Chromosome – Mảng 1D phân tách số 0

```
chromosome = [ 0, 3, 5, 1, 0, 8, 2, 0, 4, 7, 6, 0 ]
              ^  └─ Route 1 ─┘ └─ Route 2 ┘ └──Route 3──┘  ^
            depot                                         depot

L = N + K + 1  (N khách hàng + K+1 số 0 phân tách)
```

### Object Pooling – Triệt tiêu GC

```
Boot Phase → pre-allocate poolSize × Individual (chromosome[] cố định)
GA Loop   → acquire() từ pool (LIFO ArrayDeque) → dùng → release()
GC Pauses = 0 ms  (zero heap allocation trong vòng lặp)
```

---

## 3. Cấu trúc dự án

```
src/main/java/com/example/demo/
├── config/
│   └── AsyncConfig.java             ← Cấu hình gaTaskExecutor (Thread pool riêng cho GA)
├── controller/
│   └── OptimizationController.java  ← REST API POST /api/v1/optimize/cvrp (Async CompletableFuture)
├── dto/
│   ├── request/
│   │   ├── GaConfig.java            ← Tham số GA (vehicleCapacity, maxVehicles, ...)
│   │   ├── LocationDto.java         ← Tọa độ depot (id:int, latitude, longitude)
│   │   ├── CustomerDto.java         ← Khách hàng (id:int, demand:double, lat, lng)
│   │   └── OptimizationRequest.java ← DTO đầu vào chính
│   └── response/
│       ├── SolutionSummary.java     ← Tổng quãng đường, số xe, %Gap vs BKS
│       ├── RouteDetail.java         ← Chi tiết từng tuyến xe
│       └── OptimizationResponse.java← DTO đầu ra chính
├── model/
│   ├── Individual.java              ← Nhiễm sắc thể GA + Object Pool API
│   ├── IndividualPool.java          ← Pre-allocated LIFO pool (ArrayDeque)
│   ├── ProblemContext.java          ← costMatrix[][], demands[], vehicleCapacity
│   └── DecodingResult.java          ← record(totalDistance, penalty, vehiclesUsed)
└── service/
    ├── DistanceMatrixService.java   ← Pre-compute O(N²): Haversine / Euclidean (nint rounding)
    ├── SinglePassDecoder.java       ← Giải mã O(N) theo mục 2.2.2.1 tài liệu
    ├── GeneticOperators.java        ← Tournament Selection, Modified OX, Mutation (Swap/Shift/Inversion)
    ├── GaOptimizationEngine.java    ← Master-Slave Parallel GA Engine, Dynamic Penalty, Time Budget
    └── RouteBuilderService.java     ← Giải mã Individual thành danh sách RouteDetail response
```

```
src/test/java/com/example/demo/
└── service/
    ├── SinglePassDecoderTest.java   ← Test tính khoảng cách, số xe, capacity penalty (8 tests)
    ├── ModifiedOXCrossoverTest.java ← Test bảo toàn N customers, K+1 depots, no duplicates (25 tests)
    └── GaTimeBudgetTest.java        ← Test ngắt time_budget_ms ~1s khi max_generations=1,000,000 (3 tests)
```

---

## 4. API Specification

### `POST /api/v1/optimize/cvrp`

#### Request Body

```json
{
  "batch_id": "BATCH_20261019_001",
  "config": {
    "vehicle_capacity": 100.0,
    "max_vehicles": 5,
    "time_budget_ms": 3000,
    "population_size": 200,
    "max_generations": 500,
    "crossover_rate": 0.85,
    "mutation_rate": 0.10
  },
  "depot": {
    "id": 0,
    "latitude": 10.762622,
    "longitude": 106.660172
  },
  "customers": [
    { "id": 1, "demand": 19.0, "lat": 10.77123, "lng": 106.6721 },
    { "id": 2, "demand": 30.0, "lat": 10.75412, "lng": 106.6412 },
    { "id": 3, "demand": 16.0, "lat": 10.78119, "lng": 106.6915 }
  ]
}
```

#### Response Body

```json
{
  "batch_id": "BATCH_20261019_001",
  "status": "SUCCESS",
  "execution_time_ms": 1420,
  "summary": {
    "total_distance_km": 42.85,
    "vehicles_used": 3,
    "gap_to_bks_percent": 0.42
  },
  "routes": [
    {
      "vehicle_id": 1,
      "route_sequence": [3, 5, 1],
      "total_load": 35.0,
      "distance_km": 14.2
    },
    {
      "vehicle_id": 2,
      "route_sequence": [8, 2],
      "total_load": 30.0,
      "distance_km": 11.5
    }
  ]
}
```

#### Status Codes

| Status | Ý nghĩa |
|---|---|
| `SUCCESS` | GA kết thúc, lời giải hợp lệ |
| `FAILED` | Lỗi xử lý nội bộ |
| `INFEASIBLE` | Không tìm được lời giải (Σdemand > K × C_cap) |

#### Validation Rules

| Field | Ràng buộc |
|---|---|
| `config.vehicle_capacity` | `> 0` |
| `config.max_vehicles` | `1 ≤ K ≤ 200` |
| `config.population_size` | `10 ≤ P ≤ 1000` |
| `config.crossover_rate` | `[0.0, 1.0]` |
| `config.mutation_rate` | `[0.0, 1.0]` |
| `customers` | `size ≥ 1`, `size ≤ 1000` |
| `customer.id` | `≥ 1` (0 dành cho depot) |
| `customer.demand` | `> 0` |

---

## 5. Dataset CVRPLib – Bộ Augerat (A)

Thư mục `E:\dacn\demo\A` chứa **27 instances** chuẩn quốc tế từ bộ **Augerat et al. (1995)**.

### Đặc điểm chung

| Thuộc tính | Giá trị |
|---|---|
| Loại khoảng cách | `EUC_2D` (Euclidean 2D phẳng) |
| Sức chứa xe (`CAPACITY`) | **100** (tất cả instances) |
| Format | `.vrp` (dữ liệu) + `.sol` (BKS reference) |
| Chỉ số depot | Node `1` trong file (ánh xạ sang index `0` khi load) |

> **Lưu ý quan trọng:** Bộ dataset này dùng tọa độ **Euclidean 2D** (không phải GPS).  
> Khi benchmark, phải dùng `DistanceMatrixService.buildEuclidean()`, **không** dùng `build()` (Haversine).

### Bảng instance – BKS (Best Known Solution)

| Instance | N (khách hàng) | K (xe) | BKS |
|---|---:|---:|---:|
| A-n32-k5 | 31 | 5 | **784** |
| A-n33-k5 | 32 | 5 | 661 |
| A-n33-k6 | 32 | 6 | 742 |
| A-n34-k5 | 33 | 5 | 778 |
| A-n36-k5 | 35 | 5 | 799 |
| A-n37-k5 | 36 | 5 | 669 |
| A-n37-k6 | 36 | 6 | 949 |
| A-n38-k5 | 37 | 5 | 730 |
| A-n39-k5 | 38 | 5 | 822 |
| A-n39-k6 | 38 | 6 | 831 |
| A-n44-k6 | 43 | 6 | 937 |
| A-n45-k6 | 44 | 6 | 944 |
| A-n45-k7 | 44 | 7 | 1146 |
| A-n46-k7 | 45 | 7 | 914 |
| A-n48-k7 | 47 | 7 | 1073 |
| A-n53-k7 | 52 | 7 | 1010 |
| A-n54-k7 | 53 | 7 | 1167 |
| A-n55-k9 | 54 | 9 | 1073 |
| A-n60-k9 | 59 | 9 | 1354 |
| A-n61-k9 | 60 | 9 | 1034 |
| A-n62-k8 | 61 | 8 | 1288 |
| A-n63-k10 | 62 | 10 | 1314 |
| A-n63-k9 | 62 | 9 | 1616 |
| A-n64-k9 | 63 | 9 | 1401 |
| A-n65-k9 | 64 | 9 | 1174 |
| A-n69-k9 | 68 | 9 | 1159 |
| A-n80-k10 | 79 | 10 | **1763** |

### Phân nhóm theo độ khó

```
Nhỏ  (N ≤ 39): A-n32 → A-n39  (10 instances) — Khởi động, kiểm tra tính đúng đắn
Trung (N ≤ 55): A-n44 → A-n55  (8 instances)  — Benchmark chính
Lớn  (N ≤ 80): A-n60 → A-n80  (9 instances)  — Kiểm tra hiệu năng đa luồng
```

### Format file `.vrp`

```
NAME : A-n32-k5
COMMENT : (Augerat et al, No of trucks: 5, Optimal value: 784)
TYPE : CVRP
DIMENSION : 32          ← tổng nodes = 1 depot + 31 khách hàng
EDGE_WEIGHT_TYPE : EUC_2D
CAPACITY : 100
NODE_COORD_SECTION
 1 82 76                ← Node 1 = DEPOT (x=82, y=76)
 2 96 44                ← Node 2 = Customer 1
 ...
DEMAND_SECTION
1 0                     ← Depot demand = 0
2 19                    ← Customer demand
...
DEPOT_SECTION
 1                      ← Depot là node 1
 -1
EOF
```

### Format file `.sol`

```
Route #1: 21 31 19 17 13 7 26    ← node IDs (1-indexed, depot=1 ngầm ở đầu/cuối)
Route #2: 12 1 16 30
...
Cost 784                          ← Tổng khoảng cách Euclidean (BKS)
```

---

## 6. Tiến độ triển khai

### ✅ Giai đoạn 1 – Models & DTOs

| Class | Mô tả |
|---|---|
| `GaConfig` | Tham số GA: `vehicleCapacity`, `maxVehicles`, `timeBudgetMs`, `populationSize`, `maxGenerations`, `crossoverRate`, `mutationRate` |
| `LocationDto` | Depot: `id:int`, `latitude`, `longitude` |
| `CustomerDto` | Khách hàng: `id:int` (1-indexed), `demand:double`, `lat`, `lng` |
| `OptimizationRequest` | DTO input với `@Valid` đầy đủ |
| `SolutionSummary` | `totalDistanceKm`, `vehiclesUsed`, `gapToBksPercent` |
| `RouteDetail` | `vehicleId:int`, `routeSequence:List<Integer>`, `totalLoad:double`, `distanceKm` |
| `OptimizationResponse` | DTO output với `status ∈ {SUCCESS, FAILED, INFEASIBLE}` |
| `Individual` | Chromosome GA với `reset()`, `copyFrom()`, `compareTo()` |
| `IndividualPool` | LIFO `ArrayDeque` – pre-allocated, zero GC trong GA loop |

### ✅ Giai đoạn 2 – Pre-computation & Single-Pass Decoder

| Class | Mô tả |
|---|---|
| `ProblemContext` | Bundle dữ liệu tĩnh: `costMatrix[N+1][N+1]`, `demands[N+1]`, `chromosomeLength = N+K+1` |
| `DistanceMatrixService` | `build()` → Haversine (GPS, km); `buildEuclidean()` → CVRPLib benchmark |
| `DecodingResult` | Java record: `(totalDistance, totalCapacityPenalty, vehiclesUsed)` |
| `SinglePassDecoder` | O(N) linear scan; `decode()`, `decodeAndSetFitness()` |

**Thuật toán Single-Pass (O(N)):**
```
prevNode = 0, routeLoad = 0, routeDistance = 0
for gene in chromosome[0..L-1]:
    if gene == 0:                              // vách ngăn depot
        if prevNode != 0:                      // tuyến không rỗng
            routeDistance += cost[prevNode][0] // về depot
            totalDistance += routeDistance
            if routeLoad > vehicleCapacity:
                penalty += (routeLoad - vehicleCapacity)
            vehiclesUsed++
        reset(prevNode=0, routeLoad=0, routeDistance=0)
    else:                                      // khách hàng
        routeDistance += cost[prevNode][gene]
        routeLoad     += demands[gene]
        prevNode       = gene
```

### ✅ Giai đoạn 3 – Genetic Operators

| Toán tử | Phương thức | Chi tiết triển khai |
|---|---|---|
| **Selection** | `tournamentSelect()` | Binary Tournament ($k=2$), chọn cá thể có fitness cao hơn (Maximize $1/\text{cost}$) |
| **Crossover** | `modifiedOX()` | Modified Order Crossover (OX) sao chép đoạn `[cut1..cut2]`, điền xoay vòng từ P2, bảo toàn đúng $N$ customers và $K+1$ số 0 |
| **Mutation** | `mutate()` | Chọn ngẫu nhiên 1 trong 3: **Swap** (đổi chỗ 2 customer), **Relocate** (dời 1 customer), **Inversion** (đảo ngược đoạn `[i..j]` trong `[1..L-2]`) |

### ✅ Giai đoạn 4 – Parallel GA Optimization Engine

| Thành phần | Phương thức | Chi tiết |
|---|---|---|
| **Population Initializer** | `initializePopulation()` | 70% ngẫu nhiên (Fisher-Yates) + 30% heuristic Nearest-Neighbor có nhiễu (perturbation) |
| **Parallel Evaluation** | `evaluatePopulationParallel()` | Chia chunks trên `FixedThreadPool(N_CPU)`, gọi `SinglePassDecoder`, `costMatrix` read-only |
| **Fitness Formulation** | Maximization | $\text{Fitness} = 1.0 / (\text{TotalDistance} + \alpha(t) \times \text{CapacityPenalty})$ (mục 2.4.1.3) |
| **Dynamic Penalty** | `computeAlpha()` | $\alpha(t) = \alpha_0 \times (1 + t / T)^\gamma$ ($\alpha_0=100, \gamma=2$) |
| **Generational Loop** | `runOptimization()` | Elitism ($E=1$), swap thế hệ zero-allocation, kiểm tra `time_budget_ms` dừng khẩn cấp |

### ✅ Giai đoạn 5 – REST Controller & Async Processing

| Thành phần | Endpoint / Class | Chi tiết |
|---|---|---|
| **Async Config** | `AsyncConfig` | Thread pool `gaTaskExecutor` riêng biệt (core: $N_{\text{CPU}}$, max: $2N_{\text{CPU}}$, queue: 50) |
| **REST Controller** | `POST /api/v1/optimize/cvrp` | `@Async("gaTaskExecutor")`, `CompletableFuture`, non-blocking Tomcat I/O |
| **Feasibility Pre-check** | `checkFeasibility()` | Kiểm tra sớm: $\sum \text{demand} \le K \times C_{\text{cap}}$, $K \ge 1$, $T_{\text{budget}} \ge 100\text{ms}$ |
| **Route Builder** | `RouteBuilderService` | Giải mã `Individual` thành danh sách `RouteDetail` ($O(N)$) |

### ✅ Giai đoạn 6 – Unit Testing (JUnit 5 + AssertJ)

Bộ kiểm thử gồm **37 test cases** bao phủ toàn diện các luồng logic trọng yếu:

| Test Suite | File | Số test | Kết quả | Mô tả kiểm thử |
|---|---|---:|:---:|---|
| **SinglePassDecoderTest** | `SinglePassDecoderTest.java` | 8 | ✅ PASS | Kiểm tra tính đúng quãng đường, nhận diện đúng 2 xe với `[0, 1, 2, 0, 3, 0]`, tuyến rỗng `[0, 0]`, và phạt vi phạm tải trọng chính xác |
| **ModifiedOXCrossoverTest** | `ModifiedOXCrossoverTest.java` | 25 | ✅ PASS | Kiểm tra tính bảo toàn không mất customer, không trùng lặp, bảo toàn đúng $K+1$ số 0 depot, 20 lần ngẫu nhiên đa dạng điểm cắt |
| **GaTimeBudgetTest** | `GaTimeBudgetTest.java` | 3 | ✅ PASS | Chạy với `max_generations = 1,000,000`, khẳng định engine dừng chuẩn xác sau $\sim 1000\text{ms}$ (vượt qua 20,552 thế hệ) và $\sim 500\text{ms}$ |
| **DemoApplicationTests** | `DemoApplicationTests.java` | 1 | ✅ PASS | Spring Boot Context loading test |

### 🔲 Giai đoạn 7 – VRP File Parser & Benchmarking Augerat (A) *(tiếp theo)*

- `VrpFileParser` – đọc file `.vrp` / `.sol` từ CVRPLib
- `BenchmarkRunner` – chạy tự động 27 instances Augerat, đo thời gian thực thi và tính `%Gap` so với BKS

### 🔲 Giai đoạn 8 – OR-Tools Baseline Integration *(tùy chọn)*

- Tích hợp Google OR-Tools làm baseline đối sánh theo tài liệu đề tài

---

## 7. Hướng dẫn chạy

### Yêu cầu

- Java 17+
- Maven (tích hợp sẵn `mvnw`)

### Build & Run

```bash
# 1. Biên dịch mã nguồn
./mvnw compile

# 2. Chạy toàn bộ 37 Unit Tests
./mvnw test

# 3. Khởi chạy ứng dụng Spring Boot
./mvnw spring-boot:run
```

### Kiểm thử API qua cURL

Sau khi server khởi động (port 8080), có thể gửi request kiểm thử:

```bash
curl -X POST http://localhost:8080/api/v1/optimize/cvrp \
  -H "Content-Type: application/json" \
  -d '{
    "batch_id": "TEST_001",
    "config": {
      "vehicle_capacity": 100.0,
      "max_vehicles": 3,
      "time_budget_ms": 1000,
      "population_size": 100,
      "max_generations": 500,
      "crossover_rate": 0.85,
      "mutation_rate": 0.10
    },
    "depot": {
      "id": 0,
      "latitude": 10.762622,
      "longitude": 106.660172
    },
    "customers": [
      { "id": 1, "demand": 19.0, "lat": 10.77123, "lng": 106.6721 },
      { "id": 2, "demand": 30.0, "lat": 10.75412, "lng": 106.6412 },
      { "id": 3, "demand": 16.0, "lat": 10.78119, "lng": 106.6915 },
      { "id": 4, "demand": 25.0, "lat": 10.74100, "lng": 106.6500 },
      { "id": 5, "demand": 10.0, "lat": 10.79000, "lng": 106.6800 }
    ]
  }'
```

### Swagger UI & OpenAPI Docs

- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON Spec:** `http://localhost:8080/api-docs`

### Cấu trúc thư mục dataset

```
E:\dacn\demo\
├── A\                    ← CVRPLib Augerat instances (27 VRP + 27 SOL)
│   ├── A-n32-k5.vrp
│   ├── A-n32-k5.sol
│   └── ...
├── src\
└── README.md
```

---

## Tài liệu tham khảo

- Augerat, P. et al. (1995). *Approche polyèdrale du problème de tournées de véhicules.* INPG Grenoble.
- Prins, C. (2004). *A simple and effective evolutionary algorithm for the capacitated vehicle routing problem.* Computers & Operations Research, 31(12), 1985-2002.
- Baker, B.M. & Ayechew, M.A. (2003). *A genetic algorithm for the vehicle routing problem.* Computers & Operations Research, 30(5), 787-800.
- Uchoa, E. et al. (2017). *New benchmark instances for the CVRP.* European Journal of Operational Research, 257(3), 845-858.
