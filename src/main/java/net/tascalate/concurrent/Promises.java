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
    
    
    public static final Promise<Boolean> TRUE = Promises.success(Boolean.TRUE);
    public static final Promise<Boolean> FALSE = Promises.success(Boolean.FALSE);
    
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
     * Returns the shared null-completed promise, cast to the required type.
     *
     * @param <T> the nominal element type
     * @return a promise that completes with {@code null}
     */
    @SuppressWarnings("unchecked")
    public static <T> Promise<T> nothing() {
        return (Promise<T>) PROMISE_NOTHING;
    }

    /**
     * Returns the shared cancelled promise, cast to the required type.
     *
     * @param <T> the nominal element type
     * @return a promise that is already cancelled
     */
    @SuppressWarnings("unchecked")
    public static <T> Promise<T> canceled() {
        return (Promise<T>)PROMISE_CANCELED;
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
    
    /**
     * Sequentially iterates over the given {@link Iterable}, invoking the asynchronous
     * {@code loopBody} for each element.
     * <p>
     * The {@code loopBody} must return a {@link CompletionStage} of {@link Boolean}.
     * If the stage completes with {@code true}, the iteration continues with the next element.
     * If it completes with {@code false}, the iteration is terminated early.
     * </p>
     * <p>
     * The returned {@link Promise} completes successfully with {@code null} when all elements
     * have been processed or the loop is terminated early. It completes exceptionally if the
     * {@code loopBody} throws an exception or returns a failed stage.
     * </p>
     * <p>
     * Unlike parallel execution (e.g., {@link #all(CompletionStage...)}), this method guarantees
     * that the {@code loopBody} for the next element is not invoked until the stage for the
     * current element has completed.
     * </p>
     *
     * @param <T>      the type of elements in the iterable
     * @param source   the elements to iterate over
     * @param loopBody the asynchronous action to perform for each element, returning a stage
     *                 that indicates whether to continue ({@code true}) or stop ({@code false})
     * @return a promise that completes when the iteration finishes or is stopped
     * @see #loop(Object, Predicate, Function)
     */
    public static <T> Promise<Void> forEach(Iterable<? extends T> source, Function<? super T, ? extends CompletionStage<Boolean>> loopBody) {
        return loop(source.iterator(), Iterator::hasNext, iterator -> {
            T item = iterator.next();
            return from(loopBody.apply(item))
                   .dependent()
                   .thenApply(resume -> resume ? iterator : Collections.emptyIterator(), true);
        }).dependent()
          .thenAccept($ -> {}, true)
          ;
    }
    
    /**
     * Executes an asynchronous {@code while}-loop.
     * <p>
     * The loop evaluates the synchronous {@code loopCondition} before each iteration.
     * If the condition returns {@code true}, the asynchronous {@code loopBody} is invoked
     * with the current value. The value produced by the {@code loopBody}'s stage becomes
     * the input for the next iteration's condition check.
     * </p>
     * <p>
     * The loop terminates when the {@code loopCondition} returns {@code false}, or if an
     * iteration fails exceptionally. The returned {@link Promise} completes with the final
     * value that caused the condition to return {@code false}.
     * </p>
     * <p>
     * <b>Cancellation:</b> The returned promise is cancellable. Cancelling it will attempt
     * to cancel the currently executing {@link CompletionStage} returned by the {@code loopBody}.
     * </p>
     * <p>
     * <b>Stack Safety:</b> The implementation is stack-safe. If the {@code loopBody} returns
     * an already-completed stage, the next iteration is scheduled without growing the call stack.
     * </p>
     *
     * @param <T>           the type of the loop state/value
     * @param initialValue  the initial value passed to the first condition check
     * @param loopCondition a synchronous predicate evaluated before each iteration
     * @param loopBody      an asynchronous function that computes the next state
     * @return a promise that completes with the final state when the loop terminates
     * @see #forEach(Iterable, Function)
     */
    public static <T> Promise<T> loop(T initialValue, 
                                      Predicate<? super T> loopCondition,
                                      Function<? super T, ? extends CompletionStage<T>> loopBody) {
        AsyncLoop<T> asyncLoop = new AsyncLoop<>(loopCondition, loopBody);
        asyncLoop.run(initialValue);
        return asyncLoop;  
    }
    
    /**
     * Emulates a {@code try-with-resources} block for a <i>synchronous</i> function applied 
     * to an {@link AutoCloseable} resource delivered by a {@link CompletionStage}.
     * <p>
     * When the {@code stage} completes successfully, the resource is passed to {@code fn}. 
     * After {@code fn} returns (or throws), {@code resource.close()} is called synchronously. 
     * If both {@code fn} and {@code close()} throw, the exception from {@code close()} is 
     * added as a suppressed exception to the exception from {@code fn}.
     * </p>
     *
     * @param <T>   the result type of the function
     * @param <R>   the type of the {@link AutoCloseable} resource
     * @param stage the stage producing the resource
     * @param fn    the synchronous function to apply to the resource
     * @return a promise that completes with the result of {@code fn}, or exceptionally if 
     *         the stage, the function, or the close operation fails
     */
    public static <T, R extends AutoCloseable> Promise<T> tryApply(CompletionStage<R> stage,
                                                                   Function<? super R, ? extends T> fn) {
        return tryApply(from(stage), fn);
    }
    
    /**
     * Emulates a {@code try-with-resources} block for a <i>synchronous</i> function applied 
     * to an {@link AutoCloseable} resource delivered by a {@link Promise}.
     *
     * @param <T> the result type of the function
     * @param <R> the type of the {@link AutoCloseable} resource
     * @param p   the promise producing the resource
     * @param fn  the synchronous function to apply to the resource
     * @return a promise that completes with the result of {@code fn}
     */
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

    /**
     * Emulates a {@code try-with-resources} block for a <i>synchronous</i> function applied 
     * to an {@link AsyncCloseable} resource delivered by a {@link CompletionStage}.
     * <p>
     * When the {@code stage} completes successfully, the resource is passed to {@code fn}. 
     * After {@code fn} returns (or throws), {@code resource.close()} is invoked, and the 
     * resulting stage is awaited. The final promise completes only after the asynchronous 
     * close operation finishes.
     * </p>
     *
     * @param <T>   the result type of the function
     * @param <R>   the type of the {@link AsyncCloseable} resource
     * @param stage the stage producing the resource
     * @param fn    the synchronous function to apply to the resource
     * @return a promise that completes with the result of {@code fn} after the resource is closed
     */
    public static <T, R extends AsyncCloseable> Promise<T> tryApplyEx(CompletionStage<R> stage,
                                                                      Function<? super R, ? extends T> fn) {
        return tryApplyEx(from(stage), fn);
    }
    
    /**
     * Emulates a {@code try-with-resources} block for a <i>synchronous</i> function applied 
     * to an {@link AsyncCloseable} resource delivered by a {@link Promise}.
     *
     * @param <T> the result type of the function
     * @param <R> the type of the {@link AsyncCloseable} resource
     * @param p   the promise producing the resource
     * @param fn  the synchronous function to apply to the resource
     * @return a promise that completes with the result of {@code fn} after the resource is closed
     */
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
    
    /**
     * Emulates a {@code try-with-resources} block for an <i>asynchronous</i> function applied 
     * to an {@link AutoCloseable} resource delivered by a {@link CompletionStage}.
     * <p>
     * When the {@code stage} completes successfully, the resource is passed to {@code fn}, 
     * which returns a new {@link CompletionStage}. The resource is closed synchronously only 
     * after the stage returned by {@code fn} completes (successfully or exceptionally).
     * </p>
     *
     * @param <T>   the result type of the asynchronous function
     * @param <R>   the type of the {@link AutoCloseable} resource
     * @param stage the stage producing the resource
     * @param fn    the asynchronous function to apply to the resource
     * @return a promise that completes with the result of {@code fn} after the resource is closed
     */
    public static <T, R extends AutoCloseable> Promise<T> tryCompose(CompletionStage<R> stage,
                                                                     Function<? super R, ? extends CompletionStage<T>> fn) {
        return tryCompose(from(stage), fn);
    }

    /**
     * Emulates a {@code try-with-resources} block for an <i>asynchronous</i> function applied 
     * to an {@link AutoCloseable} resource delivered by a {@link Promise}.
     *
     * @param <T> the result type of the asynchronous function
     * @param <R> the type of the {@link AutoCloseable} resource
     * @param p   the promise producing the resource
     * @param fn  the asynchronous function to apply to the resource
     * @return a promise that completes with the result of {@code fn} after the resource is closed
     */
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
    
    /**
     * Emulates a {@code try-with-resources} block for an <i>asynchronous</i> function applied 
     * to an {@link AsyncCloseable} resource delivered by a {@link CompletionStage}.
     * <p>
     * This is the fully asynchronous variant: both the operation on the resource (via {@code fn}) 
     * and the release of the resource (via {@code close()}) are asynchronous. The resource is 
     * closed only after the stage returned by {@code fn} completes, and the final promise 
     * completes only after the asynchronous close operation finishes.
     * </p>
     *
     * @param <T>   the result type of the asynchronous function
     * @param <R>   the type of the {@link AsyncCloseable} resource
     * @param stage the stage producing the resource
     * @param fn    the asynchronous function to apply to the resource
     * @return a promise that completes with the result of {@code fn} after the resource is asynchronously closed
     */    
    public static <T, R extends AsyncCloseable> Promise<T> tryComposeEx(CompletionStage<R> stage,
                                                                        Function<? super R, ? extends CompletionStage<T>> fn) {
        return tryComposeEx(from(stage), fn);
    }
    
    /**
     * Emulates a {@code try-with-resources} block for an <i>asynchronous</i> function applied 
     * to an {@link AsyncCloseable} resource delivered by a {@link Promise}.
     *
     * @param <T> the result type of the asynchronous function
     * @param <R> the type of the {@link AsyncCloseable} resource
     * @param p   the promise producing the resource
     * @param fn  the asynchronous function to apply to the resource
     * @return a promise that completes with the result of {@code fn} after the resource is asynchronously closed
     */    
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
    
    /**
     * Processes the given values <b>chunk by chunk</b>, spawning one asynchronous task per
     * element and collecting all results with the {@code downstream} {@link Collector} —
     * an asynchronous, concurrency-bounded analogue of
     * {@link java.util.stream.Stream#collect(java.util.stream.Collector)}.
     *
     * <h2>Execution model</h2>
     * <ul>
     *   <li>Values are drained from the source <b>lazily</b>, at most {@code chunkSize}
     *       elements at a time; subsequent chunks are not pulled until the current chunk
     *       completes. The number of simultaneously in-flight tasks never exceeds
     *       {@code chunkSize}.</li>
     *   <li>Within a chunk the {@code spawner} tasks run <b>concurrently</b> (as if combined
     *       with {@link #all(CompletionStage...)}); chunks themselves are processed
     *       <b>sequentially</b>.</li>
     *   <li>Every chunk's results are accumulated in a fresh collector container and merged
     *       into the running result via the collector's {@code combiner}. Chunk order — and,
     *       within a chunk, the source order — is preserved, so ordered collectors produce
     *       results in encounter order.</li>
     *   <li>An empty source completes successfully with the collector's empty result.</li>
     * </ul>
     *
     * <h2>Failure and cancellation</h2>
     * <p>
     * If any spawned task fails, the returned promise fails as well and the remaining
     * in-flight tasks of the current chunk are cancelled. Cancelling the returned promise
     * interrupts the processing loop; no further chunks are started.
     * </p>
     *
     * @param <S>        the source element type
     * @param <T>        the type of the spawned tasks' results
     * @param <A>        the collector's intermediate accumulation type
     * @param <R>        the final result type
     * @param values     the elements to process
     * @param chunkSize  the maximum number of concurrently running tasks; must be positive
     * @param spawner    a function producing an asynchronous task for each element
     * @param downstream the collector used to accumulate and finish the results; its
     *                   {@code combiner} must be correctly implemented
     * @return a promise of the collected result
     * @see #forEach(Iterable, Function)
     * @see #all(CompletionStage...)
     */    
    public static <S, T, A, R> Promise<R> chunked(Iterable<? extends S> values, 
                                                  int chunkSize, 
                                                  Function<? super S, CompletionStage<? extends T>> spawner, 
                                                  Collector<T, A, R> downstream) {
        return chunked1(values.iterator(), null, chunkSize, spawner, downstream);
    }
    
    /**
     * Variant of {@link #chunked(Iterable, int, Function, Collector)} that executes the
     * collector's {@code supplier}, {@code accumulator}, {@code combiner} and {@code finisher}
     * on the given {@code downstreamExecutor} instead of the threads that complete the
     * spawned tasks.
     * <p>
     * Use this overload to offload expensive accumulation from I/O threads, or to confine
     * accumulation to a specific thread (e.g., an event/UI thread).
     * </p>
     *
     * @param <S>               the source element type
     * @param <T>               the type of the spawned tasks' results
     * @param <A>               the collector's intermediate accumulation type
     * @param <R>               the final result type
     * @param values            the elements to process
     * @param chunkSize         the maximum number of concurrently running tasks; must be positive
     * @param spawner           a function producing an asynchronous task for each element
     * @param downstream        the collector used to accumulate and finish the results; its
     *                          {@code combiner} must be correctly implemented
     * @param downstreamExecutor the executor for all collector invocations
     * @return a promise of the collected result
     */    
    public static <S, T, A, R> Promise<R> chunked(Iterable<? extends S> values, 
                                                  int chunkSize, 
                                                  Function<? super S, CompletionStage<? extends T>> spawner, 
                                                  Collector<T, A, R> downstream,
                                                  Executor downstreamExecutor) {
        return chunked2(values.iterator(), null, chunkSize, spawner, downstream, downstreamExecutor);
    }
    
    /**
     * Variant of {@link #chunked(Iterable, int, Function, Collector)} accepting a
     * {@link Stream} source.
     * <p>
     * The stream is consumed <b>lazily</b>, chunk by chunk, and is
     * <b>closed automatically</b> once processing completes — successfully or
     * exceptionally. Do not close the stream yourself; any exception thrown by
     * {@code Stream.close()} is attached as a suppressed exception to the failure.
     * </p>
     *
     * @param <S>        the source element type
     * @param <T>        the type of the spawned tasks' results
     * @param <A>        the collector's intermediate accumulation type
     * @param <R>        the final result type
     * @param values     the stream of elements to process; closed automatically on completion
     * @param chunkSize  the maximum number of concurrently running tasks; must be positive
     * @param spawner    a function producing an asynchronous task for each element
     * @param downstream the collector used to accumulate and finish the results; its
     *                   {@code combiner} must be correctly implemented
     * @return a promise of the collected result
     */    
    public static <S, T, A, R> Promise<R> chunked(Stream<? extends S> values, 
                                                  int chunkSize, 
                                                  Function<? super S, CompletionStage<? extends T>> spawner, 
                                                  Collector<T, A, R> downstream) {
        return chunked1(values.iterator(), values, chunkSize, spawner, downstream);
    }
    
    /**
     * Variant of {@link #chunked(Iterable, int, Function, Collector, Executor)} accepting a
     * {@link Stream} source. The stream is consumed lazily and closed automatically once
     * processing completes; collector invocations run on the given {@code downstreamExecutor}.
     *
     * @param <S>               the source element type
     * @param <T>               the type of the spawned tasks' results
     * @param <A>               the collector's intermediate accumulation type
     * @param <R>               the final result type
     * @param values            the stream of elements to process; closed automatically on completion
     * @param chunkSize         the maximum number of concurrently running tasks; must be positive
     * @param spawner           a function producing an asynchronous task for each element
     * @param downstream        the collector used to accumulate and finish the results; its
     *                          {@code combiner} must be correctly implemented
     * @param downstreamExecutor the executor for all collector invocations
     * @return a promise of the collected result
     */    
    public static <S, T, A, R> Promise<R> chunked(Stream<? extends S> values, 
                                                  int chunkSize, 
                                                  Function<? super S, CompletionStage<? extends T>> spawner, 
                                                  Collector<T, A, R> downstream,
                                                  Executor downstreamExecutor) {
        return chunked2(values.iterator(), values, chunkSize, spawner, downstream, downstreamExecutor);
    }
    
    /** Sequential-chunk implementation without a downstream executor; {@code source} (when non-null)
     *  is the {@code AutoCloseable} stream to close on completion. 
     */    
    private static <S, T, A, R> Promise<R> chunked1(Iterator<? extends S> values, 
                                                    Object source,
                                                    int chunkSize, 
                                                    Function<? super S, CompletionStage<? extends T>> spawner, 
                                                    Collector<T, A, R> downstream) {
        return
            parallelStep1(values, chunkSize, spawner, downstream)
            .dependent()
            .thenApply(downstream.finisher().compose(IndexedStep::payload), true)
            .asʹ(maybeClosingSource(null != source? source : values))
            .unwrap();
    }

    /** Chunked implementation that confines collector invocations to {@code downstreamExecutor}. 
     */
    private static <S, T, A, R> Promise<R> chunked2(Iterator<? extends S> values, 
                                                    Object source,
                                                    int chunkSize, 
                                                    Function<? super S, CompletionStage<? extends T>> spawner, 
                                                    Collector<T, A, R> downstream,
                                                    Executor downstreamExecutor) {
        return 
            parallelStep2(values, chunkSize, spawner, downstream, downstreamExecutor)
            .dependent()
            .thenApplyAsync(downstream.finisher().compose(IndexedStep::payload), downstreamExecutor, true)
            .asʹ(maybeClosingSource(null != source? source : values))
            .unwrap();
    }
    
    /** One loop step: drains a batch, spawns its tasks concurrently and folds the results
     *  into the running accumulation carried by {@link IndexedStep}. */    
    private static <S, T, A, R> Promise<IndexedStep<A>> parallelStep1(
        Iterator<? extends S> values, int chunkSize,
        Function<? super S, CompletionStage<? extends T>> spawner,                                                        
        Collector<T, A, R> downstream) {

        return loop(new IndexedStep<>(), step -> step.initial() || values.hasNext(), step -> {
            List<S> valuesBatch = drainBatch(values, chunkSize);
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
        Iterator<? extends S> values, int chunkSize,
        Function<? super S, CompletionStage<? extends T>> spawner,                                                        
        Collector<T, A, R> downstream,
        Executor downstreamExecutor) {

        return loop(new IndexedStep<>(), step -> step.initial() || values.hasNext(), step -> {
            List<S> valuesBatch = drainBatch(values, chunkSize);
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
    
    /** Loop state: the ordinal of the processed chunk (0 = not started yet) and the current
     *  collector accumulation. {@code initial()} being part of the loop condition guarantees
     *  one iteration even for an empty source. */ 
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
    
    /** Pulls at most {@code chunkSize} elements from the iterator into a list. */
    private static <T> List<T> drainBatch(Iterator<? extends T> values, int chunkSize) {
        List<T> valuesBatch = new ArrayList<>(chunkSize);
        for (int count = 0; values.hasNext() && count < chunkSize; count++) {
            valuesBatch.add(values.next());
        }        
        return valuesBatch;
    }
    
    /** Accumulates one chunk's results into a fresh collector container and merges it with
     *  the running accumulation via the collector's combiner. */
    private static <T, A, R> A accumulate(List<T> vals, boolean initial, A current, Collector<T, A, R> downstream) {
        A insertion = downstream.supplier().get();
        vals.stream()
            .forEach(v -> downstream.accumulator().accept(insertion, v));
        
        return initial ? insertion : downstream.combiner().apply(current, insertion);
    }
    
    /** Returns a decorator that closes an {@code AutoCloseable} source when the promise completes,
     *  suppressing any close exception onto the failure, or identity when there is nothing to close. */
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
     * Returns a promise that is completed successfully when <b>all</b> of the given stages 
     * complete normally. 
     * <p>
     * If any stage completes exceptionally, the resulting promise fails with a 
     * {@link MultitargetException} containing the failure(s). By default, all remaining 
     * incomplete stages are cancelled upon the first failure.
     * </p>
     * <p>
     * The successful result is a {@link List} containing the results of all stages in the 
     * exact order they were provided.
     * </p>
     *
     * @param <T>      the common supertype of the resulting values
     * @param promises the stages to combine
     * @return a combined promise yielding a list of results
     */
    @SafeVarargs
    public static <T> Promise<List<T>> all(CompletionStage<? extends T>... promises) {
        return all(Arrays.asList(promises));
    }
    
    /**
     * List-based variant of {@link #all(CompletionStage...)}.
     *
     * @param <T>      the common supertype of the resulting values
     * @param promises the list of stages to combine
     * @return a combined promise yielding a list of results
     */
    public static <T> Promise<List<T>> all(List<? extends CompletionStage<? extends T>> promises) {
        return all(true, promises);        
    }
    
    /**
     * Map-based variant of {@link #all(CompletionStage...)}.
     * <p>
     * The successful result is a {@link Map} containing the successfully completed entries.
     * If any stage fails, the resulting promise fails and the map is not produced.
     * </p>
     *
     * @param <K>      the type of the map keys
     * @param <T>      the common supertype of the resulting values
     * @param promises the map of stages to combine
     * @return a combined promise yielding a map of results
     */
    public static <K, T> Promise<Map<K, T>> all(Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return all(true, promises);
    }
    
    /**
     * Returns a promise that is completed successfully when <b>all</b> of the given stages 
     * complete normally.
     * <p>
     * If any stage completes exceptionally, the resulting promise fails with a 
     * {@link MultitargetException}.
     * </p>
     *
     * @param <T>             the common supertype of the resulting values
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        when the first failure occurs
     * @param promises        the stages to combine
     * @return a combined promise yielding a list of results
     */
    @SafeVarargs
    public static <T> Promise<List<T>> all(boolean cancelRemaining, CompletionStage<? extends T>... promises) {
        return all(cancelRemaining, Arrays.asList(promises));
    }

    /**
     * List-based variant of {@link #all(boolean, CompletionStage...)}.
     *
     * @param <T>             the common supertype of the resulting values
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        when the first failure occurs
     * @param promises        the list of stages to combine
     * @return a combined promise yielding a list of results
     */
    public static <T> Promise<List<T>> all(boolean cancelRemaining, List<? extends CompletionStage<? extends T>> promises) {
        return atLeast(null != promises ? promises.size() : 0, 0, cancelRemaining, promises);
    }
    
    /**
     * Map-based variant of {@link #all(boolean, CompletionStage...)}.
     *
     * @param <K>             the type of the map keys
     * @param <T>             the common supertype of the resulting values
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        when the first failure occurs
     * @param promises        the map of stages to combine
     * @return a combined promise yielding a map of results
     */    
    public static <K, T> Promise<Map<K, T>> all(boolean cancelRemaining, 
                                                Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return atLeast(null == promises ? 0 : promises.size(), 0, cancelRemaining, promises);
    }

    /**
     * Returns a promise that is completed successfully when <b>any</b> (at least one) of the 
     * given stages completes normally.
     * <p>
     * This is a <i>non-strict</i> combinator: exceptions from individual stages are ignored 
     * as long as at least one stage succeeds. The resulting promise only fails if <b>all</b> 
     * stages complete exceptionally, in which case it fails with a {@link MultitargetException}.
     * By default, all remaining incomplete stages are cancelled upon the first success.
     * </p>
     *
     * @param <T>      the common supertype of the resulting values
     * @param promises the stages to combine
     * @return a combined promise yielding the first successful result
     */
    @SafeVarargs
    public static <T> Promise<T> any(CompletionStage<? extends T>... promises) {
        return any(Arrays.asList(promises));
    }

    /**
     * List-based variant of {@link #any(CompletionStage...)}.
     *
     * @param <T>      the common supertype of the resulting values
     * @param promises the list of stages to combine
     * @return a combined promise yielding the first successful result
     */
    public static <T> Promise<T> any(List<? extends CompletionStage<? extends T>> promises) {
        return any(true, promises);
    }

    /**
     * Map-based variant of {@link #any(CompletionStage...)}.
     * <p>
     * The successful result is a {@link Map} containing the single successfully completed entry 
     * that finished first.
     * </p>
     *
     * @param <K>      the type of the map keys
     * @param <T>      the common supertype of the resulting values
     * @param promises the map of stages to combine
     * @return a combined promise yielding a map with the first successful entry
     */    
    public static <K, T> Promise<Map<K, T>> any(Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return any(true, promises);
    }
    
    /**
     * Returns a promise that is completed successfully when <b>any</b> (at least one) of the 
     * given stages completes normally.
     * <p>
     * This is a <i>non-strict</i> combinator: exceptions are tolerated as long as at least one 
     * stage succeeds. It fails with a {@link MultitargetException} only if all stages fail.
     * </p>
     *
     * @param <T>             the common supertype of the resulting values
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        when the first success occurs
     * @param promises        the stages to combine
     * @return a combined promise yielding the first successful result
     */
    @SafeVarargs
    public static <T> Promise<T> any(boolean cancelRemaining, CompletionStage<? extends T>... promises) {
        return any(cancelRemaining, Arrays.asList(promises));
    }
    
    /**
     * List-based variant of {@link #any(boolean, CompletionStage...)}.
     *
     * @param <T>             the common supertype of the resulting values
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        when the first success occurs
     * @param promises        the list of stages to combine
     * @return a combined promise yielding the first successful result
     */
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

    /**
     * Map-based variant of {@link #any(boolean, CompletionStage...)}.
     *
     * @param <K>             the type of the map keys
     * @param <T>             the common supertype of the resulting values
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        when the first success occurs
     * @param promises        the map of stages to combine
     * @return a combined promise yielding a map with the first successful entry
     */    
    public static <K, T> Promise<Map<K, T>> any(boolean cancelRemaining, 
                                                Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return atLeast(1, maxAllowedErrors(promises, 1), cancelRemaining, promises);
    }
    
    /**
     * Returns a promise that is completed successfully when <b>any</b> (at least one) of the 
     * given stages completes normally.
     * <p>
     * This is a <i>strict</i> combinator: if <b>any</b> stage completes exceptionally 
     * <i>before</i> the first successful result is available, the resulting promise immediately 
     * fails with that exact exception (not wrapped in a {@link MultitargetException}).
     * By default, all remaining incomplete stages are cancelled upon success or first failure.
     * </p>
     *
     * @param <T>      the common supertype of the resulting values
     * @param promises the stages to combine
     * @return a combined promise yielding the first successful result
     */
    @SafeVarargs
    public static <T> Promise<T> anyStrict(CompletionStage<? extends T>... promises) {
        return anyStrict(Arrays.asList(promises)); 
    }
    
    /**
     * List-based variant of {@link #anyStrict(CompletionStage...)}.
     *
     * @param <T>      the common supertype of the resulting values
     * @param promises the list of stages to combine
     * @return a combined promise yielding the first successful result
     */    
    public static <T> Promise<T> anyStrict(List<? extends CompletionStage<? extends T>> promises) {
        return anyStrict(true, promises);        
    }

    /**
     * Map-based variant of {@link #anyStrict(CompletionStage...)}.
     *
     * @param <K>      the type of the map keys
     * @param <T>      the common supertype of the resulting values
     * @param promises the map of stages to combine
     * @return a combined promise yielding a map with the first successful entry
     */    
    public static <K, T> Promise<Map<K, T>> anyStrict(Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return anyStrict(true, promises);
    }    

    /**
     * Returns a promise that is completed successfully when <b>any</b> (at least one) of the 
     * given stages completes normally.
     * <p>
     * This is a <i>strict</i> combinator: fails immediately with the exact exception if any 
     * stage fails before a success is recorded.
     * </p>
     *
     * @param <T>             the common supertype of the resulting values
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        when the outcome (success or failure) is determined
     * @param promises        the stages to combine
     * @return a combined promise yielding the first successful result
     */
    @SafeVarargs
    public static <T> Promise<T> anyStrict(boolean cancelRemaining, CompletionStage<? extends T>... promises) {
        return anyStrict(cancelRemaining, Arrays.asList(promises));
    }
    
    /**
     * List-based variant of {@link #anyStrict(boolean, CompletionStage...)}.
     *
     * @param <T>             the common supertype of the resulting values
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        when the outcome is determined
     * @param promises        the list of stages to combine
     * @return a combined promise yielding the first successful result
     */
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

    /**
     * Map-based variant of {@link #anyStrict(boolean, CompletionStage...)}.
     *
     * @param <K>             the type of the map keys
     * @param <T>             the common supertype of the resulting values
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        when the outcome is determined
     * @param promises        the map of stages to combine
     * @return a combined promise yielding a map with the first successful entry
     */    
    public static <K, T> Promise<Map<K, T>> anyStrict(boolean cancelRemaining, 
                                                      Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return atLeast(1, 0, cancelRemaining, promises);
    }
    
    /**
     * Returns a promise that completes successfully when at least {@code minResultsCount} 
     * of the given stages complete normally.
     * <p>
     * This is a <i>non-strict</i> combinator: it tolerates failures as long as the required 
     * number of successes is reached. It fails only if it becomes mathematically impossible 
     * to reach {@code minResultsCount}. By default, remaining stages are cancelled once the 
     * outcome is known.
     * </p>
     * <p>
     * The successful result is a {@link List} where successfully completed stages have their 
     * values at the corresponding indices, and failed or uncompleted stages have {@code null}.
     * </p>
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param promises        the stages to combine
     * @return a combined promise yielding a list of results
     */
    @SafeVarargs
    public static <T> Promise<List<T>> atLeast(int minResultsCount, CompletionStage<? extends T>... promises) {
        return atLeast(minResultsCount, Arrays.asList(promises));
    }
    
    /**
     * List-based variant of {@link #atLeast(int, CompletionStage...)}.
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param promises        the list of stages to combine
     * @return a combined promise yielding a list of results
     */
    public static <T> Promise<List<T>> atLeast(int minResultsCount, List<? extends CompletionStage<? extends T>> promises) {
        return atLeast(minResultsCount, true, promises);
    }
    
    /**
     * Similar to {@link #atLeast(int, CompletionStage...)}, but returns a {@code List<Optional<T>>}.
     * <p>
     * This variant is essential when a stage's successful result might legitimately be {@code null}, 
     * and you need to distinguish between a {@code null} result and a failed/uncompleted stage 
     * (which will be represented by an empty {@link Optional}).
     * </p>
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param promises        the stages to combine
     * @return a combined promise yielding a list of {@link Optional} results
     */
    @SafeVarargs
    public static <T> Promise<List<Optional<T>>> atLeastOrdered(int minResultsCount, CompletionStage<? extends T>... promises) {
        return atLeastOrdered(minResultsCount, Arrays.asList(promises));
    }    
    
    /**
     * List-based variant of {@link #atLeastOrdered(int, CompletionStage...)}.
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param promises        the list of stages to combine
     * @return a combined promise yielding a list of {@link Optional} results
     */
    public static <T> Promise<List<Optional<T>>> atLeastOrdered(int minResultsCount, List<? extends CompletionStage<? extends T>> promises) {
        return atLeastOrdered(minResultsCount, true, promises);
    }    
    
    /**
     * Map-based variant of {@link #atLeast(int, CompletionStage...)}.
     * <p>
     * The successful result is a {@link Map} containing up to {@code minResultsCount} successfully 
     * completed entries.
     * </p>
     *
     * @param <K>             the type of the map keys
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param promises        the map of stages to combine
     * @return a combined promise yielding a map of results
     */
    public static <K, T> Promise<Map<K, T>> atLeast(int minResultsCount, 
                                                    Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return atLeast(minResultsCount, true, promises);
    }    

    /**
     * Returns a promise that completes successfully when at least {@code minResultsCount} 
     * of the given stages complete normally.
     * <p>
     * This is a <i>non-strict</i> combinator.
     * </p>
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        once the outcome is known
     * @param promises        the stages to combine
     * @return a combined promise yielding a list of results
     */
    @SafeVarargs
    public static <T> Promise<List<T>> atLeast(int minResultsCount, boolean cancelRemaining, 
                                               CompletionStage<? extends T>... promises) {
        
        return atLeast(minResultsCount, cancelRemaining, Arrays.asList(promises));
    }
    
    /**
     * List-based variant of {@link #atLeast(int, boolean, CompletionStage...)}.
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        once the outcome is known
     * @param promises        the list of stages to combine
     * @return a combined promise yielding a list of results
     */
    public static <T> Promise<List<T>> atLeast(int minResultsCount, boolean cancelRemaining,
                                               List<? extends CompletionStage<? extends T>> promises) {
        return atLeast(minResultsCount, maxAllowedErrors(promises, minResultsCount), cancelRemaining, promises);
    }
    
    /**
     * List-based variant of {@link #atLeastOrdered(int, CompletionStage...)} with cancellation control.
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        once the outcome is known
     * @param promises        the stages to combine
     * @return a combined promise yielding a list of {@link Optional} results
     */
    @SafeVarargs
    public static <T> Promise<List<Optional<T>>> atLeastOrdered(int minResultsCount, boolean cancelRemaining, 
                                                                CompletionStage<? extends T>... promises) {
        
        return atLeastOrdered(minResultsCount, cancelRemaining, Arrays.asList(promises));
    }    
    
    /**
     * List-based variant of {@link #atLeastOrdered(int, boolean, CompletionStage...)}.
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        once the outcome is known
     * @param promises        the list of stages to combine
     * @return a combined promise yielding a list of {@link Optional} results
     */
    public static <T> Promise<List<Optional<T>>> atLeastOrdered(int minResultsCount, boolean cancelRemaining,
                                                                List<? extends CompletionStage<? extends T>> promises) {
        return atLeastOrdered(minResultsCount, maxAllowedErrors(promises, minResultsCount), cancelRemaining, promises);
    }
    
    /**
     * Map-based variant of {@link #atLeast(int, boolean, CompletionStage...)}.
     *
     * @param <K>             the type of the map keys
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        once the outcome is known
     * @param promises        the map of stages to combine
     * @return a combined promise yielding a map of results
     */    
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
     * Returns a promise that completes successfully when at least {@code minResultsCount} 
     * of the given stages complete normally.
     * <p>
     * This is a <i>strict</i> combinator: if <b>any</b> stage completes exceptionally 
     * <i>before</i> {@code minResultsCount} successful results are available, the resulting 
     * promise immediately fails with that exact exception. By default, remaining stages are 
     * cancelled once the outcome is known.
     * </p>
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param promises        the stages to combine
     * @return a combined promise yielding a list of results
     */
    @SafeVarargs
    public static <T> Promise<List<T>> atLeastStrict(int minResultsCount, CompletionStage<? extends T>... promises) {
        return atLeastStrict(minResultsCount, Arrays.asList(promises));
    }
    
    /**
     * List-based variant of {@link #atLeastStrict(int, CompletionStage...)}.
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param promises        the list of stages to combine
     * @return a combined promise yielding a list of results
     */
    public static <T> Promise<List<T>> atLeastStrict(int minResultsCount, 
                                                     List<? extends CompletionStage<? extends T>> promises) {
        return atLeastStrict(minResultsCount, true, promises);
    }
    
    /**
     * Similar to {@link #atLeastStrict(int, CompletionStage...)}, but returns a {@code List<Optional<T>>} 
     * to safely handle stages that legitimately return {@code null}.
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param promises        the stages to combine
     * @return a combined promise yielding a list of {@link Optional} results
     */
    @SafeVarargs
    public static <T> Promise<List<Optional<T>>> atLeastOrderedStrict(int minResultsCount, CompletionStage<? extends T>... promises) {
        return atLeastOrderedStrict(minResultsCount, Arrays.asList(promises));
    }
    
    /**
     * List-based variant of {@link #atLeastOrderedStrict(int, CompletionStage...)}.
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param promises        the list of stages to combine
     * @return a combined promise yielding a list of {@link Optional} results
     */    
    public static <T> Promise<List<Optional<T>>> atLeastOrderedStrict(int minResultsCount, 
                                                                      List<? extends CompletionStage<? extends T>> promises) {
        return atLeastOrderedStrict(minResultsCount, true, promises);
    }    
    
    /**
     * Map-based variant of {@link #atLeastStrict(int, CompletionStage...)}.
     *
     * @param <K>             the type of the map keys
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param promises        the map of stages to combine
     * @return a combined promise yielding a map of results
     */    
    public static <K, T> Promise<Map<K, T>> atLeastStrict(int minResultsCount, 
                                                          Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return atLeastStrict(minResultsCount, true, promises);
    }    

    /**
     * Returns a promise that completes successfully when at least {@code minResultsCount} 
     * of the given stages complete normally.
     * <p>
     * This is a <i>strict</i> combinator: fails immediately if any stage fails before the 
     * required number of successes is reached.
     * </p>
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        once the outcome is known
     * @param promises        the stages to combine
     * @return a combined promise yielding a list of results
     */
    @SafeVarargs
    public static <T> Promise<List<T>> atLeastStrict(int minResultsCount, boolean cancelRemaining, 
                                                     CompletionStage<? extends T>... promises) {
        return atLeast(minResultsCount, cancelRemaining, Arrays.asList(promises));
    }    
    
    /**
     * List-based variant of {@link #atLeastStrict(int, boolean, CompletionStage...)}.
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        once the outcome is known
     * @param promises        the list of stages to combine
     * @return a combined promise yielding a list of results
     */
    public static <T> Promise<List<T>> atLeastStrict(int minResultsCount, boolean cancelRemaining, 
                                                     List<? extends CompletionStage<? extends T>> promises) {
        return atLeast(minResultsCount, 0, cancelRemaining, promises);
    }
    
    /**
     * List-based variant of {@link #atLeastOrderedStrict(int, CompletionStage...)} with cancellation control.
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        once the outcome is known
     * @param promises        the stages to combine
     * @return a combined promise yielding a list of {@link Optional} results
     */
    @SafeVarargs
    public static <T> Promise<List<Optional<T>>> atLeastOrderedStrict(int minResultsCount, boolean cancelRemaining, 
                                                                      CompletionStage<? extends T>... promises) {
        return atLeastOrderedStrict(minResultsCount, cancelRemaining, Arrays.asList(promises));
    }  
    
    /**
     * List-based variant of {@link #atLeastOrderedStrict(int, boolean, CompletionStage...)}.
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        once the outcome is known
     * @param promises        the list of stages to combine
     * @return a combined promise yielding a list of {@link Optional} results
     */
    public static <T> Promise<List<Optional<T>>> atLeastOrderedStrict(int minResultsCount, boolean cancelRemaining, 
                                                                      List<? extends CompletionStage<? extends T>> promises) {
        return atLeastOrdered(minResultsCount, 0, cancelRemaining, promises);
    }    
    
    /**
     * Map-based variant of {@link #atLeastStrict(int, boolean, CompletionStage...)}.
     *
     * @param <K>             the type of the map keys
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param cancelRemaining if {@code true}, all remaining incomplete stages are cancelled 
     *                        once the outcome is known
     * @param promises        the map of stages to combine
     * @return a combined promise yielding a map of results
     */
    public static <K, T> Promise<Map<K, T>> atLeastStrict(int minResultsCount, boolean cancelRemaining, 
                                                          Map<? extends K, ? extends CompletionStage<? extends T>> promises) {
        return atLeast(minResultsCount, 0, cancelRemaining, promises);
    }
    
    /**
     * The most general combinator: completes successfully when at least {@code minResultsCount} 
     * stages succeed, but fails immediately if {@code maxErrorsCount} stages fail 
     * <i>before</i> the required number of successes is reached.
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param maxErrorsCount  the maximum number of exceptional completions allowed before failing
     * @param cancelRemaining whether to cancel remaining stages once the outcome is known
     * @param promises        the stages to combine
     * @return a combined promise yielding a list of results
     */
    @SafeVarargs
    public static <T> Promise<List<T>> atLeast(int minResultsCount, int maxErrorsCount, boolean cancelRemaining, 
                                               CompletionStage<? extends T>... promises) {
        
        return atLeast(minResultsCount, maxErrorsCount, cancelRemaining, Arrays.asList(promises));
    }
    
    /**
     * List-based variant of {@link #atLeast(int, int, boolean, CompletionStage...)}.
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param maxErrorsCount  the maximum number of exceptional completions allowed before failing
     * @param cancelRemaining whether to cancel remaining stages once the outcome is known
     * @param promises        the list of stages to combine
     * @return a combined promise yielding a list of results
     */
    public static <T> Promise<List<T>> atLeast(int minResultsCount, int maxErrorsCount, boolean cancelRemaining, 
                                               List<? extends CompletionStage<? extends T>> promises) {
        return atLeast(
            minResultsCount, maxErrorsCount, cancelRemaining, 
            AggregatingPromise.newWithSuccessResults(), Collections::singletonList, 
            promises
        );
    }    
    
    /**
     * Similar to {@link #atLeast(int, int, boolean, CompletionStage...)}, but returns a 
     * {@code List<Optional<T>>} to safely handle stages that legitimately return {@code null}.
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param maxErrorsCount  the maximum number of exceptional completions allowed before failing
     * @param cancelRemaining whether to cancel remaining stages once the outcome is known
     * @param promises        the stages to combine
     * @return a combined promise yielding a list of {@link Optional} results
     */
    @SafeVarargs
    public static <T> Promise<List<Optional<T>>> atLeastOrdered(int minResultsCount, int maxErrorsCount, boolean cancelRemaining, 
                                                                CompletionStage<? extends T>... promises) {
        
        return atLeastOrdered(minResultsCount, maxErrorsCount, cancelRemaining, Arrays.asList(promises));
    }    
    
    /**
     * List-based variant of {@link #atLeastOrdered(int, int, boolean, CompletionStage...)}.
     *
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param maxErrorsCount  the maximum number of exceptional completions allowed before failing
     * @param cancelRemaining whether to cancel remaining stages once the outcome is known
     * @param promises        the list of stages to combine
     * @return a combined promise yielding a list of {@link Optional} results
     */
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
    
    /**
     * Map-based variant of {@link #atLeast(int, int, boolean, CompletionStage...)}.
     *
     * @param <K>             the type of the map keys
     * @param <T>             the common supertype of the resulting values
     * @param minResultsCount the minimum number of successful completions required
     * @param maxErrorsCount  the maximum number of exceptional completions allowed before failing
     * @param cancelRemaining whether to cancel remaining stages once the outcome is known
     * @param promises        the map of stages to combine
     * @return a combined promise yielding a map of results
     */
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
    
    /**
     * Executes the given context-free, result-less code block on the {@code executor}
     * and retries it according to the {@code retryPolicy} until it completes normally,
     * the retry budget is exhausted, or an abort condition matches.
     * <p>
     * Since the operation produces no result, the policy is forcibly switched to
     * {@link RetryPolicy#acceptNullResult()} — only thrown exceptions can drive retries.
     * </p>
     *
     * @param codeBlock   the operation to (re)execute on every attempt
     * @param executor    the executor on which the operation runs
     * @param retryPolicy the policy governing retries, backoff delays and per-attempt timeouts
     * @return a promise that completes when an attempt finally succeeds, or fails with a
     *         {@link RetryException} carrying the last error when the policy gives up
     */
    public static Promise<Void> retry(Runnable codeBlock, Executor executor, 
                                      RetryPolicy<? super Void> retryPolicy) {
        return retry(RetryRunnable.from(codeBlock), executor, retryPolicy);
    }

    /**
     * Executes the given <i>contextual</i>, result-less code block on the {@code executor}
     * and retries it according to the {@code retryPolicy}.
     * <p>
     * Unlike the {@link Runnable} overload, the block receives the current
     * {@link RetryContext} before every attempt and may adapt its behavior to the
     * attempt number, the duration, the result or the error of the previous attempt.
     * </p>
     *
     * @param codeBlock   the operation to (re)execute on every attempt
     * @param executor    the executor on which the operation runs
     * @param retryPolicy the policy governing retries, backoff delays and per-attempt timeouts
     * @return a promise that completes when an attempt finally succeeds, or fails with a
     *         {@link RetryException} carrying the last error when the policy gives up
     */
    public static Promise<Void> retry(RetryRunnable codeBlock, Executor executor, 
                                      RetryPolicy<? super Void> retryPolicy) {
        return retry(RetryCallable.from(codeBlock), executor, retryPolicy.acceptNullResult());
    }

    /**
     * Executes the given context-free code block on the {@code executor} and retries it
     * according to the {@code retryPolicy} until it returns a <i>valid</i> result,
     * the retry budget is exhausted, or an abort condition matches.
     * <p>
     * An attempt may fail in two ways: by throwing an exception, or by returning a result
     * rejected by the policy's result validator (see {@link RetryPolicy#withResultValidator(java.util.function.Predicate)},
     * {@link RetryPolicy#rejectNullResult()}). Both are subject to the retry rules.
     * </p>
     *
     * @param <T>         the result type
     * @param codeBlock   the operation to (re)execute on every attempt
     * @param executor    the executor on which the operation runs
     * @param retryPolicy the policy governing retries, backoff delays and per-attempt timeouts
     * @return a promise of the first valid result, failing with a {@link RetryException}
     *         when the policy gives up
     */
    public static <T> Promise<T> retry(Callable<T> codeBlock, Executor executor, 
                                       RetryPolicy<? super T> retryPolicy) {
        return retry(RetryCallable.from(codeBlock), executor, retryPolicy);
    }

    /**
     * Executes the given <i>contextual</i> code block on the {@code executor} and retries it
     * according to the {@code retryPolicy}.
     * <p>
     * The block receives the current {@link RetryContext} and may adapt per attempt.
     * The type parameter {@code C} is the result type observed by the policy; the block
     * may return any subtype {@code T} of it.
     * </p>
     *
     * @param <T>         the result type returned by the block
     * @param <C>         the result type tracked by the policy
     * @param codeBlock   the operation to (re)execute on every attempt
     * @param executor    the executor on which the operation runs
     * @param retryPolicy the policy governing retries, backoff delays and per-attempt timeouts
     * @return a promise of the first valid result, failing with a {@link RetryException}
     *         when the policy gives up
     */
    public static <T extends C, C> Promise<T> retry(RetryCallable<T, C> codeBlock, Executor executor, 
                                                    RetryPolicy<? super C> retryPolicy) {
        return retryFuture(
            (RetryContext<C> ctx) -> CompletableTask.submit(() -> codeBlock.call(ctx), executor), 
            retryPolicy
        );
    }

    /**
     * Executes the given code block returning an {@link Optional} and retries it according
     * to the {@code retryPolicy}.
     * <p>
     * An empty {@code Optional} is passed to the policy's result validator as a {@code null}
     * result. Combined with {@link RetryPolicy#rejectNullResult()} this turns
     * "no value yet" into a retryable condition — the idiomatic setup for polling loops.
     * </p>
     *
     * @param <T>         the value type inside the {@code Optional}
     * @param codeBlock   the operation to (re)execute on every attempt
     * @param executor    the executor on which the operation runs
     * @param retryPolicy the policy governing retries, backoff delays and per-attempt timeouts
     * @return a promise of the first non-empty result, failing with a {@link RetryException}
     *         when the policy gives up
     */
    public static <T> Promise<T> retryOptional(Callable<Optional<T>> codeBlock, Executor executor, 
                                               RetryPolicy<? super T> retryPolicy) {
        return retryOptional(RetryCallable.from(codeBlock), executor, retryPolicy);
    }

    /**
     * Contextual variant of {@link #retryOptional(Callable, Executor, RetryPolicy)}:
     * the block receives the current {@link RetryContext} and may adapt per attempt.
     *
     * @param <T>         the value type inside the {@code Optional}
     * @param <C>         the result type tracked by the policy
     * @param codeBlock   the operation to (re)execute on every attempt
     * @param executor    the executor on which the operation runs
     * @param retryPolicy the policy governing retries, backoff delays and per-attempt timeouts
     * @return a promise of the first non-empty result, failing with a {@link RetryException}
     *         when the policy gives up
     */
    public static <T extends C, C> Promise<T> retryOptional(RetryCallable<Optional<T>, C> codeBlock, Executor executor, 
                                                            RetryPolicy<? super C> retryPolicy) {
        // Need explicit type on lambda param
        return retry((RetryContext<C> ctx) -> codeBlock.call(ctx).orElse(null), executor, retryPolicy);
    }

    /**
     * Retries an <i>asynchronous</i> operation expressed as a factory of
     * {@link CompletionStage}s. No explicit {@link Executor} is required: the invoker is
     * called synchronously by the retry loop, while the asynchronous work happens inside
     * the returned stage on whatever executor the third-party API provides.
     * <p>
     * Both failure modes are retryable subject to the policy: an exception thrown by the
     * invoker itself, and an exceptional completion of the produced stage. The per-attempt
     * timeout of the policy applies to the produced stage.
     * </p>
     *
     * @param <T>         the result type of the asynchronous operation
     * @param invoker     a factory producing a fresh {@link CompletionStage} on every attempt
     * @param retryPolicy the policy governing retries, backoff delays and per-attempt timeouts
     * @return a promise of the first valid result, failing with a {@link RetryException}
     *         when the policy gives up
     */
    public static <T> Promise<T> retryFuture(Callable<? extends CompletionStage<T>> invoker, 
                                             RetryPolicy<? super T> retryPolicy) {
        return retryFuture(RetryCallable.from(invoker), retryPolicy);
    }

    /**
     * Contextual variant of {@link #retryFuture(Callable, RetryPolicy)}: the factory receives
     * the current {@link RetryContext} and may produce a different {@link CompletionStage}
     * on every attempt (e.g., adjust request parameters after a partial result).
     *
     * @param <T>           the result type of the asynchronous operation
     * @param <C>           the result type tracked by the policy
     * @param futureFactory a factory producing a fresh {@link CompletionStage} on every attempt
     * @param retryPolicy   the policy governing retries, backoff delays and per-attempt timeouts
     * @return a promise of the first valid result, failing with a {@link RetryException}
     *         when the policy gives up
     */
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
    
    /**
     * Shared singleton promise. Returns {@code null} as the received value.
     */
    private static final Promise<Object> PROMISE_NOTHING = Promises.success(null);
    
    /**
     * Shared singleton cancelled promise
     */
    private static final Promise<Object> PROMISE_CANCELED; 
    static {
        PROMISE_CANCELED = new CompletableFutureWrapper<>();
        PROMISE_CANCELED.cancel(true);
    }
}
