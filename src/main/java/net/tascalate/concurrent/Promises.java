/**
 * Copyright 2015-2019 Valery Silaev (http://vsilaev.com)
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
import static net.tascalate.concurrent.SharedFunctions.selectFirst;
import static net.tascalate.concurrent.SharedFunctions.unwrapCompletionException;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterators;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;


/**
 * Utility class to create a resolved (either successfully or faulty) {@link Promise}-s; 
 * to wrap an arbitrary {@link CompletionStage} interface to the {@link Promise} API;
 * to combine several {@link CompletionStage}-s into aggregating promise.
 *    
 * @author vsilaev
 *
 */
public final class Promises {

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
    
    public static <T> Promise<Promise<T>> elevated(CompletionStage<? extends T> promise) {
        Promise<Promise<T>> result = from(promise).dependent()
                                                  .thenApply(Promises::success, true);
        return result.unwrap();
    }
    
    public static <T> Promise<T> narrowed(CompletionStage<? extends CompletionStage<T>> promise) {
        return from(promise).dependent()
                            .thenCompose(Promises::from, true)
                            .unwrap();
    }
    
    public static <T> Promise<Optional<T>> successOptional(CompletionStage<? extends T> promise) {
        Promise<Optional<T>> result =
            from(promise).dependent()
                         .handle((r, e) -> Optional.ofNullable(null == e ? r : null), true);
        return result.unwrap();
    }
    
    public static <T> Promise<Stream<T>> successStream(CompletionStage<? extends T> promise) {
        Promise<Stream<T>> result =
            from(promise).dependent()
                         .handle((r, e) -> null == e ? Stream.of(r) : Stream.empty(), true);
        return result.unwrap();
    }
    
    public static <T> Iterator<T> iterateCompletions(Stream<? extends CompletionStage<? extends T>> pendingPromises, 
                                                     int chunkSize) {
        return iterateCompletions(pendingPromises.iterator(), chunkSize);
    }

    public static <T> Iterator<T> iterateCompletions(Iterable<? extends CompletionStage<? extends T>> pendingPromises, 
                                                     int chunkSize) {
        return iterateCompletions(pendingPromises.iterator(), chunkSize);
    }
    
    private static <T> Iterator<T> iterateCompletions(Iterator<? extends CompletionStage<? extends T>> pendingPromises, 
                                                      int chunkSize) {
        return new CompletionIterator<>(pendingPromises, chunkSize);
    }
    
    public static <T> Stream<T> streamCompletions(Stream<? extends CompletionStage<? extends T>> pendingPromises, 
                                                  int chunkSize) {
        return streamCompletions(pendingPromises, chunkSize, false);
    }
    
    public static <T> Stream<T> streamCompletions(Stream<? extends CompletionStage<? extends T>> pendingPromises, 
                                                  int chunkSize, boolean cancelRemainig) {
        return streamCompletions(pendingPromises.iterator(), chunkSize, cancelRemainig);
    }

    public static <T> Stream<T> streamCompletions(Iterable<? extends CompletionStage<? extends T>> pendingPromises, 
                                                  int chunkSize) {
        return streamCompletions(pendingPromises, chunkSize, false);
    }
    
    public static <T> Stream<T> streamCompletions(Iterable<? extends CompletionStage<? extends T>> pendingPromises, 
                                                  int chunkSize, boolean cancelRemainig) {
        return streamCompletions(pendingPromises.iterator(), chunkSize, cancelRemainig);
    }
    
    private static <T> Stream<T> streamCompletions(Iterator<? extends CompletionStage<? extends T>> pendingPromises, 
                                                   int chunkSize, boolean cancelRemaining) {
        return toCompletionStream(new CompletionIterator<>(pendingPromises, chunkSize, cancelRemaining));
    }
    
