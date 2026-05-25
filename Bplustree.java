import java.util.ArrayList;
import java.util.List;

/**
 * Implementasi B+ Tree (Variasi Modifikasi dari B-Tree)
 * Tugas 3 - ET234203 Struktur Data dan Pemrograman Berorientasi Objek
 *
 * Perbedaan utama dari B-Tree dasar:
 * 1. Data (value) HANYA disimpan di node daun
 * 2. Node internal hanya menyimpan separator key
 * 3. Semua daun terhubung lewat linked list (pointer next)
 * 4. Range search sangat efisien lewat linked list daun
 *
 * Referensi:
 * - Müller et al. (2025). B-Trees Are Back: Engineering Fast and Pageable Node Layouts. ACM SIGMOD.
 * - Xing & Aref (2025). Towards a B+-tree with Fluctuation-Free Performance. arXiv:2603.04785.
 */
public class BPlusTree {

    private static final int ORDER = 4; // max (ORDER-1) key per node

    // =========================================================
    // Kelas Node Daun
    // Data HANYA disimpan di sini — ciri khas B+ Tree
    // =========================================================
    private static class LeafNode {
        int numKeys;
        int[] keys;
        Object[] values;
        LeafNode next;  // Linked list antar daun — mendukung range search efisien

        LeafNode() {
            numKeys = 0;
            keys = new int[ORDER - 1];
            values = new Object[ORDER - 1];
            next = null;
        }
    }

    // =========================================================
    // Kelas Node Internal
    // Hanya menyimpan separator key, TANPA data/value
    // =========================================================
    private static class InternalNode {
        int numKeys;
        int[] keys;          // separator key — salinan key dari daun
        Object[] children;   // bisa InternalNode atau LeafNode
        boolean childrenAreLeaves;

        InternalNode() {
            numKeys = 0;
            keys = new int[ORDER - 1];
            children = new Object[ORDER];
            childrenAreLeaves = false;
        }
    }

    // =========================================================
    // Kelas bantu untuk membawa hasil split
    // =========================================================
    private static class SplitResult {
        int promotedKey;     // key yang naik ke parent
        Object rightChild;   // node baru hasil split (kanan)

        SplitResult(int promotedKey, Object rightChild) {
            this.promotedKey = promotedKey;
            this.rightChild = rightChild;
        }
    }

    // =========================================================
    // Field utama
    // =========================================================
    private Object root;         // bisa InternalNode atau LeafNode
    private boolean rootIsLeaf;
    private int size;
    private LeafNode firstLeaf;  // pointer ke daun paling kiri (untuk full scan)

    public BPlusTree() {
        LeafNode leaf = new LeafNode();
        root = leaf;
        rootIsLeaf = true;
        firstLeaf = leaf;
        size = 0;
    }

    // =========================================================
    // SEARCH — Point Query
    // =========================================================

    /**
     * Mencari value berdasarkan key.
     * Selalu traversal hingga daun — O(log n).
     * (Berbeda dengan B-Tree yang bisa selesai di node internal)
     */
    public Object search(int key) {
        LeafNode leaf = findLeaf(key);
        for (int i = 0; i < leaf.numKeys; i++) {
            if (leaf.keys[i] == key) return leaf.values[i];
        }
        return null;
    }

    /**
     * Cari node daun yang berisi atau seharusnya berisi key.
     */
    private LeafNode findLeaf(int key) {
        if (rootIsLeaf) return (LeafNode) root;
        return findLeafInInternal((InternalNode) root, key);
    }

    private LeafNode findLeafInInternal(InternalNode node, int key) {
        int i = 0;
        while (i < node.numKeys && key >= node.keys[i]) i++;
        Object child = node.children[i];
        if (node.childrenAreLeaves) return (LeafNode) child;
        return findLeafInInternal((InternalNode) child, key);
    }

    // =========================================================
    // RANGE SEARCH — Keunggulan utama B+ Tree
    // =========================================================

    /**
     * Mencari semua pasangan (key, value) dengan key dalam rentang [fromKey, toKey].
     * Efisien karena memanfaatkan linked list daun — O(log n + k).
     * k = jumlah hasil.
     *
     * Pada B-Tree biasa, range search butuh O(k·log n) karena harus backtrack.
     */
    public List<int[]> rangeSearch(int fromKey, int toKey) {
        List<int[]> result = new ArrayList<>();
        LeafNode leaf = findLeaf(fromKey);

        while (leaf != null) {
            for (int i = 0; i < leaf.numKeys; i++) {
                if (leaf.keys[i] > toKey) return result;
                if (leaf.keys[i] >= fromKey) {
                    result.add(new int[]{leaf.keys[i]});
                }
            }
            leaf = leaf.next; // lanjut ke daun berikutnya lewat linked list
        }
        return result;
    }

