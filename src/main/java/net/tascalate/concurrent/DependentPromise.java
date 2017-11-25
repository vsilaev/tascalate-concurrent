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

import java.lang.reflect.Array;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
public class DependentPromise<T> implements Promise<T> {
    final private Promise<T> completionStage;
    final private CompletionStage<?>[] cancellableOrigins;
    
    protected DependentPromise(Promise<T> delegate, CompletionStage<?>[] cancellableOrigins) {
        this.completionStage = delegate;
        this.cancellableOrigins = cancellableOrigins; 
    }
    
    public static <U> DependentPromise<U> from(Promise<U> source) {
        if (source instanceof DependentPromise) {
            return (DependentPromise<U>)source;
        } else {
            return doWrap(source, null);
        }
    }
    
    protected void cancelOrigins(boolean mayInterruptIfRunning) {
        if (null != cancellableOrigins) {
            Arrays.stream(cancellableOrigins).filter(p -> p != null).forEach(p -> cancelPromise(p, mayInterruptIfRunning));
        }
    }
    
    protected boolean cancelPromise(CompletionStage<?> promise, boolean mayInterruptIfRunning) {
        return CompletablePromise.cancelPromise(promise, mayInterruptIfRunning);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (completionStage.cancel(mayInterruptIfRunning)) {
            cancelOrigins(mayInterruptIfRunning);
            return true;
        } else {
            return false;
        }
    }
    
    @Override
    public boolean isCancelled() {
        return completionStage.isCancelled();
    }

    @Override
    public boolean isDone() {
        return completionStage.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return completionStage.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return completionStage.get(timeout, unit);
    }

    @Override
    public T getNow(T valueIfAbsent) {
        return completionStage.getNow(valueIfAbsent);
    }
    
    public T getNow(Supplier<? extends T> valueIfAbsent) {
        return completionStage.getNow(valueIfAbsent);
    }

    protected <U> DependentPromise<U> wrap(Promise<U> original, CompletionStage<?>[] cancellableOrigins) {
        return doWrap(original, cancellableOrigins);
    }
    
    private static <U> DependentPromise<U> doWrap(Promise<U> original, CompletionStage<?>[] cancellableOrigins) {
        final DependentPromise<U> result = new DependentPromise<>(original, cancellableOrigins);
        if (result.isCancelled()) {
            // Wrapped over already cancelled Promise
            // So result.cancel() has no effect
            // and we have to cancel origins explicitly
            // right after construction
            result.cancelOrigins(true);
        }
        return result;
    }

    
    public DependentPromise<T> orTimeout(long timeout, TimeUnit unit) {
        return orTimeout(timeout, unit, true);
    }
    
    public DependentPromise<T> orTimeout(long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return orTimeout(timeout, unit, cancelOnTimeout, false);
    }

    public DependentPromise<T> orTimeout(long timeout, TimeUnit unit, boolean cancelOnTimeout, boolean enlistOrigin) {
        return orTimeout(Promises.toDuration(timeout, unit), cancelOnTimeout, enlistOrigin);
    }
    
    public DependentPromise<T> orTimeout(Duration duration) {
        return orTimeout(duration, true);
    }
    
    public DependentPromise<T> orTimeout(Duration duration, boolean cancelOnTimeout) {
        return orTimeout(duration, cancelOnTimeout, false);
    }
    
    public DependentPromise<T> orTimeout(Duration duration, boolean cancelOnTimeout, boolean enlistOrigin) {
        Promise<T> onTimeout = Promises.failAfter(duration);
        // Use *async to execute on default "this" executor
        DependentPromise<T> result = this
            .applyToEitherAsync(onTimeout, Function.identity(), enlistOrigin ? PromiseOrigin.ALL : PromiseOrigin.PARAM_ONLY);
        result.whenComplete(Promises.timeoutsCleanup(this, onTimeout, cancelOnTimeout));
        return result;
    }
    
    public DependentPromise<T> onTimeout(T value, long timeout, TimeUnit unit) {
        return onTimeout(value, timeout, unit, true);
    }
    
    public DependentPromise<T> onTimeout(T value, long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return onTimeout(value, timeout, unit, cancelOnTimeout, false);
    }
    
    public DependentPromise<T> onTimeout(T value, long timeout, TimeUnit unit, boolean cancelOnTimeout, boolean enlistOrigin) {
        return onTimeout(value, Promises.toDuration(timeout, unit), cancelOnTimeout, enlistOrigin);
    }
    
    public DependentPromise<T> onTimeout(T value, Duration duration) {
        return onTimeout(value, duration, true);
    }
    
    public DependentPromise<T> onTimeout(T value, Duration duration, boolean cancelOnTimeout) {
        return onTimeout(value, duration, cancelOnTimeout, false);
    }

