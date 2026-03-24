package simulator.core;

import simulator.model.Enums.*;
import simulator.model.PCB;
import simulator.model.DiskRequest;
import simulator.model.FileNode;
import simulator.structures.CustomLinkedList;
import simulator.structures.CustomQueue;

import java.util.concurrent.Semaphore;

/**
 * Manages the process queue and coordinates file-system operations
 * through the DiskScheduler.
 *
 * State transitions: NEW → READY → RUNNING → BLOCKED (waiting for I/O) → TERMINATED
 */
public class ProcessManager {

    public interface ProcessListener {
        void onProcessAdded(PCB pcb);
        void onProcessStateChanged(PCB pcb);
        void onAllProcessesFinished();
        void onLogMessage(String msg);
    }

    private final CustomQueue<PCB>       readyQueue  = new CustomQueue<>();
    private final CustomLinkedList<PCB>  allProcesses = new CustomLinkedList<>();
    private final Semaphore              queueSem    = new Semaphore(0);

    private final FileSystem    fs;
    private final DiskSimulator disk;
    private final DiskScheduler scheduler;
    private final JournalManager journal;
    private final LockManager    lockManager;

    private ProcessListener listener;
    private volatile boolean running = false;
    private Thread processorThread;

    public ProcessManager(FileSystem fs, DiskSimulator disk,
                          DiskScheduler scheduler, JournalManager journal,
                          LockManager lockManager) {
        this.fs          = fs;
        this.disk        = disk;
        this.scheduler   = scheduler;
        this.journal     = journal;
        this.lockManager = lockManager;
    }

    // ─── Submit Process ───────────────────────────────────────────────────────

    public PCB submitProcess(OperationType op, String targetFileId, String owner,
                              int blocksNeeded, String targetPath) {
        PCB pcb = new PCB(op, targetFileId, owner);
        pcb.setBlocksNeeded(blocksNeeded);
        pcb.setTargetPath(targetPath);
        pcb.setState(ProcessState.NEW);
        allProcesses.addLast(pcb);
        if (listener != null) listener.onProcessAdded(pcb);
        // Transition NEW → READY
        transition(pcb, ProcessState.READY);
        readyQueue.enqueue(pcb);
        queueSem.release();
        return pcb;
    }

    // ─── Processor Thread ─────────────────────────────────────────────────────

    public void start() {
        if (running) return;
        running = true;
        processorThread = new Thread(this::loop, "ProcessManager-Thread");
        processorThread.setDaemon(true);
        processorThread.start();
    }

    public void stop() { running = false; }

