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

import java.util.Optional;

import net.tascalate.concurrent.Promise;

public interface AsyncSemaphoreLock extends AbstractAsyncLock<AsyncSemaphoreLock.Token> {
    
    public long availablePermits();
    
    public int getQueueLength();
    
    public Optional<Token> drainPermits();
    
    default public Optional<Token> tryAcquire() {
        return tryAcquire(1L);
    }
    
    public Optional<Token> tryAcquire(long permits);
    
    default public Promise<Token> acquire() {
        return acquire(1L);
    }
    
    public Promise<Token> acquire(long permits);
    
    /**
     * A semaphore token indicating that necessary permit has been exclusively acquired. Once the
     * protected action is completed, permits may be released by calling
     * {@link Token#releaseLock()}
     */
    interface Token extends AsyncLock.Token {
        long permits();
    }
    
    static AsyncSemaphoreLock create(long permits, boolean fair) {
        return new DefaultAsyncSemaphoreLock(permits, fair);
    }
}