    public DependentPromise<T> onTimeout(T value, Duration duration, boolean cancelOnTimeout, boolean enlistOrigin) {
        return onTimeout(() -> value, duration, enlistOrigin); 
    }
    
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit) {
        return onTimeout(supplier, timeout, unit, true);
    }
    
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return onTimeout(supplier, Promises.toDuration(timeout, unit), false);
    }
    
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit, boolean cancelOnTimeout, boolean enlistOrigin) {
        return onTimeout(supplier, Promises.toDuration(timeout, unit), enlistOrigin);
    }
    
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration) {
        return onTimeout(supplier, duration, true);
    }
    
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout) {
        return onTimeout(supplier, duration, cancelOnTimeout, false);
    }

    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout, boolean enlistOrigin) {
        Function<T, Supplier<? extends T>> valueToSupplier = v -> () -> v;
        
        // timeout converted to supplier
        Promise<Supplier<? extends T>> onTimeout = Promises
            .delay(duration)
            .dependent()
            .thenApply(d -> supplier, true);
        
        DependentPromise<T> result = this
            // resolved value converted to supplier
            .thenApply(valueToSupplier, enlistOrigin)
            // Use *async to execute on default "this" executor
            .applyToEitherAsync(onTimeout, Supplier::get,  PromiseOrigin.ALL);
        
        result.whenComplete(Promises.timeoutsCleanup(this, onTimeout, cancelOnTimeout));
        return result;
    }
    
    @Override
    public DependentPromise<T> dependent() {
    	return this;
    }
    
    public <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn) {
        return thenApply(fn, false);
    }

    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return thenApplyAsync(fn, false);
    }

    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return thenApplyAsync(fn, executor, false);
    }

    public <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return wrap(completionStage.thenApply(fn), self(enlistOrigin));
    }

    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return wrap(completionStage.thenApplyAsync(fn), self(enlistOrigin));
    }

    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor, boolean enlistOrigin) {
        return wrap(completionStage.thenApplyAsync(fn, executor), self(enlistOrigin));
    }    
    
    public DependentPromise<Void> thenAccept(Consumer<? super T> action) {
        return thenAccept(action, false);
    }

    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return thenAcceptAsync(action, false);
    }

    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return thenAcceptAsync(action, executor, false);
    }
    
    public DependentPromise<Void> thenAccept(Consumer<? super T> action, boolean enlistOrigin) {
        return wrap(completionStage.thenAccept(action), self(enlistOrigin));
    }

    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, boolean enlistOrigin) {
        return wrap(completionStage.thenAcceptAsync(action), self(enlistOrigin));
    }

    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor, boolean enlistOrigin) {
        return wrap(completionStage.thenAcceptAsync(action, executor), self(enlistOrigin));
    }    

    public DependentPromise<Void> thenRun(Runnable action) {
        return thenRun(action, false);
    }

    public DependentPromise<Void> thenRunAsync(Runnable action) {
        return thenRunAsync(action, false);
    }

    public DependentPromise<Void> thenRunAsync(Runnable action, Executor executor) {
        return thenRunAsync(action, executor, false);
    }
    
    public DependentPromise<Void> thenRun(Runnable action, boolean enlistOrigin) {
        return wrap(completionStage.thenRun(action), self(enlistOrigin));
    }

    public DependentPromise<Void> thenRunAsync(Runnable action, boolean enlistOrigin) {
        return wrap(completionStage.thenRunAsync(action), self(enlistOrigin));
    }

    public DependentPromise<Void> thenRunAsync(Runnable action, Executor executor, boolean enlistOrigin) {
        return wrap(completionStage.thenRunAsync(action, executor), self(enlistOrigin));
    }

    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombine(other, fn, PromiseOrigin.NONE);
    }

    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, fn, PromiseOrigin.NONE);
    }

    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn, 
                                                       Executor executor) {
        
        return thenCombineAsync(other, fn, executor, PromiseOrigin.NONE);
    }
    
    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, 
                                                  BiFunction<? super T, ? super U, ? extends V> fn,
                                                  Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.thenCombine(other, fn), selfAndParam(other, enlistOptions));
    }

    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, 
                                                       BiFunction<? super T, ? super U, ? extends V> fn,
                                                       Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.thenCombineAsync(other, fn), selfAndParam(other, enlistOptions));
    }

    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn, 
                                                       Executor executor,
                                                       Set<PromiseOrigin> enlistOptions) {
        
        return wrap(completionStage.thenCombineAsync(other, fn, executor), selfAndParam(other, enlistOptions));
    }
    

    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBoth(other, action, PromiseOrigin.NONE);
    }

    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBothAsync(other, action, PromiseOrigin.NONE);
    }

    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                          BiConsumer<? super T, ? super U> action, 
                                                          Executor executor) {
        
        return thenAcceptBothAsync(other, action, executor, PromiseOrigin.NONE);
    }

    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, 
                                                     BiConsumer<? super T, ? super U> action,
                                                     Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.thenAcceptBoth(other, action), selfAndParam(other, enlistOptions));
    }

    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                          BiConsumer<? super T, ? super U> action,
                                                          Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.thenAcceptBothAsync(other, action), selfAndParam(other, enlistOptions));
    }

    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                          BiConsumer<? super T, ? super U> action, 
                                                          Executor executor,
                                                          Set<PromiseOrigin> enlistOptions) {
        
        return wrap(completionStage.thenAcceptBothAsync(other, action, executor), selfAndParam(other, enlistOptions));
    }    
    
    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return runAfterBoth(other, action, PromiseOrigin.NONE);
    }

    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action, PromiseOrigin.NONE);
    }

    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                                    Runnable action, 
                                                    Executor executor) {
        return runAfterBothAsync(other, action, executor, PromiseOrigin.NONE);
    }

    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.runAfterBoth(other, action), selfAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.runAfterBothAsync(other, action), selfAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                                    Runnable action, 
                                                    Executor executor,
                                                    Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.runAfterBothAsync(other, action, executor), selfAndParam(other, enlistOptions));
    }
    
    
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEither(other, fn, PromiseOrigin.NONE);
    }

    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEitherAsync(other, fn, PromiseOrigin.NONE);
    }

    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Executor executor) {
        
        return applyToEitherAsync(other, fn, executor, PromiseOrigin.NONE);
    }
    
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, 
                                                 Function<? super T, U> fn,
                                                 Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.applyToEither(other, fn), selfAndParam(other, enlistOptions));
    }

    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.applyToEitherAsync(other, fn), selfAndParam(other, enlistOptions));
    }

    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Executor executor,
                                                      Set<PromiseOrigin> enlistOptions) {
        
        return wrap(completionStage.applyToEitherAsync(other, fn, executor), selfAndParam(other, enlistOptions));
    }    

    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEither(other, action, PromiseOrigin.NONE);
    }

    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEitherAsync(other, action, PromiseOrigin.NONE);
    }

    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Executor executor) {
        
        return acceptEitherAsync(other, action, executor, PromiseOrigin.NONE);
    }

    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, 
                                               Consumer<? super T> action,
                                               Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.acceptEither(other, action), selfAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.acceptEitherAsync(other, action), selfAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Executor executor,
                                                    Set<PromiseOrigin> enlistOptions) {
        
        return wrap(completionStage.acceptEitherAsync(other, action, executor), selfAndParam(other, enlistOptions));
    }    
    
    public DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return runAfterEither(other, action, PromiseOrigin.NONE);
    }

    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action, PromiseOrigin.NONE);
    }

    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                                      Runnable action, 
                                                      Executor executor) {
        return runAfterEitherAsync(other, action, executor, PromiseOrigin.NONE);
    }

    public DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.runAfterEither(other, action), selfAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.runAfterEitherAsync(other, action), selfAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                                      Runnable action, 
                                                      Executor executor,
                                                      Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.runAfterEitherAsync(other, action, executor), selfAndParam(other, enlistOptions));
    }
    
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenCompose(fn, false);
    }

    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenComposeAsync(fn, false);
    }

    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return thenComposeAsync(fn, executor, false);
    }
    
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return wrap(completionStage.thenCompose(fn), self(enlistOrigin));
    }

    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return wrap(completionStage.thenComposeAsync(fn), self(enlistOrigin));
    }

    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor, boolean enlistOrigin) {
        return wrap(completionStage.thenComposeAsync(fn, executor), self(enlistOrigin));
    }

    public DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return exceptionally(fn, false);
    }

    public DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn, boolean enlistOrigin) {
        return wrap(completionStage.exceptionally(fn), self(enlistOrigin));
    }
    
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return whenComplete(action, false);
    }

    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return whenCompleteAsync(action, false);
    }

    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return whenCompleteAsync(action, executor, false);
    }
    
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return wrap(completionStage.whenComplete(action), self(enlistOrigin));
    }

    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return wrap(completionStage.whenCompleteAsync(action), self(enlistOrigin));
    }

    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor, boolean enlistOrigin) {
        return wrap(completionStage.whenCompleteAsync(action, executor), self(enlistOrigin));
    }

    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handle(fn, false);
    }

    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handleAsync(fn, false);
    }

    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return handleAsync(fn, executor, false);
    }
    
    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return wrap(completionStage.handle(fn), self(enlistOrigin));
    }

    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return wrap(completionStage.handleAsync(fn), self(enlistOrigin));
    }

    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor, boolean enlistOrigin) {
        return wrap(completionStage.handleAsync(fn, executor), self(enlistOrigin));
    }


    public CompletableFuture<T> toCompletableFuture() {
        return completionStage.toCompletableFuture();
    }
    
    private DependentPromise<T>[] self(boolean enlist) {
        if (enlist) {
            @SuppressWarnings("unchecked")
            DependentPromise<T>[] result = (DependentPromise<T>[])Array.newInstance(DependentPromise.class, 1);
            result[0] = this;
            return result;
        } else {
            return null;
        }
    }
    
    private CompletionStage<?>[] selfAndParam(CompletionStage<?> param, Set<PromiseOrigin> enlistOptions) {
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
    
}
