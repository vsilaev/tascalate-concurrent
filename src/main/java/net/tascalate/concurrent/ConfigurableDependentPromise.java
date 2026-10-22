/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
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

import static net.tascalate.concurrent.AsyncInternals.cancelPromise;
import static net.tascalate.concurrent.AsyncInternals.iif;

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
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.tascalate.concurrent.core.CompletionStageAPI;
import net.tascalate.concurrent.core.Decorator;

/**
 * <p>{@link Promise} wrapper that may keep track of the origins of this promise and cancel them
 * along with this promise itself.
 * 
 * <p>For example:
 * <pre>
 * <code>
 * DependentPromise&lt;?&gt; p1 = DependentPromise.from(CompletableTask.runAsync(this::someLongRunningMethod, myExecutor));
 * DependentPromise&lt;?&gt; p2 = p1.thenRunAsync(this::someOtherLongRunningTask, true);
 * ...
 * p2.cancel(true);
 * </code>
 * </pre>
 * 
 * <p>In the example <code>p2</code> is created by specifying <code>p1</code> as an origin (the last argument is <code>true</code>).
 * Now, when cancelling <code>p2</code>, both <code>p2</code> and <code>p1</code> will be cancelled if not yet completed.
 * 
 * <p>The interface adds overloads to all composition methods declared in {@link CompletionStage} and {@link Promise}.
 * 
 * <p>The methods that accept another {@link CompletionStage} as an argument (those with names containing 
 * <code>Both</code> and <code>Either</code>) are overloaded with a set of {@link Promise.Origin} as an argument to let
 * you specify what to enlist as an origin: "this" (the promise on which the method is called) or the parameter.
 * 
 * <p>The rest of the methods from the {@link CompletionStage} and {@link Promise} APIs are overloaded with a boolean argument 
 * <code>enlistOrigin</code> that specifies whether or not the {@link Promise} object whose
 * method is invoked should be added as an origin to the result.
 * 
 * <p>All methods originally specified in {@link CompletionStage} delegate to their overloaded counterparts 
 * using the {@code defaultEnlistOptions} configured when this promise was created (via {@link Promise#dependent(Set)} 
 * or {@link #from(Promise, Set)}). Depending on these options, the standard methods may automatically enlist 
 * "this" promise, the parameter stage, or both for cancellation. If the options are {@link Promise.Origin#NONE} 
 * (the default), no stages are implicitly enlisted.
 *
 * @author vsilaev
 * @param <T> the type of the successfully resolved promise value
 */
public class ConfigurableDependentPromise<T> implements DependentPromise<T>, Decorator<T> {

    /** The underlying promise being decorated. */
    protected final Promise<T> delegate;

    /** 
     * The array of upstream stages that should be cancelled when this promise is cancelled.
     * May be {@code null} or empty if no origins are enlisted.
     */
    protected final CompletionStage<?>[] cancellableOrigins;

    /** 
     * The default set of origins to enlist when composition methods are called without 
     * explicit enlistment parameters.
     */
    protected final Set<Promise.Origin> defaultEnlistOptions;

    /**
     * Creates a new configurable dependent promise.
     *
     * @param delegate             the underlying promise to decorate
     * @param defaultEnlistOptions the default origins to enlist for cancellation; 
     *                             if {@code null} or empty, defaults to {@link Promise.Origin#NONE}
     * @param cancellableOrigins   the specific upstream stages to cancel when this promise is cancelled;
     *                             may be {@code null} or empty
     */
    protected ConfigurableDependentPromise(Promise<T> delegate, 
                                           Set<Promise.Origin> defaultEnlistOptions, 
                                           CompletionStage<?>[] cancellableOrigins) {
        this.delegate = delegate;
        this.defaultEnlistOptions = defaultEnlistOptions == null || defaultEnlistOptions.isEmpty() ? 
            Promise.Origin.NONE : defaultEnlistOptions;
        this.cancellableOrigins = cancellableOrigins;
    }

    /**
     * Registers the cancellation hook on the delegate. If there are any 
     * {@code cancellableOrigins}, an {@code onCancel} callback is attached to the 
     * delegate that will cancel those origins when the delegate is cancelled.
     *
     * @return this instance, for fluent chaining
     */
    protected ConfigurableDependentPromise<T> setup() {
        if (!isEmptyArray(cancellableOrigins)) {
            delegate.onCancel(() -> cancelPromises(cancellableOrigins, true));
        }
        return this;
    }

