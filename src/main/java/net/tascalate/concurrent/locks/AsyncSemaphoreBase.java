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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import net.tascalate.concurrent.CompletableFutureWrapper;
import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Promises;

abstract class AsyncSemaphoreBase<T> {
    
    private final long totalPermits;
    private final boolean fair;
    
    private final AtomicLong availablePermits;    
    private final Queue<AbstractSemaphorePromise> waiters;
    
    AsyncSemaphoreBase(long totalPermits, boolean fair) {
        if (totalPermits <= 0) {
            throw new IllegalArgumentException("totalPermits must be a positive integer");
        }
        this.totalPermits = totalPermits;
        this.fair         = fair;
        
        availablePermits = new AtomicLong(totalPermits);
        waiters = new ConcurrentLinkedQueue<>();
    }
    
    public long availablePermits() {
        return availablePermits.get();
    }
    
    public int getQueueLength() {
        return waiters.size();
    }
    
    long drainPermitsInternal() {
        if (fair) {
            //nextWaiters();
        }
        long acquired = availablePermits.getAndSet(0);
        return acquired; 
    }
    
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
        
        boolean[] altered = {false};
        availablePermits.accumulateAndGet(permits, (current, delta) -> {
            if (current >= delta) {
                altered[0]  = true;
                return current - delta;
            } else {
                altered[0] = false;
                return current;
            }
        });
        return altered[0]; 
    }
    
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

    void release(long permits) {
        long current = availablePermits.addAndGet(permits);
        if (current < 0 || current > totalPermits) {
            throw new IllegalStateException(String.format(
                "After releasing %d permits number of available permits %d is not within range 1..%d", 
                permits, current, totalPermits
            ));
        }
        nextWaiters();
    }
    
    abstract protected T createPromisePayload(long permits);
    abstract protected AbstractSemaphorePromise createPromise(long permits);
    
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
                    
                    long oldPermits = availablePermits.get();
                    long newPermits = oldPermits - head.permits();
                    if (newPermits < 0) {
                        // No available permits 
                        // just exit loop
                        break;
                    }
                    
                    if (availablePermits.compareAndSet(oldPermits, newPermits)) {
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
                            availablePermits.addAndGet(head.permits());
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
    
    @Override
    public String toString() {
        return String.format(
            "%s(totalPermits=%d, fair=%s, availablePermits=%d, queueSize=%d)", 
            getClass().getSimpleName(), totalPermits, fair, availablePermits.get(), waiters.size()
        );
    }
    
    abstract protected class AbstractSemaphorePromise extends CompletableFutureWrapper<T> {
        private final AtomicBoolean locked = new AtomicBoolean(); 
        
        boolean lock() {
            return locked.compareAndSet(false,  true);
        }
        
        boolean unlock() {
            return locked.compareAndSet(true,  false);
        }
        
        abstract long permits();
        
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (super.cancel(mayInterruptIfRunning)) {
                waiters.remove(this);
                return true;
            } else {
                return false;
            }
        }
        
        abstract boolean acquire();
    }

}
