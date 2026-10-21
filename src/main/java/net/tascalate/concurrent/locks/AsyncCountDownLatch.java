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

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.LongBinaryOperator;

import net.tascalate.concurrent.CompletableFutureWrapper;

/**
 * An asynchronous analogue of {@link java.util.concurrent.CountDownLatch}.
 *
 * <p>
 * Unlike the JDK latch, whose {@code await()} blocks the calling thread, this class
 * <b>is itself a {@link net.tascalate.concurrent.Promise}</b>: parties interested in
 * the latch reaching zero simply subscribe to it or compose further stages, and are
 * notified asynchronously. The promise completes with the <em>initial</em> count.
 * </p>
 *
 * <pre>{@code
 * AsyncCountDownLatch latch = new AsyncCountDownLatch(3);
 * latch.thenRun(() -> startProcessing());     // fires when the count reaches zero
 *
 * latch.countDown();   // 2 remaining
 * latch.countDown();   // 1 remaining
 * latch.countDown();   // 0 → the promise completes
 * }</pre>
 *
 * <p>
 * A latch created with a count of zero is already complete. The count never goes
 * below zero; superfluous {@code countDown} calls have no effect. Like the JDK
 * latch, it is a one-shot barrier &mdash; the count cannot be reset.
 * </p>
 *
 * <p>
 * This class is thread-safe: counting down and subscribing may happen concurrently
 * from any threads.
 * </p>
 */
public class AsyncCountDownLatch extends CompletableFutureWrapper<Long> {

    private static final AtomicLongFieldUpdater<AsyncCountDownLatch> COUNT_UPDATER =
        AtomicLongFieldUpdater.newUpdater(AsyncCountDownLatch.class, "count");

    /** Decrement clamped at zero. */
    private static final LongBinaryOperator DECREMENT =
        (prev, change) -> Math.max(0, prev - change);

    /** The initial count; reported as the promise result upon completion. */
    private final long initial;

    /** The current count; touched only through {@link #COUNT_UPDATER}. */
    @SuppressWarnings("unused")
    private volatile long count;

    /**
     * Creates a latch with the given initial count.
     * <p>
     * If the count is zero, the latch is created already complete.
     * </p>
     *
     * @param count the number of times {@link #countDown()} must be invoked before
     *              the latch completes; must not be negative
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public AsyncCountDownLatch(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count < 0 : " + count);
        }
        this.count = this.initial = count;
        if (count == 0) {
            success(count);
        }
    }

    /**
     * Decrements the count by one, completing the latch if the count reaches zero.
     *
     * @return the count before this invocation
     */
    public long countDown() {
        return countDown(1);
    }

    /**
     * Decrements the count by the given delta, completing the latch if the count
     * reaches zero.
     *
     * @param delta the amount to decrement by; must be positive
     * @return the count before this invocation
     * @throws IllegalArgumentException if {@code delta <= 0}
     */
    public long countDown(long delta) {
        if (delta <= 0) {
            throw new IllegalArgumentException("delta <= 0 : " + delta);
        }
        long current = COUNT_UPDATER.accumulateAndGet(this, delta, DECREMENT);
        if (current == 0) {
          success(initial);
        }
        return current;
    }
}