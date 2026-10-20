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

import java.util.concurrent.RunnableFuture;

/**
 * A hybrid interface that unifies the {@link Promise} API with the
 * {@link RunnableFuture} contract.
 *
 * <p>
 * This bridge type represents an asynchronous task that can be simultaneously:
 * </p>
 * <ul>
 *   <li><b>Executed by an executor</b> &mdash; as a {@link Runnable} submitted to a
 *       {@link java.util.concurrent.ExecutorService} (via {@link RunnableFuture});</li>
 *   <li><b>Consumed asynchronously</b> &mdash; through the fluent {@link Promise} API
 *       ({@code thenApply}, {@code thenCompose}, {@code dependOn}, cancellation
 *       propagation, and all other {@code Promise} operators).</li>
 * </ul>
 *
 * <p>
 * Instances are created internally by {@link TaskExecutorService} implementations
 * (see {@link ThreadPoolTaskExecutor} and {@link TaskExecutors.TaskExecutorServiceAdapter})
 * via their {@code newTaskFor(...)} hooks, so that {@code submit(...)} calls return
 * fully-featured {@link Promise}s while the underlying task still participates in the
 * standard {@code ExecutorService} execution machinery &mdash; including
 * {@link java.util.concurrent.CompletionService} and
 * {@link TaskCompletionService}.
 * </p>
 *
 * @param <T> the result type of the asynchronous computation
 * @see Promise
 * @see RunnableFuture
 * @see TaskExecutorService
 * @see TaskExecutors#newRunnablePromise(java.util.concurrent.Executor, java.util.concurrent.Callable)
 */
public interface RunnablePromise<T> extends Promise<T>, RunnableFuture<T> {

}
