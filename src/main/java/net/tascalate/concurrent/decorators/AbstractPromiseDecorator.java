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
package net.tascalate.concurrent.decorators;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.tascalate.concurrent.DependentPromise;
import net.tascalate.concurrent.Promise;

/**
 * Abstract base class for decorating a {@link Promise}.
 * <p>
 * This is the ultimate base class in the decorator hierarchy. It extends 
 * {@link AbstractFutureDecorator} and implements the full {@link Promise} interface. 
 * It delegates all advanced {@link Promise} methods (such as timeouts, delays, filters, 
 * and dependent conversions) to the underlying delegate, wrapping the results to 
 * preserve the decoration chain.
 * </p>
 * <p>
 * Subclasses can override specific methods to intercept and modify behavior (e.g., 
 * adding context propagation, executor binding, or custom cancellation logic) while 
 * relying on this class to handle the boilerplate of delegating the remaining API.
 * </p>
 * <h2>Class Hierarchy</h2>
 * <pre>{@code
 * AbstractCompletionStageDecorator
 *     ├── CompletionStageDecorator
 *     │       └── ExecutorBoundCompletionStage
 *     └── AbstractPromiseLikeDecorator
 *             └── AbstractFutureDecorator
 *                     ├── CompletableFutureDecorator
 *                     └── BlockingCompletionStageDecorator
 *
 * AbstractPromiseDecorator  <-- YOU ARE HERE (Adds Promise lifecycle, timeouts, filters)
 *     ├── ExecutorBoundPromise
 *     ├── ExtendedPromiseDecorator
 *     │       └── CustomizablePromiseDecorator
 *     └── AbstractDependentPromiseDecorator
 *             ├── ExecutorBoundDependentPromise
 *             └── ExtendedDependentPromiseDecorator
 *                     └── CustomizableDependentPromiseDecorator
 * }</pre>
 * @param <T> a type of the successfully resolved promise value
 * @param <D> a type of the concrete {@link Promise} subclass being decorated
 * @author vsilaev
 * @see AbstractFutureDecorator
 * @see Promise
 */
abstract public class AbstractPromiseDecorator<T, D extends Promise<T>>
    extends AbstractFutureDecorator<T, D>
    implements Promise<T> {
    
    /**
     * Creates a new promise decorator wrapping the specified delegate.
     *
     * @param delegate the {@link Promise} to delegate calls to
     */
    protected AbstractPromiseDecorator(D delegate) {
        super(delegate);
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
     * Delegates to the underlying promise's {@code unwrap()} method.
     * </p>
     */
    @Override
    public Promise<T> unwrap() {
        return delegate;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Delegates to the underlying promise's {@code raw()} method to fully unroll 
     * all decoration layers.
     * </p>
     */
    @Override
    public Promise<T> raw() {
        return delegate.raw();
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> dependent() {
        return delegate.dependent();
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> dependent(Set<Promise.Origin> defaultEnlistOptions) {
        return delegate.dependent(defaultEnlistOptions);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Wraps the result of the delegate's {@code defaultAsyncOn} to preserve the 
     * decoration chain.
     * </p>
     */
    @Override
    public Promise<T> defaultAsyncOn(Executor executor) {
        return wrap(delegate.defaultAsyncOn(executor));
    }

    /** 
     * {@inheritDoc} 
     * <p>
     * Delegates directly to the underlying promise. The action is attached to the 
     * delegate, so it does not need to be wrapped.
     * </p>
     */
    @Override 
    public Promise<T> onCancel(Runnable action) {
        //return wrap(delegate.onCancel(code)); -- also ok
        delegate.onCancel(action);
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> delay(long timeout, TimeUnit unit) {
        return wrap(delegate.delay(timeout, unit));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> delay(long timeout, TimeUnit unit, boolean delayOnError) {
        return wrap(delegate.delay(timeout, unit, delayOnError));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> delay(Duration duration) {
        return wrap(delegate.delay(duration));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> delay(Duration duration, boolean delayOnError) {
        return wrap(delegate.delay(duration, delayOnError));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> orTimeout(long timeout, TimeUnit unit) {
        return wrap(delegate.orTimeout(timeout, unit));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> orTimeout(long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return wrap(delegate.orTimeout(timeout, unit, cancelOnTimeout));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> orTimeout(Duration duration) {
        return wrap(delegate.orTimeout(duration));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> orTimeout(Duration duration, boolean cancelOnTimeout) {
        return wrap(delegate.orTimeout(duration, cancelOnTimeout));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> onTimeout(T value, long timeout, TimeUnit unit) {
        return wrap(delegate.onTimeout(value, timeout, unit));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> onTimeout(T value, long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return wrap(delegate.onTimeout(value, timeout, unit, cancelOnTimeout));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> onTimeout(T value, Duration duration) {
        return wrap(delegate.onTimeout(value, duration));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> onTimeout(T value, Duration duration, boolean cancelOnTimeout) {
        return wrap(delegate.onTimeout(value, duration, cancelOnTimeout));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit) {
        return wrap(delegate.onTimeout(supplier, timeout, unit));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return wrap(delegate.onTimeout(supplier, timeout, unit, cancelOnTimeout));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> onTimeout(Supplier<? extends T> supplier, Duration duration) {
        return wrap(delegate.onTimeout(supplier, duration));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout) {
        return wrap(delegate.onTimeout(supplier, duration, cancelOnTimeout));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return wrap(delegate.exceptionallyAsync(fn));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        return wrap(delegate.exceptionallyAsync(fn, executor));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return wrap(delegate.exceptionallyCompose(fn));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return wrap(delegate.exceptionallyComposeAsync(fn));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        return wrap(delegate.exceptionallyComposeAsync(fn, executor));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> thenFilter(Predicate<? super T> predicate) {
        return wrap(delegate.thenFilter(predicate));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> thenFilter(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier) {
        return wrap(delegate.thenFilter(predicate, errorSupplier));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> thenFilterAsync(Predicate<? super T> predicate) {
        return wrap(delegate.thenFilterAsync(predicate));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier) {
        return wrap(delegate.thenFilterAsync(predicate, errorSupplier));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor) {
        return wrap(delegate.thenFilterAsync(predicate, executor));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> thenFilterAsync(Predicate<? super T> predicate, 
                                      Function<? super T, Throwable> errorSupplier, 
                                      Executor executor) {
        return wrap(delegate.thenFilterAsync(predicate, errorSupplier, executor));
    }
}