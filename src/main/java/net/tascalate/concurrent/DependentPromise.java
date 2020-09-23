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
package net.tascalate.concurrent;

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

import net.tascalate.concurrent.decorators.ExecutorBoundDependentPromise;

/**
 * 
 * <p>{@link Promise} wrapper that may keep track origin of this promise and cancel them
 * along with this promise itself.
 * 
 * For example:
 * <pre>
 * <code>
 * DependentPromise&lt;?&gt; p1 = DependentPromise.from(CallableTask.runAsync(this::someLongRunningMethod, myExecutor));
 * DependentPromise&lt;?&gt; p2 = p1.thenRunAsync(this::someOtherLongRunningTask, true);
 * ...
 * p2.cancel(true); 
 *  
 * </code>
 * </pre>
 * <p>In the example <code>p2</code> is created with specifying <code>p1</code> as origin (last argument is <code>true</code>).
 * Now when canceling <code>p2</code> both <code>p2</code> and <code>p1</code> will be cancelled if not completed yet. 
 * 
 * <p>The class add overloads to all composition methods declared in {@link CompletionStage} and {@link Promise} interface.
 * 
 * <p>The ones that accepts another {@link CompletionStage} as argument (named <code>*Both*</code> and
 * <code>*Either*</code> are overloaded with a set of @{link {@link PromiseOrigin} as an argument to let
 * you specify what to enlist as origin: "this" related to the method call or the parameter.
 * 
 * <p>Rest of methods from {@link CompletionStage} and {@link Promise} API are overloaded with boolean argument 
 * <code>enlistOrigin</code> that specify whether or not the {@link Promise} object whose
 * method is invoked should be added as an origin to result.
 * 
 * <p>All methods originally specified in {@link CompletionStage} does not add "this" as an origin to
 * resulting promise.
 * 
 * @author vsilaev
 *
 * @param <T>
 *   a type of the successfully resolved promise value    
 */
public interface DependentPromise<T> extends Promise<T> {
    
    @Override
    default <D> D as(Function<? super Promise<T>, D> decoratorFactory) {
        return asʹ(decoratorFactory);
    }
    
    default <D> D asʹ(Function<? super DependentPromise<T>, D> decoratorFactory) {
        return decoratorFactory.apply(this);
    }
    
    // Only for backward-compatibility with versions below 0.5.4
    public static <U> DependentPromise<U> from(Promise<U> source) {
        return ConfigurableDependentPromise.from(source);
    }
    
    // For symmetry with above
    public static <U> DependentPromise<U> from(Promise<U> source, Set<PromiseOrigin> defaultEnlistOptions) {
        return ConfigurableDependentPromise.from(source, defaultEnlistOptions);
    }

    @Override
    DependentPromise<T> onCancel(Runnable action);
    
    @Override
    default DependentPromise<T> defaultAsyncOn(Executor executor) {
        return new ExecutorBoundDependentPromise<>(this, executor);
    }
    
    // Delay
    @Override
    default DependentPromise<T> delay(long timeout, TimeUnit unit) {
        return delay(timeout, unit, true);
    }
    
    @Override
    default DependentPromise<T> delay(long timeout, TimeUnit unit, 
                                      boolean delayOnError) {
        return delay(Timeouts.toDuration(timeout, unit), delayOnError);
    }
    
    @Override
    default DependentPromise<T> delay(Duration duration) {
        return delay(duration, true);
    }
    
    default DependentPromise<T> delay(long timeout, TimeUnit unit, 
                                      boolean delayOnError, boolean enlistOrigin) {
        return delay(Timeouts.toDuration(timeout, unit), delayOnError, enlistOrigin);        
    }
    
    @Override
    DependentPromise<T> delay(Duration duration, boolean delayOnError);

    DependentPromise<T> delay(Duration duration, boolean delayOnError, boolean enlistOrigin);

    // Or Timeout
    @Override    
    default DependentPromise<T> orTimeout(long timeout, TimeUnit unit) {
        return orTimeout(timeout, unit, true);
    }
    
    @Override
    default DependentPromise<T> orTimeout(long timeout, TimeUnit unit, 
                                          boolean cancelOnTimeout) {
        return orTimeout(Timeouts.toDuration(timeout, unit), cancelOnTimeout);
    }
    
    default DependentPromise<T> orTimeout(long timeout, TimeUnit unit, 
                                          boolean cancelOnTimeout, boolean enlistOrigin) {
        return orTimeout(Timeouts.toDuration(timeout, unit), cancelOnTimeout, enlistOrigin);
    }
    
