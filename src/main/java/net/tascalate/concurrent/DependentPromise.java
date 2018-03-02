/**
 * ﻿Copyright 2015-2017 Valery Silaev (http://vsilaev.com)
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 
 * <p>{@link Promise} wrapper that may keep track origin of this promise and cancel them
 * along with this promise itself.
 * 
 * For example:
 * <pre>
 * <code>
 * DependentPromise&lt;?&gt; p1 = DefaultDependentPromise.from(CallableTask.runAsync(this::someLongRunningMethod, myExecutor));
 * DependentPromise&lt;?&gt; p2 = p1.thenRunAsync(this::someOtherLongRunningTask, true);
 * ...
 * p2.cancel(true); 
 *  
 * </code>
 * </pre>
 * <p>In the example <code>p2</code> is created with specifying <code>p1</code> as origin (last argument is <code>true</code>).
 * Now when canceling <code>p2</code> both <code>p2</code> and <code>p1</code> will be cancelled if not completed yet. 
 * 
 * <p>The class add overloads to all composition methods declared in {@link CompletionStage} interface.
 * 
 * <p>The ones that accepts another {@link CompletionStage} as argument (named <code>*Both*</code> and
 * <code>*Either*</code> are overloaded with a set of @{link {@link PromiseOrigin} as an argument to let
 * you specify what to enlist as origin: "this" related to method call or the parameter.
 * 
 * <p>Rest of methods from  {@link CompletionStage} API are overloaded with boolean argument 
 * <code>enlistOrigin</code> that specify whether or not the {@link Promise} object whose
 * method is invoiked should be added as an origin to result.
 * 
 * <p>All methods originally  specified in {@link CompletionStage} does not add "this" as an origin to
 * resulting promise.
 * 
 * @author vsilaev
 *
 * @param <T>
 *   a type of the successfully resolved promise value    
 */
public interface DependentPromise<T> extends Promise<T> {
    
    @Override
    default DependentPromise<T> dependent() {
        return this;
    }
    
    // Delay
    default DependentPromise<T> delay(long timeout, TimeUnit unit, boolean delayOnError, boolean enlistOrigin) {
        return delay(Timeouts.toDuration(timeout, unit), delayOnError, enlistOrigin);        
    }

    @Override
    default DependentPromise<T> delay(Duration duration, boolean delayOnError) {
        return delay(duration, delayOnError, false);
    }
    
    DependentPromise<T> delay(Duration duration, boolean delayOnError, boolean enlistOrigin);

    // Or Timeout
    default DependentPromise<T> orTimeout(long timeout, TimeUnit unit, boolean cancelOnTimeout, boolean enlistOrigin) {
        return orTimeout(Timeouts.toDuration(timeout, unit), cancelOnTimeout, enlistOrigin);
    }

    @Override
    default DependentPromise<T> orTimeout(Duration duration, boolean cancelOnTimeout) {
        return orTimeout(duration, cancelOnTimeout, false);
    }
    
    DependentPromise<T> orTimeout(Duration duration, boolean cancelOnTimeout, boolean enlistOrigin);

    // On Timeout
    default DependentPromise<T> onTimeout(T value, Duration duration, boolean cancelOnTimeout, boolean enlistOrigin) {
        return onTimeout(() -> value, duration, cancelOnTimeout, enlistOrigin); 
    }

