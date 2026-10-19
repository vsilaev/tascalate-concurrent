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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A grouped context variable that manages a heterogeneous collection of {@link ContextVar}s.
 * <p>
 * Unlike {@link ModifiableContextVarGroup} which applies values directly to mutable storage,
 * this group delegates the execution wrapping to each individual {@code ContextVar}'s 
 * {@code callWith} method. This allows mixing different types of context variables 
 * (e.g., a {@code ThreadLocal} and a {@code ScopedValue}) into a single logical unit
 * for context propagation.
 * </p>
 *
 * @param <T> the common supertype of the values held by the grouped variables
 * @see ContextVar
 */
abstract class ContextVarGroup<T> implements ContextVar<List<T>> {
    
    /** The list of context variables managed by this group. */
    final List<? extends ContextVar<? extends T>> vars;
    
    /**
     * Constructs a new group with the specified list of variables.
     *
     * @param vars the variables to group
     */
     ContextVarGroup(List<? extends ContextVar<? extends T>> vars) {
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
      * Executes the given code with the captured values applied to all variables in the group.
      * <p>
      * The implementation iterates through the variables and wraps the callable recursively,
      * effectively creating a nested execution context for each variable.
      * </p>
      *
      * @param capturedValue the list of values to apply
      * @param code          the code to execute
      * @param <V>           the return type of the callable
      * @return the result of the callable
      * @throws Exception if the callable throws an exception
      */
     @Override
     public <V> V callWith(List<T> capturedValue, Callable<V> code) throws Exception {
         Iterator<? extends ContextVar<?>> varsIterator = vars.iterator();
         Iterator<? extends T> valuesIterator = capturedValue.iterator();
         Callable<V> wrappedCode = code;
         while (varsIterator.hasNext() && valuesIterator.hasNext()) {
             @SuppressWarnings("unchecked")
             ContextVar<? super T> contextVar = (ContextVar<Object>)varsIterator.next();
             T contextVal = valuesIterator.next();
             wrappedCode = ContextVarHelper.nextCallable(contextVar, contextVal, wrappedCode);
         }
         return wrappedCode.call();            
     }
     
     /**
      * A "strict" variant of the context variable group.
      * Converts all child variables to their strict equivalents.
      *
      * @param <T> the type of the values
      */
     static final class Strict<T> extends ContextVarGroup<T> {
         Strict(List<? extends ContextVar<? extends T>> vars) {
             super(ContextVarHelper.convertList(vars, ContextVar::strict));
         }
         
         Strict(List<? extends ContextVar<? extends T>> vars, boolean noTransform) {
             super(vars);
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
         public String toString() {
             return String.format("<generic-ctx-vars-strict>{%s}", vars);
         }
     }
     
     /**
      * An "optimized" variant of the context variable group.
      * Converts all child variables to their optimized equivalents.
      *
      * @param <T> the type of the values
      */
     static final class Optimized<T> extends ContextVarGroup<T> {
         Optimized(List<? extends ContextVar<? extends T>> vars) {
             super(ContextVarHelper.convertList(vars, ContextVar::optimized));
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
             return String.format("<generic-ctx-vars-optimized>{%s}", vars);
         }
     }
     
     /** Default factory for creating {@link Optimized} groups. */
     @SuppressWarnings({ "rawtypes", "unchecked" })
     static final Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> FACTORY = vars -> 
         new ContextVarGroup.Optimized(vars);
}