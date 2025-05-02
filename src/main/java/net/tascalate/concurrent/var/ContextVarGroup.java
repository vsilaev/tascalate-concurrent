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

class ContextVarGroup<T> implements ContextVar<List<? extends T>> {

    final List<? extends ContextVar<? extends T>> vars;
    
    ContextVarGroup(List<? extends ContextVar<? extends T>> vars) {
        this.vars = vars;
    }
    
    @Override
    public List<? extends T> get() {
        return vars.stream()
                   .map(ContextVar::get)
                   .collect(Collectors.toList());
    }

    @Override
    public <V> V callWith(List<? extends T> capturedValue, Callable<V> code) throws Exception {
        Iterator<? extends ContextVar<?>> varsIterator = vars.iterator();
        Iterator<? extends T> valuesIterator = capturedValue.iterator();
        Callable<V> wrappedCode = code;
        while (varsIterator.hasNext() && valuesIterator.hasNext()) {
            @SuppressWarnings("unchecked")
            ContextVar<? super T> contextVar = (ContextVar<Object>)varsIterator.next();
            T contextVal = valuesIterator.next();
            wrappedCode = ContextVarHelper.nextCallable(contextVar, contextVal, wrappedCode);
        }
        return code.call();            
    }

    @Override
    public String toString() {
        return String.format("<generic-ctx-vars>%s", vars);
    }
    
    static final class Strict<T> extends ContextVarGroup<T> {
        Strict(List<? extends ContextVar<? extends T>> vars) {
            super(ContextVarHelper.convertList(vars, ContextVar::strict));
        }
     
        @Override
        public Strict<T> strict() {
            return this;
        }
        
        @Override
        public Optimized<T> optimized() {
            return new Optimized<>(vars);
        }
        
        @Override
        public String toString() {
            return String.format("<generic-ctx-vars-strict>%s", vars);
        }
    }
    
    static final class Optimized<T> extends ContextVarGroup<T> {
        Optimized(List<? extends ContextVar<? extends T>> vars) {
            super(ContextVarHelper.convertList(vars, ContextVar::optimized));
        }
     
        @Override
        public Strict<T> strict() {
            return new Strict<>(vars);
        }
        
        @Override
        public Optimized<T> optimized() {
            return this;
        }
        
        @Override
        public String toString() {
            return String.format("<generic-ctx-vars-optimized>%s", vars);
        }
    }
}