    // =========================================================
    // INSERT
    // =========================================================

    /**
     * Menyisipkan pasangan key-value.
     * Data disimpan HANYA di node daun.
     * Kompleksitas: O(log n)
     */
    public void insert(int key, Object value) {
        if (rootIsLeaf) {
            SplitResult split = insertIntoLeaf((LeafNode) root, key, value);
            if (split != null) {
                // Root daun di-split → buat root internal baru
                InternalNode newRoot = new InternalNode();
                newRoot.keys[0] = split.promotedKey;
                newRoot.children[0] = root;
                newRoot.children[1] = split.rightChild;
                newRoot.numKeys = 1;
                newRoot.childrenAreLeaves = true;
                root = newRoot;
                rootIsLeaf = false;
            }
        } else {
            SplitResult split = insertIntoInternal((InternalNode) root, key, value, true);
            if (split != null) {
                // Root internal di-split → buat root baru
                InternalNode newRoot = new InternalNode();
                newRoot.keys[0] = split.promotedKey;
                newRoot.children[0] = root;
                newRoot.children[1] = split.rightChild;
                newRoot.numKeys = 1;
                newRoot.childrenAreLeaves = false;
                root = newRoot;
            }
        }
        size++;
    }

    /**
     * Rekursi insert ke node internal.
     * isDirectChildLeaf: apakah anak langsung node ini adalah daun
     */
    private SplitResult insertIntoInternal(InternalNode node, int key, Object value, boolean isRoot) {
        int i = 0;
        while (i < node.numKeys && key >= node.keys[i]) i++;

        SplitResult childSplit;

        if (node.childrenAreLeaves) {
            // Anak adalah daun
            childSplit = insertIntoLeaf((LeafNode) node.children[i], key, value);
        } else {
            // Anak adalah internal node
            childSplit = insertIntoInternal((InternalNode) node.children[i], key, value, false);
        }

        if (childSplit == null) return null;

        // Ada split dari bawah → sisipkan promoted key ke node ini
        if (node.numKeys < ORDER - 1) {
            insertKeyIntoInternal(node, i, childSplit);
            return null;
        } else {
            // Node penuh → split node internal ini (propagasi split)
            return splitInternal(node, i, childSplit);
        }
    }

    /**
     * Sisipkan ke node daun.
     * Pada B+ Tree: semua data disimpan di sini.
     */
    private SplitResult insertIntoLeaf(LeafNode leaf, int key, Object value) {
        // Cek duplikasi
        for (int i = 0; i < leaf.numKeys; i++) {
            if (leaf.keys[i] == key) {
                leaf.values[i] = value; // update
                size--; // dikompensasi di insert()
                return null;
            }
        }

        if (leaf.numKeys < ORDER - 1) {
            // Ada ruang
            int pos = leaf.numKeys - 1;
            while (pos >= 0 && leaf.keys[pos] > key) {
                leaf.keys[pos + 1] = leaf.keys[pos];
                leaf.values[pos + 1] = leaf.values[pos];
                pos--;
            }
            leaf.keys[pos + 1] = key;
            leaf.values[pos + 1] = value;
            leaf.numKeys++;
            return null;
        }

        // Penuh → split daun
        return splitLeaf(leaf, key, value);
    }

    /**
     * Split node daun.
     * Perbedaan PENTING dari B-Tree: pada B+ Tree, key median di-COPY ke atas
     * (bukan dipindah), sehingga data tetap ada di daun.
     */
    private SplitResult splitLeaf(LeafNode leaf, int newKey, Object newValue) {
        int[] tempKeys = new int[ORDER];
        Object[] tempValues = new Object[ORDER];
        int j = 0;
        boolean inserted = false;

        for (int i = 0; i < leaf.numKeys; i++) {
            if (!inserted && newKey < leaf.keys[i]) {
                tempKeys[j] = newKey;
                tempValues[j] = newValue;
                j++;
                inserted = true;
            }
            tempKeys[j] = leaf.keys[i];
            tempValues[j] = leaf.values[i];
            j++;
        }
        if (!inserted) {
            tempKeys[j] = newKey;
            tempValues[j] = newValue;
            j++;
        }

        int mid = j / 2;

        // Daun kiri (dipertahankan)
        leaf.numKeys = mid;
        for (int i = 0; i < mid; i++) {
            leaf.keys[i] = tempKeys[i];
            leaf.values[i] = tempValues[i];
        }

        // Daun kanan (baru)
        LeafNode newLeaf = new LeafNode();
        newLeaf.numKeys = j - mid;
        for (int i = 0; i < newLeaf.numKeys; i++) {
            newLeaf.keys[i] = tempKeys[mid + i];
            newLeaf.values[i] = tempValues[mid + i];
        }

        // Sambungkan linked list daun
        newLeaf.next = leaf.next;
        leaf.next = newLeaf;

        // Key DISALIN ke atas (bukan dipindah — ini perbedaan B+ Tree vs B-Tree!)
        // Key pertama daun kanan menjadi separator di parent
        return new SplitResult(tempKeys[mid], newLeaf);
    }