    @Override
    default DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout) {
        return onTimeout(supplier, duration, cancelOnTimeout, false);
    }
    
    DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout, boolean enlistOrigin);
    
    default <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn) {
        return thenApply(fn, false);
    }

    <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn, boolean enlistOrigin);
    
    default <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return thenApplyAsync(fn, false);
    }

    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, boolean enlistOrigin);
    
    default <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return thenApplyAsync(fn, executor, false);
    }

    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor, boolean enlistOrigin);
    
    default DependentPromise<Void> thenAccept(Consumer<? super T> action) {
        return thenAccept(action, false);
    }

    DependentPromise<Void> thenAccept(Consumer<? super T> action, boolean enlistOrigin);

    default DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return thenAcceptAsync(action, false);
    }

    DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, boolean enlistOrigin);
    
    default DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return thenAcceptAsync(action, executor, false);
    }

    DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor, boolean enlistOrigin);
    
    default DependentPromise<Void> thenRun(Runnable action) {
        return thenRun(action, false);
    }

    DependentPromise<Void> thenRun(Runnable action, boolean enlistOrigin);
    
    default DependentPromise<Void> thenRunAsync(Runnable action) {
        return thenRunAsync(action, false);
    }
    
    DependentPromise<Void> thenRunAsync(Runnable action, boolean enlistOrigin);

    default DependentPromise<Void> thenRunAsync(Runnable action, Executor executor) {
        return thenRunAsync(action, executor, false);
    }
    
    DependentPromise<Void> thenRunAsync(Runnable action, Executor executor, boolean enlistOrigin);

    default <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombine(other, fn, PromiseOrigin.NONE);
    }

    <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, 
                                           BiFunction<? super T, ? super U, ? extends V> fn,
                                           Set<PromiseOrigin> enlistOptions);

    
    default <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, fn, PromiseOrigin.NONE);
    }

    <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, 
                                                BiFunction<? super T, ? super U, ? extends V> fn,
                                                Set<PromiseOrigin> enlistOptions);
    
    default 
    <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                BiFunction<? super T, ? super U, ? extends V> fn, 
                                                Executor executor) {
        
        return thenCombineAsync(other, fn, executor, PromiseOrigin.NONE);
    }


    <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                BiFunction<? super T, ? super U, ? extends V> fn, 
                                                Executor executor,
                                                Set<PromiseOrigin> enlistOptions);

    default <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBoth(other, action, PromiseOrigin.NONE);
    }

    <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, 
                                              BiConsumer<? super T, ? super U> action,
                                              Set<PromiseOrigin> enlistOptions);

    default <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBothAsync(other, action, PromiseOrigin.NONE);
    }
    
    <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                   BiConsumer<? super T, ? super U> action,
                                                   Set<PromiseOrigin> enlistOptions);

    default <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                           BiConsumer<? super T, ? super U> action, 
                                                           Executor executor) {
        
        return thenAcceptBothAsync(other, action, executor, PromiseOrigin.NONE);
    }


    <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                   BiConsumer<? super T, ? super U> action, 
                                                   Executor executor,
                                                   Set<PromiseOrigin> enlistOptions);    


    default DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return runAfterBoth(other, action, PromiseOrigin.NONE);
    }

    DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions);
    
    default DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action, PromiseOrigin.NONE);
    }

    DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions);
    
    default DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                                     Runnable action, 
                                                     Executor executor) {
        return runAfterBothAsync(other, action, executor, PromiseOrigin.NONE);
    }

    DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                             Runnable action, 
                                             Executor executor,
                                             Set<PromiseOrigin> enlistOptions);
    
    default <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEither(other, fn, PromiseOrigin.NONE);
    }
    
    <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, 
                                          Function<? super T, U> fn,
                                          Set<PromiseOrigin> enlistOptions);    

    default <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEitherAsync(other, fn, PromiseOrigin.NONE);
    }


    <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                               Function<? super T, U> fn,
                                               Set<PromiseOrigin> enlistOptions);

    default <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                       Function<? super T, U> fn,
                                                       Executor executor) {
        
        return applyToEitherAsync(other, fn, executor, PromiseOrigin.NONE);
    }


    <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                               Function<? super T, U> fn,
                                               Executor executor,
                                               Set<PromiseOrigin> enlistOptions);
    

    default DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEither(other, action, PromiseOrigin.NONE);
    }
    
    DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, 
                                        Consumer<? super T> action,
                                        Set<PromiseOrigin> enlistOptions);

    default DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEitherAsync(other, action, PromiseOrigin.NONE);
    }

    DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                             Consumer<? super T> action,
                                             Set<PromiseOrigin> enlistOptions);

    
    default DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                     Consumer<? super T> action,
                                                     Executor executor) {
        
        return acceptEitherAsync(other, action, executor, PromiseOrigin.NONE);
    }

    DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                             Consumer<? super T> action,
                                             Executor executor,
                                             Set<PromiseOrigin> enlistOptions);

    
    default DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return runAfterEither(other, action, PromiseOrigin.NONE);
    }

    DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions);
    
    default DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action, PromiseOrigin.NONE);
    }

    DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions);
    
    default DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                                       Runnable action, 
                                                       Executor executor) {
        return runAfterEitherAsync(other, action, executor, PromiseOrigin.NONE);
    }
    
    DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                               Runnable action, 
                                               Executor executor,
                                               Set<PromiseOrigin> enlistOptions);

    default <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenCompose(fn, false);
    }

    <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin);
    
    default <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenComposeAsync(fn, false);
    }
    
    <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin);
    
    default <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return thenComposeAsync(fn, executor, false);
    }
    
    <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor, boolean enlistOrigin);

    default DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return exceptionally(fn, false);
    }
    
    DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn, boolean enlistOrigin);
    
    default DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return whenComplete(action, false);
    }

    DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin);

    default DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return whenCompleteAsync(action, false);
    }

    DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin);

    default DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return whenCompleteAsync(action, executor, false);
    }

    DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor, boolean enlistOrigin);
    
    default <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handle(fn, false);
    }

    <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin);
    
    default <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handleAsync(fn, false);
    }

    <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin);

    
    default <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return handleAsync(fn, executor, false);
    }

    <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor, boolean enlistOrigin);
}
