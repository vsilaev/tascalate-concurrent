/**
 * Original work: copyright 2009-2015 Lukáš Křečan 
 * https://github.com/lukas-krecan/completion-stage
 * 
 * This class is based on the work create by Lukáš Křečan 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/lukas-krecan/completion-stage/blob/completion-stage-0.0.9/src/main/java/net/javacrumbs/completionstage/SimpleCompletionStage.java
 * 
 * Modified work: copyright 2015-2020 Valery Silaev (http://vsilaev.com)
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
import static net.tascalate.concurrent.SharedFunctions.unwrapCompletionException;
import static net.tascalate.concurrent.SharedFunctions.unwrapExecutionException;
import static net.tascalate.concurrent.SharedFunctions.wrapCompletionException;
import static net.tascalate.concurrent.SharedFunctions.wrapExecutionException;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Base superclass for both root and intermediate {@link Promise}-s that
 * represent blocking long-running tasks
 * 
 * @author vsilaev
 *
 * @param <T>
 *   a type of the successfully executed task result   
 */
abstract class AbstractCompletableTask<T> extends PromiseAdapter<T> implements Promise<T> {

    private final CallbackRegistry<T> callbackRegistry = new CallbackRegistry<>();
    protected final RunnableFuture<T> task;
    protected final Callable<T> action;

    protected AbstractCompletableTask(Executor defaultExecutor, Callable<T> action) {
        super(defaultExecutor);
        this.action = action;
        this.task = new StageTransition(action);
    }
    
    private CompletionStage<?>[] cancellableOrigins;
    private Object cancellableOriginsLock = new Object();
    
    protected void resetCancellableOrigins(CompletionStage<?>... origins) {
        synchronized (cancellableOriginsLock) {
            this.cancellableOrigins = origins; 
        }
    }
    
    protected void cancelOrigins(boolean mayInterruptIfRunning) {
        synchronized (cancellableOriginsLock) {
            if (null == cancellableOrigins) {
                return;
            }
            Arrays.stream(cancellableOrigins).forEach(p -> cancelPromise(p, mayInterruptIfRunning)); 
        }
    }

    abstract void fireTransition(Callable<T> code);

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (task.cancel(mayInterruptIfRunning)) {
            onError(new CancellationException());
            cancelOrigins(mayInterruptIfRunning);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean isCancelled() {
        return task.isCancelled();
    }

    @Override
    public boolean isDone() {
        return task.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        try {
            return task.get();
        } catch (ExecutionException ex) {
            throw rewrapExecutionException(ex);
        }
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        try {
            return task.get(timeout, unit);
        } catch (ExecutionException ex) {
            throw rewrapExecutionException(ex);
        }
    }
    
    @Override
    public boolean isCompletedExceptionally() {
        return callbackRegistry.isFailure();
    }

    boolean onSuccess(T result) {
        return callbackRegistry.success(result);
    }

    boolean onError(Throwable ex) {
        return callbackRegistry.failure(ex);
    }

    class StageTransition extends FutureTask<T>
                          implements CompletableFuture.AsynchronousCompletionTask {
        StageTransition(Callable<T> callable) {
            super(callable);
        }

        @Override
        protected void set(T v) {
            super.set(v);
            onSuccess(v);
        };

        @Override
        protected void setException(Throwable t) {
            super.setException(t);
            onError(t);
        };
    }

    @Override
    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        AbstractCompletableTask<U> nextStage = internalCreateCompletionStage(executor);
        addCallbacks(nextStage, fn, AbstractCompletableTask::forwardException, executor);
        return nextStage;
    }

