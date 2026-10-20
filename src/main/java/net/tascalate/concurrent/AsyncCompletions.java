/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
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

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A blocking, pull-based bridge that turns a sequence of {@link CompletionStage}s into an
 * {@link Iterator} &mdash; or, via the {@code stream(...)} factories, a sequential {@link Stream} &mdash;
 * of their results, allowing asynchronous producers to be consumed by ordinary synchronous
 * code: plain {@code for} loops, try-with-resources blocks, sequential stream pipelines.
 *
 * <h2>Blocking consumption</h2>
 * <p>
 * <b>Unlike all other combinators in this library</b> ({@code all}, {@code any},
 * {@code atLeast}, {@code chunked}, {@code forEach}, {@code loop}), which are non-blocking
 * and return {@link Promise}s, {@code AsyncCompletions} expects <b>blocking consumption</b>:
 * {@link #next()} blocks the calling thread until the next result settles. Never drive it
 * from an event-loop, virtual thread pool, or otherwise non-blocking thread.
 * </p>
 *
 * <h2>Key characteristics</h2>
 * <ul>
 *   <li><b>Completion order:</b> results are delivered in the order stages <em>complete</em>,
 *       not in source order. Use {@code Promises.all} / {@code Promises.chunked} when
 *       positional order matters.</li>
 *   <li><b>Fail-fast errors:</b> an exceptionally completed stage causes {@link #next()} to
 *       throw its exception at the moment of consumption; iteration stops there.</li>
 *   <li><b>Bounded concurrency (backpressure):</b> at most {@code chunkSize} stages are
 *       <i>enlisted</i> at once. With {@code chunkSize > 0} the outcome queue is bounded,
 *       so completion callbacks may themselves block on enqueueing while the consumer is
 *       slow, providing backpressure to fast producers. {@code chunkSize <= 0} means unlimited.</li>
 *   <li><b>Cancellation on close:</b> {@link #close()} &mdash; or closing a stream obtained from
 *       {@code stream(...)} &mdash; stops the sequence and cancels outstanding work according to
 *       the configured {@link Cancel} policy.</li>
 * </ul>
 *
 * <h2>Threading</h2>
 * <p>
 * Single-consumer design: {@link #hasNext()} / {@link #next()} must be driven by exactly one
 * thread (the "main" thread controlling the iteration). Completion callbacks may run on
 * arbitrary threads; they only enqueue outcomes into a thread-safe queue.
 * </p>
 *
 * <pre>{@code
 * try (Stream<HttpResponse> responses = AsyncCompletions.stream(requests, 16)) {
 *     responses.forEach(r -> store(r)); // blocks as needed; at most 16 requests in flight
 * }                                     // in-flight requests cancelled on close (Cancel.ENLISTED)
 * }</pre>
 *
 * @param <T> the type of the stage results
 * @see Cancel
 */
public class AsyncCompletions<T> implements Iterator<T>, AutoCloseable {

    /**
     * The cancellation policy applied by {@link AsyncCompletions#close()}: defines which
     * stages to cancel once consumption stops.
     */
    public static enum Cancel {

        /** Cancel nothing; enlisted stages are left to complete on their own. */
        NONE {
            @Override
            void apply(Set<CompletionStage<?>> enlistedPromises, Iterator<? extends CompletionStage<?>> pendingPromises) {
            }
        },

        /** Cancel only the stages currently enlisted (submitted but not yet consumed). */
        ENLISTED {
            @Override
            void apply(Set<CompletionStage<?>> enlistedPromises, Iterator<? extends CompletionStage<?>> pendingPromises) {
                enlistedPromises.forEach(p -> AsyncInternals.cancelPromise(p, true));
            }
        },

        /** Cancel enlisted stages <em>and</em> every pending stage not yet enlisted. */
        ALL {
            @Override
            void apply(Set<CompletionStage<?>> enlistedPromises, Iterator<? extends CompletionStage<?>> pendingPromises) {
                ENLISTED.apply(enlistedPromises, pendingPromises);
                while (pendingPromises.hasNext()) {
                    CompletionStage<?> nextPromise = pendingPromises.next();
                    AsyncInternals.cancelPromise(nextPromise, true);
                }
            }            
        };

        /**
         * Applies this cancellation policy.
         *
         * @param enlistedPromises stages currently in flight
         * @param pendingPromises  stages not yet enlisted
         */
        abstract void apply(Set<CompletionStage<?>> enlistedPromises, Iterator<? extends CompletionStage<?>> pendingPromises);
    }

    /** Maximum number of simultaneously enlisted stages; {@code <= 0} means unlimited. */
    private final int chunkSize;

    /** The cancellation policy applied by {@link #close()}. */
    private final Cancel cancelPolicy;

    /** Not-yet-enlisted stages, pulled lazily. */
    private final Iterator<? extends CompletionStage<? extends T>> pendingPromises;

    /** Outcomes of completed stages awaiting consumption; bounded when {@code chunkSize > 0}. */
    private final BlockingQueue<Try<T>> settledResults;

    /** Stages currently enlisted (callbacks registered) but not yet consumed. */
    private final Set<CompletionStage<?>> enlistedPromises;

    /**
     * Number of enlisted-but-not-yet-consumed stages.
     * <p>
     * The sentinel {@code Integer.MIN_VALUE} marks a forcibly closed sequence.
     * <b>Must be accessed from a single thread</b> &mdash; the "main" thread that controls
     * iteration via {@link #hasNext()} / {@link #next()}.
     * </p>
     */
    private int inProgress = 0;

    /**
     * Creates a sequence with the {@link Cancel#NONE} policy.
     *
     * @param pendingValues the stages to consume, pulled lazily
     * @param chunkSize     maximum number of simultaneously enlisted stages; {@code <= 0} for unlimited
     */
    AsyncCompletions(Iterator<? extends CompletionStage<? extends T>> pendingValues, int chunkSize) {
        this(pendingValues, chunkSize, Cancel.NONE);
    }

    /**
     * Creates a sequence with an explicit cancellation policy.
     *
     * @param pendingValues the stages to consume, pulled lazily
     * @param chunkSize     maximum number of simultaneously enlisted stages; {@code <= 0} for unlimited
     * @param cancelPolicy  the policy applied on {@link #close()}; {@code null} means {@link Cancel#NONE}
     */
    protected AsyncCompletions(Iterator<? extends CompletionStage<? extends T>> pendingValues, 
                                 int chunkSize, 
                                 Cancel cancelPolicy) {
        this.chunkSize       = chunkSize;
        this.cancelPolicy    = cancelPolicy == null ? Cancel.NONE : cancelPolicy;
        this.pendingPromises = pendingValues;
        this.settledResults  = chunkSize > 0 ? new LinkedBlockingQueue<>(chunkSize) 
                                             : new LinkedBlockingQueue<>(); 
        
        this.enlistedPromises = Collections.newSetFromMap(new ConcurrentHashMap<>()); 
    }

    /**
     * Returns {@code true} if more results are available or can still be produced.
     * <p>
     * Never blocks: it checks the queue of settled results and the number of in-flight
     * stages, and enlists more pending stages when needed. Note that it may return
     * {@code true} while no result has settled yet &mdash; the subsequent {@link #next()} then
     * blocks until one settles.
     * </p>
     *
     * @return {@code true} if the iteration can continue; always {@code false} after {@link #close()}
     */
    @Override
    public boolean hasNext() {
        if (inProgress < 0) {
            // Forcibly closed
            return false;
        } else {
            return !settledResults.isEmpty() || // There are some resolved results available
                   inProgress > 0            || // If we are still producing then there are more...
                   enlistPending();             // More was enlisted - then some available
        }
    }

    /**
     * Returns the next settled result, <b>blocking the calling thread</b> until a stage
     * completes if no settled result is available.
     * <p>
     * Consuming an exceptionally completed stage throws its exception here (fail-fast);
     * consuming a settled result frees a slot and may trigger enlistment of pending stages.
     * </p>
     *
     * @return the next result, in completion order
     * @throws NoSuchElementException if the sequence is exhausted or was {@linkplain #close() closed}
     * @throws CompletionException   if the consumed stage completed exceptionally (or the
     *                               waiting thread was interrupted)
     */
    @Override
    public T next() {
        while (true) {
            if (inProgress < 0) {
                // Forcibly closed
                throw new NoSuchElementException("This sequence was closed");
            } else {
                Try<T> settledResult = settledResults.poll();
                if (null != settledResult) {
                    // There are some resolved results available
                    inProgress--;
                    enlistPending();
                    return settledResult.done(); 
                } else {
                    if (inProgress > 0) {
                        // If we are still producing then await for any result...
                        try {
                            settledResult = settledResults.take();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt(); 
                            Try.failure(ex).done();
                        }
                        inProgress--;
                        enlistPending();
                        return settledResult.done();
                    } else {
                        if (enlistPending()) {
                            // More was enlisted
                            continue; //recursion via loop
                        } else {
                            // ...or stop when over
                            throw new NoSuchElementException();
                        }
                    }
                }
            }
        }
    }

    /**
     * Unsupported: elements cannot be removed from the underlying asynchronous sequence.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    /**
     * Terminates the sequence: marks it closed, applies the configured {@link Cancel} policy
     * to in-flight and pending stages, and discards all buffered results.
     * <p>
     * After this call {@link #hasNext()} returns {@code false} and {@link #next()} throws
     * {@link NoSuchElementException}. Idempotent; safe for try-with-resources.
     * </p>
     */
    @Override
    public void close() {
        inProgress = Integer.MIN_VALUE;
        cancelPolicy.apply(enlistedPromises, pendingPromises);
        enlistedPromises.clear();
        settledResults.clear();
    }

    /**
     * Creates a blocking iterator over the results of the given stream of stages, with the
     * {@link Cancel#NONE} policy and concurrency governed by {@code chunkSize}.
     *
     * @param pendingPromises the stages to consume, pulled lazily
     * @param chunkSize       maximum number of simultaneously enlisted stages; {@code <= 0} for unlimited
     * @param <T>             the result type
     * @return a blocking iterator delivering results in completion order
     */
    public static <T> Iterator<T> iterate(Stream<? extends CompletionStage<? extends T>> pendingPromises, 
                                          int chunkSize) {
        return iterate(pendingPromises.iterator(), chunkSize);
    }

    /**
     * Creates a blocking iterator over the results of the given iterable of stages, with the
     * {@link Cancel#NONE} policy and concurrency governed by {@code chunkSize}.
     *
     * @param pendingPromises the stages to consume, pulled lazily
     * @param chunkSize       maximum number of simultaneously enlisted stages; {@code <= 0} for unlimited
     * @param <T>             the result type
     * @return a blocking iterator delivering results in completion order
     */
    public static <T> Iterator<T> iterate(Iterable<? extends CompletionStage<? extends T>> pendingPromises, 
                                          int chunkSize) {
        return iterate(pendingPromises.iterator(), chunkSize);
    }

    private static <T> Iterator<T> iterate(Iterator<? extends CompletionStage<? extends T>> pendingPromises, 
                                           int chunkSize) {
        return new AsyncCompletions<>(pendingPromises, chunkSize);
    }

    /**
     * Creates a sequential, non-parallel {@link Stream} over the results of the given stream
     * of stages, delivered in completion order. The {@link Cancel#ENLISTED} policy is used by default.
     * <p>
     * The stream must be closed &mdash; preferably via try-with-resources &mdash; to release resources
     * and cancel outstanding work; closing the stream closes the underlying sequence.
     * </p>
     *
     * @param pendingPromises the stages to consume, pulled lazily
     * @param chunkSize       maximum number of simultaneously enlisted stages; {@code <= 0} for unlimited
     * @param <T>             the result type
     * @return a blocking sequential stream over the results
     */
    public static <T> Stream<T> stream(Stream<? extends CompletionStage<? extends T>> pendingPromises, 
                                       int chunkSize) {
        return stream(pendingPromises, chunkSize, Cancel.ENLISTED);
    }

    /**
     * Creates a sequential, non-parallel {@link Stream} over the results of the given stream
     * of stages, delivered in completion order, with an explicit cancellation policy.
     *
     * @param pendingPromises the stages to consume, pulled lazily
     * @param chunkSize       maximum number of simultaneously enlisted stages; {@code <= 0} for unlimited
     * @param cancelOption    the policy applied when the stream (and thus the sequence) is closed
     * @param <T>             the result type
     * @return a blocking sequential stream over the results
     */
    public static <T> Stream<T> stream(Stream<? extends CompletionStage<? extends T>> pendingPromises, 
                                       int chunkSize, Cancel cancelOption) {
        return stream(pendingPromises.iterator(), chunkSize, cancelOption);
    }

    /**
     * Creates a sequential, non-parallel {@link Stream} over the results of the given iterable
     * of stages, delivered in completion order. The {@link Cancel#ENLISTED} policy is used by default.
     *
     * @param pendingPromises the stages to consume, pulled lazily
     * @param chunkSize       maximum number of simultaneously enlisted stages; {@code <= 0} for unlimited
     * @param <T>             the result type
     * @return a blocking sequential stream over the results
     */
    public static <T> Stream<T> stream(Iterable<? extends CompletionStage<? extends T>> pendingPromises, 
                                       int chunkSize) {
        return stream(pendingPromises, chunkSize, Cancel.ENLISTED);
    }

    /**
     * Creates a sequential, non-parallel {@link Stream} over the results of the given iterable
     * of stages, delivered in completion order, with an explicit cancellation policy.
     *
     * @param pendingPromises the stages to consume, pulled lazily
     * @param chunkSize       maximum number of simultaneously enlisted stages; {@code <= 0} for unlimited
     * @param cancelOption    the policy applied when the stream (and thus the sequence) is closed
     * @param <T>             the result type
     * @return a blocking sequential stream over the results
     */
    public static <T> Stream<T> stream(Iterable<? extends CompletionStage<? extends T>> pendingPromises, 
                                       int chunkSize, Cancel cancelOption) {
        return stream(pendingPromises.iterator(), chunkSize, cancelOption);
    }

    private static <T> Stream<T> stream(Iterator<? extends CompletionStage<? extends T>> pendingPromises, 
                                        int chunkSize, Cancel cancelOption) {
        return toStream(new AsyncCompletions<>(pendingPromises, chunkSize, cancelOption));
    }

    /**
     * Wraps the sequence into a sequential stream whose {@code close()} closes the sequence
     * (applying its cancellation policy).
     */
    private static <T> Stream<T> toStream(AsyncCompletions<T> iterator) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.IMMUTABLE), false)
                            .onClose(iterator::close); 
    }

    /**
     * Pulls stages from the source iterator and enlists them (attaches completion callbacks)
     * until the concurrency cap ({@code chunkSize}) is reached, the source is exhausted,
     * or the sequence is closed.
     * <p>
     * The {@link #inProgress} counter is incremented <i>before</i> the callback is attached
     * to correctly handle stages that are already completed.
     * </p>
     *
     * @return {@code true} if at least one new stage was enlisted
     */
    private boolean enlistPending() {
        boolean enlisted = false;
        while (pendingPromises.hasNext()) {
            // +1 before setting completion handler -- 
            // while stage may be completed already
            // we should increment step-by-step 
            // instead of setting the value at once
            int currentInProgress = ++inProgress; 
            if (currentInProgress < 0) {
                break;
            }
            CompletionStage<? extends T> nextPromise = pendingPromises.next();
            enlistedPromises.add(nextPromise);
            nextPromise.whenComplete(enlistResolved(nextPromise));
            enlisted = true;
            if (chunkSize > 0 && currentInProgress >= chunkSize) {
                break;
            }
        };  
        return enlisted;
    }

    /**
     * Creates the completion callback for an enlisted stage.
     * <p>
     * When the stage completes, it is removed from the {@link #enlistedPromises} set,
     * and its outcome (success or failure) is enqueued into {@link #settledResults}.
     * With a bounded queue ({@code chunkSize > 0}) this call may block the completing
     * thread until the consumer frees a slot, providing backpressure to fast producers.
     * </p>
     *
     * @param promise the enlisted stage
     * @return the completion callback enqueuing the outcome
     */
    private BiConsumer<T, Throwable> enlistResolved(CompletionStage<? extends T> promise) {
        return (resolvedValue, ex) -> {
            enlistedPromises.remove(promise);
            try {
                if (ex == null) {
                    settledResults.put(Try.success(resolvedValue));
                } else {
                    settledResults.put(Try.failure(ex));
                }
            } catch (InterruptedException ie) {
                // Shouldn't happen for the queue with an unlimited size
                if (null != ex) {
                    ie.addSuppressed(ex);
                }
                Thread.currentThread().interrupt(); 
                throw new CompletionException(ie);
            }
        };
    }
}