    /**
     * Wraps the given promise as a {@code DependentPromise} with no default enlistment.
     *
     * @param <U>    the promise result type
     * @param source the source promise
     * @return the wrapped dependent promise
     */
    public static <U> DependentPromise<U> from(Promise<U> source) {
        return from(source, Promise.Origin.NONE);
    }

    /**
     * Wraps the given promise as a {@code DependentPromise} with the specified default enlistment options.
     *
     * @param <U>                  the promise result type
     * @param source               the source promise
     * @param defaultEnlistOptions the default origins to enlist for cancellation
     * @return the wrapped dependent promise
     */
    public static <U> DependentPromise<U> from(Promise<U> source, Set<Promise.Origin> defaultEnlistOptions) {
        return doWrap(source, defaultEnlistOptions, null);
    }

    /**
     * Wraps the result of a composition operation into a new {@code DependentPromise},
     * inheriting the {@code defaultEnlistOptions} of this instance and attaching the 
     * specified {@code cancellableOrigins}.
     *
     * @param <U>                the result type of the new promise
     * @param original           the promise returned by the delegate's composition method
     * @param cancellableOrigins the upstream stages to enlist for cancellation
     * @return the newly wrapped dependent promise
     */
    protected <U> DependentPromise<U> wrap(Promise<U> original, CompletionStage<?>[] cancellableOrigins) {
        return doWrap(original, defaultEnlistOptions, cancellableOrigins);
    }

