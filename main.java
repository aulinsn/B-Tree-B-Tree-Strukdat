import java.util.List;
import java.util.Random;

/**
 * Demo dan Perbandingan Performa B-Tree vs B+ Tree
 * Tugas 3 - ET234203 Struktur Data dan Pemrograman Berorientasi Objek
 *
 * Menjalankan:
 *   javac BTree.java BPlusTree.java Main.java
 *   java Main
 */
public class Main {

    private static final int DEMO_SIZE    = 15;       // data kecil untuk demo visual
    private static final int PERF_SIZE    = 100_000;  // data besar untuk pengukuran performa
    private static final int QUERY_COUNT  = 1_000;    // jumlah query untuk benchmar
    private static final int RANGE_FROM   = 0;
    private static final int RANGE_TO     = 1_000;

    public static void main(String[] args) {
        printHeader("TUGAS 3 — B-Tree vs B+ Tree");
        printHeader("ET234203 Struktur Data dan Pemrograman Berorientasi Objek");
        System.out.println();

        // =============================================
        // BAGIAN 1: Demo Visual (data kecil)
        // =============================================
        demoVisual();

        // =============================================
        // BAGIAN 2: Demo Range Search
        // =============================================
        demoRangeSearch();

        // =============================================
        // BAGIAN 3: Benchmar Performa
        // =============================================
        benchmarkPerformance();

        // =============================================
        // BAGIAN 4: Demo Delete
        // =============================================
        demoDelete();
    }

    // ==========================================================
    // BAGIAN 1: Demo Visual — menampilkan struktur pohon
    // ==========================================================
    private static void demoVisual() {
        printSection("1. DEMO VISUAL — Struktur Pohon (15 Data)");

        int[] demoData = {10, 20, 5, 6, 12, 30, 7, 17, 3, 25, 14, 18, 21, 4, 22};

        // --- B-Tree ---
        BTree btree = new BTree();
        System.out.println("Memasukkan data: ");
        for (int d : demoData) {
            System.out.print(d + " ");
            btree.insert(d, "v" + d);
        }
        System.out.println("\n");

        btree.print();
        System.out.println();

        // --- B+ Tree ---
        BPlusTree bpTree = new BPlusTree();
        for (int d : demoData) {
            bpTree.insert(d, "v" + d);
        }

        bpTree.print();
        System.out.println();
        bpTree.printLeaves();
        System.out.println();

        // --- Perbedaan utama ---
        printBox(
            "PERBEDAAN UTAMA YANG TERLIHAT:",
            "B-Tree  : Data (v) ada di SEMUA node (internal + daun)",
            "B+ Tree : Data (v) HANYA ada di node daun",
            "B+ Tree : Semua daun terhubung [→] — mendukung range scan efisien",
            "B+ Tree : Node internal hanya berisi separator key (tanpa value)"
        );
    }

    // ==========================================================
    // BAGIAN 2: Demo Range Search
    // ==========================================================
    private static void demoRangeSearch() {
        printSection("2. DEMO RANGE SEARCH — Keunggulan B+ Tree");

        BPlusTree bpTree = new BPlusTree();
        for (int i = 1; i <= 30; i++) {
            bpTree.insert(i, "nilai_" + i);
        }

        int from = 10, to = 20;
        System.out.printf("Mencari semua key dalam rentang [%d, %d]:%n", from, to);

        long start = System.nanoTime();
        List<int[]> results = bpTree.rangeSearch(from, to);
        long duration = System.nanoTime() - start;

        System.out.print("Hasil: ");
        for (int[] r : results) {
            System.out.print(r[0] + " ");
        }
        System.out.printf("%n%d hasil ditemukan dalam %.3f ms%n", results.size(), duration / 1_000_000.0);
        System.out.println();

        System.out.println("Penjelasan:");
        System.out.println("  B+ Tree: traversal ke daun pertama ≥10, lalu scan linear lewat linked list");
        System.out.println("  B-Tree : harus backtrack ke internal node berkali-kali → lebih lambat");
        System.out.println();
    }

