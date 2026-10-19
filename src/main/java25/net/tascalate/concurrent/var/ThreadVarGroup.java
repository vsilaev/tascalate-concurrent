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
 * A grouped context variable that batches the portable variables ({@link ThreadVar})
 * of the JDK 25+ source set, binding all of their underlying {@link ScopedValue}s
 * within a <b>single</b> {@link ScopedValue.Carrier} scope.
 *
 * <h2>Why grouping exists</h2>
 * <p>
 * When several portable variables are bound together in a {@link ContextVar.Binding}
 * chain or relayed together via {@link ContextVar#relay(ContextVar...)},
 * {@link ThreadVar#groupFactory()} routes them here instead of applying them one by
 * one. All values are then bound atomically in one carrier chain &mdash;
 * {@code ScopedValue.where(sv1, v1).where(sv2, v2)…} &mdash; rather than via nested
 * scopes, which is both cheaper and keeps the binding/unbinding of the whole set atomic.
 * </p>
 *
 * <h2>Separation from mutable groups</h2>
 * <p>
 * {@link #FACTORY} is a <b>different grouping key</b> from
 * {@link ModifiableContextVarGroup#FACTORY_STRICT} / {@code FACTORY_OPTIMIZED}.
 * This separation is essential: {@code ThreadVar} on JDK 25+ is immutable
 * ({@code set}/{@code remove} throw {@link UnsupportedOperationException}), so it must
 * never be routed into {@link ModifiableContextVarGroup}, whose apply/restore machinery
 * mutates its members. The distinct key guarantees that portable variables are always
 * batched among themselves, while genuinely mutable variables
 * ({@link ThreadLocalVar}, {@link CustomContextVar}) continue to share
 * {@code ModifiableContextVarGroup}.
 * </p>
 *
 * <h2>Null support</h2>
 * <p>
 * {@link ScopedValue} does not accept {@code null}. Values are therefore encoded with
 * {@link ThreadVar#wrap(Object)} (mapping {@code null} to {@link ThreadVar#NULL_SENTINEL})
 * when binding, and decoded with {@link ThreadVar#unwrap(Object)} when reading. Binding
 * {@code null} is legal; {@link #get()} reports both a bound {@code null} and an unbound
 * variable as {@code null}, matching the observable contract of the
 * {@link ThreadLocal}-backed base-JDK implementation.
 * </p>
 *
 * @param <T> the common supertype of the values held by the grouped variables
 * @see ThreadVar
 * @see ScopedValue.Carrier
 */
final class ThreadVarGroup<T> implements ContextVar<List<T>> {

    /**
     * The underlying scoped values, one per grouped {@link ThreadVar}.
     * Declared with an {@code Object} payload so that {@link ThreadVar#NULL_SENTINEL}
     * can be stored for bound {@code null} values.
     */
    private final List<? extends ScopedValue<Object>> scopedValues;

    /**
     * Creates a group over the given scoped values.
     * <p>Package-private: instances are produced exclusively by {@link #FACTORY}.</p>
     *
     * @param scopedValues the scoped values to bind collectively, in group order
     */
    ThreadVarGroup(List<? extends ScopedValue<Object>> scopedValues) {
         this.scopedValues = scopedValues;
     }

     /**
      * {@inheritDoc}
      * <p>
      * Returns the current values in group order. Uses
      * {@link ScopedValue#orElse(Object)} with {@link ThreadVar#NULL_SENTINEL} rather
      * than {@link ScopedValue#get()}, so reading a group with unbound members never
      * throws; unbound and bound-{@code null} members are both reported as {@code null}.
      * </p>
      */
     @Override
     public List<T> get() {
         return scopedValues.stream()
                            .map(v -> ThreadVar.<T>unwrap(v.orElse(ThreadVar.NULL_SENTINEL)))  
                            .collect(Collectors.toList());
     }

     /**
      * Returns a diagnostic representation including the scoped values and the current
      * values.
      * <p><b>Caution:</b> the values may be sensitive; avoid logging this in production.</p>
      */
     @Override
     public String toString() {
         return String.format("<thread-vars>[%s] = %s", scopedValues, get());
     } 

     /**
      * Runs the code with all captured values bound within one {@link ScopedValue.Carrier}
      * scope. All previous bindings are restored automatically when the scope completes,
      * including exceptional exit. Threads started inside the call observe every binding.
      * <p>If the group is empty, the code runs directly without a carrier.</p>
      *
      * @param capturedValues the values to bind, aligned with the group's scoped values;
      *                       entries may be {@code null}
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
      * Supplies a value with all captured values bound within one
      * {@link ScopedValue.Carrier} scope; bindings are restored on every exit path.
      * <p>If the group is empty, the supplier runs directly.</p>
      *
      * @param capturedValues the values to bind, aligned with the group's scoped values;
      *                       entries may be {@code null}
      * @param code           the code to execute within the combined binding scope
      * @param <V>            the result type
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
      * Executes the callable with all captured values bound within one
      * {@link ScopedValue.Carrier} scope; bindings are restored on every exit path.
      * <p>If the group is empty, the callable runs directly.</p>
      *
      * @param capturedValues the values to bind, aligned with the group's scoped values;
      *                       entries may be {@code null}
      * @param code           the code to execute within the combined binding scope
      * @param <V>            the result type
      * @return the result of the callable
      * @throws Exception if the callable throws; all bindings are still restored
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
      * Builds a single {@link ScopedValue.Carrier} binding every scoped value in this
      * group to its corresponding captured value, encoding {@code null} values with
      * {@link ThreadVar#wrap(Object)}.
      * <p>
      * If the two lists differ in size, only the common prefix is bound &mdash; the
      * grouping machinery always produces aligned pairs, so this is defensive only.
      * </p>
      *
      * @param capturedValues the values to bind, aligned with {@link #scopedValues}
      * @return a carrier holding all bindings, or {@code null} if the group is empty
      */
     private ScopedValue.Carrier createCallChain(List<? extends T> capturedValues) {
         ScopedValue.Carrier c = null;
         var scopedValuesIterator = scopedValues.iterator();
         var capturedValuesIterator = capturedValues.iterator();
         while (scopedValuesIterator.hasNext() && capturedValuesIterator.hasNext()) {
             ScopedValue<Object> scopedValue = scopedValuesIterator.next();
             var capturedValue = capturedValuesIterator.next();
             if (null == c) {
                 c = ScopedValue.where(scopedValue, ThreadVar.wrap(capturedValue));
             } else {
                 c = c.where(scopedValue, ThreadVar.wrap(capturedValue));
             }
         }
         return c;
     }

     /**
      * Grouping factory registered by {@link ThreadVar#groupFactory()} on JDK 25+.
      * <p>
      * Casts every member to {@link ThreadVar} and extracts its backing scoped value.
      * The cast is safe by construction: the merge machinery in
      * {@link ContextVar.Binding} and {@link ContextVarHelper} buckets variables by
      * their {@code groupFactory()} key, so only {@code ThreadVar}s ever reach this
      * factory.
      * </p>
      */
     @SuppressWarnings({ "rawtypes", "unchecked" })
     static final Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> FACTORY = vars -> {
         List<ScopedValue<Object>> svs = new ArrayList<>();
         vars.forEach(v -> {
             ThreadVar<?> tv = (ThreadVar<?>)v;
             svs.add(tv.scopedValue);
         });
         return new ThreadVarGroup(svs);
     };
}