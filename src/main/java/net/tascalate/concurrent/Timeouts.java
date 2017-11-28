package net.tascalate.concurrent;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
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
            duration.toMillis(), TimeUnit.MILLISECONDS
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
            duration.toMillis(), TimeUnit.MILLISECONDS
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
        return Duration.of(delay, toChronoUnit(timeUnit));
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
    
    static <R, E extends Throwable> BiConsumer<R, E> configureDelay(Promise<?> self, CompletablePromise<?> delayed, Duration duration, boolean delayOnError) {
        return (r, e) -> {
            if (e == null || (!self.isCancelled() && delayOnError)) {
                Promise<?> timeout = Timeouts.delay(duration);
                delayed.whenComplete( (u, v) -> timeout.cancel(true) );
                timeout.whenComplete( (u, v) -> {
                    if (null != v) {
                        // Timeout-related error
                        delayed.onFailure(Promises.wrapException(v));
                    } else if (null == e) {
                        // Original error
                        delayed.onSuccess(null);
                    } else {
                        // Original result
                        delayed.onFailure(Promises.wrapException(e));
                    }
                });
            } else {
                // when error and should not delay on error
                delayed.onFailure(Promises.wrapException(e));
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
