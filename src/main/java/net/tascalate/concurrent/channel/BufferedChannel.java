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

/**
 * The primary {@link Channel} implementation, supporting both rendezvous
 * (capacity 0) and buffered (capacity &gt; 0) modes.
 * <p>
 * Internally the channel maintains three structures guarded by a single
 * {@link ReentrantLock}:
 * <ul>
 *   <li>{@code buffer} -- the element buffer (always empty for rendezvous
 *       channels).</li>
 *   <li>{@code waitSenders} -- a FIFO queue of senders blocked because the
 *       buffer was full and no receiver was available.</li>
 *   <li>{@code waitReceivers} -- a FIFO queue of receivers blocked because
 *       the buffer was empty and no sender was available.</li>
 * </ul>
 * <p>
 * All send and receive operations return a {@link Promise}. When an
 * operation cannot complete immediately, a {@link ChannelPromise} is
 * enqueued and its future returned to the caller. A matching operation on
 * the opposite side later dequeues and completes that promise.
 * <p>
 * The channel participates in {@code select} statements through the
 * {@link SelectCoordinator} protocol. Each queued waiter carries a
 * reference to the coordinator of the select that registered it. Before a
 * waiter is completed, the completing side calls
 * {@link ChannelPromise#tryClaim(Channel)} to verify that the owning
 * select has not already resolved via another channel. Waiters that fail
 * this check (or that are already done) are called "phantoms" and are
 * silently discarded by the {@link #removePhantomSender} and
 * {@link #removePhantomReceiver} helpers.
 * <p>
 * Null values are supported through a private sentinel object, avoiding
 * per-element {@code Optional} allocation.
 * <p>
 * This class is thread-safe. All mutable state is guarded by
 * {@link #lock}.
 *
 * @param <T> the element type carried by this channel
 */
public class BufferedChannel<T> implements Channel<T> {

    /**
     * The maximum number of elements that can be held in the buffer.
     * Zero for rendezvous channels. Immutable after construction.
     */
    private final int capacity;

    /**
     * The lock guarding all mutable channel state: the buffer, both
     * waiter queues, and the close mode.
     */
    private final ReentrantLock lock;

    /**
     * The element buffer. Declared as {@code Queue<Object>} rather than
     * {@code Queue<T>} so that null values can be stored via the
     * {@link #NULL_SENTINEL} sentinel, since {@link ArrayDeque} does not
     * permit null elements. This avoids per-element {@code Optional}
     * allocation.
     */
    private final Queue<Object> buffer = new ArrayDeque<>();

    /**
     * FIFO queue of senders waiting for buffer space or a matching
     * receiver. Non-static inner class so each sender can capture the
     * enclosing channel for {@link ChannelPromise#tryClaim}.
     */
    private final Queue<WaitingSender> waitSenders = new ArrayDeque<>();

    /**
     * FIFO queue of receivers waiting for an element or a matching sender.
     */
    private final Queue<ChannelPromise<T>> waitReceivers = new ArrayDeque<>();

    /**
     * The close mode, or {@code null} while the channel is open. Once
     * set, the channel is permanently closed and this value never changes.
     */
    private CloseMode closedMode = null; // null = OPEN

    /**
     * Creates a non-fair buffered channel with the given capacity.
     *
     * @param capacity the buffer capacity; 0 for a rendezvous channel
     * @throws IllegalArgumentException if {@code capacity} is negative
     */
    public BufferedChannel(int capacity) {
        this(capacity, false);
    }

