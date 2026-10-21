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

import static net.tascalate.concurrent.AsyncInternals.selectSecond;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import net.tascalate.concurrent.decorators.ExecutorBoundPromise;

/**
 * Concrete implementation of {@link Promise} for long-running blocking tasks.
 *
 * <p>
 * A {@code CompletableTask} is the <i>root</i> of an asynchronous composition chain:
 * it wraps a {@link Callable} that runs on an {@link Executor} and produces a
 * {@link Promise} that can be further composed with the library's combinators.
 * </p>
 *
 * <h2>Creation</h2>
 * <p>Instances are created through static factory methods, not directly:</p>
 * <ul>
 *   <li>{@link #submit(Callable, Executor)} — the common entry point: runs a blocking
 *       {@link Callable} asynchronously and returns a promise of its result.</li>
 *   <li>{@link #runAsync(Runnable, Executor)} / {@link #supplyAsync(Supplier, Executor)}
 *       — mirrors of the corresponding {@link java.util.concurrent.CompletableFuture}
 *       factories.</li>
 *   <li>{@link #completed(Object, Executor)} — an already-resolved promise.</li>
 *   <li>{@link #asyncOn(Executor)} / {@link #asyncOn(Executor, boolean)} — an
 *       already-resolved, value-less promise that carries an executor for subsequent
 *       asynchronous composition steps.</li>
 *   <li>{@link #waitFor(CompletionStage, Executor)} — adapts an arbitrary
 *       {@link CompletionStage} to this library's executor-bound model.</li>
 *   <li>{@link #delay(long, TimeUnit, Executor)} / {@link #delay(Duration, Executor)} —
 *       a promise that resolves after a delay.</li>
 * </ul>
 *
 * <h2>Executor binding</h2>
 * <p>
 * Every {@code CompletableTask} is "bound" to a default {@link Executor}. Any
 * asynchronous composition method invoked on the resulting promise
 * ({@code thenApplyAsync}, {@code thenAcceptAsync}, {@code whenCompleteAsync}, etc.)
 * runs its function on this executor unless an explicit executor is passed. The binding
 * is <i>transitive</i>: nested composition calls inherit the same executor unless
 * overridden (see {@link #completed(Object, Executor)} for an example).
 * </p>
 *
 * <h2>Role in the chain</h2>
 * <p>
 * As the root, a {@code CompletableTask} cannot itself be the <i>target</i> of a
 * transition: {@link #fireTransition(Callable)} throws
 * {@link UnsupportedOperationException}. Transitions to subsequent stages are handled
 * by {@link CompletableSubTask} instances produced via
 * {@link #createCompletionStage(Executor)}.
 * </p>
 *
 * @param <T> the type of the successfully executed task result
 * @author vsilaev
 * @see CompletableSubTask
 * @see AbstractCompletableTask
 */
public class CompletableTask<T> extends AbstractCompletableTask<T> {

    /**
     * Creates a {@code CompletableTask}; for internal use only.
     *
     * @param executor the default {@link Executor} used to run functions passed to
     *                 asynchronous composition methods
     * @param callable the {@link Callable} that completes this task
     */
    protected CompletableTask(Executor executor, Callable<T> callable) {
        super(executor, callable);
    }

    /**
     * Returns a resolved {@link Promise} with the specified value; the promise is
     * "bound" to the specified executor. That is, any function passed to the promise's
     * composition methods (such as {@code thenApplyAsync} / {@code thenAcceptAsync} /
     * {@code whenCompleteAsync} etc.) is executed using this executor unless an executor
     * is overridden via an explicit composition method parameter. Moreover, nested
     * composition calls use the same executor unless it is redefined:
     * <pre>{@code
     * CompletableTask
     *   .complete("Hello!", myExecutor)
     *   .thenApplyAsync(myMapper)
     *   .thenApplyAsync(myTransformer)
     *   .thenAcceptAsync(myConsumer)
     *   .thenRunAsync(myAction)
     * }</pre>
     * All of {@code myMapper}, {@code myTransformer}, {@code myConsumer} and
     * {@code myAction} are executed using {@code myExecutor}.
     *
     * @param <T>             the type of the successfully executed task result
     * @param value           the task result
     * @param defaultExecutor the default {@link Executor} used to run functions passed to
     *                        asynchronous composition methods
     * @return a resolved {@link Promise} with the supplied value, bound to the executor
     */
    public static <T> Promise<T> completed(T value, Executor defaultExecutor) {
        CompletableTask<T> result = new CompletableTask<>(defaultExecutor, () -> value);
        SAME_THREAD_EXECUTOR.execute(result.task);
        return result;
    }

