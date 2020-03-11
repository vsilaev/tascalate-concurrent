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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

class AggregatingPromise<T> extends CompletableFutureWrapper<List<T>> {

    final private List<T> results;
    final private List<Throwable> errors;
    final private AtomicIntegerArray completions;

    final private AtomicInteger resultsCount = new AtomicInteger(0);
    final private AtomicInteger errorsCount = new AtomicInteger(0);

    final private AtomicBoolean done = new AtomicBoolean(false);

    final private int minResultsCount;
    final private int maxErrorsCount;
    final private boolean cancelRemaining;
    final private List<? extends CompletionStage<? extends T>> promises;

    AggregatingPromise(final int minResultsCount, final int maxErrorsCount, final boolean cancelRemaining,
                       final List<? extends CompletionStage<? extends T>> promises) {

        if (null == promises || promises.isEmpty()) {
            throw new IllegalArgumentException("There are should be at least one promise specified");
        }
        int size = promises.size();
        this.promises = promises;
        this.minResultsCount = minResultsCount < 0 ? 
            size : Math.max(1, Math.min(size, minResultsCount));
        this.maxErrorsCount = maxErrorsCount < 0 ? 
            promises.size() - minResultsCount : Math.max(0, Math.min(maxErrorsCount, size - minResultsCount));
        this.cancelRemaining = cancelRemaining;
        results = newList(size);
        errors = newList(size);
        completions = new AtomicIntegerArray(size);
        setupCompletionHandlers();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (done.compareAndSet(false, true)) {
            // Synchronized around done
            markRemainingCancelled();
            cancelPromises();
            return super.cancel(mayInterruptIfRunning);
        } else {
            return false;
        }
    }

    void onComplete(int idx, T result, Throwable error) {
        if (!completions.compareAndSet(idx, PENDING, null == error ? COMPLETED_RESULT : COMPLETED_ERROR)) {
            // Already completed
            return;
        }
        if (null == error) {
            // ON NEXT RESULT
            final int c = resultsCount.incrementAndGet();
            if (c <= minResultsCount) {
                // Only one thread may access this due to check with
                // "completions"
                results.set(idx, result);
                if (c == minResultsCount && done.compareAndSet(false, true)) {
                    // Synchronized around done
                    markRemainingCancelled();
                    // Now no other thread can modify results array.
                    delegate.complete(Collections.unmodifiableList(results));
                    if (cancelRemaining) {
                        cancelPromises();
                    }
                }
            }
        } else {
            // ON NEXT ERROR
            final int c = errorsCount.getAndIncrement();
            // We are reporting maxErrorsCount + 1 exceptions
            // So if we specify that no exceptions should happen
            // we will report at least one
            if (c <= maxErrorsCount) {
                // Only one thread may access this due to check with
                // "completions"
                errors.set(idx, error);
                if (c == maxErrorsCount && done.compareAndSet(false, true)) {
                    // Synchronized around done
                    markRemainingCancelled();
                    // Now no other thread can modify errors array.
                    delegate.completeExceptionally(new MultitargetException(Collections.unmodifiableList(errors)));

                    if (cancelRemaining) {
                        cancelPromises();
                    }
                }
            }
        }
    }

    private void markRemainingCancelled() {
        for (int idx = completions.length() - 1; idx >= 0; idx--) {
            completions.compareAndSet(idx, PENDING, COMPLETED_CANCEL);
        }
    }

    private void setupCompletionHandlers() {
        int i = 0;
        for (CompletionStage<? extends T> promise : promises) {
            final int idx = i++;
            promise.whenComplete((r, e) -> onComplete(idx, r, e));
        }
    }

    private void cancelPromises() {
        int i = 0;
        for (CompletionStage<? extends T> promise : promises) {
            final int idx = i++;
            if (completions.get(idx) == COMPLETED_CANCEL) {
                cancelPromise(promise, true);
            }
        }
    }

    private static <T> List<T> newList(int length) {
        final ArrayList<T> result = new ArrayList<>(length);
        for (int i = length - 1; i >= 0; i--) {
            result.add(null);
        }
        return result;
    }

    private static final int PENDING = 0;
    private static final int COMPLETED_RESULT = 1;
    private static final int COMPLETED_ERROR = 2;
    private static final int COMPLETED_CANCEL = -1;
}
