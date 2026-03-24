package simulator.model;

public class DiskBlock {
    private int id;
    private boolean occupied;
    private String fileId;   // null if free
    private int nextBlock;   // -1 if last block of file or free

    public DiskBlock(int id) {
        this.id = id;
        this.occupied = false;
        this.fileId = null;
        this.nextBlock = -1;
    }

    public void allocate(String fileId) {
        this.occupied = true;
        this.fileId = fileId;
        this.nextBlock = -1;
    }

    public void free() {
        this.occupied = false;
        this.fileId = null;
        this.nextBlock = -1;
    }

    public int getId() { return id; }
    public boolean isOccupied() { return occupied; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public int getNextBlock() { return nextBlock; }
    public void setNextBlock(int nextBlock) { this.nextBlock = nextBlock; }
}