    private void loop() {
        while (running) {
            try {
                queueSem.acquire();
                if (!running) break;
                PCB pcb = readyQueue.dequeue();
                if (pcb == null) continue;
                executeProcess(pcb);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (listener != null) listener.onAllProcessesFinished();
    }

    private void executeProcess(PCB pcb) {
        transition(pcb, ProcessState.RUNNING);
        pcb.setStartedAt(System.currentTimeMillis());
        log("Ejecutando: " + pcb.getProcessName() + " → " + pcb.getOperation());
        sleep(150);

        String fileId = pcb.getTargetFileId();
        FileNode file = fileId != null ? fs.findById(fileId) : null;

        switch (pcb.getOperation()) {

            case CREATE -> {
                // Journal: write PENDING before allocating
                int journalId = journal.beginOperation(
                    OperationType.CREATE, null,
                    pcb.getTargetPath(),
                    "CREATE " + pcb.getTargetPath() + " [" + pcb.getBlocksNeeded() + " bloques]");

                // BLOCKED: waiting for disk I/O
                transition(pcb, ProcessState.BLOCKED);

                // Determine where to create (use targetPath as "parentId/name")
                String[] parts = pcb.getTargetPath().split("\\|");
                if (parts.length >= 3) {
                    String parentId   = parts[0];
                    String name       = parts[1];
                    int    blocks     = Integer.parseInt(parts[2]);
                    String nodeOwner  = parts.length > 3 ? parts[3] : pcb.getOwner();

                    // Acquire exclusive lock on parent dir (simplified: on a virtual "disk")
                    FileNode created = fs.createFile(parentId, name, blocks, nodeOwner);
                    if (created != null) {
                        // Submit disk request for visualization
                        DiskRequest req = new DiskRequest(
                            created.getFirstBlock(), OperationType.CREATE,
                            pcb.getPid(), created.getId(), name);
                        scheduler.addRequest(req);

                        // Store allocated blocks in journal
                        int first = created.getFirstBlock();
                        int[] allocBlocks = new int[blocks];
                        int cur = first, i = 0;
                        while (cur != -1 && i < blocks) {
                            allocBlocks[i++] = cur;
                            cur = disk.getBlock(cur).getNextBlock();
                        }
                        journal.setAllocatedBlocks(journalId, allocBlocks);
                        journal.commitOperation(journalId);
                        pcb.setTargetFileId(created.getId());
                        log("✅ Archivo creado: " + name + " (primer bloque=" + first + ")");
                    } else {
                        journal.commitOperation(journalId); // no change, still safe
                        log("❌ No se pudo crear: " + name);
                    }
                }
            }

            case READ -> {
                transition(pcb, ProcessState.BLOCKED);
                if (file != null) {
                    boolean locked = lockManager.acquire(
                        file.getId(), file.getName(), LockType.SHARED, pcb.getPid());
                    DiskRequest req = new DiskRequest(
                        Math.max(0, file.getFirstBlock()), OperationType.READ,
                        pcb.getPid(), file.getId(), file.getName());
                    scheduler.addRequest(req);
                    sleep(300);
                    if (locked) lockManager.release(file.getId(), LockType.SHARED, pcb.getPid());
                    log("📖 Lectura: " + file.getName());
                }
            }

            case UPDATE -> {
                transition(pcb, ProcessState.BLOCKED);
                if (file != null) {
                    boolean locked = lockManager.acquire(
                        file.getId(), file.getName(), LockType.EXCLUSIVE, pcb.getPid());
                    DiskRequest req = new DiskRequest(
                        Math.max(0, file.getFirstBlock()), OperationType.UPDATE,
                        pcb.getPid(), file.getId(), file.getName());
                    scheduler.addRequest(req);
                    sleep(300);
                    if (locked) lockManager.release(file.getId(), LockType.EXCLUSIVE, pcb.getPid());
                    log("✏️ Actualización: " + file.getName());
                }
            }

            case DELETE -> {
                if (file != null) {
                    int journalId = journal.beginOperation(
                        OperationType.DELETE, file.getId(), file.getName(),
                        "DELETE " + file.getName());
                    transition(pcb, ProcessState.BLOCKED);
                    boolean locked = lockManager.acquire(
                        file.getId(), file.getName(), LockType.EXCLUSIVE, pcb.getPid());
                    DiskRequest req = new DiskRequest(
                        Math.max(0, file.getFirstBlock()), OperationType.DELETE,
                        pcb.getPid(), file.getId(), file.getName());
                    scheduler.addRequest(req);
                    sleep(200);
                    fs.delete(file.getId());
                    if (locked) lockManager.release(file.getId(), LockType.EXCLUSIVE, pcb.getPid());
                    journal.commitOperation(journalId);
                    log("🗑️ Eliminado: " + file.getName());
                }
            }
        }

        pcb.setFinishedAt(System.currentTimeMillis());
        transition(pcb, ProcessState.TERMINATED);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void transition(PCB pcb, ProcessState newState) {
        pcb.setState(newState);
        if (listener != null)
            javax.swing.SwingUtilities.invokeLater(() -> listener.onProcessStateChanged(pcb));
    }

    private void log(String msg) {
        if (listener != null)
            javax.swing.SwingUtilities.invokeLater(() -> listener.onLogMessage(msg));
    }

    private void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    public void setListener(ProcessListener l) { this.listener = l; }
    public CustomLinkedList<PCB> getAllProcesses() { return allProcesses; }
    public boolean isRunning() { return running; }
}
