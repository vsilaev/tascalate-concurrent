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

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ScheduledExecutorService} decorator that propagates relayed context variables
 * to scheduled tasks.
 * <p>
 * <b>Dynamic resolution:</b> the context snapshot is taken at the moment of each
 * {@code schedule(...)} / {@code scheduleAtFixedRate(...)} / {@code scheduleWithFixedDelay(...)}
 * call. Note that for <em>periodic</em> tasks the single snapshot captured at scheduling time
 * is reused for every repetition of the task.
 * </p>
 *
 * @param <D> the type of the delegate scheduled executor service
 * @see ContextRelay#contextual(ScheduledExecutorService)
 */
class ContextualScheduledExecutorService<D extends ScheduledExecutorService>
        extends ContextualExecutorService<D>
        implements ScheduledExecutorService {

    /**
     * Creates a decorator for the given scheduled executor service.
     *
     * @param delegate the scheduled executor service to wrap
     * @param ctxRelay the relay capturing context on each scheduling call
     */
    ContextualScheduledExecutorService(D delegate, ContextRelay<?> ctxRelay) {
         super(delegate, ctxRelay);
     }

     /**
      * Schedules the command wrapped with the context snapshot captured <b>at the moment
      * of this call</b>.
      *
      * @param command the command to execute
      * @param delay   the delay before execution
      * @param unit    the time unit of the delay
      * @return a scheduled future representing the pending completion
      */
     @Override
     public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
         return delegate.schedule(contextualRunnable(command), delay, unit);
     }

     /**
      * Schedules the callable wrapped with the context snapshot captured <b>at the moment
      * of this call</b>.
      *
      * @param callable the callable to execute
      * @param delay    the delay before execution
      * @param unit     the time unit of the delay
      * @param <V>      the result type of the callable
      * @return a scheduled future representing the pending result
      */
     @Override
     public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
         return delegate.schedule(contextualCallable(callable), delay, unit);
     }

     /**
      * Schedules a periodic command wrapped with the context snapshot captured <b>at the
      * moment of this call</b>; the same snapshot is reused for every repetition.
      * Executions are scheduled at a fixed rate.
      *
      * @param command      the command to execute
      * @param initialDelay the delay before the first execution
      * @param period       the period between successive executions
      * @param unit         the time unit of the delays
      * @return a scheduled future representing the periodic task
      */
     @Override
     public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
         return delegate.scheduleAtFixedRate(contextualRunnable(command), initialDelay, period, unit);
     }

     /**
      * Schedules a periodic command wrapped with the context snapshot captured <b>at the
      * moment of this call</b>; the same snapshot is reused for every repetition.
      * Delays are measured from the completion of the previous execution.
      *
      * @param command      the command to execute
      * @param initialDelay the delay before the first execution
      * @param delay        the delay between the completion of one execution and the start of the next
      * @param unit         the time unit of the delays
      * @return a scheduled future representing the periodic task
      */
     @Override
     public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
         return delegate.scheduleWithFixedDelay(contextualRunnable(command), initialDelay, delay, unit);
     }
}