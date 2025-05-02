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

import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

abstract class ModifiableContextVarGroup<T> implements ModifiableContextVar<List<? extends T>> {
    final List<? extends ModifiableContextVar<? extends T>> vars;
    
    ModifiableContextVarGroup(List<? extends ModifiableContextVar<? extends T>> vars) {
        this.vars = vars;
    }
    
    @Override
    public List<? extends T> get() {
        return vars.stream()
                   .map(ContextVar::get)
                   .collect(Collectors.toList());
    }
    
    @Override
    public void set(List<? extends T> value) {
        ContextVarHelper.applyValues(vars, value);
    }
    
    @Override
    public void remove() {
        vars.forEach(ModifiableContextVar::remove);
    }
    
    final static class Strict<T> extends ModifiableContextVarGroup<T> {
        
        Strict(List<? extends ModifiableContextVar<? extends T>> vars) {
            // No conversion necessary due to inner logic
            super(vars/*ContextVarHelper.convertList(vars, ModifiableContextVar::strict)*/);
        }
        
        @Override
        public <V> V callWith(List<? extends T> capturedValue, Callable<V> code) throws Exception {
            List<? extends T> previousValues = this.get();
            ContextVarHelper.applyValues(vars, capturedValue);
            try {
                return code.call();
            } finally {
                ContextVarHelper.applyValues(vars, previousValues);
            }                
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
            return String.format("<custom-ctx-vars-strict>%s", vars);
        } 
    }
    
    final static class Optimized<T> extends ModifiableContextVarGroup<T> {
        
        Optimized(List<? extends ModifiableContextVar<? extends T>> vars) {
            // No conversion necessary due to inner logic
            super(vars/*ContextVarHelper.convertList(vars, ModifiableContextVar::optimized)*/);
        }
        
        @Override
        public <V> V callWith(List<? extends T> capturedValue, Callable<V> code) throws Exception {
            ContextVarHelper.applyValues(vars, capturedValue);
            try {
                return code.call();
            } finally {
                vars.forEach(ModifiableContextVar::remove);
            }                
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
            return String.format("<custom-ctx-vars-optimized>%s", vars);
        } 
    }
}
