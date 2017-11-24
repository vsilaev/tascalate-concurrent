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
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utility class to create a resolved (either successfully or faulty) {@link Promise}-s; 
 * to wrap an arbitrary {@link CompletionStage} interface to the {@link Promise} API;
 * to combine several {@link CompletionStage}-s into aggregating promise.
 *    
 * @author vsilaev
 *
 */
public class Promises {

    /**
     * Method to create a successfully resolved {@link Promise} with a value provided 
     * @param <T>
     *   a type of the value
     * @param value
     *   a value to wrap
     * @return 
     *   a successfully resolved {@link Promise} with a value provided
     */
    public static <T> Promise<T> success(T value) {
        CompletablePromise<T> result = new CompletablePromise<>();
        result.onSuccess(value);
        return result;
    }
    
    /**
     * Method to create a faulty resolved {@link Promise} with an exception provided 
     * @param exception
     *   an exception to wrap
     * @return 
     *   a faulty resolved {@link Promise} with an exception provided
     */    
    public static <T> Promise<T> failure(Throwable exception) {
        CompletablePromise<T> result = new CompletablePromise<>();
        result.onFailure(exception);
        return result;
    }

    /**
     * Adapts a stage passed to the {@link Promise} API
     * @param <T>
     *   a type of the value
     * @param stage
     *   a {@link CompletionStage} to be wrapped
     * @return
     *   a {@link Promise}
     */
    public static <T> Promise<T> from(CompletionStage<T> stage) {
        if (stage instanceof Promise) {
            return (Promise<T>) stage;
        }

        if (stage instanceof CompletableFuture) {
            return new CompletablePromise<>((CompletableFuture<T>)stage);
        }
        
        CompletablePromise<T> result = createLinkedPromise(stage);
        stage.whenComplete(handler(result::onSuccess, result::onFailure));
        return result;
    }

    static <T, R> Promise<R> from(CompletionStage<T> stage, 
                                  Function<? super T, ? extends R> resultConverter,
                                  Function<? super Throwable, ? extends Throwable> errorConverter) {
        
        CompletablePromise<R> result = createLinkedPromise(stage);
        stage.whenComplete(handler(
            acceptConverted(result::onSuccess, resultConverter),
            acceptConverted(result::onFailure, errorConverter)
        ));
        return result;
    }
    
    /**
     * Converts {@link CompletionStage} to a {@link DependentPromise}
     * @param stage
     * original CompletionStage
     * @param <T>
     * a type of the successfully resolved promise value  
     * @return
     * created DependentPromise
     */
    public static <T> DependentPromise<T> dependent(CompletionStage<T> stage) {
        return dependent(from(stage));
    }

    /**
     * Converts {@link Promise} to a {@link DependentPromise}
     * @param stage
     * original Promise
     * @param <T>
     * a type of the successfully resolved promise value 
     * @return
     * created DependentPromise
     */
    public static <T> DependentPromise<T> dependent(Promise<T> stage) {
        return DependentPromise.from(stage);
    }
    
    public static <T> Promise<T> task(CompletionStage<T> stage, Executor executor) {
        return dependent(CompletableTask.asyncOn(executor))
               .thenCombineAsync(stage, (u, v) -> v, PromiseOrigin.PARAM_ONLY);
    }

