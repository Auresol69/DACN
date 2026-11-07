package com.example.demo.service;

import com.example.demo.dto.request.GaConfig;
import com.example.demo.model.DecodingResult;
import com.example.demo.model.Individual;
import com.example.demo.model.IndividualPool;
import com.example.demo.model.ProblemContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * GA Optimization Engine – Giai đoạn 4
 *
 * Kiến trúc: Master-Slave Song song (Parallel Master-Slave)
 *   - Master thread điều phối Selection, Crossover, Mutation.
 *   - Slave threads (FixedThreadPool) thực hiện đánh giá fitness song song.
 *   - costMatrix chỉ được ĐỌC → thread-safe hoàn toàn, không cần lock.
 *
 * Fitness (mục 2.4.1.3):
 *   fitness = 1.0 / (totalDistance + α(t) × capacityPenalty)
 *   → MAXIMIZE (cá thể có fitness CAO hơn là tốt hơn).
 *
 * Dynamic penalty: α(t) = α₀ × (1 + t/T)^γ
 *   α₀ = 100, γ = 2 (tăng dần theo thế hệ để phạt nặng hơn vi phạm capacity)
 *
 * Nguồn: Mục 2.4 – Tài liệu "dacn remake.pdf"
 */
@Service
public class GaOptimizationEngine {

    private static final Logger log = LoggerFactory.getLogger(GaOptimizationEngine.class);

    // ── Hằng số penalty động ──────────────────────────────────────────────────
    private static final double ALPHA_0       = 100.0; // hệ số phạt ban đầu
    private static final double GAMMA         = 2.0;   // số mũ tăng penalty

    // ── Elitism ──────────────────────────────────────────────────────────────
    private static final int ELITISM_COUNT = 1; // số cá thể elite giữ nguyên sang thế hệ sau

    // ── Dependencies ─────────────────────────────────────────────────────────
    private final SinglePassDecoder decoder;
    private final GeneticOperators  operators;
    private final int               numThreads;

