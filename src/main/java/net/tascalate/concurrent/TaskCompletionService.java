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
import java.util.concurrent.CompletionService;
import java.util.concurrent.TimeUnit;

/**
 * A specialization of {@link CompletionService} that returns {@link Promise}s
 * instead of standard {@link java.util.concurrent.Future}s for submitted tasks and retrieved results.
 * <p>
 * This interface unseparates the submission of asynchronous tasks from the consumption of their results,
 * allowing tasks to be submitted to an underlying {@link java.util.concurrent.Executor} while results
 * are retrieved in completion order via {@link #take()} or {@link #poll()}.
 * </p>
 *
 * @param <V> the type of the values returned by the submitted tasks
 * @author vsilaev
 * @see TaskExecutorCompletionService
 */
public interface TaskCompletionService<V> extends CompletionService<V> {
    
    /**
     * {@inheritDoc}
     * @return a {@link Promise} representing the pending result of the task
     */
    Promise<V> submit(Callable<V> task);

    /**
     * {@inheritDoc}
     * @return a {@link Promise} representing the pending result of the task
     */
    Promise<V> submit(Runnable task, V result);

    /**
     * {@inheritDoc}
     * @return a {@link Promise} representing the result of the next completed task
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    Promise<V> take() throws InterruptedException;

    /**
     * {@inheritDoc}
     * @return a {@link Promise} representing the result of the next completed task,
     *         or {@code null} if no task has completed yet
     */
    Promise<V> poll();

    /**
     * {@inheritDoc}
     * @return a {@link Promise} representing the result of the next completed task,
     *         or {@code null} if the specified waiting time elapses before a task completes
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    Promise<V> poll(long timeout, TimeUnit unit) throws InterruptedException;
}