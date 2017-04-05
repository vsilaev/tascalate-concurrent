package net.tascalate.concurrent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

class AggregatingPromise<T> extends CompletablePromise<List<T>> {

    final private T[] results;
    final private Throwable[] errors;
    final private AtomicIntegerArray completions;

    final private AtomicInteger resultsCount = new AtomicInteger(0);
    final private AtomicInteger errorsCount = new AtomicInteger(0);

    final private AtomicBoolean done = new AtomicBoolean(false);

    final private int minResultsCount;
    final private int maxErrorsCount;
    final private boolean cancelRemaining;
    final private CompletionStage<? extends T>[] promises;

    @SafeVarargs
    AggregatingPromise(final int minResultsCount, final int maxErrorsCount, final boolean cancelRemaining,
                       final CompletionStage<? extends T>... promises) {
        
        if (null == promises || promises.length < 1) {
            throw new IllegalArgumentException("There are should be at least one promise specified");
        }
        this.promises = promises;
        this.minResultsCount = minResultsCount < 0 ? 
            promises.length : Math.max(1, Math.min(promises.length, minResultsCount));
        this.maxErrorsCount = maxErrorsCount < 0 ? 
            promises.length - minResultsCount : Math.max(0, Math.min(maxErrorsCount, promises.length - minResultsCount));
        this.cancelRemaining = cancelRemaining;
        results = newArray(promises.length);
        errors = new Throwable[promises.length];
        completions = new AtomicIntegerArray(promises.length);
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
                results[idx] = result;
                if (c == minResultsCount && done.compareAndSet(false, true)) {
                    // Synchronized around done
                    markRemainingCancelled();
                    // Now no other thread can modify results array.
                    onSuccess(new ArrayList<>(Arrays.asList(results)));
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
                errors[idx] = error;
                if (c == maxErrorsCount && done.compareAndSet(false, true)) {
                    // Synchronized around done
                    markRemainingCancelled();
                    // Now no other thread can modify errors array.
                    onError(new MultitargetException(new ArrayList<>(Arrays.asList(errors))));

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
        for (final CompletionStage<? extends T> promise : promises) {
            final int idx = i++;
            promise.whenComplete((r, e) -> onComplete(idx, r, e));
        }
    }

    private void cancelPromises() {
        for (int idx = promises.length - 1; idx >= 0; idx--) {
            if (completions.get(idx) == COMPLETED_CANCEL) {
                CompletablePromise.cancelPromise(promises[idx], true);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T[] newArray(int length) {
        return (T[]) new Object[length];
    }

    private static final int PENDING = 0;
    private static final int COMPLETED_RESULT = 1;
    private static final int COMPLETED_ERROR = 2;
    private static final int COMPLETED_CANCEL = -1;
}
