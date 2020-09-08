/**
 * Copyright 2015-2020 Valery Silaev (http://vsilaev.com)
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

public interface AsyncSemaphore {
    /**
     * Acquires 1 permit from the semaphore as if by calling {@link #acquire(long)} with an argument
     * of 1.
     *
     * @return a {@link Promise} which will complete when 1 permit has been successfully
     *         acquired
     * @see #acquire(long)
     */
    default Promise<Long> acquire() {
      return acquire(1L);
    }

    /**
     * Acquires the given number of permits from the semaphore, returning a stage which will complete
     * when all of the permits are exclusively acquired. The stage may already be complete if the
     * permits are available immediately.
     * <p>
     * If the permits are not available immediately, the acquisition will enter a queue and an
     * incomplete stage will be returned. Semantics of the waiter queue, including ordering policies,
     * are implementation specific and will be defined by the given implementing class. The returned
     * stage will complete when sufficient permits have been {@link #release(long) released} and
     * assigned to this acquisition by the governing queue policy.
     *
     * @param permits A non-negative number of permits to acquire from the semaphore
     * @return a {@link Promise} which will be completed when all {@code permits} have been
     *         acquired
     * @throws IllegalArgumentException if the requested permits are negative, or exceed any
     *         restrictions enforced by the given implementation
     */
    Promise<Long> acquire(long permits);
    
    /**
     * Releases 1 permit from the semaphore as if by calling {@link #release(long)} with an argument
     * of 1.
     *
     * @see #release(long)
     */
    default void release() {
      release(1L);
    }

    /**
     * Releases the given number of permits to the semaphore.
     * <p>
     * If there are unfulfilled acquires pending, this method will release permits to the waiting
     * acquisitions based on the implementation's release and acquire policies. Consequently, this
     * method may complete a number of waiting stages and execute the corresponding observers.
     *
     * @param permits A non-negative number of permits to release to the semaphore
     * @throws IllegalArgumentException if the released permits are negative, or exceed any
     *         restrictions enforced by the given implementation
     */
    void release(long permits);
    

    /**
     * Attempts to acquire 1 permit from the semaphore as if by calling {@link #tryAcquire(long)} with
     * an argument of 1.
     *
     * @return true if the single request permit is available, and has been immediately acquired
     * @see #tryAcquire(long)
     */
    default boolean tryAcquire() {
      return tryAcquire(1L);
    }

    /**
     * Attempts to acquire the given number of permits from the semaphore, returning a boolean
     * indicating whether all of the permits were immediately available and have been exclusively
     * acquired.
     * <p>
     * Implementations may define precise behavior of this method with respect to competing
     * acquisitions, e.g. whether permits may be acquired while other acquisitions are waiting. This
     * interface only requires that this method will succeed when the given permits are available and
     * there are no other acquisitions queued.
     *
     * @param permits A non-negative number of permits to acquire from the semaphore
     * @return true if all of the requested permits are available, and have been immediately acquired
     * @throws IllegalArgumentException if the requested permits are negative, or exceed any
     *         restrictions enforced by the given implementation
     */
    boolean tryAcquire(long permits);

    /**
     * Acquires all permits that are immediately available.
     * <p>
     * After this call -- provided there are no intermediate {@link #release(long) releases} -- any
     * attempt to {@link #acquire(long) acquire} will queue and any {@link #tryAcquire(long)
     * tryAcquire} will fail.
     *
     * @return the number of permits that were available and have been drained
     */
    long drainPermits();

    /**
     * Gets the number of currently available permits.
     * <p>
     * The bounds of the returned value are not defined; an implementation may, for example, choose to
     * represent waiting acquisitions as holding negative permits, and thus the value returned by this
     * method could be negative. Furthermore, a positive number of permits returned by this method may
     * not indicate that such permits are acquirable, as the waiter-queue policy may prohibit
     * fulfilling further acquisitions.
     * <p>
     * This value is produced on a best-effort basis, and should not be used for any control logic.
     * Generally it is only useful in testing, debugging, or statistics purposes.
     *
     * @return the number of currently available permits
     */
    long availablePermits();

    /**
     * Gets the number of unfulfilled acquisitions waiting on this semaphore's permits.
     * <p>
     * This value is produced on a best-effort basis, and should not be used for any control logic.
     * Generally it is only useful in testing, debugging, or statistics purposes.
     *
     * @return the number of unfulfilled acquisitions waiting on this semaphore's permits
     */
    int getQueueLength();

    static AsyncSemaphore create(long permits, boolean fair) {
        return new DefaultAsyncSemaphore(permits, fair);
    }
}
