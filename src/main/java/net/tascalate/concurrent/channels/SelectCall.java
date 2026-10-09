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
package net.tascalate.concurrent.channels;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Promises;
import net.tascalate.concurrent.Try;

/**
 * Encapsulates a single execution of a {@code select} statement over a
 * set of channel operations.
 * <p>
 * This is the Java analogue of Go's {@code select} statement. It
 * multiplexes across multiple send and receive operations, completing
 * with whichever case becomes ready first. The evaluation proceeds in
 * two phases:
 * <ol>
 *   <li><b>Phase 1 (non-blocking):</b> all cases are tried in a random
 *       order via {@code trySend} / {@code tryReceive}. If any case is
 *       immediately ready, the select completes with that case's result.
 *       If a {@link Select#otherwise() default} case is present and no
 *       case is ready, the default wins.</li>
 *   <li><b>Phase 2 (blocking):</b> if no case is ready and no default
 *       exists, waiters are registered on every active case. The returned
 *       promise completes when the first case becomes ready, and all
 *       losing cases are cancelled.</li>
 * </ol>
 * <p>
 * Cases are shuffled randomly before evaluation, matching Go's behavior
 * of choosing randomly among ready cases.
 * <p>
 * The select validates its arguments eagerly: an empty case array,
 * duplicate channels, multiple default cases, and unsupported case types
 * are all rejected with {@link IllegalArgumentException} before any
 * asynchronous work begins.
 * <p>
 * This class is not intended for direct instantiation. Use the static
 * factory method {@link #select(Select.Op[])} or the convenience method
 * {@link Channel#select(Select.Op[])}.
 *
 * @param <T> the select result type
 * @see Select
 * @see Channel#select(Select.Op[])
 */
final class SelectCall<T> {

    /** The cases to evaluate, in their original order. */
    private final Select.Op<T>[] cases;

    /**
     * A shuffled permutation of case indices. Maps each evaluation
     * position to the original index in {@link #cases}, so that the
     * returned {@link Select.Result} reports the caller's original index.
     */
    private final Integer[] order;

    /**
     * Indices (in the shuffled order) of cases that failed during Phase 1.
     * These cases are skipped in Phase 2.
     */
    private final Set<Integer> failedIndexes = new HashSet<>();

