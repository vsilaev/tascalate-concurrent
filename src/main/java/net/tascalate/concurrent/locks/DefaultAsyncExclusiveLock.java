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

import net.tascalate.concurrent.CompletableFutureWrapper;
import net.tascalate.concurrent.Promise;

/**
 * The default {@link AsyncExclusiveLock} implementation based on a FIFO waiter queue.
 * <p>
 * Each {@link #acquire()} call enqueues a {@link LockPromise} at the tail of a
 * {@link ConcurrentLinkedQueue}. The promise at the head of the queue is completed
 * (and thus granted the lock) whenever the lock is free; releasing the lock removes
 * the head and grants the lock to the next waiter. Acquisitions are therefore served
 * in arrival order.
 * </p>
 * <p>
 * The granted {@link Token} releases the lock via an idempotent release-once guard
 * (an {@code int} flag managed through an {@link AtomicIntegerFieldUpdater}), so
 * releasing a token more than once has no effect.
 * </p>
 * <p>
 * Create instances with {@link AsyncExclusiveLock#create()}.
 * </p>
 *
 * @see AsyncExclusiveLock
 */
public class DefaultAsyncExclusiveLock implements AsyncExclusiveLock {
    /** CAS-based updater for the per-promise release flag {@link LockPromise#released}. */
    private static final AtomicIntegerFieldUpdater<LockPromise> RELEASED =
        AtomicIntegerFieldUpdater.newUpdater(LockPromise.class, "released");

    /** Waiters for the lock, served in FIFO order. */
    final Queue<LockPromise> waiters = new ConcurrentLinkedQueue<>();

    /**
     * {@inheritDoc}
     * <p>
     * Implemented via {@link #acquire()}: if the returned promise is already complete
     * the lock was granted immediately and its token is returned; otherwise the waiter
     * is removed from the queue and {@code null} is returned.
     * </p>
     */
    @Override
     public Token tryAcquire() {
         Promise<Token> lock = acquire();
         if (lock.isDone()) {
             return lock.join();
         } else {
             waiters.remove(lock);
             return null;
         }
     }

     /**
      * {@inheritDoc}
      * <p>
      * The acquisition is enqueued at the tail of the waiter queue; the returned
      * promise completes when this waiter reaches the head of the queue and the lock
      * is granted.
      * </p>
      */
     @Override
     public Promise<Token> acquire() {
         LockPromise promise = new LockPromise();
         waiters.add(promise); // Add to tail;
         nextWaiter();
         return promise;
     }

     /**
      * Grants the lock to the waiter at the head of the queue, if any.
      */
     private void nextWaiter() {
         LockPromise head = waiters.peek(); // Peek from head
         if (head != null) {
             head.acquire();
         }
     }

     /**
      * Returns a diagnostic string indicating whether the lock is currently acquired
      * and how many waiters are queued.
      */
     @Override
     public String toString() {
         LockPromise head = waiters.peek();
         boolean isAcquired = head != null & head.isDone();
         int size = Math.max(0,  waiters.size() - (isAcquired ? 1 : 0));
         return String.format(
             "%s(acquired=%s, queueSize=%d)", 
             getClass().getSimpleName(), isAcquired, size
         );
     }

     /**
      * A waiter promise for the lock. Completing it grants the lock; releasing the
      * associated token removes it from the queue head and grants the lock to the
      * next waiter.
      */
     private class LockPromise extends CompletableFutureWrapper<Token> {

         /**
          * Release-once guard: {@code 0} = not released, {@code 1} = released.
          * Accessed exclusively via {@link DefaultAsyncExclusiveLock#RELEASED}.
          */
         @SuppressWarnings("unused")
         volatile int released = 0;

         /** The token delivered upon successful acquisition. */
         private final Token token = this::release;

         /**
          * Cancels this pending acquisition; on success the waiter is removed from
          * the queue so it is no longer granted the lock.
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
          * Grants the lock to this waiter by completing the promise with the token.
          *
          * @return {@code true} if this invocation performed the completion
          */
         boolean acquire() {
             return success(token);
         }

         /**
          * Releases the lock held by this waiter: removes it from the queue head
          * (verifying it is indeed the head) and grants the lock to the next waiter.
          * Idempotent — only the first invocation takes effect.
          *
          * @throws IllegalStateException if the released lock is not at the head of the queue
          */
         void release() {
             if (RELEASED.compareAndSet(this, 0, 1)) {
                 LockPromise released = waiters.poll(); // Remove current head
                 if (this != released) {
                     throw new IllegalStateException("The released lock is not equal to the top of the queue");
                 }
                 nextWaiter();
             }            
         }
     }
}