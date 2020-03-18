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

public class ExtendedDependentPromiseDecorator <T> 
    extends AbstractDependentPromiseDecorator<T> {

    protected ExtendedDependentPromiseDecorator(DependentPromise<T> delegate) {
        super(delegate);
    }
    
    protected Runnable wrapArgument(Runnable original, boolean async) {
        return original;
    }
    
    private <U, R> Function<U, R> wrapArgument(Function<U, R> original, boolean async) {
        return wrapArgument(original, async, false);
    }
    
    protected <U, R> Function<U, R> wrapArgument(Function<U, R> original, boolean async, boolean isCompose) {
        return original;
    }
    
    protected <U> Consumer<U> wrapArgument(Consumer<U> original, boolean async) {
        return original;
    }
    
    protected <U> Supplier<U> wrapArgument(Supplier<U> original, boolean async) {
        return original;
    }
    
    protected <U> Predicate<U> wrapArgument(Predicate<U> original, boolean async) {
        return original;
    }
    
    protected <U, V, R> BiFunction<U, V, R> wrapArgument(BiFunction<U, V, R> original, boolean async) {
        return original;
    }
    
    protected <U, V> BiConsumer<U, V> wrapArgument(BiConsumer<U, V> original, boolean async) {
        return original;
    }
    
    protected <U> CompletionStage<U> wrapArgument(CompletionStage<U> original, boolean async) {
        return original;
    }
    
    protected Executor wrapArgument(Executor original) {
        return original;
    }
    
    protected <U> DependentPromise<U> wrapResult(CompletionStage<U> original) {
        return new ExtendedDependentPromiseDecorator<>((DependentPromise<U>)original);
    }
    
    @Override
    protected final <U> DependentPromise<U> wrap(CompletionStage<U> original) {
        return wrapResult(original);
    }    
    
    @Override
    public DependentPromise<T> onCancel(Runnable code) {
        return super.onCancel(wrapArgument(code, false));
    }
    
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit, 
                                         boolean cancelOnTimeout, boolean enlistOrigin) {
        return super.onTimeout(wrapArgument(supplier, true), timeout, unit, cancelOnTimeout, enlistOrigin);
    }

    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, 
                                         boolean cancelOnTimeout, boolean enlistOrigin) {
        return super.onTimeout(wrapArgument(supplier, true), duration, cancelOnTimeout, enlistOrigin);
    }

    @Override
    public <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return super.thenApply(wrapArgument(fn, false), enlistOrigin);
    }
    
    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return super.thenApplyAsync(wrapArgument(fn, true), enlistOrigin);
    }
    
    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, 
                                                  Executor executor, 
                                                  boolean enlistOrigin) {
        return super.thenApplyAsync(wrapArgument(fn, true), wrapArgument(executor), enlistOrigin);
    }
    
    @Override
    public DependentPromise<Void> thenAccept(Consumer<? super T> action, boolean enlistOrigin) {
        return super.thenAccept(wrapArgument(action, false), enlistOrigin);
    }

    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, boolean enlistOrigin) {
        return super.thenAcceptAsync(wrapArgument(action, true), enlistOrigin);
    }
    
    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, 
                                                  Executor executor, 
                                                  boolean enlistOrigin) {
        return super.thenAcceptAsync(wrapArgument(action, true), wrapArgument(executor), enlistOrigin);
    }
    
    @Override
    public DependentPromise<Void> thenRun(Runnable action, boolean enlistOrigin) {
        return super.thenRun(wrapArgument(action, false), enlistOrigin);
    }
    
    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action, boolean enlistOrigin) {
        return super.thenRunAsync(wrapArgument(action, true), enlistOrigin);
    }

    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action, 
                                               Executor executor, 
                                               boolean enlistOrigin) {
        return super.thenRunAsync(wrapArgument(action, true), wrapArgument(executor), enlistOrigin);
    }

    @Override
    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, 
                                                  BiFunction<? super T, ? super U, ? extends V> fn,
                                                  Set<PromiseOrigin> enlistOptions) {
        return super.thenCombine(
            wrapArgument(other, false), wrapArgument(fn, false), enlistOptions
        );
    }

    
    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, 
                                                       BiFunction<? super T, ? super U, ? extends V> fn,
                                                       Set<PromiseOrigin> enlistOptions) {
        return super.thenCombineAsync(
            wrapArgument(other, true), wrapArgument(fn, true), enlistOptions
        );
    }
    
    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn, 
                                                       Executor executor,
                                                       Set<PromiseOrigin> enlistOptions) {
        return super.thenCombineAsync(
            wrapArgument(other, true), wrapArgument(fn, true), wrapArgument(executor), enlistOptions
        );
    }
    

    @Override
    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, 
                                                     BiConsumer<? super T, ? super U> action,
                                                     Set<PromiseOrigin> enlistOptions) {
        return super.thenAcceptBoth(
            wrapArgument(other, false), wrapArgument(action, false), enlistOptions
        );
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                          BiConsumer<? super T, ? super U> action,
                                                          Set<PromiseOrigin> enlistOptions) {
        return super.thenAcceptBothAsync(
            wrapArgument(other, true), wrapArgument(action, true), enlistOptions
        );
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                          BiConsumer<? super T, ? super U> action, 
                                                          Executor executor,
                                                          Set<PromiseOrigin> enlistOptions) {
        return super.thenAcceptBothAsync(
            wrapArgument(other, true), wrapArgument(action, true), wrapArgument(executor), enlistOptions
        );
    }

    @Override
    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other, 
                                               Runnable action, 
                                               Set<PromiseOrigin> enlistOptions) {
        return super.runAfterBoth(
            wrapArgument(other, false), wrapArgument(action, false), enlistOptions
        );
    }
    
    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                                    Runnable action, 
                                                    Set<PromiseOrigin> enlistOptions) {
        return super.runAfterBothAsync(
            wrapArgument(other, true), wrapArgument(action, true), enlistOptions
        );
    }
    
    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                                    Runnable action, 
                                                    Executor executor,
                                                    Set<PromiseOrigin> enlistOptions) {
        return super.runAfterBothAsync(
            wrapArgument(other, true), wrapArgument(action, true), wrapArgument(executor), enlistOptions
        );
    }
    
    @Override
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, 
                                                 Function<? super T, U> fn,
                                                 Set<PromiseOrigin> enlistOptions) {
        return super.applyToEither(
            wrapArgument(other, false), wrapArgument(fn, false), enlistOptions
        );
    }
    

    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Set<PromiseOrigin> enlistOptions) {
        return super.applyToEitherAsync(
            wrapArgument(other, true), wrapArgument(fn, true), enlistOptions
        );
    }

    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Executor executor,
                                                      Set<PromiseOrigin> enlistOptions) {
        return super.applyToEitherAsync(
            wrapArgument(other, true), wrapArgument(fn, true), wrapArgument(executor), enlistOptions
        );
    }
    

    @Override
    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, 
                                               Consumer<? super T> action,
                                               Set<PromiseOrigin> enlistOptions) {
        return super.acceptEither(
            wrapArgument(other, false), wrapArgument(action, false), enlistOptions
        );
    }

    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Set<PromiseOrigin> enlistOptions) {
        return super.acceptEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true), enlistOptions
        );
    }

    
    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Executor executor,
                                                    Set<PromiseOrigin> enlistOptions) {
        return super.acceptEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true), wrapArgument(executor), enlistOptions
        );
    }

    
    @Override
    public DependentPromise<Void> runAfterEither(CompletionStage<?> other, 
                                                 Runnable action, 
                                                 Set<PromiseOrigin> enlistOptions) {
        return super.runAfterEither(
            wrapArgument(other, false), wrapArgument(action, false), enlistOptions
        );
    }
    
    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                                      Runnable action, 
                                                      Set<PromiseOrigin> enlistOptions) {
        return super.runAfterEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true), enlistOptions
        );
    }
    
    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                                      Runnable action, 
                                                      Executor executor,
                                                      Set<PromiseOrigin> enlistOptions) {
        return super.runAfterEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true), executor, enlistOptions
        );
    }

    @Override
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return super.thenCompose(wrapArgument(fn, false, true), enlistOrigin);
    }
    
    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return super.thenComposeAsync(wrapArgument(fn, true, true), enlistOrigin);
    }
    
    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, 
                                                    Executor executor, 
                                                    boolean enlistOrigin) {
        return super.thenComposeAsync(wrapArgument(fn, true, true), wrapArgument(executor), enlistOrigin);
    }

    @Override
    public DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn, boolean enlistOrigin) {
        return super.exceptionally(wrapArgument(fn, false), enlistOrigin);
    }
    
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, boolean enlistOrigin) {
        return super.exceptionallyAsync(wrapArgument(fn, true), enlistOrigin);
    }
    
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, 
                                                  Executor executor, 
                                                  boolean enlistOrigin) {
        return super.exceptionallyAsync(wrapArgument(fn, true), executor, enlistOrigin);
    }
    
    @Override
    public DependentPromise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn, boolean enlistOrigin) {
        return super.exceptionallyCompose(wrapArgument(fn, false, true), enlistOrigin);
    }
    
    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, boolean enlistOrigin) {
        return super.exceptionallyComposeAsync(wrapArgument(fn, true, true), enlistOrigin);
    }

    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, 
                                                         Executor executor, 
                                                         boolean enlistOrigin) {
        return super.exceptionallyComposeAsync(wrapArgument(fn, true, true), executor, enlistOrigin);
    }
    
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate, boolean enlistOrigin) {
        return super.thenFilter(wrapArgument(predicate, false), enlistOrigin);
    }
    
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate, Supplier<Throwable> errorSupplier, boolean enlistOrigin) {
        return super.thenFilter(wrapArgument(predicate, false), wrapArgument(errorSupplier, false), enlistOrigin);
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, boolean enlistOrigin) {
        return super.thenFilterAsync(wrapArgument(predicate, true), enlistOrigin);
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Supplier<Throwable> errorSupplier, boolean enlistOrigin) {
        return super.thenFilterAsync(wrapArgument(predicate, true), wrapArgument(errorSupplier, true), enlistOrigin);
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor, boolean enlistOrigin) {
        return super.thenFilterAsync(wrapArgument(predicate, true), wrapArgument(executor), enlistOrigin);
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Supplier<Throwable> errorSupplier, Executor executor, boolean enlistOrigin) {
        return super.thenFilterAsync(wrapArgument(predicate, true), wrapArgument(errorSupplier, true), wrapArgument(executor), enlistOrigin);
    }
    
    @Override
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return super.whenComplete(wrapArgument(action, false), enlistOrigin);
    }

    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return super.whenCompleteAsync(wrapArgument(action, true), enlistOrigin);
    }

    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, 
                                                 Executor executor, 
                                                 boolean enlistOrigin) {
        return super.whenCompleteAsync(wrapArgument(action, true), wrapArgument(executor), enlistOrigin);
    }
    
    @Override
    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return super.handle(wrapArgument(fn, false), enlistOrigin);
    }
    
    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return super.handleAsync(wrapArgument(fn, true), enlistOrigin);
    }

    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, 
                                               Executor executor, 
                                               boolean enlistOrigin) {
        return super.handleAsync(wrapArgument(fn, true), wrapArgument(executor), enlistOrigin);
    }

    // ---
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit) {
        return super.onTimeout(wrapArgument(supplier, true), timeout, unit);
    }
    
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return super.onTimeout(wrapArgument(supplier, true), timeout, unit, cancelOnTimeout);
    }
    
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration) {
        return super.onTimeout(wrapArgument(supplier, true), duration);
    }
    
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout) {
        return super.onTimeout(wrapArgument(supplier, true), duration, cancelOnTimeout);
    }

    @Override
    public <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn) {
        return super.thenApply(wrapArgument(fn, false));
    }

    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return super.thenApplyAsync(wrapArgument(fn, true));
    }

    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return super.thenApplyAsync(wrapArgument(fn, true), wrapArgument(executor));
    }

    @Override
    public DependentPromise<Void> thenAccept(Consumer<? super T> action) {
        return super.thenAccept(wrapArgument(action, false));
    }

    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return super.thenAcceptAsync(wrapArgument(action, true));
    }

    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return super.thenAcceptAsync(wrapArgument(action, true), wrapArgument(executor));
    }

    @Override
    public DependentPromise<Void> thenRun(Runnable action) {
        return super.thenRun(wrapArgument(action, false));
    }

    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action) {
        return super.thenRunAsync(wrapArgument(action, true));
    }

    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action, Executor executor) {
        return super.thenRunAsync(wrapArgument(action, true), wrapArgument(executor));
    }

    @Override
    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, 
                                                  BiFunction<? super T, ? super U, ? extends V> fn) {
        return super.thenCombine(
            wrapArgument(other, false), wrapArgument(fn, false)
        );
    }

    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, 
                                                       BiFunction<? super T, ? super U, ? extends V> fn) {
        return super.thenCombineAsync(
            wrapArgument(other, true), wrapArgument(fn, true)
        );
    }

    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn, 
                                                       Executor executor) {
        return super.thenCombineAsync(
            wrapArgument(other, true), wrapArgument(fn, true), wrapArgument(executor)
        );
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, 
                                                     BiConsumer<? super T, ? super U> action) {
        return super.thenAcceptBoth(
            wrapArgument(other, false), wrapArgument(action, false)
        );
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                          BiConsumer<? super T, ? super U> action) {
        return super.thenAcceptBothAsync(
            wrapArgument(other, true), wrapArgument(action, true)
        );
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                          BiConsumer<? super T, ? super U> action, 
                                                          Executor executor) {
        return super.thenAcceptBothAsync(
            wrapArgument(other, true), wrapArgument(action, true), wrapArgument(executor)
        );
    }

    @Override
    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return super.runAfterBoth(
            wrapArgument(other, false), wrapArgument(action, false)
        );
    }

    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return super.runAfterBothAsync(
            wrapArgument(other, true), wrapArgument(action, true)
        );
    }

    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                                    Runnable action, 
                                                    Executor executor) {
        return super.runAfterBothAsync(
            wrapArgument(other, true), wrapArgument(action, true), wrapArgument(executor)
        );
    }

    @Override
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, 
                                                 Function<? super T, U> fn) {
        return super.applyToEither(
            wrapArgument(other, false), wrapArgument(fn, false)
        );
    }

    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn) {
        return super.applyToEitherAsync(
            wrapArgument(other, true), wrapArgument(fn, true)
        );
    }

    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Executor executor) {
        return super.applyToEitherAsync(
            wrapArgument(other, true), wrapArgument(fn, true), wrapArgument(executor)
        );
    }

    @Override
    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, 
                                               Consumer<? super T> action) {
        return super.acceptEither(
            wrapArgument(other, false), wrapArgument(action, false)
        );
    }

    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action) {
        return super.acceptEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true)
        );
    }

    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Executor executor) {
        return super.acceptEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true), wrapArgument(executor)
        );
    }

    @Override
    public DependentPromise<Void> runAfterEither(CompletionStage<?> other, 
                                                 Runnable action) {
        return super.runAfterEither(
            wrapArgument(other, false), wrapArgument(action, false)
        );
    }

    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                                      Runnable action) {
        return super.runAfterEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true)
        );
    }

    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                                      Runnable action, 
                                                      Executor executor) {
        return super.runAfterEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true), wrapArgument(executor)
        );
    }

    @Override
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return super.thenCompose(wrapArgument(fn, false, true));
    }

    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return super.thenComposeAsync(wrapArgument(fn, true, true));
    }

    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return super.thenComposeAsync(wrapArgument(fn, true, true), wrapArgument(executor));
    }

    @Override
    public DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return super.exceptionally(wrapArgument(fn, false));
    }
    
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return super.exceptionallyAsync(wrapArgument(fn, true));
    }
    
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        return super.exceptionallyAsync(wrapArgument(fn, true), executor);
    }
    
    @Override
    public DependentPromise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return super.exceptionallyCompose(wrapArgument(fn, false, true));
    }
    
    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return super.exceptionallyComposeAsync(wrapArgument(fn, true, true));
    }

    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        return super.exceptionallyComposeAsync(wrapArgument(fn, true, true), executor);
    }
    
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate) {
        return super.thenFilter(wrapArgument(predicate, false));
    }
    
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate, Supplier<Throwable> errorSupplier) {
        return super.thenFilter(wrapArgument(predicate, false), wrapArgument(errorSupplier, false));
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate) {
        return super.thenFilterAsync(wrapArgument(predicate, true));
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Supplier<Throwable> errorSupplier) {
        return super.thenFilterAsync(wrapArgument(predicate, true), wrapArgument(errorSupplier, true));
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor) {
        return super.thenFilterAsync(wrapArgument(predicate, true), wrapArgument(executor));
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Supplier<Throwable> errorSupplier, Executor executor) {
        return super.thenFilterAsync(wrapArgument(predicate, true), wrapArgument(errorSupplier, true), wrapArgument(executor));
    }

    @Override
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return super.whenComplete(wrapArgument(action, false));
    }

    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return super.whenCompleteAsync(wrapArgument(action, true));
    }

    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return super.whenCompleteAsync(wrapArgument(action, true), wrapArgument(executor));
    }

    @Override
    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return super.handle(wrapArgument(fn, false));
    }

    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return super.handleAsync(wrapArgument(fn, true));
    }

    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return super.handleAsync(wrapArgument(fn, true), wrapArgument(executor));
    }
}
