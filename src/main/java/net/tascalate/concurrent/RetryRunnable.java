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

/**
 * A retriable, result-less operation that receives the current {@link RetryContext}
 * before every attempt and may adapt its behavior to the attempt number or to the
 * outcome of the previous attempt.
 * <p>
 * Use this interface instead of {@link Runnable} when the operation is <i>contextual</i>;
 * plain {@link Runnable}s (convertible via {@link #from(Runnable)}) are for operations
 * that behave identically on every iteration.
 * </p>
 *
 * @see RetryCallable
 * @see Promises#retry(RetryRunnable, java.util.concurrent.Executor, RetryPolicy)
 */
@FunctionalInterface
public interface RetryRunnable {

    /**
     * Performs one attempt of the operation.
     *
     * @param ctx the state of the retry loop before this attempt (attempt index,
     *            duration / result / error of the previous attempt)
     */
    void run(RetryContext<Void> ctx);

    /**
     * Adapts a context-free {@link Runnable} to a {@code RetryRunnable} that ignores
     * the retry context.
     *
     * @param runnable the operation to adapt
     * @return a contextual wrapper delegating to the given runnable
     */
    public static RetryRunnable from(Runnable runnable) {
        return ctx -> runnable.run();
    }
}