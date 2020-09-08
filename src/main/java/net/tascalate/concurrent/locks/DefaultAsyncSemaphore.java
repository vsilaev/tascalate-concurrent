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

public class DefaultAsyncSemaphore extends AsyncSemaphoreBase<Long> 
                                   implements AsyncSemaphore {
    
    public DefaultAsyncSemaphore(long totalPermits, boolean fair) {
        super(totalPermits, fair);
    }
    
    @Override
    public long drainPermits() {
        return drainPermitsInternal();
    }
    
    @Override
    public boolean tryAcquire(long permits) {
        return tryAcquireInternal(permits);
    }
    
    @Override
    public void release(long permits) {
        super.release(permits);
    }
    
    @Override
    protected Long createPromisePayload(long permits) {
        return Long.valueOf(permits);
    }

    @Override
    protected SemaphorePromise createPromise(long permits) {
        return new SemaphorePromise(permits);
    }
    
    private class SemaphorePromise extends AbstractSemaphorePromise {
        private final Long permits;
        
        SemaphorePromise(long permits) {
            this.permits = Long.valueOf(permits);
        }
        
        long permits() {
            return permits.longValue();
        }
        
        boolean acquire() {
            return onSuccess(permits);
        }
    }
}
