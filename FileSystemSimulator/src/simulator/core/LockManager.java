package simulator.core;

import simulator.model.Enums.LockType;
import simulator.structures.CustomLinkedList;

import java.util.concurrent.Semaphore;

/**
 * Manages shared (read) and exclusive (write) locks per file.
 * - Shared lock  : multiple readers allowed simultaneously.
 * - Exclusive lock: only one writer; no readers.
 */
public class LockManager {

    public interface LockListener {
        void onLockChanged(CustomLinkedList<LockInfo> activeLocks);
    }

    public static class LockInfo {
        public String fileId;
        public String fileName;
        public LockType type;
        public int holderPid;
        public long acquiredAt;

        public LockInfo(String fileId, String fileName, LockType type, int holderPid) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.type = type;
            this.holderPid = holderPid;
            this.acquiredAt = System.currentTimeMillis();
        }

        @Override public String toString() {
            return fileName + " [" + type + "] PID=" + holderPid;
        }
    }

    // Per-file control: using parallel arrays (no HashMap)
    private static final int MAX_TRACKED_FILES = 256;
    private final String[]    trackedIds       = new String[MAX_TRACKED_FILES];
    private final Semaphore[] writeSem         = new Semaphore[MAX_TRACKED_FILES];
    private final int[]       readerCount      = new int[MAX_TRACKED_FILES];
    private final Semaphore[] readerMutex      = new Semaphore[MAX_TRACKED_FILES];
    private int trackedCount = 0;

    private final CustomLinkedList<LockInfo> activeLocks = new CustomLinkedList<>();
    private LockListener listener;

    private int getOrCreate(String fileId) {
        for (int i = 0; i < trackedCount; i++)
            if (trackedIds[i].equals(fileId)) return i;
        if (trackedCount >= MAX_TRACKED_FILES) return -1;
        trackedIds[trackedCount] = fileId;
        writeSem[trackedCount]   = new Semaphore(1);
        readerMutex[trackedCount] = new Semaphore(1);
        readerCount[trackedCount] = 0;
        return trackedCount++;
    }

    // ─── Acquire / Release ────────────────────────────────────────────────────

    /**
     * Acquire a shared (read) or exclusive (write) lock.
     * Blocks until available.
     */
    public boolean acquire(String fileId, String fileName, LockType type, int pid) {
        int idx = getOrCreate(fileId);
        if (idx < 0) return false;
        try {
            if (type == LockType.SHARED) {
                readerMutex[idx].acquire();
                readerCount[idx]++;
                if (readerCount[idx] == 1) writeSem[idx].acquire(); // first reader blocks writers
                readerMutex[idx].release();
            } else { // EXCLUSIVE
                writeSem[idx].acquire();
            }
            addLockInfo(fileId, fileName, type, pid);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Release a previously acquired lock. */
    public void release(String fileId, LockType type, int pid) {
        int idx = -1;
        for (int i = 0; i < trackedCount; i++)
            if (trackedIds[i].equals(fileId)) { idx = i; break; }
        if (idx < 0) return;

        try {
            if (type == LockType.SHARED) {
                readerMutex[idx].acquire();
                readerCount[idx]--;
                if (readerCount[idx] == 0) writeSem[idx].release(); // last reader releases
                readerMutex[idx].release();
            } else {
                writeSem[idx].release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        removeLockInfo(fileId, pid);
    }

    // ─── Lock Info Tracking ───────────────────────────────────────────────────

    private synchronized void addLockInfo(String fileId, String fileName, LockType type, int pid) {
        activeLocks.addLast(new LockInfo(fileId, fileName, type, pid));
        notifyListener();
    }

    private synchronized void removeLockInfo(String fileId, int pid) {
        LockInfo toRemove = null;
        for (LockInfo li : activeLocks) {
            if (li.fileId.equals(fileId) && li.holderPid == pid) { toRemove = li; break; }
        }
        if (toRemove != null) { activeLocks.remove(toRemove); notifyListener(); }
    }

    public synchronized CustomLinkedList<LockInfo> getActiveLocks() { return activeLocks; }

    public void setListener(LockListener l) { this.listener = l; }

    private void notifyListener() {
        if (listener != null) listener.onLockChanged(activeLocks);
    }
}
