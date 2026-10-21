/**
 * Original work: copyright 2009-2015 Lukáš Křečan
 * https://github.com/lukas-krecan/completion-stage
 * This class is based on the work create by Lukáš Křečan
 * under the Apache License, Version 2.0. Please see
 * https://github.com/lukas-krecan/completion-stage/blob/completion-stage-0.0.9/src/main/java/net/javacrumbs/completionstage/SimpleCompletionStage.java
 *
 * Modified work: copyright 2015-2026 Valery Silaev (http://vsilaev.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.tascalate.concurrent;

import static net.tascalate.concurrent.AsyncInternals.cancelPromise;
import static net.tascalate.concurrent.AsyncInternals.iif;
import static net.tascalate.concurrent.AsyncInternals.unwrapCompletionException;
import static net.tascalate.concurrent.AsyncInternals.unwrapExecutionException;
import static net.tascalate.concurrent.AsyncInternals.wrapExecutionException;

import java.util.Set;
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

import net.tascalate.concurrent.core.CompletionStageAPI;

/**
 * Base superclass for both root and intermediate {@link Promise}s that represent
 * blocking long-running tasks.
 *
 * <h2>Bridging execution and composition</h2>
 * <p>
 * This class bridges the JDK's {@link FutureTask} execution model — a task is
 * submitted to an {@link Executor} and blocks a thread until it finishes — with
 * the {@link Promise} composition model — results flow through a chain of
 * non-blocking callbacks. It does so by wrapping a {@link RunnableFuture} whose
 * completion (success or exception) triggers the corresponding resolution of this
 * promise via {@link #success(Object)} / {@link #failure(Throwable)}.
 * </p>
 *
 * <h2>Subclass responsibilities</h2>
 * <p>
 * Concrete subclasses implement two abstract members:
 * </p>
 * <ul>
 *   <li>{@link #fireTransition(Callable)} — defines how this stage's action is
 *       triggered by a preceding stage. The root task ({@link CompletableTask})
 *       cannot be a transition target and throws
 *       {@link UnsupportedOperationException}; an intermediate task
 *       ({@link CompletableSubTask}) installs the callable into a delegating
 *       action and runs it.</li>
 *   <li>{@link #createCompletionStage(Executor)} — factory for the next stage in
 *       a composition chain.</li>
 * </ul>
 *
 * <h2>Composition methods</h2>
 * <p>
 * The asynchronous composition methods of {@link Promise}
 * ({@code thenApplyAsync}, {@code handleAsync}, {@code thenComposeAsync},
 * {@code exceptionallyComposeAsync}, {@code whenCompleteAsync},
 * {@code exceptionallyAsync}) are overridden here with implementations tuned for
 * the blocking-task model: instead of building deeply nested
 * {@code CompletionStage} wrappers, each composition step creates a single
 * {@code AbstractCompletableTask} (via {@link #newSubTask(Executor)}) and wires
 * the success/failure callbacks directly through {@link #addCallbacks}.
 * </p>
 *
 * <h2>Cancellation</h2>
 * <p>
 * {@link #cancel(boolean)} cancels the underlying {@link RunnableFuture} and, if
 * an {@linkplain #intermediateStage intermediate stage} has been recorded (as done
 * by {@code thenComposeAsync} to track the stage returned by the user function),
 * that stage is cancelled as well, so work is not left running after the consumer
 * is no longer interested.
 * </p>
 *
 * @param <T> the type of the successfully executed task result
 * @see CompletableTask
 * @see CompletableSubTask
 * @see Promise
 */
