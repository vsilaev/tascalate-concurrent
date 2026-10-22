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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

import net.tascalate.concurrent.decorators.CompletableFutureDecorator;

/**
 * A wrapper around a {@link CompletableFuture} that adapts it to the {@link Promise} API.
 * <p>
 * This class provides the foundational implementation for wrapping standard Java
 * {@link CompletableFuture}s. It ensures that composition methods (like {@code thenCompose})
 * correctly propagate cancellation to the underlying stages, addressing a known limitation
 * in the default JDK implementation where such propagation is often missing.
 * </p>
 *
 * @param <T> the type of the successfully resolved promise value
 * @author vsilaev
 * @see CompletableFutureDecorator
 * @see Promise
 */
public class CompletableFutureWrapper<T> extends CompletableFutureDecorator<T> {

    /**
     * Creates a new wrapper with a newly created, uncompleted {@link CompletableFuture}.
     */
    protected CompletableFutureWrapper() {
        super();
    }

    /**
     * Creates a new wrapper around the specified delegate {@link CompletableFuture}.
     *
     * @param delegate the underlying future to wrap
     */
    protected CompletableFutureWrapper(CompletableFuture<T> delegate) {
        super(delegate);
    }

    /**
     * Completes this promise successfully with the given value.
     *
     * @param value the value to complete with
     * @return {@code true} if this invocation caused the completion, {@code false} otherwise
     */
    protected boolean success(T value) {
        return delegate.complete(value);
    }

    /**
     * Completes this promise exceptionally with the given throwable.
     *
     * @param ex the exception to complete with
     * @return {@code true} if this invocation caused the completion, {@code false} otherwise
     */
    protected boolean failure(Throwable ex) {
        return delegate.completeExceptionally(ex);
    }

    /**
     * Completes this promise either successfully or exceptionally based on the arguments.
     *
     * @param value the value to complete with (ignored if {@code ex} is non-null)
     * @param ex    the exception to complete with (if non-null)
     * @return {@code true} if this invocation caused the completion, {@code false} otherwise
     */
    boolean complete(T value, Throwable ex) {
        return null == ex ? success(value) : failure(ex);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a concrete subclass that neither completes nor cancels this wrapper,
     * preserving the isolation of the underlying delegate.
     * </p>
     */
    @Override
    public CompletableFuture<T> toCompletableFuture() {
        // Return concrete subclass that neither completes nor cancels this wrapper
        return (CompletableFuture<T>)delegate.thenApply(Function.identity());
    }
    // Report self-origin

    /**
     * {@inheritDoc}
     * <p>
     * Reports self-origin, indicating that this wrapper is the root of the composition chain.
     * </p>
     */
    @Override
    public CompletionStage<T> α() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected <U> Promise<U> wrapNew(CompletionStage<U> original) {
        return new CompletableFutureWrapper<>((CompletableFuture<U>)original);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to ensure proper cancellation propagation. By default, the
     * {@link CompletableFuture} API does not interrupt a promise returned from
     * {@code thenCompose(fn)}. This implementation captures the resulting stage
     * and attaches an {@code onCancel} hook to propagate the cancellation.
     * </p>
     */
    @Override
    public <U> Promise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        CompletionStageRef<U> ref = new CompletionStageRef<>();
        return super.thenCompose(ref.captureResult(fn)).onCancel(ref.cancel);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to ensure proper cancellation propagation, similar to {@link #thenCompose(Function)}.
     * </p>
     */
    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        CompletionStageRef<U> ref = new CompletionStageRef<>();
        return super.thenComposeAsync(ref.captureResult(fn)).onCancel(ref.cancel);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to ensure proper cancellation propagation, similar to {@link #thenCompose(Function)}.
     * </p>
     */
    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, 
                                           Executor executor) {
        CompletionStageRef<U> ref = new CompletionStageRef<>();
        return super.thenComposeAsync(ref.captureResult(fn), executor).onCancel(ref.cancel);        
    }

    /**
     * {@inheritDoc}
     * <p>
     * The default {@link CompletionStage} API implementation for {@code exceptionallyAsync} /
     * {@code exceptionallyCompose[Async]} does not handle cancellation well due to numerous
     * orchestrated internal calls (e.g., {@code handle} -&gt; {@code handleAsync} -&gt; {@code thenCompose}).
     * This implementation uses a safer, custom approach via {@link AsyncInternals}.
     * </p>
     */
    @Override
    public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return AsyncInternals.exceptionallyAsync(this, fn);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        return AsyncInternals.exceptionallyAsync(this, fn, executor);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return AsyncInternals.exceptionallyCompose(this, fn);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return AsyncInternals.exceptionallyComposeAsync(this, fn);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, 
                                                Executor executor) {
        return AsyncInternals.exceptionallyComposeAsync(this, fn, executor);
    }   
}