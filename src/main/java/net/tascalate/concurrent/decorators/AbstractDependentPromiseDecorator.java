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

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.tascalate.concurrent.DependentPromise;
import net.tascalate.concurrent.PromiseOrigin;

public abstract class AbstractDependentPromiseDecorator<T> 
    extends AbstractPromiseDecorator<T, DependentPromise<T>> 
    implements DependentPromise<T> {

    protected AbstractDependentPromiseDecorator(DependentPromise<T> delegate) {
        super(delegate);
    }
    
    @Override
    protected <U> DependentPromise<U> wrap(CompletionStage<U> original) {
        return (DependentPromise<U>)super.wrap(original);
    }
    
    @Override
    abstract protected <U> DependentPromise<U> wrapNew(CompletionStage<U> original);
    
    @Override
    public DependentPromise<T> dependent() {
        return wrap(super.dependent());
    }

    @Override
    public DependentPromise<T> dependent(Set<PromiseOrigin> defaultEnlistOptions) {
        return wrap(super.dependent(defaultEnlistOptions));
    }
    
    @Override
    public DependentPromise<T> defaultAsyncOn(Executor executor) {
        return (DependentPromise<T>)super.defaultAsyncOn(executor);
    }

    @Override 
    public DependentPromise<T> onCancel(Runnable code) {
        return (DependentPromise<T>)super.onCancel(code);
    }
    
    @Override
    public DependentPromise<T> delay(long timeout, TimeUnit unit) {
        return (DependentPromise<T>)super.delay(timeout, unit);
    }
    
    @Override
    public DependentPromise<T> delay(long timeout, TimeUnit unit, boolean delayOnError) {
        return (DependentPromise<T>)super.delay(timeout, unit, delayOnError);
    }
    
    @Override
    public DependentPromise<T> delay(Duration duration) {
        return (DependentPromise<T>)super.delay(duration);
    }
    
    @Override
    public DependentPromise<T> delay(Duration duration, boolean delayOnError) {
        return (DependentPromise<T>)super.delay(duration, delayOnError);
    }
    
    @Override
    public DependentPromise<T> delay(long timeout, TimeUnit unit, boolean delayOnError, boolean enlistOrigin) {
        return wrap(delegate.delay(timeout, unit, delayOnError, enlistOrigin));        
    }

    @Override
    public DependentPromise<T> delay(Duration duration, boolean delayOnError, boolean enlistOrigin) {
        return wrap(delegate.delay(duration, delayOnError, enlistOrigin));
    }

    @Override    
    public DependentPromise<T> orTimeout(long timeout, TimeUnit unit) {
        return (DependentPromise<T>)super.orTimeout(timeout, unit);
    }
    
    @Override
    public DependentPromise<T> orTimeout(long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return (DependentPromise<T>)super.orTimeout(timeout, unit, cancelOnTimeout);
    }
    
    @Override
    public DependentPromise<T> orTimeout(Duration duration) {
        return (DependentPromise<T>)super.orTimeout(duration);
    }
    
    @Override
    public DependentPromise<T> orTimeout(Duration duration, boolean cancelOnTimeout) {
        return (DependentPromise<T>)super.orTimeout(duration, cancelOnTimeout);
    }
    
    @Override
    public DependentPromise<T> orTimeout(long timeout, TimeUnit unit, boolean cancelOnTimeout, boolean enlistOrigin) {
        return wrap(delegate.orTimeout(timeout, unit, cancelOnTimeout, enlistOrigin));
    }

    @Override
    public DependentPromise<T> orTimeout(Duration duration, boolean cancelOnTimeout, boolean enlistOrigin) {
        return wrap(delegate.orTimeout(duration, cancelOnTimeout, enlistOrigin));
    }

    @Override
    public DependentPromise<T> onTimeout(T value, long timeout, TimeUnit unit) {
        return (DependentPromise<T>)super.onTimeout(value, timeout, unit);
    }
    
    @Override
    public DependentPromise<T> onTimeout(T value, long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return (DependentPromise<T>)super.onTimeout(value, timeout, unit, cancelOnTimeout);
    }
    
    @Override
    public DependentPromise<T> onTimeout(T value, Duration duration) {
        return (DependentPromise<T>)super.onTimeout(value, duration);
    }
    
    @Override
    public DependentPromise<T> onTimeout(T value, Duration duration, boolean cancelOnTimeout) {
        return (DependentPromise<T>)super.onTimeout(value, duration, cancelOnTimeout);
    }
    
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit) {
        return (DependentPromise<T>)super.onTimeout(supplier, timeout, unit);
    }
    
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return (DependentPromise<T>)super.onTimeout(supplier, timeout, unit, cancelOnTimeout);
    }
    
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration) {
        return (DependentPromise<T>)super.onTimeout(supplier, duration);
    }
    
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout) {
        return (DependentPromise<T>)super.onTimeout(supplier, duration, cancelOnTimeout);
    }
    
    @Override
    public DependentPromise<T> onTimeout(T value, long timeout, TimeUnit unit, boolean cancelOnTimeout, boolean enlistOrigin) {
        return wrap(delegate.onTimeout(value, timeout, unit, cancelOnTimeout, enlistOrigin));
    }
    
    @Override
    public DependentPromise<T> onTimeout(T value, Duration duration, boolean cancelOnTimeout, boolean enlistOrigin) {
        return wrap(delegate.onTimeout(value, duration, cancelOnTimeout, enlistOrigin)); 
    }
    
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit, boolean cancelOnTimeout, boolean enlistOrigin) {
        return wrap(delegate.onTimeout(supplier, timeout, unit, cancelOnTimeout, enlistOrigin));
    }

    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout, boolean enlistOrigin) {
        return wrap(delegate.onTimeout(supplier, duration, cancelOnTimeout, enlistOrigin));
    }
    
    @Override
    public <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.thenApply(fn, enlistOrigin));
    }
    
    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.thenApplyAsync(fn, enlistOrigin));
    }
    
    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenApplyAsync(fn, executor, enlistOrigin));
    }
    
    @Override
    public DependentPromise<Void> thenAccept(Consumer<? super T> action, boolean enlistOrigin) {
        return wrap(delegate.thenAccept(action, enlistOrigin));
    }

    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, boolean enlistOrigin) {
        return wrap(delegate.thenAcceptAsync(action, enlistOrigin));
    }
    
    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenAcceptAsync(action, executor, enlistOrigin));
    }
    
    @Override
    public DependentPromise<Void> thenRun(Runnable action, boolean enlistOrigin) {
        return wrap(delegate.thenRun(action, enlistOrigin));
    }
    
    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action, boolean enlistOrigin) {
        return wrap(delegate.thenRunAsync(action, enlistOrigin));
    }

    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenRunAsync(action, executor, enlistOrigin));
    }

    @Override
    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, 
                                                  BiFunction<? super T, ? super U, ? extends V> fn,
                                                  Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.thenCombine(other, fn, enlistOptions));
    }

    
    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, 
                                                       BiFunction<? super T, ? super U, ? extends V> fn,
                                                       Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.thenCombineAsync(other, fn, enlistOptions));
    }
    
    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn, 
                                                       Executor executor,
                                                       Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.thenCombineAsync(other, fn, executor, enlistOptions));
    }
    

    @Override
    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, 
                                                     BiConsumer<? super T, ? super U> action,
                                                     Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.thenAcceptBoth(other, action, enlistOptions));
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                          BiConsumer<? super T, ? super U> action,
                                                          Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.thenAcceptBothAsync(other, action, enlistOptions));
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                          BiConsumer<? super T, ? super U> action, 
                                                          Executor executor,
                                                          Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.thenAcceptBothAsync(other, action, executor, enlistOptions));
    }

    @Override
    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterBoth(other, action, enlistOptions));
    }
    
    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterBothAsync(other, action, enlistOptions));
    }
    
    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                                    Runnable action, 
                                                    Executor executor,
                                                    Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterBothAsync(other, action, executor, enlistOptions));
    }
    
    @Override
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, 
                                                 Function<? super T, U> fn,
                                                 Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.applyToEither(other, fn, enlistOptions));
    }
    

    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.applyToEitherAsync(other, fn, enlistOptions));
    }

    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Executor executor,
                                                      Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.applyToEitherAsync(other, fn, executor, enlistOptions));
    }
    

    @Override
    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, 
                                               Consumer<? super T> action,
                                               Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.acceptEither(other, action, enlistOptions));
    }

    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.acceptEitherAsync(other, action, enlistOptions));
    }

    
    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Executor executor,
                                                    Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.acceptEitherAsync(other, action, executor, enlistOptions));
    }

    
    @Override
    public DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterEither(other, action, enlistOptions));
    }
    
    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterEitherAsync(other, action, enlistOptions));
    }
    
    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                                      Runnable action, 
                                                      Executor executor,
                                                      Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterEitherAsync(other, action, executor, enlistOptions));
    }

    @Override
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return wrap(delegate.thenCompose(fn, enlistOrigin));
    }
    
    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return wrap(delegate.thenComposeAsync(fn, enlistOrigin));
    }
    
    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenComposeAsync(fn, executor, enlistOrigin));
    }

    @Override
    public DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn, boolean enlistOrigin) {
        return wrap(delegate.exceptionally(fn, enlistOrigin));
    }
    
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, boolean enlistOrigin) {
        return wrap(delegate.exceptionallyAsync(fn, enlistOrigin));
    }
    
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.exceptionallyAsync(fn, executor, enlistOrigin));
    }
    
    @Override
    public DependentPromise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn, boolean enlistOrigin) {
        return wrap(delegate.exceptionallyCompose(fn, enlistOrigin));
    }
    
    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, boolean enlistOrigin) {
        return wrap(delegate.exceptionallyComposeAsync(fn, enlistOrigin));
    }

    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, 
                                                         Executor executor, 
                                                         boolean enlistOrigin) {
        return wrap(delegate.exceptionallyComposeAsync(fn, executor, enlistOrigin));
    }
    
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate, boolean enlistOrigin) {
        return wrap(delegate.thenFilter(predicate, enlistOrigin));
    }
    
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate, 
                                          Function<? super T, Throwable> errorSupplier, 
                                          boolean enlistOrigin) {
        return wrap(delegate.thenFilter(predicate, errorSupplier, enlistOrigin));
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, boolean enlistOrigin) {
        return wrap(delegate.thenFilterAsync(predicate, enlistOrigin));
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, 
                                               Function<? super T, Throwable> errorSupplier, 
                                               boolean enlistOrigin) {
        return wrap(delegate.thenFilterAsync(predicate, errorSupplier, enlistOrigin));
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, 
                                               Executor executor, 
                                               boolean enlistOrigin) {
        return wrap(delegate.thenFilterAsync(predicate, executor, enlistOrigin));
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, 
                                               Function<? super T, Throwable> errorSupplier, 
                                               Executor executor, 
                                               boolean enlistOrigin) {
        return wrap(delegate.thenFilterAsync(predicate, errorSupplier, executor, enlistOrigin));
    }
    
    @Override
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return wrap(delegate.whenComplete(action, enlistOrigin));
    }

    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return wrap(delegate.whenCompleteAsync(action, enlistOrigin));
    }

    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.whenCompleteAsync(action, executor, enlistOrigin));
    }
    
    @Override
    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.handle(fn, enlistOrigin));
    }
    
    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.handleAsync(fn, enlistOrigin));
    }

    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.handleAsync(fn, executor, enlistOrigin));
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn) {
        return (DependentPromise<U>)super.thenApply(fn);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return (DependentPromise<U>)super.thenApplyAsync(fn);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return (DependentPromise<U>)super.thenApplyAsync(fn, executor);
    }    
    
    @Override
    public DependentPromise<Void> thenAccept(Consumer<? super T> action) {
        return (DependentPromise<Void>)super.thenAccept(action);
    }

    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return (DependentPromise<Void>)super.thenAcceptAsync(action);
    }

    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return (DependentPromise<Void>)super.thenAcceptAsync(action, executor);
    }    

    @Override
    public DependentPromise<Void> thenRun(Runnable action) {
        return (DependentPromise<Void>)super.thenRun(action);
    }

    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action) {
        return (DependentPromise<Void>)super.thenRunAsync(action);
    }

    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action, Executor executor) {
        return (DependentPromise<Void>)super.thenRunAsync(action, executor);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return (DependentPromise<V>)super.thenCombine(other, fn);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return (DependentPromise<V>)super.thenCombineAsync(other, fn);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn, 
                                                       Executor executor) {
        return (DependentPromise<V>)super.thenCombineAsync(other, fn, executor);
    }
    
    @Override
    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return (DependentPromise<Void>)super.thenAcceptBoth(other, action);
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return (DependentPromise<Void>)super.thenAcceptBothAsync(other, action);
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                          BiConsumer<? super T, ? super U> action,
                                                          Executor executor) {
        return (DependentPromise<Void>)super.thenAcceptBothAsync(other, action, executor);
    }    
    
    @Override
    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return (DependentPromise<Void>)super.runAfterBoth(other, action);
    }

    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return (DependentPromise<Void>)super.runAfterBothAsync(other, action);
    }

    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                                    Runnable action, 
                                                    Executor executor) {
        return (DependentPromise<Void>)super.runAfterBothAsync(other, action, executor);
    }
    
    @Override
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return (DependentPromise<U>)super.applyToEither(other, fn);
    }

    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return (DependentPromise<U>)super.applyToEitherAsync(other, fn);
    }

    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Executor executor) {
        return (DependentPromise<U>)super.applyToEitherAsync(other, fn, executor);
    }    

    @Override
    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return (DependentPromise<Void>)super.acceptEither(other, action);
    }

    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return (DependentPromise<Void>)super.acceptEitherAsync(other, action);
    }

    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Executor executor) {
        return (DependentPromise<Void>)super.acceptEitherAsync(other, action, executor);
    }    

    @Override
    public DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return (DependentPromise<Void>)super.runAfterEither(other, action);
    }

    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return (DependentPromise<Void>)super.runAfterEitherAsync(other, action);
    }

    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                                      Runnable action, 
                                                      Executor executor) {
        
        return (DependentPromise<Void>)super.runAfterEitherAsync(other, action, executor);
    }
    
    @Override
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return (DependentPromise<U>)super.thenCompose(fn);
    }

    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return (DependentPromise<U>)super.thenComposeAsync(fn);
    }

    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return (DependentPromise<U>)super.thenComposeAsync(fn, executor);
    }

    @Override
    public DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return (DependentPromise<T>)super.exceptionally(fn);
    }
    
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return (DependentPromise<T>)super.exceptionallyAsync(fn);
    }
    
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        return (DependentPromise<T>)super.exceptionallyAsync(fn, executor);
    }
    
    @Override
    public DependentPromise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return (DependentPromise<T>)super.exceptionallyCompose(fn);
    }
    
    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return (DependentPromise<T>)super.exceptionallyComposeAsync(fn);
    }

    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        return (DependentPromise<T>)super.exceptionallyComposeAsync(fn, executor);
    }
    
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate) {
        return (DependentPromise<T>)super.thenFilter(predicate);
    }
    
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier) {
        return (DependentPromise<T>)super.thenFilter(predicate, errorSupplier);
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate) {
        return (DependentPromise<T>)super.thenFilterAsync(predicate);
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier) {
        return (DependentPromise<T>)super.thenFilterAsync(predicate, errorSupplier);
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor) {
        return (DependentPromise<T>)super.thenFilterAsync(predicate, executor);
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, 
                                               Function<? super T, Throwable> errorSupplier, 
                                               Executor executor) {
        return (DependentPromise<T>)super.thenFilterAsync(predicate, errorSupplier, executor);
    }
    
    @Override
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return (DependentPromise<T>)super.whenComplete(action);
    }

    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return (DependentPromise<T>)super.whenCompleteAsync(action);
    }

    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return (DependentPromise<T>)super.whenCompleteAsync(action, executor);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return (DependentPromise<U>)super.handle(fn);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return (DependentPromise<U>)super.handleAsync(fn);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return (DependentPromise<U>)super.handleAsync(fn, executor);
    }
    
    @Override
    public CompletableFuture<T> toCompletableFuture(boolean enlistOrigin) {
        return delegate.toCompletableFuture(enlistOrigin);
    }

}
