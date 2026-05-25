/**
 * Implementasi B-Tree Dasar
 * Tugas 3 - ET234203 Struktur Data dan Pemrograman Berorientasi Objek
 *
 * Referensi:
 * - Müller et al. (2025). B-Trees Are Back: Engineering Fast and Pageable Node Layouts. ACM SIGMOD.
 * - Bayer & McCreight (1970). Organization and maintenance of large ordered indices.
 */
public class BTree {

    private static final int ORDER = 4; // setiap node max ORDER-1 key, min ceil(ORDER/2)-1 key

    // =========================================================
    // Kelas Node
    // =========================================================
    private static class Node {
        int numKeys;
        int[] keys;
        Object[] values;   // nilai disimpan di SEMUA node (internal + daun) — ciri khas B-Tree
        Node[] children;
        boolean isLeaf;

        Node(boolean isLeaf) {
            this.isLeaf = isLeaf;
            this.numKeys = 0;
            this.keys = new int[ORDER - 1];
            this.values = new Object[ORDER - 1];
            this.children = new Node[ORDER];
        }
    }

    // =========================================================
    // Kelas bantu untuk membawa hasil split ke atas
    // =========================================================
    private static class SplitResult {
        int medianKey;
        Object medianValue;
        Node newNode;

        SplitResult(int medianKey, Object medianValue, Node newNode) {
            this.medianKey = medianKey;
            this.medianValue = medianValue;
            this.newNode = newNode;
        }
    }

    // =========================================================
    // Field utama
    // =========================================================
    private Node root;
    private int size;

    public BTree() {
        root = new Node(true);
        size = 0;
    }

    // =========================================================
    // SEARCH
    // =========================================================

    /**
     * Mencari nilai berdasarkan key.
     * Kompleksitas: O(log n)
     */
    public Object search(int key) {
        return searchRecursive(root, key);
    }

    private Object searchRecursive(Node node, int key) {
        int i = 0;
        // Cari posisi yang tepat
        while (i < node.numKeys && key > node.keys[i]) {
            i++;
        }
        // Cek apakah key ada di node ini (B-Tree: data bisa ada di node internal)
        if (i < node.numKeys && key == node.keys[i]) {
            return node.values[i];
        }
        // Jika daun dan tidak ketemu
        if (node.isLeaf) {
            return null;
        }
        // Rekursi ke child yang sesuai
        return searchRecursive(node.children[i], key);
    }

    // =========================================================
    // INSERT
    // =========================================================

    /**
     * Menyisipkan pasangan key-value.
     * Jika key sudah ada, value diperbarui.
     * Kompleksitas: O(log n)
     */
    public void insert(int key, Object value) {
        SplitResult result = insertRecursive(root, key, value);
        if (result != null) {
            // Root di-split → buat root baru
            Node newRoot = new Node(false);
            newRoot.keys[0] = result.medianKey;
            newRoot.values[0] = result.medianValue;
            newRoot.children[0] = root;
            newRoot.children[1] = result.newNode;
            newRoot.numKeys = 1;
            root = newRoot;
        }
        size++;
    }

    /**
     * Rekursi insert.
     * Mengembalikan SplitResult jika node di-split, null jika tidak.
     */
    private SplitResult insertRecursive(Node node, int key, Object value) {
        if (node.isLeaf) {
            // Sisipkan langsung di daun
            return insertIntoLeaf(node, key, value);
        }

        // Cari posisi child yang sesuai
        int i = node.numKeys - 1;
        while (i >= 0 && key < node.keys[i]) {
            i--;
        }
        // Periksa apakah key sudah ada di node internal ini
        if (i >= 0 && key == node.keys[i]) {
            node.values[i] = value; // update
            size--; // akan di-increment lagi di insert()
            return null;
        }
        i++;

        // Rekursi ke child
        SplitResult childSplit = insertRecursive(node.children[i], key, value);
        if (childSplit == null) {
            return null; // tidak ada split
        }

        // Child di-split → sisipkan median ke node ini
        if (node.numKeys < ORDER - 1) {
            // Node masih ada ruang
            insertIntoInternal(node, i, childSplit);
            return null;
        } else {
            // Node penuh → split node ini juga (propagasi split)
            return splitInternal(node, i, childSplit);
        }
    }

    /** Sisipkan key ke node daun. */
    private SplitResult insertIntoLeaf(Node leaf, int key, Object value) {
        // Periksa apakah key sudah ada
        for (int i = 0; i < leaf.numKeys; i++) {
            if (leaf.keys[i] == key) {
                leaf.values[i] = value; // update
                size--; // akan di-increment lagi
                return null;
            }
        }

        if (leaf.numKeys < ORDER - 1) {
            // Ada ruang — geser dan sisipkan
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
        } else {
            // Node penuh → split daun
            return splitLeaf(leaf, key, value);
        }
    }

