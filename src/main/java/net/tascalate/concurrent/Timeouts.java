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

import static net.tascalate.concurrent.SharedFunctions.wrapCompletionException;
import static net.tascalate.concurrent.LinkedCompletion.FutureCompletion;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

class Timeouts {
    
    static final Duration NEGATIVE_DURATION = Duration.ofNanos(-1);
    
    private Timeouts() {}
    
    /**
     * Creates a promise that is resolved successfully after delay specified
     * @param duration
     * the duration of timeout
     * @return
     * the new promise
     */
    static Promise<Duration> delay(Duration duration) {
        TimeUnit unit;
        long amount;
        // Try to get value with best precision without throwing ArythmeticException due to overflow
        if (duration.compareTo(MAX_BY_NANOS) < 0) {
            amount = duration.toNanos();
            unit = TimeUnit.NANOSECONDS;
        } else if (duration.compareTo(MAX_BY_MILLIS) < 0) {
            amount = duration.toMillis();
            unit = TimeUnit.MILLISECONDS;
        } else {
            amount = duration.getSeconds();
            unit = TimeUnit.SECONDS; 
        }
        
        FutureCompletion<Duration> result = new FutureCompletion<>();
        Future<?> timeout = scheduler.schedule( 
            () -> result.complete(duration), amount, unit 
        );
        return result.dependsOn(timeout).toPromise();
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
        TimeUnit unit;
        long amount;
        // Try to get value with best precision without throwing ArythmeticException due to overflow
        if (duration.compareTo(MAX_BY_NANOS) < 0) {
            amount = duration.toNanos();
            unit = TimeUnit.NANOSECONDS;
        } else if (duration.compareTo(MAX_BY_MILLIS) < 0) {
            amount = duration.toMillis();
            unit = TimeUnit.MILLISECONDS;
        } else {
            amount = duration.getSeconds();
            unit = TimeUnit.SECONDS; 
        }
        
        FutureCompletion<T> result = new FutureCompletion<>();
        Future<?> timeout = scheduler.schedule(
            () -> result.completeExceptionally(new TimeoutException("Timeout after " + duration)), 
            amount, unit
        );
        return result.dependsOn(timeout).toPromise();
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
        return Duration.of(delay, toChronoUnit(timeUnit));
    }
    
    static <T, U> BiConsumer<T, U> timeoutsCleanup(Promise<T> self, Promise<?> timeout, boolean cancelOnTimeout) {
        return (r, e) -> {
            // Result comes from timeout and cancel-on-timeout is set
            // If both are done then cancel has no effect anyway
            if (cancelOnTimeout && timeout.isDone() && !timeout.isCancelled()) {
                self.cancel(true);
            }
            timeout.cancel(true);
        };
    }
    
    static <T, E extends Throwable> BiConsumer<T, E> configureDelay(Promise<? extends T> self, CompletableFuture<? super T> delayed, Duration duration, boolean delayOnError) {
        return (originalResult, originalError) -> {
            if (originalError == null || (delayOnError && !self.isCancelled())) {
                Promise<?> timeout = delay(duration);
                delayed.whenComplete( (r, e) -> timeout.cancel(true) );
                timeout.whenComplete( (r, timeoutError) -> {
                    if (null != timeoutError) {
                        delayed.completeExceptionally(wrapCompletionException(timeoutError));
                    } else if (null == originalError) {
                        delayed.complete(originalResult);
                    } else {
                        delayed.completeExceptionally(wrapCompletionException(originalError));
                    }
                });
            } else {
                // when error and should not delay on error
                delayed.completeExceptionally(wrapCompletionException(originalError));
            }
        };
    }
    
    private static ChronoUnit toChronoUnit(TimeUnit unit) { 
        Objects.requireNonNull(unit, "unit"); 
        switch (unit) { 
            case NANOSECONDS: 
                return ChronoUnit.NANOS; 
            case MICROSECONDS: 
                return ChronoUnit.MICROS; 
            case MILLISECONDS: 
                return ChronoUnit.MILLIS; 
            case SECONDS: 
                return ChronoUnit.SECONDS; 
            case MINUTES: 
                return ChronoUnit.MINUTES; 
            case HOURS: 
                return ChronoUnit.HOURS; 
            case DAYS: 
                return ChronoUnit.DAYS; 
            default: 
                throw new IllegalArgumentException("Unknown TimeUnit constant"); 
        } 
    }     
    
    private static final Duration MAX_BY_NANOS  = Duration.ofNanos(Long.MAX_VALUE);
    private static final Duration MAX_BY_MILLIS = Duration.ofMillis(Long.MAX_VALUE);

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