    /**
     * Creates a buffered channel with the given capacity and fairness
     * policy.
     * <p>
     * A fair channel grants access to waiting senders and receivers in
     * FIFO order, preventing starvation at the cost of throughput. A
     * non-fair channel allows barging and is faster under contention.
     *
     * @param capacity the buffer capacity; 0 for a rendezvous channel
     * @param fair     {@code true} for FIFO ordering of waiters,
     *                 {@code false} to allow barging
     * @throws IllegalArgumentException if {@code capacity} is negative
     */
    public BufferedChannel(int capacity, boolean fair) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity must be >= 0");
        }
        this.capacity = capacity;
        this.lock = new ReentrantLock(fair);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Delegates to {@link #send(Object, SelectCoordinator)} with the
     * {@link SelectCoordinator#anyWins()} coordinator, meaning this send
     * is not part of a select statement and always has permission to
     * complete.
     */
    @Override
    public Promise<T> send(T value) {
        return send(value, SelectCoordinator.anyWins());
    }

    /**
     * Sends a value, coordinated by the given {@link SelectCoordinator}.
     * <p>
     * The method first checks for a waiting receiver (rendezvous handoff),
     * then for buffer space, and finally enqueues a {@link WaitingSender}
     * if neither is available.
     * <p>
     * Before completing a handoff or buffering the value, the coordinator
     * is consulted via {@link SelectCoordinator#tryClaim(Channel)}. If the
     * coordinator has already been won by another channel in the same
     * select, a cancelled promise is returned instead.
     * <p>
     * The blocking (enqueue) path does NOT call the coordinator, because
     * no completion is happening yet. The coordinator is attached to the
     * {@link WaitingSender} so that a future receiver can verify the
     * owning select is still active before completing it.
     *
     * @param value       the value to send
     * @param coordinator the select coordinator; {@code null} is treated
     *                    as {@link SelectCoordinator#anyWins()}
     * @return a promise that completes when the value is accepted, or a
     *         cancelled promise if the coordinator was already won
     */
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
                    if (removePhantomReceiver(matched)) {
                        continue;
                    }
                    isWon = isWon || coordinator.tryClaim(this);
                    if (!isWon) {
                        return canceled();
                    }
                    matchedReceiver = matched;
                    waitReceivers.poll(); // actually consume it now
                    handoff = true;
                    break;
                }
                if (handoff) {
                    // rendezvous -- completed outside lock
                } else if (buffer.size() < capacity) {
                    isWon = isWon || coordinator.tryClaim(this);
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
                    WaitingSender ws = new WaitingSender(value, coordinator); 
                    waitSenders.add(ws);
                    return ws.future;
                }
            } finally {
                lock.unlock();
            }
            if (handoff) {
                if (matchedReceiver.success(value)) {
                    return matchedReceiver;
                }
                // receiver cancelled concurrently -> retry.
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Delegates to {@link #receive(SelectCoordinator)} with the
     * {@link SelectCoordinator#anyWins()} coordinator, meaning this
     * receive is not part of a select statement and always has permission
     * to complete.
     */
    @Override
    public Promise<T> receive() {
        return receive(SelectCoordinator.anyWins());
    }

    /**
     * Receives a value, coordinated by the given {@link SelectCoordinator}.
     * <p>
     * The method first checks the buffer, then for a waiting sender
     * (rendezvous handoff), and finally enqueues a receiver promise if
     * neither is available.
     * <p>
     * When a buffered value is consumed, one waiting sender (if any) is
     * promoted into the buffer to maintain the buffer invariant. The
     * promotion uses the same phantom-checking logic as direct handoffs.
     * <p>
     * Before consuming a buffered value or completing a rendezvous, the
     * coordinator is consulted. If it has already been won by another
     * channel in the same select, a cancelled promise is returned.
     * <p>
     * The blocking (enqueue) path does NOT call the coordinator. The
     * coordinator is attached to the receiver promise so that a future
     * sender can verify the owning select is still active.
     *
     * @param coordinator the select coordinator; {@code null} is treated
     *                    as {@link SelectCoordinator#anyWins()}
     * @return a promise that completes with the received value, or a
     *         cancelled promise if the coordinator was already won
     */
    @Override
    public Promise<T> receive(SelectCoordinator coordinator) {
        if (null == coordinator) {
            coordinator = SelectCoordinator.anyWins();
        }
        boolean isWon = false;
        while (true) {
            T resultFromBuffer = null;
            WaitingSender rendezvousSender = null;
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
                    isWon = isWon || coordinator.tryClaim(this);
                    if (!isWon) {
                        return canceled();
                    }
                    // BUFFER PATH
                    resultFromBuffer = unwrap(buffer.poll());
                    completedImmediately = true;
                    WaitingSender ws;
                    while ((ws = waitSenders.peek()) != null) {
                        if (removePhantomSender(ws)) {
                            continue;
                        }
                        if (ws.complete()) {
                            buffer.add(wrap(ws.value));
                            waitSenders.poll();
                            break;
                        } else {
                            waitSenders.poll();
                        }
                    }
                } else {
                    // RENDEZVOUS PATH
                    WaitingSender ws;
                    while ((ws = waitSenders.peek()) != null) {
                        if (removePhantomSender(ws)) {
                            continue;
                        }
                        isWon = isWon || coordinator.tryClaim(this);
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
                        ChannelPromise<T> receiverFuture = new ChannelPromise<>(coordinator);
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

    /**
     * Non-blocking receive. Returns immediately without registering a
     * waiter.
     * <p>
     * First attempts to consume from the buffer. If the buffer is empty,
     * attempts a rendezvous handoff with a waiting sender. Phantom
     * senders (cancelled or claimed by another select) are silently
     * skipped.
     * <p>
     * When a buffered value is consumed, one waiting sender is promoted
     * into the buffer via {@link #promoteOneSender()}.
     *
     * @return a successful {@link Try} with the value, a failed
     *         {@link Try} if closed in {@code FAIL_ALL} mode, a successful
     *         {@link Try} with {@code null} on end-of-stream in
     *         {@code DRAIN} mode, or {@code null} if no value is ready
     */
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
            WaitingSender ws;
            while ((ws = waitSenders.peek()) != null) {
                if (removePhantomSender(ws)) {
                    continue;
                }
                if (ws.complete()) { // atomic claim
                    waitSenders.poll();
                    return Try.success(ws.value);
                } else {
                    waitSenders.poll();
                }
                // cancelled concurrently -> try next
            }
            return null; // not ready
        } finally {
            lock.unlock();
        }
    }

    /**
     * Non-blocking send. Returns immediately without registering a waiter.
     * <p>
     * First attempts a rendezvous handoff with a waiting receiver. If no
     * receiver is available, attempts to buffer the value. Phantom
     * receivers are silently skipped.
     *
     * @param value the value to send
     * @return a successful {@link Try} if the value was delivered or
     *         buffered, a failed {@link Try} if closed, or {@code null}
     *         if the channel is full
     */
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
                while ((matched = waitReceivers.peek()) != null) {
                    if (removePhantomReceiver(matched)) {
                        continue;
                    }
                    waitReceivers.poll();
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
            if (matchedReceiver.success(value)) {
                return Try.success(value);
            }
            // Receiver was cancelled concurrently -> retry
        }
    }

    /**
     * Closes the channel with the given mode. Idempotent.
     * <p>
     * All waiting senders are failed with {@link IllegalStateException}.
     * Waiting receivers are either failed ({@code FAIL_ALL}) or completed
     * with {@code null} ({@code DRAIN}). In {@code FAIL_ALL} mode the
     * buffer is also cleared.
     * <p>
     * After closing, new send attempts fail immediately. Receive behavior
     * depends on the mode: {@code DRAIN} allows draining the buffer,
     * {@code FAIL_ALL} rejects all receives.
     *
     * @param mode the close mode; must not be {@code null}
     * @throws IllegalArgumentException if {@code mode} is {@code null}
     */
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
            WaitingSender ws;
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
            f.failure(ex);
        }
        for (ChannelPromise<T> f : toResolve) {
            if (failReceivers) {
                f.failure(ex);
            } else {
                f.success(null);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isClosed() {
        lock.lock();
        try {
            return closedMode != null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloseMode closedMode() {
        lock.lock();
        try {
            return closedMode;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * Returns the current number of elements in the buffer. This is a
     * snapshot and may be stale by the time the caller acts on it.
     */
    @Override
    public int size() {
        lock.lock();
        try {
            return buffer.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Capacity is immutable and can be read without locking.
     */
    @Override
    public int capacity() {
        return capacity;   // immutable, no lock needed
    }

    /**
     * Checks whether the given receiver promise is a "phantom" -- either
     * already done (cancelled) or claimed by another select -- and
     * removes it from the queue if so.
     * <p>
     * Must be called while holding {@link #lock} and only when the given
     * promise is at the head of {@link #waitReceivers}.
     *
     * @param matched the receiver promise at the head of the queue
     * @return {@code true} if the receiver was a phantom and has been
     *         removed; {@code false} if it is live and ready to be used
     */
    private boolean removePhantomReceiver(ChannelPromise<T> matched) {
        if (matched.isDone() || !matched.tryClaim(this)) {
            waitReceivers.poll();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks whether the given waiting sender is a "phantom" -- either
     * already done (cancelled) or claimed by another select -- and
     * removes it from the queue if so.
     * <p>
     * Must be called while holding {@link #lock} and only when the given
     * sender is at the head of {@link #waitSenders}.
     *
     * @param waitingSender the sender at the head of the queue
     * @return {@code true} if the sender was a phantom and has been
     *         removed; {@code false} if it is live and ready to be used
     */
    private boolean removePhantomSender(WaitingSender waitingSender) {
        if (waitingSender.isDone() || !waitingSender.tryClaim()) {
            waitSenders.poll();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Promotes one waiting sender into the buffer.
     * <p>
     * Called after a buffered value has been consumed, to maintain the
     * buffer invariant: if there are waiting senders and space has been
     * freed, one sender's value is moved into the buffer and its promise
     * completed.
     * <p>
     * Phantom senders are skipped. If the chosen sender's promise cannot
     * be completed (it was cancelled concurrently), it is discarded and
     * the next sender is tried.
     * <p>
     * Must be called while holding {@link #lock}.
     */
    private void promoteOneSender() {
        WaitingSender ws;
        while ((ws = waitSenders.peek()) != null) {       // peek
            if (removePhantomSender(ws)) {
                continue;
            }
            if (ws.complete()) {
                waitSenders.poll();
                buffer.add(wrap(ws.value));
                break;
            } else {
                waitSenders.poll();
            }
        }
    }

    // ------------------------------------------------------------------
    // inner classes
    // ------------------------------------------------------------------

    /**
     * A sender waiting for a matching receiver or buffer space.
     * <p>
     * This is a non-static inner class so that {@link #tryClaim()} can
     * pass the enclosing {@code BufferedChannel} instance as the owner
     * argument to {@link ChannelPromise#tryClaim(Channel)}.
     * <p>
     * The sender carries the value to send and a {@link ChannelPromise}
     * that will be completed when a receiver accepts the value. The
     * promise also carries the {@link SelectCoordinator} of the select
     * that registered this sender, enabling phantom detection.
     */
    private class WaitingSender {

        /** The value to be sent when a receiver becomes available. */
        final T value;

        /**
         * The promise that will be completed when a receiver accepts the
         * value. Also carries the select coordinator for phantom checks.
         */
        final ChannelPromise<T> future;

        /**
         * Creates a new waiting sender.
         *
         * @param value       the value to send
         * @param coordinator the select coordinator of the owning select,
         *                    or {@code null} for a direct (non-select) send
         */
        WaitingSender(T value, SelectCoordinator coordinator) {
            this.value = value;
            this.future = new ChannelPromise<>(coordinator);
        }

        /**
         * Returns {@code true} if the underlying future is already done
         * (completed or cancelled).
         *
         * @return {@code true} if done
         */
        boolean isDone() {
            return future.isDone();
        }

        /**
         * Attempts to claim this sender for the enclosing channel.
         * Delegates to the future's coordinator. Returns {@code false}
         * if the owning select has already resolved via another channel.
         *
         * @return {@code true} if the claim succeeded
         */
        boolean tryClaim() {
            return future.tryClaim(BufferedChannel.this);
        }

        /**
         * Completes this sender's future with its value, indicating that
         * a receiver has accepted the value.
         *
         * @return {@code true} if this call completed the future;
         *         {@code false} if it was already completed or cancelled
         */
        boolean complete() {
            return future.success(value);
        }
    }

    /**
     * Shared singleton promise representing end-of-stream (DRAIN close
     * with empty buffer). Returns {@code null} as the received value.
     */
    private static final Promise<Object> PROMISE_NOTHING = Promises.success(null);

    /**
     * Returns the shared end-of-stream promise, cast to the required type.
     *
     * @param <T> the nominal element type
     * @return a promise that completes with {@code null}
     */
    @SuppressWarnings("unchecked")
    private static <T> Promise<T> nothing() {
        return (Promise<T>) PROMISE_NOTHING;
    }

    /**
     * Shared singleton cancelled promise, returned when a coordinator
     * has already been won by another channel.
     */
    private static final Promise<Object> PROMISE_CANCELED; 
    static {
        PROMISE_CANCELED = new ChannelPromise<>(null);
        PROMISE_CANCELED.cancel(true);
    }

    /**
     * Returns the shared cancelled promise, cast to the required type.
     *
     * @param <T> the nominal element type
     * @return a promise that is already cancelled
     */
    @SuppressWarnings("unchecked")
    private static <T> Promise<T> canceled() {
        return (Promise<T>)PROMISE_CANCELED;
    }

    /**
     * Sentinel object used to represent null values in the buffer.
     * {@link ArrayDeque} does not permit null elements, so nulls are
     * stored as this sentinel and unwrapped on retrieval.
     */
    private static final Object NULL_SENTINEL = new Object();

    /**
     * Wraps a value for storage in the buffer. Null values are replaced
     * with {@link #NULL_SENTINEL}.
     *
     * @param value the value to wrap
     * @return the wrapped value, or the sentinel for null
     */
    private static Object wrap(Object value) {
        return value == null ? NULL_SENTINEL : value;
    }

    /**
     * Unwraps a value retrieved from the buffer. The sentinel is
     * converted back to {@code null}.
     *
     * @param raw the raw value from the buffer
     * @return the original value, or {@code null} for the sentinel
     */
    @SuppressWarnings("unchecked")
    private T unwrap(Object raw) {
        return raw == NULL_SENTINEL ? null : (T) raw;
    }
}