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
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import net.tascalate.concurrent.core.CancelMethodsCache;

class AsyncInternals {
    
    private AsyncInternals() {}
    
    abstract static class Either<A, B> {
        A left()  { throw new UnsupportedOperationException(); }
        B right() { throw new UnsupportedOperationException(); }

        abstract boolean isLeft();
        
        static <A, B> Either<A, B> lift(A a, B b) {
            if (null == b) {
                return new Either<A, B>() {
                    A left() { return a; }
                    boolean isLeft() { return true; }
                };
            } else {
                return new Either<A, B>() {
                    B right() { return b; }
                    boolean isLeft() { return false; }
                };
            }
        }
    }
    
    static class TimeMeasurement {
        final TimeUnit unit;
        final long amount;
        
        TimeMeasurement(Duration duration) {
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
    
    static <T> Promise<T> exceptionallyAsync(Promise<T> p, 
                                             Function<Throwable, ? extends T> fn) {
        
        DependentPromise<Either<T, Throwable>> h = p.dependent().handle(Either::lift, false);
        return
        h.thenCompose(e -> e.isLeft() ? p : h.thenApplyAsync(in -> fn.apply(in.right())), true)
         .unwrap(); 
    }

    static <T> Promise<T> exceptionallyAsync(Promise<T> p, 
                                             Function<Throwable, ? extends T> fn, 
                                             Executor executor) {
        DependentPromise<Either<T, Throwable>> h = p.dependent().handle(Either::lift, false);
        return
        h.thenCompose(e -> e.isLeft() ? p : h.thenApplyAsync(in -> fn.apply(in.right()), executor), true)
         .unwrap(); 
    }
    
    static <T> Promise<T> exceptionallyCompose(Promise<T> p, 
                                               Function<Throwable, ? extends CompletionStage<T>> fn) {
        
        DependentPromise<Either<T, Throwable>> h = p.dependent().handle(Either::lift, false);
        return
        h.thenCompose(e -> e.isLeft() ? p : fn.apply(e.right()), true)
         .unwrap(); 
    }

    static <T> Promise<T> exceptionallyComposeAsync(Promise<T> p,
                                                    Function<Throwable, ? extends CompletionStage<T>> fn) {
        
        DependentPromise<Either<T, Throwable>> h = p.dependent().handle(Either::lift, false);
        return
        h.thenCompose(e -> e.isLeft() ? p : h.thenComposeAsync(in -> fn.apply(in.right())), true)
         .unwrap(); 
    }
    
    static <T> Promise<T> exceptionallyComposeAsync(Promise<T> p,
                                                    Function<Throwable, ? extends CompletionStage<T>> fn,
                                                    Executor executor) {
        DependentPromise<Either<T, Throwable>> h = p.dependent().handle(Either::lift, false);
        return
        h.thenCompose(e -> e.isLeft() ? p : h.thenComposeAsync(in -> fn.apply(in.right()), executor), true)
         .unwrap();         
    }
    
    /**
     * Creates a promise that is resolved successfully after delay specified
     * @param duration
     * the duration of timeout
     * @return
     * the new promise
     */
    static Promise<Duration> delay(Duration duration) {
        TimeMeasurement tm = new TimeMeasurement(duration);
        CompletableFutureWrapper<Duration> result = new CompletableFutureWrapper<>();
        Future<?> timeout = scheduler.schedule( 
            () -> result.success(duration), tm.amount, tm.unit 
        );
        return result.onCancel(() -> timeout.cancel(true));
    }
    
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
        TimeMeasurement tm = new TimeMeasurement(duration);
        CompletableFutureWrapper<T> result = new CompletableFutureWrapper<>();
        Future<?> timeout = scheduler.schedule(
            () -> result.failure(new TimeoutException("Timeout after " + duration)), 
            tm.amount, tm.unit
        );
        return result.onCancel(() -> timeout.cancel(true));
    }
    
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
    
    static Throwable unwrapCompletionException(Throwable ex) {
        Throwable nested = ex;
        while (nested instanceof CompletionException) {
            nested = nested.getCause();
        }
        return null == nested ? ex : nested;
    }

    static CompletionException wrapCompletionException(Throwable e) {
        if (e instanceof CompletionException) {
            return (CompletionException) e;
        } else {
            return new CompletionException(e);
        }
    }
    
    static Throwable unwrapExecutionException(Throwable ex) {
        Throwable nested = ex;
        while (nested instanceof ExecutionException) {
            nested = nested.getCause();
        }
        return null == nested ? ex : nested;
    }
    
    static ExecutionException wrapExecutionException(Throwable e) {
        if (e instanceof ExecutionException) {
            return (ExecutionException) e;
        } else {
            return new ExecutionException(e);
        }
    }
    
    static <T> CompletionStage<T> failure(Function<? super T, Throwable> errorSupplier, T value) {
        CompletableFuture<T> result = new CompletableFuture<>();
        result.completeExceptionally(errorSupplier.apply(value));
        return result;
    }

    static boolean cancelPromise(CompletionStage<?> promise, boolean mayInterruptIfRunning) {
        if (promise instanceof Future) {
            Future<?> future = (Future<?>) promise;
            return future.cancel(mayInterruptIfRunning);
        } else {
            return CancelMethodsCache.cancellationOf(promise.getClass())
                                     .apply(promise, mayInterruptIfRunning);
        }
    }
    
    static <T, U> Function<T, U> applyAndCancel(Function<T, U> fn, boolean cancel, Promise<?> target, Promise<?> timeout) {
        if (cancel) {
            return fn.andThen(u -> {
                target.cancel(true); 
                if (null != timeout) {
                    timeout.cancel(true);
                }
                return u; 
            });
        } else if (null == timeout){
            return fn;
        } else {
            return fn.andThen(u -> {
                timeout.cancel(true);
                return u; 
            });
        }
    }

    @SuppressWarnings("unchecked")
    static <U, V> BiFunction<U, V, U> selectFirst() {
        return (BiFunction<U, V, U>)SELECT_FIRST;
    }
    
    @SuppressWarnings("unchecked")
    static <U, V> BiFunction<U, V, V> selectSecond() {
        return (BiFunction<U, V, V>)SELECT_SECOND;
    }
    
    @SuppressWarnings("unchecked")
    static <T, R> Function<T, R> nullify() {
        return (Function<T, R>)NULLIFY;
    }
    
    static <T> Supplier<T> supply(T value) {
        return () -> value;
    }
    
    static void iif(boolean v) {}

    static final Function<Object, Throwable> NO_SUCH_ELEMENT = t -> new NoSuchElementException("Result rejected by filter: " + t);
    
    private static final BiFunction<Object, Object, Object> SELECT_FIRST  = (u, v) -> u;
    private static final BiFunction<Object, Object, Object> SELECT_SECOND = (u, v) -> v;
    private static final Function<Object, Object> NULLIFY = v -> null;
    
    private static final Duration MAX_BY_NANOS  = Duration.ofNanos(Long.MAX_VALUE);
    private static final Duration MAX_BY_MILLIS = Duration.ofMillis(Long.MAX_VALUE);

    private static final ScheduledExecutorService scheduler = 
        Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder()
                                                .withDaemonFlag(true)
                                                .withNameFormat("tascalate-concurrent-timer-workers-%1$d")
                                            .build());
}