    public GaOptimizationEngine(SinglePassDecoder decoder, GeneticOperators operators) {
        this.decoder    = decoder;
        this.operators  = operators;
        // Số luồng = số CPU logic (thường 4-16)
        this.numThreads = Runtime.getRuntime().availableProcessors();
        log.info("GaOptimizationEngine khởi tạo với {} luồng worker", numThreads);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Chạy thuật toán GA và trả về cá thể tốt nhất tìm được.
     *
     * @param ctx ngữ cảnh bài toán (costMatrix, demands, … – read-only)
     * @param cfg cấu hình GA từ request (populationSize, maxGenerations, timeBudgetMs, …)
     * @return cá thể có fitness CAO NHẤT (= cost thấp nhất) tìm được trong ngân sách thời gian
     * @throws InterruptedException nếu luồng master bị gián đoạn
     */
    public Individual runOptimization(ProblemContext ctx, GaConfig cfg)
            throws InterruptedException {

        final int    popSize       = cfg.getPopulationSize();
        final int    maxGen        = cfg.getMaxGenerations();
        final long   timeBudgetMs  = cfg.getTimeBudgetMs();
        final double crossoverRate = cfg.getCrossoverRate();
        final double mutationRate  = cfg.getMutationRate();
        final int    chromLen      = ctx.chromosomeLength;

        // ── Cấp phát pool một lần – tái sử dụng suốt vòng lặp ───────────────
        // popSize × 2 (current + offspring) + ELITISM_COUNT buffer + 1 bestEver
        IndividualPool pool = new IndividualPool(popSize * 2 + ELITISM_COUNT + 4, chromLen);

        // Hai mảng thế hệ: population (hiện tại) và offspring (mới tạo)
        Individual[] population = new Individual[popSize];
        Individual[] offspring  = new Individual[popSize];
        for (int i = 0; i < popSize; i++) {
            population[i] = pool.acquire(chromLen);
            offspring[i]  = pool.acquire(chromLen);
        }
        // Cá thể lưu kết quả tốt nhất xuyên thế hệ
        Individual bestEver = pool.acquire(chromLen);

        // ── Thread pool cho evaluation song song ─────────────────────────────
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // ── Buffer tái sử dụng cho crossover (1 per thread, ThreadLocal) ─────
        ThreadLocal<boolean[]> visitedTL =
                ThreadLocal.withInitial(GeneticOperators::createVisitedBuffer);

        // ── RNG master (selection/crossover/mutation trên master thread) ──────
        Random masterRng = new Random();

        try {
            // =================================================================
            // 1. Khởi tạo quần thể ban đầu (mục 2.2.4)
            // =================================================================
            log.info("Khởi tạo quần thể: {} cá thể (70% random, 30% greedy)", popSize);
            initializePopulation(population, popSize, ctx, masterRng);

            // =================================================================
            // 2. Đánh giá quần thể ban đầu (song song)
            // =================================================================
            evaluatePopulationParallel(population, popSize, ctx, executor, 0, maxGen);

            // Lưu cá thể tốt nhất ban đầu
            bestEver.copyFrom(getBest(population, popSize));
            log.info("Gen 0 | bestFitness={} | dist={}",
                    bestEver.getFitness(), bestEver.getTotalDistance());

            final long startTime = System.currentTimeMillis();
            int lastLogGen = 0;

            // =================================================================
            // 3. Vòng lặp chính (mục 2.4)
            // =================================================================
            for (int gen = 1; gen <= maxGen; gen++) {

                // ── Kiểm tra ngân sách thời gian (quan trọng!) ───────────────
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= timeBudgetMs) {
                    log.info("Hết ngân sách thời gian ({} ms) sau {} thế hệ. Dừng sớm.", elapsed, gen - 1);
                    break;
                }

                // ── Elitism: giữ cá thể tốt nhất sang thế hệ sau ────────────
                offspring[0].copyFrom(bestEver);

                // ── Tạo offspring mới bằng Selection + Crossover + Mutation ──
                for (int i = ELITISM_COUNT; i < popSize; i++) {

                    // Selection: chọn 2 cha mẹ bằng Tournament (k=2)
                    int p1Idx = operators.tournamentSelect(population, popSize, 2, masterRng);
                    int p2Idx = operators.tournamentSelect(population, popSize, 2, masterRng);

                    // Crossover (Modified OX) theo xác suất crossoverRate
                    if (masterRng.nextDouble() < crossoverRate) {
                        operators.modifiedOX(
                                population[p1Idx].getChromosome(),
                                population[p2Idx].getChromosome(),
                                offspring[i].getChromosome(),
                                chromLen,
                                visitedTL.get(),
                                masterRng
                        );
                        offspring[i].setActiveLength(chromLen);
                    } else {
                        // Không crossover: sao chép nguyên cha mẹ p1
                        offspring[i].copyFrom(population[p1Idx]);
                    }

                    // Mutation theo xác suất mutationRate
                    if (masterRng.nextDouble() < mutationRate) {
                        operators.mutate(offspring[i].getChromosome(), chromLen, masterRng);
                    }
                }

                // ── Đánh giá offspring song song ─────────────────────────────
                evaluatePopulationParallel(offspring, popSize, ctx, executor, gen, maxGen);

                // ── Swap thế hệ (không cấp phát mảng mới!) ───────────────────
                Individual[] tmp = population;
                population = offspring;
                offspring  = tmp;

                // ── Cập nhật bestEver nếu tìm được cá thể tốt hơn ───────────
                Individual genBest = getBest(population, popSize);
                if (genBest.getFitness() > bestEver.getFitness()) {
                    bestEver.copyFrom(genBest);
                }

                // Log mỗi 50 thế hệ
                if (gen - lastLogGen >= 50 || gen == maxGen) {
                    lastLogGen = gen;
                    log.debug("Gen {} | bestFit={} | dist={} | elapsed={}ms",
                            gen, bestEver.getFitness(), bestEver.getTotalDistance(),
                            System.currentTimeMillis() - startTime);
                }
            }

            log.info("GA hoàn tất | bestDist={} | penalty={}",
                    bestEver.getTotalDistance(), bestEver.getCapacityPenalty());
            return bestEver;

        } finally {
            executor.shutdown();
            // Trả pool về: giải phóng cả population và offspring arrays
            pool.releaseAll(population, population.length);
            pool.releaseAll(offspring,  offspring.length);
        }
    }

    // =========================================================================
    // 2.2.4  InitializePopulation: 70% random + 30% greedy nearest insertion
    // =========================================================================

    /**
     * Khởi tạo quần thể ban đầu:
     *   - 70% ngẫu nhiên: hoán vị Fisher-Yates + chèn K-1 số 0 ngẫu nhiên
     *   - 30% heuristic:  greedy nearest-neighbor insertion (có perturbation nhỏ)
     */
    void initializePopulation(Individual[] population, int popSize,
                              ProblemContext ctx, Random rng) {
        int numCustomers = ctx.numCustomers;
        int maxVehicles  = ctx.maxVehicles;
        int chromLen     = ctx.chromosomeLength;

        int heuristicCount = Math.max(1, (int) (popSize * 0.30));
        int randomCount    = popSize - heuristicCount;

        for (int i = 0; i < randomCount; i++) {
            generateRandomChromosome(population[i].getChromosome(), chromLen,
                    numCustomers, maxVehicles, rng);
            population[i].setActiveLength(chromLen);
        }

        for (int i = randomCount; i < popSize; i++) {
            generateGreedyChromosome(population[i].getChromosome(), chromLen,
                    numCustomers, maxVehicles, ctx, rng);
            population[i].setActiveLength(chromLen);
        }
    }