abstract class AbstractCompletableTask<T> extends PromiseAdapterExtended<T>
                                          implements Promise<T> {

    /** Tracks completion state and notifies dependent stages. */
    private final CallbackRegistry<T> callbackRegistry = new CallbackRegistry<>();

    /** The underlying executable future wrapping this stage's action. */
    protected final RunnableFuture<T> task;

    /**
     * Creates a task with the given default executor and action.
     * <p>
     * The action is wrapped in a {@link FutureTask} whose {@code set} /
     * {@code setException} hooks are overridden to resolve this promise
     * (via {@link #success(Object)} / {@link #failure(Throwable)}) as soon as the
     * future completes.
     * </p>
     *
     * @param defaultExecutor the executor used for subsequent asynchronous composition steps
     * @param action          the action this stage executes
     */
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

    /**
     * The stage currently composing this promise's result, if any.
     * <p>
     * Set by {@code thenComposeAsync} / {@code exceptionallyComposeAsync} to the
     * stage returned by the user function so that {@link #cancel(boolean)} can
     * propagate cancellation to it.
     * </p>
     */
    private volatile CompletionStage<?> intermediateStage;

    /**
     * Triggers this stage's action. Implemented by subclasses: the root task
     * ({@link CompletableTask}) does not support transitions and throws
     * {@link UnsupportedOperationException}; the intermediate task
     * ({@link CompletableSubTask}) installs the given callable and runs it.
     *
     * @param code the action to execute for this stage
     */
    abstract void fireTransition(Callable<T> code);

    /**
     * {@inheritDoc}
     * <p>
     * Cancels the underlying {@link RunnableFuture} and, if an intermediate
     * composition stage is present, cancels it too.
     * </p>
     */
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

    /** {@inheritDoc} */
    @Override
    public boolean isCancelled() {
        return task.isCancelled();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDone() {
        return task.isDone();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Blocks until the task completes, unwrapping any {@link ExecutionException}
     * to rethrow the original cause.
     * </p>
     */
    @Override
    public T get() throws InterruptedException, ExecutionException {
        try {
            return task.get();
        } catch (ExecutionException ex) {
            throw rewrapExecutionException(ex);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Blocks until the task completes or the timeout elapses, unwrapping any
     * {@link ExecutionException} to rethrow the original cause.
     * </p>
     */
    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        try {
            return task.get(timeout, unit);
        } catch (ExecutionException ex) {
            throw rewrapExecutionException(ex);
        }
    }

    /** {@inheritDoc} */
    @Override 
    public boolean isIn(State state) {
        return CompletionStageAPI.current().isInState(task, state, true);
    }

    /** {@inheritDoc} */
    @Override 
    public boolean isIn(Set<State> states) {
        return CompletionStageAPI.current().isInState(task, states, true);
    }

    /** {@inheritDoc} */
    @Override 
    public T resultNow() {
        return CompletionStageAPI.current().resultNow(task, true);
    }

    /** {@inheritDoc} */
    @Override 
    public Throwable exceptionNow() {
        return CompletionStageAPI.current().exceptionNow(task, true);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCompletedExceptionally() {
        return callbackRegistry.isFailure();
    }

    /**
     * Resolves this promise successfully with the given result and notifies
     * registered dependents.
     *
     * @param result the value to complete with
     * @return {@code true} if this invocation performed the transition
     */
    boolean success(T result) {
        return callbackRegistry.success(result);
    }

    /**
     * Resolves this promise exceptionally with the given throwable and notifies
     * registered dependents.
     *
     * @param ex the exception to complete with
     * @return {@code true} if this invocation performed the transition
     */
    boolean failure(Throwable ex) {
        return callbackRegistry.failure(ex);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return String.format("%s@%d[%s]", getClass().getSimpleName(), System.identityHashCode(this), task);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to minimize wrapper layers: the function is registered directly on
     * a fresh sub-task rather than routed through {@code handleAsync}.
     * </p>
     */
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

    /**
     * {@inheritDoc}
     * <p>
     * Overridden symmetrically with {@link #thenApplyAsync(Function, Executor)}:
     * the recovery function is registered directly on a fresh sub-task.
     * </p>
     */
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

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to wire both the result and exception paths of the handler directly
     * onto a fresh sub-task. Exceptions thrown by the handler itself are propagated,
     * preserving {@code CompletableFuture} semantics.
     * </p>
     */
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

    /**
     * {@inheritDoc}
     * <p>
     * Implemented with two cooperating stages: a transient {@code Void} stage that
     * invokes the user function, and the actual {@code nextStage} that is returned
     * to the caller. The transient stage is enlisted on {@code nextStage} before the
     * function runs so that cancelling {@code nextStage} also cancels the stage the
     * function returned, avoiding orphaned work.
     * </p>
     */
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

    /**
     * {@inheritDoc}
     * <p>
     * Symmetric to {@link #thenComposeAsync(Function, Executor)} but on the failure
     * path: the recovery function is invoked only if this stage completed
     * exceptionally. A throw from the recovery function replaces the original
     * exception (the original is attached as suppressed), matching
     * {@code CompletionStage.composeExceptionally} semantics.
     * </p>
     */
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

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to run the action on both the success and failure paths directly on
     * a fresh sub-task. An exception thrown by the action overrides a normal result
     * but is attached as suppressed to an existing failure, following
     * {@code CompletableFuture} behavior.
     * </p>
     */
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

    /**
     * {@inheritDoc}
     * <p>
     * Returns a new {@link CompletableFuture} that mirrors this promise's outcome.
     * </p>
     */
    @Override
    public CompletableFuture<T> toCompletableFuture() {
        CompletableFuture<T> result = new CompletableFuture<>();
        whenComplete((r, e) -> iif(e == null ? result.complete(r) : result.completeExceptionally(e)));
        return result;
    }

    /**
     * Implements {@code applyToEitherAsync}: two source stages race, and the first
     * to complete drives a fresh sub-task whose result is mapped by {@code fn}.
     * <p>
     * Exists as a separate method (rather than inlining into
     * {@code runAfterEitherAsync}) to reconcile generics with the unexpectedly typed
     * {@code other} parameter; the alternative would be to ignore a compiler warning.
     * </p>
     *
     * @param <R>      the common result type of the racing stages
     * @param <U>      the mapped result type
     * @param first    the first racing stage
     * @param second   the second racing stage
     * @param fn       the function applied to whichever stage wins
     * @param executor the executor for the resulting stage
     * @return a promise of the mapped winner
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

    /**
     * Creates the next stage in a composition chain.
     *
     * @param <U>      the result type of the new stage
     * @param executor the default executor for the new stage's asynchronous composition steps
     * @return a fresh {@code AbstractCompletableTask}
     */
    abstract protected <U> AbstractCompletableTask<U> createCompletionStage(Executor executor);

    /**
     * Creates a fresh sub-task, choosing the default executor: an explicitly supplied
     * executor wins, but {@code SAME_THREAD_EXECUTOR} is never used as a default —
     * the current stage's default executor is preserved instead.
     *
     * @param <U>      the result type of the new stage
     * @param executor the requested executor, or {@code SAME_THREAD_EXECUTOR}
     * @return a fresh sub-task
     */
    private <U> AbstractCompletableTask<U> newSubTask(Executor executor) {
        // Preserve default async executor, or use user-supplied executor as default
        // But don't let SAME_THREAD_EXECUTOR to be a default async executor
        return createCompletionStage(executor == SAME_THREAD_EXECUTOR ? getDefaultExecutor() : executor);
    }

    /**
     * Returns a consumer that fires this stage's transition with the value produced
     * by the given converter.
     *
     * @param <U>       the input type of the converter
     * @param converter maps the incoming value to this stage's result type
     * @return a consumer triggering the transition
     */
    private <U> Consumer<? super U> runTransition(Function<? super U, ? extends T> converter) {
        return u -> fireTransition(() -> converter.apply(u)); 
    }

    /**
     * Builds a {@link BiConsumer} that dispatches to {@code onResult} when the
     * throwable is {@code null}, and to {@code onError} otherwise.
     */
    private static <U, V> BiConsumer<U, V> biConsumer(Consumer<? super U> onResult, Consumer<? super V> onError) {
        return (u, v) -> {
            if (null == v) {
                onResult.accept(u);
            } else {
                onError.accept(v);
            }
        };
    }

    /**
     * Unwraps a nested {@link ExecutionException} / {@link CompletionException} down to
     * the original cause and rewraps it in a single {@link ExecutionException}.
     */
    private static ExecutionException rewrapExecutionException(ExecutionException ex) {
        return wrapExecutionException( 
                   unwrapCompletionException(
                       unwrapExecutionException(ex)
                   )
               );
    }    

    /**
     * Registers the success/failure callbacks of this stage onto the given target stage.
     *
     * @param <U>             the target result type
     * @param targetStage     the stage that will receive the outcome
     * @param successCallback maps a successful result to the target's result
     * @param failureCallback maps an exception to the target's result
     * @param executor        the executor for the target's asynchronous steps
     * @return the target stage
     */
    private <U> AbstractCompletableTask<U> addCallbacks(AbstractCompletableTask<U> targetStage,
                                                        Function<? super T, ? extends U> successCallback, 
                                                        Function<Throwable, ? extends U> failureCallback,
                                                        Executor executor) {
        callbackRegistry.addCallbacks(targetStage, successCallback, failureCallback, executor);
        return targetStage;
    }
}