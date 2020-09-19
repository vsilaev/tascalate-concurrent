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

import static net.tascalate.concurrent.SharedFunctions.NO_SUCH_ELEMENT;
import static net.tascalate.concurrent.SharedFunctions.cancelPromise;
import static net.tascalate.concurrent.SharedFunctions.failure;
import static net.tascalate.concurrent.SharedFunctions.selectFirst;

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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
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
    protected final Promise<T> delegate;
    protected final CompletionStage<?>[] cancellableOrigins;
    protected final Set<PromiseOrigin> defaultEnlistOptions;
    
    protected ConfigurableDependentPromise(Promise<T> delegate, 
                                           Set<PromiseOrigin> defaultEnlistOptions, 
                                           CompletionStage<?>[] cancellableOrigins) {
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
    
    private static <U> DependentPromise<U> doWrap(Promise<U> original, 
                                                  Set<PromiseOrigin> defaultEnlistOptions, 
                                                  CompletionStage<?>[] cancellableOrigins) {
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
        ConfigurableDependentPromise<U> result = 
            new ConfigurableDependentPromise<>(original, defaultEnlistOptions, cancellableOrigins);
        
        if (result.isCancelled()) {
            // Wrapped over already cancelled Promise
            // So result.cancel() has no effect
            // and we have to cancel origins explicitly
            // right after construction
            cancelPromises(result.cancellableOrigins, true);
        }
        return result;
    }
    
    @Override
    public DependentPromise<T> onCancel(Runnable code) {
        return new ExtraCancellationDependentPromise<>(this, code);
    }
    
    // All delay overloads delegate to these methods
    @Override
    public DependentPromise<T> delay(Duration duration, boolean delayOnError) {
        return delay(duration, delayOnError, defaultEnlistOrigin());
    }
    
    @Override
    public DependentPromise<T> delay(Duration duration, boolean delayOnError, boolean enlistOrigin) {
        if (!(delayOnError || enlistOrigin)) {
            // Fast route
            return thenCompose( 
                v -> thenCombineAsync(Timeouts.delay(duration), selectFirst(), PromiseOrigin.PARAM_ONLY)
            );
        }
        CompletableFuture<Try<? super T>> delayed = new CompletableFuture<>();
        whenComplete(Timeouts.configureDelay(this, delayed, duration, delayOnError));
        // Use *Async to execute on default "this" executor
        return 
        this.handle(Try.liftResult(), enlistOrigin)
            .thenCombineAsync(delayed, selectFirst(), PromiseOrigin.ALL)
            .thenCompose(Try::asPromise, true);
    }

    // All orTimeout overloads delegate to these methods
    @Override
    public DependentPromise<T> orTimeout(Duration duration, boolean cancelOnTimeout) {
        return orTimeout(duration, cancelOnTimeout, defaultEnlistOrigin());
    }
    
    @Override
    public DependentPromise<T> orTimeout(Duration duration, boolean cancelOnTimeout, boolean enlistOrigin) {
        Promise<? extends Try<T>> onTimeout = Timeouts.delayed(null, duration);
        DependentPromise<T> result = 
        this.handle(Try.liftResult(), enlistOrigin)
            // Use *Async to execute on default "this" executor
            .applyToEitherAsync(onTimeout, v -> doneOrTimeout(v, duration), PromiseOrigin.ALL)
            .thenCompose(Try::asPromise, true);
        
        result.whenComplete(Timeouts.timeoutsCleanup(this, onTimeout, cancelOnTimeout));
        return result;
    }
    
    @Override
    public DependentPromise<T> onTimeout(T value, Duration duration, boolean cancelOnTimeout) {
        return onTimeout(value, duration, cancelOnTimeout, defaultEnlistOrigin());
    }
    
    @Override
    public DependentPromise<T> onTimeout(T value, Duration duration, boolean cancelOnTimeout, boolean enlistOrigin) {
        Promise<Try<T>> onTimeout = Timeouts.delayed(Try.success(value), duration);
        DependentPromise<T> result = 
        this.handle(Try.liftResult(), enlistOrigin)
            // Use *Async to execute on default "this" executor
            .applyToEitherAsync(onTimeout, Function.identity(), PromiseOrigin.ALL)
            .thenCompose(Try::asPromise, true);

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
        // timeout converted to supplier
        Promise<Supplier<Try<T>>> onTimeout = Timeouts.delayed(tryCall(supplier), duration);
        
        DependentPromise<T> result = 
        this.handle(Try.liftResult(), enlistOrigin)
            .thenApply(SharedFunctions::supply, true)
            // Use *Async to execute on default "this" executor
            .applyToEitherAsync(onTimeout, Supplier::get, PromiseOrigin.ALL)
            .thenCompose(Try::asPromise, true);
        
        result.whenComplete(Timeouts.timeoutsCleanup(this, onTimeout, cancelOnTimeout));
        return result;
    }
    
    @Override
    public <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.thenApply(fn), origin(enlistOrigin));
    }

    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.thenApplyAsync(fn), origin(enlistOrigin));
    }

    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenApplyAsync(fn, executor), origin(enlistOrigin));
    }    
    
    @Override
    public DependentPromise<Void> thenAccept(Consumer<? super T> action, boolean enlistOrigin) {
        return wrap(delegate.thenAccept(action), origin(enlistOrigin));
    }

    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, boolean enlistOrigin) {
        return wrap(delegate.thenAcceptAsync(action), origin(enlistOrigin));
    }

    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenAcceptAsync(action, executor), origin(enlistOrigin));
    }    

    @Override
    public DependentPromise<Void> thenRun(Runnable action, boolean enlistOrigin) {
        return wrap(delegate.thenRun(action), origin(enlistOrigin));
    }

    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action, boolean enlistOrigin) {
        return wrap(delegate.thenRunAsync(action), origin(enlistOrigin));
    }

    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action, Executor executor, boolean enlistOrigin) {
        return wrap(delegate.thenRunAsync(action, executor), origin(enlistOrigin));
    }

    @Override
    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, 
                                                  BiFunction<? super T, ? super U, ? extends V> fn,
                                                  Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.thenCombine(other, fn), originAndParam(other, enlistOptions));
    }

    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, 
                                                       BiFunction<? super T, ? super U, ? extends V> fn,
                                                       Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.thenCombineAsync(other, fn), originAndParam(other, enlistOptions));
    }

    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn, 
                                                       Executor executor,
                                                       Set<PromiseOrigin> enlistOptions) {
        
        return wrap(delegate.thenCombineAsync(other, fn, executor), originAndParam(other, enlistOptions));
    }
    
    @Override
    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, 
                                                     BiConsumer<? super T, ? super U> action,
                                                     Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.thenAcceptBoth(other, action), originAndParam(other, enlistOptions));
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                          BiConsumer<? super T, ? super U> action,
                                                          Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.thenAcceptBothAsync(other, action), originAndParam(other, enlistOptions));
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                          BiConsumer<? super T, ? super U> action, 
                                                          Executor executor,
                                                          Set<PromiseOrigin> enlistOptions) {
        
        return wrap(delegate.thenAcceptBothAsync(other, action, executor), originAndParam(other, enlistOptions));
    }    
    
    @Override
    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterBoth(other, action), originAndParam(other, enlistOptions));
    }

    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterBothAsync(other, action), originAndParam(other, enlistOptions));
    }

    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                                    Runnable action, 
                                                    Executor executor,
                                                    Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterBothAsync(other, action, executor), originAndParam(other, enlistOptions));
    }

    @Override
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, 
                                                 Function<? super T, U> fn,
                                                 Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.applyToEither(other, fn), originAndParam(other, enlistOptions));
    }

    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.applyToEitherAsync(other, fn), originAndParam(other, enlistOptions));
    }

    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Executor executor,
                                                      Set<PromiseOrigin> enlistOptions) {
        
        return wrap(delegate.applyToEitherAsync(other, fn, executor), originAndParam(other, enlistOptions));
    }    

    @Override
    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, 
                                               Consumer<? super T> action,
                                               Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.acceptEither(other, action), originAndParam(other, enlistOptions));
    }

    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.acceptEitherAsync(other, action), originAndParam(other, enlistOptions));
    }

    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Executor executor,
                                                    Set<PromiseOrigin> enlistOptions) {
        
        return wrap(delegate.acceptEitherAsync(other, action, executor), originAndParam(other, enlistOptions));
    }    

    @Override
    public DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterEither(other, action), originAndParam(other, enlistOptions));
    }

    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterEitherAsync(other, action), originAndParam(other, enlistOptions));
    }

    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                                      Runnable action, 
                                                      Executor executor,
                                                      Set<PromiseOrigin> enlistOptions) {
        return wrap(delegate.runAfterEitherAsync(other, action, executor), originAndParam(other, enlistOptions));
    }
    
    @Override
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return wrap(delegate.thenCompose(fn), origin(enlistOrigin));
    }

    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return wrap(delegate.thenComposeAsync(fn), origin(enlistOrigin));
    }

    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, 
                                                    Executor executor, 
                                                    boolean enlistOrigin) {
        return wrap(delegate.thenComposeAsync(fn, executor), origin(enlistOrigin));
    }

    @Override
    public DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn, boolean enlistOrigin) {
        return wrap(delegate.exceptionally(fn), origin(enlistOrigin));
    }

    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, boolean enlistOrigin) {
        PromiseRef<T> onError = new PromiseRef<>();
        return setupExceptionalHandler(
            handle((r, ex) -> ex == null ? this : onError.modify( handleAsync((r1, ex1) -> fn.apply(ex1), false) ), 
                   enlistOrigin),
            onError);
    }
    
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor, boolean enlistOrigin) {
        PromiseRef<T> onError = new PromiseRef<>();
        return setupExceptionalHandler(
            handle((r, ex) -> ex == null ? this : onError.modify( handleAsync((r1, ex1) -> fn.apply(ex1), executor, false) ), 
                   enlistOrigin),
            onError);
    }
    
    @Override
    public DependentPromise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn, boolean enlistOrigin) {
        CompletionStageRef<T> onError = new CompletionStageRef<>();
        return drop( handle((r, ex) -> ex == null ? 
                                       this : 
                                       onError.modify(fn.apply(ex)), enlistOrigin).onCancel(onError.cancel) );
    }
    
    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, boolean enlistOrigin) {
        PromiseRef<T> onError = new PromiseRef<>();
        return setupExceptionalHandler(
            handle((r, ex) -> ex == null ? this : onError.modify(exceptionallyComposeHelper(fn, null, false)),
                   enlistOrigin),
            onError);
    }

    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, 
                                                         Executor executor, 
                                                         boolean enlistOrigin) {
        PromiseRef<T> onError = new PromiseRef<>();
        return setupExceptionalHandler(
            handle((r, ex) -> ex == null ? this : onError.modify(exceptionallyComposeHelper(fn, executor, true)),
                   enlistOrigin),
            onError);
    }
    
    Promise<T> exceptionallyComposeHelper(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor, boolean useExecutor) {
        CompletionStageRef<T> ref = new CompletionStageRef<>();
        DependentPromise<CompletionStage<T>> asyncLifted = useExecutor ?
            handleAsync((r1, ex1) -> ref.modify(fn.apply(ex1)), executor, false) 
            :
            handleAsync((r1, ex1) -> ref.modify(fn.apply(ex1)), false);
            
        return drop(asyncLifted.onCancel(ref.cancel));
    }
    
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate, boolean enlistOrigin) {
        return thenFilter(predicate, NO_SUCH_ELEMENT, enlistOrigin);
    }
    
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, boolean enlistOrigin) {
        return thenCompose(v -> predicate.test(v) ? this : failure(errorSupplier, v), enlistOrigin);
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, boolean enlistOrigin) {
        return thenFilterAsync(predicate, NO_SUCH_ELEMENT, enlistOrigin);
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, boolean enlistOrigin) {
        return thenComposeAsync(v -> predicate.test(v) ? this : failure(errorSupplier, v), enlistOrigin); 
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor, boolean enlistOrigin) {
        return thenFilterAsync(predicate, NO_SUCH_ELEMENT, executor, enlistOrigin);
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, Executor executor, boolean enlistOrigin) {
        return thenComposeAsync(v -> predicate.test(v) ? this : failure(errorSupplier, v), executor, enlistOrigin);
    }
    
    @Override
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return wrap(delegate.whenComplete(action), origin(enlistOrigin));
    }

    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return wrap(delegate.whenCompleteAsync(action), origin(enlistOrigin));
    }

    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, 
                                                 Executor executor, 
                                                 boolean enlistOrigin) {
        return wrap(delegate.whenCompleteAsync(action, executor), origin(enlistOrigin));
    }

    @Override
    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.handle(fn), origin(enlistOrigin));
    }

    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return wrap(delegate.handleAsync(fn), origin(enlistOrigin));
    }

    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, 
                                               Executor executor, 
                                               boolean enlistOrigin) {
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
    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, 
                                                  BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombine(other, fn, defaultEnlistOptions);
    }

    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, 
                                                       BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, fn, defaultEnlistOptions);
    }

    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn, 
                                                       Executor executor) {
        
        return thenCombineAsync(other, fn, executor, defaultEnlistOptions);
    }
    
    @Override
    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, 
                                                     BiConsumer<? super T, ? super U> action) {
        return thenAcceptBoth(other, action, defaultEnlistOptions);
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                          BiConsumer<? super T, ? super U> action) {
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
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return exceptionallyAsync(fn, defaultEnlistOrigin());
    }
    
    @Override
    public DependentPromise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return exceptionallyCompose(fn, defaultEnlistOrigin());
    }
    
    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return exceptionallyComposeAsync(fn, defaultEnlistOrigin());
    }

    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        return exceptionallyComposeAsync(fn, executor, defaultEnlistOrigin());
    }
    
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        return exceptionallyAsync(fn, executor, defaultEnlistOrigin());
    }

    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate) {
        return thenFilter(predicate, defaultEnlistOrigin());
    }
    
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier) {
        return thenFilter(predicate, errorSupplier, defaultEnlistOrigin());
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate) {
        return thenFilterAsync(predicate, defaultEnlistOrigin());
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier) {
        return thenFilterAsync(predicate, errorSupplier, defaultEnlistOrigin());
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor) {
        return thenFilterAsync(predicate, executor, defaultEnlistOrigin());
    }
    
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, Executor executor) {
        return thenFilterAsync(predicate, errorSupplier, executor, defaultEnlistOrigin());
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
    
    @Override
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
    public boolean isCompletedExceptionally() {
        return delegate.isCompletedExceptionally();
    }
    
    @Override
    public Promise<T> unwrap() {
        if (null == cancellableOrigins || cancellableOrigins.length == 0) {
            // No state collected, may optimize away own reference
            return delegate;
        } else {
            return cancellablePromiseOf(delegate);
        }
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
    
    @Override
    public CompletableFuture<T> toCompletableFuture(boolean enlistOrigin) {
        if (!enlistOrigin) {
            return delegate.toCompletableFuture();
        } else {
            CompletableFutureWrapper<T> result = new CompletableFutureWrapper<T>() {
                @Override 
                public boolean cancel(boolean mayInterruptIfRunning) {
                    if (ConfigurableDependentPromise.this.cancel(mayInterruptIfRunning)) {
                        return super.cancel(mayInterruptIfRunning);
                    } else {
                        return false;
                    }
                }
            };
            whenComplete(result::complete);
            return result.toCompletableFuture(); // Effectively result.delegate
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

    static <T> DependentPromise<T> setupExceptionalHandler(DependentPromise<? extends Promise<T>> origin, 
                                                           PromiseRef<?> onException) {
        return (DependentPromise<T>)
            drop(origin).onCancel(onException.cancel)
                        .unwrap();
    }
    
    static <T> DependentPromise<T> drop(DependentPromise<? extends CompletionStage<T>> lifted) {
        return lifted.thenCompose(Function.identity(), true);
    }
    
    private static boolean identicalSets(Set<?> a, Set<?> b) {
        return a.containsAll(b) && b.containsAll(a);
    }
    
    
    static <T> Try<T> doneOrTimeout(Try<T> result, Duration duration) {
        return null != result ? result: Try.failure(new TimeoutException("Timeout after " + duration));
    }
    
    private static <R> Supplier<Try<R>> tryCall(Supplier<? extends R> supplier) {
        return () -> {
            try {
                return Try.success(supplier.get());
            } catch (Throwable ex) {
                return Try.failure(ex);
            }
        };
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
        public Promise<T> unwrap() {
            return unwrap(Promise::unwrap);
        }
        
        @Override
        public Promise<T> raw() {
            return unwrap(Promise::raw);
        }

        private Promise<T> unwrap(Function<Promise<T>, Promise<T>> fn) {
            Promise<T> unwrapped = fn.apply(delegate);
            if (unwrapped == delegate) {
                return this;
            } else {
                return new UndecoratedCancellationPromise<>(unwrapped, dependent);
            }   
        }
        
        @Override
        protected <U> Promise<U> wrap(CompletionStage<U> original) {
            // No wrapping by definition
            return (Promise<U>)original;
        }
    }

    static class PromiseRef<T> extends AtomicReference<Promise<T>> {
        private static final long serialVersionUID = 1L;
        
        Promise<T> modify(Promise<T> newValue) {
            set(newValue);
            return newValue;
        }
        
        Runnable cancel = () -> {
            Promise<?> promise = get();
            if (null != promise) {
                promise.cancel(true);
            }
        };
    }
}
