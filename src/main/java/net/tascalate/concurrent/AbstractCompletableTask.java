/**
 * Original work: copyright 2009-2015 Lukáš Křečan 
 * https://github.com/lukas-krecan/completion-stage
 * 
 * This class is based on the work create by Lukáš Křečan 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/lukas-krecan/completion-stage/blob/completion-stage-0.0.9/src/main/java/net/javacrumbs/completionstage/SimpleCompletionStage.java
 * 
 * Modified work: copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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
import static net.tascalate.concurrent.SharedFunctions.iif;
import static net.tascalate.concurrent.SharedFunctions.unwrapCompletionException;
import static net.tascalate.concurrent.SharedFunctions.unwrapExecutionException;
import static net.tascalate.concurrent.SharedFunctions.wrapExecutionException;

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
abstract class AbstractCompletableTask<T> extends PromiseAdapterExtended<T> 
                                          implements Promise<T> {

    private final CallbackRegistry<T> callbackRegistry = new CallbackRegistry<>();
    protected final RunnableFuture<T> task;

    protected AbstractCompletableTask(Executor defaultExecutor, Callable<T> action) {
        super(defaultExecutor);
        this.task = new FutureTask<T>(action) {
            @Override
            protected void set(T v) {
                super.set(v);
                success(v);
            };

            @Override
            protected void setException(Throwable t) {
                super.setException(t);
                failure(t);
            };
        };
    }
    
    private volatile CompletionStage<?> intermediateStage;

    abstract void fireTransition(Callable<T> code);

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (task.cancel(mayInterruptIfRunning)) {
            failure(new CancellationException());
            CompletionStage<?> s = intermediateStage;
            if (null != s) {
                cancelPromise(s, mayInterruptIfRunning);
            }
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

    boolean success(T result) {
        return callbackRegistry.success(result);
    }

    boolean failure(Throwable ex) {
        return callbackRegistry.failure(ex);
    }
    
    @Override
    public String toString() {
        return String.format("%s@%d[%s]", getClass().getSimpleName(), System.identityHashCode(this), task);
    }

    // Override thenApplyAsync and exceptionallyAsync just to minimize amount of wrappers
    // Otherwise delegation to handleAsync (in superclass) works perfect
    @Override
    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return addCallbacks(
            newSubTask(executor), 
            r -> {
                try {
                    return fn.apply(r);
                } catch (Throwable e) {
                    return forwardException(e);
                }
            }, 
            forwardException(), 
            executor
        );
    }
    
    @Override
    public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        // Symmetrical with thenApplyAsync
        return addCallbacks(
            newSubTask(executor), 
            Function.identity(), 
            failure -> {
                try {
                    return fn.apply(failure);
                } catch (Throwable e) {
                    if (e != failure) {
                        e.addSuppressed(failure);
                    }
                    return forwardException(e);
                }
            }, 
            executor
        );
    }

    @Override
    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return addCallbacks(
            newSubTask(executor), 
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
                    if (e != failure) {
                        e.addSuppressed(failure);
                    }
                    return forwardException(e);
                }
            },
            executor
        );
    }    

    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {

        AbstractCompletableTask<Void> tempStage = newSubTask(executor);
        AbstractCompletableTask<U> nextStage = newSubTask(executor);
        // Need to enlist tempStage while it is non-visible outside
        // and may not be used to interrupt fn.apply();
        nextStage.intermediateStage = tempStage;

        // We must ALWAYS run through the execution
        // of nextStage.task when this nextStage is
        // exposed to the client, even in a "trivial" case:
        // Success path, just return value
        Consumer<? super U> onResult = nextStage.runTransition(Function.identity());
        // Failure path, just re-throw exception
        Consumer<? super Throwable> onError = nextStage.runTransition(forwardException());

        // Important -- tempStage is the target here
        addCallbacks(
            tempStage, 
            r -> {
                try {
                    // tempStage is completed successfully, so no sense
                    // to include it in cancellableOrigins
                    nextStage.intermediateStage = null;
                    if (nextStage.isDone()) {
                        // Was canceled by user code
                        // Don't need to run anything
                        return null;
                    }
                    CompletionStage<U> returned = fn.apply(r);
                    // nextStage is in progress
                    
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
                    
                    // Always assign before check for cancellation to avoid race
                    nextStage.intermediateStage = returned;
                    if (nextStage.isCancelled()) {
                        cancelPromise(returned, true);
                    } else {
                        // Synchronous, while transition to tempStage is asynchronous already
                        returned.whenComplete(biConsumer(onResult, onError));
                    }
                } catch (Throwable e) {
                    // no need to check nextStage.isCancelled()
                    // while there are no origins to cancel
                    // propagate error immediately
                    // synchronous, while transition to tempStage is asynchronous already
                    onError.accept(e);  
                }
                return null;
            }, 
            consumerAsFunction(onError),
            executor
        );

        return nextStage;
    }

    // Provide better impl. rather than several nested stages by default
    @Override
    public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        // Symmetrical with thenComposeAsync
        // See comments for thenComposeAsync -- all are valid here, this is just a different path in Either (left vs right)
        AbstractCompletableTask<Void> tempStage = newSubTask(executor);
        AbstractCompletableTask<T> nextStage = newSubTask(executor);

        nextStage.intermediateStage = tempStage;

        Consumer<? super T> onResult = nextStage.runTransition(Function.identity());
        Consumer<? super Throwable> onError = nextStage.runTransition(forwardException());

        addCallbacks(
            tempStage, 
            consumerAsFunction(onResult),
            failure -> {
                try {
                    nextStage.intermediateStage = null;
                    if (nextStage.isDone()) {
                        // Was canceled by user code
                        // Don't need to run anything
                        return null;
                    }
                    CompletionStage<T> returned = fn.apply(failure);
                    nextStage.intermediateStage = returned;
                    if (nextStage.isCancelled()) {
                        cancelPromise(returned, true);
                    } else {
                        returned.whenComplete(biConsumer(onResult, onError));
                    }
                } catch (Throwable e) {
                    // In JDK 12 CompletionStage.composeExceptionally[Async] uses *.handle[Async]
                    // So overwrite returned error with the latest one - as in handle()
                    if (e != failure) {
                        e.addSuppressed(failure);
                    }
                    onError.accept(e);  
                }
                return null;
            }, 
            executor
        );

        return nextStage;
    }

    @Override
    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return addCallbacks(
            newSubTask(executor), 
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
                    if (e != failure) {
                        failure.addSuppressed(e);
                    }
                }
                return forwardException(failure);
            }, 
            executor
        );
    }

    @Override
    public CompletableFuture<T> toCompletableFuture() {
        CompletableFuture<T> result = new CompletableFuture<>();
        whenComplete((r, e) -> iif(e == null ? result.complete(r) : result.completeExceptionally(e)));
        return result;
    }

    /**
     * This method exists just to reconcile generics when called from
     * {@link #runAfterEitherAsync} which has unexpected type of parameter
     * "other". The alternative is to ignore compiler warning.
     */
    @Override
    protected <R, U> Promise<U> doApplyToEitherAsync(CompletionStage<? extends R> first,
                                                     CompletionStage<? extends R> second, 
                                                     Function<? super R, U> fn, 
                                                     Executor executor) {

        AbstractCompletableTask<R> nextStage = newSubTask(executor);

        // Next stage is not exposed to the client, so we can
        // short-circuit its initiation - just fire callbacks
        // without task execution (unlike as in other methods,
        // even in thenComposeAsync with its ad-hoc execution)

        // In certain sense, nextStage here is bogus: neither
        // of Future-defined methods are functional.
        BiConsumer<R, Throwable> action = (r, e) -> iif(
            e == null ? nextStage.success(r) : nextStage.failure(forwardException(e))
        );
        // only the first result is accepted by completion stage,
        // the other one is ignored
        first.whenComplete(action);
        second.whenComplete(action);

        return nextStage.thenApplyAsync(fn, executor);
    }

    abstract protected <U> AbstractCompletableTask<U> createCompletionStage(Executor executor);
    
    private <U> AbstractCompletableTask<U> newSubTask(Executor executor) {
        // Preserve default async executor, or use user-supplied executor as default
        // But don't let SAME_THREAD_EXECUTOR to be a default async executor
        return createCompletionStage(executor == SAME_THREAD_EXECUTOR ? getDefaultExecutor() : executor);
    }
    
    private <U> Consumer<? super U> runTransition(Function<? super U, ? extends T> converter) {
        return u -> fireTransition(() -> converter.apply(u)); 
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

    private static ExecutionException rewrapExecutionException(ExecutionException ex) {
        return wrapExecutionException( 
                   unwrapCompletionException(
                       unwrapExecutionException(ex)
                   )
               );
    }    

    private <U> AbstractCompletableTask<U> addCallbacks(AbstractCompletableTask<U> targetStage,
                                                        Function<? super T, ? extends U> successCallback, 
                                                        Function<Throwable, ? extends U> failureCallback,
                                                        Executor executor) {
        
        callbackRegistry.addCallbacks(targetStage, successCallback, failureCallback, executor);
        return targetStage;
    }

}
