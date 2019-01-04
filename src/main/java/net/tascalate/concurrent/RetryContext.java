/**
 * ﻿Copyright 2015-2018 Valery Silaev (http://vsilaev.com)
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

import java.time.Duration;

public final class RetryContext {
    private final RetryPolicy policy;
    private final int retryCount;
    private final Duration lastCallDuration;
    private final Throwable lastThrowable;
    
    private RetryContext(RetryPolicy policy, int retryCount, Duration lastCallDuration, Throwable lastThrowable) {
        this.policy = policy;
        this.retryCount = retryCount;
        this.lastCallDuration = lastCallDuration; 
        this.lastThrowable = lastThrowable;
    }
    

    static RetryContext initial(RetryPolicy policy) {
        return new RetryContext(policy, 0, Duration.ZERO, null);
    }
    
    RetryPolicy.Outcome shouldContinue() {
        return policy.shouldContinue(this);
    }
    
    public int getRetryCount() {
        return retryCount;
    }
    
    public Duration getLastCallDuration() {
        return lastCallDuration;
    }

    public Throwable getLastThrowable() {
        return lastThrowable;
    }
    
    RetryContext nextRetry(Duration callDuration) {
        return nextRetry(callDuration, null);
    }
    
    RetryContext nextRetry(Duration callDuration, Throwable throwable) {
        return new RetryContext(policy, retryCount + 1, callDuration, throwable);
    }
    
    RetryException asFailure() {
        RetryException result = new RetryException(retryCount, lastCallDuration, lastThrowable);
        result.fillInStackTrace();
        return result;
    }
    
    public RetryContext overrideRetryCount(int newRetryCount) {
        return new RetryContext(policy, newRetryCount, lastCallDuration, lastThrowable);
    }
    
    public RetryContext overrideLastCallDuration(Duration newDuration) {
        return new RetryContext(policy, retryCount, newDuration, lastThrowable);
    }

    public RetryContext overrideLastThrowable(Throwable newThrowable) {
        return new RetryContext(policy, retryCount, lastCallDuration, newThrowable);
    }
}
