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
package net.tascalate.concurrent;

import static net.tascalate.concurrent.SharedFunctions.selectSecond;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import net.tascalate.concurrent.decorators.ExecutorBoundPromise;

/**
 * 
 * Concrete implementation of {@link Promise} interface for long-running blocking tasks
 * 
 * @author vsilaev
 *
 * @param <T>
 *   a type of the successfully executed task result   
 */
public class CompletableTask<T> extends AbstractCompletableTask<T> {

    /**
     * Creates a CompletableTask; for internal use only 
     * @param executor
     *   a default {@link Executor} to run functions passed to async composition methods 
     * @param callable
     *   a {@link Callable} that completes this task
     */
    protected CompletableTask(Executor executor, Callable<T> callable) {
        super(executor, callable);
    }

    /**
     * Returns a resolved {@link Promise} with specified value; the promise is "bound" to the specified executor. 
     * I.e. any function passed to composition methods of Promise (like <code>thenApplyAsync</code> 
     * / <code>thenAcceptAsync</code> / <code>whenCompleteAsync</code> etc.) will be executed using this executor 
     * unless executor is overridden via explicit composition method parameter. Moreover, any nested 
     * composition calls will use same executor, if it’s not redefined via explicit composition method parameter:
     * {@code}<pre>CompletableTask
     *   .complete("Hello!", myExecutor)
     *   .thenApplyAsync(myMapper)
     *   .thenApplyAsync(myTransformer)   
     *   .thenAcceptAsync(myConsumer)
     *   .thenRunAsync(myAction)
     *  </pre>
     * All of <code>myMapper</code>, <code>myTransformer</code>, <code>myConsumer</code>, <code>myActtion</code> 
     * will be executed using <code>myExecutor</code>
     * 
     * @param <T>
     *   a type of the successfully executed task result 
     * @param value
     *   a task result
     * @param defaultExecutor
     *   a default {@link Executor} to run functions passed to async composition methods 
     *   (like <code>thenApplyAsync</code> / <code>thenAcceptAsync</code> / <code>whenCompleteAsync</code> etc.)
     * @return
     *   resolved {@link Promise} with a value passed; the promise is bound to the specified executor
     */
    public static <T> Promise<T> completed(T value, Executor defaultExecutor) {
        CompletableTask<T> result = new CompletableTask<>(defaultExecutor, () -> value);
        SAME_THREAD_EXECUTOR.execute(result.task);
        return result;
    }

    /**
     * Returns a resolved no-value {@link Promise} that is "bound" to the specified executor. 
     * I.e. any function passed to composition methods of Promise (like <code>thenApplyAsync</code> 
     * / <code>thenAcceptAsync</code> / <code>whenCompleteAsync</code> etc.) will be executed using this executor 
     * unless executor is overridden via explicit composition method parameter. Moreover, any nested 
     * composition calls will use same executor, if it’s not redefined via explicit composition method parameter:
     * {@code}<pre>CompletableTask
     *   .asyncOn(myExecutor)
     *   .thenApplyAsync(myValueGenerator)
     *   .thenAcceptAsync(myConsumer)
     *   .thenRunAsync(myAction)
     *  </pre>
     * All of <code>myValueGenerator</code>, <code>myConsumer</code>, <code>myActtion</code> will be executed 
     * using <code>myExecutor</code>
     * 
     * @param executor
     *   a default {@link Executor} to run functions passed to async composition methods 
     *   (like <code>thenApplyAsync</code> / <code>thenAcceptAsync</code> / <code>whenCompleteAsync</code> etc.)
     * @return
     *   resolved non-value {@link Promise} bound to the specified executor
     */
    public static Promise<Void> asyncOn(Executor executor) {
        return completed(null, executor);
    }
    
