package com.stackrating.storage;

public interface NonThrowingCloseable extends AutoCloseable {
    @Override
    void close();
}
