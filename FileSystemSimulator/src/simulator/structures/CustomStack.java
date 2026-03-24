package simulator.structures;

public class CustomStack<T> {
    private final CustomLinkedList<T> list = new CustomLinkedList<>();

    public void push(T data) { list.addFirst(data); }
    public T pop() { return list.removeFirst(); }
    public T peek() { return list.peekFirst(); }
    public boolean isEmpty() { return list.isEmpty(); }
    public int size() { return list.size(); }
}
