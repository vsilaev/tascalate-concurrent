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
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultAsyncSemaphoreLock extends AsyncSemaphoreBase<AsyncSemaphoreLock.Token> 
                                      implements AsyncSemaphoreLock {
    
    public DefaultAsyncSemaphoreLock(long totalPermits, boolean fair) {
        super(totalPermits, fair);
    }
    
    @Override
    public Optional<Token> drainPermits() {
        long acquired = drainPermitsInternal();
        return acquired > 0 ? Optional.of(createPromisePayload(acquired)) : Optional.empty(); 
    }
    
    @Override
    public Optional<Token> tryAcquire(long permitsCount) {
        if (tryAcquireInternal(permitsCount)) {
            return Optional.of(createPromisePayload(permitsCount));
        } else {
            return Optional.empty();
        }
    }
    
    @Override
    protected Token createPromisePayload(long permits) {
        return new AbstractToken(permits) {
            private final AtomicBoolean released = new AtomicBoolean();
            @Override
            public void release() {
                if (released.compareAndSet(false, true)) {
                    DefaultAsyncSemaphoreLock.this.release(permits());
                }
            }
        };  
    }

    @Override
    protected LockPromise createPromise(long permitsCount) {
        return new LockPromise(permitsCount);
    }
    
    abstract static class AbstractToken implements Token {
        private final long permitsCount;
        
        public AbstractToken(long permitsCount) {
            this.permitsCount = permitsCount;
        }
        
        @Override
        public long permits() {
            return permitsCount;
        }
    }
    
    private class LockPromise extends AbstractSemaphorePromise {
        private final AtomicBoolean released = new AtomicBoolean();
        private final Token token;
        
        LockPromise(long permitsCount) {
            this.token = new AbstractToken(permitsCount) {
                @Override
                public void release() {
                    LockPromise.this.release();
                }
            };
        }
        
        long permits() {
            return token.permits();
        }
        
        boolean acquire() {
            return onSuccess(token);
        }
        
        void release() {
            if (released.compareAndSet(false, true)) {
                DefaultAsyncSemaphoreLock.this.release(permits());
            }            
        }        
    }
}