    /**
     * Creates a new select call over the given cases.
     * <p>
     * The cases array is stored by reference. A shuffled index
     * permutation is generated immediately.
     *
     * @param cases the cases to evaluate; must not be null or empty
     */
    private SelectCall(Select.Op<T>[] cases) {
        this.cases = cases;
        this.order = new Integer[cases.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        // Fisher-Yates shuffle (Go picks randomly among ready cases)
        Collections.shuffle(Arrays.asList(order));
    }

    /**
     * Phase 1: non-blocking evaluation of all cases.
     * <p>
     * Iterates over the cases in shuffled order and attempts each one
     * via {@code trySend} or {@code tryReceive}. The first case that
     * succeeds causes an immediate return. Cases that fail (e.g., due to
     * a closed channel) are recorded in {@link #failedIndexes} for
     * exclusion from Phase 2.
     * <p>
     * If a {@link Select.Default} case is present and no other case is
     * ready, the default wins. Multiple defaults are rejected with
     * {@link IllegalArgumentException}.
     *
     * @return a successful {@link Try} if a case is ready, a failed
     *         {@link Try} if a case errored and no other case is ready,
     *         or {@code null} if no case is ready and no default exists
     */
    private Try<Select.Result<T>> phase1() {
        Throwable firstFailure = null;

        // Phase 1: non-blocking try
        int defaultOriginalIdx = -1;
        for (int idx = 0; idx < order.length; idx++) {
            int originalIdx = order[idx];
            Select.Op<T> c = cases[originalIdx];
            if (c instanceof Select.Default) {
                if (defaultOriginalIdx >= 0) {
                    throw new IllegalArgumentException("Multiple default cases in select");
                }
                defaultOriginalIdx = originalIdx;
            } else if (c instanceof Select.Disabled) {
                // Skip
            } else if (c instanceof Select.Receive) {
                Select.Receive<T> trc = (Select.Receive<T>)c;
                Try<T> r = trc.channel().tryReceive();
                if (r == null) {
                    continue; // not ready, try next case
                } else if (r.isFailure()) {
                    if (null == firstFailure) {
                        firstFailure = r.error();
                    }
                    failedIndexes.add(idx);
                } else if (r.isSuccess()) {
                    return success(originalIdx, trc, r.value() /*isSend=false*/);
                } else {
                    throw new IllegalStateException();
                }
            } else if (c instanceof Select.Send) {
                Select.Send<T> tsc = (Select.Send<T>)c; 
                Try<T> r = tsc.channel().trySend(tsc.value());
                if (r == null) {
                    continue; // Not ready (full), try next case
                } else if (r.isFailure()) {
                    if (firstFailure == null) {
                        firstFailure = r.error();
                    }
                    failedIndexes.add(idx);
                } else if (r.isSuccess()) {
                    // Successfully sent!
                    return success(originalIdx, tsc, r.value());
                }
            } else {
                throw new IllegalArgumentException("Unsupported SelectCase type: " + c.getClass().getName());
            }
        }
        // Nothing was immediately ready -> use default if present
        if (defaultOriginalIdx >= 0) {
            return success(defaultOriginalIdx, cases[defaultOriginalIdx], null /*isSend=false*/);
        } else if (firstFailure != null) {
            return Try.failure(firstFailure);
        } else {
            return null;
        }
    }

    /**
     * Phase 2: asynchronous registration of waiters on all active cases.
     * <p>
     * For each case that did not fail in Phase 1, a coordinated send or
     * receive is initiated. All waiters share a single
     * {@link SelectCoordinator#createFirstWins()} coordinator, ensuring
     * that at most one case can complete.
     * <p>
     * Each case's future is wrapped in a {@link SelectResultHolder} stage
     * that captures the original case index, the matched case object, and
     * any error. {@link CancellationException} is re-thrown rather than
     * captured, so that cancelled losing stages do not produce spurious
     * results.
     *
     * @return the list of completion stages to race via
     *         {@link Promises#any(java.util.Collection)}
     */
    private List<CompletionStage<SelectResultHolder<T>>> phase2() {
        // Phase 2: async wait using tascalate Promises
        List<CompletionStage<SelectResultHolder<T>>> stages = new ArrayList<>();
        SelectCoordinator coordinator = SelectCoordinator.createFirstWins();
        for (int idx = 0; idx < order.length; idx++) {
            if (failedIndexes.contains(idx)) {
                continue;
            }
            int originalIdx = order[idx];
            Select.Op<T> c = cases[originalIdx];
            if (c instanceof Select.Default || c instanceof Select.Disabled) {
                continue; 
            } 
            Promise<T> originalFuture;
            if (c instanceof Select.Receive) {
                originalFuture = ((Select.Receive<T>)c).channel().receive(coordinator);
            } else if (c instanceof Select.Send) {
                Select.Send<T> tsc = (Select.Send<T>)c; 
                originalFuture = tsc.channel().send(tsc.value(), coordinator);
            } else {
                throw new IllegalArgumentException("Unsupported SelectCase type: " + c.getClass().getName());
            }
            CompletionStage<SelectResultHolder<T>> stage = 
            originalFuture.dependent()
                          .handle((val, ex) -> {
                              if (ex instanceof CancellationException) {
                                  throw (CancellationException)ex;
                              } else {
                                  return new SelectResultHolder<>(originalIdx, c, val, ex); 
                              }
                          }, true);
            stages.add(stage);
        }
        return stages;
    }

    /**
     * Executes the select: runs Phase 1, and if no case is immediately
     * ready, runs Phase 2 and races the resulting stages.
     * <p>
     * The returned promise completes with the winning case's
     * {@link Select.Result}. If all cases fail in Phase 1 and no default
     * exists, the promise fails with the first error. If all cases are
     * disabled and no default exists, the promise never completes
     * (matching Go's {@code select{}} blocking-forever semantics).
     * <p>
     * When the first stage completes, {@link Promises#any} automatically
     * cancels the losing stages. Thanks to {@code dependent(true)}, this
     * cancellation propagates all the way to the channel, cleaning up
     * its internal {@code waitSenders} / {@code waitReceivers} queues.
     *
     * @return a promise that completes with the winning case's result
     */
    Promise<Select.Result<T>> execute() {
        Try<Select.Result<T>> readyResult = phase1();
        if (readyResult != null && readyResult.isSuccess()) {
            return Promises.success(readyResult.value());
        }
        List<CompletionStage<SelectResultHolder<T>>> stages = phase2();
        if (stages.isEmpty()) {
            if (readyResult != null) {
                return Promises.failure(readyResult.error());
            } else {
                // All cases are Disabled (no Default present, otherwise Phase 1
                // would have returned). Go semantics: select{} blocks forever.
                return NilChannel.incomplete();
            }
        } else {
            // Promises.any races the stages. The first to complete wins.
            // It automatically calls .cancel() on the losing stages.
            // Thanks to dependent(true), this cancellation propagates all the way 
            // to the AsyncChannel, cleaning up its internal waitSenders/waitReceivers queues!
            return 
            Promises.any(stages)
                    .dependent()
                    .thenCompose(res -> 
                        res.error != null ? Promises.failure(res.error)
                                          : Promises.success(selectResult(res.index, res.match, res.value)), true)
                    .unwrap();
        }  
    }

    /**
     * Creates and executes a select statement over the given cases.
     * <p>
     * This is the main entry point for select evaluation. It validates
     * the input, checks for duplicate channels, and delegates to
     * {@link #execute()}.
     * <p>
     * Validation errors are thrown synchronously as
     * {@link IllegalArgumentException}, consistent with the convention
     * that argument errors are programmer errors. Duplicate channels are
     * rejected because the {@link SelectCoordinator} cannot distinguish
     * between two cases on the same channel.
     *
     * @param <T>   the select result type
     * @param cases the cases to evaluate; must not be null or empty,
     *              must not contain duplicate channels or multiple
     *              default cases
     * @return a promise that completes with the winning case's result
     * @throws IllegalArgumentException if the case array is null or empty,
     *         or contains duplicate channels
     */
    @SafeVarargs
    static <T> Promise<Select.Result<T>> select(Select.Op<T>... cases) {
        if (cases == null || cases.length == 0) {
            throw new IllegalArgumentException("At least one case required");
        }
        Set<Channel<?>> seen = new HashSet<>();
        for (Select.Op<T> c : cases) {
            Object ch;
            if (c instanceof Select.Receive) {
                ch = ((Select.Receive<?>)c).channel();
            } else if (c instanceof Select.Send) {
                ch = ((Select.Send<?>)c).channel();
            } else {
                ch = null;
            }
            if (ch != null && !seen.add((Channel<?>)ch)) {
                throw new IllegalArgumentException("Duplicate channel in select: " + ch);
            }
        }
        SelectCall<T> call = new SelectCall<>(cases);
        return call.execute();
    }

    /**
     * Internal wrapper to unify types for {@link Promises#any} and to
     * capture exceptions.
     * <p>
     * Each stage in Phase 2 produces a {@code SelectResultHolder} that
     * records the original case index, the matched case object, the
     * received or sent value, and any error. This allows the final
     * {@link Promises#any} to race heterogeneous stages and still
     * reconstruct a typed {@link Select.Result}.
     *
     * @param <T> the select result type
     */
    static final class SelectResultHolder<T> {

        /** The original index of the matched case in the caller's array. */
        final int index;

        /** The matched case object. */
        final Select.Op<T> match;

        /** The value received or sent, or {@code null} for default cases. */
        final T value;

        /** The error, or {@code null} on success. */
        final Throwable error;

        /**
         * Creates a new result holder.
         *
         * @param index the original case index
         * @param match the matched case object
         * @param value the value, or {@code null}
         * @param error the error, or {@code null}
         */
        public SelectResultHolder(int index, Select.Op<T> match, T value, Throwable error) {
            this.index = index;
            this.match = match;
            this.value = value;
            this.error = error;
        }
    }

    /**
     * Wraps a successful case outcome in a {@link Try}.
     *
     * @param <T>        the select result type
     * @param idx        the original case index
     * @param selectCase the matched case object
     * @param value      the value, or {@code null}
     * @return a successful {@link Try} containing the result
     */
    private static <T> Try<Select.Result<T>> success(int idx, Select.Op<T> selectCase, T value) {
        return Try.success(selectResult(idx, selectCase, value));
    }

    /**
     * Constructs a {@link Select.Result} from its components.
     *
     * @param <T>        the select result type
     * @param idx        the original case index
     * @param selectCase the matched case object
     * @param value      the value, or {@code null}
     * @return a new {@link Select.Result}
     */
    private static <T> Select.Result<T> selectResult(int idx, Select.Op<T> selectCase, T value) {
        return new Select.Result<>(idx, selectCase, value);
    }
}