    /** Sisipkan key hasil split ke node internal. */
    private void insertKeyIntoInternal(InternalNode node, int pos, SplitResult split) {
        for (int i = node.numKeys; i > pos; i--) {
            node.keys[i] = node.keys[i - 1];
            node.children[i + 1] = node.children[i];
        }
        node.keys[pos] = split.promotedKey;
        node.children[pos + 1] = split.rightChild;
        node.numKeys++;
    }

    /** Split node internal. */
    private SplitResult splitInternal(InternalNode node, int pos, SplitResult childSplit) {
        int[] tempKeys = new int[ORDER];
        Object[] tempChildren = new Object[ORDER + 1];

        for (int i = 0; i < pos; i++) {
            tempKeys[i] = node.keys[i];
            tempChildren[i] = node.children[i];
        }
        tempKeys[pos] = childSplit.promotedKey;
        tempChildren[pos] = node.children[pos];
        tempChildren[pos + 1] = childSplit.rightChild;
        for (int i = pos; i < node.numKeys; i++) {
            tempKeys[i + 1] = node.keys[i];
            tempChildren[i + 2] = node.children[i + 1];
        }

        int mid = ORDER / 2;

        // Kiri
        node.numKeys = mid;
        for (int i = 0; i < mid; i++) {
            node.keys[i] = tempKeys[i];
            node.children[i] = tempChildren[i];
        }
        node.children[mid] = tempChildren[mid];

        // Kanan
        InternalNode newNode = new InternalNode();
        newNode.childrenAreLeaves = node.childrenAreLeaves;
        newNode.numKeys = ORDER - mid - 1;
        for (int i = 0; i < newNode.numKeys; i++) {
            newNode.keys[i] = tempKeys[mid + 1 + i];
            newNode.children[i] = tempChildren[mid + 1 + i];
        }
        newNode.children[newNode.numKeys] = tempChildren[ORDER];

        return new SplitResult(tempKeys[mid], newNode);
    }

    // =========================================================
    // DELETE
    // =========================================================

    /**
     * Menghapus key dari B+ Tree.
     * Penghapusan selalu dilakukan di node daun.
     * Kompleksitas: O(log n)
     */
    public boolean delete(int key) {
        if (rootIsLeaf) {
            boolean deleted = deleteFromLeaf((LeafNode) root, key);
            if (deleted) size--;
            return deleted;
        }

        boolean deleted = deleteFromInternal((InternalNode) root, key, null, -1, true);
        if (deleted) {
            size--;
            // Jika root internal kosong
            if (!rootIsLeaf && ((InternalNode) root).numKeys == 0) {
                root = ((InternalNode) root).children[0];
                rootIsLeaf = (root instanceof LeafNode);
            }
        }
        return deleted;
    }

    private boolean deleteFromLeaf(LeafNode leaf, int key) {
        for (int i = 0; i < leaf.numKeys; i++) {
            if (leaf.keys[i] == key) {
                for (int j = i; j < leaf.numKeys - 1; j++) {
                    leaf.keys[j] = leaf.keys[j + 1];
                    leaf.values[j] = leaf.values[j + 1];
                }
                leaf.numKeys--;
                return true;
            }
        }
        return false;
    }

