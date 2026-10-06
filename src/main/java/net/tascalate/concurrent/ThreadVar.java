/**
 * Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.concurrent;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

public final class ThreadVar<T> {
    private final ThreadLocal<T> threadLocal = new ThreadLocal<>(); 
    private final String name;
    
    public ThreadVar(String name) {
        this.name = name;
    }
    
    public T value() {
        return threadLocal.get();
    }

    public void runWith(T newValue, Runnable code) {
        runWith(value(), newValue, code);
    }
    
    public void runWith(T oldValue, T newValue, Runnable code) {
        threadLocal.set(newValue);
        try {
            code.run();
        } finally {
            reset(oldValue);
        }        
    }
    
    public <V> V supplyWith(T newValue, Supplier<V> supplier) {
        return supplyWith(value(), newValue, supplier);
    }
    
    public <V> V supplyWith(T oldValue, T newValue, Supplier<V> supplier) {
        threadLocal.set(newValue);
        try {
            return supplier.get();
        } finally {
            reset(oldValue);
        }
    }
    
    public <V> V callWith(T newValue, Callable<V> callable) throws Exception {
        return callWith(value(), newValue, callable);
    }
    
    public <V> V callWith(T oldValue, T newValue, Callable<V> callable) throws Exception {
        threadLocal.set(newValue);
        try {
            return callable.call();
        } finally {
            reset(oldValue);
        }
    }
    
    private void reset(T previous) {
        if (null == previous) {
            threadLocal.remove();
        } else {
            threadLocal.set(previous);
        }
    }
    
    @Override
    public String toString() {
        return getClass().getName() + '[' + name + ']';
    }
    
}
