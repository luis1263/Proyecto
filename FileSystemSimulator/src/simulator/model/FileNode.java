package simulator.model;

import simulator.model.Enums.FileType;
import simulator.structures.CustomLinkedList;
import java.awt.Color;
import java.util.UUID;

public class FileNode {
    private String id;
    private String name;
    private FileType type;
    private int sizeInBlocks;   // 0 for directories
    private String owner;
    private Color color;
    private FileNode parent;
    private CustomLinkedList<FileNode> children; // for directories
    private int firstBlock;     // -1 if directory or not yet allocated

    public FileNode(String name, FileType type, int sizeInBlocks, String owner) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.type = type;
        this.sizeInBlocks = sizeInBlocks;
        this.owner = owner;
        this.firstBlock = -1;
        this.children = new CustomLinkedList<>();
        this.color = null;
    }

    // Constructor used for JSON loading (fixed id)
    public FileNode(String id, String name, FileType type, int sizeInBlocks, String owner) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.sizeInBlocks = sizeInBlocks;
        this.owner = owner;
        this.firstBlock = -1;
        this.children = new CustomLinkedList<>();
        this.color = null;
    }

    public void addChild(FileNode child) {
        child.setParent(this);
        children.addLast(child);
    }

    public boolean removeChild(FileNode child) {
        return children.remove(child);
    }

    public FileNode findChild(String name) {
        for (FileNode child : children) {
            if (child.getName().equals(name)) return child;
        }
        return null;
    }

    public boolean isDirectory() { return type == FileType.DIRECTORY; }
    public boolean isFile() { return type == FileType.FILE; }

    // ─── Getters & Setters ───────────────────────────────────────────────────

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public FileType getType() { return type; }
    public int getSizeInBlocks() { return sizeInBlocks; }
    public void setSizeInBlocks(int s) { this.sizeInBlocks = s; }
    public String getOwner() { return owner; }
    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }
    public FileNode getParent() { return parent; }
    public void setParent(FileNode parent) { this.parent = parent; }
    public CustomLinkedList<FileNode> getChildren() { return children; }
    public int getFirstBlock() { return firstBlock; }
    public void setFirstBlock(int firstBlock) { this.firstBlock = firstBlock; }

    @Override
    public String toString() { return name; }
}
