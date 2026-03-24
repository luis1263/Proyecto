package simulator.core;

import simulator.model.Enums.*;
import simulator.model.FileNode;
import simulator.model.DiskRequest;
import simulator.model.JournalEntry;
import simulator.model.PCB;
import simulator.structures.CustomLinkedList;
import simulator.util.ColorManager;
import simulator.util.JsonManager;
import simulator.util.JsonManager.TestCase;

import java.io.IOException;

/**
 * Facade that wires together all subsystems.
 * The GUI interacts exclusively with this class.
 */
public class FileSystemController {

    // ─── Subsystems ──────────────────────────────────────────────────────────
    private final ColorManager   colorManager;
    private final DiskSimulator  disk;
    private final FileSystem     fs;
    private final JournalManager journal;
    private final LockManager    lockManager;
    private       DiskScheduler  scheduler;
    private       ProcessManager processManager;

    // ─── State ───────────────────────────────────────────────────────────────
    private UserMode mode             = UserMode.ADMIN;
    private boolean  simulateCrash    = false;
    private int      initialHead      = 0;

    // ─── Listeners (wired from GUI) ───────────────────────────────────────────
    private DiskScheduler.SchedulerListener  schedulerListener;
    private ProcessManager.ProcessListener   processListener;
    private JournalManager.JournalListener   journalListener;
    private LockManager.LockListener         lockListener;

    public FileSystemController() {
        colorManager   = new ColorManager();
        disk           = new DiskSimulator();
        fs             = new FileSystem(disk, colorManager);
        journal        = new JournalManager();
        lockManager    = new LockManager();
        rebuildSchedulerAndPM(SchedulingPolicy.FIFO, 0);
    }

    private void rebuildSchedulerAndPM(SchedulingPolicy policy, int head) {
        if (processManager != null) processManager.stop();
        if (scheduler      != null) scheduler.stopProcessing();

        scheduler = new DiskScheduler(head, policy);
        if (schedulerListener != null) scheduler.setListener(schedulerListener);

        processManager = new ProcessManager(fs, disk, scheduler, journal, lockManager);
        if (processListener != null) processManager.setListener(processListener);

        journal.setListener(journalListener);
        lockManager.setListener(lockListener);
        processManager.start();
    }

    // ─── File Operations ──────────────────────────────────────────────────────

    public FileNode createFile(String parentId, String name, int sizeBlocks, String owner) {
        if (mode == UserMode.USER) return null; // USER can't create

        // Journal: PENDING before allocating
        int jid = journal.beginOperation(OperationType.CREATE, null, name,
                "CREATE " + name + " [" + sizeBlocks + " bloques] en parent=" + parentId);

        if (simulateCrash) {
            // Don't commit → entry stays PENDING
            return null;
        }

        FileNode created = fs.createFile(parentId, name, sizeBlocks, owner);
        if (created == null) { journal.commitOperation(jid); return null; }

        // Store allocated block chain in journal
        storeBlocksInJournal(jid, created);
        journal.commitOperation(jid);
        return created;
    }

    public FileNode createDirectory(String parentId, String name, String owner) {
        if (mode == UserMode.USER) return null;
        return fs.createDirectory(parentId, name, owner);
    }

    public boolean rename(String nodeId, String newName) {
        if (mode == UserMode.USER) return false;
        return fs.rename(nodeId, newName);
    }

    public boolean deleteNode(String nodeId) {
        if (mode == UserMode.USER) return false;
        FileNode node = fs.findById(nodeId);
        if (node == null) return false;

        int jid = journal.beginOperation(OperationType.DELETE, nodeId,
                node.getName(), "DELETE " + node.getName());
        if (simulateCrash) return false;

        boolean ok = fs.delete(nodeId);
        journal.commitOperation(jid);
        return ok;
    }

    // ─── Process Submission ───────────────────────────────────────────────────

    /** Submit a CRUD I/O process to the process queue. */
    public PCB submitIOProcess(OperationType op, String fileId, String owner,
                                int blocksNeeded, String targetPath) {
        return processManager.submitProcess(op, fileId, owner, blocksNeeded, targetPath);
    }

    // ─── Disk Scheduling ──────────────────────────────────────────────────────

