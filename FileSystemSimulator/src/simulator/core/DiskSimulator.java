package simulator.core;

import simulator.model.DiskBlock;
import simulator.model.FileNode;
import simulator.structures.CustomLinkedList;

/**
 * Simulates a disk with TOTAL_BLOCKS blocks.
 * Files use CHAINED ALLOCATION: each block stores a pointer to the next block.
 */
public class DiskSimulator {

    public static final int TOTAL_BLOCKS = 200;

    private final DiskBlock[] blocks;
    private int freeBlockCount;

    public DiskSimulator() {
        blocks = new DiskBlock[TOTAL_BLOCKS];
        for (int i = 0; i < TOTAL_BLOCKS; i++) {
            blocks[i] = new DiskBlock(i);
        }
        freeBlockCount = TOTAL_BLOCKS;
    }

    // ─── Allocation ──────────────────────────────────────────────────────────

    /**
     * Allocates {@code count} blocks for a file using chained allocation.
     * Returns the first block id, or -1 if not enough space.
     */
    public synchronized int allocateBlocks(String fileId, int count) {
        if (count <= 0 || freeBlockCount < count) return -1;

        // Gather free block IDs
        int[] freeBlocks = new int[count];
        int found = 0;
        for (int i = 0; i < TOTAL_BLOCKS && found < count; i++) {
            if (!blocks[i].isOccupied()) {
                freeBlocks[found++] = i;
            }
        }
        if (found < count) return -1;

        // Chain them together
        for (int i = 0; i < count; i++) {
            blocks[freeBlocks[i]].allocate(fileId);
            if (i < count - 1) {
                blocks[freeBlocks[i]].setNextBlock(freeBlocks[i + 1]);
            } else {
                blocks[freeBlocks[i]].setNextBlock(-1); // last block
            }
        }
        freeBlockCount -= count;
        return freeBlocks[0];
    }

    /**
     * Allocates blocks for a file starting AT a specific position (for test case loading).
     * Remaining blocks go to the next available free slots.
     */
    public synchronized int allocateBlocksAt(String fileId, int startPos, int count) {
        if (count <= 0) return -1;

        // First, try to use startPos as the first block
        int[] chosen = new int[count];
        int idx = 0;

        if (startPos >= 0 && startPos < TOTAL_BLOCKS && !blocks[startPos].isOccupied()) {
            chosen[idx++] = startPos;
        }

        // Fill remaining from free blocks (skip startPos already chosen)
        for (int i = 0; i < TOTAL_BLOCKS && idx < count; i++) {
            if (!blocks[i].isOccupied() && i != startPos) {
                chosen[idx++] = i;
            }
        }

        if (idx < count) return -1; // not enough space

        for (int i = 0; i < count; i++) {
            blocks[chosen[i]].allocate(fileId);
            blocks[chosen[i]].setNextBlock(i < count - 1 ? chosen[i + 1] : -1);
        }
        freeBlockCount -= count;
        return chosen[0];
    }

    /**
     * Frees all blocks belonging to a file (follows the chain).
     */
    public synchronized void freeBlocks(int firstBlock) {
        int cur = firstBlock;
        while (cur != -1) {
            int next = blocks[cur].getNextBlock();
            blocks[cur].free();
            freeBlockCount++;
            cur = next;
        }
    }

    /**
     * Returns ordered list of block IDs for a file, following the chain.
     */
    public CustomLinkedList<Integer> getFileBlockChain(int firstBlock) {
        CustomLinkedList<Integer> chain = new CustomLinkedList<>();
        int cur = firstBlock;
        while (cur != -1 && cur < TOTAL_BLOCKS) {
            chain.addLast(cur);
            cur = blocks[cur].getNextBlock();
        }
        return chain;
    }

    // ─── Queries ─────────────────────────────────────────────────────────────

    public DiskBlock getBlock(int id) { return blocks[id]; }
    public DiskBlock[] getBlocks() { return blocks; }
    public int getFreeBlockCount() { return freeBlockCount; }
    public int getUsedBlockCount() { return TOTAL_BLOCKS - freeBlockCount; }

    /** Returns the block IDs of all free blocks. */
    public int[] getFreeBlockIds() {
        int[] ids = new int[freeBlockCount];
        int idx = 0;
        for (int i = 0; i < TOTAL_BLOCKS; i++) {
            if (!blocks[i].isOccupied()) ids[idx++] = i;
        }
        return ids;
    }

    /** Full reset */
    public synchronized void reset() {
        for (int i = 0; i < TOTAL_BLOCKS; i++) blocks[i].free();
        freeBlockCount = TOTAL_BLOCKS;
    }
}
