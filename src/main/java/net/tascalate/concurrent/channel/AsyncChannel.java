/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.tascalate.concurrent.channel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Promises;
import net.tascalate.concurrent.Try;

public class AsyncChannel<T> implements AsyncSendChannel<T>, AsyncReceiveChannel<T> {

    private final int capacity;
    private final ReentrantLock lock;

    // Queue<Object> instead of Queue<Optional<T>> — no per-element allocation
    private final Queue<Object> buffer = new ArrayDeque<>();
    private final Queue<WaitingSender<T>> waitSenders = new ArrayDeque<>();
    private final Queue<ChannelOperationPromise<T>> waitReceivers = new ArrayDeque<>();

    private CloseMode closedMode = null; // null = OPEN
    
    
    public static <T> AsyncChannel<T> rendezvous() {
        return rendezvous(false);
    }
    
    public static <T> AsyncChannel<T> rendezvous(boolean fair) {
        return new AsyncChannel<>(0, fair);
    }
    
    public static <T> AsyncChannel<T> buffered(int capacity) {
        return buffered(capacity, false);
    }
    
    public static <T> AsyncChannel<T> buffered(int capacity, boolean fair) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be > 0 for buffered channel");
        }
        return new AsyncChannel<>(capacity, fair);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> Promise<SelectResult<T>> select(SelectCase.Typed<T>... cases) {
        return Select.select(cases);
    }

    @SuppressWarnings("unchecked")
    public static <T> Promise<SelectResult<Object>> select(SelectCase<T>... cases) {
        return Select.select(cases);
    }

    public AsyncChannel(int capacity) {
        this(capacity, false);
    }

    public AsyncChannel(int capacity, boolean fair) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity must be >= 0");
        }
        this.capacity = capacity;
        this.lock = new ReentrantLock(fair);
    }

    @Override
    public Promise<Void> send(T value) {
        while (true) {
            ChannelOperationPromise<Void> senderFuture = new ChannelOperationPromise<>();
            ChannelOperationPromise<T> matchedReceiver = null;
            boolean handoff = false;

            lock.lock();
            try {
                if (closedMode != null) {
                    return Promises.failure(new IllegalStateException("Channel is closed"));
                }

                ChannelOperationPromise<T> matched;
                while ((matched = waitReceivers.poll()) != null) {
                    if (matched.isDone()) {
                        continue;
                    }
                    matchedReceiver = matched;
                    handoff = true;
                    break;
                }

                if (handoff) {
                    // rendezvous — completed outside lock
                } else if (buffer.size() < capacity) {
                    buffer.add(wrap(value));
                    return Promises.success(null);
                } else {
                    waitSenders.add(new WaitingSender<>(value, senderFuture));
                    return senderFuture;
                }
            } finally {
                lock.unlock();
            }

            if (handoff) {
                if (matchedReceiver.settledSuccess(value)) {
                    return Promises.success(null);
                }
                // receiver cancelled concurrently → retry
            }
        }
    }

    @Override
    public Promise<T> receive() {
        while (true) {
            ChannelOperationPromise<T> receiverFuture = new ChannelOperationPromise<>();
            T result = null;
            boolean completedImmediately = false;
            WaitingSender<T> rendezvousSender = null;

            lock.lock();
            try {
                if (closedMode != null) {
                    if (closedMode == CloseMode.FAIL_ALL) {
                        return Promises.failure(new IllegalStateException("Channel is closed"));
                    }
                    if (buffer.isEmpty() && waitSenders.isEmpty()) {
                        return Promises.success(null);
                    }
                }

                if (!buffer.isEmpty()) {
                    // ── BUFFER PATH ───────────────────────────────────
                    result = unwrap(buffer.poll());
                    completedImmediately = true;

                    WaitingSender<T> ws;
                    while ((ws = waitSenders.poll()) != null) {
                        if (ws.isDone()) {
                            continue;
                        }
                        if (ws.complete()) {
                            buffer.add(wrap(ws.value));
                            break;
                        }
                    }
                } else {
                    // ── RENDEZVOUS PATH ───────────────────────────────
                    WaitingSender<T> ws;
                    while ((ws = waitSenders.poll()) != null) {
                        if (ws.isDone()) {
                            continue;
                        }
                        rendezvousSender = ws;
                        result = ws.value;
                        completedImmediately = true;
                        break;
                    }

                    if (!completedImmediately) {
                        if (closedMode != null) {
                            return Promises.success(null);
                        }
                        waitReceivers.add(receiverFuture);
                        return receiverFuture;
                    }
                }
            } finally {
                lock.unlock();
            }

            if (completedImmediately) {
                if (rendezvousSender != null) {
                    if (!rendezvousSender.complete()) {
                        continue;
                    }
                }
                return Promises.success(result);
            }

            return receiverFuture;
        }
    }

    @Override
    public Try<T> tryReceive() {
        lock.lock();
        try {
            if (closedMode != null) {
                if (closedMode == CloseMode.FAIL_ALL) {
                    return Try.failure(new IllegalStateException("Channel is closed"));
                }
                if (buffer.isEmpty() && waitSenders.isEmpty()) {
                    return Try.success(null); // EOF
                }
            }

            // ── buffered path (only reachable when capacity > 0) ──
            if (!buffer.isEmpty()) {
                T result = unwrap(buffer.poll());
                promoteOneSender();
                return Try.success(result);
            }

            // ── rendezvous path (the ONLY path when capacity == 0) ──
            WaitingSender<T> ws;
            while ((ws = waitSenders.poll()) != null) {
                if (ws.isDone()) {
                    continue; // skip cancelled
                }
                if (ws.complete()) { // atomic claim
                    return Try.success(ws.value);
                }
                // cancelled concurrently → try next
            }

            return null; // not ready
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean trySend(T value) {
        while (true) {
            ChannelOperationPromise<T> matchedReceiver = null;

            lock.lock();
            try {
                if (closedMode != null) {
                    return false;
                }

                ChannelOperationPromise<T> matched;
                while ((matched = waitReceivers.poll()) != null) {
                    if (matched.isDone()) {
                        continue;
                    }
                    matchedReceiver = matched;
                    break;
                }

                if (matchedReceiver == null) {
                    if (buffer.size() < capacity) {
                        buffer.add(wrap(value));
                        return true;
                    } else {
                        return false;
                    }
                }
            } finally {
                lock.unlock();
            }

            if (matchedReceiver.settledSuccess(value)) {
                return true;
            }
            // receiver cancelled → retry
        }
    }

    @Override
    public void close(CloseMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("CloseMode cannot be null");
        }

        List<ChannelOperationPromise<?>> toFail = new ArrayList<>();
        List<ChannelOperationPromise<T>> toResolve = new ArrayList<>();
        boolean failReceivers = (mode == CloseMode.FAIL_ALL);

        lock.lock();
        try {
            if (closedMode != null) {
                return;
            }
            closedMode = mode;

            WaitingSender<T> ws;
            while ((ws = waitSenders.poll()) != null) {
                toFail.add(ws.future);
            }

            ChannelOperationPromise<T> wr;
            while ((wr = waitReceivers.poll()) != null) {
                toResolve.add(wr);
            }

            if (failReceivers) {
                buffer.clear();
            }
        } finally {
            lock.unlock();
        }

        IllegalStateException ex = new IllegalStateException("Channel is closed");
        for (ChannelOperationPromise<?> f : toFail) {
            f.settledFailure(ex);
        }
        for (ChannelOperationPromise<T> f : toResolve) {
            if (failReceivers) {
                f.settledFailure(ex);
            } else {
                f.settledSuccess(null);
            }
        }
    }

    @Override
    public boolean isClosed() {
        lock.lock();
        try {
            return closedMode != null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CloseMode closedMode() {
        lock.lock();
        try {
            return closedMode;
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public int size() {
        lock.lock();
        try {
            return buffer.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int capacity() {
        return capacity;   // immutable, no lock needed
    }

    private void promoteOneSender() {
        WaitingSender<T> ws;
        while ((ws = waitSenders.poll()) != null) {
            if (ws.isDone()) continue;
            if (ws.complete()) {
                buffer.add(wrap(ws.value));
                break;
            }
        }
    }

    // inner classes

    private static class WaitingSender<T> {
        final T value;
        final ChannelOperationPromise<Void> future;

        WaitingSender(T value, ChannelOperationPromise<Void> future) {
            this.value = value;
            this.future = future;
        }
        
        boolean isDone() {
            return future.isDone();
        }
        
        boolean complete() {
            return future.settledSuccess(null);
        }
    }
    
    // ArrayDeque forbids null elements. Instead of wrapping every value
    // in Optional (which allocates on every non-null add), we use a
    // private identity-checked sentinel. Zero allocation for non-null values.
    private static final Object NULL_SENTINEL = new Object();

    private static Object wrap(Object value) {
        return value == null ? NULL_SENTINEL : value;
    }

    @SuppressWarnings("unchecked")
    private T unwrap(Object raw) {
        return raw == NULL_SENTINEL ? null : (T) raw;
    }

}