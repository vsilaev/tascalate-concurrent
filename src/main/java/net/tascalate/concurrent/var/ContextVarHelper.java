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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

class ContextVarHelper {
    
    static String generateVarName() {
        return "<anonymous" + COUNTER.getAndIncrement() + ">";
    }
    
    static <T> void applyValues(List<? extends ModifiableContextVar<? extends T>> threadLocals, List<? extends T> data) {
        Iterator<? extends ContextVar<?>> vars = threadLocals.iterator();
        Iterator<? extends T> values = data.iterator();
        while (vars.hasNext() && values.hasNext()) {
            @SuppressWarnings("unchecked")
            ModifiableContextVar<? super T> contextVar = (ModifiableContextVar<Object>)vars.next();
            T contextVal = values.next();
            if (null == contextVal) {
                contextVar.remove();
            } else {
                contextVar.set(contextVal);
            }
        }
    }
    
    static <T, V> Callable<V> nextCallable(ContextVar<? super T> contextVar, T contextValue, Callable<V> originalCode) {
        return new Callable<V>() {
            @Override
            public V call() throws Exception {
                return contextVar.callWith(contextValue, originalCode);
            }
        };
    }
    
    static <T, U> List<U> convertList(List<T> list, Function<T, U> mapper) {
        return list.stream().map(mapper).collect(Collectors.toList());
    }
    
    static final ContextVar<Object> EMPTY = new ContextVar<Object>() {
        @Override
        public Object get() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V> V callWith(Object capturedValue, Callable<V> code) throws Exception {
            return code.call();
        }
        
        @Override
        public String toString() {
            return "<empty-ctx-vars>";
        }
    };
    
    private static final AtomicLong COUNTER = new AtomicLong();
}