    public void setSchedulingPolicy(SchedulingPolicy policy) {
        scheduler.setPolicy(policy);
    }

    public void setScanDirection(ScanDirection dir) {
        scheduler.setScanDirection(dir);
    }

    public void setInitialHead(int pos) {
        this.initialHead = pos;
        scheduler.setHeadPosition(pos);
    }

    public void startDiskScheduler() {
        scheduler.startProcessing();
    }

    public void addDiskRequest(DiskRequest req) {
        scheduler.addRequest(req);
    }

    // ─── Journal & Recovery ───────────────────────────────────────────────────

    public void setSimulateCrash(boolean crash) { this.simulateCrash = crash; }
    public boolean isSimulateCrash()            { return simulateCrash; }

    public CustomLinkedList<JournalEntry> recover() {
        return journal.recover(fs, disk);
    }

    // ─── Mode ─────────────────────────────────────────────────────────────────

    public void setMode(UserMode mode)  { this.mode = mode; }
    public UserMode getMode()           { return mode; }
    public boolean isAdmin()            { return mode == UserMode.ADMIN; }

    // ─── Save / Load ──────────────────────────────────────────────────────────

    public void saveState(String path) throws IOException {
        JsonManager.saveState(fs, path);
    }

    public void loadState(String path) throws IOException {
        JsonManager.loadState(fs, disk, path);
    }

    public void loadTestCase(String path) throws IOException {
        TestCase tc = JsonManager.loadTestCase(path);
        // Reset
        reset(tc.initialHead, scheduler.getPolicy());

        // Create system files at their fixed block positions
        String rootId = fs.getRoot().getId();
        for (JsonManager.TestCase.SystemFile sf : tc.systemFiles) {
            fs.createFileAt(rootId, sf.name, sf.blocks, "system", sf.blockPos);
        }

        // Add disk requests
        int pid = 99;
        for (JsonManager.TestCase.RequestEntry req : tc.requests) {
            OperationType op;
            try { op = OperationType.valueOf(req.op); }
            catch (Exception e) { op = OperationType.READ; }
            DiskRequest dr = new DiskRequest(req.pos, op, pid++, "tc-" + req.pos, "req" + req.pos);
            scheduler.addRequest(dr);
        }
    }

    /** Full reset with new head position and policy. */
    public void reset(int headPos, SchedulingPolicy policy) {
        if (processManager != null) processManager.stop();
        if (scheduler != null) scheduler.stopProcessing();

        fs.reset();
        disk.reset();
        colorManager.reset();
        journal.clear();
        PCB.resetPidCounter();
        DiskRequest.resetIdCounter();
        JournalEntry.resetIdCounter();

        rebuildSchedulerAndPM(policy, headPos);
        this.initialHead = headPos;
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public FileSystem    getFs()          { return fs; }
    public DiskSimulator getDisk()        { return disk; }
    public DiskScheduler getScheduler()   { return scheduler; }
    public JournalManager getJournal()    { return journal; }
    public LockManager   getLockManager() { return lockManager; }
    public ProcessManager getProcessManager() { return processManager; }
    public ColorManager  getColorManager(){ return colorManager; }
    public int           getInitialHead() { return initialHead; }

    // ─── Listener wiring ─────────────────────────────────────────────────────

    public void setSchedulerListener(DiskScheduler.SchedulerListener l) {
        schedulerListener = l;
        scheduler.setListener(l);
    }
    public void setProcessListener(ProcessManager.ProcessListener l) {
        processListener = l;
        processManager.setListener(l);
    }
    public void setJournalListener(JournalManager.JournalListener l) {
        journalListener = l;
        journal.setListener(l);
    }
    public void setLockListener(LockManager.LockListener l) {
        lockListener = l;
        lockManager.setListener(l);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void storeBlocksInJournal(int jid, FileNode file) {
        int count = file.getSizeInBlocks();
        int[] blks = new int[count];
        int cur = file.getFirstBlock(), i = 0;
        while (cur != -1 && i < count) {
            blks[i++] = cur;
            cur = disk.getBlock(cur).getNextBlock();
        }
        journal.setAllocatedBlocks(jid, blks);
    }
}
