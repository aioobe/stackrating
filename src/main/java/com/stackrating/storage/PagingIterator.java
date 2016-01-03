package com.stackrating.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class PagingIterator<T> implements Iterator<List<T>> {

    private final int pageSize;
    private final Iterator<T> underlyingIterator;

    public PagingIterator(Collection<T> collection, int pageSize) {
        this.pageSize = pageSize;
        underlyingIterator = collection.iterator();
    }

    @Override
    public boolean hasNext() {
        return underlyingIterator.hasNext();
    }

    @Override
    public List<T> next() {
        List<T> page = new ArrayList<>();
        while (underlyingIterator.hasNext() && page.size() < pageSize) {
            page.add(underlyingIterator.next());
        }
        return page;
    }

    public static <T> Iterable<List<T>> getPages(Collection<T> collection, int pageSize) {
        return () -> new PagingIterator(collection, pageSize);
    }
}
