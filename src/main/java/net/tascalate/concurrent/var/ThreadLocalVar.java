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

/**
 * A {@link ContextVar} implementation backed by a standard {@link java.lang.ThreadLocal}.
 * <p>
 * This class provides thread-bound context storage. It is available in the standard 
 * source set (JDK 1.8 - 24). In the Java 25+ source set, the {@code ThreadVar} class 
 * is backed by {@code ScopedValue} instead.
 * </p>
 * <p>
 * Instances can be created using the static factory methods {@link #newInstance()} 
 * or by wrapping an existing {@link ThreadLocal} using {@link #of(ThreadLocal)}.
 * </p>
 *
 * @param <T> the type of the value held by this context variable
 * @see ModifiableContextVar
 */
public abstract class ThreadLocalVar<T> extends ModifiableContextVar<T> {
    
    /** The human-readable name of this variable (useful for debugging). */
    final String name;
    
    /** The underlying {@link ThreadLocal} delegate. */
    final ThreadLocal<T> delegate;
    
    /**
     * Constructs a new {@code ThreadLocalVar} with the specified name and delegate.
     *
     * @param name     the name of the variable
     * @param delegate the underlying {@link ThreadLocal}
     */
    ThreadLocalVar(String name, ThreadLocal<T> delegate) {
         this.name = name;
         this.delegate = delegate;
     }
     
     /**
      * Returns the current value of this context variable for the calling thread.
      *
      * @return the current value, or null if not set
      */
     @Override
     public T get() { 
         return delegate.get();
     }
     
     /**
      * Sets the value of this context variable for the current thread.
      *
      * @param value the new value to set
      */
     @Override
     void set(T value) {
         delegate.set(value);
     }
     
     /**
      * Removes the value of this context variable for the current thread.
      */
     @Override
     void remove() {
         delegate.remove();
     }
     
     /**
      * A "strict" implementation that saves the current state of the thread's context variable,
      * applies the captured value, executes the code, and then restores the original state.
      *
      * @param <T> the type of the value
      */
     static final class Strict<T> extends ThreadLocalVar<T> {
         Strict(String name, ThreadLocal<T> delegate) {
             super(name, delegate);
         }
         
         @Override
         public <V> V callWith(T capturedValue, Callable<V> code) throws Exception {
             T prevValue = this.get();
             delegate.set(capturedValue);
             try {
                 return code.call();
             } finally {
                 if (null == prevValue) {
                     delegate.remove();
                 } else {
                     delegate.set(prevValue);
                 }
             }                
         }
         
         @Override
         public Strict<T> strict() {
             return this;
         }
         
         @Override
         public Optimized<T> optimized() {
             return new Optimized<>(name, delegate);
         }
         
         @Override
         public Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> groupFactory() {
             return ModifiableContextVarGroup.FACTORY_STRICT;
         }
         
         @Override
         public String toString() {
             return String.format("<thread-local-var-strict>[%s/%s] = %s", name, delegate, get());
         }
     }
     
     /**
      * An "optimized" implementation that applies the captured value, executes the code,
      * and then unconditionally clears (removes) the variable. This is faster than {@link Strict}
      * but does not preserve any pre-existing ambient values on the thread.
      *
      * @param <T> the type of the value
      */
     static final class Optimized<T> extends ThreadLocalVar<T> {
         Optimized(String name, ThreadLocal<T> delegate) {
             super(name, delegate);
         }
         
         @Override
         public <V> V callWith(T capturedValue, Callable<V> code) throws Exception {
             delegate.set(capturedValue);
             try {
                 return code.call();
             } finally {
                 delegate.remove();
             }                
         }
         
         @Override
         public Strict<T> strict() {
             return new Strict<>(name, delegate);
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
             return String.format("<thread-local-var-optimized>[%s/%s] = %s", name, delegate, get());
         }
     }
     
     /**
      * Creates a new instance of {@code ThreadLocalVar} with an auto-generated anonymous name.
      *
      * @param <T> the type of the value
      * @return a new {@code ThreadLocalVar} instance
      */
     public static <T> ContextVar<T> newInstance() {
         return newInstance(ContextVarHelper.generateVarName());
     }
     
     /**
      * Creates a new instance of {@code ThreadLocalVar} with the specified name.
      *
      * @param name the name of the variable
      * @param <T>  the type of the value
      * @return a new {@code ThreadLocalVar} instance
      */
     public static <T> ContextVar<T> newInstance(String name) {
         return of(name, new ThreadLocal<>());
     }
     
     /**
      * Wraps an existing {@link ThreadLocal} as a {@code ThreadLocalVar} with an auto-generated name.
      *
      * @param threadLocal the thread local to wrap
      * @param <T>         the type of the value
      * @return a new {@code ThreadLocalVar} instance
      */
     public static <T> ContextVar<T> of(ThreadLocal<T> threadLocal) {
         return of(ContextVarHelper.generateVarName(), threadLocal);
     }
     
     /**
      * Wraps an existing {@link ThreadLocal} as a {@code ThreadLocalVar} with the specified name.
      *
      * @param name        the name of the variable
      * @param threadLocal the thread local to wrap
      * @param <T>         the type of the value
      * @return a new {@code ThreadLocalVar} instance
      */
     public static <T> ContextVar<T> of(String name, ThreadLocal<T> threadLocal) {
         return new ThreadLocalVar.Strict<>(name, threadLocal);
     }
}