    /**
     * Internal factory that creates a new {@code ConfigurableDependentPromise} or reuses 
     * the existing one if it's already a {@code ConfigurableDependentPromise} with the 
     * same {@code defaultEnlistOptions} and no new origins to enlist.
     */
    private static <U> DependentPromise<U> doWrap(Promise<U> original, 
                                                  Set<Promise.Origin> defaultEnlistOptions, 
                                                  CompletionStage<?>[] cancellableOrigins) {
        if (isEmptyArray(cancellableOrigins)) {
            // Nothing to enlist additionally for this "original" instance
            if (original instanceof ConfigurableDependentPromise) {
                ConfigurableDependentPromise<U> ioriginal = (ConfigurableDependentPromise<U>)original;
                if (identicalSets(ioriginal.defaultEnlistOptions, defaultEnlistOptions)) {
                    // Same defaultEnlistOptions, may reuse 
                    return ioriginal;
                }
            }
        }
        
        return new ConfigurableDependentPromise<>(original, defaultEnlistOptions, cancellableOrigins).setup();
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> onCancel(Runnable action) {
        delegate.onCancel(action);
        return this;
    }

    // ========================================================================
    // Delay / Timeout Overrides
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> delay(Duration duration, boolean delayOnError) {
        return delay(duration, delayOnError, defaultEnlistOrigin());
    }

    /**
     * {@inheritDoc}
     * <p>
     * This overload explicitly specifies whether the current promise ({@code this}) 
     * should be enlisted for cancellation when the resulting promise is cancelled.
     * </p>
     */
    @Override
    public DependentPromise<T> delay(Duration duration, boolean delayOnError, boolean enlistOrigin) {
        return wrap(delegate.delay(duration, delayOnError), origin(enlistOrigin));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> orTimeout(Duration duration, boolean cancelOnTimeout) {
        return orTimeout(duration, cancelOnTimeout, defaultEnlistOrigin());
    }

    /**
     * {@inheritDoc}
     * <p>
     * This overload explicitly specifies whether the current promise ({@code this}) 
     * should be enlisted for cancellation when the resulting promise is cancelled.
     * </p>
     */
    @Override
    public DependentPromise<T> orTimeout(Duration duration, boolean cancelOnTimeout, boolean enlistOrigin) {
        return wrap(delegate.orTimeout(duration, cancelOnTimeout), origin(enlistOrigin));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> onTimeout(T value, Duration duration, boolean cancelOnTimeout) {
        return onTimeout(value, duration, cancelOnTimeout, defaultEnlistOrigin());
    }

    /**
     * {@inheritDoc}
     * <p>
     * This overload explicitly specifies whether the current promise ({@code this}) 
     * should be enlisted for cancellation when the resulting promise is cancelled.
     * </p>
     */
    @Override
    public DependentPromise<T> onTimeout(T value, Duration duration, boolean cancelOnTimeout, boolean enlistOrigin) {
        return wrap(delegate.onTimeout(value, duration, cancelOnTimeout), origin(enlistOrigin));
    }

    // All onTimeout overloads delegate to this method

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout) {
        return onTimeout(supplier, duration, cancelOnTimeout, defaultEnlistOrigin());
    }

    /**
     * {@inheritDoc}
     * <p>
     * This overload explicitly specifies whether the current promise ({@code this}) 
     * should be enlisted for cancellation when the resulting promise is cancelled.
     * </p>
     */
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout, boolean enlistOrigin) {
        return wrap(delegate.onTimeout(supplier, duration, cancelOnTimeout), origin(enlistOrigin));
    }

    // ========================================================================
    // Unary Composition Methods (with enlistOrigin flag)
    // ========================================================================
    /**
     * {@inheritDoc}
     * <p>
     * This overload explicitly specifies whether the current promise ({@code this}) 
     * should be enlisted for cancellation when the resulting promise is cancelled.
     * </p>
     */
    @Override
    public <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.thenApply(fn), origin(enlistOrigin));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.thenApplyAsync(fn), origin(enlistOrigin));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenApplyAsync(fn, executor), origin(enlistOrigin));
    }    

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenAccept(Consumer<? super T> action, boolean enlistOrigin) {
        return wrap(delegate.thenAccept(action), origin(enlistOrigin));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, boolean enlistOrigin) {
        return wrap(delegate.thenAcceptAsync(action), origin(enlistOrigin));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenAcceptAsync(action, executor), origin(enlistOrigin));
    }    

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenRun(Runnable action, boolean enlistOrigin) {
        return wrap(delegate.thenRun(action), origin(enlistOrigin));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action, boolean enlistOrigin) {
        return wrap(delegate.thenRunAsync(action), origin(enlistOrigin));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenRunAsync(action, executor), origin(enlistOrigin));
    }

    // ========================================================================
    // Binary Composition Methods (with enlistOptions set)
    // ========================================================================

    /**
     * {@inheritDoc}
     * <p>
     * This overload explicitly specifies which origins (the current promise, the parameter 
     * stage, both, or neither) should be enlisted for cancellation when the resulting 
     * promise is cancelled.
     * </p>
     */
    @Override
    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, 
                                                  BiFunction<? super T, ? super U, ? extends V> fn,
                                                  Set<Promise.Origin> enlistOptions) {
        return wrap(delegate.thenCombine(other, fn), originAndParam(other, enlistOptions));
    }

    /** {@inheritDoc} */
    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, 
                                                       BiFunction<? super T, ? super U, ? extends V> fn,
                                                       Set<Promise.Origin> enlistOptions) {
        return wrap(delegate.thenCombineAsync(other, fn), originAndParam(other, enlistOptions));
    }

    /** {@inheritDoc} */
    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn, 
                                                       Executor executor,
                                                       Set<Promise.Origin> enlistOptions) {
        
        return wrap(delegate.thenCombineAsync(other, fn, executor), originAndParam(other, enlistOptions));
    }
    

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, 
                                                     BiConsumer<? super T, ? super U> action,
                                                     Set<Promise.Origin> enlistOptions) {
        return wrap(delegate.thenAcceptBoth(other, action), originAndParam(other, enlistOptions));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                          BiConsumer<? super T, ? super U> action,
                                                          Set<Promise.Origin> enlistOptions) {
        return wrap(delegate.thenAcceptBothAsync(other, action), originAndParam(other, enlistOptions));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                          BiConsumer<? super T, ? super U> action, 
                                                          Executor executor,
                                                          Set<Promise.Origin> enlistOptions) {
        
        return wrap(delegate.thenAcceptBothAsync(other, action, executor), originAndParam(other, enlistOptions));
    }    
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action, Set<Promise.Origin> enlistOptions) {
        return wrap(delegate.runAfterBoth(other, action), originAndParam(other, enlistOptions));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Set<Promise.Origin> enlistOptions) {
        return wrap(delegate.runAfterBothAsync(other, action), originAndParam(other, enlistOptions));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                                    Runnable action, 
                                                    Executor executor,
                                                    Set<Promise.Origin> enlistOptions) {
        return wrap(delegate.runAfterBothAsync(other, action, executor), originAndParam(other, enlistOptions));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, 
                                                 Function<? super T, U> fn,
                                                 Set<Promise.Origin> enlistOptions) {
        return wrap(delegate.applyToEither(other, fn), originAndParam(other, enlistOptions));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Set<Promise.Origin> enlistOptions) {
        return wrap(delegate.applyToEitherAsync(other, fn), originAndParam(other, enlistOptions));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Executor executor,
                                                      Set<Promise.Origin> enlistOptions) {
        
        return wrap(delegate.applyToEitherAsync(other, fn, executor), originAndParam(other, enlistOptions));
    }    

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, 
                                               Consumer<? super T> action,
                                               Set<Promise.Origin> enlistOptions) {
        return wrap(delegate.acceptEither(other, action), originAndParam(other, enlistOptions));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Set<Promise.Origin> enlistOptions) {
        return wrap(delegate.acceptEitherAsync(other, action), originAndParam(other, enlistOptions));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Executor executor,
                                                    Set<Promise.Origin> enlistOptions) {
        
        return wrap(delegate.acceptEitherAsync(other, action, executor), originAndParam(other, enlistOptions));
    }    

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action, Set<Promise.Origin> enlistOptions) {
        return wrap(delegate.runAfterEither(other, action), originAndParam(other, enlistOptions));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Set<Promise.Origin> enlistOptions) {
        return wrap(delegate.runAfterEitherAsync(other, action), originAndParam(other, enlistOptions));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                                      Runnable action, 
                                                      Executor executor,
                                                      Set<Promise.Origin> enlistOptions) {
        return wrap(delegate.runAfterEitherAsync(other, action, executor), originAndParam(other, enlistOptions));
    }
    
    // ========================================================================
    // Composition / Error Handling (with enlistOrigin flag)
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return wrap(delegate.thenCompose(fn), origin(enlistOrigin));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return wrap(delegate.thenComposeAsync(fn), origin(enlistOrigin));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, 
                                                    Executor executor, 
                                                    boolean enlistOrigin) {
        return wrap(delegate.thenComposeAsync(fn, executor), origin(enlistOrigin));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn, boolean enlistOrigin) {
        return wrap(delegate.exceptionally(fn), origin(enlistOrigin));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, boolean enlistOrigin) {
        return wrap(delegate.exceptionallyAsync(fn), origin(enlistOrigin));
    }
    

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.exceptionallyAsync(fn, executor), origin(enlistOrigin));
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn, boolean enlistOrigin) {
        return wrap(delegate.exceptionallyCompose(fn), origin(enlistOrigin));
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, boolean enlistOrigin) {
        return wrap(delegate.exceptionallyComposeAsync(fn), origin(enlistOrigin));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, 
                                                         Executor executor, 
                                                         boolean enlistOrigin) {
        return wrap(delegate.exceptionallyComposeAsync(fn, executor), origin(enlistOrigin));
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate, boolean enlistOrigin) {
        return wrap(delegate.thenFilter(predicate), origin(enlistOrigin));
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, boolean enlistOrigin) {
        return wrap(delegate.thenFilter(predicate, errorSupplier), origin(enlistOrigin));
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, boolean enlistOrigin) {
        return wrap(delegate.thenFilterAsync(predicate), origin(enlistOrigin));
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, boolean enlistOrigin) {
        return wrap(delegate.thenFilterAsync(predicate, errorSupplier), origin(enlistOrigin)); 
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenFilterAsync(predicate, executor), origin(enlistOrigin));
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenFilterAsync(predicate, errorSupplier, executor), origin(enlistOrigin));
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return wrap(delegate.whenComplete(action), origin(enlistOrigin));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return wrap(delegate.whenCompleteAsync(action), origin(enlistOrigin));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, 
                                                 Executor executor, 
                                                 boolean enlistOrigin) {
        return wrap(delegate.whenCompleteAsync(action, executor), origin(enlistOrigin));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.handle(fn), origin(enlistOrigin));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.handleAsync(fn), origin(enlistOrigin));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, 
                                               Executor executor, 
                                               boolean enlistOrigin) {
        return wrap(delegate.handleAsync(fn, executor), origin(enlistOrigin));
    }
    
    // ========================================================================
    // Standard Composition Methods (Delegating to explicit enlistment overloads)
    // ========================================================================

    /**
     * {@inheritDoc}
     * <p>
     * Delegates to the explicit enlistment overload using the {@code defaultEnlistOptions} 
     * configured for this promise.
     * </p>
     */
    @Override
    public <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn) {
        return thenApply(fn, defaultEnlistOrigin());
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return thenApplyAsync(fn, defaultEnlistOrigin());
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return thenApplyAsync(fn, executor, defaultEnlistOrigin());
    }    
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenAccept(Consumer<? super T> action) {
        return thenAccept(action, defaultEnlistOrigin());
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return thenAcceptAsync(action, defaultEnlistOrigin());
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return thenAcceptAsync(action, executor, defaultEnlistOrigin());
    }    

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenRun(Runnable action) {
        return thenRun(action, defaultEnlistOrigin());
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action) {
        return thenRunAsync(action, defaultEnlistOrigin());
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action, Executor executor) {
        return thenRunAsync(action, executor, defaultEnlistOrigin());
    }

    /** {@inheritDoc} */
    @Override
    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, 
                                                  BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombine(other, fn, defaultEnlistOptions);
    }

    /** {@inheritDoc} */
    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, 
                                                       BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, fn, defaultEnlistOptions);
    }

    /** {@inheritDoc} */
    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn, 
                                                       Executor executor) {
        
        return thenCombineAsync(other, fn, executor, defaultEnlistOptions);
    }
    
    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, 
                                                     BiConsumer<? super T, ? super U> action) {
        return thenAcceptBoth(other, action, defaultEnlistOptions);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                          BiConsumer<? super T, ? super U> action) {
        return thenAcceptBothAsync(other, action, defaultEnlistOptions);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                          BiConsumer<? super T, ? super U> action,
                                                          Executor executor) {
        
        return thenAcceptBothAsync(other, action, executor, defaultEnlistOptions);
    }    
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return runAfterBoth(other, action, defaultEnlistOptions);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action, defaultEnlistOptions);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                                    Runnable action, 
                                                    Executor executor) {
        return runAfterBothAsync(other, action, executor, defaultEnlistOptions);
    }
    
    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEither(other, fn, defaultEnlistOptions);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEitherAsync(other, fn, defaultEnlistOptions);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Executor executor) {
        
        return applyToEitherAsync(other, fn, executor, defaultEnlistOptions);
    }    

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEither(other, action, defaultEnlistOptions);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEitherAsync(other, action, defaultEnlistOptions);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Executor executor) {
        
        return acceptEitherAsync(other, action, executor, defaultEnlistOptions);
    }    

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return runAfterEither(other, action, defaultEnlistOptions);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action, defaultEnlistOptions);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                                      Runnable action, 
                                                      Executor executor) {
        
        return runAfterEitherAsync(other, action, executor, defaultEnlistOptions);
    }
    
    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenCompose(fn, defaultEnlistOrigin());
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenComposeAsync(fn, defaultEnlistOrigin());
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return thenComposeAsync(fn, executor, defaultEnlistOrigin());
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return exceptionally(fn, defaultEnlistOrigin());
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return exceptionallyAsync(fn, defaultEnlistOrigin());
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return exceptionallyCompose(fn, defaultEnlistOrigin());
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return exceptionallyComposeAsync(fn, defaultEnlistOrigin());
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        return exceptionallyComposeAsync(fn, executor, defaultEnlistOrigin());
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        return exceptionallyAsync(fn, executor, defaultEnlistOrigin());
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate) {
        return thenFilter(predicate, defaultEnlistOrigin());
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier) {
        return thenFilter(predicate, errorSupplier, defaultEnlistOrigin());
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate) {
        return thenFilterAsync(predicate, defaultEnlistOrigin());
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier) {
        return thenFilterAsync(predicate, errorSupplier, defaultEnlistOrigin());
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor) {
        return thenFilterAsync(predicate, executor, defaultEnlistOrigin());
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, Executor executor) {
        return thenFilterAsync(predicate, errorSupplier, executor, defaultEnlistOrigin());
    }
    
    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return whenComplete(action, defaultEnlistOrigin());
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return whenCompleteAsync(action, defaultEnlistOrigin());
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return whenCompleteAsync(action, executor, defaultEnlistOrigin());
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handle(fn, defaultEnlistOrigin());
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handleAsync(fn, defaultEnlistOrigin());
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return handleAsync(fn, executor, defaultEnlistOrigin());
    }
    
    // ========================================================================
    // DependentPromise / Decorator Overrides
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> dependent() {
        return dependent(Promise.Origin.NONE);
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * If the requested {@code defaultEnlistOptions} are identical to the current ones, 
     * this instance is returned to avoid unnecessary wrapping.
     * </p>
     */
    @Override
    public DependentPromise<T> dependent(Set<Promise.Origin> defaultEnlistOptions) {
        if (null == defaultEnlistOptions) {
            defaultEnlistOptions = Promise.Origin.NONE;
        }
        
        if (identicalSets(defaultEnlistOptions, this.defaultEnlistOptions)) {
            return this;
        } else {
            return ConfigurableDependentPromise.from(delegate, defaultEnlistOptions);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Cancels the underlying delegate. The cancellation of enlisted origins is handled 
     * by the hook registered in {@link #setup()}.
     * </p>
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        // See postConstruct -- handling is done in delegate.whenComplete
        return delegate.cancel(mayInterruptIfRunning);
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDone() {
        return delegate.isDone();
    }

    /** {@inheritDoc} */
    @Override
    public T get() throws InterruptedException, ExecutionException {
        return delegate.get();
    }

    /** {@inheritDoc} */
    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.get(timeout, unit);
    }

    /** {@inheritDoc} */
    @Override
    public T getNow(T valueIfAbsent) throws CancellationException, CompletionException {
        return delegate.getNow(valueIfAbsent);
    }
    
    /** {@inheritDoc} */
    @Override
    public T getNow(Supplier<? extends T> valueIfAbsent) throws CancellationException, CompletionException {
        return delegate.getNow(valueIfAbsent);
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean isIn(State state) {
        return CompletionStageAPI.current().isInState(delegate, state, true);
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean isIn(Set<State> states) {
        return CompletionStageAPI.current().isInState(delegate, states, true);
    }
    
    /** {@inheritDoc} */
    @Override 
    public T resultNow() {
        return CompletionStageAPI.current().resultNow(delegate, true);
    }
    
    /** {@inheritDoc} */
    @Override 
    public Throwable exceptionNow() {
        return CompletionStageAPI.current().exceptionNow(delegate, true);
    }
    
    /** {@inheritDoc} */
    @Override
    public T join() throws CancellationException, CompletionException {
        return delegate.join();
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean isCompletedExceptionally() {
        return delegate.isCompletedExceptionally();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the immediate underlying delegate.
     * </p>
     */
    @Override
    public Promise<T> unwrap() {
        return delegate;
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * Recursively unwraps all decoration layers to reveal the underlying base promise.
     * </p>
     */
    @Override
    public Promise<T> raw() {
        return delegate.raw();
    }
    
    /** {@inheritDoc} */
    @Override
    public CompletableFuture<T> toCompletableFuture() {
        return toCompletableFuture(defaultEnlistOrigin());
    }

    /**
     * Converts this dependent promise to a standard {@link CompletableFuture}, with explicit 
     * control over cancellation propagation.
     * <p>
     * If {@code enlistOrigin} is {@code true}, the returned {@code CompletableFuture} is 
     * wrapped in an anonymous subclass that overrides {@code cancel(boolean)} to also 
     * cancel this {@code ConfigurableDependentPromise}, ensuring the cancellation chain 
     * is not broken when crossing the {@code CompletableFuture} boundary.
     * </p>
     *
     * @param enlistOrigin if {@code true}, cancelling the returned future cancels this promise
     * @return the {@code CompletableFuture}
     */
    @Override
    public CompletableFuture<T> toCompletableFuture(boolean enlistOrigin) {
        CompletionStage<T> alpha = alphaOf(delegate);
        CompletableFuture<T> rootDelegate = alpha instanceof CompletableFuture ? (CompletableFuture<T>)alpha : null; 
        CompletableFuture<T> result;
        if (enlistOrigin) {
            if (rootDelegate != null) {
                // Use toCompletableFuture only if there is a delegate
                CompletableFuture<T> defaultResult = delegate.toCompletableFuture();
                if (rootDelegate == defaultResult) {
                    return defaultResult;
                }
            }
            result = new CompletableFuture<T>() {
                @Override 
                public boolean cancel(boolean mayInterruptIfRunning) {
                    ConfigurableDependentPromise.this.cancel(mayInterruptIfRunning);
                    return super.cancel(mayInterruptIfRunning);
                }
            };
        } else {
            CompletableFuture<T> defaultResult = delegate.toCompletableFuture();
            if (rootDelegate != defaultResult) {
                // Assume if there is no rootDelegate or rootDelegate != rootDelegate.toCompletableFuture
                // then toCompletableFuture returns stage that doesn't cancel original one
                return defaultResult;
            }
            result = new CompletableFuture<>();
        }
        whenComplete((r, e) -> iif(null == e ? result.complete(r) : result.completeExceptionally(e)));
        return result;
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * Extracts the "alpha" (innermost raw promise) from the delegate.
     * </p>
     */
    @Override
    public CompletionStage<T> α() {
        return alphaOf(delegate);
    }
    
    /**
     * Returns a diagnostic string representation of this promise, including the delegate, 
     * enlistment options, and enlisted cancellable origins.
     */
    @Override
    public String toString() {
        return String.format(
            "%s@%d[delegate=%s, enlistOptions=%s, cancellable=%s]", 
            getClass().getSimpleName(), System.identityHashCode(this), 
            delegate, this.defaultEnlistOptions, null == cancellableOrigins ? "{}" : Arrays.asList(cancellableOrigins)
        );
    }
    
    // ========================================================================
    // Internal Helpers
    // ========================================================================

    /**
     * Extracts the "alpha" (innermost raw promise) from a potentially decorated promise.
     */
    private static <T> CompletionStage<T> alphaOf(Promise<T> promise) {
        if (promise instanceof Decorator) {
            @SuppressWarnings("unchecked")
            Decorator<T> decorator = (Decorator<T>)promise;
            return decorator.α();
        } else {
            return promise;
        }
    }
    
    /**
     * Creates a single-element array containing this promise if {@code enlist} is true,
     * or {@code null} otherwise. Used for unary composition methods.
     */
    private CompletionStage<?>[] origin(boolean enlist) {
        if (enlist) {
            CompletionStage<?>[] result = new CompletionStage<?>[1];
            result[0] = this;
            return result;
        } else {
            return null;
        }
    }
    
    /**
     * Creates an array of origins based on the provided {@code enlistOptions} set.
     * Includes this promise if {@link Promise.Origin#THIS} is present, and the 
     * {@code param} stage if {@link Promise.Origin#PARAM} is present.
     * Used for binary composition methods.
     */
    private CompletionStage<?>[] originAndParam(CompletionStage<?> param, Set<Promise.Origin> enlistOptions) {
        int size = enlistOptions.size();
        if (size == 0) {
            return null;
        }
        
        CompletionStage<?>[] result = new CompletionStage<?>[size];
        int idx = 0;
        if (enlistOptions.contains(Promise.Origin.THIS)) {
            result[idx++] = this;
        }
        if (enlistOptions.contains(Promise.Origin.PARAM) && param != null) {
            result[idx++] = param;
        }
        return result;
    }

    /**
     * Returns {@code true} if the {@code defaultEnlistOptions} contains {@link Promise.Origin#THIS}.
     * Used as the default {@code enlistOrigin} boolean parameter for unary methods.
     */
    private boolean defaultEnlistOrigin() {
        return defaultEnlistOptions.contains(Promise.Origin.THIS);
    }

    /**
     * Iterates over the given array of promises and cancels them.
     *
     * @param promises              the promises to cancel
     * @param mayInterruptIfRunning whether to interrupt the executing thread
     */
    static void cancelPromises(CompletionStage<?>[] promises, boolean mayInterruptIfRunning) {
        if (!isEmptyArray(promises)) {
            Arrays.stream(promises)
                  .filter(p -> p != null)
                  .forEach(p -> cancelPromise(p, mayInterruptIfRunning));
        }
    }


    /**
     * Checks if two sets contain exactly the same elements.
     */
    private static boolean identicalSets(Set<?> a, Set<?> b) {
        return a.containsAll(b) && b.containsAll(a);
    }
    
    /**
     * Checks if an array is {@code null} or empty.
     */
    private static boolean isEmptyArray(Object[] array) {
        return null == array || array.length == 0;
    }
}