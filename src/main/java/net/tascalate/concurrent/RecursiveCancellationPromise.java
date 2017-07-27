package net.tascalate.concurrent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RecursiveCancellationPromise<T> extends AbstractDelegatingPromise<T, Promise<T>> {
    
    final private LinkedBlockingQueue<CompletionStage<?>> cancellableOrigins;
    
    protected RecursiveCancellationPromise(Promise<T> delegate, List<CompletionStage<?>> cancellableOrigins) {
        super(delegate);
        this.cancellableOrigins = cancellableOrigins == null ? new LinkedBlockingQueue<>() : new LinkedBlockingQueue<>(cancellableOrigins);
    }
    
    public static <U> RecursiveCancellationPromise<U> from(Promise<U> source) {
        return new RecursiveCancellationPromise<>(source, Collections.emptyList());
    }
    
    public RecursiveCancellationPromise<T> enlistCancellableOrigin(CompletionStage<?> origin) {
        if (isCancelled()) {
            CompletablePromise.cancelPromise(origin, true);
        } else if (!isDone()) {
            try {
                cancellableOrigins.put(origin);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }
        return this;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        final boolean result = completionStage.cancel(mayInterruptIfRunning);
        final ArrayList<CompletionStage<?>> toCancel = new ArrayList<>();
        cancellableOrigins.drainTo(toCancel);
        toCancel.stream().forEach(p -> CompletablePromise.cancelPromise(p, mayInterruptIfRunning));
        return result;
    }

    @Override
    public boolean isCancelled() {
        return completionStage.isCancelled();
    }

    @Override
    public boolean isDone() {
        return completionStage.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return completionStage.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return completionStage.get(timeout, unit);
    }

    @Override
    protected <U> Promise<U> wrap(CompletionStage<U> original) {
        return new RecursiveCancellationPromise<>((Promise<U>)original, Collections.singletonList(this));
    }
}