    @Override
    default DependentPromise<T> orTimeout(Duration duration) {
        return orTimeout(duration, true);
    }
    
    @Override
    DependentPromise<T> orTimeout(Duration duration, boolean cancelOnTimeout);

    DependentPromise<T> orTimeout(Duration duration, 
                                  boolean cancelOnTimeout, boolean enlistOrigin);

    // On Timeout
    @Override
    default DependentPromise<T> onTimeout(T value, long timeout, TimeUnit unit) {
        return onTimeout(value, timeout, unit, true);
    }
    
    @Override
    default DependentPromise<T> onTimeout(T value, 
                                          long timeout, TimeUnit unit, 
                                          boolean cancelOnTimeout) {
        return onTimeout(value, Timeouts.toDuration(timeout, unit), cancelOnTimeout);
    }
    
    default DependentPromise<T> onTimeout(T value, 
                                          long timeout, TimeUnit unit, 
                                          boolean cancelOnTimeout, boolean enlistOrigin) {
        return onTimeout(value, Timeouts.toDuration(timeout, unit), cancelOnTimeout, enlistOrigin);
    }
    
    @Override
    default DependentPromise<T> onTimeout(T value, Duration duration) {
        return onTimeout(value, duration, true);
    }
    
    @Override
    DependentPromise<T> onTimeout(T value, Duration duration, boolean cancelOnTimeout);
    
    DependentPromise<T> onTimeout(T value, Duration duration, 
                                  boolean cancelOnTimeout, boolean enlistOrigin);
    
    @Override
    default DependentPromise<T> onTimeout(Supplier<? extends T> supplier, 
                                          long timeout, TimeUnit unit) {
        return onTimeout(supplier, timeout, unit, true);
    }
    
    @Override
    default DependentPromise<T> onTimeout(Supplier<? extends T> supplier, 
                                          long timeout, TimeUnit unit, 
                                          boolean cancelOnTimeout) {
        return onTimeout(supplier, Timeouts.toDuration(timeout, unit), cancelOnTimeout);
    }
    
    default DependentPromise<T> onTimeout(Supplier<? extends T> supplier, 
                                          long timeout, TimeUnit unit, 
                                          boolean cancelOnTimeout, boolean enlistOrigin) {
        return onTimeout(supplier, Timeouts.toDuration(timeout, unit), cancelOnTimeout, enlistOrigin);
    }
    
