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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The core interface representing a contextual variable that holds a value specific 
 * to the current execution scope (e.g., thread, virtual thread, or request).
 * <p>
 * Context variables can be read via {@link #get()} and bound to specific values for
 * synchronous execution using the {@link #bind(Object)} operator, which returns a 
 * {@link Binding} that can be chained for multiple variables.
 * </p>
 *
 * <h2>Strict vs Optimized Propagation</h2>
 * <ul>
 *     <li><b>Strict ({@link #strict()}):</b> Preserves any existing ambient value on the 
 *         current thread before applying the new value, and restores it after execution.</li>
 *     <li><b>Optimized ({@link #optimized()}):</b> Overwrites the current value and clears it
 *         after execution. This is faster but destructive to pre-existing values.</li>
 * </ul>
 *
 * @param <T> the type of the value held by this context variable
 * @see ContextRelay
 */
public interface ContextVar<T> {

    /**
     * Represents a chain of context variable bindings for synchronous execution.
     * <p>
     * Created via {@link ContextVar#bind(Object)} or {@link ContextVar#bind(ContextVar, Object)}.
     * Bindings can be chained to assign values to multiple variables simultaneously.
     * </p>
     *
     * @param <T> the type of the value bound in this specific link of the chain
     */
    public static class Binding<T> {
        private final Binding<?> previous;
        private final ContextVar<T> variable;
        private final T value;

        Binding(Binding<?> previous, ContextVar<T> variable, T value) {
            this.previous = previous;
            this.variable = variable;
            this.value = value;
        }

        /**
         * Chains a new variable and value to this binding.
         *
         * @param variable the next variable to bind
         * @param value    the value to assign to the variable
         * @param <V>      the type of the new value
         * @return a new {@link Binding} representing the extended chain
         */
        public <V> Binding<V> bind(ContextVar<V> variable, V value) {
            return new Binding<V>(this, variable, value);
        }

        /**
         * Executes the given {@link Runnable} with all bound context variables applied.
         *
         * @param code the code to execute
         */
        public void run(Runnable code) {
            createContextualization(this).runWith(code);
        }

        /**
         * Executes the given {@link Supplier} with all bound context variables applied.
         *
         * @param code the code to execute
         * @param <V>  the return type of the supplier
         * @return the result of the supplier
         */
        public <V> V supply(Supplier<V> code) {
            return createContextualization(this).supplyWith(code);
        }

        /**
         * Executes the given {@link Callable} with all bound context variables applied.
         *
         * @param code the code to execute
         * @param <V>  the return type of the callable
         * @return the result of the callable
         * @throws Exception if the callable throws an exception
         */
        public <V> V call(Callable<V> code) throws Exception {
            return createContextualization(this).callWith(code);            
        }

        @SuppressWarnings("unchecked")
        private List<VarAssignment<Object>> varBinding() {
            List<VarAssignment<Object>> result = new ArrayList<>(4);
            result.add(new VarAssignment<>((ContextVar<Object>)this.variable, this.value));
            return result;
        }

        Contextualization<T> createContextualization() {
            return Contextualization.create(variable, value);
        }

        static Contextualization<?> createContextualization(Binding<?> top) {
            if (null == top.previous) {
                return top.createContextualization(); 
            }
            Map<Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>>, List<VarAssignment<Object>>>
                gfm = new HashMap<>();
            for (Binding<?> binding = top; binding != null; binding = binding.previous) {
                Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> key = binding.variable.groupFactory();
                List<VarAssignment<Object>> list = gfm.get(key);
                if (list == null) {
                    gfm.put(key, binding.varBinding());
                } else {
                    list.addAll(binding.varBinding());
                }
            }
            // This groups separate ThreadLocalVar-s and (most importantly) ScopedLocalVar-s
            // into the single ThreadLocalVarGroup and ScopedLocalVarGroup
            List<VarAssignment<? extends Object>> groupped =
            gfm.entrySet()
               .stream()
               .map(e -> {
                   List<VarAssignment<Object>> list = e.getValue();
                   if (list.size() == 1) {
                       return list.get(0);
                   }
                   List<ContextVar<Object>> variables = new ArrayList<>(list.size());
                   List<Object> values = new ArrayList<>(list.size());
                   for (VarAssignment<Object> i : list) {
                       variables.add(i.variable);
                       values.add(i.value);
                   } // check one only
                   ContextVar<List<?>> group = (ContextVar<List<?>>) e.getKey().apply(variables);
                   return new VarAssignment<List<?>>(group, values);
               })
               .collect(Collectors.toList());
            
            if (groupped.isEmpty()) {
                return Contextualization.create(ContextVar.empty(), null);
            } else if (groupped.size() == 1) {
                @SuppressWarnings("unchecked")
                VarAssignment<Object> binding = (VarAssignment<Object>)groupped.get(0);
                return Contextualization.create(binding.variable, binding.value);
            } else {
                List<ContextVar<? extends Object>> topGroups = new ArrayList<>();
                List<Object> topValues = new ArrayList<>();
                for (VarAssignment<? extends Object> i : groupped) {
                    topGroups.add(i.variable);
                    topValues.add(i.value);
                }
                return Contextualization.create(new ContextVarGroup.Strict<>(topGroups, true), topValues);
            }
        }
    }

    /**
     * Returns the current value of this context variable for the calling thread/scope.
     *
     * @return the current value, or null/default if not set
     */
    T get();

    /**
     * Starts a binding chain for synchronous execution with the specified value.
     *
     * @param value the value to bind
     * @return a {@link Binding} object that can be chained or executed
     */
    default Binding<T> bind(T value) {
        return new Binding<>(null, this, value);
    }

    /**
     * Executes the given {@link Callable} with the specified captured value applied to this variable.
     *
     * @param capturedValue the value to apply during execution
     * @param code          the code to execute
     * @param <V>           the return type of the callable
     * @return the result of the callable
     * @throws Exception if the callable throws an exception
     */
    <V> V callWith(T capturedValue, Callable<V> code) throws Exception;

    /**
     * Executes the given {@link Runnable} with the specified captured value applied to this variable.
     *
     * @param capturedValue the value to apply during execution
     * @param code          the code to execute
     */
    default void runWith(T capturedValue, Runnable code) {
        try {
            callWith(capturedValue, () -> {
                code.run();
                return null;
            });
        } catch (Error | RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Executes the given {@link Supplier} with the specified captured value applied to this variable.
     *
     * @param capturedValue the value to apply during execution
     * @param code          the code to execute
     * @param <V>           the return type of the supplier
     * @return the result of the supplier
     */
    default <V> V supplyWith(T capturedValue, Supplier<V> code) {
        try {
            return callWith(capturedValue, code::get);
        } catch (Error | RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Creates a {@link ContextRelay} to propagate this variable's value across thread boundaries
     * for asynchronous executions.
     *
     * @return a new {@link ContextRelay} for this variable
     */
    default ContextRelay<T> relay() {
        return new ContextRelay<T>(this);
    }

    /**
     * Convert this var to the variant with pessimistic propagation option (Strict).
     * Useful for rare cases when thread might have its own default values 
     * of the context variables and they must be restored. 
     * <p>The logic is the following:</p>
     * <ol>
     * <li>Save context variables from the current thread</li>
     * <li>Apply context variables from the snapshot</li>
     * <li>Execute code</li>
     * <li>Restore context variables saved in the step [1]</li>
     * </ol>
     *
     * @return a strict variant of this context variable
     */
    default ContextVar<T> strict() {
        return this;
    }

    /**
     * Convert this var to the variant that is optimized for performance.
     * <p>The logic is the following:</p>
     * <ol>
     * <li>Apply context variables from the snapshot</li>
     * <li>Execute code</li>
     * <li>Reset context variables</li>
     * </ol>
     *
     * @return an optimized variant of this context variable
     */
    default ContextVar<T> optimized() {
        return this;
    }

    /**
     * Returns the factory used to group multiple context variables of this type together.
     *
     * @return a function that creates a grouped context var from a list of variables
     */
    default Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> groupFactory() {
        return ContextVarGroup.FACTORY;
    }

    /**
     * Creates a new context variable backed by the <b>platform-default</b> context storage:
     * a {@link ThreadLocal} on JDK 1.8&ndash;24 and a {@code ScopedValue} on JDK 25+.
     * <p>
     * This is the recommended, portable way to declare context variables &mdash; application
     * code depends only on the {@code ContextVar} interface and never on the underlying
     * storage. Use {@link ThreadLocalVar#of(ThreadLocal)} to pin thread-local storage on
     * every JDK, or {@code ScopedValueVar.of(...)} (JDK 25+) to opt explicitly into scoped
     * values.
     * </p>
     * <p>
     * Note that semantics follow the platform primitive: on JDK 25+ bindings are immutable
     * and visible to threads spawned while the binding is active; on earlier JDKs the value
     * is confined to the owning thread.
     * </p>
     *
     * @param <T> the type of the value held by the variable
     * @return a new platform-default context variable with an auto-generated name
     */
    public static <T> ContextVar<T> newInstance() {
        return ThreadVar.newInstance();
    }

    /**
     * Creates a new context variable backed by the <b>platform-default</b> context storage
     * (see {@link #newInstance()}), with the given diagnostic name.
     *
     * @param name the variable name, used in {@code toString()} output for debugging
     * @param <T>  the type of the value held by the variable
     * @return a new platform-default context variable
     */
    public static <T> ContextVar<T> newInstance(String name) {
        return ThreadVar.newInstance(name);
    }
    
    /**
     * Starts a binding chain for the specified variable and value.
     *
     * @param variable the variable to bind
     * @param value    the value to assign
     * @param <V>      the type of the value
     * @return a new {@link Binding}
     */
    public static <V> Binding<V> bind(ContextVar<V> variable, V value) {
        return new Binding<V>(null, variable, value);
    }

    /**
     * Returns an empty context variable that performs no operations and holds no state.
     * <p>
     * Reading it via {@link #get()} yields {@code null}, and applying it
     * ({@code runWith} / {@code supplyWith} / {@code callWith}) is a no-op: the wrapped
     * code executes unchanged. Relaying it produces a no-op {@link ContextRelay} that
     * propagates nothing, which makes it a safe identity element when composing relays.
     * </p>
     *
     * @param <T> the type of the value
     * @return an empty context variable
     */
    @SuppressWarnings("unchecked")
    public static <T> ContextVar<T> empty() {
        return (ContextVar<T>)ContextVarHelper.EMPTY;
    }

    /**
     * Creates a {@link ContextRelay} that propagates the specified context variables 
     * across thread boundaries.
     *
     * @param contextVars the variables to relay
     * @return a {@link ContextRelay} encompassing all provided variables
     */
    @SafeVarargs
    public static ContextRelay<Object> relay(ContextVar<?>... contextVars) {
        Objects.requireNonNull(contextVars, "contextVars");
        switch (contextVars.length) {
            case 0: return empty().relay();
            case 1: {
                @SuppressWarnings("unchecked")
                ContextVar<Object> solo = (ContextVar<Object>)contextVars[0]; 
                return solo.relay();
            }
            default: return ContextVarHelper.cast(ContextVarHelper.merge(contextVars)).relay(); 
        }
    }

    /**
     * Creates a {@link ContextRelay} that propagates the specified set of context variables 
     * across thread boundaries.
     *
     * @param contextVars the set of variables to relay
     * @return a {@link ContextRelay} encompassing all provided variables
     */
    public static ContextRelay<Object> relay(Set<ContextVar<?>> contextVars) {
        Objects.requireNonNull(contextVars, "contextVars");
        switch (contextVars.size()) {
            case 0: return empty().relay();
            case 1: {
                @SuppressWarnings("unchecked")
                ContextVar<Object> solo = (ContextVar<Object>)contextVars.iterator().next(); 
                return solo.relay();
            }
            default: return ContextVarHelper.cast(ContextVarHelper.merge(contextVars)).relay(); 
        }
    }
}