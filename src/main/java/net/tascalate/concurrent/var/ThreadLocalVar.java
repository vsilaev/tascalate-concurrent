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

public abstract class ThreadLocalVar<T> extends ModifiableContextVar<T> {
    final String name;
    final ThreadLocal<T> delegate;
    
    ThreadLocalVar(String name, ThreadLocal<T> delegate) {
        this.name = name;
        this.delegate = delegate;
    }
    
    @Override
    public T get() { 
        return delegate.get();
    }
    
    @Override
    void set(T value) {
        delegate.set(value);
    }

    @Override
    void remove() {
        delegate.remove();
    }
    
    static final class Strict<T> extends ThreadLocalVar<T> {
        
        Strict(String name, ThreadLocal<T> delegate) {
            super(name, delegate);
        }
        
        @Override
        public <V> V callWith(T capturedValue, Callable<V> code) throws Exception {
            T prevValue = this.get();
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
            return String.format("<thread-local-ctx-var-strict>[%s = %s]", name, delegate);
        }
        
    }
    
    static final class Optimized<T> extends ThreadLocalVar<T> {
        
        Optimized(String name, ThreadLocal<T> delegate) {
            super(name, delegate);
        }
        
        @Override
        public <V> V callWith(T capturedValue, Callable<V> code) throws Exception {
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
        public String toString() {
            return String.format("<thread-local-ctx-var-optimized>[%s = %s]", name, delegate);
        }
        
    }
    
    public static <T> ThreadLocalVar<T> newInstance() {
        return newInstance(ContextVarHelper.generateVarName());
    }
    
    public static <T> ThreadLocalVar<T> newInstance(String name) {
        return of(name, new ThreadLocal<>());
    }
    
    public static <T> ThreadLocalVar<T> of(ThreadLocal<T> threadLocal) {
        return of(ContextVarHelper.generateVarName(), threadLocal);
    }
    
    public static <T> ThreadLocalVar<T> of(String name, ThreadLocal<T> threadLocal) {
        return new ThreadLocalVar.Optimized<>(name, threadLocal);
    }
}
