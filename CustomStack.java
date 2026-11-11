import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A manually implemented, fixed-capacity Stack (LIFO) of {@link Batch} objects built on a plain array.
 * No java.util.Stack / ArrayDeque is used for the stack itself.
 *
 * Invariants:
 *   - Initially top = -1 (empty stack)
 *   - push : top = top + 1, then store element at data[top]
 *   - pop  : read element at data[top], then top = top - 1
 *   - peek : read element at data[top] (top does not change)
 *   - isEmpty : top == -1
 *   - isFull  : top == capacity - 1
 *
 * Time complexity: push, pop, peek, isEmpty, isFull are all O(1).
 * search and removeWhere use only stack operations (pop everything into a helper stack and push it back),
 * so they are O(n). Space complexity: O(capacity).
 */
public class CustomStack {

    /** Thrown when pushing onto a full stack. */
    public static class StackOverflowException extends RuntimeException {
        public StackOverflowException(String message) { super(message); }
    }

    /** Thrown when popping/peeking an empty stack. */
    public static class StackUnderflowException extends RuntimeException {
        public StackUnderflowException(String message) { super(message); }
    }

    /** Result of a stack search. depths are 1-based counted from the TOP (TOP = 1). */
    public static class SearchResult {
        public final List<Batch> matches = new ArrayList<>();
        public final List<Integer> depths = new ArrayList<>();
        public int examined;
    }

    private final Batch[] data;     // fixed-capacity storage
    private final int capacity;
    private int top = -1;           // index of the top element, -1 means empty

    public CustomStack(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive.");
        this.capacity = capacity;
        this.data = new Batch[capacity];
    }

    /** PUSH: add a batch on top of the stack. O(1). */
    public void push(Batch batch) {
        if (batch == null) throw new IllegalArgumentException("Cannot push a null batch.");
        if (isFull()) {
            throw new StackOverflowException("Stack Overflow! The stack is full (capacity = " + capacity + ").");
        }
        top = top + 1;          // move TOP up first
        data[top] = batch;      // then place the element
    }

    /** POP: remove and return the top batch (the latest one). O(1). */
    public Batch pop() {
        if (isEmpty()) {
            throw new StackUnderflowException("Stack Underflow! The stack is empty, nothing to pop.");
        }
        Batch removed = data[top];
        data[top] = null;       // clear the slot so it can be garbage collected
        top = top - 1;          // move TOP down after removing
        return removed;
    }

    /** PEEK: return the top batch without removing it. O(1). */
    public Batch peek() {
        if (isEmpty()) {
            throw new StackUnderflowException("Stack Underflow! The stack is empty, nothing to peek.");
        }
        return data[top];
    }

    /** isEmpty: true when TOP == -1. O(1). */
    public boolean isEmpty() { return top == -1; }

    /** isFull: true when TOP == capacity - 1. O(1). */
    public boolean isFull() { return top == capacity - 1; }

    public int size() { return top + 1; }

    public int getCapacity() { return capacity; }

    public int getTop() { return top; }

    /**
     * SEARCH: walks the stack from TOP to BOTTOM using only POP and PUSH. Every examined batch is moved to a
     * helper stack and afterwards pushed back, so the original order is restored. O(n).
     */
    public SearchResult search(Predicate<Batch> test) {
        SearchResult result = new SearchResult();
        CustomStack helper = new CustomStack(Math.max(1, size()));
        while (!isEmpty()) {
            Batch b = pop();
            helper.push(b);
            result.examined++;
            if (test.test(b)) {
                result.matches.add(b);
                result.depths.add(result.examined);
            }
        }
        while (!helper.isEmpty()) push(helper.pop());      // restore the original order
        return result;
    }

    /**
     * Removes every batch that matches the test, keeping the order of the others (pop all into a helper
     * stack, skip the matches, push the rest back). O(n).
     * @return number of removed batches
     */
    public int removeWhere(Predicate<Batch> test) {
        CustomStack helper = new CustomStack(Math.max(1, size()));
        int removed = 0;
        while (!isEmpty()) {
            Batch b = pop();
            if (test.test(b)) removed++;
            else helper.push(b);
        }
        while (!helper.isEmpty()) push(helper.pop());
        return removed;
    }

    /** Copy of the elements from BOTTOM (index 0) to TOP. O(n) - used only for drawing/saving. */
    public Batch[] toArray() {
        Batch[] copy = new Batch[top + 1];
        for (int i = 0; i <= top; i++) copy[i] = data[i];
        return copy;
    }
}