    /**
     * Returns a resolved no-value {@link Promise} that is "bound" to the specified executor. 
     * I.e. any function passed to composition methods of Promise (like <code>thenApplyAsync</code> 
     * / <code>thenAcceptAsync</code> / <code>whenCompleteAsync</code> etc.) will be executed using this executor 
     * unless executor is overridden via explicit composition method parameter. Moreover, any nested 
     * composition calls will use same executor, if it’s not redefined via explicit composition method parameter:
     * {@code}<pre>CompletableTask
     *   .asyncOn(myExecutor)
     *   .thenApplyAsync(myValueGenerator)
     *   .thenAcceptAsync(myConsumer)
     *   .thenRunAsync(myAction)
     *  </pre>
     * <p>All of <code>myValueGenerator</code>, <code>myConsumer</code>, <code>myActtion</code> will be executed 
     * using <code>myExecutor</code>.
     * <p>Moreover, if <code>enforceDefaultAsync</code> is true, then default executor will be propagated to 
     * dependent promises even if corresponding transition was executed on another executor (via composition methods 
     * with explicit executor argument).
     * 
     * @param executor
     *   a default {@link Executor} to run functions passed to async composition methods 
     *   (like <code>thenApplyAsync</code> / <code>thenAcceptAsync</code> / <code>whenCompleteAsync</code> etc.)
     * @param enforceDefaultAsync
     *   if true then default executor will be propagated to dependent promises 
     *   even if corresponding transition was executed on another executor 
     * @return
     *   resolved non-value {@link Promise} bound to the specified executor
     */    
    public static Promise<Void> asyncOn(Executor executor, boolean enforceDefaultAsync) {
        Promise<Void> result = completed(null, executor);
        if (enforceDefaultAsync) {
            class EnforcedExecutorBoundPromise<T> extends ExecutorBoundPromise<T> {
                public EnforcedExecutorBoundPromise(Promise<T> delegate) {
                    super(delegate, executor);
                }
                
                @Override
                protected <U> Promise<U> wrapNew(CompletionStage<U> original) {
                    return new EnforcedExecutorBoundPromise<>((Promise<U>)original);
                }
                
                // No need to overwrite dependent(...) results
                // while underlying delegate is undecorateable promise (this)
                
                @Override
                public Promise<T> unwrap() {
                    // We are wrapping always raw promise and
                    // it's explicitly forbidden to remove own behavior
                    // via enforceDefaultAsync
                    return this;
                }
                
                @Override
                public Promise<T> raw() {
                    // Same as for unwrap
                    return this;
                }
            }
            
            return new EnforcedExecutorBoundPromise<>(result);
        } else {
            return result;
        }
    }
    
    /**
     * Returns a new {@link Promise} that is asynchronously resolved by a task running in the given executor 
     * after it runs the given action.
     * @param runnable
     *   the action to run before resolving the returned {@link Promise}
     * @param executor
     *   the executor to use for asynchronous execution
     * @return
     *   the new {@link Promise}
     */
    public static Promise<Void> runAsync(Runnable runnable, Executor executor) {
        return submit(() -> { runnable.run(); return null; }, executor);
    }
    
    /**
     * Returns a new {@link Promise} that is asynchronously resolved by a task running in the given executor 
     * with the value obtained by calling the given {@link Supplier}.
     * @param <U>
     *   the function's return type
     * @param supplier
     *   a function returning the value to be used to resolve the returned {@link Promise}
     * @param executor
     *   the executor to use for asynchronous execution
     * @return
     *   the new {@link Promise}
     */
    public static <U> Promise<U> supplyAsync(Supplier<U> supplier, Executor executor) {
        return submit(supplier::get, executor);
    }
    
    /**
     * Returns a new {@link Promise} that is asynchronously resolved by a task running in the given executor 
     * with the value obtained by calling the given {@link Callable}.
     * @param <U>
     *   the function's return type
     * @param call
     *   a function returning the value to be used to resolve the returned {@link Promise}
     * @param executor
     *   the executor to use for asynchronous execution
     * @return
     *   the new {@link Promise}
     */
    public static <U> Promise<U> submit(Callable<U> call, Executor executor) {
        CompletableTask<U> result = new CompletableTask<>(executor, call);
        executor.execute(result.task);
        return result;
    }
    
    public static <T> Promise<T> waitFor(CompletionStage<T> stage, Executor executor) {
        return waitFor(stage, executor, false);
    }
    
    public static <T> Promise<T> waitFor(CompletionStage<T> stage, Executor executor, boolean dependentStage) {
        return asyncOn(executor)
               .dependent()
               .thenCombine(stage, selectSecond(), enlistParamOrNone(dependentStage))
               .unwrap();
    }
    
    public static Promise<Duration> delay(long timeout, TimeUnit unit, Executor executor) {
        return delay(Timeouts.toDuration(timeout, unit), executor);
    }
    
    public static Promise<Duration> delay(Duration duration, Executor executor) {
        return asyncOn(executor)
               .dependent()
               .thenCombineAsync(Timeouts.delay(duration), selectSecond(), enlistParamOrNone(true))
               .unwrap();
    }
    
    protected void runTask() {
        task.run();
    }
    
    @Override
    void fireTransition(Callable<T> code) {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Creates a nested sub-task for a composition method 
     * @param executor
     *   a default executor for async composition methods of nested sub-task
     * @return
     *   an instance of {@link CompletableSubTask} bound to the specified executor
     */
    @Override
    protected <U> AbstractCompletableTask<U> createCompletionStage(Executor executor) {
        return new CompletableSubTask<U>(executor);
    }
    
    private static Set<PromiseOrigin> enlistParamOrNone(boolean enlistParam) {
        return enlistParam ? PromiseOrigin.PARAM_ONLY : PromiseOrigin.NONE;
    }
}
