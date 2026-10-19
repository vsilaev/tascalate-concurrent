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

import java.util.concurrent.Callable;

/**
 * A retriable operation that receives the current {@link RetryContext} before every
 * attempt, may adapt its behavior per attempt, and returns a value subject to the
 * {@link RetryPolicy} validation.
 * <p>
 * Use this interface instead of {@link Callable} when the operation is <i>contextual</i>;
 * plain {@link Callable}s (convertible via {@link #from(Callable)}) are for operations
 * that behave identically on every iteration.
 * </p>
 *
 * @param <V> the type of the value returned by the operation
 * @param <T> the result type tracked by the {@link RetryPolicy}; the context passed to
 *            {@link #call(RetryContext)} is a {@code RetryContext<T>}, and the policy
 *            observes values of this type (or a supertype) when deciding whether to retry
 * @see RetryRunnable
 * @see Promises#retry(RetryCallable, java.util.concurrent.Executor, RetryPolicy)
 */
@FunctionalInterface
public interface RetryCallable<V, T> {

    /**
     * Performs one attempt of the operation.
     *
     * @param ctx the state of the retry loop before this attempt
     * @return the attempt result, to be validated by the retry policy
     * @throws Exception if the attempt fails; subject to the policy's retry/abort rules
     */
    V call(RetryContext<T> ctx) throws Exception;

    /**
     * Adapts a context-free {@link Callable} to a {@code RetryCallable} that ignores
     * the retry context.
     *
     * @param callable the operation to adapt
     * @param <V>      the result type of the operation
     * @param <T>      the result type tracked by the policy (unused by the adapter)
     * @return a contextual wrapper delegating to the given callable
     */
    public static <V, T> RetryCallable<V, T> from(Callable<? extends V> callable) {
        return ctx -> callable.call();
    }

    /**
     * Adapts a contextual, result-less {@link RetryRunnable} to a {@code RetryCallable}
     * returning {@code null} (a {@code Void} result).
     *
     * @param runnable the operation to adapt
     * @return a callable wrapper delegating to the given runnable
     */
    public static RetryCallable<Void, Void> from(RetryRunnable runnable) {
        return ctx -> { 
            runnable.run(ctx); 
            return null; 
        };
    }

    /**
     * Adapts a context-free {@link Runnable} to a {@code RetryCallable} returning
     * {@code null} (a {@code Void} result).
     *
     * @param runnable the operation to adapt
     * @return a callable wrapper delegating to the given runnable
     */
    public static RetryCallable<Void, Void> from(Runnable runnable) {
        return RetryCallable.from(RetryRunnable.from(runnable));
    }
}