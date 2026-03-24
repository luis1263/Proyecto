package simulator.core;

import simulator.model.Enums.*;
import simulator.model.FileNode;
import simulator.structures.CustomLinkedList;
import simulator.util.ColorManager;

/**
 * Manages the hierarchical file-system tree.
 * All CRUD operations go through here; block allocation is delegated to DiskSimulator.
 */
public class FileSystem {

    private final FileNode root;
    private final DiskSimulator disk;
    private final ColorManager colorManager;

    // Map file ID → FileNode (using parallel arrays — no HashMap)
    private static final int MAX_FILES = 512;
    private final String[]   fileIds   = new String[MAX_FILES];
    private final FileNode[] fileIndex = new FileNode[MAX_FILES];
    private int fileCount = 0;

    public FileSystem(DiskSimulator disk, ColorManager colorManager) {
        this.disk         = disk;
        this.colorManager = colorManager;
        root = new FileNode("root", FileType.DIRECTORY, 0, "admin");
        registerNode(root);
    }

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    /** Create a file inside parentPath (e.g., "/root/docs"). Returns the new node or null on error. */
    public FileNode createFile(String parentId, String name, int sizeInBlocks, String owner) {
        FileNode parent = findById(parentId);
        if (parent == null || !parent.isDirectory()) return null;
        if (parent.findChild(name) != null) return null; // duplicate
        if (disk.getFreeBlockCount() < sizeInBlocks) return null;

        FileNode file = new FileNode(name, FileType.FILE, sizeInBlocks, owner);
        file.setColor(colorManager.assignColor(file.getId()));
        int firstBlock = disk.allocateBlocks(file.getId(), sizeInBlocks);
        if (firstBlock == -1) return null;
        file.setFirstBlock(firstBlock);
        parent.addChild(file);
        registerNode(file);
        return file;
    }

    /** Create a file at a FIXED starting block position (for test-case loading). */
    public FileNode createFileAt(String parentId, String name, int sizeInBlocks,
                                  String owner, int startBlock) {
        FileNode parent = findById(parentId);
        if (parent == null || !parent.isDirectory()) return null;

        FileNode file = new FileNode(name, FileType.FILE, sizeInBlocks, owner);
        file.setColor(colorManager.assignColor(file.getId()));
        int firstBlock = disk.allocateBlocksAt(file.getId(), startBlock, sizeInBlocks);
        if (firstBlock == -1) return null;
        file.setFirstBlock(firstBlock);
        parent.addChild(file);
        registerNode(file);
        return file;
    }

    /** Create a directory. */
    public FileNode createDirectory(String parentId, String name, String owner) {
        FileNode parent = findById(parentId);
        if (parent == null || !parent.isDirectory()) return null;
        if (parent.findChild(name) != null) return null;

        FileNode dir = new FileNode(name, FileType.DIRECTORY, 0, owner);
        parent.addChild(dir);
        registerNode(dir);
        return dir;
    }

    /** Rename a file or directory (admin only). */
    public boolean rename(String nodeId, String newName) {
        FileNode node = findById(nodeId);
        if (node == null || node == root) return false;
        // Check sibling duplicate
        if (node.getParent() != null && node.getParent().findChild(newName) != null) return false;
        node.setName(newName);
        return true;
    }

    /** Delete a file or directory recursively. */
    public boolean delete(String nodeId) {
        FileNode node = findById(nodeId);
        if (node == null || node == root) return false;

        // Recursively free
        deleteRecursive(node);

        // Remove from parent
        if (node.getParent() != null) node.getParent().removeChild(node);
        return true;
    }

    private void deleteRecursive(FileNode node) {
        if (node.isFile()) {
            if (node.getFirstBlock() >= 0) disk.freeBlocks(node.getFirstBlock());
            colorManager.releaseColor(node.getId());
            unregisterNode(node);
        } else {
            for (FileNode child : node.getChildren()) deleteRecursive(child);
            unregisterNode(node);
        }
    }

    // ─── Index Management ─────────────────────────────────────────────────────

    private void registerNode(FileNode node) {
        if (fileCount < MAX_FILES) {
            fileIds[fileCount]   = node.getId();
            fileIndex[fileCount] = node;
            fileCount++;
        }
    }

    private void unregisterNode(FileNode node) {
        for (int i = 0; i < fileCount; i++) {
            if (fileIds[i] != null && fileIds[i].equals(node.getId())) {
                // Shift left
                System.arraycopy(fileIds,   i + 1, fileIds,   i, fileCount - i - 1);
                System.arraycopy(fileIndex, i + 1, fileIndex, i, fileCount - i - 1);
                fileCount--;
                fileIds[fileCount] = null;
                fileIndex[fileCount] = null;
                return;
            }
        }
    }

    public FileNode findById(String id) {
        if (id == null) return null;
        for (int i = 0; i < fileCount; i++) {
            if (id.equals(fileIds[i])) return fileIndex[i];
        }
        return null;
    }

    /** Find node by absolute path (e.g., "root/docs/file.txt"). */
    public FileNode findByPath(String path) {
        if (path == null || path.isEmpty()) return root;
        String[] parts = path.split("/");
        FileNode cur = root;
        for (String part : parts) {
            if (part.isEmpty() || part.equals("root")) continue;
            cur = cur.findChild(part);
            if (cur == null) return null;
        }
        return cur;
    }

    /** Returns all file nodes (non-directory) in a flat list. */
    public CustomLinkedList<FileNode> getAllFiles() {
        CustomLinkedList<FileNode> list = new CustomLinkedList<>();
        collectFiles(root, list);
        return list;
    }

    private void collectFiles(FileNode node, CustomLinkedList<FileNode> list) {
        if (node.isFile()) {
            list.addLast(node);
        } else {
            for (FileNode child : node.getChildren()) collectFiles(child, list);
        }
    }

    public FileNode getRoot() { return root; }

    /** Full system reset. */
    public void reset() {
        // Delete all children of root
        CustomLinkedList<FileNode> children = new CustomLinkedList<>();
        for (FileNode c : root.getChildren()) children.addLast(c);
        for (FileNode c : children) delete(c.getId());
    }
}