    // ── 70%: Random chromosome ───────────────────────────────────────────────

    /**
     * Tạo chromosome ngẫu nhiên theo mục 2.2.4:
     *   1. Hoán vị ngẫu nhiên dãy customer [1..N] bằng Fisher-Yates.
     *   2. Chèn ngẫu nhiên K-1 số 0 vào trong dãy (khoảng cách đều).
     *   3. Thêm 0 ở đầu và 0 ở cuối → format [0, ..., 0].
     */
    private void generateRandomChromosome(int[] chr, int chromLen,
                                          int numCustomers, int maxVehicles, Random rng) {
        // Bước 1: Hoán vị Fisher-Yates
        int[] customers = new int[numCustomers];
        for (int i = 0; i < numCustomers; i++) customers[i] = i + 1;
        for (int i = numCustomers - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = customers[i]; customers[i] = customers[j]; customers[j] = t;
        }

        // Bước 2: Chọn K-1 vị trí ngẫu nhiên trong inner[] cho số 0
        // inner có độ dài = numCustomers + (maxVehicles - 1)
        int innerLen    = chromLen - 2; // bỏ 0 đầu + 0 cuối
        int zerosNeeded = maxVehicles - 1;

        boolean[] isZeroSlot = new boolean[innerLen];
        int placed = 0;
        for (int i = 0; i < innerLen && placed < zerosNeeded; i++) {
            // Reservoir sampling: đảm bảo đúng K-1 số 0
            double prob = (double)(zerosNeeded - placed) / (innerLen - i);
            if (rng.nextDouble() < prob) {
                isZeroSlot[i] = true;
                placed++;
            }
        }

        // Bước 3: Điền inner[]
        chr[0] = 0;
        int cIdx = 0;
        for (int i = 0; i < innerLen; i++) {
            chr[i + 1] = isZeroSlot[i] ? 0 : customers[cIdx++];
        }
        chr[chromLen - 1] = 0;
    }

    // ── 30%: Greedy Nearest-Neighbor Insertion ───────────────────────────────

    /**
     * Tạo chromosome bằng heuristic "Nearest Neighbor Insertion" (mục 2.2.4):
     *   - Từ depot, luôn chọn khách hàng gần nhất chưa phục vụ còn vừa capacity.
     *   - Khi không còn khách hàng vừa tải → bắt đầu tuyến mới (chèn 0).
     *   - Thêm nhiễu ngẫu nhiên nhỏ vào khoảng cách (perturbation) để đa dạng hoá.
     */
    private void generateGreedyChromosome(int[] chr, int chromLen,
                                          int numCustomers, int maxVehicles,
                                          ProblemContext ctx, Random rng) {
        double[][] cost    = ctx.costMatrix;
        double[]   demands = ctx.demands;
        double     cap     = ctx.vehicleCapacity;

        boolean[] visited  = new boolean[numCustomers + 1];
        int[]     sequence = new int[numCustomers];
        int[]     breaks   = new int[maxVehicles]; // vị trí bắt đầu tuyến mới (index trong sequence)
        int       seqIdx   = 0;
        int       breakCount = 0;

        double currentLoad = 0.0;
        int    currentNode = 0; // bắt đầu từ depot

        for (int step = 0; step < numCustomers; step++) {
            int    bestCustomer = -1;
            double bestDist     = Double.MAX_VALUE;

            // Tìm customer gần nhất còn capacity
            for (int c = 1; c <= numCustomers; c++) {
                if (!visited[c] && currentLoad + demands[c] <= cap) {
                    // Nhiễu nhỏ: tránh tie-breaking deterministic
                    double d = cost[currentNode][c] + rng.nextDouble() * 1e-6;
                    if (d < bestDist) {
                        bestDist     = d;
                        bestCustomer = c;
                    }
                }
            }

            if (bestCustomer == -1) {
                // Không có customer nào vừa → mở tuyến mới
                if (breakCount < maxVehicles - 1) {
                    breaks[breakCount++] = seqIdx; // đánh dấu vị trí bẻ tuyến
                }
                currentLoad = 0.0;
                currentNode = 0;

                // Chọn customer gần nhất chưa thăm (không giới hạn capacity)
                for (int c = 1; c <= numCustomers; c++) {
                    if (!visited[c]) {
                        double d = cost[0][c] + rng.nextDouble() * 1e-6;
                        if (d < bestDist) {
                            bestDist     = d;
                            bestCustomer = c;
                        }
                    }
                }
                if (bestCustomer == -1) break; // tất cả đã thăm
            }

            visited[bestCustomer] = true;
            sequence[seqIdx++]    = bestCustomer;
            currentLoad          += demands[bestCustomer];
            currentNode           = bestCustomer;
        }

        // ── Chuyển sequence + breaks → chromosome format ──────────────────────
        chr[0] = 0; // depot đầu
        int chrIdx = 1;
        int bIdx   = 0; // con trỏ vào mảng breaks

        for (int s = 0; s < seqIdx; s++) {
            // Nếu đây là vị trí cần bẻ tuyến → chèn số 0 (depot)
            if (bIdx < breakCount && breaks[bIdx] == s) {
                if (chrIdx < chromLen - 1) chr[chrIdx++] = 0;
                bIdx++;
            }
            if (chrIdx < chromLen - 1) chr[chrIdx++] = sequence[s];
        }

        // Điền các 0 còn thiếu (nếu số tuyến < maxVehicles)
        while (chrIdx < chromLen - 1) {
            chr[chrIdx++] = 0;
        }
        chr[chromLen - 1] = 0; // depot cuối
    }