    /** Split node daun. Mengembalikan median yang harus naik ke parent. */
    private SplitResult splitLeaf(Node leaf, int newKey, Object newValue) {
        // Kumpulkan semua key + yang baru dalam array sementara
        int[] tempKeys = new int[ORDER];
        Object[] tempValues = new Object[ORDER];
        int inserted = false ? 0 : 0;
        int j = 0;
        boolean done = false;
        for (int i = 0; i < leaf.numKeys; i++) {
            if (!done && newKey < leaf.keys[i]) {
                tempKeys[j] = newKey;
                tempValues[j] = newValue;
                j++;
                done = true;
            }
            tempKeys[j] = leaf.keys[i];
            tempValues[j] = leaf.values[i];
            j++;
        }
        if (!done) {
            tempKeys[j] = newKey;
            tempValues[j] = newValue;
            j++;
        }

        int mid = j / 2;
        // Node kiri (tetap di leaf)
        leaf.numKeys = mid;
        for (int i = 0; i < mid; i++) {
            leaf.keys[i] = tempKeys[i];
            leaf.values[i] = tempValues[i];
        }

        // Node kanan (baru)
        Node newLeaf = new Node(true);
        newLeaf.numKeys = j - mid;
        for (int i = 0; i < newLeaf.numKeys; i++) {
            newLeaf.keys[i] = tempKeys[mid + i];
            newLeaf.values[i] = tempValues[mid + i];
        }

        // Pada B-Tree dasar, median ikut ke atas (bukan copy — data ada di internal)
        return new SplitResult(tempKeys[mid], tempValues[mid], newLeaf);
    }

    /** Sisipkan hasil split child ke node internal. */
    private void insertIntoInternal(Node node, int pos, SplitResult split) {
        for (int i = node.numKeys; i > pos; i--) {
            node.keys[i] = node.keys[i - 1];
            node.values[i] = node.values[i - 1];
            node.children[i + 1] = node.children[i];
        }
        node.keys[pos] = split.medianKey;
        node.values[pos] = split.medianValue;
        node.children[pos + 1] = split.newNode;
        node.numKeys++;
    }

    /** Split node internal. */
    private SplitResult splitInternal(Node node, int pos, SplitResult childSplit) {
        int[] tempKeys = new int[ORDER];
        Object[] tempValues = new Object[ORDER];
        Node[] tempChildren = new Node[ORDER + 1];

        for (int i = 0; i < pos; i++) {
            tempKeys[i] = node.keys[i];
            tempValues[i] = node.values[i];
            tempChildren[i] = node.children[i];
        }
        tempKeys[pos] = childSplit.medianKey;
        tempValues[pos] = childSplit.medianValue;
        tempChildren[pos] = node.children[pos];
        tempChildren[pos + 1] = childSplit.newNode;
        for (int i = pos; i < node.numKeys; i++) {
            tempKeys[i + 1] = node.keys[i];
            tempValues[i + 1] = node.values[i];
            tempChildren[i + 2] = node.children[i + 1];
        }

        int mid = ORDER / 2;
        // Kiri (tetap di node)
        node.numKeys = mid;
        for (int i = 0; i < mid; i++) {
            node.keys[i] = tempKeys[i];
            node.values[i] = tempValues[i];
            node.children[i] = tempChildren[i];
        }
        node.children[mid] = tempChildren[mid];

        // Kanan (baru)
        Node newNode = new Node(false);
        newNode.numKeys = ORDER - mid - 1;
        for (int i = 0; i < newNode.numKeys; i++) {
            newNode.keys[i] = tempKeys[mid + 1 + i];
            newNode.values[i] = tempValues[mid + 1 + i];
            newNode.children[i] = tempChildren[mid + 1 + i];
        }
        newNode.children[newNode.numKeys] = tempChildren[ORDER];

        return new SplitResult(tempKeys[mid], tempValues[mid], newNode);
    }

    // =========================================================
    // DELETE (Sederhana — dengan merge)
    // =========================================================

    /**
     * Menghapus key dari B-Tree.
     * Kompleksitas: O(log n)
     */
    public boolean delete(int key) {
        boolean deleted = deleteRecursive(root, key, null, -1);
        if (deleted) {
            size--;
            // Jika root menjadi kosong dan bukan daun
            if (root.numKeys == 0 && !root.isLeaf) {
                root = root.children[0];
            }
        }
        return deleted;
    }

    private boolean deleteRecursive(Node node, int key, Node parent, int parentIndex) {
        int i = 0;
        while (i < node.numKeys && key > node.keys[i]) i++;

        if (node.isLeaf) {
            if (i < node.numKeys && node.keys[i] == key) {
                // Hapus dari daun
                for (int j = i; j < node.numKeys - 1; j++) {
                    node.keys[j] = node.keys[j + 1];
                    node.values[j] = node.values[j + 1];
                }
                node.numKeys--;
                return true;
            }
            return false;
        }

        if (i < node.numKeys && node.keys[i] == key) {
            // Key ada di node internal → ganti dengan successor in-order (kiri dari child[i+1])
            Node successor = node.children[i + 1];
            while (!successor.isLeaf) successor = successor.children[0];
            node.keys[i] = successor.keys[0];
            node.values[i] = successor.values[0];
            deleteRecursive(node.children[i + 1], successor.keys[0], node, i + 1);
            return true;
        }

        boolean deleted = deleteRecursive(node.children[i], key, node, i);
        if (deleted) {
            // Rebalance jika perlu
            rebalance(node, i);
        }
        return deleted;
    }

