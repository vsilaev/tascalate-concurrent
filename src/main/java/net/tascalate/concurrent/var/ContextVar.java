/**
 * Copyright 2015-2025 Valery Silaev (http://vsilaev.com)
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public interface ContextVar<T> {
    public static class Binding<T> {
        private final Binding<?> previous;
        private final ContextVar<T> variable;
        private final T value;
        
        Binding(Binding<?> previous, ContextVar<T> variable, T value) {
            this.previous = previous;
            this.variable = variable;
            this.value = value;
        }
        
        <V> Binding<V> bind(ContextVar<V> variable, V value) {
            return new Binding<V>(this, variable, value);
        }
        
        public void run(Runnable code) {
            createContextualization(this).runWith(code);
        }
        
        public <V> V supply(Supplier<V> code) {
            return createContextualization(this).supplyWith(code);
        }
        
        public <V> V call(Callable<V> code) throws Exception {
            return createContextualization(this).callWith(code);            
        }
        
        @SuppressWarnings("unchecked")
        private List<VarAssignment<Object>> varBinding() {
            List<VarAssignment<Object>> result = new ArrayList<>(4);
            result.add(new VarAssignment<>((ContextVar<Object>)this.variable, this.value));
            return result;
        }
        
        static Contextualization<?> createContextualization(Binding<?> top) {
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
                return Contextualization.create(new ContextVarGroup.Optimized<>(topGroups, true), topValues);
            }
        }
    }
    
    T get();
    
    default Binding<T> bind(T value) {
        return new Binding<>(null, this, value);
    }
    
    <V> V callWith(T capturedValue, Callable<V> code) throws Exception;

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
    
    default <V> V supplyWith(T capturedValue, Supplier<V> code) {
        try {
            return callWith(capturedValue, code::get);
        } catch (Error | RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    
    default ContextTrampoline<T> relay() {
        return new ContextTrampoline<T>(this);
    }
    
    /**
     * Convert this var to the variant with pessimistic propagation option.
     * Useful for rare cases when thread might have its own default values 
     * of the context variables and they must be restored. 
     * <p>The logic is the following:</p>
     * <ol>
     * <li>Save context variables from the current thread</li>
     * <li>Apply context variables from the snapshot</li>
     * <li>Execute code</li>
     * <li>Restore context variables saved in the step [1]</li>
     * </ol>
     */
    default ContextVar<T> strict() {
        return this;
    }
    
    /**
     * Convert this var to the variant that is optimized for performance
     * <p>The logic is the following:</p>
     * <ol>
     * <li>Apply context variables from the snapshot</li>
     * <li>Execute code</li>
     * <li>Reset context variables</li>
     * </ol>
     */
    default ContextVar<T> optimized() {
        return this;
    }
    
    default Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> groupFactory() {
        return ContextVarGroup.FACTORY;
    }
    
    public static <V> Binding<V> bind(ContextVar<V> variable, V value) {
        return new Binding<V>(null, variable, value);
    }

    @SuppressWarnings("unchecked")
    public static <T> ContextVar<T> empty() {
        return (ContextVar<T>)ContextVarHelper.EMPTY;
    }
    
    @SafeVarargs
    public static <T> ContextVar<List<T>> of(ContextVar<? extends T>... vars) {
        return of(Arrays.asList(vars));
    }
    
    public static <T> ContextVar<List<T>> of(List<? extends ContextVar<? extends T>> vars) {
        if (null == vars || vars.isEmpty()) {
            return ContextVar.empty();
        } else {
            return new ContextVarGroup.Optimized<>(vars);
        }
    }
}