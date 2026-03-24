package simulator.model;

import simulator.model.Enums.*;

public class PCB {
    private static int nextPid = 1;

    private int pid;
    private String processName;
    private ProcessState state;
    private OperationType operation;
    private String targetFileId;
    private String targetPath;       // path for CREATE operation
    private int blocksNeeded;        // for CREATE
    private String owner;
    private long createdAt;
    private long startedAt;
    private long finishedAt;
    private String statusMessage;

    public PCB(OperationType op, String targetFileId, String owner) {
        this.pid = nextPid++;
        this.processName = "P" + pid + "-" + op.name();
        this.state = ProcessState.NEW;
        this.operation = op;
        this.targetFileId = targetFileId;
        this.owner = owner;
        this.createdAt = System.currentTimeMillis();
        this.startedAt = -1;
        this.finishedAt = -1;
        this.statusMessage = "En cola";
    }

    public static void resetPidCounter() { nextPid = 1; }

    // ─── Getters & Setters ───────────────────────────────────────────────────

    public int getPid() { return pid; }
    public String getProcessName() { return processName; }
    public ProcessState getState() { return state; }
    public void setState(ProcessState state) { this.state = state; }
    public OperationType getOperation() { return operation; }
    public String getTargetFileId() { return targetFileId; }
    public void setTargetFileId(String id) { this.targetFileId = id; }
    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String path) { this.targetPath = path; }
    public int getBlocksNeeded() { return blocksNeeded; }
    public void setBlocksNeeded(int n) { this.blocksNeeded = n; }
    public String getOwner() { return owner; }
    public long getCreatedAt() { return createdAt; }
    public long getStartedAt() { return startedAt; }
    public void setStartedAt(long t) { this.startedAt = t; }
    public long getFinishedAt() { return finishedAt; }
    public void setFinishedAt(long t) { this.finishedAt = t; }
    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String msg) { this.statusMessage = msg; }
}
