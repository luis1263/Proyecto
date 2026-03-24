package simulator.model;

import simulator.model.Enums.OperationType;

public class DiskRequest {
    private static int nextId = 1;

    private int requestId;
    private int blockPosition;       // position on disk (for scheduling)
    private OperationType operation;
    private int processPid;
    private String fileId;
    private String fileName;
    private boolean processed;

    public DiskRequest(int blockPosition, OperationType operation, int processPid,
                       String fileId, String fileName) {
        this.requestId = nextId++;
        this.blockPosition = blockPosition;
        this.operation = operation;
        this.processPid = processPid;
        this.fileId = fileId;
        this.fileName = fileName;
        this.processed = false;
    }

    public static void resetIdCounter() { nextId = 1; }

    public int getRequestId() { return requestId; }
    public int getBlockPosition() { return blockPosition; }
    public OperationType getOperation() { return operation; }
    public int getProcessPid() { return processPid; }
    public String getFileId() { return fileId; }
    public String getFileName() { return fileName; }
    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean p) { this.processed = p; }
}