    private static <T> Stream<T> toCompletionStream(CompletionIterator<T> iterator) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false)
                            .onClose(iterator::close); 
    }
    
    /**
     * <p>Returns a promise that is resolved successfully when all {@link CompletionStage}-s passed as parameters
     * are completed normally; if any promise completed exceptionally, then resulting promise is resolved faulty
     * as well.
     * <p>The resolved result of this promise contains a list of the resolved results of the 
     * {@link CompletionStage}-s passed as an argument at corresponding positions.
     * <p>When resulting promise is resolved faulty, all remaining incomplete {@link CompletionStage}-s are 
     * cancelled.  
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
     * <p>Returns a promise that is resolved successfully when all {@link CompletionStage}-s passed as parameters
     * are completed normally; if any promise completed exceptionally, then resulting promise is resolved faulty
     * as well.
     * <p>The resolved result of this promise contains a list of the resolved results of the 
     * {@link CompletionStage}-s passed as an argument at corresponding positions.
     * <p>When resulting promise is resolved faulty <em>and</em> <code>cancelRemaining</code> parameter is
     * <code>true</code>, all remaining incomplete {@link CompletionStage}-s are cancelled.  
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
     * <p>Returns a promise that is resolved successfully when any {@link CompletionStage} passed as parameters
     * is completed normally (race is possible); if all promises completed exceptionally, then resulting promise
     * is resolved faulty as well.
     * <p>The resolved result of this promise contains a value of the first resolved result of the 
     * {@link CompletionStage}-s passed as an argument.
     * <p>When resulting promise is resolved successfully, all remaining incomplete {@link CompletionStage}-s
     * are cancelled.
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
     * <p>Returns a promise that is resolved successfully when any {@link CompletionStage} passed as parameters
     * is completed normally (race is possible); if all promises completed exceptionally, then resulting promise 
     * is resolved faulty as well.
     * <p>The resolved result of this promise contains a value of the first resolved result of the 
     * {@link CompletionStage}-s passed as an argument.
     * <p>When resulting promise is resolved successfully <em>and</em> <code>cancelRemaining</code> parameter is
     * <code>true</code>, all remaining incomplete {@link CompletionStage}-s are cancelled.
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
     * <p>Returns a promise that is resolved successfully when any {@link CompletionStage} passed as parameters 
     * is completed normally (race is possible); if any promise completed exceptionally before first result is 
     * available, then resulting promise is resolved faulty as well (unlike non-Strict variant, where exceptions 
     * are ignored if result is available at all).
     * <p>The resolved result of this promise contains a value of the first resolved result of the 
     * {@link CompletionStage}-s passed as an argument.
     * <p>When resulting promise is resolved either successfully or faulty, all remaining incomplete 
     * {@link CompletionStage}-s are cancelled. 
     * <p>Unlike other methods to combine promises (any, all, atLeast, atLeastStrict), the {@link Promise} returns
     * from this method reports exact exception. All other methods wrap it to {@link MultitargetException}. 
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
     * <p>Returns a promise that is resolved successfully when any {@link CompletionStage} passed as parameters
     *  is completed normally (race is possible); if any promise completed exceptionally before first result is
     *  available, then resulting promise is resolved faulty as well (unlike non-Strict variant, where exceptions
     *  are ignored if result is available at all).
     * <p>The resolved result of this promise contains a value of the first resolved result of the 
     * {@link CompletionStage}-s passed as an argument.
     * <p>When resulting promise is resolved either successfully or faulty <em>and</em> <code>cancelRemaining</code>
     * parameter is <code>true</code>, all remaining incomplete {@link CompletionStage}-s are cancelled. 
     * <p>Unlike other methods to combine promises (any, all, atLeast, atLeastStrict), the {@link Promise} returns 
     * from this method reports exact exception. All other methods wrap it to {@link MultitargetException}. 
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
     * <p>Returns a promise that is resolved successfully when at least <code>minResultCount</code> of 
     * {@link CompletionStage}-s passed as parameters are completed normally (race is possible); if less than 
     * <code>minResultCount</code> of promises completed normally, then resulting promise is resolved faulty.
     * <p>The resolved result of this promise contains a list of the resolved results of the 
     * {@link CompletionStage}-s passed as an argument at corresponding positions. Non-completed or completed 
     * exceptionally promises have <code>null</code> values.
     * <p>When resulting promise is resolved successfully, all remaining incomplete {@link CompletionStage}-s 
     * are cancelled. 
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
     * <p>Returns a promise that is resolved successfully when at least <code>minResultCount</code> of 
     * {@link CompletionStage}-s passed as parameters are completed normally (race is possible); if less than 
     * <code>minResultCount</code> of promises completed normally, then resulting promise is resolved faulty.
     * <p>The resolved result of this promise contains a list of the resolved results of the 
     * {@link CompletionStage}-s passed as an argument at corresponding positions. Non-completed or completed 
     * exceptionally promises have <code>null</code> values.
     * <p>When resulting promise is resolved successfully <em>and</em> <code>cancelRemaining</code> parameter 
     * is <code>true</code>, all remaining incomplete {@link CompletionStage}-s are cancelled.  
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
    public static <T> Promise<List<T>> atLeast(int minResultsCount, boolean cancelRemaining, 
                                               CompletionStage<? extends T>... promises) {
        
        return atLeast(minResultsCount, cancelRemaining, Arrays.asList(promises));
    }
    
    public static <T> Promise<List<T>> atLeast(int minResultsCount, boolean cancelRemaining,
            
                                               List<? extends CompletionStage<? extends T>> promises) {
        return atLeast(minResultsCount, (promises == null ? 0 : promises.size()) - minResultsCount, cancelRemaining, promises);
    }
    
    /**
     * <p>Generalization of the {@link Promises#anyStrict(CompletionStage...)} method.</p>
     * <p>Returns a promise that is resolved successfully when at least <code>minResultCount</code> of 
     * {@link CompletionStage}-s passed as parameters are completed normally (race is possible); if less than 
     * <code>minResultCount</code> of promises completed normally, then resulting promise is resolved faulty. 
     * If any promise completed exceptionally <em>before</em> <code>minResultCount</code> of results are 
     * available, then resulting promise is resolved faulty as well. 
     * <p>The resolved result of this promise contains a list of the resolved results of the 
     * {@link CompletionStage}-s passed as an argument at corresponding positions. Non-completed promises 
     * have <code>null</code> values.
     * <p>When resulting promise is resolved either successfully or faulty, all remaining incomplete 
     * {@link CompletionStage}-s are cancelled.
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
    
    public static <T> Promise<List<T>> atLeastStrict(int minResultsCount, 
                                                     List<? extends CompletionStage<? extends T>> promises) {
        return atLeastStrict(minResultsCount, true, promises);
    }

    /**
     * <p>Generalization of the {@link Promises#anyStrict(CompletionStage...)} method.</p>
     * <p>Returns a promise that is resolved successfully when at least <code>minResultCount</code> of 
     * {@link CompletionStage}-s passed as parameters are completed normally (race is possible); if less than 
     * <code>minResultCount</code> of promises completed normally, then resulting promise is resolved faulty. 
     * If any promise completed exceptionally <em>before</em> <code>minResultCount</code> of results are available, 
     * then resulting promise is resolved faulty as well. 
     * <p>The resolved result of this promise contains a list of the resolved results of the 
     * {@link CompletionStage}-s passed as an argument at corresponding positions. Non-completed promises have 
     * <code>null</code> values.
     * <p>When resulting promise is resolved either successfully or faulty <em>and</em> <code>cancelRemaining</code> 
     * parameter is <code>true</code>, all remaining incomplete {@link CompletionStage}-s are cancelled.
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
    public static <T> Promise<List<T>> atLeastStrict(int minResultsCount, boolean cancelRemaining, 
                                                     CompletionStage<? extends T>... promises) {
        return atLeast(minResultsCount, cancelRemaining, Arrays.asList(promises));
    }    
    
    public static <T> Promise<List<T>> atLeastStrict(int minResultsCount, boolean cancelRemaining, 
                                                     List<? extends CompletionStage<? extends T>> promises) {
        return atLeast(minResultsCount, 0, cancelRemaining, promises);
    }
    
    /**
     * <p>General method to combine several {@link CompletionStage}-s passed as arguments into single promise.</p>
     * <p>The resulting promise is resolved successfully when at least <code>minResultCount</code> of 
     * {@link CompletionStage}-s passed as parameters are completed normally (race is possible).
     * <p>If less than <code>minResultCount</code> of promises completed normally, then resulting promise is 
     * resolved faulty. 
     * <p>If <code>maxErrorsCount</code> of promises completed exceptionally <em>before</em> <code>minResultCount</code> 
     * of results are available, then resulting promise is resolved faulty as well. 
     * <p>The resolved result of this promise contains a list of the resolved results of the {@link CompletionStage}-s 
     * passed as an argument at corresponding positions. Non-completed promises and promises completed exceptionally 
     * have <code>null</code> values.
     * <p>When resulting promise is resolved either successfully or faulty, all remaining incomplete 
     * {@link CompletionStage}-s are cancelled <em>if</em> <code>cancelRemaining</code> parameter is <code>true</code>. 
     * 
     * @param <T>
     *   a common supertype of the resulting values 
     * @param minResultsCount
     *   a minimum number of promises that should be completed normally to resolve resulting promise successfully
     * @param maxErrorsCount
     *   a maximum number of promises that may be completed exceptionally before resolving resulting promise faulty
     * @param cancelRemaining
     *   a flag that indicates (if true) whether or not all remaining incomplete {@link CompletionStage}-s should 
     *   be cancelled once a resulting promise outcome is known. 
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
    
    public static Promise<Void> retry(Runnable codeBlock, Executor executor, 
                                      RetryPolicy<? super Void> retryPolicy) {
        
        return retry(ctx -> { codeBlock.run(); }, executor, retryPolicy);
    }
    
    public static Promise<Void> retry(RetryRunnable codeBlock, Executor executor, 
                                      RetryPolicy<? super Void> retryPolicy) {
        
        return retry(ctx -> { codeBlock.run(ctx); return null; }, executor, retryPolicy.acceptNullResult());
    }

    public static <T> Promise<T> retry(Callable<T> codeBlock, Executor executor, 
                                       RetryPolicy<? super T> retryPolicy) {
        
        return retry(toRetryCallable(codeBlock), executor, retryPolicy);
    }

    public static <T extends C, C> Promise<T> retry(RetryCallable<T, C> codeBlock, Executor executor, 
                                                    RetryPolicy<? super C> retryPolicy) {
        
        return startRetry(retryPolicy, new RetryInitiator<T, C>() {
            @Override
            public void run(RetryContext<C> ctx, CompletableFuture<T> result, Consumer<Promise<?>> cancellation) {
                tryValueOnce(codeBlock, executor, ctx, result, cancellation);
            }
        }).defaultAsyncOn(executor);        
    }
    
    public static <T> Promise<T> retryOptional(Callable<Optional<T>> codeBlock, Executor executor, 
                                               RetryPolicy<? super T> retryPolicy) {
        
        return retryOptional(toRetryCallable(codeBlock), executor, retryPolicy);
    }
    
    public static <T extends C, C> Promise<T> retryOptional(RetryCallable<Optional<T>, C> codeBlock, Executor executor, 
                                                            RetryPolicy<? super C> retryPolicy) {
        
        // Need explicit type on lambda param
        return retry((RetryContext<C> ctx) -> codeBlock.call(ctx).orElse(null), executor, retryPolicy);
    }
    
    public static <T> Promise<T> retryFuture(Callable<? extends CompletionStage<T>> invoker, 
                                             RetryPolicy<? super T> retryPolicy) {
        
        return retryFuture(toRetryCallable(invoker), retryPolicy);
    }
    
    public static <T extends C, C> Promise<T> retryFuture(RetryCallable<? extends CompletionStage<T>, C> futureFactory, 
                                                          RetryPolicy<? super C> retryPolicy) {
        
        return startRetry(retryPolicy, new RetryInitiator<T, C>() {
            @Override
            public void run(RetryContext<C> ctx, CompletableFuture<T> result, Consumer<Promise<?>> cancellation) {
                tryFutureOnce(futureFactory, ctx, result, cancellation, null);
            }
        });
    }
    
    private static <T extends C, C> Promise<T> startRetry(RetryPolicy<? super C> retryPolicy, RetryInitiator<T, C> initiator) {
        
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
        Consumer<Promise<?>> cancellation = p -> {
            // If result promise is cancelled after callPromise was set need to stop;               
            callPromiseRef.set( p );    
            if (result.isDone()) {
                p.cancel(true);
            }
        };
        
        RetryContext<C> ctx = RetryContext.initial(retryPolicy);     
        initiator.run(ctx, result, cancellation);
        return new CompletableFutureWrapper<>(result);
    }
    
    private static <T extends C, C> void tryValueOnce(RetryCallable<T, C> codeBlock, 
                                                      Executor executor, 
                                                      RetryContext<C> ctx, 
                                                      CompletableFuture<T> result, 
                                                      Consumer<Promise<?>> cancellation) {
        
        // Promise may be cancelled outside of polling
        if (result.isDone()) {
            return;
        }
        
        RetryPolicy.Verdict verdict = ctx.shouldContinue();
        if (verdict.shouldExecute()) {
            Supplier<Promise<?>> callSupplier = () -> {
                long startTime = System.nanoTime();                
                Runnable call = () -> {
                    try {
                        T value = codeBlock.call(ctx);
                        if (ctx.isValidResult(value)) {
                            result.complete(value);
                        } else {
                            long finishTime = System.nanoTime();
                            RetryContext<C> nextCtx = ctx.nextRetry(duration(startTime, finishTime), value);
                            tryValueOnce(codeBlock, executor, nextCtx, result, cancellation);
                        }
                    } catch (Exception ex) {
                        long finishTime = System.nanoTime();
                        RetryContext<C> nextCtx = ctx.nextRetry(duration(startTime, finishTime), ex);
                        tryValueOnce(codeBlock, executor, nextCtx, result, cancellation);
                    }
                };
                
                // Call should be done via CompletableTask to let it be interruptible               
            	Promise<?> p = CompletableTask.runAsync(call, executor);
            	return applyExecutionTimeout(p, verdict);
            };
            Duration backoffDelay = verdict.backoffDelay();
            if (DelayPolicy.isValid(backoffDelay)) {
                // Invocation after timeout, change cancellation target
                Promise<?> later = Timeouts
                    .delay(backoffDelay)
                    .dependent()
                    .thenRun(() -> cancellation.accept( callSupplier.get() ), true);
                cancellation.accept( later );
            } else {
                // Immediately send to executor
                cancellation.accept( callSupplier.get() ); 
            } 
        } else {
            result.completeExceptionally(ctx.asFailure());
        }
    }
    
    private static <T extends C, C> void tryFutureOnce(RetryCallable<? extends CompletionStage<T>, C> futureFactory, 
                                                       RetryContext<C> ctx, 
                                                       CompletableFuture<T> result,
                                                       Consumer<Promise<?>> cancellation,
                                                       Promise<?> prev) {
        // Promise may be cancelled outside of polling
        if (result.isDone()) {
            return;
        }
        
        RetryPolicy.Verdict verdict = ctx.shouldContinue();
        if (verdict.shouldExecute()) {
            Supplier<Promise<?>> callSupplier = () -> {
                long startTime = System.nanoTime();
                
                Promise<? extends T> target;
                try {
                    target = Promises.from(futureFactory.call(ctx));
                } catch (Exception ex) {
                    target = Promises.failure(ex);
                }

                AtomicBoolean isRecursive = new AtomicBoolean(true);
                Thread invokerThread = Thread.currentThread();

                Promise<? extends T> p = target;
                p.whenComplete((value, ex) -> {
                    if (null == ex && ctx.isValidResult(value)) {
                        result.complete(value);
                    } else {
                        long finishTime = System.nanoTime();
                        RetryContext<C> nextCtx = ctx.nextRetry(
                            duration(startTime, finishTime), unwrapCompletionException(ex)
                        );
                        boolean callLater = isRecursive.get() && Thread.currentThread() == invokerThread;
                        if (callLater) {
                            // Call after minimal possible delay
                            callLater(
                                p, Duration.ofNanos(1), cancellation, 
                                () -> tryFutureOnce(futureFactory, nextCtx, result, cancellation, p)
                            );
                        } else {
                            tryFutureOnce(futureFactory, nextCtx, result, cancellation, p);
                        }
                    }
                });
                isRecursive.set(false);
                return applyExecutionTimeout(p, verdict);
            };
            Duration backoffDelay = verdict.backoffDelay();
            if (null != prev && DelayPolicy.isValid(backoffDelay)) {
                callLater(prev, backoffDelay, cancellation, () -> cancellation.accept( callSupplier.get() ));
            } else {
                // Immediately send to executor
                cancellation.accept( callSupplier.get() ); 
            }  
        } else {
            result.completeExceptionally(ctx.asFailure());
        }        
    }
    
    private static <T> void callLater(Promise<T> completedPromise, Duration delay, Consumer<Promise<?>> cancellation, Runnable code) {
        Promise<?> later = completedPromise
            .dependent()
            .thenCombine(Timeouts.delay(delay), selectFirst(), PromiseOrigin.PARAM_ONLY)
            .whenCompleteAsync((r, e) -> code.run(), true);
        cancellation.accept(later);
    }
    
    private static <T> Promise<T> applyExecutionTimeout(Promise<T> singleInvocationPromise, RetryPolicy.Verdict verdict) {
        Duration timeout = verdict.timeout();
        if (DelayPolicy.isValid(timeout)) {
            singleInvocationPromise.dependent().orTimeout( timeout, true, true ).unwrap();
        }
        return singleInvocationPromise;        
    }
    
    private static <T, U> Promise<T> transform(CompletionStage<U> original, 
                                               Function<? super U, ? extends T> resultMapper, 
                                               Function<? super Throwable, ? extends Throwable> errorMapper) {
        CompletablePromise<T> result = new CompletablePromise<>();
        original.whenComplete((r, e) -> {
           if (null == e) {
               result.onSuccess( resultMapper.apply(r) );
           } else {
               result.onFailure( errorMapper.apply(e) );
           }
        });
        return result.onCancel(() -> cancelPromise(original, true));
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
        Exception ex = new NoSuchElementException(message);
        //TODO: exceptional completion vs runtime exception on combined promise construction?
        ex.fillInStackTrace();
        return failure(ex);
        /*
        throw new IllegalArgumentException(message);        
        */
    }
    
    private static <V, T> RetryCallable<V, T> toRetryCallable(Callable<? extends V> callable) {
        return ctx -> callable.call();
    }
    
    private static Duration duration(long startTime, long finishTime) {
        return Duration.ofNanos(finishTime - startTime);
    }
    
    private static abstract class RetryInitiator<T extends C, C> {
        abstract void run(RetryContext<C> ctx, CompletableFuture<T> result, Consumer<Promise<?>> cancellation);
    }

}
