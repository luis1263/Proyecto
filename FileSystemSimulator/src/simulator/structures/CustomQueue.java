package simulator.structures;

public class CustomQueue<T> {
    private final CustomLinkedList<T> list = new CustomLinkedList<>();

    public void enqueue(T data) { list.addLast(data); }
    public T dequeue() { return list.removeFirst(); }
    public T peek() { return list.peekFirst(); }
    public boolean isEmpty() { return list.isEmpty(); }
    public int size() { return list.size(); }
    public void clear() { list.clear(); }

    /** Snapshot as array for iteration without removing. */
    public Object[] toArray() { return list.toArray(); }

    public CustomLinkedList<T> asList() { return list; }
}
