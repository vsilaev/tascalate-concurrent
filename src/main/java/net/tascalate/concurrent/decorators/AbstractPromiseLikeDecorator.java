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

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.core.Decorator;

public abstract class AbstractPromiseLikeDecorator<T, D extends CompletionStage<T>>
    extends AbstractCompletionStageDecorator<T, D>
    implements CompletionStage<T>, Decorator<T> {

    protected AbstractPromiseLikeDecorator(D delegate) {
        super(delegate);
    }

    @Override
    protected <U> Promise<U> wrap(CompletionStage<U> original) {
        return (Promise<U>)super.wrap(original);
    }
    
    @Override
    abstract protected <U> Promise<U> wrapNew(CompletionStage<U> original);
    
    private static <U> Promise<U> cast(CompletionStage<U> stage) {
        return (Promise<U>)stage;
    }
     
    // --
    @Override
    public <U> Promise<U> thenApply(Function<? super T, ? extends U> fn) {
        return cast(super.thenApply(fn));
    }

    @Override
    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return cast(super.thenApplyAsync(fn));
    }

    @Override
    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return cast(super.thenApplyAsync(fn, executor));
    }

    @Override
    public Promise<Void> thenAccept(Consumer<? super T> action) {
        return cast(super.thenAccept(action));
    }

    @Override
    public Promise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return cast(super.thenAcceptAsync(action));
    }

    @Override
    public Promise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return cast(super.thenAcceptAsync(action, executor));
    }

    @Override
    public Promise<Void> thenRun(Runnable action) {
        return cast(super.thenRun(action));
    }

    @Override
    public Promise<Void> thenRunAsync(Runnable action) {
        return cast(super.thenRunAsync(action));
    }

    @Override
    public Promise<Void> thenRunAsync(Runnable action, Executor executor) {
        return cast(super.thenRunAsync(action, executor));
    }

    @Override
    public <U, V> Promise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return cast(super.thenCombine(other, fn));
    }
    
    @Override
    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return cast(super.thenCombineAsync(other, fn));
    }

    @Override
    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                              BiFunction<? super T, ? super U, ? extends V> fn, 
                                              Executor executor) {
        
        return cast(super.thenCombineAsync(other, fn, executor));
    }

    @Override
    public <U> Promise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return cast(super.thenAcceptBoth(other, action));
    }

    @Override
    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return cast(super.thenAcceptBothAsync(other, action));
    }

    @Override
    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                 BiConsumer<? super T, ? super U> action, 
                                                 Executor executor) {
        
        return cast(super.thenAcceptBothAsync(other, action, executor));
    }

    @Override
    public Promise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return cast(super.runAfterBoth(other, action));
    }

    @Override
    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return cast(super.runAfterBothAsync(other, action));
    }

    @Override
    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                           Runnable action, 
                                           Executor executor) {
        return cast(super.runAfterBothAsync(other, action, executor));
    }

    @Override
    public <U> Promise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return cast(super.applyToEither(other, fn));
    }

    @Override
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return cast(super.applyToEitherAsync(other, fn));
    }

    @Override
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                             Function<? super T, U> fn,
                                             Executor executor) {
        
        return cast(super.applyToEitherAsync(other, fn, executor));
    }

    @Override
    public Promise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return cast(super.acceptEither(other, action));
    }

    @Override
    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return cast(super.acceptEitherAsync(other, action));
    }

    @Override
    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                           Consumer<? super T> action,
                                           Executor executor) {
        
        return cast(super.acceptEitherAsync(other, action, executor));
    }

    @Override
    public Promise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return cast(super.runAfterEither(other, action));
    }

    @Override
    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return cast(super.runAfterEitherAsync(other, action));
    }

    @Override
    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                             Runnable action, 
                                             Executor executor) {
        return cast(super.runAfterEitherAsync(other, action, executor));
    }

    @Override
    public <U> Promise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return cast(super.thenCompose(fn));
    }

    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return cast(super.thenComposeAsync(fn));
    }

    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return cast(super.thenComposeAsync(fn, executor));
    }

    @Override
    public Promise<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return cast(super.exceptionally(fn));
    }

    /* Since Java 12 */
    @Override
    public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return cast(super.exceptionallyAsync(fn));
    }
    
    /* Since Java 12 */
    @Override
    public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        return cast(super.exceptionallyAsync(fn, executor));
    }
    
    /* Since Java 12 */
    @Override
    public Promise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return cast(super.exceptionallyCompose(fn));
    }
    
    /* Since Java 12 */
    @Override
    public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return cast(super.exceptionallyComposeAsync(fn));
    }

    /* Since Java 12 */
    @Override
    public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        return cast(super.exceptionallyComposeAsync(fn, executor));
    }
    

    @Override
    public Promise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return cast(super.whenComplete(action));
    }

    @Override
    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return cast(super.whenCompleteAsync(action));
    }

    @Override
    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return cast(super.whenCompleteAsync(action, executor));
    }

    @Override
    public <U> Promise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return cast(super.handle(fn));
    }

    @Override
    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return cast(super.handleAsync(fn));
    }

    @Override
    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return cast(super.handleAsync(fn, executor));
    }
    
    // Alpha-operator -- the origin of origins
    @Override
    public CompletionStage<T> α() {
        CompletionStage<T> p = delegate;
        if (p instanceof Decorator) {
            return decorator(p).α();
        } else {
            // Default path -- unroll
            while (p instanceof AbstractCompletionStageDecorator) {
                @SuppressWarnings("unchecked")
                AbstractCompletionStageDecorator<T, ? extends CompletionStage<T>> ap = 
                    (AbstractCompletionStageDecorator<T, ? extends CompletionStage<T>>)p;
                p = ap.delegate;
                if (p instanceof Decorator) {
                    return decorator(p).α();
                }
            }
            return p;
        }
    }
    
    @SuppressWarnings("unchecked")
    private static <T> Decorator<T> decorator(CompletionStage<T> delegate) {
        return (Decorator<T>)delegate;
    }
}
