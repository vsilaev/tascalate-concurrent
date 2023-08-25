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

import static net.tascalate.concurrent.SharedFunctions.cancelPromise;
import static net.tascalate.concurrent.SharedFunctions.wrapCompletionException;
import static net.tascalate.concurrent.SharedFunctions.iif;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;


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

    
    public static <T> Promise<T> maybe(Optional<T> maybeValue) {
        return maybeValue.map(Promises::success)
                         .orElseGet(() -> Promises.failure(new NoSuchElementException()));
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

    public static Throwable unwrapCompletionException(Throwable ex) {
        return SharedFunctions.unwrapCompletionException(ex);
    }
    
    public static <T> Promise<T> loop(T initialValue, 
                                      Predicate<? super T> loopCondition,
                                      Function<? super T, ? extends CompletionStage<T>> loopBody) {
        AsyncLoop<T> asyncLoop = new AsyncLoop<>(loopCondition, loopBody);
        asyncLoop.run(initialValue);
        return asyncLoop;  
    }
    
    public static <T, R extends AutoCloseable> Promise<T> tryApply(CompletionStage<R> stage,
                                                                   Function<? super R, ? extends T> fn) {
        return tryApply(from(stage), fn);
    }
    
    public static <T, R extends AutoCloseable> Promise<T> tryApply(Promise<R> p,
                                                                   Function<? super R, ? extends T> fn) {
        return p.thenApply(r -> {
                    try (R resource = r) {
                        return (T)fn.apply(resource);
                    } catch (RuntimeException | Error rte) {
                        throw rte;
                    } catch (Throwable ex) {
                        throw new CompletionException(ex);
                    }
                });
    }

    public static <T, R extends AsyncCloseable> Promise<T> tryApplyEx(CompletionStage<R> stage,
                                                                      Function<? super R, ? extends T> fn) {
        return tryApplyEx(from(stage), fn);
    }
    
    public static <T, R extends AsyncCloseable> Promise<T> tryApplyEx(Promise<R> p,
                                                                      Function<? super R, ? extends T> fn) {
        return p.thenCompose(resource -> {
                   T result;
                   try {
                       result = fn.apply(resource);
                   } catch (Throwable onAction) {
                       try {
                           // Don't use dependent here
                           // So resource.close() is never cancellable
                           return resource.close().thenCompose(__ -> failure(onAction));
                       } catch (Throwable onClose) {
                           onAction.addSuppressed(onClose);
                           return failure(onAction);
                       }
                   }
                           
                   try {
                       // Don't use dependent here
                       // So resource.close() is never cancellable
                       return resource.close().thenApply(__ -> result);
                   } catch (Throwable onClose) {
                       return failure(onClose);
                   }
               });
    }
    
    public static <T, R extends AutoCloseable> Promise<T> tryCompose(CompletionStage<R> stage,
                                                                     Function<? super R, ? extends CompletionStage<T>> fn) {
        return tryCompose(from(stage), fn);
    }

    public static <T, R extends AutoCloseable> Promise<T> tryCompose(Promise<R> p,
                                                                     Function<? super R, ? extends CompletionStage<T>> fn) {
        return p.thenCompose(resource -> {
                   CompletionStage<T> action;
                   try {
                       action = fn.apply(resource);
                   } catch (Throwable onAction) {
                       try {
                           resource.close();
                           return failure(onAction);
                       } catch (Exception onClose) {
                           onAction.addSuppressed(onClose);
                           return failure(onAction);
                       }
                   }

                   CompletableFutureWrapper<T> result = new CompletableFutureWrapper<>();
                   action.whenComplete((actionResult, actionException) -> {
                       try {
                           resource.close();
                           result.complete(actionResult, actionException);
                       } catch (Throwable onClose) {
                           completeExceptionally(result, actionException, onClose);
                       }
                   });
                   return result.onCancel(() -> cancelPromise(action, true));
               });        
    }
    
    public static <T, R extends AsyncCloseable> Promise<T> tryComposeEx(CompletionStage<R> stage,
                                                                        Function<? super R, ? extends CompletionStage<T>> fn) {
        return tryComposeEx(from(stage), fn);
    }
    
    public static <T, R extends AsyncCloseable> Promise<T> tryComposeEx(Promise<R> p,
                                                                        Function<? super R, ? extends CompletionStage<T>> fn) {
        return p.thenCompose(resource -> {
                   CompletionStage<T> action;
                   try {
                       action = fn.apply(resource);
                   } catch (Throwable onAction) {
                       try {
                           // Don't use dependent here
                           // So resource.close() is never cancellable
                           return resource.close().thenCompose(__ -> failure(onAction));
                       } catch (Throwable onClose) {
                           onAction.addSuppressed(onClose);
                           return failure(onAction);
                       }                               
                   }

                   CompletableFutureWrapper<T> result = new CompletableFutureWrapper<>();
                   action.whenComplete((actionResult, actionException) -> {
                       try {
                           CompletionStage<?> afterClose = resource.close();
                           afterClose.whenComplete((__, onClose) -> {
                               if (null == onClose) {
                                   result.complete(actionResult, actionException);
                               } else {
                                   completeExceptionally(result, actionException, onClose);
                               }
                           });
                       } catch (Throwable onClose) {
                           completeExceptionally(result, actionException, onClose);
                       }
                   });
                   return result.onCancel(() -> cancelPromise(action, true));
               });
    }
    
    public static <S, T, A, R> Promise<R> partitioned(Iterable<? extends S> values, 
                                                      int batchSize, 
                                                      Function<? super S, CompletionStage<? extends T>> spawner, 
                                                      Collector<T, A, R> downstream) {
        return partitioned1(values.iterator(), null, batchSize, spawner, downstream);
    }
    
    public static <S, T, A, R> Promise<R> partitioned(Iterable<? extends S> values, 
                                                      int batchSize, 
                                                      Function<? super S, CompletionStage<? extends T>> spawner, 
                                                      Collector<T, A, R> downstream,
                                                      Executor downstreamExecutor) {
        return partitioned2(values.iterator(), null, batchSize, spawner, downstream, downstreamExecutor);
    }
    
    public static <S, T, A, R> Promise<R> partitioned(Stream<? extends S> values, 
                                                      int batchSize, 
                                                      Function<? super S, CompletionStage<? extends T>> spawner, 
                                                      Collector<T, A, R> downstream) {
        return partitioned1(values.iterator(), values, batchSize, spawner, downstream);
    }
    
    public static <S, T, A, R> Promise<R> partitioned(Stream<? extends S> values, 
                                                      int batchSize, 
                                                      Function<? super S, CompletionStage<? extends T>> spawner, 
                                                      Collector<T, A, R> downstream,
                                                      Executor downstreamExecutor) {
        return partitioned2(values.iterator(), values, batchSize, spawner, downstream, downstreamExecutor);
    }
    
    private static <S, T, A, R> Promise<R> partitioned1(Iterator<? extends S> values, 
                                                        Object source,
                                                        int batchSize, 
                                                        Function<? super S, CompletionStage<? extends T>> spawner, 
                                                        Collector<T, A, R> downstream) {
        return
            parallelStep1(values, batchSize, spawner, downstream)
            .dependent()
            .thenApply(downstream.finisher().compose(IndexedStep::payload), true)
            .asʹ(maybeClosingSource(null != source? source : values))
            .unwrap();
    }

    private static <S, T, A, R> Promise<R> partitioned2(Iterator<? extends S> values, 
                                                        Object source,
                                                        int batchSize, 
                                                        Function<? super S, CompletionStage<? extends T>> spawner, 
                                                        Collector<T, A, R> downstream,
                                                        Executor downstreamExecutor) {
        return 
            parallelStep2(values, batchSize, spawner, downstream, downstreamExecutor)
            .dependent()
            .thenApplyAsync(downstream.finisher().compose(IndexedStep::payload), downstreamExecutor, true)
            .asʹ(maybeClosingSource(null != source? source : values))
            .unwrap();
    }
    
    private static <S, T, A, R> Promise<IndexedStep<A>> parallelStep1(
        Iterator<? extends S> values, int batchSize,
        Function<? super S, CompletionStage<? extends T>> spawner,                                                        
        Collector<T, A, R> downstream) {

        return loop(new IndexedStep<>(), step -> step.initial() || values.hasNext(), step -> {
            List<S> valuesBatch = drainBatch(values, batchSize);
            if (valuesBatch.isEmpty()) {
                // Over
                return Promises.success(step.initial() ? step.next(downstream.supplier().get()) : step);
            } else {
                List<CompletionStage<? extends T>> promisesBatch = 
                    valuesBatch.stream()
                               .map(spawner)
                               .collect(Collectors.toList());
                
                return 
                Promises.all(promisesBatch)
                        .dependent()
                        .thenApply(vals -> step.next(
                            accumulate(vals, step.initial(), step.payload(), downstream)
                        ), true);
            }
        });
    }

    private static <S, T, A, R> Promise<IndexedStep<A>> parallelStep2(
        Iterator<? extends S> values, int batchSize,
        Function<? super S, CompletionStage<? extends T>> spawner,                                                        
        Collector<T, A, R> downstream,
        Executor downstreamExecutor) {

        return loop(new IndexedStep<>(), step -> step.initial() || values.hasNext(), step -> {
            List<S> valuesBatch = drainBatch(values, batchSize);
            if (valuesBatch.isEmpty()) {
                // Over
                return step.initial() ?
                    CompletableTask.supplyAsync(() -> step.next(downstream.supplier().get()), downstreamExecutor)
                    :
                    Promises.success(step);
            } else {
                List<CompletionStage<? extends T>> promisesBatch = 
                        valuesBatch.stream()
                                   .map(spawner)
                                   .collect(Collectors.toList());
                
                return 
                Promises.all(promisesBatch)
                        .dependent()
                        .thenApplyAsync(vals -> step.next(
                            accumulate(vals, step.initial(), step.payload(), downstream)
                        ), downstreamExecutor, true);                
            }
        });
    }
    
    private static class IndexedStep<T> {
        private final int idx;
        private final T payload;
        
        IndexedStep() {
            this(0, null);
        }
        
        private IndexedStep(int idx, T payload) {
            this.idx     = idx;
            this.payload = payload;
        }
        
        IndexedStep<T> next(T payload) {
            return new IndexedStep<>(idx + 1, payload);
        }
        
        boolean initial() {
            return idx == 0;
        }
        
        T payload() {
            return payload;
        }
    }
    
    private static <T> List<T> drainBatch(Iterator<? extends T> values, int batchSize) {
        List<T> valuesBatch = new ArrayList<>(batchSize);
        for (int count = 0; values.hasNext() && count < batchSize; count++) {
            valuesBatch.add(values.next());
        }        
        return valuesBatch;
    }
    
    private static <T, A, R> A accumulate(List<T> vals, boolean initial, A current, Collector<T, A, R> downstream) {
        A insertion = downstream.supplier().get();
        vals.stream()
            .forEach(v -> downstream.accumulator().accept(insertion, v));
        
        return initial ? insertion : downstream.combiner().apply(current, insertion);
    }
    
    private static <T> Function<DependentPromise<T>, DependentPromise<T>> maybeClosingSource(Object source) {
        if (source instanceof AutoCloseable) {
            return p -> p.whenComplete((r, e) -> {
                try {
                    ((AutoCloseable)source).close();
                } catch (Exception ex) {
                    if (null != e) {
                        e.addSuppressed(ex);
                    } else {
                        throw wrapCompletionException(ex);
                    }
                }
            }, true);
        } else {
            return Function.identity();
        }
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
    
    public static <K, T> Promise<Map<K, T>> all(Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
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
    
    public static <K, T> Promise<Map<K, T>> all(boolean cancelRemaining, 
                                                Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return atLeast(null == promises ? 0 : promises.size(), 0, cancelRemaining, promises);
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
    
    /*
    @SafeVarargs
    public static <T> Promise<List<T>> anyOrdered(CompletionStage<? extends T>... promises) {
        return anyOrdered(Arrays.asList(promises));
    }
    
    public static <T> Promise<List<T>> anyOrdered(List<? extends CompletionStage<? extends T>> promises) {
        return anyOrdered(true, promises);
    }
    */
    
    public static <K, T> Promise<Map<K, T>> any(Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
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
                    Promises::firstElement, Function.identity() /* DO NOT unwrap multitarget exception */
                );
        }
    }
    
    /*
    @SafeVarargs
    public static <T> Promise<List<T>> anyOrdered(boolean cancelRemaining, CompletionStage<? extends T>... promises) {
        return anyOrdered(cancelRemaining, Arrays.asList(promises));
    }    
    
    public static <T> Promise<List<T>> anyOrdered(boolean cancelRemaining, List<? extends CompletionStage<? extends T>> promises) {
        return atLeastOrdered(1, cancelRemaining, promises); 
    } 
    */   
    
    public static <K, T> Promise<Map<K, T>> any(boolean cancelRemaining, 
                                                Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return atLeast(1, maxAllowedErrors(promises, 1), cancelRemaining, promises);
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
    
    /*
    @SafeVarargs
    public static <T> Promise<List<T>> anyOrderedStrict(CompletionStage<? extends T>... promises) {
        return anyOrderedStrict(Arrays.asList(promises)); 
    }
    
    public static <T> Promise<List<T>> anyOrderedStrict(List<? extends CompletionStage<? extends T>> promises) {
        return anyOrderedStrict(true, promises);        
    }
    */
    
    public static <K, T> Promise<Map<K, T>> anyStrict(Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
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
                    Promises::firstElement, Promises::unwrapMultitargetException
                );
        }
    }
    
    /*
    @SafeVarargs
    public static <T> Promise<List<T>> anyOrderedStrict(boolean cancelRemaining, CompletionStage<? extends T>... promises) {
        return anyOrderedStrict(cancelRemaining, Arrays.asList(promises));
    }
    
    public static <T> Promise<List<T>> anyOrderedStrict(boolean cancelRemaining, List<? extends CompletionStage<? extends T>> promises) {
        return atLeastOrdered(1, 0, cancelRemaining, promises);
    }
    */
    
    public static <K, T> Promise<Map<K, T>> anyStrict(boolean cancelRemaining, 
                                                      Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return atLeast(1, 0, cancelRemaining, promises);
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
    
    @SafeVarargs
    public static <T> Promise<List<Optional<T>>> atLeastOrdered(int minResultsCount, CompletionStage<? extends T>... promises) {
        return atLeastOrdered(minResultsCount, Arrays.asList(promises));
    }    
    
    public static <T> Promise<List<Optional<T>>> atLeastOrdered(int minResultsCount, List<? extends CompletionStage<? extends T>> promises) {
        return atLeastOrdered(minResultsCount, true, promises);
    }    
    
    public static <K, T> Promise<Map<K, T>> atLeast(int minResultsCount, 
                                                    Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
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
        return atLeast(minResultsCount, maxAllowedErrors(promises, minResultsCount), cancelRemaining, promises);
    }
    
    @SafeVarargs
    public static <T> Promise<List<Optional<T>>> atLeastOrdered(int minResultsCount, boolean cancelRemaining, 
                                                                CompletionStage<? extends T>... promises) {
        
        return atLeastOrdered(minResultsCount, cancelRemaining, Arrays.asList(promises));
    }    
    
    public static <T> Promise<List<Optional<T>>> atLeastOrdered(int minResultsCount, boolean cancelRemaining,
                                                                List<? extends CompletionStage<? extends T>> promises) {
        return atLeastOrdered(minResultsCount, maxAllowedErrors(promises, minResultsCount), cancelRemaining, promises);
    }
    
    public static <K, T> Promise<Map<K, T>> atLeast(int minResultsCount, boolean cancelRemaining, 
                                                    Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return atLeast(
            minResultsCount, maxAllowedErrors(promises, minResultsCount), cancelRemaining, promises
        );
    }
    
    private static int maxAllowedErrors(Map<?, ?> promises, int minResultsCount) {
        return null == promises ? 0 : maxAllowedErrors(promises.entrySet(), minResultsCount);
        
    }
    
    private static int maxAllowedErrors(Collection<?> promises, int minResultsCount) {
        return null == promises ? 0 : Math.max(0, promises.size() - minResultsCount);
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
    
    @SafeVarargs
    public static <T> Promise<List<Optional<T>>> atLeastOrderedStrict(int minResultsCount, CompletionStage<? extends T>... promises) {
        return atLeastOrderedStrict(minResultsCount, Arrays.asList(promises));
    }
    
    public static <T> Promise<List<Optional<T>>> atLeastOrderedStrict(int minResultsCount, 
                                                                      List<? extends CompletionStage<? extends T>> promises) {
        return atLeastOrderedStrict(minResultsCount, true, promises);
    }    
    
    public static <K, T> Promise<Map<K, T>> atLeastStrict(int minResultsCount, 
                                                          Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
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
    
    @SafeVarargs
    public static <T> Promise<List<Optional<T>>> atLeastOrderedStrict(int minResultsCount, boolean cancelRemaining, 
                                                                      CompletionStage<? extends T>... promises) {
        return atLeastOrderedStrict(minResultsCount, cancelRemaining, Arrays.asList(promises));
    }  
    
    public static <T> Promise<List<Optional<T>>> atLeastOrderedStrict(int minResultsCount, boolean cancelRemaining, 
                                                                      List<? extends CompletionStage<? extends T>> promises) {
        return atLeastOrdered(minResultsCount, 0, cancelRemaining, promises);
    }    
    
    public static <K, T> Promise<Map<K, T>> atLeastStrict(int minResultsCount, boolean cancelRemaining, 
                                                          Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
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
        return atLeast(
            minResultsCount, maxErrorsCount, cancelRemaining, 
            AggregatingPromise.newWithSuccessResults(), Collections::singletonList, 
            promises
        );
    }    
    
    @SafeVarargs
    public static <T> Promise<List<Optional<T>>> atLeastOrdered(int minResultsCount, int maxErrorsCount, boolean cancelRemaining, 
                                                                CompletionStage<? extends T>... promises) {
        
        return atLeastOrdered(minResultsCount, maxErrorsCount, cancelRemaining, Arrays.asList(promises));
    }    
    
    public static <T> Promise<List<Optional<T>>> atLeastOrdered(int minResultsCount, int maxErrorsCount, boolean cancelRemaining, 
                                                                List<? extends CompletionStage<? extends T>> promises) {
        return atLeast(
            minResultsCount, maxErrorsCount, cancelRemaining, 
            AggregatingPromise.newWithAllResults(), v -> Collections.singletonList(Optional.ofNullable(v)), 
            promises
        );
    }      
    
    private static <T, R> Promise<List<R>> atLeast(int minResultsCount, int maxErrorsCount, boolean cancelRemaining,
                                                   AggregatingPromise.Constructor<T, R> ctr,
                                                   Function<? super T, ? extends List<R>> singleResultMapper,
                                                   List<? extends CompletionStage<? extends T>> promises) {
        
        int size = null == promises ? 0 : promises.size();
        if (minResultsCount > size) {
            Promise<List<R>> result = insufficientNumberOfArguments(minResultsCount, size);
            if (cancelRemaining && size > 0) {
                promises.stream().forEach( p -> cancelPromise(p, true) );
            }
            return result;
        } else if (minResultsCount == 0) {
            return success(Collections.emptyList());
        } else if (size == 1) {
            CompletionStage<? extends T> stage = promises.get(0);
            return transform(stage, singleResultMapper, Promises::wrapMultitargetException);
        } else {
            return ctr.create(minResultsCount, maxErrorsCount, cancelRemaining, promises)
                      .start();
        }
    }
    
    public static <K, T> Promise<Map<K, T>> atLeast(int minResultsCount, int maxErrorsCount, boolean cancelRemaining, 
                                                    Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        Map<K, T> result = new ConcurrentHashMap<>();
        return atLeast(
            minResultsCount, maxErrorsCount, cancelRemaining, 
            AggregatingPromise.newWithEmptyResults(), SharedFunctions.nullify(),
            collectKeyedResults(result, promises)
        ) 
        .dependent()
        .thenApply(__ -> Collections.unmodifiableMap(limitEntries(result, minResultsCount)), true)
        .unwrap();
    }    
    
    public static Promise<Void> retry(Runnable codeBlock, Executor executor, 
                                      RetryPolicy<? super Void> retryPolicy) {
        
        return retry(RetryRunnable.from(codeBlock), executor, retryPolicy);
    }
    
    public static Promise<Void> retry(RetryRunnable codeBlock, Executor executor, 
                                      RetryPolicy<? super Void> retryPolicy) {
        
        return retry(RetryCallable.from(codeBlock), executor, retryPolicy.acceptNullResult());
    }

    public static <T> Promise<T> retry(Callable<T> codeBlock, Executor executor, 
                                       RetryPolicy<? super T> retryPolicy) {
        
        return retry(RetryCallable.from(codeBlock), executor, retryPolicy);
    }

    public static <T extends C, C> Promise<T> retry(RetryCallable<T, C> codeBlock, Executor executor, 
                                                    RetryPolicy<? super C> retryPolicy) {
        // 
        return retryFuture(
            (RetryContext<C> ctx) -> CompletableTask.submit(() -> codeBlock.call(ctx), executor), 
            retryPolicy
        );
    }
    
    public static <T> Promise<T> retryOptional(Callable<Optional<T>> codeBlock, Executor executor, 
                                               RetryPolicy<? super T> retryPolicy) {
        
        return retryOptional(RetryCallable.from(codeBlock), executor, retryPolicy);
    }
    
    public static <T extends C, C> Promise<T> retryOptional(RetryCallable<Optional<T>, C> codeBlock, Executor executor, 
                                                            RetryPolicy<? super C> retryPolicy) {
        
        // Need explicit type on lambda param
        return retry((RetryContext<C> ctx) -> codeBlock.call(ctx).orElse(null), executor, retryPolicy);
    }
    
    public static <T> Promise<T> retryFuture(Callable<? extends CompletionStage<T>> invoker, 
                                             RetryPolicy<? super T> retryPolicy) {
        
        return retryFuture(RetryCallable.from(invoker), retryPolicy);
    }
    
    public static <T extends C, C> Promise<T> retryFuture(RetryCallable<? extends CompletionStage<T>, C> futureFactory, 
                                                          RetryPolicy<? super C> retryPolicy) {
        return retryImpl((RetryContext<C> ctx) -> {
            try {
                // Not sure how many time futureFactory.call will take
                return from(futureFactory.call(ctx));
            } catch (Throwable ex) {
                return failure(ex);
            }
        }, retryPolicy, true);
    }

    private static <T extends C, C> Promise<T> retryImpl(Function<? super RetryContext<C>, ? extends Promise<T>> futureFactory, 
                                                         RetryPolicy<? super C> retryPolicy,
                                                         boolean usePrevAsync) {

        class State {
            private final boolean isDone;
            private final DependentPromise<?> prevAsync;

            final RetryContext<C> ctx;
            final RetryPolicy.Verdict verdict;
            
            
            private State(RetryContext<C> ctx, DependentPromise<?> prevAsync, boolean isDone) {
                this.ctx       = ctx;
                this.prevAsync = prevAsync;
                this.isDone    = isDone;
                this.verdict   = isDone ? null : retryPolicy.shouldContinue(ctx);
            }
            
            // Initial
            State(RetryContext<C> ctx) {
                this(ctx, null, false);
            }
            
            // Transition to intermediate
            State next(Throwable error, Duration d, DependentPromise<?> prevAsync) {
               return new State(ctx.nextRetry(d, unwrapCompletionException(error)), useAsync(prevAsync), false); 
            }
            
            State next(T result, Duration d, DependentPromise<?> prevAsync) {
                return new State(ctx.nextRetry(d, result), useAsync(prevAsync), false);
            }
            
            // Transition to final
            State done(T result, Duration d) {
                return new State(ctx.nextRetry(d, result), null, true);
            }
            
            boolean isRunning() {
                return !isDone && verdict.shouldExecute();
            }
            
            @SuppressWarnings("unchecked")
            Promise<T> toPromise() {
                // it's safe to cast types C -> T while we use T in done
                return  isDone ? success((T)ctx.getLastResult()) : failure(ctx.asFailure());
            }
            
            private DependentPromise<?> useAsync(DependentPromise<?> prevAsync) {
                return prevAsync != null ? prevAsync : this.prevAsync;
            }
            
            DependentPromise<?> makeDelay(Duration delay) {
                return prevAsync == null ?
                    Timeouts.delay(delay).dependent()
                    :
                    prevAsync.exceptionally(SharedFunctions.nullify()) // Don't propagate own error
                             .delay(delay, true, true);
            }
        }
        
        return loop(new State(RetryContext.initial()), State::isRunning , currentState -> {
            RetryContext<C> ctx = currentState.ctx;
            RetryPolicy.Verdict verdict = currentState.verdict;

            Supplier<Promise<State>> callSupplier = () -> {
                long startTime = System.nanoTime();

                DependentPromise<T> target = futureFactory.apply(ctx).dependent();
                DependentPromise<T> withTimeout;
                
                Duration timeout = verdict.timeout();
                if (DelayPolicy.isValid(timeout)) {
                    withTimeout = target.orTimeout(timeout, true, true);
                } else {
                    withTimeout = target;
                }

                return withTimeout.handle((value, ex) -> {
                    Duration duration = Duration.ofNanos(System.nanoTime() - startTime);
                    if (null == ex) {
                        if (retryPolicy.acceptResult(value)) {
                            return currentState.done(value, duration);
                        } else {
                            return currentState.next(value, duration, target);
                        }
                    } else {
                        return currentState.next(ex, duration, target);
                    }                  
                }, true);
            };
            
            Duration backoffDelay = verdict.backoffDelay();
            return DelayPolicy.isValid(backoffDelay) ?
                currentState
                .makeDelay(backoffDelay)
                .thenCompose(__ -> callSupplier.get(), true)
                .exceptionally(ex -> {
                    // May be thrown when backoff delay is interrupted (canceled)
                    return currentState.next(ex, Duration.ZERO, null);
                 }, true)
                :
                callSupplier.get();
        })
        .dependent()
        .thenCompose(State::toPromise, true)
        .unwrap();
    }    
    
    private static <T, U> Promise<T> transform(CompletionStage<U> original, 
                                               Function<? super U, ? extends T> resultMapper, 
                                               Function<? super Throwable, ? extends Throwable> errorMapper) {
        CompletableFutureWrapper<T> result = new CompletableFutureWrapper<>();
        original.whenComplete(
            (r, e) -> iif(null == e ? result.success(resultMapper.apply(r)) : result.failure(errorMapper.apply(e)))
        );
        return result.onCancel(() -> cancelPromise(original, true));
    }
    
    private static <T> T firstElement(Collection<? extends T> collection) {
        return collection.stream().findFirst().get();
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
            return new MultitargetException(
                "Aggregated promise was completed exceptionally (1 out of 1)", 
                Collections.singletonList(exception)
            );
        }
    }
    
    private static <T> Promise<T> insufficientNumberOfArguments(int minResultCount, int size) {
        String message = String.format(
            "The number of futures supplied (%d) is less than a number of futures to await (%d)", 
            size, minResultCount
        );
        Exception ex = new NoSuchElementException(message);
        //Prefer exceptional completion as opposed to runtime exception on combined promise construction
        ex.fillInStackTrace();
        return failure(ex);
        /*
        throw new IllegalArgumentException(message);        
        */
    }
    
    private static <T> void completeExceptionally(CompletableFutureWrapper<T> result, Throwable actionException, Throwable onClose) {
        if (null != actionException) {
            actionException.addSuppressed(onClose);
            result.failure(actionException);
        } else {
            result.failure(onClose);
        }        
    }
    
    private static <K, T> List<? extends CompletionStage<? extends T>> 
        collectKeyedResults(Map<K, T> result, 
                            Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        
        if (null == promises || promises.isEmpty()) {
            return Collections.emptyList();
        }
        return promises.entrySet()
                       .stream()
                       .map(e -> 
                           from((CompletionStage<? extends T>)e.getValue())
                           .dependent()
                           .thenApply(value -> {
                               result.put(e.getKey(), value);
                               return value;
                           }, true)
                       )
                       .collect(Collectors.toList())
        ;        
    }
    
    private static <K, V> Map<K, V> limitEntries(Map<K, V> map, int maxCount) {
        // Copy to avoid ongoing mods from other threads
        Map<K, V> result = new HashMap<>(map);
        if (result.size() <= maxCount) {
            return result;
        } else {
            return result.entrySet()
                         .stream()
                         .limit(maxCount)
                         .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }
}
