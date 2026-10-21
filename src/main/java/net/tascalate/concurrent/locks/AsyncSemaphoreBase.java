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
package net.tascalate.concurrent.locks;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import net.tascalate.concurrent.CompletableFutureWrapper;
import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Promises;

/**
 * Abstract base implementation of an asynchronous counting semaphore, providing
 * lock-free permit accounting and a concurrent waiter queue shared by concrete
 * semaphore locks.
 *
 * <h2>Permit accounting</h2>
 * <p>
 * Available permits are tracked in a {@code volatile long} field modified exclusively
 * through the {@link #AVAILABLE_PERMITS} field updater with compare-and-set operations.
 * {@link #tryAcquireInternal(long)} reserves permits atomically; {@link #release(long)}
 * returns them and then wakes waiters.
 * </p>
 *
 * <h2>Waiter queue</h2>
 * <p>
 * Acquisition requests that cannot be satisfied immediately are enqueued as
 * {@link AbstractSemaphorePromise}s in a {@link ConcurrentLinkedQueue}. Whenever
 * permits appear (on {@link #release(long)} or on enqueue), {@link #nextWaiters()}
 * walks the queue head-first and fulfils as many waiters as the available permits
 * allow. Each waiter is guarded by a per-node lock so that only one thread at a
 * time performs its state transition; concurrent visits to the same head simply
 * back off.
 * </p>
 *
 * <h2>Fairness</h2>
 * <p>
 * In fair mode {@link #tryAcquireInternal(long)} refuses to acquire while any
 * waiter is queued, preventing barging and granting permits strictly in arrival
 * order. In unfair mode, new requests may acquire permits ahead of queued waiters.
 * </p>
 *
 * <h2>Cancellation</h2>
 * <p>
 * Cancelling a queued acquisition promise removes the waiter from the queue; any
 * permits tentatively reserved for a concurrently cancelled head are returned to
 * the pool by {@link #nextWaiters()}.
 * </p>
 *
 * @param <T> the token payload type of the concrete semaphore
 */
abstract class AsyncSemaphoreBase<T> {

    /** CAS-based updater for the {@link #availablePermits} field. */
    @SuppressWarnings("rawtypes")
    private static final AtomicLongFieldUpdater<AsyncSemaphoreBase> AVAILABLE_PERMITS =
        AtomicLongFieldUpdater.newUpdater(AsyncSemaphoreBase.class, "availablePermits");

    /** Total number of permits managed by this semaphore. */
    private final long totalPermits;

    /** Whether acquisitions are granted strictly in arrival order. */
    private final boolean fair;

    /** Currently available permits; touched only through {@link #AVAILABLE_PERMITS}. */
    @SuppressWarnings("unused")
    private volatile long availablePermits;

    /** Waiters queued for permits they could not acquire immediately. */
    private final Queue<AbstractSemaphorePromise> waiters;

    /**
     * Creates a semaphore base with the given total permits and fairness policy.
     *
     * @param totalPermits the total number of permits; must be positive
     * @param fair         {@code true} to grant permits strictly in arrival order
     * @throws IllegalArgumentException if {@code totalPermits <= 0}
     */
    AsyncSemaphoreBase(long totalPermits, boolean fair) {
        if (totalPermits <= 0) {
            throw new IllegalArgumentException("totalPermits must be a positive integer");
        }
        this.totalPermits = totalPermits;
        this.fair         = fair;
        this.availablePermits = totalPermits;
        waiters = new ConcurrentLinkedQueue<>();
    }

    /**
     * Returns the number of permits currently available for acquisition.
     *
     * @return the number of available permits
     */
    public long availablePermits() {
        return AVAILABLE_PERMITS.get(this);
    }

    /**
     * Returns the number of acquisition requests currently waiting in the queue.
     *
     * @return the number of queued waiters
     */
    public int getQueueLength() {
        return waiters.size();
    }

    /**
     * Atomically acquires and removes all permits currently available.
     *
     * @return the number of permits drained (possibly zero)
     */
    long drainPermitsInternal() {
        if (fair) {
            //nextWaiters();
        }
        long acquired = AVAILABLE_PERMITS.getAndSet(this, 0);
        return acquired; 
    }