    /**
     * Returns a resolved no-value {@link Promise} that is "bound" to the specified
     * executor. Any function passed to the promise's composition methods
     * ({@code thenApplyAsync} / {@code thenAcceptAsync} / {@code whenCompleteAsync} etc.)
     * is executed using this executor unless overridden, and nested composition calls
     * use the same executor:
     * <pre>{@code
     * CompletableTask
     *   .asyncOn(myExecutor)
     *   .thenApplyAsync(myValueGenerator)
     *   .thenAcceptAsync(myConsumer)
     *   .thenRunAsync(myAction)
     * }</pre>
     * All of {@code myValueGenerator}, {@code myConsumer} and {@code myAction} are
     * executed using {@code myExecutor}.
     *
     * @param executor the default {@link Executor} used to run functions passed to
     *                 asynchronous composition methods
     * @return a resolved non-value {@link Promise} bound to the specified executor
     */
    public static Promise<Void> asyncOn(Executor executor) {
        return completed(null, executor);
    }

    /**
     * Returns a resolved no-value {@link Promise} that is "bound" to the specified
     * executor, as {@link #asyncOn(Executor)}, with an additional guarantee: when
     * {@code enforceDefaultAsync} is {@code true}, the default executor is propagated to
     * dependent promises even if a corresponding transition was executed on another
     * executor (via a composition method with an explicit executor argument).
     *
     * @param executor          the default {@link Executor} used to run functions passed to
     *                          asynchronous composition methods
     * @param enforceDefaultAsync if {@code true}, the default executor is propagated to
     *                          dependent promises even across transitions that ran on other
     *                          executors
     * @return a resolved non-value {@link Promise} bound to the specified executor
     */    
    public static Promise<Void> asyncOn(Executor executor, boolean enforceDefaultAsync) {
        Promise<Void> result = completed(null, executor);
        if (enforceDefaultAsync) {
            class EnforcedExecutorBoundPromise<T> extends ExecutorBoundPromise<T> {
                public EnforcedExecutorBoundPromise(Promise<T> delegate) {
                    super(delegate, executor);
                }

                @Override
                protected <U> Promise<U> wrapNew(CompletionStage<U> original) {
                    return new EnforcedExecutorBoundPromise<>((Promise<U>)original);
                }

                // No need to overwrite dependent(...) results
                // while underlying delegate is undecorateable promise (this)
                @Override
                public Promise<T> unwrap() {
                    // We are wrapping always raw promise and
                    // it's explicitly forbidden to remove own behavior
                    // via enforceDefaultAsync
                    return this;
                }

                @Override
                public Promise<T> raw() {
                    // Same as for unwrap
                    return this;
                }
            }
            return new EnforcedExecutorBoundPromise<>(result);
        } else {
            return result;
        }
    }

    /**
     * Returns a new {@link Promise} that is asynchronously resolved by a task running
     * in the given executor after it runs the given action.
     *
     * @param runnable the action to run before resolving the returned {@link Promise}
     * @param executor the executor to use for asynchronous execution
     * @return the new {@link Promise}
     */
    public static Promise<Void> runAsync(Runnable runnable, Executor executor) {
        return submit(() -> { runnable.run(); return null; }, executor);
    }

    /**
     * Returns a new {@link Promise} that is asynchronously resolved by a task running in
     * the given executor with the value obtained by calling the given {@link Supplier}.
     *
     * @param <U>      the function's return type
     * @param supplier a function returning the value used to resolve the returned
     *                 {@link Promise}
     * @param executor the executor to use for asynchronous execution
     * @return the new {@link Promise}
     */
    public static <U> Promise<U> supplyAsync(Supplier<U> supplier, Executor executor) {
        return submit(supplier::get, executor);
    }

