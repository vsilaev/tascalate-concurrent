/**
 * ﻿Copyright 2015-2017 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.concurrent;

public class RetryContext {
    private final RetryPolicy policy;
    private final int retry;
    private final long lastCallDuration;
    private final Throwable lastThrowable;
    
    public RetryContext(RetryPolicy policy, int retry, long lastCallDuration, Throwable lastThrowable) {
        this.policy = policy;
        this.retry = retry;
        this.lastCallDuration = lastCallDuration; 
        this.lastThrowable = lastThrowable;
    }
    

    public static RetryContext initial(RetryPolicy policy) {
        return new RetryContext(policy, 0, 0, null);
    }
    
    public RetryPolicy.Outcome shouldContinue() {
        return policy.shouldContinue(this);
    }
    
    public int getRetryCount() {
        return retry;
    }
    
    public long getLastCallDuration() {
        return lastCallDuration;
    }

    public Throwable getLastThrowable() {
        return lastThrowable;
    }
    
    public RetryContext nextRetry(long callDuration) {
        return new RetryContext(policy, retry + 1, callDuration, null);
    }
    
    public RetryContext nextRetry(long callDuration, Throwable throwable) {
        return new RetryContext(policy, retry + 1, callDuration, throwable);
    }
    
    public RetryException asFailure() {
        RetryException result = new RetryException(retry, lastCallDuration, lastThrowable);
        result.fillInStackTrace();
        return result;
    }
    
    public RetryContext asPrevRetry() {
        if (retry == 0) {
            throw new IllegalStateException("Initial retry has no previous retry");
        }
        return new RetryContext(policy, retry - 1, lastCallDuration, lastThrowable);
    }

}