    /**
     * Attempts to reserve the given number of permits atomically.
     * <p>
     * In fair mode the attempt fails whenever any waiter is queued, so that queued
     * requests are served first. The reservation itself is a CAS loop that only
     * subtracts permits when enough are available.
     * </p>
     *
     * @param permits the number of permits to acquire; must be within
     *                {@code 1 .. totalPermits}
     * @return {@code true} if the permits were reserved
     * @throws IllegalArgumentException if {@code permits} is outside the valid range
     */
    boolean tryAcquireInternal(long permits) {
        if (permits <= 0 || permits > totalPermits) {
            throw new IllegalArgumentException(
                String.format("Requested number of permits %d is not within range 1..%d", permits, totalPermits)
            );
        }
        if (fair) {
            //nextWaiters(); 
            if (null != waiters.peek()) {
                // Let fulfill queued requests first
                return false;
            }
        }
        // CAS loop equivalent to AtomicLong.accumulateAndGet: subtract permits only
        // when enough are available, otherwise leave the value unchanged
        while (true) {
            long current = AVAILABLE_PERMITS.get(this);
            if (current < permits) {
                return false;
            }
            if (AVAILABLE_PERMITS.compareAndSet(this, current, current - permits)) {
                return true;
            }
        }
    }

    /**
     * Acquires the given number of permits, asynchronously waiting if they are not
     * available now.
     *
     * @param permits the number of permits to acquire; validated by
     *                {@link #tryAcquireInternal(long)}
     * @return a promise that completes with the token payload once the permits are
     *         reserved; already complete when the permits were available immediately
     */
    public Promise<T> acquire(long permits) {
        // argument check is in tryAcquire
        if (tryAcquireInternal(permits)) {
            return Promises.success(createPromisePayload(permits));
        } else {
            AbstractSemaphorePromise promise = createPromise(permits);
            waiters.add(promise);
            // Need to call waiters while available permits might change
            // right after adding this promise to the queue
            nextWaiters();
            return promise;            
        }
    }

    /**
     * Returns the given number of permits to the pool and wakes queued waiters.
     *
     * @param permits the number of permits to release
     * @throws IllegalStateException if the resulting number of available permits is
     *         outside the range {@code 0 .. totalPermits} (i.e., more permits were
     *         released than acquired)
     */
    void release(long permits) {
        long current = AVAILABLE_PERMITS.addAndGet(this, permits);
        if (current < 0 || current > totalPermits) {
            throw new IllegalStateException(String.format(
                "After releasing %d permits number of available permits %d is not within range 1..%d", 
                permits, current, totalPermits
            ));
        }
        nextWaiters();
    }

    /**
     * Creates the token payload for a successful acquisition of the given permits.
     *
     * @param permits the acquired number of permits
     * @return the payload delivered by the acquisition promise
     */
    abstract protected T createPromisePayload(long permits);

    /**
     * Creates a waiter promise for an acquisition of the given permits that could
     * not be satisfied immediately.
     *
     * @param permits the requested number of permits
     * @return the queued waiter promise
     */
    abstract protected AbstractSemaphorePromise createPromise(long permits);

    /**
     * Fulfils queued waiters while permits allow.
     * <p>
     * Walks the queue head-first. For each head it takes the per-node lock, checks
     * for cancellation, reserves the head's permits with a CAS on
     * {@link #availablePermits}, and completes the head. Tentatively reserved
     * permits of a concurrently cancelled head are returned to the pool and the
     * walk continues. A failed CAS (concurrent permit change) restarts the
     * iteration; a locked head or insufficient permits ends it for this thread.
     * </p>
     */
    private void nextWaiters() {
        AbstractSemaphorePromise head = null;
        do {
            head = waiters.peek();
            if (null == head) {
                // waiters queue is empty
                break;
            }
            if (head.lock()) {
                try {
                    if (head.isDone()) {
                        // Same as edge case below - just for speed-up:
                        // If canceled fully or canceling is in progress
                        // remove here just to avoid wasting cycles
                        // when same remove is running in 
                        // AbstractSemaphorePromise.cancel 
                        waiters.remove(head);
                        continue;
                    }
                    long oldPermits = AVAILABLE_PERMITS.get(this);
                    long newPermits = oldPermits - head.permits();
                    if (newPermits < 0) {
                        // No available permits 
                        // just exit loop
                        break;
                    }
                    if (AVAILABLE_PERMITS.compareAndSet(this, oldPermits, newPermits)) {
                        if (head.acquire()) {
                            // due to head.acquire each head may be removed only once
                            // regardless of concurrent access (due to inherited CompletableFuture behavior)
                            AbstractSemaphorePromise found = waiters.poll();
                            if (found != head) {
                                throw new IllegalStateException("The acquired semaphore promise is not equal to the top of the queue");
                            }
                        } else {
                            // Edge case:
                            // head was cancelled concurrently revert permits and continue;
                            // same as with releasePermits but with loop instead of recursion 
                            waiters.remove(head); // ???
                            AVAILABLE_PERMITS.addAndGet(this, head.permits());
                        }
                    } else {
                        // concurrently acquired permits -- 
                        // need re-check on next iteration
                    }
                } finally {
                    head.unlock();
                }
            } else {
                // concurrent access to the same head
                // just exit loop in this thread
                break;
            }
        } while (head != null);
    }

