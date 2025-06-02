package com.qprint.utils;

import java.util.*;

// TODO: did i just recreate a deque?
public class FixedSizeList<E> extends AbstractList<E> {
    private final int maxSize;
    private final LinkedList<E> list;

    public FixedSizeList(int maxSize) {
        if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be > 0");
        this.maxSize = maxSize;
        this.list = new LinkedList<>();
    }

    @Override
    public boolean add(E e) {
        if (list.size() >= maxSize) {
            list.removeLast(); // Remove oldest
        }
        list.addFirst(e); // Add newest
        return true;
    }

    @Override
    public E remove(int index) {
        return list.remove(index);
    }

    @Override
    public E get(int index) {
        return list.get(index); // index 0 = newest
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public Iterator<E> iterator() {
        return list.iterator(); // Iterates from newest to oldest
    }
}
