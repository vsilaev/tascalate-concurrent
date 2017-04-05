package net.tascalate.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

class CompletableSubTask<T> extends AbstractCompletableTask<T> {

    static class DelegatingCallable<T> implements Callable<T> {

        final private AtomicBoolean setupGuard = new AtomicBoolean(false);
        private Callable<T> delegate;

        void setup(Callable<T> delegate) {
            if (setupGuard.compareAndSet(false, true)) {
                this.delegate = delegate;
            } else {
                throw new IllegalStateException("Delegate may be set only once");
            }
        }

        @Override
        public T call() throws Exception {
            if (!setupGuard.get()) {
                throw new IllegalStateException("Call is not configured");
            } else {
                return delegate.call();
            }
        }

    }

    CompletableSubTask(Executor executor) {
        super(executor, new DelegatingCallable<T>());
    }

    @Override
    Runnable setupTransition(Callable<T> code) {
        DelegatingCallable<T> transitionCall = (DelegatingCallable<T>) action;
        transitionCall.setup(code);
        return task;
    }

    @Override
    protected <U> AbstractCompletableTask<U> createCompletionStage(Executor executor) {
        return new CompletableSubTask<U>(executor);
    }
}
