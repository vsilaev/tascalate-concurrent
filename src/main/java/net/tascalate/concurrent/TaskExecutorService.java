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
import java.util.concurrent.ExecutorService;

/**
 * A specialization of {@link ExecutorService} that returns {@link Promise}s from its
 * {@code submit} methods instead of standard {@link java.util.concurrent.Future}s.
 * <p>
 * This allows seamless integration with the Tascalate Concurrent library's asynchronous
 * programming model, enabling fluent chaining, cancellation propagation, and advanced
 * combinator operations directly on the results of submitted tasks.
 * </p>
 * <p>
 * Instances can be created via the factory methods in {@link TaskExecutors} or by
 * instantiating {@link ThreadPoolTaskExecutor} directly. Existing standard
 * {@code ExecutorService}s can be adapted using {@link TaskExecutors#adapt(ExecutorService)}.
 * </p>
 *
 * @author vsilaev
 * @see TaskExecutors
 * @see ThreadPoolTaskExecutor
 */
public interface TaskExecutorService extends ExecutorService {
    
    /**
     * Submits a value-returning task for execution and returns a {@link Promise}
     * representing the pending result of the task.
     *
     * @param <T>  the type of the task's result
     * @param task the task to submit
     * @return a {@link Promise} that can be used to wait for the result and attach callbacks
     */
    <T> Promise<T> submit(Callable<T> task);

    /**
     * Submits a {@link Runnable} task for execution and returns a {@link Promise}
     * representing the specified result upon successful completion.
     *
     * @param <T>    the type of the result
     * @param task   the task to submit
     * @param result the result to return upon successful completion
     * @return a {@link Promise} that can be used to wait for the result and attach callbacks
     */
    <T> Promise<T> submit(Runnable task, T result);

    /**
     * Submits a {@link Runnable} task for execution and returns a {@link Promise}
     * representing the completion of the task. The promise will resolve to {@code null}
     * upon successful completion.
     *
     * @param task the task to submit
     * @return a {@link Promise} that can be used to wait for completion and attach callbacks
     */
    Promise<?> submit(Runnable task);
}