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
package net.tascalate.concurrent;

import java.time.Duration;

public class RetryException extends Exception {
    private static final long serialVersionUID = 1L;
    
    private final int retry;
    private final Duration lastCallDuration;
    
    public RetryException() {
        retry = 0;
        lastCallDuration = Duration.ZERO;
    }
    
    public RetryException(int retry, Duration lastCallDuration, Throwable lastThrowable) {
        super(lastThrowable);
        this.retry = retry;
        this.lastCallDuration = lastCallDuration;
    }
    
    public int getRetryCount() {
        return retry;
    }
    
    public Duration getLastCallDuration() {
        return lastCallDuration;
    }
}
