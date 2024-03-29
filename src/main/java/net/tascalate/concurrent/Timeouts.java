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
package net.tascalate.concurrent;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
        TimeMeasurment tm = new TimeMeasurment(duration);
        CompletableFutureWrapper<Duration> result = new CompletableFutureWrapper<>();
        Future<?> timeout = scheduler.schedule( 
            () -> result.success(duration), tm.amount, tm.unit 
        );
        return result.onCancel(() -> timeout.cancel(true));
    }
    
    /*
     * Creates a promise that is resolved after delay specified
     * @param delay
     * the duration of timeout
     * @param timeUnit
     * the time unit of the delay
     * @return
     * the new promise
     */
    /*
    static Promise<Duration> delay(long delay, TimeUnit timeUnit) {
        return delay( toDuration(delay, timeUnit) );
    }
    
    static <T> Promise<T> delayed(T value, long delay, TimeUnit timeUnit) {
        return delayed(value, toDuration(delay, timeUnit));
    }
    */
    
    static <T> Promise<T> delayed(T value, Duration duration) {
        return delay(duration).dependent().thenApply(d -> value, true);        
    }
    
    /**
     * Creates a promise that is resolved erronously with {@link TimeoutException} after delay specified
     * @param duration
     * the duration of timeout
     * @return
     * the new promise
     */
    static <T> Promise<T> failAfter(Duration duration) {
        TimeMeasurment tm = new TimeMeasurment(duration);
        CompletableFutureWrapper<T> result = new CompletableFutureWrapper<>();
        Future<?> timeout = scheduler.schedule(
            () -> result.failure(new TimeoutException("Timeout after " + duration)), 
            tm.amount, tm.unit
        );
        return result.onCancel(() -> timeout.cancel(true));
    }

    /*
     * Creates a promise that is resolved erronously with {@link TimeoutException} after delay specified
     * @param delay
     * the duration of timeout
     * @param timeUnit
     * the time unit of the delay
     * @return
     * the new promise
     */
    /*
    static <T> Promise<T> failAfter(long delay, TimeUnit timeUnit) {
        return failAfter( toDuration(delay, timeUnit) );
    }
    */
    
    static Duration toDuration(long delay, TimeUnit timeUnit) {
        return Duration.of(delay, toChronoUnit(timeUnit));
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
    
    static class TimeMeasurment {
        final TimeUnit unit;
        final long amount;
        
        TimeMeasurment(Duration duration) {
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
        }
    }
    
    private static final Duration MAX_BY_NANOS  = Duration.ofNanos(Long.MAX_VALUE);
    private static final Duration MAX_BY_MILLIS = Duration.ofMillis(Long.MAX_VALUE);

    private static final ScheduledExecutorService scheduler = 
        Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder()
                                                .withDaemonFlag(true)
                                                .withNameFormat(Timeouts.class.getName() + "-workers-%1$d")
                                            .build());
}