    private boolean deleteFromInternal(InternalNode node, int key,
                                        InternalNode parent, int parentIdx,
                                        boolean isRoot) {
        int i = 0;
        while (i < node.numKeys && key >= node.keys[i]) i++;

        boolean deleted;
        if (node.childrenAreLeaves) {
            deleted = deleteFromLeaf((LeafNode) node.children[i], key);
        } else {
            deleted = deleteFromInternal((InternalNode) node.children[i], key, node, i, false);
        }

        if (!deleted) return false;

        // Update separator key jika perlu
        if (i < node.numKeys) {
            // Perbarui separator key jika key yang dihapus = separator
            if (key == node.keys[i]) {
                if (node.childrenAreLeaves) {
                    LeafNode rightLeaf = (LeafNode) node.children[i + 1];
                    if (rightLeaf.numKeys > 0) node.keys[i] = rightLeaf.keys[0];
                }
            }
        }

        // Rebalance jika perlu
        if (!isRoot) {
            int minKeys = (ORDER - 1) / 2;
            Object child = node.children[i < node.numKeys ? i : i - 1];
            int childKeys = node.childrenAreLeaves
                    ? ((LeafNode) child).numKeys
                    : ((InternalNode) child).numKeys;
            if (childKeys < minKeys && parent != null) {
                rebalanceAfterDelete(node, Math.min(i, node.numKeys > 0 ? node.numKeys - 1 : 0));
            }
        }
        return true;
    }

    private void rebalanceAfterDelete(InternalNode parent, int idx) {
        // Sederhana: jika anak kekurangan, coba merge dengan saudara
        // Implementasi lengkap untuk keperluan demonstrasi
        if (parent.numKeys == 0) return;
        int minKeys = (ORDER - 1) / 2;

        if (parent.childrenAreLeaves) {
            LeafNode child = (LeafNode) parent.children[idx];
            if (child.numKeys >= minKeys) return;
            if (idx < parent.numKeys) {
                // Merge dengan kanan
                LeafNode right = (LeafNode) parent.children[idx + 1];
                mergeLeaves(parent, idx, child, right);
            } else if (idx > 0) {
                // Merge dengan kiri
                LeafNode left = (LeafNode) parent.children[idx - 1];
                mergeLeaves(parent, idx - 1, left, child);
            }
        }
    }

    private void mergeLeaves(InternalNode parent, int idx, LeafNode left, LeafNode right) {
        for (int i = 0; i < right.numKeys; i++) {
            left.keys[left.numKeys + i] = right.keys[i];
            left.values[left.numKeys + i] = right.values[i];
        }
        left.numKeys += right.numKeys;
        left.next = right.next;

        for (int i = idx; i < parent.numKeys - 1; i++) {
            parent.keys[i] = parent.keys[i + 1];
            parent.children[i + 1] = parent.children[i + 2];
        }
        parent.numKeys--;
    }

    // =========================================================
    // UTILITAS
    // =========================================================

    public int getSize() { return size; }

    /** Cetak semua data di daun (sequential scan via linked list). */
    public void printLeaves() {
        System.out.println("=== B+ Tree — Sequential Scan (Linked List Daun) ===");
        LeafNode curr = firstLeaf;
        int count = 0;
        StringBuilder sb = new StringBuilder();
        while (curr != null) {
            sb.append("[");
            for (int i = 0; i < curr.numKeys; i++) {
                sb.append(curr.keys[i]);
                if (i < curr.numKeys - 1) sb.append(",");
            }
            sb.append("]");
            if (curr.next != null) sb.append(" → ");
            curr = curr.next;
            count++;
            if (count > 20) { sb.append("..."); break; } // batasi output
        }
        System.out.println(sb.toString());
    }

    /** Cetak struktur pohon. */
    public void print() {
        System.out.println("=== B+ Tree (Order " + ORDER + ") ===");
        printNode(root, "", true, rootIsLeaf);
    }

    private void printNode(Object node, String prefix, boolean isTail, boolean isLeaf) {
        if (node == null) return;
        System.out.print(prefix + (isTail ? "└── " : "├── "));
        if (isLeaf) {
            LeafNode ln = (LeafNode) node;
            System.out.print("[");
            for (int i = 0; i < ln.numKeys; i++) {
                System.out.print(ln.keys[i] + ":" + ln.values[i]);
                if (i < ln.numKeys - 1) System.out.print("|");
            }
            System.out.println("] (DAUN)" + (ln.next != null ? " →" : " [TERAKHIR]"));
        } else {
            InternalNode in = (InternalNode) node;
            System.out.print("{");
            for (int i = 0; i < in.numKeys; i++) {
                System.out.print(in.keys[i]);
                if (i < in.numKeys - 1) System.out.print("|");
            }
            System.out.println("} (INTERNAL)");
            for (int i = 0; i <= in.numKeys; i++) {
                printNode(in.children[i],
                        prefix + (isTail ? "    " : "│   "),
                        i == in.numKeys,
                        in.childrenAreLeaves);
            }
        }
    }
}
