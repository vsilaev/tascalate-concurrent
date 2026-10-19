/**
 * Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.concurrent.var;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * An {@link ExecutorService} decorator that propagates relayed context variables
 * to all submitted tasks.
 * <p>
 * <b>Dynamic resolution:</b> the context snapshot is taken at the moment of each
 * {@code submit(...)}, {@code invokeAll(...)} and {@code invokeAny(...)} call — i.e.,
 * every task inherits the context that is active in the submitting thread at its
 * submission time. Lifecycle operations ({@code shutdown}, {@code awaitTermination}, etc.)
 * are delegated as-is and carry no context.
 * </p>
 *
 * @param <D> the type of the delegate executor service
 * @see ContextRelay#contextual(ExecutorService)
 */
class ContextualExecutorService<D extends ExecutorService>
        extends ContextualExecutor<D>
        implements ExecutorService {

    /**
     * Creates a decorator for the given executor service.
     *
     * @param delegate the executor service to wrap
     * @param ctxRelay the relay capturing context on each submission
     */
    ContextualExecutorService(D delegate, ContextRelay<?> ctxRelay) {
         super(delegate, ctxRelay);
     }

     /** {@inheritDoc} Delegates directly; carries no context. */
     @Override
     public void shutdown() {
         delegate.shutdown();
     }

     /** {@inheritDoc} Delegates directly; carries no context. */
     @Override
     public List<Runnable> shutdownNow() {
         return delegate.shutdownNow();
     }

     /** {@inheritDoc} Delegates directly; carries no context. */
     @Override
     public boolean isShutdown() {
         return delegate.isShutdown();
     }

     /** {@inheritDoc} Delegates directly; carries no context. */
     @Override
     public boolean isTerminated() {
         return delegate.isTerminated();
     }

     /** {@inheritDoc} Delegates directly; carries no context. */
     @Override
     public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
         return delegate.awaitTermination(timeout, unit);
     }

     /**
      * Submits the task wrapped with the context snapshot captured <b>at the moment
      * of this call</b>.
      *
      * @param task the task to submit
      * @param <T>  the result type of the task
      * @return a future representing the pending result
      */
     @Override
     public <T> Future<T> submit(Callable<T> task) {
         return delegate.submit(contextualCallable(task));
     }

     /**
      * Submits the task wrapped with the context snapshot captured <b>at the moment
      * of this call</b>.
      *
      * @param task   the task to submit
      * @param result the result to return upon completion
      * @param <T>    the result type
      * @return a future representing the pending result
      */
     @Override
     public <T> Future<T> submit(Runnable task, T result) {
         return delegate.submit(contextualRunnable(task), result);
     }

     /**
      * Submits the task wrapped with the context snapshot captured <b>at the moment
      * of this call</b>.
      *
      * @param task the task to submit
      * @return a future representing the pending completion
      */
     @Override
     public Future<?> submit(Runnable task) {
         return delegate.submit(contextualRunnable(task));
     }

     /**
      * Invokes all tasks, each wrapped with the context snapshot captured <b>at the
      * moment of this call</b>.
      *
      * @param tasks the tasks to invoke
      * @param <T>   the result type of the tasks
      * @return futures representing the results
      * @throws InterruptedException if the current thread is interrupted while waiting
      */
     @Override
     public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
         return delegate.invokeAll(contextualCallables(tasks));
     }

     /**
      * Invokes all tasks with a timeout, each wrapped with the context snapshot captured
      * <b>at the moment of this call</b>.
      *
      * @param tasks   the tasks to invoke
      * @param timeout the maximum time to wait
      * @param unit    the time unit of the timeout
      * @param <T>     the result type of the tasks
      * @return futures representing the results
      * @throws InterruptedException if the current thread is interrupted while waiting
      */
     @Override
     public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
             throws InterruptedException {
         return delegate.invokeAll(contextualCallables(tasks), timeout, unit);
     }

     /**
      * Invokes the tasks, each wrapped with the context snapshot captured <b>at the
      * moment of this call</b>, and returns the result of the one that completed first.
      *
      * @param tasks the tasks to invoke
      * @param <T>   the result type of the tasks
      * @return the result of the first completed task
      * @throws InterruptedException if the current thread is interrupted while waiting
      * @throws ExecutionException   if no task completes successfully
      */
     @Override
     public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
         return delegate.invokeAny(contextualCallables(tasks));
     }

     /**
      * Invokes the tasks with a timeout, each wrapped with the context snapshot captured
      * <b>at the moment of this call</b>, and returns the result of the one that completed first.
      *
      * @param tasks   the tasks to invoke
      * @param timeout the maximum time to wait
      * @param unit    the time unit of the timeout
      * @param <T>     the result type of the tasks
      * @return the result of the first completed task
      * @throws InterruptedException if the current thread is interrupted while waiting
      * @throws ExecutionException   if no task completes successfully
      * @throws TimeoutException     if the timeout elapses before any task completes
      */
     @Override
     public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
             throws InterruptedException, ExecutionException, TimeoutException {
         return delegate.invokeAny(contextualCallables(tasks), timeout, unit);
     }

     /**
      * Wraps every task in the collection with the current context snapshot.
      *
      * @param tasks the original tasks
      * @param <T>   the result type of the tasks
      * @return contextualized copies of the tasks
      */
     private <T> Collection<Callable<T>> contextualCallables(Collection<? extends Callable<T>> tasks) {
         return tasks.stream().map(this::contextualCallable).collect(Collectors.toList());
     }

     /**
      * Wraps the given task with the context snapshot taken <b>at the moment of this call</b>.
      *
      * @param original the original task
      * @param <T>      the result type of the task
      * @return a task that applies the captured context before running the original
      */
     <T> Callable<T> contextualCallable(Callable<T> original) {
         return ctxRelay.contextual(original);
     }
}