    /**
     * Returns a diagnostic string with the permit totals, fairness, availability,
     * and queue size.
     */
    @Override
    public String toString() {
        return String.format(
            "%s(totalPermits=%d, fair=%s, availablePermits=%d, queueSize=%d)", 
            getClass().getSimpleName(), totalPermits, fair, AVAILABLE_PERMITS.get(this), waiters.size()
        );
    }

    /**
     * A waiter promise queued for permits that were not available at acquisition time.
     * <p>
     * Extends {@link CompletableFutureWrapper} so that completing the promise delivers
     * the token payload to the caller. A per-node lock (an {@code int} flag managed
     * through {@link AsyncSemaphorePromiseSupport#LOCKED}) serializes state transitions
     * performed by {@link AsyncSemaphoreBase#nextWaiters()} and {@link #cancel(boolean)}.
     * </p>
     */
    abstract protected class AbstractSemaphorePromise extends CompletableFutureWrapper<T> {

        /**
         * Per-node mutex guarding state transitions of this waiter:
         * {@code 0} = unlocked, {@code 1} = locked. Accessed exclusively via
         * {@link AsyncSemaphorePromiseSupport#LOCKED}.
         */
        volatile int locked; 

        /**
         * Acquires the per-node lock.
         *
         * @return {@code true} if the lock was acquired by this thread
         */
        @SuppressWarnings("unchecked")
        boolean lock() {
            return AsyncSemaphorePromiseSupport.LOCKED.compareAndSet(this, 0, 1);
        }

        /**
         * Releases the per-node lock.
         *
         * @return {@code true} if the lock was released by this thread
         */
        @SuppressWarnings("unchecked")
        boolean unlock() {
            return AsyncSemaphorePromiseSupport.LOCKED.compareAndSet(this, 1, 0);
        }

        /**
         * Returns the number of permits this waiter has requested.
         *
         * @return the requested permit count
         */
        abstract long permits();

        /**
         * Cancels this pending acquisition; on success the waiter is removed from
         * the queue so it is no longer considered by {@link #nextWaiters()}.
         *
         * @param mayInterruptIfRunning passed through to the underlying future
         * @return {@code true} if this promise was cancelled by this call
         */
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (super.cancel(mayInterruptIfRunning)) {
                waiters.remove(this);
                return true;
            } else {
                return false;
            }
        }

        /**
         * Completes this waiter with its token payload exactly once.
         *
         * @return {@code true} if this invocation performed the completion;
         *         {@code false} if the promise was already completed (e.g., cancelled
         *         concurrently)
         */
        abstract boolean acquire();
    }
}

/**
 * Hosts the per-waiter lock field updater of
 * {@link AsyncSemaphoreBase.AbstractSemaphorePromise}.
 * <p>
 * The updater is declared here — rather than in {@code AsyncSemaphoreBase} or in the
 * promise class itself — because {@code AbstractSemaphorePromise} is an <i>inner</i>
 * class of a generic type: on JDK 8 an inner class cannot declare static members, and
 * its class literal cannot be referenced from the static context of its generic
 * enclosing class. A top-level class in the same package avoids both restrictions;
 * the {@code locked} field is package-private so that the updater's reflective
 * access check passes.
 * </p>
 */
final class AsyncSemaphorePromiseSupport {

    private AsyncSemaphorePromiseSupport() {}

    /**
     * Per-node lock of {@link AsyncSemaphoreBase.AbstractSemaphorePromise}:
     * a CAS {@code 0 -> 1} acquires it, a CAS {@code 1 -> 0} releases it.
     */
    @SuppressWarnings({ "rawtypes"})
    static final AtomicIntegerFieldUpdater LOCKED =
        AtomicIntegerFieldUpdater.newUpdater(AsyncSemaphoreBase.AbstractSemaphorePromise.class, "locked");
}