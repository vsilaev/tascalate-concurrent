/**
 * Original work: copyright 2009-2015 Lukáš Křečan
 * https://github.com/lukas-krecan/completion-stage
 * 
 * This class is based on the work create by Lukáš Křečan 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/lukas-krecan/completion-stage/blob/completion-stage-0.0.9/src/main/java/net/javacrumbs/completionstage/CompletionStageAdapter.java
 *
 * Modified work: copyright 2015-2026 Valery Silaev (http://vsilaev.com)
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

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An intermediate abstract base class that reduces the implementation burden 
 * for custom {@link Promise} subclasses to a minimal set of primitive asynchronous 
 * operations.
 * <p>
 * This class extends {@link AbstractPromise} and delegates almost all asynchronous 
 * composition methods down to just five core primitive methods. Subclasses only 
 * need to implement these five primitives to get a fully functioning, highly 
 * optimized {@link Promise}:
 * </p>
 * <ul>
 *   <li>{@link Promise#handleAsync(BiFunction, Executor)}</li>
 *   <li>{@link Promise#thenComposeAsync(Function, Executor)}</li>
 *   <li>{@link Promise#exceptionallyComposeAsync(Function, Executor)}</li>
 *   <li>{@link Promise#whenCompleteAsync(BiConsumer, Executor)}</li>
 *   <li>{@link #doApplyToEitherAsync(CompletionStage, CompletionStage, Function, Executor)}</li>
 * </ul>
 * <p>
 * Re-implementation of {@code exceptionallyComposeAsync} is optional but strongly 
 * recommended for optimal performance.
 * </p>
 *
 * @param <T> the type of the successfully resolved promise value
 * @author vsilaev
 * @see AbstractPromise
 * @see AbstractCompletableTask
 */
// Not public yet
abstract class AbstractMinimalPromise<T> extends AbstractPromise<T> {

    /**
     * Creates a new minimal promise with the specified default executor.
     *
     * @param defaultExecutor the executor to use for asynchronous methods
     */
    protected AbstractMinimalPromise(Executor defaultExecutor) {
        super(defaultExecutor);
    }

    // --- Delegating *Async methods to the 5 core primitives ---

    /** {@inheritDoc} */
    @Override
    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return handleAsync((r, e) -> null == e ? fn.apply(r) : forwardException(e), executor);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        // Symmetrical with thenApplyAsync
        return handleAsync((r, e) -> null == e ? r : fn.apply(e), executor);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return thenApplyAsync(consumerAsFunction(action), executor);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> thenRunAsync(Runnable action, Executor executor) {
        return thenApplyAsync(runnableAsFunction(action), executor);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, Executor executor) {
        return handleAsync(
            (r, e) -> null == e ? predicate.test(r) ? r : forwardException(errorSupplier.apply(r)) : forwardException(e), 
            executor
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                              BiFunction<? super T, ? super U, ? extends V> fn, 
                                              Executor executor) {
        return thenCompose(result1 -> other.thenApplyAsync(result2 -> fn.apply(result1, result2), executor));
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                             Function<? super T, U> fn,
                                             Executor executor) {
        return doApplyToEitherAsync(this, other, fn, executor);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                                           Consumer<? super T> action,
                                           Executor executor) {
        return applyToEitherAsync(other, consumerAsFunction(action), executor);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                             Runnable action, 
                                             Executor executor) {
        return doApplyToEitherAsync(this, other, runnableAsFunction(action), executor);
    }

    /**
     * Implements the core primitive for racing two stages and applying a function 
     * to the first one to complete.
     *
     * @param <R>      the common result type of the racing stages
     * @param <U>      the mapped result type
     * @param first    the first racing stage
     * @param second   the second racing stage
     * @param fn       the function applied to whichever stage wins
     * @param executor the executor for the resulting stage
     * @return a promise of the mapped winner
     */
    abstract protected <R, U> Promise<U> doApplyToEitherAsync(CompletionStage<? extends R> first,
                                                              CompletionStage<? extends R> second, 
                                                              Function<? super R, U> fn, 
                                                              Executor executor);

    /**
     * Throws a {@link java.util.concurrent.CompletionException} wrapping the given throwable.
     * Used to propagate exceptions through the primitive methods.
     *
     * @param <U> the expected return type (never actually returned)
     * @param e   the throwable to forward
     * @return never returns normally
     * @throws java.util.concurrent.CompletionException always
     */
    protected static <U> U forwardException(Throwable e) {
        throw AsyncInternals.wrapCompletionException(e);
    }

    /**
     * Returns a function that always throws a {@link CompletionException} 
     * wrapping its input.
     *
     * @param <U> the expected return type
     * @return a function that forwards exceptions
     */
    @SuppressWarnings("unchecked")
    protected static <U> Function<Throwable, U> forwardException() {
        return (Function<Throwable, U>)FORWARD_EXCEPTION;
    }

    /**
     * Adapts a {@link Consumer} to a {@link Function} that returns {@code null}.
     *
     * @param <V>    the input type
     * @param <R>    the return type (always {@code null})
     * @param action the consumer to adapt
     * @return the adapted function
     */
    protected static <V, R> Function<V, R> consumerAsFunction(Consumer<? super V> action) {
        return result -> {
            action.accept(result);
            return null;
        };
    }

    /**
     * Adapts a {@link Runnable} to a {@link Function} that returns {@code null}.
     *
     * @param <R>    the return type (always {@code null})
     * @param action the runnable to adapt
     * @return the adapted function
     */
    private static <R> Function<R, Void> runnableAsFunction(Runnable action) {
        return result -> {
            action.run();
            return null;
        };
    }

    /** Singleton instance of the exception-forwarding function. */
    private static final Function<Throwable, Object> FORWARD_EXCEPTION = 
        AbstractMinimalPromise::forwardException;
}