    @Override
    default DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration) {
        return onTimeout(supplier, duration, true);
    }
    
    @Override
    DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, 
                                  boolean cancelOnTimeout);

    DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, 
                                  boolean cancelOnTimeout, boolean enlistOrigin);
    
    <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn, boolean enlistOrigin);
    
    <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, boolean enlistOrigin);
    
    <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor, boolean enlistOrigin);
    
    DependentPromise<Void> thenAccept(Consumer<? super T> action, boolean enlistOrigin);

    DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, boolean enlistOrigin);
    
    DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor, boolean enlistOrigin);
    
    DependentPromise<Void> thenRun(Runnable action, boolean enlistOrigin);
    
    DependentPromise<Void> thenRunAsync(Runnable action, boolean enlistOrigin);

    DependentPromise<Void> thenRunAsync(Runnable action, Executor executor, boolean enlistOrigin);

    <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, 
                                           BiFunction<? super T, ? super U, ? extends V> fn,
                                           Set<PromiseOrigin> enlistOptions);

    
    <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, 
                                                BiFunction<? super T, ? super U, ? extends V> fn,
                                                Set<PromiseOrigin> enlistOptions);
    
    <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                BiFunction<? super T, ? super U, ? extends V> fn, 
                                                Executor executor,
                                                Set<PromiseOrigin> enlistOptions);

    <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, 
                                              BiConsumer<? super T, ? super U> action,
                                              Set<PromiseOrigin> enlistOptions);

    <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                   BiConsumer<? super T, ? super U> action,
                                                   Set<PromiseOrigin> enlistOptions);

    <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                   BiConsumer<? super T, ? super U> action, 
                                                   Executor executor,
                                                   Set<PromiseOrigin> enlistOptions);    

    DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions);
    
    DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions);
    
    DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                             Runnable action, 
                                             Executor executor,
                                             Set<PromiseOrigin> enlistOptions);
    
    <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, 
                                          Function<? super T, U> fn,
                                          Set<PromiseOrigin> enlistOptions);    

    <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                               Function<? super T, U> fn,
                                               Set<PromiseOrigin> enlistOptions);

    <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                               Function<? super T, U> fn,
                                               Executor executor,
                                               Set<PromiseOrigin> enlistOptions);
    

    DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, 
                                        Consumer<? super T> action,
                                        Set<PromiseOrigin> enlistOptions);

    DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                             Consumer<? super T> action,
                                             Set<PromiseOrigin> enlistOptions);

    
    DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                             Consumer<? super T> action,
                                             Executor executor,
                                             Set<PromiseOrigin> enlistOptions);

    
    DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions);
    
    DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions);
    
    DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                               Runnable action, 
                                               Executor executor,
                                               Set<PromiseOrigin> enlistOptions);

    <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin);
    
    <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin);
    
    <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, 
                                             Executor executor, 
                                             boolean enlistOrigin);

    DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn, boolean enlistOrigin);
    
    DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, boolean enlistOrigin);
    
    DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, 
                                           Executor executor, 
                                           boolean enlistOrigin);
    
    DependentPromise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn, boolean enlistOrigin);
    
    DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, boolean enlistOrigin);

    DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, 
                                                  Executor executor, 
                                                  boolean enlistOrigin);
    
    DependentPromise<T> thenFilter(Predicate<? super T> predicate, boolean enlistOrigin);
    
    DependentPromise<T> thenFilter(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, boolean enlistOrigin);
    
    DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, boolean enlistOrigin);
    
    DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, boolean enlistOrigin);
    
    DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor, boolean enlistOrigin);
    
    DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, Executor executor, boolean enlistOrigin);
    
    DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin);

    DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin);

    DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, 
                                          Executor executor, 
                                          boolean enlistOrigin);
    
    <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin);
    
    <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin);

    <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, 
                                        Executor executor, 
                                        boolean enlistOrigin);
    
    CompletableFuture<T> toCompletableFuture(boolean enlistOrigin);
    
    // Re-declare CompletionStage original methods with right return type
    @Override
    <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn);

    @Override
    <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn);

    @Override
    <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor);
    
    @Override
    DependentPromise<Void> thenAccept(Consumer<? super T> action);

    @Override
    DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action);

    @Override
    DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor);

    @Override
    DependentPromise<Void> thenRun(Runnable action);

    @Override
    DependentPromise<Void> thenRunAsync(Runnable action);

    @Override
    DependentPromise<Void> thenRunAsync(Runnable action, Executor executor);

    @Override
    <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, 
                                           BiFunction<? super T, ? super U, ? extends V> fn);

    @Override
    <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, 
                                                BiFunction<? super T, ? super U, ? extends V> fn);

    @Override
    <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                BiFunction<? super T, ? super U, ? extends V> fn, 
                                                Executor executor);
    
    @Override
    <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, 
                                              BiConsumer<? super T, ? super U> action);

    @Override
    <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                   BiConsumer<? super T, ? super U> action);
    
    @Override
    <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                   BiConsumer<? super T, ? super U> action,
                                                   Executor executor); 
    
    @Override
    DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action);

    @Override
    DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action);

    @Override
    DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                             Runnable action, 
                                             Executor executor);
    
    @Override
    <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn);

    @Override
    <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn);

    @Override
    <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                               Function<? super T, U> fn,
                                               Executor executor); 

    @Override
    DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action);

    @Override
    DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action);

    @Override
    DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                             Consumer<? super T> action,
                                             Executor executor); 

    @Override
    DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action);

    @Override
    DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action);

    @Override
    DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                               Runnable action, 
                                               Executor executor);
    
    @Override
    <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn);

    @Override
    <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn);

    @Override
    <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor);

    @Override
    DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn);
    
    @Override
    DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn);
    
    @Override
    DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor);
    
    @Override
    DependentPromise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn);
    
    @Override
    DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn);

    @Override
    DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor);

    @Override
    DependentPromise<T> thenFilter(Predicate<? super T> predicate);
    
    @Override
    DependentPromise<T> thenFilter(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier);
    
    @Override
    DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate);
    
    @Override
    DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier);
    
    @Override
    DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor);
    
    @Override
    DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, Executor executor);
    
    @Override
    DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action);

    @Override
    DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action);

    @Override
    DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor);

    @Override
    <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn);

    @Override
    <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn);

    @Override
    <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor);
    
}
