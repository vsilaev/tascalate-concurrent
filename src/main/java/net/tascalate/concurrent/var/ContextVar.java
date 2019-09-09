/**
 * Copyright 2015-2019 Valery Silaev (http://vsilaev.com)
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
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public interface ContextVar<T> {
    
    T get();
    
    void set(T value);
    
    default void remove() {
        set(null);
    }
    
    public static <T> ContextVar<T> define(Supplier<? extends T> reader, Consumer<? super T> writer) {
        return define(reader, writer, null);
    }
    
    public static <T> ContextVar<T> define(String name, Supplier<? extends T> reader, Consumer<? super T> writer) {
        return define(name, reader, writer, null);
    }
    
    public static <T> ContextVar<T> define(Supplier<? extends T> reader, Consumer<? super T> writer, Runnable eraser) {
        return define(ContextualPromiseFactory.generateVarName(), reader, writer, eraser);
    }
    
    public static <T> ContextVar<T> define(String name, Supplier<? extends T> reader, Consumer<? super T> writer, Runnable eraser) {
        return new ContextVar<T>() {
            @Override
            public T get() { 
                return reader.get();
            }

            @Override
            public void set(T value) {
                writer.accept(value);
            }

            @Override
            public void remove() {
                if (null != eraser) {
                    eraser.run();
                } else {
                    set(null);
                }
            }
            
            @Override
            public String toString() {
                return String.format("<custom-ctx-var>[%s]", name);
            }
        };
    }

    public static <T> ContextVar<T> from(ThreadLocal<T> tl) {
        return new ContextVar<T>() {
            @Override
            public T get() { 
                return tl.get();
            }

            @Override
            public void set(T value) {
                tl.set(value);
            }

            @Override
            public void remove() {
                tl.remove();
            }
            
            @Override
            public String toString() {
                return String.format("<thread-local-ctx-var>[%s]", tl);
            }
        };
    }    
    
    
    public static ContextualPromiseFactory relay(ContextVar<?> contextVar) {
        return new ContextualPromiseFactory(Collections.singletonList(contextVar));
    }
    
    public static ContextualPromiseFactory relay(ThreadLocal<?> threadLocal) {
        return relay(ContextVar.from(threadLocal));
    }

    public static ContextualPromiseFactory relay(ContextVar<?>... contextVars) {
        return new ContextualPromiseFactory(Arrays.asList(contextVars));
    }
    
    public static ContextualPromiseFactory relay(ThreadLocal<?>... threadLocals) {
        return new ContextualPromiseFactory(Arrays.stream(threadLocals).map(ContextVar::from).collect(Collectors.toList()));
    }

    public static ContextualPromiseFactory relay(List<? extends ContextVar<?>> contextVars) {
        return new ContextualPromiseFactory(
            contextVars == null ? Collections.emptyList() : new ArrayList<>(contextVars)
        );
    }
    
    public static ContextualPromiseFactory relayThreadLocals(List<? extends ThreadLocal<?>> threadLocals) {
        return new ContextualPromiseFactory(
            threadLocals == null ? Collections.emptyList() : threadLocals
                .stream()
                .map(tl -> ContextVar.from((ThreadLocal<?>)tl))
                .collect(Collectors.toList())
        );
    }
}
