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
class ExplicitDependentPromise<T> implements DependentPromise<T> {
    final protected Promise<T> delegate;
    final protected CompletionStage<?>[] cancellableOrigins;
    
    protected ExplicitDependentPromise(Promise<T> delegate, CompletionStage<?>[] cancellableOrigins) {
        this.delegate = delegate;
        this.cancellableOrigins = cancellableOrigins;
    }
    
    static <U> DependentPromise<U> from(Promise<U> source) {
        return doWrap(source, null);
    }
    
    protected <U> DependentPromise<U> wrap(Promise<U> original, CompletionStage<?>[] cancellableOrigins) {
        return doWrap(original, cancellableOrigins);
    }
    
    static <U> DependentPromise<U> doWrap(Promise<U> original, CompletionStage<?>[] cancellableOrigins) {
        if (null == cancellableOrigins || cancellableOrigins.length == 0) {
            if (original instanceof DependentPromise) {
                return (DependentPromise<U>)original;
            }
        }
        final ExplicitDependentPromise<U> result = new ExplicitDependentPromise<>(original, cancellableOrigins);
        result.checkCanceledOnCreation();
        return result;
    }

    public DependentPromise<T> delay(Duration duration, boolean delayOnError, boolean enlistOrigin) {
        CompletablePromise<T> delayed = new CompletablePromise<>();
        whenComplete(Timeouts.configureDelay(this, delayed, duration, delayOnError));
        // Use *async to execute on default "this" executor
        return thenCombineAsync(
            delayed, (r, d) -> r, enlistOrigin ? PromiseOrigin.ALL : PromiseOrigin.PARAM_ONLY
        );
    }
    
