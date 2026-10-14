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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class ContextVarHelper {
    
    static String generateVarName() {
        return "<anonymous" + COUNTER.getAndIncrement() + ">";
    }

    static <T> void applyValues(List<? extends ModifiableContextVar<? extends T>> variables, List<? extends T> data) {
        Iterator<? extends ContextVar<?>> vars = variables.iterator();
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
    
    @SafeVarargs
    static ContextVar<?> merge(ContextVar<?>... contextVars) {
        Objects.requireNonNull(contextVars, "contextVars");
        switch (contextVars.length) {
            case 0: return ContextVar.empty();
            case 1: return contextVars[0];
            default: return merge(new HashSet<>(Arrays.asList(contextVars))); 
        }
    }
    
    static ContextVar<?> merge(Set<? extends ContextVar<?>> contextVars) {
        Objects.requireNonNull(contextVars, "contextVars");
        switch (contextVars.size()) {
            case 0: return ContextVar.empty();
            case 1: return contextVars.iterator().next();
            default:
                Map<Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>>, List<ContextVar<?>>>
                gfm = new HashMap<>();
            
            for (ContextVar<?> contextVar : contextVars) {
                Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> key = contextVar.groupFactory();
                List<ContextVar<?>> list = gfm.get(key);
                if (list == null) {
                    list = new ArrayList<>();
                    list.add(contextVar);
                    gfm.put(key, list);
                } else {
                    list.addAll(Collections.singletonList(contextVar));
                }
            }
            
            // This groups separate ThreadLocalVar-s and (most importantly) ScopedLocalVar-s
            // into the single ThreadLocalVarGroup and ScopedLocalVarGroup
            List<ContextVar<?>> groupped =
            gfm.entrySet()
               .stream()
               .map(e -> {
                   List<ContextVar<?>> list = e.getValue();
                   if (list.size() == 1) {
                       return list.get(0);
                   }
                   ContextVar<List<?>> group = (ContextVar<List<?>>) e.getKey().apply(list);
                   return group;
               })
               .collect(Collectors.toList());
            
            if (groupped.isEmpty()) {
                return ContextVar.empty();
            } else if (groupped.size() == 1) {
                return groupped.get(0);
            } else {
                return new ContextVarGroup.Strict<>(groupped, true);
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    static ContextVar<Object> cast(ContextVar<?> ctxVar) {
        return (ContextVar<Object>)ctxVar;
    }
    
    static final ContextVar<Object> EMPTY = new ContextVar<Object>() {
        @Override
        public Object get() {
            throw new NoSuchElementException();
        }

        @Override
        public <V> V callWith(Object capturedValue, Callable<V> code) throws Exception {
            return code.call();
        }
        
        @Override
        public void runWith(Object capturedValue, Runnable code) {
            code.run();
        }
        
        @Override
        public <V> V supplyWith(Object capturedValue, Supplier<V> code) {
            return code.get();
        }
        
        @Override
        public String toString() {
            return "<empty-ctx-vars>";
        }
    };
    
    private static final AtomicLong COUNTER = new AtomicLong();
}
