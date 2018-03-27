/**
 * ﻿Copyright 2015-2018 Valery Silaev (http://vsilaev.com)
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

import static net.tascalate.concurrent.SharedFunctions.cancelPromise;
import static net.tascalate.concurrent.LinkedCompletion.FutureCompletion;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.tascalate.concurrent.decorators.AbstractPromiseDecorator; 

/**
 * 
 * <p>{@link DependentPromise} implementation, i.e. concrete wrapper that may keep track origin of this promise 
 * and cancel them along with this promise itself.
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
 * <p>The class implements overloads to all composition methods declared in {@link DependentPromise} interface.
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
public class ConfigurableDependentPromise<T> implements DependentPromise<T> {
    final protected Promise<T> delegate;
    final protected CompletionStage<?>[] cancellableOrigins;
    final private Set<PromiseOrigin> defaultEnlistOptions;
    
    protected ConfigurableDependentPromise(Promise<T> delegate, Set<PromiseOrigin> defaultEnlistOptions, CompletionStage<?>[] cancellableOrigins) {
        this.delegate = delegate;
        this.defaultEnlistOptions = defaultEnlistOptions == null || defaultEnlistOptions.isEmpty() ? 
            PromiseOrigin.NONE : defaultEnlistOptions;
        this.cancellableOrigins = cancellableOrigins;
    }
    
    public static <U> DependentPromise<U> from(Promise<U> source) {
        return from(source, PromiseOrigin.NONE);
    }
    
    public static <U> DependentPromise<U> from(Promise<U> source, Set<PromiseOrigin> defaultEnlistOptions) {
        return doWrap(source, defaultEnlistOptions, null);
    }
    
    protected <U> DependentPromise<U> wrap(Promise<U> original, CompletionStage<?>[] cancellableOrigins) {
        return doWrap(original, defaultEnlistOptions, cancellableOrigins);
    }
    
    private static <U> DependentPromise<U> doWrap(Promise<U> original, Set<PromiseOrigin> defaultEnlistOptions, CompletionStage<?>[] cancellableOrigins) {
        if (null == cancellableOrigins || cancellableOrigins.length == 0) {
            // Nothing to enlist additionally for this "original" instance
            if (original instanceof ConfigurableDependentPromise) {
                ConfigurableDependentPromise<U> ioriginal = (ConfigurableDependentPromise<U>)original;
                if (identicalSets(ioriginal.defaultEnlistOptions, defaultEnlistOptions)) {
                    // Same defaultEnlistOptions, may reuse 
                    return ioriginal;
                }
            }
        }
        final ConfigurableDependentPromise<U> result = new ConfigurableDependentPromise<>(original, defaultEnlistOptions, cancellableOrigins);
        if (result.isCancelled()) {
            // Wrapped over already cancelled Promise
            // So result.cancel() has no effect
            // and we have to cancel origins explicitly
            // right after construction
            cancelPromises(result.cancellableOrigins, true);
        }
        return result;
    }
    
    
    // All delay overloads delegate to these methods
    @Override
    public DependentPromise<T> delay(Duration duration, boolean delayOnError) {
        return delay(duration, delayOnError, defaultEnlistOrigin());
    }
    
    @Override
    public DependentPromise<T> delay(Duration duration, boolean delayOnError, boolean enlistOrigin) {
        CompletableFuture<T> delayed = new CompletableFuture<>();
        whenComplete(Timeouts.configureDelay(this, delayed, duration, delayOnError));
        // Use *async to execute on default "this" executor
        return thenCombineAsync(
            delayed, (r, d) -> r, enlistOrigin ? PromiseOrigin.ALL : PromiseOrigin.PARAM_ONLY
        );
    }

    // All orTimeout overloads delegate to these methods
    @Override
    public DependentPromise<T> orTimeout(Duration duration, boolean cancelOnTimeout) {
        return orTimeout(duration, cancelOnTimeout, defaultEnlistOrigin());
    }
    
    @Override
    public DependentPromise<T> orTimeout(Duration duration, boolean cancelOnTimeout, boolean enlistOrigin) {
        Promise<T> onTimeout = Timeouts.failAfter(duration);
        // Use *async to execute on default "this" executor
        DependentPromise<T> result = this
            .applyToEitherAsync(onTimeout, Function.identity(), enlistOrigin ? PromiseOrigin.ALL : PromiseOrigin.PARAM_ONLY);
        result.whenComplete(Timeouts.timeoutsCleanup(this, onTimeout, cancelOnTimeout));
        return result;
    }
    
    // All onTimeout overloads delegate to this method
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout) {
        return onTimeout(supplier, duration, cancelOnTimeout, defaultEnlistOrigin());
    }

    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout, boolean enlistOrigin) {
        Function<T, Supplier<? extends T>> valueToSupplier = v -> () -> v;
        
        // timeout converted to supplier
        Promise<Supplier<? extends T>> onTimeout = Timeouts
            .delay(duration)
            .dependent()
            .thenApply(d -> supplier, true);
        
        DependentPromise<T> result = this
            // resolved value converted to supplier
            .thenApply(valueToSupplier, enlistOrigin)
            // Use *async to execute on default "this" executor
            .applyToEitherAsync(onTimeout, Supplier::get,  PromiseOrigin.ALL);
        
        result.whenComplete(Timeouts.timeoutsCleanup(this, onTimeout, cancelOnTimeout));
        return result;
    }
    
    public <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.thenApply(fn), origin(enlistOrigin));
    }

    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.thenApplyAsync(fn), origin(enlistOrigin));
    }

    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenApplyAsync(fn, executor), origin(enlistOrigin));
    }    
    
    public DependentPromise<Void> thenAccept(Consumer<? super T> action, boolean enlistOrigin) {
        return wrap(delegate.thenAccept(action), origin(enlistOrigin));
    }

    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, boolean enlistOrigin) {
        return wrap(delegate.thenAcceptAsync(action), origin(enlistOrigin));
    }

    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenAcceptAsync(action, executor), origin(enlistOrigin));
    }    

    public DependentPromise<Void> thenRun(Runnable action, boolean enlistOrigin) {
        return wrap(delegate.thenRun(action), origin(enlistOrigin));
    }

    public DependentPromise<Void> thenRunAsync(Runnable action, boolean enlistOrigin) {
        return wrap(delegate.thenRunAsync(action), origin(enlistOrigin));
    }

    public DependentPromise<Void> thenRunAsync(Runnable action, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenRunAsync(action, executor), origin(enlistOrigin));
    }

    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, 
                                                  BiFunction<? super T, ? super U, ? extends V> fn,
                                                  Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.thenCombine(other, fn), originAndParam(other, enlistOptions));
    }

    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, 
                                                       BiFunction<? super T, ? super U, ? extends V> fn,
                                                       Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.thenCombineAsync(other, fn), originAndParam(other, enlistOptions));
    }

    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn, 
                                                       Executor executor,
                                                       Set<PromiseOrigin> enlistOptions) {
        
        return wrap(delegate.thenCombineAsync(other, fn, executor), originAndParam(other, enlistOptions));
    }
    
    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, 
                                                     BiConsumer<? super T, ? super U> action,
                                                     Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.thenAcceptBoth(other, action), originAndParam(other, enlistOptions));
    }

    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                          BiConsumer<? super T, ? super U> action,
                                                          Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.thenAcceptBothAsync(other, action), originAndParam(other, enlistOptions));
    }

    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                          BiConsumer<? super T, ? super U> action, 
                                                          Executor executor,
                                                          Set<PromiseOrigin> enlistOptions) {
        
        return wrap(delegate.thenAcceptBothAsync(other, action, executor), originAndParam(other, enlistOptions));
    }    
    
    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterBoth(other, action), originAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterBothAsync(other, action), originAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                                    Runnable action, 
                                                    Executor executor,
                                                    Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterBothAsync(other, action, executor), originAndParam(other, enlistOptions));
    }
    
    
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, 
                                                 Function<? super T, U> fn,
                                                 Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.applyToEither(other, fn), originAndParam(other, enlistOptions));
    }

    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.applyToEitherAsync(other, fn), originAndParam(other, enlistOptions));
    }

    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Executor executor,
                                                      Set<PromiseOrigin> enlistOptions) {
        
        return wrap(delegate.applyToEitherAsync(other, fn, executor), originAndParam(other, enlistOptions));
    }    

    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, 
                                               Consumer<? super T> action,
                                               Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.acceptEither(other, action), originAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.acceptEitherAsync(other, action), originAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Executor executor,
                                                    Set<PromiseOrigin> enlistOptions) {
        
        return wrap(delegate.acceptEitherAsync(other, action, executor), originAndParam(other, enlistOptions));
    }    

    public DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterEither(other, action), originAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterEitherAsync(other, action), originAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                                      Runnable action, 
                                                      Executor executor,
                                                      Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterEitherAsync(other, action, executor), originAndParam(other, enlistOptions));
    }
    
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return wrap(delegate.thenCompose(fn), origin(enlistOrigin));
    }

    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return wrap(delegate.thenComposeAsync(fn), origin(enlistOrigin));
    }

    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenComposeAsync(fn, executor), origin(enlistOrigin));
    }

    public DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn, boolean enlistOrigin) {
        return wrap(delegate.exceptionally(fn), origin(enlistOrigin));
    }
    
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return wrap(delegate.whenComplete(action), origin(enlistOrigin));
    }

    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return wrap(delegate.whenCompleteAsync(action), origin(enlistOrigin));
    }

    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.whenCompleteAsync(action, executor), origin(enlistOrigin));
    }

    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.handle(fn), origin(enlistOrigin));
    }

    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.handleAsync(fn), origin(enlistOrigin));
    }

    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.handleAsync(fn, executor), origin(enlistOrigin));
    }
    
    @Override
    public <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn) {
        return thenApply(fn, defaultEnlistOrigin());
    }

    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return thenApplyAsync(fn, defaultEnlistOrigin());
    }

    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return thenApplyAsync(fn, executor, defaultEnlistOrigin());
    }    
    
    @Override
    public DependentPromise<Void> thenAccept(Consumer<? super T> action) {
        return thenAccept(action, defaultEnlistOrigin());
    }

    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return thenAcceptAsync(action, defaultEnlistOrigin());
    }

    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return thenAcceptAsync(action, executor, defaultEnlistOrigin());
    }    

    @Override
    public DependentPromise<Void> thenRun(Runnable action) {
        return thenRun(action, defaultEnlistOrigin());
    }

    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action) {
        return thenRunAsync(action, defaultEnlistOrigin());
    }

    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action, Executor executor) {
        return thenRunAsync(action, executor, defaultEnlistOrigin());
    }

    @Override
    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombine(other, fn, defaultEnlistOptions);
    }

    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, fn, defaultEnlistOptions);
    }

    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn, 
                                                       Executor executor) {
        
        return thenCombineAsync(other, fn, executor, defaultEnlistOptions);
    }
    
    @Override
    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBoth(other, action, defaultEnlistOptions);
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBothAsync(other, action, defaultEnlistOptions);
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                          BiConsumer<? super T, ? super U> action,
                                                          Executor executor) {
        
        return thenAcceptBothAsync(other, action, executor, defaultEnlistOptions);
    }    
    
    @Override
    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return runAfterBoth(other, action, defaultEnlistOptions);
    }

    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action, defaultEnlistOptions);
    }

    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                                    Runnable action, 
                                                    Executor executor) {
        return runAfterBothAsync(other, action, executor, defaultEnlistOptions);
    }
    
    @Override
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEither(other, fn, defaultEnlistOptions);
    }

    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEitherAsync(other, fn, defaultEnlistOptions);
    }

    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Executor executor) {
        
        return applyToEitherAsync(other, fn, executor, defaultEnlistOptions);
    }    

    @Override
    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEither(other, action, defaultEnlistOptions);
    }

    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEitherAsync(other, action, defaultEnlistOptions);
    }

    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Executor executor) {
        
        return acceptEitherAsync(other, action, executor, defaultEnlistOptions);
    }    

    @Override
    public DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return runAfterEither(other, action, defaultEnlistOptions);
    }

    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action, defaultEnlistOptions);
    }

    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                                      Runnable action, 
                                                      Executor executor) {
        
        return runAfterEitherAsync(other, action, executor, defaultEnlistOptions);
    }
    
    @Override
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenCompose(fn, defaultEnlistOrigin());
    }

    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenComposeAsync(fn, defaultEnlistOrigin());
    }

    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return thenComposeAsync(fn, executor, defaultEnlistOrigin());
    }

    @Override
    public DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return exceptionally(fn, defaultEnlistOrigin());
    }
    
    @Override
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return whenComplete(action, defaultEnlistOrigin());
    }

    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return whenCompleteAsync(action, defaultEnlistOrigin());
    }

    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return whenCompleteAsync(action, executor, defaultEnlistOrigin());
    }

    @Override
    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handle(fn, defaultEnlistOrigin());
    }

    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handleAsync(fn, defaultEnlistOrigin());
    }

    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return handleAsync(fn, executor, defaultEnlistOrigin());
    }
    
    @Override
    public DependentPromise<T> dependent() {
        return dependent(PromiseOrigin.NONE);
    }
    
    public DependentPromise<T> dependent(Set<PromiseOrigin> defaultEnlistOptions) {
        if (null == defaultEnlistOptions) {
            defaultEnlistOptions = PromiseOrigin.NONE;
        }
        
        if (identicalSets(defaultEnlistOptions, this.defaultEnlistOptions)) {
            return this;
        } else {
            return ConfigurableDependentPromise.from(
                null == cancellableOrigins || cancellableOrigins.length == 0 ? delegate : cancellablePromiseOf(delegate), 
                defaultEnlistOptions
            );
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (delegate.cancel(mayInterruptIfRunning)) {
            cancelPromises(cancellableOrigins, mayInterruptIfRunning);
            return true;
        } else {
            return false;
        }
    }
    
    @Override
    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    @Override
    public boolean isDone() {
        return delegate.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return delegate.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.get(timeout, unit);
    }

    @Override
    public T getNow(T valueIfAbsent) throws CancellationException, CompletionException {
        return delegate.getNow(valueIfAbsent);
    }
    
    @Override
    public T getNow(Supplier<? extends T> valueIfAbsent) throws CancellationException, CompletionException {
        return delegate.getNow(valueIfAbsent);
    }
    
    @Override
    public T join() throws CancellationException, CompletionException {
        return delegate.join();
    }

    @Override
    public Promise<T> raw() {
        if (null == cancellableOrigins || cancellableOrigins.length == 0) {
            // No state collected, may optimize away own reference
            return delegate.raw();
        } else {
            return cancellablePromiseOf(delegate.raw());
        }
    }
    
    protected Promise<T> cancellablePromiseOf(Promise<T> original) {
        return new UndecoratedCancellationPromise<>(original, cancellableOrigins);
    }

    @Override
    public CompletableFuture<T> toCompletableFuture() {
        return toCompletableFuture(defaultEnlistOrigin());
    }
    
    public CompletableFuture<T> toCompletableFuture(boolean enlistOrigin) {
        if (!enlistOrigin) {
            return delegate.toCompletableFuture();
        } else {
            FutureCompletion<T> result = new FutureCompletion<T>().dependsOn(this);
            whenComplete((r, e) -> {
               if (null != e) {
                   result.completeExceptionally(e);
               } else {
                   result.complete(r);
               }
            });
            return result;
        }
    }
    
    private CompletionStage<?>[] origin(boolean enlist) {
        if (enlist) {
            CompletionStage<?>[] result = new CompletionStage<?>[1];
            result[0] = this;
            return result;
        } else {
            return null;
        }
    }
    
    private CompletionStage<?>[] originAndParam(CompletionStage<?> param, Set<PromiseOrigin> enlistOptions) {
        final CompletionStage<?>[] result = new CompletionStage<?>[enlistOptions.size()];
        int idx = 0;
        if (enlistOptions.contains(PromiseOrigin.THIS)) {
            result[idx++] = this;
        }
        if (enlistOptions.contains(PromiseOrigin.PARAM) && param != null) {
            result[idx++] = param;
        }
        return result;
    }

    private boolean defaultEnlistOrigin() {
        return defaultEnlistOptions.contains(PromiseOrigin.THIS);
    }

    static void cancelPromises(CompletionStage<?>[] promises, boolean mayInterruptIfRunning) {
        if (null != promises) {
            Arrays.stream(promises)
              .filter(p -> p != null)
              .forEach(p -> cancelPromise(p, mayInterruptIfRunning));
        }
    }
    
    private static boolean identicalSets(Set<?> a, Set<?> b) {
        return a.containsAll(b) && b.containsAll(a);
    }
    
    
    static class UndecoratedCancellationPromise<T> extends AbstractPromiseDecorator<T, Promise<T>> {
        private final CompletionStage<?>[] dependent;
        UndecoratedCancellationPromise(Promise<T> original, CompletionStage<?>[] dependent) {
            super(original);
            this.dependent = dependent;
        }
        
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (super.cancel(mayInterruptIfRunning)) {
                cancelPromises(dependent, mayInterruptIfRunning);
                return true;
            } else {
                return false;
            }
        }
        
        @Override
        public Promise<T> raw() {
            // May not unwrap further
            return this;
        }

        @Override
        protected <U> Promise<U> wrap(CompletionStage<U> original) {
            // No wrapping by definition
            return (Promise<U>)original;
        }
    }
}
