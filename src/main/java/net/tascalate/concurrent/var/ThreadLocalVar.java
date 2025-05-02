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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

public abstract class ThreadLocalVar<T> implements ModifiableContextVar<T> {
    final ThreadLocal<T> delegate;
    
    ThreadLocalVar(ThreadLocal<T> delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public T get() { 
        return delegate.get();
    }
    
    @Override
    public void set(T value) {
        delegate.set(value);
    }

    @Override
    public void remove() {
        delegate.remove();
    }
    
    static final class Strict<T> extends ThreadLocalVar<T> {
        
        Strict(ThreadLocal<T> delegate) {
            super(delegate);
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
            return new Optimized<>(delegate);
        }
        
        @Override
        public String toString() {
            return String.format("<thread-local-ctx-var-strict>[%s]", delegate);
        }
        
    }
    
    static final class Optimized<T> extends ThreadLocalVar<T> {
        
        Optimized(ThreadLocal<T> delegate) {
            super(delegate);
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
            return new Strict<>(delegate);
        }
        
        @Override
        public Optimized<T> optimized() {
            return this;
        }
        
        @Override
        public String toString() {
            return String.format("<thread-local-ctx-var-optimized>[%s]", delegate);
        }
        
    }
    
    public static <T> ThreadLocalVar<T> of(ThreadLocal<T> threadLocal) {
        return new ThreadLocalVar.Optimized<>(threadLocal);
    }
    
    @SafeVarargs
    public static <T> ContextVar<List<? extends T>> of(ThreadLocal<? extends T>... tls) {
        return of(Arrays.asList(tls));
    }
    
    public static <T> ContextVar<List<? extends T>> of(List<? extends ThreadLocal<? extends T>> threadLocals) {
        if (null == threadLocals || threadLocals.isEmpty()) {
            return ContextVar.empty();
        } else {
            return new ThreadLocalVarGroup.Optimized<>(threadLocals);
        }
    }

}