    // =========================================================================
    // 2.4.1.3  EvaluatePopulationParallel
    // =========================================================================

    /**
     * Đánh giá song song fitness cho toàn bộ quần thể.
     *
     * <p>Chia population thành {@code numThreads} chunk liên tục, mỗi chunk
     * được xử lý bởi một worker thread. Mỗi worker gọi {@link SinglePassDecoder#decode}
     * rồi tính fitness theo công thức (mục 2.4.1.3):</p>
     *
     * <pre>
     *   fitness = 1.0 / (totalDistance + α(t) × capacityPenalty)
     * </pre>
     *
     * <p>costMatrix chỉ được ĐỌC (read-only) → zero contention.</p>
     *
     * @param gen    thế hệ hiện tại (để tính dynamic α(t))
     * @param maxGen số thế hệ tối đa
     */
    void evaluatePopulationParallel(Individual[] population, int popSize,
                                    ProblemContext ctx, ExecutorService executor,
                                    int gen, int maxGen) throws InterruptedException {
        final double alpha     = computeAlpha(gen, maxGen);
        final int    chunkSize = Math.max(1, (int) Math.ceil((double) popSize / numThreads));

        List<Future<?>> futures = new ArrayList<>(numThreads);

        for (int start = 0; start < popSize; start += chunkSize) {
            final int from = start;
            final int to   = Math.min(start + chunkSize, popSize);

            futures.add(executor.submit(() -> {
                for (int i = from; i < to; i++) {
                    Individual ind = population[i];

                    // Giải mã chromosome → kết quả tuyến đường
                    DecodingResult result = decoder.decode(
                            ind.getChromosome(), ind.getActiveLength(), ctx
                    );

                    // Lưu trung gian (để build response về sau)
                    ind.setTotalDistance(result.totalDistance());
                    ind.setCapacityPenalty(result.totalCapacityPenalty());

                    // Tính fitness = 1 / (dist + α × penalty) → MAXIMIZE
                    double totalCost = result.totalDistance()
                            + alpha * result.totalCapacityPenalty();
                    ind.setFitness(totalCost > 0 ? 1.0 / totalCost : 0.0);
                }
            }));
        }

        // Chờ tất cả worker hoàn thành
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (ExecutionException e) {
                throw new RuntimeException("Lỗi khi đánh giá song song fitness", e.getCause());
            }
        }
    }

    // =========================================================================
    // Helpers nội bộ
    // =========================================================================

    /**
     * Dynamic penalty coefficient (mục 2.4.3):
     * α(t) = α₀ × (1 + t/T)^γ
     * Tăng dần theo thế hệ: phạt nặng hơn vi phạm capacity ở giai đoạn cuối.
     */
    private double computeAlpha(int gen, int maxGen) {
        return ALPHA_0 * Math.pow(1.0 + (double) gen / Math.max(1, maxGen), GAMMA);
    }

    /**
     * Lấy cá thể có fitness CAO NHẤT trong mảng (fitness = 1/cost → maximize).
     */
    private Individual getBest(Individual[] population, int popSize) {
        Individual best = population[0];
        for (int i = 1; i < popSize; i++) {
            if (population[i].getFitness() > best.getFitness()) {
                best = population[i];
            }
        }
        return best;
    }
}
