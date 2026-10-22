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

import static net.tascalate.concurrent.AsyncInternals.iif;
import static net.tascalate.concurrent.AsyncInternals.unwrapCompletionException;
import static net.tascalate.concurrent.AsyncInternals.wrapCompletionException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import net.tascalate.concurrent.core.CompletionStageAPI;
import net.tascalate.concurrent.decorators.AbstractCompletionStageDecorator;

/**
 * A manually completable implementation of the {@link Promise} API.
 * <p>
 * This class acts as an adapter that allows external code to explicitly complete,
 * cancel, or fail the promise using standard completion methods. It is particularly
 * useful when bridging legacy callback-based APIs or when manual control over the
 * promise lifecycle is required.
 * </p>
 *
 * @param <T> the type of the successfully resolved promise value
 * @author vsilaev
 * @see CompletableFutureWrapper
 * @see Promise
 */
public class CompletablePromise<T> extends CompletableFutureWrapper<T> {

    /**
     * Creates a new, uncompleted {@link CompletablePromise}.
     */
    public CompletablePromise() {
        this(new CompletableFuture<>());
    }

    /**
     * Creates a new {@link CompletablePromise} wrapping the specified delegate.
     *
     * @param delegate the underlying {@link CompletableFuture} to wrap
     */
    public CompletablePromise(CompletableFuture<T> delegate) {
        super(delegate);
    }

    /**
     * Completes this promise successfully with the given value.
     *
     * @param value the value to complete with
     * @return {@code true} if this invocation caused the completion, {@code false} otherwise
     */
    public boolean complete(T value) {
        return success(value);
    }

