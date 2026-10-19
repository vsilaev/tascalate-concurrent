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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A grouped context variable that manages a collection of {@link ScopedValue}s
 * and binds them collectively within a single {@link ScopedValue.Carrier} chain.
 * <p>
 * This class is available only in the Java 25 source set of the multi-release JAR.
 * It is the scoped-value counterpart of {@link ThreadLocalVarGroup}: instead of
 * mutating multiple thread locals, it builds one immutable carrier via repeated
 * {@link ScopedValue.Carrier#where(ScopedValue, Object)} calls and runs the supplied
 * code within that combined scope. This is the recommended (and most efficient) way
 * to propagate multiple {@code ScopedValue}s together, because all values are bound
 * atomically in a single scope rather than via nested scopes.
 * </p>
 * <p>
 * Instances are typically created internally by {@link #FACTORY} when several
 * {@link ScopedValueVar}s are merged via {@link ContextVar#relay(ContextVar...)}
 * or chained via {@link ContextVar.Binding}.
 * </p>
 *
 * @param <T> the common supertype of the values held by the grouped scoped values
 * @see ScopedValueVar
 * @see ScopedValue.Carrier
 */
final class ScopedValueVarGroup<T> implements ContextVar<List<T>> {

    /** The list of underlying {@link ScopedValue} delegates. */
    private final List<? extends ScopedValue<? extends T>> scopedValues;

    /**
     * Constructs a new group with the specified list of scoped values.
     *
     * @param scopedValues the scoped values to group
     */
    ScopedValueVarGroup(List<? extends ScopedValue<? extends T>> scopedValues) {
        this.scopedValues = scopedValues;
    }

    /**
     * Retrieves the current values of all scoped values in this group.
     *
     * @return a list of current values, in the same order as the scoped values;
     *         unbound scoped values are represented as {@code null}
     */
    @Override
    public List<T> get() {
        return scopedValues.stream()
                           .map(v -> v.isBound() ? v.get() : null)
                           .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return String.format("<scoped-value-ctx-vars>[%s] = %s", scopedValues, get());
    }

    /**
     * Executes the given {@link Runnable} with all captured values bound within a single
     * {@link ScopedValue.Carrier} scope. If the group is empty, the code runs directly.
     *
     * @param capturedValues the values to bind, in the same order as the scoped values
     * @param code           the code to execute within the combined binding scope
     */
    @Override
    public void runWith(List<T> capturedValues, Runnable code) {
        var carrier = createCallChain(capturedValues);
        if (null != carrier) {
            carrier.run(code);
        } else {
            code.run();
        }
    }

    /**
     * Executes the given {@link Supplier} with all captured values bound within a single
     * {@link ScopedValue.Carrier} scope. If the group is empty, the code runs directly.
     *
     * @param capturedValues the values to bind, in the same order as the scoped values
     * @param code           the code to execute within the combined binding scope
     * @param <V>            the return type of the supplier
     * @return the result of the supplier
     */
    @Override
    public <V> V supplyWith(List<T> capturedValues, Supplier<V> code) {
        var carrier = createCallChain(capturedValues);
        if (null != carrier) {
            return carrier.call(code::get);
        } else {
            return code.get();
        }
    }

    /**
     * Executes the given {@link Callable} with all captured values bound within a single
     * {@link ScopedValue.Carrier} scope. If the group is empty, the code runs directly.
     *
     * @param capturedValues the values to bind, in the same order as the scoped values
     * @param code           the code to execute within the combined binding scope
     * @param <V>            the return type of the callable
     * @return the result of the callable
     * @throws Exception if the callable throws an exception
     */
    @Override
    public <V> V callWith(List<T> capturedValues, Callable<V> code) throws Exception {
        var carrier = createCallChain(capturedValues);
        if (null != carrier) {
            return carrier.call(code::call);
        } else {
            return code.call();
        }
    }

    /**
     * Builds a {@link ScopedValue.Carrier} that binds every scoped value in this group
     * to its corresponding captured value.
     *
     * @param capturedValues the values to bind, aligned with {@link #scopedValues}
     * @return a carrier with all bindings applied, or {@code null} if the group is empty
     */
    private ScopedValue.Carrier createCallChain(List<? extends T> capturedValues) {
        ScopedValue.Carrier c = null;
        var scopedValuesIterator = scopedValues.iterator();
        var capturedValuesIterator = capturedValues.iterator();
        while (scopedValuesIterator.hasNext() && capturedValuesIterator.hasNext()) {
            @SuppressWarnings("unchecked")
            ScopedValue<T> scopedValue = (ScopedValue<T>) scopedValuesIterator.next();
            var capturedValue = capturedValuesIterator.next();
            if (null == c) {
                c = ScopedValue.where(scopedValue, capturedValue);
            } else {
                c = c.where(scopedValue, capturedValue);
            }
        }
        return c;
    }

    /**
     * Factory for creating {@link ScopedValueVarGroup} instances from a list of
     * {@link ScopedValueVar}s. Used by the grouping/merging machinery in
     * {@link ContextVar.Binding} and {@link ContextVarHelper}.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static final Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> FACTORY = vars -> {
        List<ScopedValue<?>> svs = new ArrayList<>();
        vars.forEach(v -> {
            ScopedValueVar<?> tv = (ScopedValueVar<?>)v;
            svs.add(tv.delegate);
        });
        return new ScopedValueVarGroup(svs);
    };
}