    /**
     * Returns a new {@link Promise} that is asynchronously resolved by a task running in
     * the given executor with the value obtained by calling the given {@link Callable}.
     * <p>
     * This is the primary entry point for running a blocking, long-running computation
     * asynchronously and chaining further work onto its result.
     * </p>
     *
     * @param <U>      the function's return type
     * @param call     a function returning the value used to resolve the returned
     *                 {@link Promise}
     * @param executor the executor to use for asynchronous execution
     * @return the new {@link Promise}
     */
    public static <U> Promise<U> submit(Callable<U> call, Executor executor) {
        CompletableTask<U> result = new CompletableTask<>(executor, call);
        executor.execute(result.task);
        return result;
    }

    /**
     * Waits for the given {@link CompletionStage} and returns its result as a
     * {@link Promise} bound to the given executor, without enlisting the stage as a
     * dependent.
     *
     * @param <T>      the stage result type
     * @param stage    the stage to wait for
     * @param executor the executor binding the returned promise
     * @return a promise of the stage's result
     */
    public static <T> Promise<T> waitFor(CompletionStage<T> stage, Executor executor) {
        return waitFor(stage, executor, false);
    }

    /**
     * Waits for the given {@link CompletionStage} and returns its result as a
     * {@link Promise} bound to the given executor.
     *
     * @param <T>            the stage result type
     * @param stage          the stage to wait for
     * @param executor       the executor binding the returned promise
     * @param dependentStage whether to enlist the stage as a dependent for cancellation
     * @return a promise of the stage's result
     */
    public static <T> Promise<T> waitFor(CompletionStage<T> stage, Executor executor, boolean dependentStage) {
        return asyncOn(executor)
               .dependent()
               .thenCombine(stage, selectSecond(), enlistParamOrNone(dependentStage))
               .unwrap();
    }

    /**
     * Returns a promise that resolves successfully with the given delay after it was
     * created, bound to the given executor.
     *
     * @param timeout  the amount of the delay
     * @param unit     the time unit of the delay
     * @param executor the executor binding the returned promise
     * @return a promise of the elapsed {@link Duration}
     */
    public static Promise<Duration> delay(long timeout, TimeUnit unit, Executor executor) {
        return delay(AsyncInternals.toDuration(timeout, unit), executor);
    }

    /**
     * Returns a promise that resolves successfully with the given delay after it was
     * created, bound to the given executor.
     *
     * @param duration the delay
     * @param executor the executor binding the returned promise
     * @return a promise of the elapsed {@link Duration}
     */
    public static Promise<Duration> delay(Duration duration, Executor executor) {
        return asyncOn(executor)
               .dependent()
               .thenCombineAsync(AsyncInternals.delay(duration), selectSecond(), enlistParamOrNone(true))
               .unwrap();
    }

    /**
     * Runs the underlying task. Typically invoked by the executor the task was
     * submitted to.
     */
    protected void runTask() {
        task.run();
    }

    /**
     * {@inheritDoc}
     * <p>
     * A root task cannot be the target of a transition, so this method always throws
     * {@link UnsupportedOperationException}. Transitions are handled by the
     * {@link CompletableSubTask} stages produced via
     * {@link #createCompletionStage(Executor)}.
     * </p>
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    void fireTransition(Callable<T> code) {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a nested sub-task for a composition method.
     *
     * @param <U>      the result type of the new stage
     * @param executor the default executor for asynchronous composition methods of the
     *                 nested sub-task
     * @return an instance of {@link CompletableSubTask} bound to the specified executor
     */
    @Override
    protected <U> AbstractCompletableTask<U> createCompletionStage(Executor executor) {
        return new CompletableSubTask<U>(executor);
    }

    /**
     * Builds the enlistment option set: {@code PARAM_ONLY} when the parameter should be
     * enlisted for cancellation, {@code NONE} otherwise.
     */
    private static Set<Promise.Origin> enlistParamOrNone(boolean enlistParam) {
        return enlistParam ? Promise.Origin.PARAM_ONLY : Promise.Origin.NONE;
    }
}