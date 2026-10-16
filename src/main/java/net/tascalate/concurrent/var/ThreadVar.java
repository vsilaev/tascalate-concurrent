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

public class ThreadVar<T> extends ModifiableContextVar<T> {
    final String name;
    final ThreadLocal<T> threadLocal; 
    
    ThreadVar(String name, ThreadLocal<T> threadLocal) {
        this.name = name;
        this.threadLocal = threadLocal;
    }
    
    public T get() {
        return threadLocal.get();
    }

    @Override
    void set(T value) {
        threadLocal.set(value);
    }

    @Override
    void remove() {
        threadLocal.remove();
    }

    @Override
    public <V> V callWith(T capturedValue, Callable<V> code) throws Exception {
        T prevValue = threadLocal.get();
        threadLocal.set(capturedValue);
        try {
            return code.call();
        } finally {
            if (null == prevValue) {
                threadLocal.remove();
            } else {
                threadLocal.set(prevValue);
            }
        }
    }

    @Override
    public ThreadVar<T> strict() {
        return this;
    }
    
    @Override
    public ThreadVar<T> optimized() {
        return new Optimized<>(name, threadLocal);
    }
    
    @Override
    public String toString() {
        return String.format("<thread-var-strict>[%s/%s] = %s", name, threadLocal, get());
    }
    
    static final class Optimized<T> extends ThreadVar<T> {
        
        Optimized(String name, ThreadLocal<T> delegate) {
            super(name, delegate);
        }
        
        @Override
        public <V> V callWith(T capturedValue, Callable<V> code) throws Exception {
            threadLocal.set(capturedValue);
            try {
                return code.call();
            } finally {
                threadLocal.remove();
            }
        }
        
        @Override
        public ThreadVar<T> strict() {
            return new ThreadVar<T>(name, threadLocal);
        }
        
        @Override
        public ThreadVar<T> optimized() {
            return this;
        }
        
        @Override
        public Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> groupFactory() {
            return ModifiableContextVarGroup.FACTORY_OPTIMIZED;
        }
        
        @Override
        public String toString() {
            return String.format("<thread-var-optimized>[%s/%s] = %s", name, threadLocal, get());
        }
        
    }
    
    public static <T> ThreadVar<T> newInstance() {
        return newInstance(ContextVarHelper.generateVarName());
    }
    
    public static <T> ThreadVar<T> newInstance(String name) {
        return new ThreadVar<>(name, new ThreadLocal<>());
    }
    
}