    // ==========================================================
    // BAGIAN 3: Benchmar Performa (100.000 data)
    // ==========================================================
    private static void benchmarkPerformance() {
        printSection("3. BENCHMAR PERFORMA — " + PERF_SIZE + " Data");

        int[] data = generateData(PERF_SIZE);
        int[] queryKeys = generateData(QUERY_COUNT);

        BTree btree = new BTree();
        BPlusTree bpTree = new BPlusTree();

        // ---- INSERT ----
        long t1 = System.currentTimeMillis();
        for (int d : data) btree.insert(d, d);
        long btreeInsertTime = System.currentTimeMillis() - t1;

        long t2 = System.currentTimeMillis();
        for (int d : data) bpTree.insert(d, d);
        long bpInsertTime = System.currentTimeMillis() - t2;

        // ---- POINT SEARCH ----
        long t3 = System.currentTimeMillis();
        int foundBTree = 0;
        for (int k : queryKeys) if (btree.search(k) != null) foundBTree++;
        long btreeSearchTime = System.currentTimeMillis() - t3;

        long t4 = System.currentTimeMillis();
        int foundBPlus = 0;
        for (int k : queryKeys) if (bpTree.search(k) != null) foundBPlus++;
        long bpSearchTime = System.currentTimeMillis() - t4;

        // ---- RANGE SEARCH ----
        long t5 = System.currentTimeMillis();
        // B-Tree: simulasi range search (traversal manual)
        int bTreeRangeCount = 0;
        for (int k = RANGE_FROM; k <= RANGE_TO; k++) {
            if (btree.search(k) != null) bTreeRangeCount++;
        }
        long btreeRangeTime = System.currentTimeMillis() - t5;

        long t6 = System.currentTimeMillis();
        List<int[]> rangeResult = bpTree.rangeSearch(RANGE_FROM, RANGE_TO);
        long bpRangeTime = System.currentTimeMillis() - t6;

        // ---- TAMPILKAN HASIL ----
        System.out.printf("%-30s %12s %12s %12s%n", "Operasi", "B-Tree (ms)", "B+ Tree (ms)", "Pemenang");
        System.out.println("-".repeat(68));
        printResult("Insert " + PERF_SIZE + " data", btreeInsertTime, bpInsertTime);
        printResult("Point Search " + QUERY_COUNT + " query", btreeSearchTime, bpSearchTime);
        printResult("Range Search [0,1000]", btreeRangeTime, bpRangeTime);

        System.out.println();
        System.out.printf("  Ukuran B-Tree  : %,d data%n", btree.getSize());
        System.out.printf("  Ukuran B+ Tree : %,d data%n", bpTree.getSize());
        System.out.println();

        printBox(
            "ANALISIS HASIL (sesuai Paper 1 — Müller et al., 2025):",
            "- Insert: B+ Tree biasanya lebih cepat karena node internal lebih ramping",
            "  → fanout lebih besar → pohon lebih pendek → lebih sedikit I/O",
            "- Point Search: B-Tree kadang lebih cepat karena data bisa di internal node",
            "  → tidak selalu harus mencapai daun",
            "- Range Search: B+ Tree JAUH lebih cepat karena linked list daun",
            "  → O(log n + k) vs O(k * log n) pada B-Tree"
        );

        System.out.println();
        printBox(
            "TENTANG FLUKTUASI INSERT (sesuai Paper 2 — Xing & Aref, 2025):",
            "- B+ Tree konvensional: fluktuasi Φ = 2H (tumbuh seiring tinggi pohon)",
            "- FFBtree: Φ = O(1) konstan — dijamin oleh proactive split",
            "- Penting untuk sistem dengan SLO ketat (e-commerce, autonomous vehicles)",
            "- Implementasi Java ini menggunakan algoritma konvensional (tanpa FFBtree)",
            "  untuk kemudahan pemahaman konsep dasar B+ Tree"
        );
    }

    // ==========================================================
    // BAGIAN 4: Demo Delete
    // ==========================================================
    private static void demoDelete() {
        printSection("4. DEMO DELETE");

        BPlusTree bpTree = new BPlusTree();
        int[] data = {5, 10, 15, 20, 25, 30, 35, 40};
        for (int d : data) bpTree.insert(d, "v" + d);

        System.out.println("Sebelum delete:");
        bpTree.printLeaves();
        System.out.println("Ukuran: " + bpTree.getSize());

        int deleteKey = 20;
        boolean deleted = bpTree.delete(deleteKey);
        System.out.printf("%nSetelah delete(%d): %s%n", deleteKey, deleted ? "berhasil" : "tidak ditemukan");
        bpTree.printLeaves();
        System.out.println("Ukuran: " + bpTree.getSize());

        System.out.println();
        System.out.printf("Search(%d) setelah delete: %s%n", deleteKey,
                bpTree.search(deleteKey) == null ? "null (sudah terhapus ✓)" : "masih ada (error!)");
        System.out.printf("Search(25) setelah delete: %s%n", bpTree.search(25));
    }

    // ==========================================================
    // UTILITAS
    // ==========================================================

    private static int[] generateData(int n) {
        int[] data = new int[n];
        Random rnd = new Random(42); // seed tetap agar reproducible
        for (int i = 0; i < n; i++) data[i] = rnd.nextInt(n * 2);
        return data;
    }

    private static void printResult(String op, long t1, long t2) {
        String winner = t1 < t2 ? "B-Tree ✓" : (t2 < t1 ? "B+ Tree ✓" : "Seri");
        System.out.printf("%-30s %12d %12d %12s%n", op, t1, t2, winner);
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("╔" + "═".repeat(title.length() + 4) + "╗");
        System.out.println("║  " + title + "  ║");
        System.out.println("╚" + "═".repeat(title.length() + 4) + "╝");
        System.out.println();
    }

    private static void printHeader(String text) {
        System.out.println("=".repeat(text.length() + 4));
        System.out.println("  " + text);
        System.out.println("=".repeat(text.length() + 4));
    }

    private static void printBox(String... lines) {
        int maxLen = 0;
        for (String l : lines) maxLen = Math.max(maxLen, l.length());
        System.out.println("┌" + "─".repeat(maxLen + 2) + "┐");
        for (String l : lines) {
            System.out.printf("│ %-" + maxLen + "s │%n", l);
        }
        System.out.println("└" + "─".repeat(maxLen + 2) + "┘");
    }
}