    @Override
    public Promise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return thenApplyAsync(consumerAsFunction(action), executor);
    }

    @Override
    public Promise<Void> thenRunAsync(Runnable action, Executor executor) {
        return thenApplyAsync(runnableAsFunction(action), executor);
    }

    @Override
    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                              BiFunction<? super T, ? super U, ? extends V> fn, 
                                              Executor executor) {

        return thenCompose(result1 -> other.thenApplyAsync(result2 -> fn.apply(result1, result2), executor));
    }

    @Override
    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                 BiConsumer<? super T, ? super U> action, 
                                                 Executor executor) {
        return thenCombineAsync(other,
                // transform BiConsumer to BiFunction
                (t, u) -> {
                    action.accept(t, u);
                    return null;
                }, executor);
    }

    @Override
    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                           Runnable action, 
                                           Executor executor) {
        return thenCombineAsync(other,
                // transform Runnable to BiFunction
                (t, r) -> {
                    action.run();
                    return null;
                }, executor);
    }

    @Override
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                             Function<? super T, U> fn,
                                             Executor executor) {

        return doApplyToEitherAsync(this, other, fn, executor);
    }

    @Override
    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                                           Consumer<? super T> action,
                                           Executor executor) {

        return applyToEitherAsync(other, consumerAsFunction(action), executor);
    }

    @Override
    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                             Runnable action, 
                                             Executor executor) {
        
        return doApplyToEitherAsync(this, other, runnableAsFunction(action), executor);
    }

    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {

        AbstractCompletableTask<Void> tempStage = internalCreateCompletionStage(executor);
        AbstractCompletableTask<U> nextStage = internalCreateCompletionStage(executor);
        // Need to enlist tempStage while it is non-visible outside
        // and may not be used to interrupt fn.apply();
        nextStage.resetCancellableOrigins(tempStage);

        // We must ALWAYS run through the execution
        // of nextStage.task when this nextStage is
        // exposed to the client, even in a "trivial" case:
        // Success path, just return value
        Consumer<? super U> onResult = nextStage.runTransition(Function.identity());
        // Failure path, just re-throw exception
        Consumer<? super Throwable> onError = nextStage.runTransition(AbstractCompletableTask::forwardException);

        // Important -- tempStage is the target here
        addCallbacks(
            tempStage, 
            consumerAsFunction(r -> {
                try {
                    CompletionStage<U> returned = fn.apply(r);
                    // tempStage is completed successfully, so no sense
                    // to include it in cancellableOrigins
                    // However, nextStage is in progress
                    // IMPORTANT: it COULD be shared, but typically is not
                    // So in very rare case some nasty behavior MAY exist 
                    // if others depends on it
                    
                    // TEST: There is a race when fn.apply(r) is completed
                    // normally and nextStage is cancelled before returned is set
                    // as its nextStage's cancellableOrigins. In this case,
                    // execution of returned continues as nextStage cannot be
                    // cancelled for a second time. However, completion stages after
                    // nextStage are completed exceptionally (correctly) since when
                    // moveToNextStage is executed nextStage is already completed
                    // (cancelled) from cancel(...) -> onError(...). In order to
                    // cancel returned here, I think you need to know whether
                    // nextStage might have been interrupted.
                    // try {
                    //    Thread.sleep(100);
                    //} catch (InterruptedException ex) {
                    //}
                    
                    nextStage.resetCancellableOrigins(returned);
                    if (nextStage.isCancelled()) {
                        nextStage.cancelOrigins(true);
                    } else {
                        // Synchronous, while transition to tempStage is asynchronous already
                        returned.whenComplete(biConsumer(onResult, onError));
                    }
                } catch (Throwable e) {
                    // must-have if fn.apply above failed 
                    nextStage.resetCancellableOrigins((CompletionStage<U>)null);
                    // no need to check nextStage.isCancelled()
                    // while there are no origins to cancel
                    // propagate error immediately
                    // synchronous, while transition to tempStage is asynchronous already
                    onError.accept(e);  
                }
            }), 
            consumerAsFunction(onError),
            executor
        );

        return nextStage;
    }

    @Override
    public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        // Symmetrical with thenApplyAsync
        AbstractCompletableTask<T> nextStage = internalCreateCompletionStage(executor);
        addCallbacks(nextStage, Function.identity(), fn, executor);
        return nextStage;
    }

    // Provide better impl. rather than several nested stages by default
    @Override
    public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        // Symmetrical with thenComposeAsync
        // See comments for thenComposeAsync -- all are valid here, this is just a different path in Either (left vs right)
        AbstractCompletableTask<Void> tempStage = internalCreateCompletionStage(executor);
        AbstractCompletableTask<T> nextStage = internalCreateCompletionStage(executor);

        nextStage.resetCancellableOrigins(tempStage);

        Consumer<? super T> onResult = nextStage.runTransition(Function.identity());
        Consumer<? super Throwable> onError = nextStage.runTransition(AbstractCompletableTask::forwardException);

        addCallbacks(
            tempStage, 
            consumerAsFunction(onResult),
            consumerAsFunction(error -> {
                try {
                    CompletionStage<T> returned = fn.apply(error);
                    nextStage.resetCancellableOrigins(returned);
                    if (nextStage.isCancelled()) {
                        nextStage.cancelOrigins(true);
                    } else {
                        returned.whenComplete(biConsumer(onResult, onError));
                    }
                } catch (Throwable e) {
                    nextStage.resetCancellableOrigins((CompletionStage<T>)null);
                    // In JDK 12 CompletionStage.composeExceptionally[Async] uses *.handle[Async]
                    // So overwrite returned error with the latest one - as in handle()
                    e.addSuppressed(error);
                    onError.accept(e);  
                }
            }), 
            executor
        );

        return nextStage;
    }
    
    // Default operation in Promise is ok - it just delegates to thenCompose[Async]
    /*
    @Override
    public Promise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, Executor executor) {
    */

    @Override
    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        AbstractCompletableTask<T> nextStage = internalCreateCompletionStage(executor);
        addCallbacks(
            nextStage, 
            result -> {
                try {
                    action.accept(result, null);
                } catch (Throwable e) {
                    // CompletableFuture wraps exception here
                    // Copying this behavior                    
                    return forwardException(e);
                }
                return result;
            }, 
            // exceptions are handled in regular way
            failure -> {
                try {
                    action.accept(null, failure);
                } catch (Throwable e) {
                    // CompletableFuture does not override exception here
                    // unlike as in handle[Async](BiFunction)
                    // Preserve this behavior, but let us add at least 
                    // suppressed exception
                    failure.addSuppressed(e);
                }
                return forwardException(failure);
            }, 
            executor
        );
        return nextStage;
    }

    @Override
    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        AbstractCompletableTask<U> nextStage = internalCreateCompletionStage(executor);
        addCallbacks(
            nextStage, 
            result -> {
                try {
                    return fn.apply(result, null);
                } catch (Throwable e) {
                    // CompletableFuture wraps exception here
                    // Copying this behavior
                    return forwardException(e);
                }
            },
            // exceptions are handled in regular way
            failure -> {
                try {
                    return fn.apply(null, failure);
                } catch (Throwable e) {
                    // CompletableFuture handle[Async](BiFunction)
                    // allows to overwrite exception for resulting stage.
                    // Copying this behavior but enlist suppressed failure
                    e.addSuppressed(failure);
                    return forwardException(e);
                }
            },
            executor
        );
        return nextStage;
    }

    @Override
    public CompletableFuture<T> toCompletableFuture() {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        // nextStage is CompletableFuture rather than AbstractCompletableTask
        // so trigger completion on ad-hoc runnable rather than on
        // nextStage.task
        Consumer<Callable<T>> setup = c -> {
            try {
                c.call();
            } catch (Throwable ex) {
                completableFuture.completeExceptionally(ex);
            }
        };
        addCallbacks(
            setup, 
            consumerAsFunction(completableFuture::complete),
            consumerAsFunction(completableFuture::completeExceptionally), 
            SAME_THREAD_EXECUTOR
        );
        return completableFuture;
    }

    abstract protected <U> AbstractCompletableTask<U> createCompletionStage(Executor executor);

    /**
     * This method exists just to reconcile generics when called from
     * {@link #runAfterEitherAsync} which has unexpected type of parameter
     * "other". The alternative is to ignore compiler warning.
     */
    private <R, U> Promise<U> doApplyToEitherAsync(CompletionStage<? extends R> first,
                                                   CompletionStage<? extends R> second, 
                                                   Function<? super R, U> fn, 
                                                   Executor executor) {

        AbstractCompletableTask<R> nextStage = internalCreateCompletionStage(executor);

        // Next stage is not exposed to the client, so we can
        // short-circuit its initiation - just fire callbacks
        // without task execution (unlike as in other methods,
        // even in thenComposeAsync with its ad-hoc execution)

        // In certain sense, nextStage here is bogus: neither
        // of Future-defined methods are functional.
        BiConsumer<R, Throwable> action = (result, failure) -> {
            if (failure == null) {
                nextStage.onSuccess(result);
            } else {
                nextStage.onError(forwardException(failure));
            }
        };
        // only the first result is accepted by completion stage,
        // the other one is ignored
        first.whenComplete(action);
        second.whenComplete(action);

        return nextStage.thenApplyAsync(fn, executor);
    }

    private <U> AbstractCompletableTask<U> internalCreateCompletionStage(Executor executor) {
        // Preserve default async executor, or use user-supplied executor as default
        // But don't let SAME_THREAD_EXECUTOR to be a default async executor
        return createCompletionStage(executor == SAME_THREAD_EXECUTOR ? getDefaultExecutor() : executor);
    }
    
    private <U> Consumer<? super U> runTransition(Function<? super U, ? extends T> converter) {
        return u -> fireTransition(() -> converter.apply(u)); 
    }

    private static <V, R> Function<V, R> consumerAsFunction(Consumer<? super V> action) {
        return result -> {
            action.accept(result);
            return null;
        };
    }

    private static <R> Function<R, Void> runnableAsFunction(Runnable action) {
        return result -> {
            action.run();
            return null;
        };
    }
    
    private static <U, V> BiConsumer<U, V> biConsumer(Consumer<? super U> onResult, Consumer<? super V> onError) {
        return (u, v) -> {
            if (null == v) {
                onResult.accept(u);
            } else {
                onError.accept(v);
            }
        };
    }

    private static <U> U forwardException(Throwable e) {
        throw wrapCompletionException(e);
    }
    
    private static ExecutionException rewrapExecutionException(ExecutionException ex) {
        return wrapExecutionException( unwrapCompletionException(unwrapExecutionException(ex)) );
    }    

    private <U> void addCallbacks(AbstractCompletableTask<U> targetStage,
                                  Function<? super T, ? extends U> successCallback, 
                                  Function<Throwable, ? extends U> failureCallback,
                                  Executor executor) {
        
        addCallbacks(targetStage::fireTransition, successCallback, failureCallback, executor);
    }

    private <U> void addCallbacks(Consumer<? super Callable<U>> stageTransition,
                                  Function<? super T, ? extends U> successCallback, 
                                  Function<Throwable, ? extends U> failureCallback,
                                  Executor executor) {
        
        callbackRegistry.addCallbacks(stageTransition, successCallback, failureCallback, executor);
    }

}