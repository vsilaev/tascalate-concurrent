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

import net.tascalate.concurrent.Promise;

/**
 * An asynchronously acquirable <b>counting semaphore</b> &mdash; the non-blocking
 * analogue of {@link java.util.concurrent.Semaphore}.
 *
 * <p>
 * The semaphore manages a fixed number of permits. Acquiring a token consumes
 * permits; releasing a token returns them. Unlike the JDK semaphore, acquisition
 * never blocks: {@link #acquire(long)} returns a {@link Promise} that completes
 * with a {@link Token} once the requested permits are available, and
 * {@link #tryAcquire(long)} performs an immediate non-blocking attempt.
 * </p>
 *
 * <pre>{@code
 * AsyncSemaphoreLock sem = AsyncSemaphoreLock.create(10, true); // 10 permits, fair
 *
 * Promise<AsyncSemaphoreLock.Token> tok = sem.acquire(3);       // wait for 3 permits
 * tok.thenCompose(t ->
 *     Promises.tryCompose(Promises.success(t), $ -> useResourceAsync())
 * );                                                            // releases 3 permits after
 * }</pre>
 *
 * <p>
 * Tokens returned by this semaphore report the number of permits they hold via
 * {@link Token#permits()}; releasing the token returns exactly that many permits.
 * </p>
 *
 * @see AsyncLock
 * @see AsyncExclusiveLock
 */
public interface AsyncSemaphoreLock extends AsyncLock<AsyncSemaphoreLock.Token> {

    /**
     * Returns the number of permits currently available for acquisition.
     *
     * @return the number of available permits
     */
    public long availablePermits();

    /**
     * Returns the number of acquisition requests currently waiting in the queue.
     *
     * @return the number of queued waiters
     */
    public int getQueueLength();

    /**
     * Immediately acquires and removes <b>all</b> permits currently available, if any.
     *
     * @return a token with all drained permits, or {@code null} if no permits were available
     */
    public Token drainPermits();

    /**
     * Attempts to immediately acquire a single permit without blocking.
     *
     * @return an token if a permit was acquired; {@code null} otherwise
     */
    default public Token tryAcquire() {
        return tryAcquire(1L);
    }

    /**
     * Attempts to immediately acquire the given number of permits without blocking.
     *
     * @param permits the number of permits to acquire; must be within
     *                {@code 1 .. totalPermits}
     * @return a token if all requested permits were acquired;
     *         {@code null} otherwise
     */
    public Token tryAcquire(long permits);

    /**
     * Acquires a single permit asynchronously.
     *
     * @return a promise that completes with a token once one permit is available
     */
    default public Promise<Token> acquire() {
        return acquire(1L);
    }

    /**
     * Acquires the given number of permits asynchronously.
     * <p>
     * The returned promise completes when all requested permits are available. The
     * token must be released once the protected action completes; releasing it
     * returns exactly {@link Token#permits()} permits to the semaphore.
     * </p>
     *
     * @param permits the number of permits to acquire; must be within
     *                {@code 1 .. totalPermits}
     * @return a promise that completes with a token once the permits are available
     */
    public Promise<Token> acquire(long permits);

    /**
     * A semaphore token indicating that the requested permits have been exclusively
     * acquired. Once the protected action is completed, the permits are returned
     * by calling {@link #release()} (or {@link #close()}).
     */
    interface Token extends AsyncExclusiveLock.Token {

        /**
         * Returns the number of permits held by this token; the same number of
         * permits is returned to the semaphore upon {@link #release()}.
         *
         * @return the number of held permits
         */
        long permits();
    }

    /**
     * Creates a new asynchronous counting semaphore.
     *
     * @param permits the total number of permits; must be positive
     * @param fair    if {@code true}, acquisitions are granted strictly in arrival
     *                order (no barging); if {@code false}, new requests may acquire
     *                permits ahead of queued waiters
     * @return a new semaphore with all permits initially available
     */
    static AsyncSemaphoreLock create(long permits, boolean fair) {
        return new DefaultAsyncSemaphoreLock(permits, fair);
    }
}