package simulator.core;

import simulator.model.DiskRequest;
import simulator.model.Enums.*;
import simulator.structures.CustomLinkedList;
import simulator.structures.CustomQueue;

import java.util.concurrent.Semaphore;

/**
 * Disk Scheduler implementing FIFO, SSTF, SCAN and C-SCAN.
 * Runs its own thread; GUI registers a listener to receive head-position updates.
 */
public class DiskScheduler {

    public interface SchedulerListener {
        void onHeadMoved(int newPosition, DiskRequest currentRequest);
        void onRequestProcessed(DiskRequest request, CustomLinkedList<Integer> order);
        void onQueueChanged(CustomLinkedList<DiskRequest> pending);
        void onSchedulerFinished();
    }

    private static final int STEP_DELAY_MS = 80;   // delay per head-step for animation

    private SchedulingPolicy policy;
    private ScanDirection scanDirection;
    private int headPosition;
    private volatile boolean running;

    private final CustomQueue<DiskRequest>     requestQueue = new CustomQueue<>();
    private final CustomLinkedList<DiskRequest> processedLog = new CustomLinkedList<>();
    private final Semaphore                     queueSem     = new Semaphore(0);

    private SchedulerListener listener;
    private Thread schedulerThread;

    public DiskScheduler(int initialHead, SchedulingPolicy policy) {
        this.headPosition   = initialHead;
        this.policy         = policy;
        this.scanDirection  = ScanDirection.UP;
        this.running        = false;
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    public synchronized void addRequest(DiskRequest req) {
        requestQueue.enqueue(req);
        queueSem.release();
        if (listener != null)
            listener.onQueueChanged(requestQueue.asList());
    }

    public void setListener(SchedulerListener l) { this.listener = l; }
    public void setPolicy(SchedulingPolicy p)    { this.policy = p; }
    public void setScanDirection(ScanDirection d){ this.scanDirection = d; }
    public void setHeadPosition(int pos)         { this.headPosition = pos; }
    public SchedulingPolicy getPolicy()          { return policy; }
    public int getHeadPosition()                 { return headPosition; }
    public boolean isRunning()                   { return running; }

    public synchronized int getPendingCount() { return requestQueue.size(); }

    /** Start processing all currently queued requests. */
    public void startProcessing() {
        if (running) return;
        running = true;
        schedulerThread = new Thread(this::processingLoop, "DiskScheduler-Thread");
        schedulerThread.setDaemon(true);
        schedulerThread.start();
    }

    public void stopProcessing() { running = false; }

    // ─── Core Processing Loop ─────────────────────────────────────────────────

    private void processingLoop() {
        while (running) {
            // Drain all pending requests into a local list, then order them
            CustomLinkedList<DiskRequest> batch = drainQueue();
            if (batch.isEmpty()) {
                running = false;
                if (listener != null) listener.onSchedulerFinished();
                break;
            }

            CustomLinkedList<DiskRequest> ordered = order(batch);
            notifyQueueChanged(ordered);

            for (DiskRequest req : ordered) {
                if (!running) break;
                animateHead(req.getBlockPosition());
                req.setProcessed(true);
                processedLog.addLast(req);
                if (listener != null)
                    listener.onRequestProcessed(req, buildOrderList(ordered));
                sleep(200);

                // Check if new requests arrived while processing
                if (!requestQueue.isEmpty()) {
                    CustomLinkedList<DiskRequest> extra = drainQueue();
                    // Re-add them to front for next batch (simplified: just re-add)
                    for (DiskRequest extra_req : extra) {
                        requestQueue.enqueue(extra_req);
                    }
                }
            }
        }
        running = false;
        if (listener != null) listener.onSchedulerFinished();
    }

    private synchronized CustomLinkedList<DiskRequest> drainQueue() {
        CustomLinkedList<DiskRequest> batch = new CustomLinkedList<>();
        while (!requestQueue.isEmpty()) batch.addLast(requestQueue.dequeue());
        return batch;
    }

    private void notifyQueueChanged(CustomLinkedList<DiskRequest> list) {
        if (listener != null) listener.onQueueChanged(list);
    }

    // ─── Scheduling Algorithms ────────────────────────────────────────────────

    private CustomLinkedList<DiskRequest> order(CustomLinkedList<DiskRequest> batch) {
        return switch (policy) {
            case FIFO  -> fifo(batch);
            case SSTF  -> sstf(batch);
            case SCAN  -> scan(batch);
            case CSCAN -> cscan(batch);
        };
    }

    /** FIFO – service in arrival order (already in order). */
    private CustomLinkedList<DiskRequest> fifo(CustomLinkedList<DiskRequest> batch) {
        return batch; // already FIFO
    }

    /** SSTF – always pick closest to current head. */
    private CustomLinkedList<DiskRequest> sstf(CustomLinkedList<DiskRequest> batch) {
        CustomLinkedList<DiskRequest> result = new CustomLinkedList<>();
        // Copy to array for manipulation
        int n = batch.size();
        DiskRequest[] arr = new DiskRequest[n];
        int i = 0;
        for (DiskRequest r : batch) arr[i++] = r;
        boolean[] used = new boolean[n];

        int cur = headPosition;
        for (int step = 0; step < n; step++) {
            int minDist = Integer.MAX_VALUE;
            int minIdx  = -1;
            for (int j = 0; j < n; j++) {
                if (!used[j]) {
                    int dist = Math.abs(arr[j].getBlockPosition() - cur);
                    if (dist < minDist) { minDist = dist; minIdx = j; }
                }
            }
            used[minIdx] = true;
            result.addLast(arr[minIdx]);
            cur = arr[minIdx].getBlockPosition();
        }
        return result;
    }

    /**
     * SCAN – sweep in one direction, service all requests, then reverse and service rest.
     */
    private CustomLinkedList<DiskRequest> scan(CustomLinkedList<DiskRequest> batch) {
        CustomLinkedList<DiskRequest> result = new CustomLinkedList<>();
        int n = batch.size();
        DiskRequest[] arr = sortedByPosition(batch, n);

        // Split: <= head and > head
        CustomLinkedList<DiskRequest> above = new CustomLinkedList<>();
        CustomLinkedList<DiskRequest> below = new CustomLinkedList<>();
        for (DiskRequest r : arr) {
            if (r.getBlockPosition() >= headPosition) above.addLast(r);
            else                                      below.addLast(r);
        }

        if (scanDirection == ScanDirection.UP) {
            // Service above (ascending), then below descending
            for (DiskRequest r : above) result.addLast(r);
            // below descending: already sorted ascending, iterate backwards
            DiskRequest[] belowArr = new DiskRequest[below.size()];
            int idx = 0;
            for (DiskRequest r : below) belowArr[idx++] = r;
            for (int j = belowArr.length - 1; j >= 0; j--) result.addLast(belowArr[j]);
        } else {
            // Service below descending, then above ascending
            DiskRequest[] belowArr = new DiskRequest[below.size()];
            int idx = 0;
            for (DiskRequest r : below) belowArr[idx++] = r;
            for (int j = belowArr.length - 1; j >= 0; j--) result.addLast(belowArr[j]);
            for (DiskRequest r : above) result.addLast(r);
        }
        return result;
    }

    /**
     * C-SCAN – sweep UP, service all requests in that direction,
     * then jump to position 0 and service the remaining.
     */
    private CustomLinkedList<DiskRequest> cscan(CustomLinkedList<DiskRequest> batch) {
        CustomLinkedList<DiskRequest> result = new CustomLinkedList<>();
        int n = batch.size();
        DiskRequest[] arr = sortedByPosition(batch, n);

        CustomLinkedList<DiskRequest> above = new CustomLinkedList<>();
        CustomLinkedList<DiskRequest> below = new CustomLinkedList<>();
        for (DiskRequest r : arr) {
            if (r.getBlockPosition() >= headPosition) above.addLast(r);
            else                                      below.addLast(r);
        }
        // Service above ascending, then below ascending
        for (DiskRequest r : above) result.addLast(r);
        for (DiskRequest r : below) result.addLast(r);
        return result;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Sort disk requests by block position (insertion sort – no java.util.Arrays.sort on objects). */
    private DiskRequest[] sortedByPosition(CustomLinkedList<DiskRequest> list, int n) {
        DiskRequest[] arr = new DiskRequest[n];
        int i = 0;
        for (DiskRequest r : list) arr[i++] = r;
        // Insertion sort
        for (int j = 1; j < n; j++) {
            DiskRequest key = arr[j];
            int k = j - 1;
            while (k >= 0 && arr[k].getBlockPosition() > key.getBlockPosition()) {
                arr[k + 1] = arr[k];
                k--;
            }
            arr[k + 1] = key;
        }
        return arr;
    }

    /** Move head one step at a time towards target, notifying listener. */
    private void animateHead(int target) {
        while (headPosition != target && running) {
            if (headPosition < target) headPosition++;
            else                       headPosition--;
            if (listener != null) listener.onHeadMoved(headPosition, null);
            sleep(STEP_DELAY_MS);
        }
    }

    private CustomLinkedList<Integer> buildOrderList(CustomLinkedList<DiskRequest> ordered) {
        CustomLinkedList<Integer> positions = new CustomLinkedList<>();
        for (DiskRequest r : ordered) positions.addLast(r.getBlockPosition());
        return positions;
    }

    private void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    public CustomLinkedList<DiskRequest> getProcessedLog() { return processedLog; }
}
