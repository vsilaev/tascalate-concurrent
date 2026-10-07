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

public class BufferedChannel<T> implements Channel<T> {

    private final int capacity;
    private final ReentrantLock lock;

    // Queue<Object> instead of Queue<Optional<T>> — no per-element allocation
    private final Queue<Object> buffer = new ArrayDeque<>();
    private final Queue<WaitingSender<T>> waitSenders = new ArrayDeque<>();
    private final Queue<ChannelPromise<T>> waitReceivers = new ArrayDeque<>();

    private CloseMode closedMode = null; // null = OPEN

    public BufferedChannel(int capacity) {
        this(capacity, false);
    }

    public BufferedChannel(int capacity, boolean fair) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity must be >= 0");
        }
        this.capacity = capacity;
        this.lock = new ReentrantLock(fair);
    }

    @Override
    public Promise<T> send(T value) {
        while (true) {
            ChannelPromise<T> matchedReceiver = null;
            boolean handoff = false;

            lock.lock();
            try {
                if (closedMode != null) {
                    return Promises.failure(new IllegalStateException("Channel is closed"));
                }

                ChannelPromise<T> matched;
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
                    return Promises.success(value);
                } else {
                    WaitingSender<T> ws = new WaitingSender<>(value, null); 
                    waitSenders.add(ws);
                    return ws.future;
                }
            } finally {
                lock.unlock();
            }

            if (handoff) {
                if (matchedReceiver.completeSuccess(value)) {
                    // matchedReceiver is completed with the same value, safe to use as a return
                    return matchedReceiver;
                }
                // receiver cancelled concurrently -> retry
            }
        }
    }
    
    @Override
    public Promise<T> send(T value, SelectCoordinator coordinator) {
        if (coordinator == null) {
            coordinator = SelectCoordinator.anyWins();
        }
        
        boolean isWon = false;
        while (true) {
            ChannelPromise<T> matchedReceiver = null;
            boolean handoff = false;

            lock.lock();
            try {
                if (closedMode != null) {
                    return Promises.failure(new IllegalStateException("Channel is closed"));
                }

                ChannelPromise<T> matched;
                // Use peek() to check before committing, just like receive
                while ((matched = waitReceivers.peek()) != null) {
                    if (matched.isDone()) {
                        waitReceivers.poll(); // clean up cancelled receiver
                        continue;
                    }
                    
                    isWon = isWon || coordinator.tryWin();
                    if (!isWon) {
                        return canceled();
                    }
                    
                    matchedReceiver = matched;
                    waitReceivers.poll(); // actually consume it now
                    handoff = true;
                    break;
                }

                if (handoff) {
                    // rendezvous — completed outside lock
                } else if (buffer.size() < capacity) {
                    isWon = isWon || coordinator.tryWin();
                    if (!isWon) {
                        return canceled();
                    }
                    buffer.add(wrap(value));
                    return Promises.success(value);
                } else {
                    // DO NOT call tryWin() here! We are not completing, just waiting.
                    // We attach the coordinator so that when an external receiver 
                    // eventually tries to complete this sender, it can check tryWin().
                    
                    // Attach coord to the WaitingSender so the receiver can check it
                    WaitingSender<T> ws = new WaitingSender<>(value, coordinator); 
                    waitSenders.add(ws);
                    return ws.future;
                }
            } finally {
                lock.unlock();
            }

            if (handoff) {
                if (matchedReceiver.completeSuccess(value)) {
                    return matchedReceiver;
                }
                // receiver cancelled concurrently -> retry.
            }
        }
    }

    @Override
    public Promise<T> receive() {
        while (true) {
            T resultFromBuffer = null;
            WaitingSender<T> rendezvousSender = null;

            lock.lock();
            try {
                if (closedMode != null) {
                    if (closedMode == CloseMode.FAIL_ALL) {
                        return Promises.failure(new IllegalStateException("Channel is closed"));
                    }
                    if (buffer.isEmpty() && waitSenders.isEmpty()) {
                        return nothing();
                    } 
                }

                boolean completedImmediately = false;
                Object buffered = buffer.poll();
                if (buffered != null) {
                    // BUFFER PATH
                    resultFromBuffer = unwrap(buffered);
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
                    // RENDEZVOUS PATH
                    WaitingSender<T> ws;
                    while ((ws = waitSenders.poll()) != null) {
                        if (ws.isDone()) {
                            continue;
                        }
                        rendezvousSender = ws;
                        completedImmediately = true;
                        break;
                    }

                    if (!completedImmediately) {
                        if (closedMode != null) {
                            return nothing();
                        }
                        ChannelPromise<T> receiverFuture = new ChannelPromise<>(null);
                        waitReceivers.add(receiverFuture);
                        return receiverFuture;
                    }
                }
            } finally {
                lock.unlock();
            }

            if (rendezvousSender != null) {
                if (rendezvousSender.complete()) {
                    // rendezvousSender is completed with the same value, safe to use as a return
                    return rendezvousSender.future;
                } else {
                    // rendezvousSender was canceled concurrently, retry
                    continue;
                }
            } else {
                // Otherwise this is result polled from buffer
                return Promises.success(resultFromBuffer);
            }
        }
    }
    
    @Override
    public Promise<T> receive(SelectCoordinator coord) {
        if (null == coord) {
            coord = SelectCoordinator.anyWins();
        }
        
        boolean isWon = false;
        while (true) {
            T resultFromBuffer = null;
            WaitingSender<T> rendezvousSender = null;

            lock.lock();
            try {
                if (closedMode != null) {
                    if (closedMode == CloseMode.FAIL_ALL) {
                        return Promises.failure(new IllegalStateException("Channel is closed"));
                    }
                    if (buffer.isEmpty() && waitSenders.isEmpty()) {
                        return nothing();
                    } 
                }

                boolean completedImmediately = false;
                if (!buffer.isEmpty()) {
                    isWon = isWon || coord.tryWin();
                    if (!isWon) {
                        return canceled();
                    }
                    // BUFFER PATH
                    resultFromBuffer = unwrap(buffer.poll());
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
                    // RENDEZVOUS PATH
                    WaitingSender<T> ws;
                    while ((ws = waitSenders.peek()) != null) {
                        if (ws.isDone()) {
                            waitSenders.poll();
                            continue;
                        }
                        
                        isWon = isWon || coord.tryWin();
                        if (!isWon) {
                            return canceled();
                        }
                        
                        waitSenders.poll(); 
                        rendezvousSender = ws;
                        completedImmediately = true;
                        break;
                    }

                    if (!completedImmediately) {
                        if (closedMode != null) {
                            return nothing();
                        }
                        ChannelPromise<T> receiverFuture = new ChannelPromise<>(coord);
                        waitReceivers.add(receiverFuture);
                        return receiverFuture;
                    }
                }
            } finally {
                lock.unlock();
            }

            if (rendezvousSender != null) {
                if (rendezvousSender.complete()) {
                    // rendezvousSender is completed with the same value, safe to use as a return
                    return rendezvousSender.future;
                } else {
                    // rendezvousSender was canceled concurrently, retry
                    continue;
                }
            } else {
                // Otherwise this is result polled from buffer
                return Promises.success(resultFromBuffer);
            }
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

            Object buffered = buffer.poll();
            // buffered path (only reachable when capacity > 0)
            if (buffered != null) {
                T result = unwrap(buffered);
                promoteOneSender();
                return Try.success(result);
            }

            // rendezvous path (the ONLY path when capacity == 0)
            WaitingSender<T> ws;
            while ((ws = waitSenders.poll()) != null) {
                if (ws.isDone()) {
                    continue; // skip cancelled
                }
                if (ws.complete()) { // atomic claim
                    return Try.success(ws.value);
                }
                // cancelled concurrently -> try next
            }

            return null; // not ready
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Try<T> trySend(T value) {
        while (true) {
            ChannelPromise<T> matchedReceiver = null;

            lock.lock();
            try {
                if (closedMode != null) {
                    // Channel is closed, sending is an error in any closing mode
                    return Try.failure(new IllegalStateException("Channel is closed"));
                }

                ChannelPromise<T> matched;
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
                        // Buffered
                        return Try.success(value);
                    } else {
                        // Channel full
                        return null;
                    }
                }
            } finally {
                lock.unlock();
            }

            if (matchedReceiver.completeSuccess(value)) {
                return Try.success(value);
            }
            // Receiver was cancelled concurrently -> retry
        }
    }

    @Override
    public void close(CloseMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("CloseMode cannot be null");
        }

        List<ChannelPromise<?>> toFail = new ArrayList<>();
        List<ChannelPromise<T>> toResolve = new ArrayList<>();
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

            ChannelPromise<T> wr;
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
        for (ChannelPromise<?> f : toFail) {
            f.completeFailure(ex);
        }
        for (ChannelPromise<T> f : toResolve) {
            if (failReceivers) {
                f.completeFailure(ex);
            } else {
                f.completeSuccess(null);
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
            if (ws.isDone()) {
                continue;
            }
            if (ws.complete()) {
                buffer.add(wrap(ws.value));
                break;
            }
        }
    }

    // inner classes

    private static class WaitingSender<T> {
        final T value;
        final ChannelPromise<T> future;

        WaitingSender(T value, SelectCoordinator coordinator) {
            this.value = value;
            this.future = new ChannelPromise<>(coordinator);
        }
        
        boolean isDone() {
            return future.isDone();
        }
        
        boolean complete() {
            return future.completeSuccess(value);
        }
    }
    
    private static final Promise<Object> PROMISE_NOTHING = Promises.success(null);
    
    @SuppressWarnings("unchecked")
    private static <T> Promise<T> nothing() {
        return (Promise<T>) PROMISE_NOTHING;
    }
    
    
    private static final Promise<Object> PROMISE_CANCELED; 
    static {
        PROMISE_CANCELED = new ChannelPromise<>(null);
        PROMISE_CANCELED.cancel(true);
    }
    
    @SuppressWarnings("unchecked")
    private static <T> Promise<T> canceled() {
        return (Promise<T>)PROMISE_CANCELED;
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