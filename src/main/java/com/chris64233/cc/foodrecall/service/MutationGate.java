package com.chris64233.cc.foodrecall.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Serializes all graph mutations (lot registration, transformations, recall
 * open/close) inside this JVM so that pessimistic row locks are always
 * acquired in a single global order and recall propagation can never miss a
 * concurrently created descendant.
 */
@Component
public class MutationGate {

    private final ReentrantLock lock = new ReentrantLock(true);

    public <T> T execute(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
