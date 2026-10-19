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
package net.tascalate.concurrent.var;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link ContextVar} implementation backed by the Java 25+ {@link ScopedValue} API.
 * <p>
 * This class is available only in the Java 25 source set of the multi-release JAR.
 * Unlike {@link ThreadLocalVar}, which wraps mutable {@link ThreadLocal} storage,
 * a {@code ScopedValue} is <b>immutable</b> and <b>scoped</b>: a new value is bound
 * for the duration of an execution scope via {@link ScopedValue#where(ScopedValue, Object)},
 * and the previous binding is automatically restored when the scope completes.
 * </p>
 *
 * <h2>Key Differences from {@code ThreadLocalVar}</h2>
 * <ul>
 *   <li><b>Immutability:</b> Values cannot be mutated in place; rebinding creates a new scope.
 *       Therefore this class implements {@link ContextVar} directly rather than
 *       {@link ModifiableContextVar}, and {@code set}/{@code remove} are not available.</li>
 *   <li><b>Inherent strictness:</b> Binding a scoped value automatically shadows any outer
 *       binding and restores it upon exit. Consequently, {@link #strict()} and
 *       {@link #optimized()} both return this instance unchanged &mdash; there is no
 *       destructive "optimized" mode, and no overhead of manually saving/restoring state.</li>
 *   <li><b>Virtual thread friendliness:</b> Scoped values are designed to work efficiently
 *       with virtual threads, avoiding the per-thread memory overhead of thread locals.</li>
 * </ul>
 *
 * <p>
 * Instances are created via the static factories {@link #newInstance()},
 * {@link #newInstance(String)}, or by wrapping an existing {@link ScopedValue}
 * with {@link #of(ScopedValue)} / {@link #of(String, ScopedValue)}.
 * </p>
 *
 * @param <T> the type of the value held by this context variable
 * @see ScopedValue
 * @see ScopedValueVarGroup
 */
public final class ScopedValueVar<T> implements ContextVar<T> {

    /** The human-readable name of this variable (useful for debugging). */
    final String name;

    /** The underlying {@link ScopedValue} delegate. */
    final ScopedValue<T> delegate;

    /**
     * Constructs a new {@code ScopedValueVar} with the specified name and delegate.
     *
     * @param name     the name of the variable
     * @param delegate the underlying {@link ScopedValue}
     */
    private ScopedValueVar(String name, ScopedValue<T> delegate) {
        this.name = name;
        this.delegate = delegate;
    }

    /**
     * Returns the current value of this context variable for the calling scope.
     *
     * @return the current value, or {@code null} if the scoped value is not bound
     */
    @Override
    public T get() {
        return delegate.isBound() ? delegate.get() : null;
    }

    /**
     * Returns the factory used to group multiple {@code ScopedValueVar}s together
     * into a single {@link ScopedValueVarGroup}, which binds all values within one
     * {@link ScopedValue.Carrier} chain.
     *
     * @return a function that creates a grouped context var from a list of variables
     */
    @Override
    public Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> groupFactory() {
        return ScopedValueVarGroup.FACTORY;
    }

    /**
     * Executes the given {@link Runnable} with this scoped value bound to the captured value.
     *
     * @param capturedValue the value to bind
     * @param code          the code to execute within the binding scope
     */
    @Override
    public void runWith(T capturedValue, Runnable code) {
        ScopedValue.where(delegate, capturedValue).run(code);
    }

    /**
     * Executes the given {@link Supplier} with this scoped value bound to the captured value.
     *
     * @param capturedValue the value to bind
     * @param code          the code to execute within the binding scope
     * @param <V>           the return type of the supplier
     * @return the result of the supplier
     */
    @Override
    public <V> V supplyWith(T capturedValue, Supplier<V> code) {
        return ScopedValue.where(delegate, capturedValue).call(code::get);
    }

    /**
     * Executes the given {@link Callable} with this scoped value bound to the captured value.
     *
     * @param capturedValue the value to bind
     * @param code          the code to execute within the binding scope
     * @param <V>           the return type of the callable
     * @return the result of the callable
     * @throws Exception if the callable throws an exception
     */
    @Override
    public <V> V callWith(T capturedValue, Callable<V> code) throws Exception {
        return ScopedValue.where(delegate, capturedValue).call(code::call);
    }

    @Override
    public String toString() {
        return String.format("<scoped-value-var>[%s/%s] = %s", name, delegate, get());
    }

    /**
     * Creates a new instance of {@code ScopedValueVar} with an auto-generated anonymous name.
     *
     * @param <T> the type of the value
     * @return a new {@code ScopedValueVar} instance
     */
    public static <T> ContextVar<T> newInstance() {
        return newInstance(ContextVarHelper.generateVarName());
    }

    /**
     * Creates a new instance of {@code ScopedValueVar} with the specified name.
     *
     * @param name the name of the variable
     * @param <T>  the type of the value
     * @return a new {@code ScopedValueVar} instance
     */
    public static <T> ContextVar<T> newInstance(String name) {
        return of(name, ScopedValue.newInstance());
    }

    /**
     * Wraps an existing {@link ScopedValue} as a {@code ScopedValueVar}
     * with an auto-generated anonymous name.
     *
     * @param scopedValue the scoped value to wrap
     * @param <T>         the type of the value
     * @return a new {@code ScopedValueVar} instance
     */
    public static <T> ContextVar<T> of(ScopedValue<T> scopedValue) {
        return of(ContextVarHelper.generateVarName(), scopedValue);
    }

    /**
     * Wraps an existing {@link ScopedValue} as a {@code ScopedValueVar} with the specified name.
     *
     * @param name        the name of the variable
     * @param scopedValue the scoped value to wrap
     * @param <T>         the type of the value
     * @return a new {@code ScopedValueVar} instance
     */
    public static <T> ContextVar<T> of(String name, ScopedValue<T> scopedValue) {
        return new ScopedValueVar<>(name, scopedValue);
    }
}