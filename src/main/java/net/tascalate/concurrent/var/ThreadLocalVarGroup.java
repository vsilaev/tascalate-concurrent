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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A grouped context variable that manages a collection of {@link java.lang.ThreadLocal}s.
 * <p>
 * This class optimizes the process of applying and restoring multiple {@code ThreadLocal} 
 * values simultaneously. By grouping them, the overhead of context switching is reduced, 
 * and the state of the thread can be saved and restored in a single batch operation.
 * </p>
 *
 * @param <T> the common supertype of the values held by the grouped thread locals
 * @see ThreadLocalVar
 */
abstract class ThreadLocalVarGroup<T> extends ModifiableContextVar<List<T>> {
    
    /** The list of underlying {@link ThreadLocal} delegates. */
    List<? extends ThreadLocal<? extends T>> threadLocals;
    
    /**
     * Constructs a new group with the specified list of thread locals.
     *
     * @param threadLocals the thread locals to group
     */
    ThreadLocalVarGroup(List<? extends ThreadLocal<? extends T>> threadLocals) {
        this.threadLocals = threadLocals;
    }
    
    /**
     * Retrieves the current values of all thread locals in this group.
     *
     * @return a list of current values, in the same order as the thread locals
     */
    @Override
    public List<T> get() {
        return threadLocals.stream()
                           .map(ThreadLocal::get)
                           .collect(Collectors.toList());
    }
    
    /**
     * Sets the values for all thread locals in this group.
     *
     * @param value the list of values to apply
     */
    @Override
    void set(List<T> value) {
        applyValues(threadLocals, value);
    }
    
    /**
     * Removes the values for all thread locals in this group.
     */
    @Override
    void remove() {
        threadLocals.forEach(ThreadLocal::remove);
    }
    
    /**
     * A "strict" implementation that saves the current state of the thread's context variables,
     * applies the captured values, executes the code, and then restores the original state.
     *
     * @param <T> the type of the values
     */
    static final class Strict<T> extends ThreadLocalVarGroup<T> {
        Strict(List<? extends ThreadLocal<? extends T>> threadLocals) {
            super(threadLocals);
        }
        
        @Override
        public <V> V callWith(List<T> capturedValue, Callable<V> code) throws Exception {
            List<? extends T> previousValues = this.get();
            applyValues(threadLocals, capturedValue);
            try {
                return code.call();
            } finally {
                applyValues(threadLocals, previousValues);
            }                
        }
        
        @Override
        public Strict<T> strict() {
            return this;
        }
        
        @Override
        public Optimized<T> optimized() {
            return new Optimized<>(threadLocals);
        }
        
        @Override
        public Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> groupFactory() {
            return ModifiableContextVarGroup.FACTORY_STRICT;
        }
        
        @Override
        public String toString() {
            return String.format("<thread-local-ctx-vars-strict>[%s] = %s", threadLocals, get());
        }
    }
    
    /**
     * An "optimized" implementation that applies the captured values, executes the code,
     * and then unconditionally clears (removes) the variables. This is faster than {@link Strict}
     * but does not preserve any pre-existing ambient values on the thread.
     *
     * @param <T> the type of the values
     */
    static final class Optimized<T> extends ThreadLocalVarGroup<T> {
        Optimized(List<? extends ThreadLocal<? extends T>> threadLocals) {
            super(threadLocals);
        }
        
        @Override
        public <V> V callWith(List<T> capturedValue, Callable<V> code) throws Exception {
            applyValues(threadLocals, capturedValue);
            try {
                return code.call();
            } finally {
                threadLocals.forEach(ThreadLocal::remove);
            }                
        }
        
        @Override
        public Strict<T> strict() {
            return new Strict<>(threadLocals);
        }
        
        @Override
        public Optimized<T> optimized() {
            return this;
        }
        
        @Override
        public String toString() {
            return String.format("<thread-local-ctx-vars-optimized>[%s] = %s", threadLocals, get());
        }
    }
    
    /**
     * Internal helper to apply a list of values to a list of thread locals.
     * If a value is {@code null}, the corresponding thread local is removed.
     *
     * @param threadLocals the thread locals to modify
     * @param data         the values to apply
     * @param <T>          the type of the values
     */
    private static <T> void applyValues(List<? extends ThreadLocal<? extends T>> threadLocals, List<? extends T> data) {
        Iterator<? extends ThreadLocal<?>> vars = threadLocals.iterator();
        Iterator<? extends T> values = data.iterator();
        while (vars.hasNext() && values.hasNext()) {
            @SuppressWarnings("unchecked")
            ThreadLocal<? super T> threadLocal = (ThreadLocal<Object>)vars.next();
            T contextVal = values.next();
            if (null == contextVal) {
                threadLocal.remove();
            } else {
                threadLocal.set(contextVal);
            }
        }
    }
    
    /** Factory for creating {@link Optimized} groups from a list of variables. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static final Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> FACTORY_OPTIMIZED = vars -> {
        List<ThreadLocal<?>> tls = new ArrayList<>();
        vars.forEach(v -> {
            ThreadLocalVar<?> tv = (ThreadLocalVar<?>)v;
            tls.add(tv.delegate);
        });
        return new ThreadLocalVarGroup.Optimized(tls);
    };
    
    /** Factory for creating {@link Strict} groups from a list of variables. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static final Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> FACTORY_STRICT = vars -> {
        List<ThreadLocal<?>> tls = new ArrayList<>();
        vars.forEach(v -> {
            ThreadLocalVar<?> tv = (ThreadLocalVar<?>)v;
            tls.add(tv.delegate);
        });
        return new ThreadLocalVarGroup.Strict(tls);
    };
}