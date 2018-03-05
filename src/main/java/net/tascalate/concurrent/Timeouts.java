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

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

class Timeouts {
    
    private Timeouts() {}
    
    /**
     * Creates a promise that is resolved successfully after delay specified
     * @param duration
     * the duration of timeout
     * @return
     * the new promise
     */
    static Promise<Duration> delay(Duration duration) {
        final CompletablePromise<Duration> promise = new CompletablePromise<>();
        final Future<?> timeout = scheduler.schedule(
            () -> promise.onSuccess(duration), 
            duration.toNanos(), TimeUnit.NANOSECONDS
        );
        promise.whenComplete((r, e) -> {
          if (null != e) {
              timeout.cancel(true);
          }
        });
        return promise;
    }
    
    /**
     * Creates a promise that is resolved after delay specified
     * @param delay
     * the duration of timeout
     * @param timeUnit
     * the time unit of the delay
     * @return
     * the new promise
     */
    static Promise<Duration> delay(long delay, TimeUnit timeUnit) {
        return delay( toDuration(delay, timeUnit) );
    }

    /**
     * Creates a promise that is resolved erronously with {@link TimeoutException} after delay specified
     * @param duration
     * the duration of timeout
     * @return
     * the new promise
     */
    static <T> Promise<T> failAfter(Duration duration) {
        final CompletablePromise<T> promise = new CompletablePromise<>();
        final Future<?> timeout = scheduler.schedule(
            () -> promise.onFailure(new TimeoutException("Timeout after " + duration)), 
            duration.toNanos(), TimeUnit.NANOSECONDS
        );
        promise.whenComplete((r, e) -> timeout.cancel(true));
        return promise;
    }

    /**
     * Creates a promise that is resolved erronously with {@link TimeoutException} after delay specified
     * @param delay
     * the duration of timeout
     * @param timeUnit
     * the time unit of the delay
     * @return
     * the new promise
     */    
    static <T> Promise<T> failAfter(long delay, TimeUnit timeUnit) {
        return failAfter( toDuration(delay, timeUnit) );
    }
    
    static Duration toDuration(long delay, TimeUnit timeUnit) {
        return Duration.ofNanos(timeUnit.toNanos(delay));
    }
    
    static <T, U> BiConsumer<T, U> timeoutsCleanup(Promise<T> self, Promise<?> timeout, boolean cancelOnTimeout) {
        return (r, e) -> {
            // Result comes from timeout and cancel-on-timeout is set
            // If both are done then cancel has no effect anyway
            if ((timeout.isDone() && !timeout.isCancelled()) && cancelOnTimeout) {
                self.cancel(true);
            }
            timeout.cancel(true);
        };
    }
    
    static <T, E extends Throwable> BiConsumer<T, E> configureDelay(Promise<? extends T> self, CompletablePromise<? super T> delayed, Duration duration, boolean delayOnError) {
        return (originalResult, originalError) -> {
            if (originalError == null || (!self.isCancelled() && delayOnError)) {
                Promise<?> timeout = delay(duration);
                delayed.whenComplete( (r, e) -> timeout.cancel(true) );
                timeout.whenComplete( (r, timeoutError) -> {
                    if (null != timeoutError) {
                        delayed.onFailure(Promises.wrapException(timeoutError));
                    } else if (null == originalError) {
                        delayed.onSuccess(originalResult);
                    } else {
                        delayed.onFailure(Promises.wrapException(originalError));
                    }
                });
            } else {
                // when error and should not delay on error
                delayed.onFailure(Promises.wrapException(originalError));
            }
        };
    }
    
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            final Thread result = Executors.defaultThreadFactory().newThread(r);
            result.setDaemon(true);
            result.setName("net.tascalate.concurrent.Timeouts");
            return result;
        }
    });
}