    /**
     * Completes this promise exceptionally with the given throwable.
     *
     * @param ex the exception to complete with
     * @return {@code true} if this invocation caused the completion, {@code false} otherwise
     */
    public boolean completeExceptionally(Throwable ex) {
        return failure(ex);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean complete(T value, Throwable ex) {
        return super.complete(value, ex);
    }

    /**
     * Completes this promise asynchronously using the default executor of the delegate.
     * <p>
     * If the default executor cannot be overridden (e.g., in a custom {@link CompletableFuture}
     * subclass), this method ensures the execution is explicitly bound to the actual default
     * executor to maintain consistent behavior.
     * </p>
     *
     * @param supplier the supplier providing the value to complete with
     * @return a promise representing the asynchronous completion
     */
    public Promise<T> completeAsync(Supplier<? extends T> supplier) {
        CompletionStageAPI api = CompletionStageAPI.current();
        Promise<T> result = completeAsync(supplier, api.defaultExecutorOf(delegate));
        if (api.defaultExecutorOverridable() || delegate.getClass() == CompletableFuture.class) {
            return result; // use what is provided
        } else {
            // defaultExecutor might be changed in ad-hoc manner via subclassing
            // let us take back to the actual default executor
            return result.dependent()
                         .thenApplyAsync(Function.identity(), true) 
                         .unwrap();
        }
    }

    /**
     * Completes this promise asynchronously using the specified executor.
     *
     * @param supplier the supplier providing the value to complete with
     * @param executor the executor to run the supplier on
     * @return a promise representing the asynchronous completion
     */
    public Promise<T> completeAsync(Supplier<? extends T> supplier, Executor executor) {
        Promise<T> result = CompletableTask.submit(supplier::get, executor);
        result.whenComplete((r, e) -> {
            // Complete only if transition was not cancelled
            if (!result.isCancelled()) {
                complete(r, unwrapCompletionException(e));
            }
        });
        return result;
    }

    /**
     * Creates a new {@link CompletablePromise} that mirrors the state of this one.
     * <p>
     * If this promise is already completed, the copy is completed immediately with
     * the same result or exception. Otherwise, it registers a callback to complete
     * the copy when this promise completes.
     * </p>
     *
     * @return a new {@link CompletablePromise} mirroring this one
     */
    public CompletablePromise<T> copy() {
        CompletablePromise<T> result = wrapNew(new CompletableFuture<>());
        if (isDone()) {
            try {
                result.complete(join());
            } catch (CompletionException ex) {
                // Don't unwrap according API docs
                result.completeExceptionally(ex);
            } catch (Throwable ex) {
                // Wrap for CancellationException and unexpected exceptions
                result.completeExceptionally(wrapCompletionException(ex));
            }
            return result;
        } else {
            whenComplete(result::complete);
            return result;
        }
    }

    /**
     * Creates a minimal, read-only {@link Promise} that mirrors the state of this one.
     * <p>
     * Unlike {@link #copy()}, the returned promise cannot be manually completed by
     * external code. It serves as a safe, restricted view of this promise's outcome.
     * </p>
     *
     * @return a minimal, read-only {@link Promise}
     */
    public Promise<T> minimalPromise() {
        CompletableFutureWrapper<T> result = new CompletableFutureWrapper<>();
        // Should we cancel result along this?
        if (isDone()) {
            try {
                result.success(join());
            } catch (CompletionException ex) {
                // Don't unwrap according API docs
                result.failure(ex);
            } catch (Throwable ex) {
                // Wrap for CancellationException and unexpected exceptions
                result.failure(wrapCompletionException(ex));
            }
            return result;            
        } else {
            whenComplete(result::complete);
            return result;
        }
    }

    /**
     * Returns a minimal {@link CompletionStage} view of this promise.
     * <p>
     * This stage is strictly read-only and delegates composition to this promise,
     * avoiding the invalid {@code thenCompose} implementation of the raw delegate.
     * </p>
     *
     * @return a minimal {@link CompletionStage} view
     */
    public CompletionStage<T> minimalCompletionStage() {
        // Should pass this, not delegate - while delegate has invalid thenCompose impl.
        return new MinimalCompletionStage<>(this);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the underlying delegate itself, allowing this {@link CompletablePromise}
     * to be completed or cancelled directly via the standard {@link CompletableFuture} API.
     * </p>
     */
    @Override
    public CompletableFuture<T> toCompletableFuture() {
        // Returns the delegate itself, so this CompletablePromise may be completed / cancelled
        return delegate;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Reports the underlying delegate as the origin of the composition chain.
     * </p>
     */
    @Override
    public CompletionStage<T> α() {
        return delegate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected <U> CompletablePromise<U> wrapNew(CompletionStage<U> original) {
        return new CompletablePromise<>((CompletableFuture<U>)original);
    }

    /**
     * A minimal, read-only decorator for {@link CompletionStage}.
     * <p>
     * This class ensures that {@link #toCompletableFuture()} returns a new, independent
     * future, adhering to the standard Java implementation contract for minimal stages.
     * </p>
     */
    static class MinimalCompletionStage<T> extends AbstractCompletionStageDecorator<T, CompletionStage<T>> {
        
        /**
         * Creates a new minimal stage wrapping the given delegate.
         *
         * @param delegate the stage to wrap
         */
        public MinimalCompletionStage(CompletionStage<T> delegate) {
            super(delegate);
        }

        /** {@inheritDoc} */
        @Override
        protected <U> CompletionStage<U> wrapNew(CompletionStage<U> original) {
            return new MinimalCompletionStage<>(original);
        }

        /**
         * {@inheritDoc}
         * <p>
         * Returns an independent future according to existing Java implementation contracts.
         * </p>
         */
        @Override
        public CompletableFuture<T> toCompletableFuture() {
            // Should be independent future according to existing Java impl. 
            // http://hg.openjdk.java.net/jdk9/jdk9/jdk/file/tip/src/java.base/share/classes/java/util/concurrent/CompletableFuture.java 
            // - MinimalCompletionStage
            CompletableFuture<T> result = new CompletableFuture<>();
            whenComplete((r, e) -> iif(null == e ? result.complete(r) : result.completeExceptionally(e)));
            return result;
        }
    }
}