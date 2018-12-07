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

import static net.tascalate.concurrent.SharedFunctions.cancelPromise;
import static net.tascalate.concurrent.SharedFunctions.unwrapCompletionException;
import static net.tascalate.concurrent.LinkedCompletion.StageCompletion;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
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

    private Promises() {}
    
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
        return new CompletableFutureWrapper<>(
            CompletableFuture.completedFuture(value)
        );
    }
    
    /**
     * Method to create a faulty resolved {@link Promise} with an exception provided 
     * @param <T>
     *   a type of the value 
     * @param exception
     *   an exception to wrap
     * @return 
     *   a faulty resolved {@link Promise} with an exception provided
     */    
    public static <T> Promise<T> failure(Throwable exception) {
        CompletableFuture<T> delegate = new CompletableFuture<>();
        delegate.completeExceptionally(exception);
        return new CompletableFutureWrapper<>(delegate);
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
            return new CompletableFutureWrapper<>((CompletableFuture<T>)stage);
        }
        
        /*
        return transform(stage, Function.identity(), Function.identity());
         */
        return CompletionStageWrapper.from(stage);
    }
    
    public static <T> CompletionStage<T> withDefaultExecutor(CompletionStage<T> stage, Executor executor) {
        return new ExecutorBoundCompletionStage<>(stage, executor);
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
    
    
    public static <T> Promise<List<T>> all(List<? extends CompletionStage<? extends T>> promises) {
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

    public static <T> Promise<List<T>> all(boolean cancelRemaining, List<? extends CompletionStage<? extends T>> promises) {
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

    public static <T> Promise<T> any(List<? extends CompletionStage<? extends T>> promises) {
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
    
    public static <T> Promise<T> any(boolean cancelRemaining, List<? extends CompletionStage<? extends T>> promises) {
        int size = null == promises ? 0 : promises.size();
        switch (size) {
            case 0:
                return insufficientNumberOfArguments(1, 0);
            case 1:
                @SuppressWarnings("unchecked")
                CompletionStage<T> singleResult = (CompletionStage<T>) promises.get(0);
                return transform(singleResult, Function.identity(), Promises::wrapMultitargetException);
            default:
                return transform(
                    atLeast(1, size - 1, cancelRemaining, promises), 
                    Promises::extractFirstNonNull, Function.identity() /* DO NOT unwrap multitarget exception */
                );
        }
    }
    
    /**
     * <p>Returns a promise that is resolved successfully when any {@link CompletionStage} passed as parameters is completed normally (race is possible); 
     * if any promise completed exceptionally before first result is available, then resulting promise is resolved faulty as well 
     * (unlike non-Strict variant, where exceptions are ignored if result is available at all).
     * <p>The resolved result of this promise contains a value of the first resolved result of the {@link CompletionStage}-s passed as an 
     * argument.
     * <p>When resulting promise is resolved either successfully or faulty, all remaining incomplete {@link CompletionStage}-s are cancelled. 
     * <p>Unlike other methods to combine promises (any, all, atLeast, atLeastStrict), the {@link Promise} returns from this method reports 
     * exact exception. All other methods wrap it to {@link MultitargetException}. 
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
    
    public static <T> Promise<T> anyStrict(List<? extends CompletionStage<? extends T>> promises) {
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
     * <p>Unlike other methods to combine promises (any, all, atLeast, atLeastStrict), the {@link Promise} returns from this method reports 
     * exact exception. All other methods wrap it to {@link MultitargetException}. 
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
    
    public static <T> Promise<T> anyStrict(boolean cancelRemaining, List<? extends CompletionStage<? extends T>> promises) {
        int size = null == promises ? 0 : promises.size();
        switch (size) {
            case 0:
                return insufficientNumberOfArguments(1, 0);
            case 1:
                @SuppressWarnings("unchecked")
                CompletionStage<T> singleResult = (CompletionStage<T>) promises.get(0);
                return from(singleResult);
            default:
                return transform(
                    atLeast(1, 0, cancelRemaining, promises), 
                    Promises::extractFirstNonNull, Promises::unwrapMultitargetException
                );
        }
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
    
    public static <T> Promise<List<T>> atLeast(int minResultsCount, List<? extends CompletionStage<? extends T>> promises) {
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
    
    public static <T> Promise<List<T>> atLeast(int minResultsCount, boolean cancelRemaining, List<? extends CompletionStage<? extends T>> promises) {
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
    
    public static <T> Promise<List<T>> atLeastStrict(int minResultsCount, List<? extends CompletionStage<? extends T>> promises) {
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
    
    public static <T> Promise<List<T>> atLeastStrict(int minResultsCount, boolean cancelRemaining, List<? extends CompletionStage<? extends T>> promises) {
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
                                               List<? extends CompletionStage<? extends T>> promises) {
        
        int size = null == promises ? 0 : promises.size();
        if (minResultsCount > size) {
            Promise<List<T>> result = insufficientNumberOfArguments(minResultsCount, size);
            if (cancelRemaining && size > 0) {
                promises.stream().forEach( p -> cancelPromise(p, true) );
            }
            return result;
        } else if (minResultsCount == 0) {
            return success(Collections.emptyList());
        } else if (size == 1) {
            CompletionStage<? extends T> stage = promises.get(0);
            return transform(stage, Collections::singletonList, Promises::wrapMultitargetException);
        } else {
            return new AggregatingPromise<>(minResultsCount, maxErrorsCount, cancelRemaining, promises);
        }
    }
    
    public static Promise<Void> retry(Runnable codeBlock, Executor executor, RetryPolicy retryPolicy) {
        return retry(ctx -> { codeBlock.run(); }, executor, retryPolicy);
    }
    
    public static Promise<Void> retry(RetryRunnable codeBlock, Executor executor, RetryPolicy retryPolicy) {
        Promise<Object> wrappedResult = poll(
            ctx -> { codeBlock.run(ctx); return IGNORE; }, 
            executor, retryPolicy
        );
        return wrappedResult.dependent().thenApply(v -> (Void)null, true).raw();  
    }
    
    public static <T> Promise<T> retry(Callable<? extends T> codeBlock, Executor executor, RetryPolicy retryPolicy) {
        return retry(toRetryCallable(codeBlock), executor, retryPolicy);
    }
    
    public static <T> Promise<T> retry(RetryCallable<? extends T> codeBlock, Executor executor, RetryPolicy retryPolicy) {
        Promise<ObjectRef<T>> wrappedResult = poll(
            ctx -> new ObjectRef<>( codeBlock.call(ctx) ), 
            executor, retryPolicy
        );
        return wrappedResult.dependent().thenApply(ObjectRef::dereference, true).raw();
    }
    
    public static <T> Promise<T> retryFuture(Callable<? extends CompletionStage<? extends T>> invoker, RetryPolicy retryPolicy) {
        return retryFuture(toRetryCallable(invoker), retryPolicy);
    }
    
    public static <T> Promise<T> retryFuture(RetryCallable<? extends CompletionStage<? extends T>> invoker, RetryPolicy retryPolicy) {
        Promise<ObjectRef<T>> wrappedResult = pollFuture(
            ctx -> Promises.from(invoker.call(ctx)).dependent().thenApply(r -> new ObjectRef<T>(r), true).raw(), 
            retryPolicy
        );
        return wrappedResult.dependent().thenApply(ObjectRef::dereference, true).raw();
    }
    
    public static <T> Promise<T> poll(Callable<T> codeBlock, Executor executor, RetryPolicy retryPolicy) {
        return poll(toRetryCallable(codeBlock), executor, retryPolicy);
    }

    public static <T> Promise<T> poll(RetryCallable<T> codeBlock, Executor executor, RetryPolicy retryPolicy) {
        return invokePoller(
            retryPolicy, 
            (ctx, result, changeCallPromiseRef) -> pollValueOnce(codeBlock, executor, ctx, result, changeCallPromiseRef)
        );
    }
    
    public static <T> Promise<T> pollFuture(Callable<? extends CompletionStage<? extends T>> invoker, RetryPolicy retryPolicy) {
        return pollFuture(toRetryCallable(invoker), retryPolicy);
    }
    
    public static <T> Promise<T> pollFuture(RetryCallable<? extends CompletionStage<? extends T>> invoker, RetryPolicy retryPolicy) {
        return invokePoller(
            retryPolicy, 
            (ctx, result, changeCallPromiseRef) -> pollFutureOnce(invoker, ctx, result, changeCallPromiseRef)
        );
    }
    
    private static <T> Promise<T> invokePoller(RetryPolicy retryPolicy, F3<RetryContext, CompletableFuture<T>, Consumer<Promise<?>>> initiator) {
        final CompletableFuture<T> result = new CompletableFuture<>();
        final AtomicReference<Promise<?>> callPromiseRef = new AtomicReference<>();
        // Cleanup latest timeout on completion;
        result.whenComplete(
            (r, e) -> 
                Optional
                .of(callPromiseRef)
                .map(AtomicReference::get)
                .ifPresent( p -> p.cancel(true) )
        );
        Consumer<Promise<?>> changeCallPromiseRef = p -> {
            // If result promise is cancelled after callPromise was set need to stop;               
            callPromiseRef.set( p );    
            if (result.isDone()) {
                p.cancel(true);
            }
        };
        
        RetryContext ctx = RetryContext.initial(retryPolicy);     
        initiator.apply(ctx, result, changeCallPromiseRef);
        return new CompletableFutureWrapper<>(result);
    }
    
    private static <T> void pollValueOnce(RetryCallable<? extends T> codeBlock, 
                                          Executor executor, RetryContext ctx, 
                                          CompletableFuture<T> resultPromise, 
                                          Consumer<Promise<?>> changeCallPromiseRef) {
        
        // Promise may be cancelled outside of polling
        if (resultPromise.isDone()) {
            return;
        }
        
        RetryPolicy.Outcome answer = ctx.shouldContinue();
        if (answer.shouldExecute()) {
            Supplier<Promise<?>> submittedCall = () -> {
                long startTime = System.nanoTime();                
                Runnable doCall = () -> {
                    try {
                        T result = codeBlock.call(ctx);
                        if (result != null) {
                            resultPromise.complete(result);
                        } else {
                            long finishTime = System.nanoTime();
                            RetryContext nextCtx = ctx.nextRetry(Duration.ofNanos(finishTime - startTime));
                            pollValueOnce(codeBlock, executor, nextCtx, resultPromise, changeCallPromiseRef);
                        }
                    } catch (Exception ex) {
                        long finishTime = System.nanoTime();
                        RetryContext nextCtx = ctx.nextRetry(Duration.ofNanos(finishTime - startTime), ex);
                        pollValueOnce(codeBlock, executor, nextCtx, resultPromise, changeCallPromiseRef);
                    }
                };
                
                // Call should be done via CompletableTask to let it be interruptible               
            	Promise<?> p = CompletableTask.runAsync(doCall, executor);
            	return applyExecutionTimeout(p, answer);
            };
            callOrApplyRetryBackoff(submittedCall, answer, changeCallPromiseRef);
        } else {
            resultPromise.completeExceptionally(ctx.asFailure());
        }
    }
    
    private static <T> void pollFutureOnce(RetryCallable<? extends CompletionStage<? extends T>> invoker, 
                                           RetryContext ctx, 
                                           CompletableFuture<T> resultPromise,
                                           Consumer<Promise<?>> changeCallPromiseRef) {
        // Promise may be cancelled outside of polling
        if (resultPromise.isDone()) {
            return;
        }
        
        RetryPolicy.Outcome answer = ctx.shouldContinue();
        if (answer.shouldExecute()) {
            Supplier<Promise<? extends T>> safeCall = () -> {
                try {
                    return Promises.from(invoker.call(ctx));
                } catch (Exception ex) {
                    return Promises.failure(ex);
                }
            };
            
            Supplier<Promise<?>> submittedCall = () -> {
                long startTime = System.nanoTime();

                Promise<? extends T> p = safeCall.get();
                p.whenComplete((result, ex) -> {
                    if (null != ex) {
                        long finishTime = System.nanoTime();
                        RetryContext nextCtx = ctx.nextRetry(Duration.ofNanos(finishTime - startTime), ex);
                        pollFutureOnce(invoker, nextCtx, resultPromise, changeCallPromiseRef);
                    } else {
                        if (result != null) {
                            resultPromise.complete(result);
                        } else {
                            long finishTime = System.nanoTime();
                            RetryContext nextCtx = ctx.nextRetry(Duration.ofNanos(finishTime - startTime));
                            pollFutureOnce(invoker, nextCtx, resultPromise, changeCallPromiseRef);
                        }                        
                    }
                });
                return applyExecutionTimeout(p, answer);
            };
            callOrApplyRetryBackoff(submittedCall, answer, changeCallPromiseRef);
        } else {
            resultPromise.completeExceptionally(ctx.asFailure());
        }        
    }
    
    private static <T> Promise<T> applyExecutionTimeout(Promise<T> singleInvocationPromise, RetryPolicy.Outcome answer) {
        Duration timeout = answer.timeout();
        if (DelayPolicy.isValid(timeout)) {
            singleInvocationPromise.orTimeout( timeout );
        }
        return singleInvocationPromise;        
    }
    
    private static void callOrApplyRetryBackoff(Supplier<Promise<?>> submittedCall, RetryPolicy.Outcome answer, Consumer<Promise<?>> changeCallPromiseRef) {
        Duration backoffDelay = answer.backoffDelay();
        if (DelayPolicy.isValid(backoffDelay)) {
            // Timeout itself
            Promise<?> backoff = Timeouts.delay( backoffDelay );
            // Canceling timeout will cancel the chain below (thenAccept)
            changeCallPromiseRef.accept( backoff );
            // Invocation after timeout, change cancellation target
            backoff.thenAccept(d -> changeCallPromiseRef.accept( submittedCall.get() ));
        } else {
            // Immediately send to executor
            changeCallPromiseRef.accept( submittedCall.get() ); 
        }        
    }
    
    private static <T, U> Promise<T> transform(CompletionStage<U> original, 
                                               Function<? super U, ? extends T> resultMapper, 
                                               Function<? super Throwable, ? extends Throwable> errorMapper) {
        StageCompletion<T> result = new StageCompletion<T>().dependsOn(original);
        original.whenComplete((r, e) -> {
           if (null == e) {
               result.complete( resultMapper.apply(r) );
           } else {
               result.completeExceptionally( errorMapper.apply(e) );
           }
        });
        return result.toPromise();
    }
    
    private static <T> T extractFirstNonNull(Collection<? extends T> collection) {
        return collection.stream().filter(Objects::nonNull).findFirst().get();
    }
    
    private static <E extends Throwable> Throwable unwrapMultitargetException(E exception) {
        Throwable targetException = unwrapCompletionException(exception);
        if (targetException instanceof MultitargetException) {
            return ((MultitargetException)targetException).getFirstException().get();
        } else {
            return targetException;
        }
    }
    
    private static <E extends Throwable> MultitargetException wrapMultitargetException(E exception) {
        if (exception instanceof MultitargetException) {
            return (MultitargetException)exception;
        } else {
            return MultitargetException.of(exception);
        }
    }
    
    private static <T> Promise<T> insufficientNumberOfArguments(int minResultCount, int size) {
        String message = String.format(
            "The number of futures supplied (%d) is less than a number of futures to await (%d)", size, minResultCount
        );
        //return failure(new NoSuchElementException(message));
        throw new IllegalArgumentException(message);        
    }
    
    private static <V> RetryCallable<V> toRetryCallable(Callable<? extends V> callable) {
        return ctx -> callable.call();
    }
    
    private static class ObjectRef<T> {
        private final T reference;
        
        ObjectRef(T reference) {
            this.reference = reference;
        }
        
        T dereference() {
            return reference;
        }
    }

    private static final Object IGNORE = new Object();
    
    @FunctionalInterface
    static interface F3<T, U, V> {
        void apply(T p1, U p2, V p3);
    }

}