    private static <T, R> CompletablePromise<R> createLinkedPromise(CompletionStage<T> stage) {
        return new CompletablePromise<R>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (super.cancel(mayInterruptIfRunning)) {
                    cancelPromise(stage, mayInterruptIfRunning);
                    return true;
                } else {
                    return false;
                }
            }
        };
    }
    
    /**
     * <p>Returns a promise that is resolved successfully when all {@link CompletionStage}-s passed as parameters are completed normally; 
     * if any promise completed exceptionally, then resulting promise is resolved faulty as well.
     * <p>The resolved result of this promise contains a list of the resolved results of the {@link CompletionStage}-s passed as an 
     * argument at corresponding positions.
     * <p>When resulting promise is resolved faulty, all remaining incomplete {@link CompletionStage}-s are cancelled.  
     * @param <T>
     *   a common supertype of the resulting values
     * @param promises
     *   an array of {@link CompletionStage}-s to combine
     * @return
     *   a combined promise
     */
    @SafeVarargs
    public static <T> Promise<List<T>> all(CompletionStage<? extends T>... promises) {
        return all(Arrays.asList(promises));
    }
    
    
    public static <T> Promise<List<T>> all(List<CompletionStage<? extends T>> promises) {
        return all(true, promises);        
    }
    /**
     * <p>Returns a promise that is resolved successfully when all {@link CompletionStage}-s passed as parameters are completed normally; 
     * if any promise completed exceptionally, then resulting promise is resolved faulty as well.
     * <p>The resolved result of this promise contains a list of the resolved results of the {@link CompletionStage}-s passed as an 
     * argument at corresponding positions.
     * <p>When resulting promise is resolved faulty <em>and</em> <code>cancelRemaining</code> parameter is <code>true</code>, 
     * all remaining incomplete {@link CompletionStage}-s are cancelled.  
     * @param <T>
     *   a common supertype of the resulting values
     * @param cancelRemaining
     *   when true and resulting promise is resolved faulty all incomplete {@link CompletionStage}-s are cancelled
     * @param promises
     *   an array of {@link CompletionStage}-s to combine
     * @return
     *   a combined promise
     */
    @SafeVarargs
    public static <T> Promise<List<T>> all(boolean cancelRemaining, CompletionStage<? extends T>... promises) {
        return all(cancelRemaining, Arrays.asList(promises));
    }

    public static <T> Promise<List<T>> all(boolean cancelRemaining, List<CompletionStage<? extends T>> promises) {
        return atLeast(null != promises ? promises.size() : 0, 0, cancelRemaining, promises);
    }
    /**
     * <p>Returns a promise that is resolved successfully when any {@link CompletionStage} passed as parameters is completed normally (race is possible); 
     * if all promises completed exceptionally, then resulting promise is resolved faulty as well.
     * <p>The resolved result of this promise contains a value of the first resolved result of the {@link CompletionStage}-s passed as an 
     * argument.
     * <p>When resulting promise is resolved successfully, all remaining incomplete {@link CompletionStage}-s are cancelled.
     * 
     * @param <T>
     *   a common supertype of the resulting values 
     * @param promises
     *   an array of {@link CompletionStage}-s to combine
     * @return
     *   a combined promise
     */
    @SafeVarargs
    public static <T> Promise<T> any(CompletionStage<? extends T>... promises) {
        return any(Arrays.asList(promises));
    }

    public static <T> Promise<T> any(List<CompletionStage<? extends T>> promises) {
        return any(true, promises);
    }
    /**
     * <p>Returns a promise that is resolved successfully when any {@link CompletionStage} passed as parameters is completed normally (race is possible); 
     * if all promises completed exceptionally, then resulting promise is resolved faulty as well.
     * <p>The resolved result of this promise contains a value of the first resolved result of the {@link CompletionStage}-s passed as an 
     * argument.
     * <p>When resulting promise is resolved successfully <em>and</em> <code>cancelRemaining</code> parameter is <code>true</code>, 
     * all remaining incomplete {@link CompletionStage}-s are cancelled.
     * 
     * @param <T>
     *   a common supertype of the resulting values 
     * @param cancelRemaining
     *   when true and resulting promise is resolved faulty all incomplete {@link CompletionStage}-s are cancelled   
     * @param promises
     *   an array of {@link CompletionStage}-s to combine
     * @return
     *   a combined promise
     */
    @SafeVarargs
    public static <T> Promise<T> any(boolean cancelRemaining, CompletionStage<? extends T>... promises) {
        return any(cancelRemaining, Arrays.asList(promises));
    }
    
    public static <T> Promise<T> any(boolean cancelRemaining, List<CompletionStage<? extends T>> promises) {
        return unwrap(atLeast(1, (promises == null ? 0 : promises.size()) - 1, cancelRemaining, promises), false);
    }
    
    /**
     * <p>Returns a promise that is resolved successfully when any {@link CompletionStage} passed as parameters is completed normally (race is possible); 
     * if any promise completed exceptionally before first result is available, then resulting promise is resolved faulty as well 
     * (unlike non-Strict variant, where exceptions are ignored if result is available at all).
     * <p>The resolved result of this promise contains a value of the first resolved result of the {@link CompletionStage}-s passed as an 
     * argument.
     * <p>When resulting promise is resolved either successfully or faulty, all remaining incomplete {@link CompletionStage}-s are cancelled. 
     * @param <T>
     *   a common supertype of the resulting values 
     * @param promises
     *   an array of {@link CompletionStage}-s to combine
     * @return
     *   a combined promise
     */
    @SafeVarargs
    public static <T> Promise<T> anyStrict(CompletionStage<? extends T>... promises) {
        return anyStrict(Arrays.asList(promises)); 
    }
    
    public static <T> Promise<T> anyStrict(List<CompletionStage<? extends T>> promises) {
        return anyStrict(true, promises);        
    }

    /**
     * <p>Returns a promise that is resolved successfully when any {@link CompletionStage} passed as parameters is completed normally (race is possible); 
     * if any promise completed exceptionally before first result is available, then resulting promise is resolved faulty as well 
     * (unlike non-Strict variant, where exceptions are ignored if result is available at all).
     * <p>The resolved result of this promise contains a value of the first resolved result of the {@link CompletionStage}-s passed as an 
     * argument.
     * <p>When resulting promise is resolved either successfully or faulty <em>and</em> <code>cancelRemaining</code> parameter is <code>true</code>, 
     * all remaining incomplete {@link CompletionStage}-s are cancelled. 
     * @param <T>
     *   a common supertype of the resulting values 
     * @param cancelRemaining
     *   when true and resulting promise is resolved faulty all incomplete {@link CompletionStage}-s are cancelled     
     * @param promises
     *   an array of {@link CompletionStage}-s to combine
     * @return
     *   a combined promise
     */
    @SafeVarargs
    public static <T> Promise<T> anyStrict(boolean cancelRemaining, CompletionStage<? extends T>... promises) {
        return anyStrict(cancelRemaining, Arrays.asList(promises));
    }
    
    public static <T> Promise<T> anyStrict(boolean cancelRemaining, List<CompletionStage<? extends T>> promises) {
        return unwrap(atLeast(1, 0, cancelRemaining, promises), true);
    }
    
    /**
     * <p>Generalization of the {@link Promises#any(CompletionStage...)} method.</p>
     * <p>Returns a promise that is resolved successfully when at least <code>minResultCount</code> of {@link CompletionStage}-s passed as parameters 
     * are completed normally (race is possible); if less than <code>minResultCount</code> of promises completed normally, then resulting promise 
     * is resolved faulty.
     * <p>The resolved result of this promise contains a list of the resolved results of the {@link CompletionStage}-s passed as an 
     * argument at corresponding positions. Non-completed or completed exceptionally promises have <code>null</code> values.
     * <p>When resulting promise is resolved successfully, all remaining incomplete {@link CompletionStage}-s are cancelled. 
     * 
     * @param <T>
     *   a common supertype of the resulting values 
     * @param minResultsCount
     *   a minimum number of promises that should be completed normally to resolve resulting promise successfully 
     * @param promises
     *   an array of {@link CompletionStage}-s to combine 
     * @return
     *   a combined promise 
     */
    @SafeVarargs
    public static <T> Promise<List<T>> atLeast(int minResultsCount, CompletionStage<? extends T>... promises) {
        return atLeast(minResultsCount, Arrays.asList(promises));
    }
    
    public static <T> Promise<List<T>> atLeast(int minResultsCount, List<CompletionStage<? extends T>> promises) {
        return atLeast(minResultsCount, true, promises);
    }

    /**
     * <p>Generalization of the {@link Promises#any(CompletionStage...)} method.</p>
     * <p>Returns a promise that is resolved successfully when at least <code>minResultCount</code> of {@link CompletionStage}-s passed as parameters 
     * are completed normally (race is possible); if less than <code>minResultCount</code> of promises completed normally, then resulting promise 
     * is resolved faulty.
     * <p>The resolved result of this promise contains a list of the resolved results of the {@link CompletionStage}-s passed as an 
     * argument at corresponding positions. Non-completed or completed exceptionally promises have <code>null</code> values.
     * <p>When resulting promise is resolved successfully <em>and</em> <code>cancelRemaining</code> parameter is <code>true</code>, 
     * all remaining incomplete {@link CompletionStage}-s are cancelled.  
     * 
     * @param <T>
     *   a common supertype of the resulting values 
     * @param minResultsCount
     *   a minimum number of promises that should be completed normally to resolve resulting promise successfully 
     * @param cancelRemaining
     *   when true and resulting promise is resolved faulty all incomplete {@link CompletionStage}-s are cancelled    
     * @param promises
     *   an array of {@link CompletionStage}-s to combine 
     * @return
     *   a combined promise 
     */
    @SafeVarargs
    public static <T> Promise<List<T>> atLeast(int minResultsCount, boolean cancelRemaining, CompletionStage<? extends T>... promises) {
        return atLeast(minResultsCount, cancelRemaining, Arrays.asList(promises));
    }
    
    public static <T> Promise<List<T>> atLeast(int minResultsCount, boolean cancelRemaining, List<CompletionStage<? extends T>> promises) {
        return atLeast(minResultsCount, (promises == null ? 0 : promises.size()) - minResultsCount, cancelRemaining, promises);
    }
    
    /**
     * <p>Generalization of the {@link Promises#anyStrict(CompletionStage...)} method.</p>
     * <p>Returns a promise that is resolved successfully when at least <code>minResultCount</code> of {@link CompletionStage}-s passed as parameters 
     * are completed normally (race is possible); if less than <code>minResultCount</code> of promises completed normally, then resulting promise 
     * is resolved faulty. If any promise completed exceptionally <em>before</em> <code>minResultCount</code> of results are available, then 
     * resulting promise is resolved faulty as well. 
     * <p>The resolved result of this promise contains a list of the resolved results of the {@link CompletionStage}-s passed as an 
     * argument at corresponding positions. Non-completed promises have <code>null</code> values.
     * <p>When resulting promise is resolved either successfully or faulty, all remaining incomplete {@link CompletionStage}-s are cancelled.
     *  
     * @param <T>
     *   a common supertype of the resulting values 
     * @param minResultsCount
     *   a minimum number of promises that should be completed normally to resolve resulting promise successfully 
     * @param promises
     *   an array of {@link CompletionStage}-s to combine 
     * @return
     *   a combined promise 
     */    
    @SafeVarargs
    public static <T> Promise<List<T>> atLeastStrict(int minResultsCount, CompletionStage<? extends T>... promises) {
        return atLeastStrict(minResultsCount, Arrays.asList(promises));
    }
    
    public static <T> Promise<List<T>> atLeastStrict(int minResultsCount, List<CompletionStage<? extends T>> promises) {
        return atLeastStrict(minResultsCount, true, promises);
    }

    /**
     * <p>Generalization of the {@link Promises#anyStrict(CompletionStage...)} method.</p>
     * <p>Returns a promise that is resolved successfully when at least <code>minResultCount</code> of {@link CompletionStage}-s passed as parameters 
     * are completed normally (race is possible); if less than <code>minResultCount</code> of promises completed normally, then resulting promise 
     * is resolved faulty. If any promise completed exceptionally <em>before</em> <code>minResultCount</code> of results are available, then 
     * resulting promise is resolved faulty as well. 
     * <p>The resolved result of this promise contains a list of the resolved results of the {@link CompletionStage}-s passed as an 
     * argument at corresponding positions. Non-completed promises have <code>null</code> values.
     * <p>When resulting promise is resolved either successfully or faulty <em>and</em> <code>cancelRemaining</code> parameter is <code>true</code>, 
     * all remaining incomplete {@link CompletionStage}-s are cancelled.
     *  
     * @param <T>
     *   a common supertype of the resulting values 
     * @param minResultsCount
     *   a minimum number of promises that should be completed normally to resolve resulting promise successfully 
     * @param cancelRemaining
     *   when true and resulting promise is resolved faulty all incomplete {@link CompletionStage}-s are cancelled     
     * @param promises
     *   an array of {@link CompletionStage}-s to combine 
     * @return
     *   a combined promise 
     */    
    @SafeVarargs
    public static <T> Promise<List<T>> atLeastStrict(int minResultsCount, boolean cancelRemaining, CompletionStage<? extends T>... promises) {
        return atLeast(minResultsCount, cancelRemaining, Arrays.asList(promises));
    }    
    
    public static <T> Promise<List<T>> atLeastStrict(int minResultsCount, boolean cancelRemaining, List<CompletionStage<? extends T>> promises) {
        return atLeast(minResultsCount, 0, cancelRemaining, promises);
    }
    
    /**
     * <p>General method to combine several {@link CompletionStage}-s passed as arguments into single promise.</p>
     * <p>The resulting promise is resolved successfully when at least <code>minResultCount</code> of {@link CompletionStage}-s passed as parameters 
     * are completed normally (race is possible).
     * <p>If less than <code>minResultCount</code> of promises completed normally, then resulting promise is resolved faulty. 
     * <p>If <code>maxErrorsCount</code> of promises completed exceptionally <em>before</em> <code>minResultCount</code> of results are available, then 
     * resulting promise is resolved faulty as well. 
     * <p>The resolved result of this promise contains a list of the resolved results of the {@link CompletionStage}-s passed as an 
     * argument at corresponding positions. Non-completed promises and promises completed exceptionally have <code>null</code> values.
     * <p>When resulting promise is resolved either successfully or faulty, all remaining incomplete {@link CompletionStage}-s are cancelled <em>if</em>
     * <code>cancelRemaining</code> parameter is <code>true</code>. 
     * 
     * @param <T>
     *   a common supertype of the resulting values 
     * @param minResultsCount
     *   a minimum number of promises that should be completed normally to resolve resulting promise successfully
     * @param maxErrorsCount
     *   a maximum number of promises that may be completed exceptionally before resolving resulting promise faulty
     * @param cancelRemaining
     *   a flag that indicates (if true) whether or not all remaining incomplete {@link CompletionStage}-s should be cancelled
     *   once a resulting promise outcome is known. 
     * @param promises
     *   an array of {@link CompletionStage}-s to combine 
     * @return
     *   a combined promise 
     */    
    @SafeVarargs
    public static <T> Promise<List<T>> atLeast(int minResultsCount, int maxErrorsCount, boolean cancelRemaining, 
            CompletionStage<? extends T>... promises) {
        return atLeast(minResultsCount, maxErrorsCount, cancelRemaining, Arrays.asList(promises));
    }
    
    public static <T> Promise<List<T>> atLeast(int minResultsCount, int maxErrorsCount, boolean cancelRemaining, 
                                               List<CompletionStage<? extends T>> promises) {
        
        int size = null == promises ? 0 : promises.size();
        if (minResultsCount > size) {
            throw new IllegalArgumentException(
                    "The number of futures supplied is less than a number of futures to await");
        } else if (minResultsCount == 0) {
            return success(Collections.emptyList());
        } else if (size == 1) {
            return from(promises.get(0), Collections::singletonList, Function.<Throwable> identity());
        } else {
            return new AggregatingPromise<>(minResultsCount, maxErrorsCount, cancelRemaining, promises);
        }
    }
    
    /**
     * Creates a promise that is resolved successfully after delay specified
     * @param duration
     * the duration of timeout
     * @return
     * the new promise
     */
    public static Promise<Duration> delay(Duration duration) {
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
    public static Promise<Duration> delay(long delay, TimeUnit timeUnit) {
        return delay( toDuration(delay, timeUnit) );
    }

    /**
     * Creates a promise that is resolved erronously with {@link TimeoutException} after delay specified
     * @param duration
     * the duration of timeout
     * @return
     * the new promise
     */
    public static <T> Promise<T> failAfter(Duration duration) {
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
    public static <T> Promise<T> failAfter(long delay, TimeUnit timeUnit) {
        return failAfter( toDuration(delay, timeUnit) );
    }
    
    public static Promise<Void> poll(Runnable codeBlock, Executor executor, RetryPolicy retryPolicy) {
        Promise<Object> wrappedResult = pollOptional(
                () -> { codeBlock.run(); return Optional.of(new Object()); }, 
                executor, retryPolicy
            );
            return dependent(wrappedResult).thenApply(v -> null, true);  
    }
    
    public static <T> Promise<T> poll(Callable<? extends T> codeBlock, Executor executor, RetryPolicy retryPolicy) {
        Promise<ObjectRef<T>> wrappedResult = pollOptional(
            () -> Optional.of(new ObjectRef<T>( codeBlock.call() )), 
            executor, retryPolicy
        );
        return dependent(wrappedResult).thenApply(ObjectRef::dereference, true);
    }
    
    public static <T> Promise<T> pollOptional(Callable<Optional<? extends T>> codeBlock, Executor executor, RetryPolicy retryPolicy) {
        final CompletablePromise<T> promise = new CompletablePromise<>();
        final AtomicReference<Promise<?>> callPromiseRef = new AtomicReference<>();
        // Cleanup latest timeout on completion;
        promise.whenComplete(
            (r, e) -> 
                Optional
                .of(callPromiseRef)
                .map(AtomicReference::get)
                .ifPresent( p -> p.cancel(true) )
        );
        Consumer<Promise<?>> changeCallPromiseRef = p -> {
        	// If result promise is cancelled after callPromise was set need to stop;            	
        	callPromiseRef.set( p );	
            if (promise.isDone()) {
                p.cancel(true);
            }
        };
        
        RetryContext ctx = RetryContext.initial(retryPolicy);
        pollOnce(codeBlock, executor, ctx, promise, changeCallPromiseRef);
        return promise;
    }
    
    private static <T> void pollOnce(Callable<Optional<? extends T>> codeBlock, 
                                     Executor executor, RetryContext ctx, 
                                     CompletablePromise<T> resultPromise, 
                                     Consumer<Promise<?>> changeCallPromiseRef) {
        
        // Promise may be cancelled outside of polling
        if (resultPromise.isDone()) {
            return;
        }
        
        RetryPolicy.Outcome answer = ctx.shouldContinue();
        if (answer.shouldExecute()) {
            Runnable doCall = () -> {
                long startTime = System.currentTimeMillis();
                try {
                    Optional<? extends T> result;
                    ctx.enter();
                    try {
                        result = codeBlock.call();
                    } finally {
                        ctx.exit();
                    }
                    if (result.isPresent()) {
                        resultPromise.onSuccess(result.get());
                    } else {
                        long finishTime = System.currentTimeMillis();
                        RetryContext nextCtx = ctx.nextRetry(finishTime - startTime);
                        pollOnce(codeBlock, executor, nextCtx, resultPromise, changeCallPromiseRef);
                    }
                } catch (Exception ex) {
                    long finishTime = System.currentTimeMillis();
                    RetryContext nextCtx = ctx.nextRetry(finishTime - startTime, ex);
                    pollOnce(codeBlock, executor, nextCtx, resultPromise, changeCallPromiseRef);
                }
            };
            
            Supplier<Promise<?>> submittedCall = () -> {
                // Call should be done via CompletableTask to let it be interruptible               
            	Promise<?> p = CompletableTask.runAsync(doCall, executor);
            	if (answer.hasTimeout()) {
                    p.orTimeout( Duration.ofMillis(Math.max(0, answer.timeoutDelayMillis()) ) );
            	}
            	return p;
            };

            long backoffDelayMillis = answer.backoffDelayMillis();
            if (backoffDelayMillis > 0) {
                // Timeout itself
                Promise<?> backoff = delay( Duration.ofMillis(backoffDelayMillis) );
                // Invocation after timeout
                backoff.thenAccept(d -> changeCallPromiseRef.accept( submittedCall.get() ));
                // Canceling timeout will cancel the chain above
                changeCallPromiseRef.accept( backoff );
            } else {
                // Immediately send to executor
            	changeCallPromiseRef.accept( submittedCall.get() ); 
            }

        } else {
            resultPromise.onFailure(ctx.asFailure());
        }
    }

    private static <T> Promise<T> unwrap(CompletionStage<List<T>> original, boolean unwrapException) {
        return from(
            original,
            c -> c.stream().filter(el -> null != el).findFirst().get(),
            unwrapException ? 
                ex -> ex instanceof MultitargetException ? MultitargetException.class.cast(ex).getFirstException().get() : ex
                :
                Function.identity()
        );
    }

    private static <T> BiConsumer<T, ? super Throwable> handler(Consumer<? super T> onResult, Consumer<? super Throwable> onError) {
        return (r, e) -> {
            if (null != e) {
                onError.accept(e);
            } else {
                try {
                    onResult.accept(r);
                } catch (Exception ex) {
                    onError.accept(ex);
                }
            }
        };
    }

    private static <T, U> Consumer<? super T> acceptConverted(Consumer<? super U> target, Function<? super T, ? extends U> converter) {
        return t -> target.accept(converter.apply(t));
    }
    
    static Duration toDuration(long delay, TimeUnit timeUnit) {
        return Duration.of(delay, toChronoUnit(timeUnit));
    }
    
    static <T, U> BiConsumer<T, U> timeoutsCleanup(Promise<T> self, Promise<?> onTimeout, boolean cancelOnTimeout) {
    	return (r, e) -> {
        	// Result comes from timeout and cancel-on-timeout is set
        	// If both are done then cancel has no effect anyway
            if ((onTimeout.isDone() && !onTimeout.isCancelled()) && cancelOnTimeout) {
                self.cancel(true);
            }
            onTimeout.cancel(true);
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
    
    private static class ObjectRef<T> {
        private final T reference;
        
        ObjectRef(T reference) {
            this.reference = reference;
        }
        
        T dereference() {
            return reference;
        }
    }
}
