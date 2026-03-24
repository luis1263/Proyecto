package simulator.core;

import simulator.model.Enums.*;
import simulator.model.JournalEntry;
import simulator.structures.CustomLinkedList;

/**
 * Implements a write-ahead log (journaling) for critical file-system operations.
 *
 * Flow:
 *   1. beginOperation(...)   → creates PENDING entry, returns entry id
 *   2. commitOperation(id)   → marks entry as COMMITTED
 *
 * On crash simulation, commitOperation is never called → entry stays PENDING.
 * recover() undoes all PENDING entries.
 */
public class JournalManager {

    public interface JournalListener {
        void onJournalUpdated(CustomLinkedList<JournalEntry> entries);
    }

    private final CustomLinkedList<JournalEntry> entries = new CustomLinkedList<>();
    private JournalListener listener;

    // ─── Write-Ahead Log API ──────────────────────────────────────────────────

    /** Write PENDING entry before executing an operation. Returns journal entry id. */
    public int beginOperation(OperationType op, String fileId, String fileName, String description) {
        JournalEntry entry = new JournalEntry(op, fileId, fileName, description);
        entries.addLast(entry);
        notifyListener();
        return entry.getId();
    }

    /** Mark entry as COMMITTED after successful operation. */
    public void commitOperation(int entryId) {
        JournalEntry e = findById(entryId);
        if (e != null) {
            e.setStatus(JournalStatus.COMMITTED);
            notifyListener();
        }
    }

    /** Store which blocks were allocated (for undo). */
    public void setAllocatedBlocks(int entryId, int[] blocks) {
        JournalEntry e = findById(entryId);
        if (e != null) e.setAllocatedBlocks(blocks);
    }

    // ─── Recovery ─────────────────────────────────────────────────────────────

    /**
     * Scans for PENDING entries and applies undo using the provided FileSystem and DiskSimulator.
     * Returns the list of undone entries.
     */
    public CustomLinkedList<JournalEntry> recover(FileSystem fs, DiskSimulator disk) {
        CustomLinkedList<JournalEntry> undone = new CustomLinkedList<>();
        for (JournalEntry e : entries) {
            if (e.getStatus() == JournalStatus.PENDING) {
                applyUndo(e, fs, disk);
                e.setStatus(JournalStatus.UNDONE);
                undone.addLast(e);
            }
        }
        notifyListener();
        return undone;
    }

    private void applyUndo(JournalEntry e, FileSystem fs, DiskSimulator disk) {
        switch (e.getOperation()) {
            case CREATE -> {
                // If file was partially created, remove it from FS and free blocks
                if (e.getFileId() != null) {
                    // Free blocks manually if specified
                    if (e.getAllocatedBlocks() != null && e.getAllocatedBlocks().length > 0) {
                        for (int b : e.getAllocatedBlocks()) {
                            if (b >= 0 && b < DiskSimulator.TOTAL_BLOCKS) {
                                disk.getBlock(b).free();
                            }
                        }
                    }
                    // Remove from file system if registered
                    fs.delete(e.getFileId());
                }
            }
            case DELETE -> {
                // On undo delete: we can't easily restore; log only
                // In a full implementation we'd restore from a pre-delete snapshot
            }
            default -> {}
        }
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    public boolean hasPendingEntries() {
        for (JournalEntry e : entries)
            if (e.getStatus() == JournalStatus.PENDING) return true;
        return false;
    }

    public CustomLinkedList<JournalEntry> getEntries() { return entries; }

    public JournalEntry findById(int id) {
        for (JournalEntry e : entries)
            if (e.getId() == id) return e;
        return null;
    }

    public void setListener(JournalListener l) { this.listener = l; }

    private void notifyListener() {
        if (listener != null) listener.onJournalUpdated(entries);
    }

    public void clear() { entries.clear(); JournalEntry.resetIdCounter(); notifyListener(); }
}