    public DependentPromise<T> orTimeout(Duration duration, boolean cancelOnTimeout, boolean enlistOrigin) {
        Promise<T> onTimeout = Timeouts.failAfter(duration);
        // Use *async to execute on default "this" executor
        DependentPromise<T> result = this
            .applyToEitherAsync(onTimeout, Function.identity(), enlistOrigin ? PromiseOrigin.ALL : PromiseOrigin.PARAM_ONLY);
        result.whenComplete(Timeouts.timeoutsCleanup(this, onTimeout, cancelOnTimeout));
        return result;
    }

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
        return wrap(delegate.thenApply(fn), self(enlistOrigin));
    }

    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.thenApplyAsync(fn), self(enlistOrigin));
    }

    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenApplyAsync(fn, executor), self(enlistOrigin));
    }    
    
    public DependentPromise<Void> thenAccept(Consumer<? super T> action, boolean enlistOrigin) {
        return wrap(delegate.thenAccept(action), self(enlistOrigin));
    }

    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, boolean enlistOrigin) {
        return wrap(delegate.thenAcceptAsync(action), self(enlistOrigin));
    }

    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenAcceptAsync(action, executor), self(enlistOrigin));
    }    

    public DependentPromise<Void> thenRun(Runnable action, boolean enlistOrigin) {
        return wrap(delegate.thenRun(action), self(enlistOrigin));
    }

    public DependentPromise<Void> thenRunAsync(Runnable action, boolean enlistOrigin) {
        return wrap(delegate.thenRunAsync(action), self(enlistOrigin));
    }

    public DependentPromise<Void> thenRunAsync(Runnable action, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenRunAsync(action, executor), self(enlistOrigin));
    }

    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, 
                                                  BiFunction<? super T, ? super U, ? extends V> fn,
                                                  Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.thenCombine(other, fn), selfAndParam(other, enlistOptions));
    }

    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, 
                                                       BiFunction<? super T, ? super U, ? extends V> fn,
                                                       Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.thenCombineAsync(other, fn), selfAndParam(other, enlistOptions));
    }

    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn, 
                                                       Executor executor,
                                                       Set<PromiseOrigin> enlistOptions) {
        
        return wrap(delegate.thenCombineAsync(other, fn, executor), selfAndParam(other, enlistOptions));
    }
    
    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, 
                                                     BiConsumer<? super T, ? super U> action,
                                                     Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.thenAcceptBoth(other, action), selfAndParam(other, enlistOptions));
    }

    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                          BiConsumer<? super T, ? super U> action,
                                                          Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.thenAcceptBothAsync(other, action), selfAndParam(other, enlistOptions));
    }

    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                          BiConsumer<? super T, ? super U> action, 
                                                          Executor executor,
                                                          Set<PromiseOrigin> enlistOptions) {
        
        return wrap(delegate.thenAcceptBothAsync(other, action, executor), selfAndParam(other, enlistOptions));
    }    
    
    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterBoth(other, action), selfAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterBothAsync(other, action), selfAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                                    Runnable action, 
                                                    Executor executor,
                                                    Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterBothAsync(other, action, executor), selfAndParam(other, enlistOptions));
    }
    
    
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, 
                                                 Function<? super T, U> fn,
                                                 Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.applyToEither(other, fn), selfAndParam(other, enlistOptions));
    }

    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.applyToEitherAsync(other, fn), selfAndParam(other, enlistOptions));
    }

    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Executor executor,
                                                      Set<PromiseOrigin> enlistOptions) {
        
        return wrap(delegate.applyToEitherAsync(other, fn, executor), selfAndParam(other, enlistOptions));
    }    

    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, 
                                               Consumer<? super T> action,
                                               Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.acceptEither(other, action), selfAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.acceptEitherAsync(other, action), selfAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Executor executor,
                                                    Set<PromiseOrigin> enlistOptions) {
        
        return wrap(delegate.acceptEitherAsync(other, action, executor), selfAndParam(other, enlistOptions));
    }    

    public DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterEither(other, action), selfAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterEitherAsync(other, action), selfAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                                      Runnable action, 
                                                      Executor executor,
                                                      Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterEitherAsync(other, action, executor), selfAndParam(other, enlistOptions));
    }
    
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return wrap(delegate.thenCompose(fn), self(enlistOrigin));
    }

    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return wrap(delegate.thenComposeAsync(fn), self(enlistOrigin));
    }

    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenComposeAsync(fn, executor), self(enlistOrigin));
    }

    public DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn, boolean enlistOrigin) {
        return wrap(delegate.exceptionally(fn), self(enlistOrigin));
    }
    
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return wrap(delegate.whenComplete(action), self(enlistOrigin));
    }

    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return wrap(delegate.whenCompleteAsync(action), self(enlistOrigin));
    }

    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.whenCompleteAsync(action, executor), self(enlistOrigin));
    }

    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.handle(fn), self(enlistOrigin));
    }

    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.handleAsync(fn), self(enlistOrigin));
    }

    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.handleAsync(fn, executor), self(enlistOrigin));
    }
    
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (delegate.cancel(mayInterruptIfRunning)) {
            cancelOrigins(mayInterruptIfRunning);
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
    public T getNow(T valueIfAbsent) {
        return delegate.getNow(valueIfAbsent);
    }
    
    public T getNow(Supplier<? extends T> valueIfAbsent) {
        return delegate.getNow(valueIfAbsent);
    }

    @Override
    public Promise<T> raw() {
        if (null == cancellableOrigins || cancellableOrigins.length == 0) {
            // No state collected, may optimize away own reference
            return delegate.raw();
        } else {
            final CompletionStage<?>[] dependent = cancellableOrigins;
            return new AbstractPromiseDecorator<T, Promise<T>>(delegate.raw()) {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    if (super.cancel(mayInterruptIfRunning)) {
                        if (null != dependent) {
                            Arrays.stream(dependent).filter(p -> p != null).forEach(p -> cancelPromise(p, mayInterruptIfRunning));
                        }
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
                    return (Promise<U>)original;
                }
            };
        }
    }

    public CompletableFuture<T> toCompletableFuture(boolean enlistOrigin) {
        if (!enlistOrigin) {
            return delegate.toCompletableFuture();
        } else {
            CompletableFuture<T> result = new CompletableFuture<T>() {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    if (super.cancel(mayInterruptIfRunning)) {
                        ExplicitDependentPromise.this.cancel(mayInterruptIfRunning);
                        return true;
                    } else {
                        return false;
                    }
                }
            };
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

    protected void cancelOrigins(boolean mayInterruptIfRunning) {
        if (null != cancellableOrigins) {
            Arrays.stream(cancellableOrigins).filter(p -> p != null).forEach(p -> cancelPromise(p, mayInterruptIfRunning));
        }
    }
    
    protected boolean cancelPromise(CompletionStage<?> promise, boolean mayInterruptIfRunning) {
        return CompletablePromise.cancelPromise(promise, mayInterruptIfRunning);
    }
    
    protected void checkCanceledOnCreation() {
        if (isCancelled()) {
            // Wrapped over already cancelled Promise
            // So result.cancel() has no effect
            // and we have to cancel origins explicitly
            // right after construction
            cancelOrigins(true);
        }
    }
    
    
    private CompletionStage<?>[] self(boolean enlist) {
        if (enlist) {
            CompletionStage<?>[] result = new CompletionStage<?>[1];
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
