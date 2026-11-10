package com.example.demo.model;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Object Pool cho lớp {@link Individual} – cơ chế cốt lõi để triệt tiêu
 * Garbage Collection trong vòng lặp Thuật toán Di truyền.
 *
 * <h2>Vấn đề cần giải quyết</h2>
 * <p>Trong mỗi thế hệ GA, hàng trăm cá thể được tạo ra và hủy bỏ.
 * Nếu dùng {@code new Individual()} liên tục, JVM sẽ liên tục cấp phát
 * heap memory, kích hoạt GC pauses và làm gián đoạn luồng tối ưu hóa.</p>
 *
 * <h2>Giải pháp: Pre-allocation + Reuse</h2>
 * <pre>
 *   ┌──────────────────────────────────────────────┐
 *   │              IndividualPool                  │
 *   │                                              │
 *   │  pool: [Ind0][Ind1][Ind2]...[IndN]           │
 *   │         ↑ Pre-allocated tại khởi động        │
 *   │                                              │
 *   │  acquire() → lấy từ pool, reset trạng thái  │
 *   │  release() → trả về pool                    │
 *   └──────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Chiến lược Master-Slave Parallelism</h2>
 * <p>Mỗi worker thread (Slave) nên có pool riêng để tránh contention.
 * Sử dụng {@link ThreadLocal} ở tầng {@code GaEngine} để gán pool per-thread.
 * Lớp này dùng {@link ArrayDeque} (không thread-safe, hiệu suất cao) thay vì
 * {@link java.util.concurrent.ConcurrentLinkedDeque} vì mỗi thread chỉ truy cập
 * pool của chính nó.</p>
 *
 * <h2>Cảnh báo sử dụng</h2>
 * <ul>
 *   <li>Luôn gọi {@link #release(Individual)} sau khi cá thể không còn dùng nữa.</li>
 *   <li>Không giữ tham chiếu đến cá thể sau khi đã release.</li>
 *   <li>Không tạo {@code new Individual()} ngoài pool này.</li>
 * </ul>
 */
public final class IndividualPool {

    /**
     * Pool nội bộ dùng stack-like access (LIFO) để tận dụng cache locality:
     * cá thể vừa trả về (còn nóng trong CPU cache) sẽ được tái sử dụng ngay.
     */
    private final Deque<Individual> pool;

    /** Kích thước tối đa của mảng chromosome cho mỗi Individual trong pool này. */
    private final int chromosomeCapacity;

    /** Tổng số cá thể pool đã tạo ra (chỉ dùng cho monitoring). */
    private final int initialSize;

    /** Đếm số lần acquire() phải tạo mới (pool bị cạn kiệt – dấu hiệu cấu hình thiếu). */
    private int overflowCount = 0;

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Khởi tạo pool và pre-allocate toàn bộ đối tượng Individual.
     * Được gọi một lần duy nhất tại thời điểm khởi động Spring Bean
     * hoặc khi GaEngine khởi tạo worker thread.
     *
     * @param poolSize           số lượng Individual cần pre-allocate.
     *                           Nên >= 2 * populationSize để đảm bảo đủ cho
     *                           cả quần thể hiện tại và thế hệ con.
     * @param chromosomeCapacity kích thước tối đa của chromosome[].
     *                           = numCustomers + maxVehicles - 1.
     *                           Giá trị an toàn: numCustomers * 2.
     */
    public IndividualPool(int poolSize, int chromosomeCapacity) {
        if (poolSize <= 0)           throw new IllegalArgumentException("poolSize phải > 0, nhận: " + poolSize);
        if (chromosomeCapacity <= 0) throw new IllegalArgumentException("chromosomeCapacity phải > 0, nhận: " + chromosomeCapacity);

        this.chromosomeCapacity = chromosomeCapacity;
        this.initialSize = poolSize;
        this.pool = new ArrayDeque<>(poolSize);

        // ── Pre-allocate toàn bộ – đây là lần DUY NHẤT new Individual() được gọi ──
        for (int i = 0; i < poolSize; i++) {
            pool.push(new Individual(chromosomeCapacity));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lấy một {@link Individual} từ pool.
     *
     * <p>Nếu pool còn đối tượng, lấy ra và reset về trạng thái sạch.
     * Nếu pool cạn kiệt (overflow), tạo mới một lần – đây là tình huống
     * cần tránh; hãy tăng {@code poolSize} trong config.</p>
     *
     * @param activeLength độ dài chromosome thực tế sẽ dùng cho cá thể này
     * @return một Individual đã reset, sẵn sàng sử dụng
     */
    public Individual acquire(int activeLength) {
        Individual individual = pool.poll(); // poll() không throw nếu rỗng
        if (individual == null) {
            // Pool cạn – tạo mới khẩn cấp và cảnh báo
            overflowCount++;
            individual = new Individual(chromosomeCapacity);
        }
        individual.reset(activeLength);
        return individual;
    }

    /**
     * Trả {@link Individual} về pool để tái sử dụng.
     *
     * <p><b>Bất biến quan trọng:</b> Sau khi gọi phương thức này,
     * không được tiếp tục đọc/ghi vào {@code individual} đó.
     * Tham chiếu nên được đặt thành {@code null} tại điểm gọi.</p>
     *
     * @param individual cá thể cần trả về pool; nếu null thì bỏ qua
     */
    public void release(Individual individual) {
        if (individual != null) {
            pool.push(individual); // LIFO – tận dụng cache locality
        }
    }

    /**
     * Trả toàn bộ mảng Individual về pool theo thứ tự ngược lại.
     * Tiện ích dùng sau khi một thế hệ hoàn thành để giải phóng quần thể cũ.
     *
     * @param population mảng các cá thể cần trả về pool
     * @param size       số lượng phần tử hợp lệ trong mảng (tránh release null)
     */
    public void releaseAll(Individual[] population, int size) {
        for (int i = 0; i < size; i++) {
            release(population[i]);
            population[i] = null; // Ngăn tham chiếu đến cá thể đã được tái sử dụng
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Monitoring & Diagnostics
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @return số lượng Individual hiện đang nằm trong pool (chưa được acquire)
     */
    public int availableCount() {
        return pool.size();
    }

    /**
     * @return kích thước ban đầu của pool (số Individual đã pre-allocate)
     */
    public int getInitialSize() {
        return initialSize;
    }

    /**
     * @return kích thước chromosome capacity của pool này
     */
    public int getChromosomeCapacity() {
        return chromosomeCapacity;
    }

    /**
     * @return số lần pool bị cạn và phải cấp phát mới khẩn cấp.
     *         Giá trị > 0 là dấu hiệu pool cần được tăng kích thước.
     */
    public int getOverflowCount() {
        return overflowCount;
    }

    /**
     * @return true nếu pool từng bị overflow – dùng để log cảnh báo
     */
    public boolean hasOverflowed() {
        return overflowCount > 0;
    }

    @Override
    public String toString() {
        return String.format(
            "IndividualPool{initialSize=%d, available=%d, chromosomeCapacity=%d, overflows=%d}",
            initialSize, pool.size(), chromosomeCapacity, overflowCount
        );
    }
}
