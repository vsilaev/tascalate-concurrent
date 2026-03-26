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
import java.util.function.Supplier;
import java.util.stream.Collectors;

class ScopedValueVarGroup<T> implements ContextVar<List<T>> {
    private final List<? extends ScopedValue<? extends T>> scopedValues;
    
    ScopedValueVarGroup(List<? extends ScopedValue<? extends T>> scopedValues) {
        this.scopedValues = scopedValues;
    }
    
    @Override
    public List<T> get() {
        return scopedValues.stream()
                           .map(v -> v.isBound() ? v.get() : null)  
                           .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return String.format("<scoped-value-ctx-vars>%s", scopedValues);
    } 
    
    @Override
    public void runWith(List<T> capturedValues, Runnable code) {
        var carrier = createCallChain(capturedValues);
        if (null != carrier) {
            carrier.run(code);
        } else {
            code.run();
        }
    }
    
    @Override
    public <V> V supplyWith(List<T> capturedValues, Supplier<V> code) {
        var carrier = createCallChain(capturedValues);
        if (null != carrier) {
            return carrier.call(code::get);
        } else {
            return code.get();
        }
    }
    
    @Override
    public <V> V callWith(List<T> capturedValues, Callable<V> code) throws Exception {
        var carrier = createCallChain(capturedValues);
        if (null != carrier) {
            return carrier.call(code::call);
        } else {
            return code.call();
        }
    }
    
    private ScopedValue.Carrier createCallChain(List<? extends T> capturedValues) {
        ScopedValue.Carrier c = null;
        var scopedValuesIterator = scopedValues.iterator();
        var capturedValuesIterator = capturedValues.iterator();
        while (scopedValuesIterator.hasNext() && capturedValuesIterator.hasNext()) {
            @SuppressWarnings("unchecked")
            ScopedValue<T> scopedValue = (ScopedValue<T>) scopedValuesIterator.next();
            var capturedValue = capturedValuesIterator.next();

            if (null == c) {
                c = ScopedValue.where(scopedValue, capturedValue);
            } else {
                c = c.where(scopedValue, capturedValue);
            }
        }
        return c;
    }

}
