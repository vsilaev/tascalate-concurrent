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

import static net.tascalate.concurrent.AsyncInternals.cancelPromise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Base implementation for promise aggregation combinators such as
 * {@link Promises#all}, {@link Promises#any}, and {@link Promises#atLeast}.
 * <p>
 * This class combines a set of source promises into a single promise that
 * produces a list of aggregated results. The aggregation semantics are
 * controlled by three parameters supplied at construction:
 * </p>
 * <ul>
 *   <li><b>{@code minResultsCount}</b> - the number of successful source
 *       completions required for the aggregated promise to succeed;</li>
 *   <li><b>{@code maxErrorsCount}</b> - the number of source failures
 *       tolerated before the aggregated promise fails;</li>
 *   <li><b>{@code cancelRemaining}</b> - whether source promises that are
 *       still pending at the moment of the final decision should be cancelled.</li>
 * </ul>
 *
 * <h2>Completion protocol</h2>
 * <p>
 * Each source promise is assigned a unique index. A per-index completion
 * tracker guarantees that every source contributes at most once, regardless
 * of how many times its completion callback fires. As sources complete, the
 * aggregator accumulates successful results and failures, and monitors two
 * terminal conditions:
 * </p>
 * <ol>
 *   <li>The required number of successful results has been reached;</li>
 *   <li>The maximum tolerated number of errors has been exceeded.</li>
 * </ol>
 * <p>
 * The first thread to observe either condition atomically marks the
 * aggregation as done, prevents further state changes, optionally cancels
 * the remaining pending sources, and completes the aggregated promise.
 * </p>
 *
 * <h2>Result collection strategy</h2>
 * <p>
 * The concrete way results are stored and collected is delegated to
 * subclasses through two abstract methods:
 * </p>
 * <ul>
 *   <li>{@link #applyResult(int, Object)} - invoked when a source completes
 *       successfully; subclasses decide whether to store, transform, or
 *       discard the value;</li>
 *   <li>{@link #collectResults(int, java.util.concurrent.atomic.AtomicIntegerArray)}
 *       - invoked when the aggregation succeeds; subclasses build the final
 *       list to be exposed to the caller.</li>
 * </ul>
 *
 * @param <T> the result type of each aggregated source promise
 * @param <R> the element type of the resulting aggregated list
 * @author vsilaev
 */
abstract class AggregatingPromise<T, R> extends CompletableFutureWrapper<List<R>> {

    /**
     * Updater for the {@link #resultsCount} field.
     */
    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<AggregatingPromise> RESULTS_COUNT_UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(AggregatingPromise.class, "resultsCount");

    /**
     * Updater for the {@link #errorsCount} field.
     */
    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<AggregatingPromise> ERRORS_COUNT_UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(AggregatingPromise.class, "errorsCount");

    /**
     * Updater for the {@link #done} flag.
     * <p>
     * The value {@code 0} means "not completed yet"; the value {@code 1}
     * means "the aggregation decision has already been made".
     * </p>
     */
    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<AggregatingPromise> DONE_UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(AggregatingPromise.class, "done");

    /**
     * Errors reported by source promises. The list is pre-sized to the number
     * of source promises; each error is written at the index of its source.
     * <p>
     * Writes are safe because each index can be completed only once due to the
     * {@code completions} CAS guard.
     * </p>
     */
    private final List<Throwable> errors;

    /**
     * Per-index completion state of each source promise.
     *
     * @see #PENDING
     * @see #COMPLETED_RESULT
     * @see #COMPLETED_ERROR
     * @see #COMPLETED_CANCEL
     */
    private final AtomicIntegerArray completions;

    /**
     * Number of successful results observed so far.
     */
    @SuppressWarnings("unused")
    private volatile int resultsCount;

    /**
     * Number of errors observed so far.
     */
    @SuppressWarnings("unused")
    private volatile int errorsCount;

    /**
     * Terminal aggregation flag: {@code 0} while the aggregator is still
     * collecting results, {@code 1} after the final decision has been made.
     */
    @SuppressWarnings("unused")
    private volatile int done;

    /**
     * Minimum number of successful results required for successful completion.
     */
    private final int minResultsCount;

    /**
     * Maximum number of errors tolerated before exceptional completion.
     */
    private final int maxErrorsCount;

    /**
     * Whether promises that are still pending at the decision point should be
     * cancelled.
     */
    private final boolean cancelRemaining;

    /**
     * The source promises being aggregated.
     */
    private final List<? extends CompletionStage<? extends T>> promises;

    /**
     * Factory interface used by {@link Promises} combinators to create concrete
     * aggregation strategies with different result-collection behavior.
     *
     * @param <T> the result type of each aggregated source promise
     * @param <R> the element type of the resulting aggregated list
     */
    @FunctionalInterface
    interface Constructor<T, R> {

        /**
         * Creates a concrete aggregator.
         *
         * @param minResultsCount required number of successful results
         * @param maxErrorsCount  tolerated number of errors
         * @param cancelRemaining whether to cancel remaining promises
         * @param promises        source promises to aggregate
         * @return a new {@code AggregatingPromise}
         */
        AggregatingPromise<T, R> create(int minResultsCount,
                                        int maxErrorsCount,
                                        boolean cancelRemaining,
                                        List<? extends CompletionStage<? extends T>> promises);
    }

    /**
     * Creates an aggregation strategy that preserves all source results as
     * {@link Optional} values.
     * <p>
     * This strategy is useful when the caller needs to inspect the complete
     * original layout of results, including absent values.
     * </p>
     *
     * @param <T> the result type of each aggregated source promise
     * @return a constructor for an optional-preserving aggregator
     */
    static <T> Constructor<T, Optional<T>> newWithAllResults() {
        return (minResultsCount, maxErrorsCount, cancelRemaining, promises) ->
            new AggregatingPromise<T, Optional<T>>(minResultsCount, maxErrorsCount, cancelRemaining, promises) {

                /** Result slots, one per source promise. */
                private List<Optional<T>> results = newList(promises.size());

                /** {@inheritDoc} */
                @Override
                void applyResult(int idx, T value) {
                    results.set(idx, Optional.ofNullable(value));
                }

                /** {@inheritDoc} */
                @Override
                List<Optional<T>> collectResults(int resultsCount, AtomicIntegerArray completions) {
                    return Collections.unmodifiableList(results);
                }
            };
    }

    /**
     * Creates an aggregation strategy that collects only successful source
     * results.
     * <p>
     * The resulting list preserves the order of the original source promises,
     * but contains only values from successfully completed promises.
     * </p>
     *
     * @param <T> the result type of each aggregated source promise
     * @return a constructor for a success-only aggregator
     */
    static <T> Constructor<T, T> newWithSuccessResults() {
        return (minResultsCount, maxErrorsCount, cancelRemaining, promises) ->
            new AggregatingPromise<T, T>(minResultsCount, maxErrorsCount, cancelRemaining, promises) {

                /** Result slots, one per source promise. */
                private List<T> results = newList(promises.size());

                /** {@inheritDoc} */
                @Override
                void applyResult(int idx, T value) {
                    results.set(idx, value);
                }

                /**
                 * {@inheritDoc}
                 * <p>
                 * Scans the completion states and collects only values whose
                 * source completed with a result.
                 * </p>
                 */
                @Override
                List<T> collectResults(int resultsCount, AtomicIntegerArray completions) {
                    List<T> collectedResult = new ArrayList<>(resultsCount);
                    for (int k = 0, size = completions.length(); k < size; k++) {
                        if (completions.get(k) == COMPLETED_RESULT) {
                            collectedResult.add(results.get(k));
                        }
                    }
                    return Collections.unmodifiableList(collectedResult);
                }
            };
    }

    /**
     * Creates an aggregation strategy that discards successful results and
     * returns an empty list.
     * <p>
     * This is useful for combinators that only care about the fact that the
     * required number of stages has completed, but do not need their values.
     * </p>
     *
     * @param <T> the result type of each aggregated source promise
     * @return a constructor for an empty-result aggregator
     */
    static <T> Constructor<T, Void> newWithEmptyResults() {
        return (minResultsCount, maxErrorsCount, cancelRemaining, promises) ->
            new AggregatingPromise<T, Void>(minResultsCount, maxErrorsCount, cancelRemaining, promises) {

                /** Results are not stored. */
                @Override
                void applyResult(int idx, T value) {
                    // Intentionally empty: results are discarded.
                }

                /** {@inheritDoc} */
                @Override
                List<Void> collectResults(int resultsCount, AtomicIntegerArray completions) {
                    return Collections.emptyList();
                }
            };
    }

    /**
     * Creates a new aggregator.
     *
     * @param minResultsCount required number of successful results; if negative,
     *                        all source promises must succeed
     * @param maxErrorsCount  tolerated number of errors; if negative, all errors
     *                        that do not prevent reaching {@code minResultsCount}
     *                        are tolerated
     * @param cancelRemaining whether to cancel remaining promises when the
     *                        aggregation decision is reached
     * @param promises        source promises to aggregate; must contain at least
     *                        one element
     * @throws IllegalArgumentException if {@code promises} is {@code null} or empty
     */
    AggregatingPromise(int minResultsCount,
                       int maxErrorsCount,
                       boolean cancelRemaining,
                       List<? extends CompletionStage<? extends T>> promises) {

        if (null == promises || promises.isEmpty()) {
            throw new IllegalArgumentException("There are should be at least one promise specified");
        }

        int size = promises.size();

        this.promises = promises;

        this.minResultsCount = minResultsCount < 0
            ? size
            : Math.max(1, Math.min(size, minResultsCount));

        this.maxErrorsCount = maxErrorsCount < 0
            ? promises.size() - this.minResultsCount
            : Math.max(0, Math.min(maxErrorsCount, size - this.minResultsCount));

        this.cancelRemaining = cancelRemaining;
        this.errors = newList(size);
        this.completions = new AtomicIntegerArray(size);
    }

    /**
     * Stores or otherwise processes a successful result for the source promise
     * at the given index.
     * <p>
     * Implementations may store the value, transform it, or ignore it depending
     * on the aggregation strategy.
     * </p>
     *
     * @param idx   index of the source promise
     * @param value successful result of the source promise
     */
    abstract void applyResult(int idx, T value);

    /**
     * Collects the final aggregated result list after successful completion.
     *
     * @param resultsCount number of successful results observed
     * @param completions  per-index completion states
     * @return the final list exposed by the aggregator
     */
    abstract List<R> collectResults(int resultsCount, AtomicIntegerArray completions);

    /**
     * {@inheritDoc}
     * <p>
     * Cancels the aggregator and all source promises that were still pending at
     * the moment of cancellation. If the aggregation decision has already been
     * made, cancellation is rejected by returning {@code false}.
     * </p>
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (DONE_UPDATER.compareAndSet(this, 0, 1)) {
            // Synchronized around done: only one thread may perform cancellation.
            markRemainingCancelled();
            cancelPromises();
            return super.cancel(mayInterruptIfRunning);
        } else {
            return false;
        }
    }

    /**
     * Completion callback invoked when one of the source promises finishes.
     * <p>
     * This method is idempotent with respect to the source index: the CAS on
     * {@code completions} guarantees that only the first completion event for a
     * given source index is processed.
     * </p>
     *
     * @param idx    index of the completed source promise
     * @param result successful result, or {@code null} if the source failed
     * @param error  failure exception, or {@code null} if the source succeeded
     */
    void onComplete(int idx, T result, Throwable error) {
        if (!completions.compareAndSet(idx, PENDING, null == error ? COMPLETED_RESULT : COMPLETED_ERROR)) {
            // Already completed.
            return;
        }

        if (null == error) {
            // ON NEXT RESULT
            int c = RESULTS_COUNT_UPDATER.incrementAndGet(this);

            if (c <= minResultsCount) {
                // Only one thread may access this index due to the
                // "completions" CAS guard above.
                applyResult(idx, result);

                if (c == minResultsCount && DONE_UPDATER.compareAndSet(this, 0, 1)) {
                    // Synchronized around done.
                    markRemainingCancelled();

                    // Now no other thread can modify the results array.
                    // Cancel before firing events: this helps with releasing
                    // thread pools, throttling, etc.
                    if (cancelRemaining) {
                        cancelPromises();
                    }

                    success(collectResults(minResultsCount, completions));
                }
            }
        } else {
            // ON NEXT ERROR
            int c = ERRORS_COUNT_UPDATER.getAndIncrement(this);

            // We are reporting maxErrorsCount + 1 exceptions, so if the caller
            // specifies that no exceptions should happen, we will report at
            // least one.
            if (c <= maxErrorsCount) {
                // Only one thread may access this index due to the
                // "completions" CAS guard above.
                errors.set(idx, error);

                if (c == maxErrorsCount && DONE_UPDATER.compareAndSet(this, 0, 1)) {
                    // Synchronized around done.
                    markRemainingCancelled();

                    // Now no other thread can modify the errors array.
                    // Cancel before firing events: this helps with releasing
                    // thread pools, throttling, etc.
                    if (cancelRemaining) {
                        cancelPromises();
                    }

                    // Because we used errorsCount.getAndIncrement() rather
                    // than incrementAndGet().
                    c++;

                    failure(new MultitargetException(
                        String.format(
                            c == 1
                                ? "Aggregated promise was completed exceptionally (1 out of %d)"
                                : "Aggregated promises were completed exceptionally (%2$d out of %1$d)",
                            promises.size(),
                            c
                        ),
                        errors
                    ));
                }
            }
        }
    }

    /**
     * Attaches completion callbacks to all source promises and returns this
     * aggregator.
     *
     * @return this aggregator promise
     */
    Promise<List<R>> start() {
        int i = 0;
        for (CompletionStage<? extends T> promise : promises) {
            int idx = i++;
            promise.whenComplete((r, e) -> onComplete(idx, r, e));
        }
        return this;
    }

    /**
     * Marks all still-pending source promises as cancelled in the
     * {@code completions} array.
     */
    private void markRemainingCancelled() {
        for (int idx = completions.length() - 1; idx >= 0; idx--) {
            completions.compareAndSet(idx, PENDING, COMPLETED_CANCEL);
        }
    }

    /**
     * Cancels all source promises previously marked as {@link #COMPLETED_CANCEL}.
     * <p>
     * This method intentionally avoids streams to keep the cancellation path
     * allocation-free on JDK 1.8.
     * </p>
     */
    private void cancelPromises() {
        for (int i = 0, size = promises.size(); i < size; i++) {
            if (completions.get(i) == COMPLETED_CANCEL) {
                cancelPromise(promises.get(i), true);
            }
        }
    }

    /**
     * Creates a mutable list of the given length filled with {@code null}
     * values.
     *
     * @param <T>    element type
     * @param length list length
     * @return a pre-sized mutable list
     */
    private static <T> List<T> newList(int length) {
        return new ArrayList<>(Collections.nCopies(length, null));
    }

    /** Source promise has not completed yet. */
    private static final int PENDING          =  0;

    /** Source promise completed successfully. */
    private static final int COMPLETED_RESULT =  1;

    /** Source promise completed exceptionally. */
    private static final int COMPLETED_ERROR  =  2;

    /** Source promise was marked cancelled by the aggregator. */
    private static final int COMPLETED_CANCEL = -1;
}