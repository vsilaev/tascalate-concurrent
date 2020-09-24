/**
 * Original work: copyright 2009-2015 Lukáš Křečan
 * https://github.com/lukas-krecan/completion-stage
 * 
 * This class is based on the work create by Lukáš Křečan 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/lukas-krecan/completion-stage/blob/completion-stage-0.0.9/src/main/java/net/javacrumbs/completionstage/CompletionStageAdapter.java
 * 
 * Modified work: copyright 2015-2020 Valery Silaev (http://vsilaev.com)
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

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Helper class to create a concrete {@link Promise} subclass as an
 * implementation from scratch.
 * <p>This class extends {@link PromiseAdapter} and delegate all methods 
 * to the following set of methods (must be implemented in subclass):
 * <ul>
 * <li>{@link Promise#handleAsync(BiFunction, Executor)}</li> 
 * <li>{@link Promise#thenComposeAsync(Function, Executor)}</li>
 * <li>{@link Promise#exceptionallyComposeAsync(Function, Executor)}</li>
 * <li>{@link Promise#whenCompleteAsync(BiConsumer, Executor)}</li> 
 * <li>{@link PromiseAdapterExtended#doApplyToEitherAsync(CompletionStage, CompletionStage, Function, Executor)}</li>
 * </ul>
 * <p>Re-implementation of <code>exceptionallyComposeAsync</code> methods from list above is optional
 * but strongly recommended.
 * 
 * @author vsilaev
 *
 * @param <T>
 *   a type of the successfully resolved promise value   
 */
// Not public yet
abstract class PromiseAdapterExtended<T> extends PromiseAdapter<T> {
    
    protected PromiseAdapterExtended(Executor defaultExecutor) {
        super(defaultExecutor);
    }
    
    @Override
    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return handleAsync((r, e) -> null == e ? fn.apply(r) : forwardException(e), executor);
    }

    @Override
    public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        // Symmetrical with thenApplyAsync
        return handleAsync((r, e) -> null == e ? r : fn.apply(e), executor);
    }

    @Override
    public Promise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return thenApplyAsync(consumerAsFunction(action), executor);
    }

    @Override
    public Promise<Void> thenRunAsync(Runnable action, Executor executor) {
        return thenApplyAsync(runnableAsFunction(action), executor);
    }
    
    @Override
    public Promise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, Executor executor) {
        return handleAsync(
            (r, e) -> null == e ? predicate.test(r) ? r : forwardException(errorSupplier.apply(r)) : forwardException(e), 
            executor
        );
    }

    @Override
    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                              BiFunction<? super T, ? super U, ? extends V> fn, 
                                              Executor executor) {

        return thenCompose(result1 -> other.thenApplyAsync(result2 -> fn.apply(result1, result2), executor));
    }

    @Override
    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                 BiConsumer<? super T, ? super U> action, 
                                                 Executor executor) {
        return thenCombineAsync(other,
                // transform BiConsumer to BiFunction
                (t, u) -> {
                    action.accept(t, u);
                    return null;
                }, executor);
    }

    @Override
    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                           Runnable action, 
                                           Executor executor) {
        return thenCombineAsync(other,
                // transform Runnable to BiFunction
                (t, r) -> {
                    action.run();
                    return null;
                }, executor);
    }

    @Override
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                             Function<? super T, U> fn,
                                             Executor executor) {

        return doApplyToEitherAsync(this, other, fn, executor);
    }

    @Override
    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                                           Consumer<? super T> action,
                                           Executor executor) {

        return applyToEitherAsync(other, consumerAsFunction(action), executor);
    }

    @Override
    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                             Runnable action, 
                                             Executor executor) {
        
        return doApplyToEitherAsync(this, other, runnableAsFunction(action), executor);
    }

    abstract protected <R, U> Promise<U> doApplyToEitherAsync(CompletionStage<? extends R> first,
                                                              CompletionStage<? extends R> second, 
                                                              Function<? super R, U> fn, 
                                                              Executor executor);

    protected static <U> U forwardException(Throwable e) {
        throw SharedFunctions.wrapCompletionException(e);
    }
    
    @SuppressWarnings("unchecked")
    protected static <U> Function<Throwable, U> forwardException() {
        return (Function<Throwable, U>)FORWARD_EXCEPTION;
    }
    
    protected static <V, R> Function<V, R> consumerAsFunction(Consumer<? super V> action) {
        return result -> {
            action.accept(result);
            return null;
        };
    }
    
    private static <R> Function<R, Void> runnableAsFunction(Runnable action) {
        return result -> {
            action.run();
            return null;
        };
    }
    
    private static final Function<Throwable, Object> FORWARD_EXCEPTION = 
        PromiseAdapterExtended::forwardException;
}
