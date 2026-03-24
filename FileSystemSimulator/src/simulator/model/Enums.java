package simulator.model;

public class Enums {

    public enum FileType { FILE, DIRECTORY }

    public enum ProcessState { NEW, READY, RUNNING, BLOCKED, TERMINATED }

    public enum OperationType { CREATE, READ, UPDATE, DELETE }

    public enum JournalStatus { PENDING, COMMITTED, UNDONE }

    public enum LockType { SHARED, EXCLUSIVE }

    public enum SchedulingPolicy { FIFO, SSTF, SCAN, CSCAN }

    public enum UserMode { ADMIN, USER }

    public enum ScanDirection { UP, DOWN }
}
