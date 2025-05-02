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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

abstract class ThreadLocalVarGroup<T> implements ModifiableContextVar<List<? extends T>> {

    List<? extends ThreadLocal<? extends T>> threadLocals;
    
    ThreadLocalVarGroup(List<? extends ThreadLocal<? extends T>> threadLocals) {
        this.threadLocals = threadLocals;
    }
    
    @Override
    public List<? extends T> get() {
        return threadLocals.stream()
                           .map(ThreadLocal::get)
                           .collect(Collectors.toList());
    }
    
    @Override
    public void set(List<? extends T> value) {
        applyValues(threadLocals, value);
    }
    
    @Override
    public void remove() {
        threadLocals.forEach(ThreadLocal::remove);
    }
    
    
    static final class Strict<T> extends ThreadLocalVarGroup<T> {

        Strict(List<? extends ThreadLocal<? extends T>> threadLocals) {
            super(threadLocals);
        }

        @Override
        public <V> V callWith(List<? extends T> capturedValue, Callable<V> code) throws Exception {
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
        public String toString() {
            return String.format("<thread-local-ctx-vars-strict>%s", threadLocals);
        }
        
    }
    
    static final class Optimized<T> extends ThreadLocalVarGroup<T> {

        Optimized(List<? extends ThreadLocal<? extends T>> threadLocals) {
            super(threadLocals);
        }

        @Override
        public <V> V callWith(List<? extends T> capturedValue, Callable<V> code) throws Exception {
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
            return String.format("<thread-local-ctx-vars-optimized>%s", threadLocals);
        }
        
    }

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
}
