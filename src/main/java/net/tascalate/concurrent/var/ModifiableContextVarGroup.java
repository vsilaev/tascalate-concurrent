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
import java.util.stream.Collectors;

/**
 * A grouped context variable that manages a collection of {@link ModifiableContextVar}s.
 * <p>
 * Instead of applying context variables one by one, this group applies them collectively.
 * This is particularly useful for optimizing performance when multiple modifiable variables
 * (e.g., multiple {@code ThreadLocal}s) need to be swapped out and restored simultaneously.
 * </p>
 *
 * @param <T> the common supertype of the values held by the grouped variables
 * @see ModifiableContextVar
 */
abstract class ModifiableContextVarGroup<T> extends ModifiableContextVar<List<T>>  {
    
    /** The list of modifiable context variables managed by this group. */
    final List<? extends ModifiableContextVar<? extends T>> vars;
    
    /**
     * Constructs a new group with the specified list of variables.
     *
     * @param vars the variables to group
     */
    ModifiableContextVarGroup(List<? extends ModifiableContextVar<? extends T>> vars) {
         this.vars = vars;
     }
     
     /**
      * Retrieves the current values of all variables in this group.
      *
      * @return a list of current values, in the same order as the variables
      */
     @Override
     public List<T> get() {
         return vars.stream()
                    .map(ContextVar::get)
                    .collect(Collectors.toList());
     } 
     
     /**
      * Sets the values for all variables in this group.
      *
      * @param value the list of values to apply
      */
     @Override
     void set(List<T> value) {
         ContextVarHelper.applyValues(vars, value);
     }
     
     /**
      * Removes the values for all variables in this group.
      */
     @Override
     void remove() {
         vars.forEach(ModifiableContextVar::remove);
     }
     
     /**
      * A "strict" implementation that saves the current state of the thread's context variables,
      * applies the captured values, executes the code, and then restores the original state.
      *
      * @param <T> the type of the values
      */
     static final class Strict<T> extends ModifiableContextVarGroup<T> {
         Strict(List<? extends ModifiableContextVar<? extends T>> vars) {
             // No conversion necessary due to inner logic
             super(vars);
         }
         
         @Override
         public <V> V callWith(List<T> capturedValue, Callable<V> code) throws Exception {
             List<? extends T> previousValues = this.get();
             ContextVarHelper.applyValues(vars, capturedValue);
             try {
                 return code.call();
             } finally {
                 ContextVarHelper.applyValues(vars, previousValues);
             }                
         }
         
         @Override
         public Strict<T> strict() {
             return this;
         }
         
         @Override
         public Optimized<T> optimized() {
             return new Optimized<>(vars);
         }
         
         @Override
         public Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> groupFactory() {
             return ModifiableContextVarGroup.FACTORY_STRICT;
         }
         
         @Override
         public String toString() {
             return String.format("<custom-ctx-vars-strict>{%s}", vars);
         }
     }
     
     /**
      * An "optimized" implementation that applies the captured values, executes the code,
      * and then unconditionally clears (removes) the variables. This is faster than {@link Strict}
      * but does not preserve any pre-existing ambient values on the thread.
      *
      * @param <T> the type of the values
      */
     static final class Optimized<T> extends ModifiableContextVarGroup<T> {
         Optimized(List<? extends ModifiableContextVar<? extends T>> vars) {
             // No conversion necessary due to inner logic
             super(vars);
         }
         
         @Override
         public <V> V callWith(List<T> capturedValue, Callable<V> code) throws Exception {
             ContextVarHelper.applyValues(vars, capturedValue);
             try {
                 return code.call();
             } finally {
                 vars.forEach(ModifiableContextVar::remove);
             }                
         }
         
         @Override
         public Strict<T> strict() {
             return new Strict<>(vars);
         }
         
         @Override
         public Optimized<T> optimized() {
             return this;
         }
         
         @Override
         public String toString() {
             return String.format("<custom-ctx-vars-optimized>{%s}", vars);
         } 
     }
     
     /** Factory for creating {@link Optimized} groups. */
     @SuppressWarnings({ "rawtypes", "unchecked" })
     static final Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> FACTORY_OPTIMIZED = vars -> 
         new ModifiableContextVarGroup.Optimized(vars);
     
     /** Factory for creating {@link Strict} groups. */
     @SuppressWarnings({ "rawtypes", "unchecked" })
     static final Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> FACTORY_STRICT = vars -> 
         new ModifiableContextVarGroup.Strict(vars);
}