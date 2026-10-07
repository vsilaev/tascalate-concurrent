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
package net.tascalate.concurrent.var;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

public final class ThreadVar<T> implements ContextVar<T> {
    private final ScopedValue<Object> scopedValue = ScopedValue.newInstance();
    private final String name;
    
    public ThreadVar(String name) {
        this.name = name;
    }
    
    public T get() {
        Object result = scopedValue.orElse(NULL_SENTINEL);
        return unwrap(result);
    }

    public void runWith(T newValue, Runnable code) {
        ScopedValue.where(scopedValue, wrap(newValue)).run(code);
    }
    
    public <V> V supplyWith(T newValue, Supplier<V> supplier) {
        return ScopedValue.where(scopedValue, wrap(newValue)).call(supplier::get);
    }
    
    public <V> V callWith(T newValue, Callable<V> callable) throws Exception {
        return ScopedValue.where(scopedValue, wrap(newValue)).call(callable::call);
    }
    
    @Override
    public String toString() {
        return getClass().getName() + '[' + name + ']';
    }
    
    private static final Object NULL_SENTINEL = new Object();

    private static Object wrap(Object value) {
        return value == null ? NULL_SENTINEL : value;
    }

    @SuppressWarnings("unchecked")
    private T unwrap(Object raw) {
        return raw == NULL_SENTINEL ? null : (T) raw;
    }
}
