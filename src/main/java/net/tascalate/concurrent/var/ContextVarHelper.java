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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Internal utility class providing helper methods for context variable management, 
 * grouping, and execution wrapping.
 * <p>
 * This class is not intended for direct use by API consumers and contains 
 * package-private utilities used by {@link ContextVar}, {@link ContextVarGroup}, 
 * and related implementations.
 * </p>
 */
final class ContextVarHelper {
    
    private ContextVarHelper() {
    }
    
    /**
     * Generates a unique anonymous name for a context variable.
     *
     * @return a generated name string (e.g., "&lt;anonymous1&gt;")
     */
    static String generateVarName() {
        return "<anonymous" + COUNTER.getAndIncrement() + ">";
    }
    
    /**
     * Iterates over a list of modifiable context variables and applies the corresponding 
     * values from a data list. If a value is {@code null}, the variable is removed.
     *
     * @param variables the list of variables to modify
     * @param data      the list of values to apply
     * @param <T>       the type of the values
     */
    static <T> void applyValues(List<? extends ModifiableContextVar<? extends T>> variables, List<? extends T> data) {
        Iterator<? extends ContextVar<?>> vars = variables.iterator();
        Iterator<? extends T> values = data.iterator();
        while (vars.hasNext() && values.hasNext()) {
            @SuppressWarnings("unchecked")
            ModifiableContextVar<? super T> contextVar = (ModifiableContextVar<Object>)vars.next();
            T contextVal = values.next();
            if (null == contextVal) {
                contextVar.remove();
            } else {
                contextVar.set(contextVal);
            }
        }
    }
    
    /**
     * Wraps a {@link Callable} to execute within the context of a specific variable and value.
     * This is used to build nested execution scopes for {@link ContextVarGroup}.
     *
     * @param contextVar   the context variable
     * @param contextValue the value to apply
     * @param originalCode the original callable to wrap
     * @param <T>          the type of the context value
     * @param <V>          the return type of the callable
     * @return a new {@link Callable} that applies the context before executing the original code
     */
    static <T, V> Callable<V> nextCallable(ContextVar<? super T> contextVar, T contextValue, Callable<V> originalCode) {
        return new Callable<V>() {
            @Override
            public V call() throws Exception {
                return contextVar.callWith(contextValue, originalCode);
            }
        };
    }
    
    /**
     * Maps a list of items using a provided function.
     *
     * @param list   the list to convert
     * @param mapper the mapping function
     * @param <T>    the input type
     * @param <U>    the output type
     * @return a new list containing the mapped items
     */
    static <T, U> List<U> convertList(List<T> list, Function<T, U> mapper) {
        return list.stream().map(mapper).collect(Collectors.toList());
    }
    
    /**
     * Merges multiple context variables into a single logical unit, grouping them by their 
     * {@code groupFactory} to optimize context propagation.
     *
     * @param contextVars the variables to merge
     * @return a single context variable representing the merged group
     */
    @SafeVarargs
    static ContextVar<?> merge(ContextVar<?>... contextVars) {
        Objects.requireNonNull(contextVars, "contextVars");
        switch (contextVars.length) {
            case 0: return ContextVar.empty();
            case 1: return contextVars[0];
            default: return merge(new HashSet<>(Arrays.asList(contextVars))); 
        }
    }
    
    /**
     * Merges a set of context variables into a single logical unit.
     * Variables are grouped by their {@code groupFactory} (e.g., all {@code ThreadLocalVar}s 
     * are grouped into a single {@code ThreadLocalVarGroup}) to minimize context switching overhead.
     *
     * @param contextVars the set of variables to merge
     * @return a single context variable representing the merged group
     */
    static ContextVar<?> merge(Set<? extends ContextVar<?>> contextVars) {
        Objects.requireNonNull(contextVars, "contextVars");
        switch (contextVars.size()) {
            case 0: return ContextVar.empty();
            case 1: return contextVars.iterator().next();
            default:
                Map<Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>>, List<ContextVar<?>>>
                gfm = new HashMap<>();
            for (ContextVar<?> contextVar : contextVars) {
                Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> key = contextVar.groupFactory();
                List<ContextVar<?>> list = gfm.get(key);
                if (list == null) {
                    list = new ArrayList<>();
                    list.add(contextVar);
                    gfm.put(key, list);
                } else {
                    list.addAll(Collections.singletonList(contextVar));
                }
            }
            // This groups separate ThreadLocalVar-s and (most importantly) ScopedLocalVar-s
            // into the single ThreadLocalVarGroup and ScopedLocalVarGroup
            List<ContextVar<?>> groupped =
            gfm.entrySet()
               .stream()
               .map(e -> {
                   List<ContextVar<?>> list = e.getValue();
                   if (list.size() == 1) {
                       return list.get(0);
                   }
                   ContextVar<List<?>> group = (ContextVar<List<?>>) e.getKey().apply(list);
                   return group;
               })
               .collect(Collectors.toList());
            
            if (groupped.isEmpty()) {
                return ContextVar.empty();
            } else if (groupped.size() == 1) {
                return groupped.get(0);
            } else {
                return new ContextVarGroup.Strict<>(groupped, true);
            }
        }
    }
    
    /**
     * Casts a wildcard context variable to {@code ContextVar<Object>}.
     *
     * @param ctxVar the variable to cast
     * @return the casted variable
     */
    @SuppressWarnings("unchecked")
    static ContextVar<Object> cast(ContextVar<?> ctxVar) {
        return (ContextVar<Object>)ctxVar;
    }
    
    /**
     * A singleton empty context variable that performs no operations and holds no state.
     * Used as a placeholder when no context variables are specified.
     */
    static final ContextVar<Object> EMPTY = new ContextVar<Object>() {
        @Override
        public Object get() {
            return null;
        }
        
        @Override
        public <V> V callWith(Object capturedValue, Callable<V> code) throws Exception {
            return code.call();
        }
        
        @Override
        public void runWith(Object capturedValue, Runnable code) {
            code.run();
        }
        
        @Override
        public <V> V supplyWith(Object capturedValue, Supplier<V> code) {
            return code.get();
        }
        
        @Override
        public String toString() {
            return "<empty-ctx-vars>";
        }
    };
    
    /** Counter for generating unique anonymous variable names. */
    private static final AtomicLong COUNTER = new AtomicLong();
}