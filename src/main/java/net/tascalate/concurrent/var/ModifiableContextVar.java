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
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface ModifiableContextVar<T> extends ContextVar<T> {
    void set(T value);
    
    default void remove() {
        set(null);
    }
    
    @Override
    default ModifiableContextVar<T> strict() {
        return this;
    }
    
    @Override
    default ModifiableContextVar<T> optimized() {
        return this;
    }
    
    public static <T> ModifiableContextVar<T> define(Supplier<? extends T> reader, Consumer<? super T> writer) {
        return define(reader, writer, null);
    }
    
    public static <T> ModifiableContextVar<T> define(String name, Supplier<? extends T> reader, Consumer<? super T> writer) {
        return define(name, reader, writer, null);
    }
    
    public static <T> ModifiableContextVar<T> define(Supplier<? extends T> reader, Consumer<? super T> writer, Runnable eraser) {
        return define(ContextVarHelper.generateVarName(), reader, writer, eraser);
    }
    
    public static <T> ModifiableContextVar<T> define(String name, Supplier<? extends T> reader, Consumer<? super T> writer, Runnable eraser) {
        return new CustomModifiableContextVar.Optimized<>(name, reader, writer, eraser);
    }
    
    @SafeVarargs
    public static <T> ContextVar<List<? extends T>> of(ModifiableContextVar<? extends T>... vars) {
        return of(Arrays.asList(vars));
    }
    
    public static <T> ContextVar<List<? extends T>> of(List<? extends ModifiableContextVar<? extends T>> vars) {
        if (null == vars || vars.isEmpty()) {
            return ContextVar.empty();
        }
        
        return new ModifiableContextVarGroup.Optimized<T>(vars);
    }
    
}
