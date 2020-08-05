/**
 * Copyright 2015-2020 Valery Silaev (http://vsilaev.com)
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
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
    
    public static <T> CompletionStage<T> withDefaultExecutor(CompletionStage<T> stage, Executor executor) {
        return new ExecutorBoundCompletionStage<>(stage, executor);
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

    
    public static <T, R extends AutoCloseable> Promise<T> tryApply(CompletionStage<R> resourcePromise,
                                                                   Function<? super R, ? extends T> fn) {
        return tryApply(from(resourcePromise), fn);
    }
    
    public static <T, R extends AutoCloseable> Promise<T> tryApply(Promise<R> resourcePromise,
                                                                   Function<? super R, ? extends T> fn) {
        return 
        resourcePromise.dependent()
                       .thenApply(r -> {
                            try (R resource = r) {
                                return (T)fn.apply(resource);
                            } catch (RuntimeException | Error rte) {
                                throw rte;
                            } catch (Throwable ex) {
                                throw new CompletionException(ex);
                            }
                        }, true)
                       .unwrap();
    }
    

    public static <T, R extends AsyncCloseable> Promise<T> tryApplyEx(CompletionStage<R> resourcePromise,
                                                                      Function<? super R, ? extends T> fn) {
        return tryApplyEx(from(resourcePromise), fn);
    }
    
    public static <T, R extends AsyncCloseable> Promise<T> tryApplyEx(Promise<R> resourcePromise,
                                                                      Function<? super R, ? extends T> fn) {
        return 
        resourcePromise.dependent()
                       .thenCompose(resource -> {
                           T result;
                           try {
                               result = fn.apply(resource);
                           } catch (Throwable actionException) {
                               try {
                                   // Use dependent here?
                                   return resource.close().thenCompose(__ -> failure(actionException));
                               } catch (Throwable onClose) {
                                   actionException.addSuppressed(onClose);
                                   return failure(onClose);
                               }
                           }
                           
                           try {
                               // Use dependent here?
                               return resource.close().thenApply(__ -> result);
                           } catch (Throwable onClose) {
                               return failure(onClose);
                           }

                       }, true)
                       .unwrap();
    }
    
    public static <T, R extends AutoCloseable> Promise<T> tryCompose(CompletionStage<R> resourcePromise,
                                                                     Function<? super R, ? extends CompletionStage<T>> fn) {
        return tryCompose(from(resourcePromise), fn);
    }

    public static <T, R extends AutoCloseable> Promise<T> tryCompose(Promise<R> resourcePromise,
                                                                     Function<? super R, ? extends CompletionStage<T>> fn) {
        return
        resourcePromise.dependent()
                       .thenCompose(resource -> {
                           CompletionStage<T> action;
                           try {
                               action = fn.apply(resource);
                           } catch (Throwable composeException) {
                               try {
                                   resource.close();
                               } catch (Exception onClose) {
                                   composeException.addSuppressed(onClose);
                               }
                               return failure(composeException);
                           }

                           CompletablePromise<T> result = new CompletablePromise<>();
                           action.whenComplete((actionResult, actionException) -> {
                               try {
                                   resource.close();
                               } catch (Throwable onClose) {
                                   if (null != actionException) {
                                       actionException.addSuppressed(onClose);
                                       result.onFailure(actionException);
                                   } else {
                                       result.onFailure(onClose);
                                   }
                                   // DONE WITH ERROR ON CLOSE
                                   return;
                               }
                               // CLOSE OK
                               if (null == actionException) {
                                   result.onSuccess(actionResult);
                               } else {
                                   result.onFailure(actionException);
                               }
                           });
                           return result.onCancel(() -> cancelPromise(action, true));
                       }, true)
                       .unwrap();        
    }
    
    
    public static <T, R extends AsyncCloseable> Promise<T> tryComposeEx(Promise<R> resourcePromise,
                                                                      Function<? super R, ? extends CompletionStage<T>> fn) {
        return
        resourcePromise.dependent()
                       .thenCompose(resource -> {
                           CompletionStage<T> action;
                           try {
                               action = fn.apply(resource);
                           } catch (Throwable composeException) {
                               try {
                                   // Use dependent here?
                                   return resource.close().thenCompose(__ -> failure(composeException));
                               } catch (Throwable onClose) {
                                   composeException.addSuppressed(onClose);
                                   return failure(onClose);
                               }                               
                           }

                           CompletablePromise<T> result = new CompletablePromise<>();
                           action.whenComplete((actionResult, actionException) -> {
                               CompletionStage<?> afterClose;
                               try {
                                   afterClose = resource.close();
                               } catch (Throwable onClose) {
                                   if (null != actionException) {
                                       actionException.addSuppressed(onClose);
                                       result.onFailure(actionException);
                                   } else {
                                       result.onFailure(onClose);
                                   }
                                   // DONE WITH ERROR ON ASYNC CLOSE
                                   return;
                               }
                               // ASYNC CLOSE INVOKE OK
                               afterClose.whenComplete((__, onClose) -> {
                                   if (null != actionException) {
                                       if (null != onClose) {
                                           actionException.addSuppressed(onClose);
                                       }
                                       result.onFailure(actionException);
                                   } else if (null != onClose) {
                                       result.onFailure(onClose);
                                   } else {
                                       result.onSuccess(actionResult);
                                   }
                               });
                           });
                           return result.onCancel(() -> cancelPromise(action, true));
                       }, true)
                       .unwrap();
    }
    
    public static <T, A, R> Promise<R> partitioned(Iterable<? extends T> values, 
                                                   int batchSize, 
                                                   Function<? super T, CompletionStage<? extends T>> spawner, 
                                                   Collector<T, A, R> downstream) {
        return partitioned1(values.iterator(), batchSize, spawner, downstream);
    }
    
    public static <T, A, R> Promise<R> partitioned(Iterable<? extends T> values, 
                                                   int batchSize, 
                                                   Function<? super T, CompletionStage<? extends T>> spawner, 
                                                   Collector<T, A, R> downstream,
                                                   Executor downstreamExecutor) {
        return partitioned2(values.iterator(), batchSize, spawner, downstream, downstreamExecutor);
    }
    
    public static <T, A, R> Promise<R> partitioned(Stream<? extends T> values, 
                                                   int batchSize, 
                                                   Function<? super T, CompletionStage<? extends T>> spawner, 
                                                   Collector<T, A, R> downstream) {
        return partitioned1(values.iterator(), batchSize, spawner, downstream);
    }
    
    public static <T, A, R> Promise<R> partitioned(Stream<? extends T> values, 
                                                   int batchSize, 
                                                   Function<? super T, CompletionStage<? extends T>> spawner, 
                                                   Collector<T, A, R> downstream,
                                                   Executor downstreamExecutor) {
        return partitioned2(values.iterator(), batchSize, spawner, downstream, downstreamExecutor);
    }
    
    
    private static <T, A, R> Promise<R> partitioned1(Iterator<? extends T> values, 
                                                    int batchSize, 
                                                    Function<? super T, CompletionStage<? extends T>> spawner, 
                                                    Collector<T, A, R> downstream) {
        return
            parallelStep1(values, batchSize, spawner, downstream)
            .dependent()
            .thenApply(downstream.finisher(), true)
            .as(onCloseSource(values))
            .unwrap();
    }
    

    private static <T, A, R> Promise<R> partitioned2(Iterator<? extends T> values, 
                                                    int batchSize, 
                                                    Function<? super T, CompletionStage<? extends T>> spawner, 
                                                    Collector<T, A, R> downstream,
                                                    Executor downstreamExecutor) {
        return 
            parallelStep2(values, batchSize, spawner, downstream, downstreamExecutor)
            .dependent()
            .thenApplyAsync(downstream.finisher(), downstreamExecutor, true)
            .as(onCloseSource(values))
            .unwrap();
    }
    
    private static <T> Function<Promise<T>, Promise<T>> onCloseSource(Object source) {
        if (source instanceof AutoCloseable) {
            return p -> p.dependent().whenComplete((r, e) -> {
                try (AutoCloseable o = (AutoCloseable)source) {
                    
                } catch (RuntimeException | Error ex) {
                    if (null != e) {
                        e.addSuppressed(ex);
                    } else {
                        throw ex;
                    }
                } catch (Exception ex) {
                    if (null != e) {
                        e.addSuppressed(ex);
                    } else {
                        throw new CompletionException(ex);
                    }
                }
            }, true);
        } else {
            return Function.identity();
        }
    }
    
    private static <T, A, R> Promise<A> parallelStep1(Iterator<? extends T> values, 
                                                     int batchSize,
                                                     Function<? super T, CompletionStage<? extends T>> spawner,                                                        
                                                     Collector<T, A, R> downstream) {

        int[] step = {0};
        return loop(null, __ -> step[0] == 0 || values.hasNext(), current -> {
            List<T> valuesBatch = drainBatch(values, batchSize);
            if (valuesBatch.isEmpty()) {
                // Over
                return Promises.success(step[0] == 0 ? downstream.supplier().get() : current);
            } else {
                List<CompletionStage<? extends T>> promisesBatch = 
                    valuesBatch.stream()
                               .map(spawner)
                               .collect(Collectors.toList());
               
                boolean initial = step[0] == 0;
                step[0]++;
                return 
                Promises.all(promisesBatch)
                        .dependent()
                        .thenApply(vals -> accumulate(vals, initial, current, downstream), true);
            }
        });
    }

    private static <T, A, R> Promise<A> parallelStep2(Iterator<? extends T> values, 
                                                     int batchSize,
                                                     Function<? super T, CompletionStage<? extends T>> spawner,                                                        
                                                     Collector<T, A, R> downstream,
                                                     Executor downstreamExecutor) {

        int[] step = {0};
        return loop(null, __ -> step[0] == 0 || values.hasNext(), current -> {
            List<T> valuesBatch = drainBatch(values, batchSize);
            if (valuesBatch.isEmpty()) {
                // Over
                Promise<A> result;
                if (step[0] == 0) {
                    result = CompletableTask.supplyAsync(downstream.supplier(), downstreamExecutor);
                } else {
                    result = Promises.success(current);
                }
                return result;
            } else {
                List<CompletionStage<? extends T>> promisesBatch = 
                        valuesBatch.stream()
                                   .map(spawner)
                                   .collect(Collectors.toList());
                boolean initial = step[0] == 0;
                step[0]++;
                return 
                Promises.all(promisesBatch)
                        .dependent()
                        .thenApplyAsync(vals -> accumulate(vals, initial, current, downstream), downstreamExecutor, true);                
            }
        });
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
    
    public static <K, T> Promise<Map<K, T>> all(Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return all(true, promises);
    }
    
    public static <K, T> Promise<Map<K, T>> all(boolean cancelRemaining, 
                                                Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        Map<K, T> result = new ConcurrentHashMap<>();
        return 
        all(cancelRemaining, groupToMap(result, promises)) 
        .dependent()
        .thenApply(__ -> Collections.unmodifiableMap(result), true)
        .unwrap();
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
    
    public static <K, T> Promise<Map<K, T>> any(Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return any(true, promises);
    }
    
    public static <K, T> Promise<Map<K, T>> any(boolean cancelRemaining, 
                                                Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        Map<K, T> result = new ConcurrentHashMap<>();
        return 
        any(cancelRemaining, groupToMap(result, promises)) 
        .dependent()
        .thenApply(__ -> Collections.unmodifiableMap(result), true)
        .unwrap();
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
    
    public static <K, T> Promise<Map<K, T>> anyStrict(Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return anyStrict(true, promises);
    }
    
    public static <K, T> Promise<Map<K, T>> anyStrict(boolean cancelRemaining, 
                                                      Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        Map<K, T> result = new ConcurrentHashMap<>();
        return 
        anyStrict(cancelRemaining, groupToMap(result, promises)) 
        .dependent()
        .thenApply(__ -> Collections.unmodifiableMap(result), true)
        .unwrap();
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
    
    public static <K, T> Promise<Map<K, T>> atLeast(int minResultsCount, 
                                                    Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return atLeast(minResultsCount, true, promises);
    }
    
    public static <K, T> Promise<Map<K, T>> atLeast(int minResultsCount, boolean cancelRemaining, 
                                                    Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        Map<K, T> result = new ConcurrentHashMap<>();
        return 
        atLeast(minResultsCount, cancelRemaining, groupToMap(result, promises)) 
        .dependent()
        .thenApply(__ -> Collections.unmodifiableMap(result), true)
        .unwrap();
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
    
    public static <K, T> Promise<Map<K, T>> atLeastStrict(int minResultsCount, 
                                                          Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return atLeastStrict(minResultsCount, true, promises);
    }
    
    public static <K, T> Promise<Map<K, T>> atLeastStrict(int minResultsCount, boolean cancelRemaining, 
                                                          Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        Map<K, T> result = new ConcurrentHashMap<>();
        return 
        atLeastStrict(minResultsCount, cancelRemaining, groupToMap(result, promises)) 
        .dependent()
        .thenApply(__ -> Collections.unmodifiableMap(result), true)
        .unwrap();
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
    
    public static <K, T> Promise<Map<K, T>> atLeast(int minResultsCount, int maxErrorsCount, boolean cancelRemaining, 
                                                    Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        Map<K, T> result = new ConcurrentHashMap<>();
        return 
        atLeast(minResultsCount, maxErrorsCount, cancelRemaining, groupToMap(result, promises)) 
        .dependent()
        .thenApply(__ -> Collections.unmodifiableMap(result), true)
        .unwrap();
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
        return tryValueOnce(codeBlock, executor, RetryContext.initial(retryPolicy));
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
        return tryFutureOnce(futureFactory, RetryContext.initial(retryPolicy));
    }
    

    private static <T extends C, C> Promise<T> tryValueOnce(RetryCallable<T, C> codeBlock, 
                                                           Executor executor, 
                                                           RetryContext<C> initialCtx) {
        
        @SuppressWarnings("unchecked")
        RetryContext<C>[] ctxRef = new RetryContext[] {initialCtx};
        return loop(null, v -> null == v || !v.isSuccess(), (Try<T> v) -> {
            RetryContext<C> ctx = ctxRef[0];
            RetryPolicy.Verdict verdict = ctx.shouldContinue();
            if (!verdict.shouldExecute()) {
                return failure(ctx.asFailure());
            }
            
            // Use Try<T> to avoid stop on exception in loop
            Supplier<Promise<Try<T>>> callSupplier = () -> {
                long startTime = System.nanoTime();
                // Call should be done via CompletableTask to let it be interruptible
                Promise<T> p = CompletableTask.submit(() -> codeBlock.call(ctx), executor);
                return handleTryResult(p, ctxRef, verdict, startTime);
            };
            
            Duration backoffDelay = verdict.backoffDelay();
            if (DelayPolicy.isValid(backoffDelay)) {
                // Invocation after timeout, change cancellation target
                DependentPromise<?> p = Timeouts.delay(backoffDelay).dependent();
                p.whenComplete((__, ex) -> {
                    // Update ctx if timeout or cancel during back-off
                    // It doesn't conflict with ctx modifications in actual call
                    // while actual call is never happens in case of error
                    if (null != ex) {
                        ctxRef[0] = ctx.nextRetry(
                            duration(0, 0), unwrapCompletionException(ex)
                        );
                    }
                });
                return p.thenCompose(__ -> callSupplier.get(), true);
            } else {
                return callSupplier.get();
            } 
        })
        .dependent()
        .thenApply(Try::done, true);
        // Don't unwrap - internal use only
    }
    
    private static <T extends C, C> Promise<T> tryFutureOnce(RetryCallable<? extends CompletionStage<T>, C> futureFactory, 
                                                             RetryContext<C> initialCtx) {

        @SuppressWarnings("unchecked")
        RetryContext<C>[] ctxRef = new RetryContext[] {initialCtx};
        DependentPromise<?>[] prev = new DependentPromise[1];
        
        return loop(null, v -> null == v || !v.isSuccess() , (Try<T> v) -> {
            RetryContext<C> ctx = ctxRef[0];
            RetryPolicy.Verdict verdict = ctx.shouldContinue();
            if (!verdict.shouldExecute()) {
                return failure(ctx.asFailure());
            }

            Supplier<Promise<Try<T>>> callSupplier = () -> {
                long startTime = System.nanoTime();
                
                Promise<T> target;
                try {
                    // Not sure how many time futureFactory.call will take
                    target = Promises.from(futureFactory.call(ctx));
                } catch (Exception ex) {
                    target = Promises.failure(ex);
                }
                
                DependentPromise<Try<T>> p = handleTryResult(target, ctxRef, verdict, startTime);
                prev[0] = p;
                return p;
            };
            Duration backoffDelay = verdict.backoffDelay();
            if (null != prev[0] && DelayPolicy.isValid(backoffDelay)) {
                DependentPromise<?> p = prev[0].delay(backoffDelay, /* Delay on error */ true, true); 
                p.whenComplete((__, ex) -> {
                    // Update ctx if timeout or cancel during back-off
                    // It doesn't conflict with ctx modifications in actual call
                    // while actual call is never happens in case of error
                    if (null != ex) {
                        ctxRef[0] = ctx.nextRetry(
                            duration(0, 0), unwrapCompletionException(ex)
                        );
                    }
                });
                // async due to unknown performance characteristics of futureFactory.call
                // - so switch to default executor of prev promise
                return p.thenComposeAsync(__ -> callSupplier.get(), true);
            } else {
                // Immediately send to executor
                return callSupplier.get(); 
            } 
        })
        .dependent()
        .thenApply(Try::done, true);
        // Don't unwrap - internal use only
    }    

    private static <T extends C, C> DependentPromise<Try<T>> handleTryResult(Promise<T> origin, 
                                                                             RetryContext<C>[] ctxRef, 
                                                                             RetryPolicy.Verdict verdict, long startTime) {
        DependentPromise<T> p;
        Duration timeout = verdict.timeout();
        if (DelayPolicy.isValid(timeout)) {
            p = origin.dependent()
                      .orTimeout(timeout, true, true);
        } else {
            p = origin.dependent();
        }
        
        return p.handle((value, ex) -> {
            RetryContext<C> ctx = ctxRef[0];
            if (null == ex && ctx.isValidResult(value)) {
                return Try.success(value);
            } else {
                long finishTime = System.nanoTime();
                ctxRef[0] = ctx.nextRetry(
                    duration(startTime, finishTime), unwrapCompletionException(ex)
                );
                return Try.failure(
                    ex != null ? ex :
                    new IllegalAccessException("Result not accepted by policy")
                );
            }                  
        }, true);
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
    
    private static <K, T> List<? extends CompletionStage<? extends T>> groupToMap(Map<K, T> result, Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return 
        promises.entrySet()
                .stream()
                .map(e -> {
                    CompletionStage<? extends T> promise = e.getValue();
                    return from(promise).dependent()
                                        .thenApply(value -> {
                                            result.put(e.getKey(), value);
                                            return value;
                                        }, true);
                })
                .collect(Collectors.toList())
        ;        
    }
    
    private static <V, T> RetryCallable<V, T> toRetryCallable(Callable<? extends V> callable) {
        return ctx -> callable.call();
    }
    
    private static Duration duration(long startTime, long finishTime) {
        return Duration.ofNanos(finishTime - startTime);
    }
}
