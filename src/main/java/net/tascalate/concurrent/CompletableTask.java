package net.tascalate.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.RunnableFuture;

public class CompletableTask<T> extends AbstractCompletableTask<T> implements RunnableFuture<T> {

    public CompletableTask(final Executor executor, Callable<T> callable) {
        super(executor, callable);
    }

    @Override
    public void run() {
        task.run();
    }

    public static <T> Promise<T> completedFuture(T value, Executor defaultExecutor) {
        CompletableTask<T> result = new CompletableTask<T>(defaultExecutor, () -> value);
        SAME_THREAD_EXECUTOR.execute(result);
        return result;
    }

    @Override
    Runnable setupTransition(Callable<T> code) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected <U> AbstractCompletableTask<U> createCompletionStage(Executor executor) {
        return new CompletableSubTask<U>(executor);
    }
}
