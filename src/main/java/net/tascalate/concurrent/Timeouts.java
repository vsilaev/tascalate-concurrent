package net.tascalate.concurrent;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

public class Timeouts {
    
    private Timeouts() {
        
    }
    
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
        1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                final Thread result = Executors.defaultThreadFactory().newThread(r);
                result.setDaemon(true);
                result.setName("net.tascalate.concurrent.Timeouts");
                return result;
            }
        }
    );
    
    public static Promise<Duration> timeout(Duration duration) {
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
    
    public static Promise<Duration> timeout(long delay, TimeUnit timeUnit) {
        return timeout( Duration.of(delay, toChronoUnit(timeUnit)) );
    }

    public static <T> Promise<T> failAfter(Duration duration) {
        final CompletablePromise<T> promise = new CompletablePromise<>();
        final Future<?> timeout = scheduler.schedule(
            () -> promise.onFailure(new TimeoutException("Timeout after " + duration)), 
            duration.toMillis(), TimeUnit.MILLISECONDS
        );
        promise.whenComplete((r, e) -> timeout.cancel(true));
        return promise;
    }
    
    public static <T> Promise<T> failAfter(long delay, TimeUnit timeUnit) {
        return failAfter(Duration.of(delay, toChronoUnit(timeUnit)));
    }
    
    public static <T> Promise<T> poll(Supplier<Optional<? extends T>> supplier, Duration delay) {
        final CompletablePromise<T> promise = new CompletablePromise<>();
        Runnable command = () -> {
          try {
              supplier.get().ifPresent(v -> promise.onSuccess(v));
          } catch (final Exception ex) {
              promise.onFailure(ex);
          }
        };
        final Future<?> poller = scheduler.scheduleAtFixedRate(command, 0, delay.toMillis(), TimeUnit.MILLISECONDS);
        promise.whenComplete((r, e) -> poller.cancel(true));
        return promise;
    }
    
    static <T> Promise<T> within(Promise<T> promise, long delay, TimeUnit timeUnit) {
        return within(promise, Duration.of(delay, toChronoUnit(timeUnit)) );
    }
    
    static <T> Promise<T> within(Promise<T> promise, Duration duration) {
        final Promise<T> onTimeout = failAfter(duration);
        final Promise<T> result = promise.applyToEitherAsync(onTimeout, Function.identity());
        result.whenComplete((r, e) -> onTimeout.cancel(true));
        return result;
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
}