    private void rebalance(Node parent, int childIdx) {
        Node child = parent.children[childIdx];
        int minKeys = (ORDER - 1) / 2;
        if (child.numKeys >= minKeys) return;

        // Coba borrow dari saudara kiri
        if (childIdx > 0 && parent.children[childIdx - 1].numKeys > minKeys) {
            borrowFromLeft(parent, childIdx);
            return;
        }
        // Coba borrow dari saudara kanan
        if (childIdx < parent.numKeys && parent.children[childIdx + 1].numKeys > minKeys) {
            borrowFromRight(parent, childIdx);
            return;
        }
        // Merge
        if (childIdx > 0) {
            mergeChildren(parent, childIdx - 1);
        } else {
            mergeChildren(parent, childIdx);
        }
    }

    private void borrowFromLeft(Node parent, int idx) {
        Node child = parent.children[idx];
        Node leftSibling = parent.children[idx - 1];
        // Geser child ke kanan
        for (int i = child.numKeys; i > 0; i--) {
            child.keys[i] = child.keys[i - 1];
            child.values[i] = child.values[i - 1];
        }
        if (!child.isLeaf) {
            for (int i = child.numKeys + 1; i > 0; i--) {
                child.children[i] = child.children[i - 1];
            }
        }
        child.keys[0] = parent.keys[idx - 1];
        child.values[0] = parent.values[idx - 1];
        if (!child.isLeaf) child.children[0] = leftSibling.children[leftSibling.numKeys];
        child.numKeys++;
        parent.keys[idx - 1] = leftSibling.keys[leftSibling.numKeys - 1];
        parent.values[idx - 1] = leftSibling.values[leftSibling.numKeys - 1];
        leftSibling.numKeys--;
    }

    private void borrowFromRight(Node parent, int idx) {
        Node child = parent.children[idx];
        Node rightSibling = parent.children[idx + 1];
        child.keys[child.numKeys] = parent.keys[idx];
        child.values[child.numKeys] = parent.values[idx];
        if (!child.isLeaf) child.children[child.numKeys + 1] = rightSibling.children[0];
        child.numKeys++;
        parent.keys[idx] = rightSibling.keys[0];
        parent.values[idx] = rightSibling.values[0];
        for (int i = 0; i < rightSibling.numKeys - 1; i++) {
            rightSibling.keys[i] = rightSibling.keys[i + 1];
            rightSibling.values[i] = rightSibling.values[i + 1];
        }
        if (!rightSibling.isLeaf) {
            for (int i = 0; i < rightSibling.numKeys; i++) {
                rightSibling.children[i] = rightSibling.children[i + 1];
            }
        }
        rightSibling.numKeys--;
    }

    private void mergeChildren(Node parent, int idx) {
        Node left = parent.children[idx];
        Node right = parent.children[idx + 1];
        left.keys[left.numKeys] = parent.keys[idx];
        left.values[left.numKeys] = parent.values[idx];
        left.numKeys++;
        for (int i = 0; i < right.numKeys; i++) {
            left.keys[left.numKeys + i] = right.keys[i];
            left.values[left.numKeys + i] = right.values[i];
        }
        if (!left.isLeaf) {
            for (int i = 0; i <= right.numKeys; i++) {
                left.children[left.numKeys + i] = right.children[i];
            }
        }
        left.numKeys += right.numKeys;
        for (int i = idx; i < parent.numKeys - 1; i++) {
            parent.keys[i] = parent.keys[i + 1];
            parent.values[i] = parent.values[i + 1];
            parent.children[i + 1] = parent.children[i + 2];
        }
        parent.numKeys--;
    }

    // =========================================================
    // UTILITAS
    // =========================================================

    public int getSize() { return size; }

    /** Cetak struktur pohon (level-order sederhana). */
    public void print() {
        System.out.println("=== B-Tree (Order " + ORDER + ") ===");
        printRecursive(root, "", true);
    }

    private void printRecursive(Node node, String prefix, boolean isTail) {
        if (node == null) return;
        System.out.print(prefix + (isTail ? "└── " : "├── "));
        System.out.print("[");
        for (int i = 0; i < node.numKeys; i++) {
            System.out.print(node.keys[i]);
            if (i < node.numKeys - 1) System.out.print("|");
        }
        System.out.println("]" + (node.isLeaf ? " (L)" : " (I)"));
        if (!node.isLeaf) {
            for (int i = 0; i <= node.numKeys; i++) {
                printRecursive(node.children[i],
                        prefix + (isTail ? "    " : "│   "),
                        i == node.numKeys);
            }
        }
    }
}
