package simulator.model;

import simulator.model.Enums.*;

public class JournalEntry {
    private static int nextId = 1;

    private int id;
    private JournalStatus status;
    private OperationType operation;
    private String description;
    private String fileId;
    private String fileName;
    private int[] allocatedBlocks;  // blocks involved (for undo)
    private long timestamp;

    public JournalEntry(OperationType operation, String fileId, String fileName, String description) {
        this.id = nextId++;
        this.status = JournalStatus.PENDING;
        this.operation = operation;
        this.fileId = fileId;
        this.fileName = fileName;
        this.description = description;
        this.timestamp = System.currentTimeMillis();
        this.allocatedBlocks = new int[0];
    }

    public static void resetIdCounter() { nextId = 1; }

    public int getId() { return id; }
    public JournalStatus getStatus() { return status; }
    public void setStatus(JournalStatus status) { this.status = status; }
    public OperationType getOperation() { return operation; }
    public String getDescription() { return description; }
    public String getFileId() { return fileId; }
    public String getFileName() { return fileName; }
    public int[] getAllocatedBlocks() { return allocatedBlocks; }
    public void setAllocatedBlocks(int[] blocks) { this.allocatedBlocks = blocks; }
    public long getTimestamp() { return timestamp; }

    public String getStatusLabel() {
        return switch (status) {
            case PENDING -> "⏳ PENDIENTE";
            case COMMITTED -> "✅ COMMIT";
            case UNDONE -> "↩ DESHECHO";
        };
    }
}
