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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An abstract base class for integrating the ContextVar API with external, custom context storage
 * mechanisms (e.g., Spring's {@code RequestAttributes}, SLF4J's MDC, or proprietary registries).
 * <p>
 * Instead of managing its own internal storage, it delegates read, write, and erase operations
 * to provided {@link java.util.function.Supplier}, {@link java.util.function.Consumer}, and 
 * {@link java.lang.Runnable} functions.
 * </p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ModifiableContextVar<User> currentUser = CustomContextVar.define(
 *     "currentUser",
 *     () -> SecurityContextHolder.getContext().getAuthentication().getPrincipal(),
 *     user -> SecurityContextHolder.getContext().setAuthentication(...),
 *     () -> SecurityContextHolder.clearContext()
 * );
 * }</pre>
 *
 * @param <T> the type of the value held by this custom variable
 * @see ModifiableContextVar
 */
public abstract class CustomContextVar<T> extends ModifiableContextVar<T> {
    
    /** The human-readable name of this variable. */
    final String name;
    
    /** A supplier to read the current value from the external storage. */
    final Supplier<? extends T> reader;
    
    /** A consumer to write a new value to the external storage. */
    final Consumer<? super T> writer; 
    
    /** A runnable to clear the value from the external storage (optional). */
    final Runnable eraser;
    
    /**
     * Constructs a new {@code CustomContextVar} with the specified accessors.
     *
     * @param name   the name of the variable
     * @param reader a supplier to read the current value
     * @param writer a consumer to write a new value
     * @param eraser a runnable to clear the value (may be null)
     */
    CustomContextVar(String name, Supplier<? extends T> reader, Consumer<? super T> writer, Runnable eraser) {
        this.name = name;
        this.reader = reader;
        this.writer = writer;
        this.eraser = eraser;
    }
    
    @Override
    public T get() { 
        return reader.get();
    }
    
    @Override
    void set(T value) {
        writer.accept(value);
    }
    
    @Override
    void remove() {
        if (null != eraser) {
            eraser.run();
        } else {
            set(null);
        }
    }
    
    /**
     * A "strict" implementation that saves the current state of the external storage,
     * applies the captured value, executes the code, and then restores the original state.
     *
     * @param <T> the type of the value
     */
    static final class Strict<T> extends CustomContextVar<T> {
        Strict(String name, Supplier<? extends T> reader, Consumer<? super T> writer, Runnable eraser) {
            super(name, reader, writer, eraser);
        }
        
        @Override
        public <V> V callWith(T capturedValue, Callable<V> code) throws Exception {
            T prevValue =  this.get();
            try {
                return code.call();
            } finally {
                if (null == prevValue) {
                    remove();
                } else {
                    set(prevValue);
                }
            }
        }
        
        @Override
        public Strict<T> strict() {
            return this;
        }
        
        @Override
        public Optimized<T> optimized() {
            return new Optimized<>(name, reader, writer, eraser);
        }
        
        @Override
        public Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> groupFactory() {
            return ModifiableContextVarGroup.FACTORY_STRICT;
        }
        
        @Override
        public String toString() {
            return String.format("<custom-ctx-var-strict>[%s] = %s", name, get());
        }
    }
    
    /**
     * An "optimized" implementation that applies the captured value, executes the code,
     * and then unconditionally clears the external storage. This is faster than {@link Strict}
     * but does not preserve any pre-existing ambient values.
     *
     * @param <T> the type of the value
     */
    static final class Optimized<T> extends CustomContextVar<T> {
        Optimized(String name, Supplier<? extends T> reader, Consumer<? super T> writer, Runnable eraser) {
            super(name, reader, writer, eraser);
        }
        
        @Override
        public <V> V callWith(T capturedValue, Callable<V> code) throws Exception {
            try {
                return code.call();
            } finally {
                remove();
            }
        }
        
        @Override
        public Strict<T> strict() {
            return new Strict<>(name, reader, writer, eraser);
        }
        
        @Override
        public Optimized<T> optimized() {
            return this;
        }
        
        @Override
        public Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> groupFactory() {
            return ModifiableContextVarGroup.FACTORY_OPTIMIZED;
        }
        
        @Override
        public String toString() {
            return String.format("<custom-ctx-var-optimized>[%s] = %s", name, get());
        }
    }
    
    /**
     * Defines a new custom context variable with the specified accessors and an auto-generated name.
     *
     * @param reader a supplier to read the current value
     * @param writer a consumer to write a new value
     * @param <T>    the type of the value
     * @return a new {@link ModifiableContextVar} instance
     */
    public static <T> ContextVar<T> define(Supplier<? extends T> reader, Consumer<? super T> writer) {
        return define(reader, writer, null);
    }
    
    /**
     * Defines a new custom context variable with the specified name and accessors.
     *
     * @param name   the name of the variable
     * @param reader a supplier to read the current value
     * @param writer a consumer to write a new value
     * @param <T>    the type of the value
     * @return a new {@link ModifiableContextVar} instance
     */
    public static <T> ContextVar<T> define(String name, Supplier<? extends T> reader, Consumer<? super T> writer) {
        return define(name, reader, writer, null);
    }
    
    /**
     * Defines a new custom context variable with the specified accessors and an auto-generated name.
     *
     * @param reader a supplier to read the current value
     * @param writer a consumer to write a new value
     * @param eraser a runnable to clear the value
     * @param <T>    the type of the value
     * @return a new {@link ModifiableContextVar} instance
     */
    public static <T> ContextVar<T> define(Supplier<? extends T> reader, Consumer<? super T> writer, Runnable eraser) {
        return define(ContextVarHelper.generateVarName(), reader, writer, eraser);
    }
    
    /**
     * Defines a new custom context variable with the specified name and accessors.
     *
     * @param name   the name of the variable
     * @param reader a supplier to read the current value
     * @param writer a consumer to write a new value
     * @param eraser a runnable to clear the value
     * @param <T>    the type of the value
     * @return a new {@link ModifiableContextVar} instance
     */
    public static <T> ContextVar<T> define(String name, Supplier<? extends T> reader, Consumer<? super T> writer, Runnable eraser) {
        return new Strict<>(name, reader, writer, eraser);
    }
}