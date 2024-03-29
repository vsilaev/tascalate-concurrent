/**
 * Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import net.tascalate.concurrent.CompletableFutureWrapper;
import net.tascalate.concurrent.Promise;

public class DefaultAsyncLock implements AsyncLock {
    final Queue<LockPromise> waiters = new ConcurrentLinkedQueue<>();
    
    @Override
    public Optional<Token> tryAcquire() {
        Promise<Token> lock = acquire();
        if (lock.isDone()) {
            return Optional.of(lock.join());
        } else {
            waiters.remove(lock);
            return Optional.empty();
        }
    }
    
    @Override
    public Promise<Token> acquire() {
        LockPromise promise = new LockPromise();
        waiters.add(promise); // Add to tail;
        
        nextWaiter();
        
        return promise;
    }

    private void nextWaiter() {
        LockPromise head = waiters.peek(); // Peek from head
        if (head != null) {
            head.acquire();
        }
    }
    
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

    private class LockPromise extends CompletableFutureWrapper<Token> {
        private final AtomicBoolean released = new AtomicBoolean();
        private final Token token = this::release;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (super.cancel(mayInterruptIfRunning)) {
                waiters.remove(this);
                return true;
            } else {
                return false;
            }
        }

        boolean acquire() {
            return success(token);
        }
        
        void release() {
            if (released.compareAndSet(false, true)) {
                LockPromise released = waiters.poll(); // Remove current head
                if (this != released) {
                    throw new IllegalStateException("The released lock is not equal to the top of the queue");
                }
                nextWaiter();
            }            
        }
    }
}
