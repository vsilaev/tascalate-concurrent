/**
 * Copyright 2015-2020 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.concurrent.decorators;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import net.tascalate.concurrent.Promise;

/**
 * Helper class to create a concrete {@link Promise} subclass via delegation
 * to the wrapped {@link CompletionStage}
 * 
 * @author vsilaev
 *
 * @param <T>
 *   a type of the successfully resolved promise value   
 * @param <D>
 *   a type of the concrete {@link CompletionStage} subclass
 */
abstract public class AbstractCompletionStageDecorator<T, D extends CompletionStage<T>> implements CompletionStage<T> {
    final protected D delegate;

    protected AbstractCompletionStageDecorator(D delegate) {
        this.delegate = delegate;
    }
    
    abstract protected <U> Promise<U> wrap(CompletionStage<U> original);

    @Override
    public <U> Promise<U> thenApply(Function<? super T, ? extends U> fn) {
        return wrap(delegate.thenApply(fn));
    }

    @Override
    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return wrap(delegate.thenApplyAsync(fn));
    }

    @Override
    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return wrap(delegate.thenApplyAsync(fn, executor));
    }

    @Override
    public Promise<Void> thenAccept(Consumer<? super T> action) {
        return wrap(delegate.thenAccept(action));
    }

    @Override
    public Promise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return wrap(delegate.thenAcceptAsync(action));
    }

    @Override
    public Promise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return wrap(delegate.thenAcceptAsync(action, executor));
    }

    @Override
    public Promise<Void> thenRun(Runnable action) {
        return wrap(delegate.thenRun(action));
    }

    @Override
    public Promise<Void> thenRunAsync(Runnable action) {
        return wrap(delegate.thenRunAsync(action));
    }

    @Override
    public Promise<Void> thenRunAsync(Runnable action, Executor executor) {
        return wrap(delegate.thenRunAsync(action, executor));
    }

    @Override
    public <U, V> Promise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return wrap(delegate.thenCombine(other, fn));
    }
    
    @Override
    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return wrap(delegate.thenCombineAsync(other, fn));
    }

    @Override
    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                              BiFunction<? super T, ? super U, ? extends V> fn, 
                                              Executor executor) {
        
        return wrap(delegate.thenCombineAsync(other, fn, executor));
    }

    @Override
    public <U> Promise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return wrap(delegate.thenAcceptBoth(other, action));
    }

    @Override
    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return wrap(delegate.thenAcceptBothAsync(other, action));
    }

    @Override
    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                 BiConsumer<? super T, ? super U> action, 
                                                 Executor executor) {
        
        return wrap(delegate.thenAcceptBothAsync(other, action, executor));
    }

    @Override
    public Promise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return wrap(delegate.runAfterBoth(other, action));
    }

    @Override
    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return wrap(delegate.runAfterBothAsync(other, action));
    }

    @Override
    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                           Runnable action, 
                                           Executor executor) {
        return wrap(delegate.runAfterBothAsync(other, action, executor));
    }

    @Override
    public <U> Promise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return wrap(delegate.applyToEither(other, fn));
    }

    @Override
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return wrap(delegate.applyToEitherAsync(other, fn));
    }

    @Override
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                             Function<? super T, U> fn,
                                             Executor executor) {
        
        return wrap(delegate.applyToEitherAsync(other, fn, executor));
    }

    @Override
    public Promise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return wrap(delegate.acceptEither(other, action));
    }

    @Override
    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return wrap(delegate.acceptEitherAsync(other, action));
    }

    @Override
    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                           Consumer<? super T> action,
                                           Executor executor) {
        
        return wrap(delegate.acceptEitherAsync(other, action, executor));
    }

    @Override
    public Promise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return wrap(delegate.runAfterEither(other, action));
    }

    @Override
    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return wrap(delegate.runAfterEitherAsync(other, action));
    }

    @Override
    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                             Runnable action, 
                                             Executor executor) {
        return wrap(delegate.runAfterEitherAsync(other, action, executor));
    }

    @Override
    public <U> Promise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return wrap(delegate.thenCompose(fn));
    }

    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return wrap(delegate.thenComposeAsync(fn));
    }

    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return wrap(delegate.thenComposeAsync(fn, executor));
    }

    @Override
    public Promise<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return wrap(delegate.exceptionally(fn));
    }
    
    /* Since Java 12 */
    /*
    @Override
    */
    public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return wrap(exceptionallyAsync(delegate, fn));
    }
    
    /* Since Java 12 */
    /*
    @Override
    */
    public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        return wrap(exceptionallyAsync(delegate, fn, executor));
    }
    
    /* Since Java 12 */
    /*
    @Override
    */
    public Promise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return wrap(exceptionallyCompose(delegate, fn));
    }
    
    /* Since Java 12 */
    /*
    @Override
    */
    public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return wrap(exceptionallyComposeAsync(delegate, fn));
    }

    /* Since Java 12 */
    /*
    @Override
    */
    public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        return wrap(exceptionallyComposeAsync(delegate, fn, executor));
    }

    @Override
    public Promise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return wrap(delegate.whenComplete(action));
    }

    @Override
    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return wrap(delegate.whenCompleteAsync(action));
    }

    @Override
    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return wrap(delegate.whenCompleteAsync(action, executor));
    }

    @Override
    public <U> Promise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return wrap(delegate.handle(fn));
    }

    @Override
    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return wrap(delegate.handleAsync(fn));
    }

    @Override
    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return wrap(delegate.handleAsync(fn, executor));
    }

    @Override
    public CompletableFuture<T> toCompletableFuture() {
        return delegate.toCompletableFuture();
    }
    
    
    public static <T> CompletionStage<T> exceptionallyAsync(CompletionStage<T> delegate, 
                                                            Function<Throwable, ? extends T> fn) {
        return delegate.handle((r, ex) -> ex == null ? 
                               delegate : 
                               delegate.<T>handleAsync((r1, ex1) -> fn.apply(ex1)))
                       .thenCompose(Function.identity());        
    }
    
    public static <T> CompletionStage<T> exceptionallyAsync(CompletionStage<T> delegate, 
                                                            Function<Throwable, ? extends T> fn, Executor executor) {
        return delegate.handle((r, ex) -> ex == null ? 
                               delegate : 
                               delegate.<T>handleAsync((r1, ex1) -> fn.apply(ex1), executor))
                       .thenCompose(Function.identity());        
    }
    
    public static <T> CompletionStage<T> exceptionallyCompose(CompletionStage<T> delegate, 
                                                              Function<Throwable, ? extends CompletionStage<T>> fn) {
        return delegate.handle((r, ex) -> ex == null ? delegate : fn.apply(ex))
                       .thenCompose(Function.identity());
    }
    
    public static <T> CompletionStage<T> exceptionallyComposeAsync(CompletionStage<T> delegate, 
                                                                   Function<Throwable, ? extends CompletionStage<T>> fn) {
        return delegate.handle((r, ex) -> ex == null ? 
                               delegate : 
                               delegate.handleAsync((r1, ex1) -> fn.apply(ex1))
                                       .thenCompose(Function.identity()))
                       .thenCompose(Function.identity());
    }
    
    public static <T> CompletionStage<T> exceptionallyComposeAsync(CompletionStage<T> delegate, 
                                                                   Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        return delegate.handle((r, ex) -> ex == null ? 
                               delegate : 
                               delegate.handleAsync((r1, ex1) -> fn.apply(ex1), executor)
                                       .thenCompose(Function.identity()))
                       .thenCompose(Function.identity());
    }
    
    @Override
    public String toString() {
        return String.format("%s[%s]", getClass().getName(), delegate);
    }
}