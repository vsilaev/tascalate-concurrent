/**
 * Copyright 2015-2020 Valery Silaev (http://vsilaev.com)
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
    
    /**
     * Defines a strategy how context variables are propagated to the execution thread
     * @author vsilaev
     *
     */
    public static enum Propagation {
        /**
         * Default propagation option that is optimized for performance
         * <p>The logic is the following:</p>
         * <ol>
         * <li>Apply context variables from the snapshot</li>
         * <li>Execute code</li>
         * <li>Reset context variables</li>
         * </ol>
         */
        OPTIMIZED,
        /**
         * Pessimistic propagation option for rare cases when thread might have 
         * its own default values of the context variables and they must be restored. 
         * <p>The logic is the following:</p>
         * <ol>
         * <li>Save context variables from the current thread</li>
         * <li>Apply context variables from the snapshot</li>
         * <li>Execute code</li>
         * <li>Restore context variables saved in the step [1]</li>
         * </ol>
         */
        STRICT;
    }
    
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
        return define(ContextTrampoline.generateVarName(), reader, writer, eraser);
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
    
    
    public static ContextTrampoline relay(ContextVar<?> contextVar) {
        return new ContextTrampoline(Collections.singletonList(contextVar));
    }
    
    public static ContextTrampoline relay(ThreadLocal<?> threadLocal) {
        return relay(ContextVar.from(threadLocal));
    }

    public static ContextTrampoline relay(ContextVar<?>... contextVars) {
        return new ContextTrampoline(Arrays.asList(contextVars));
    }
    
    public static ContextTrampoline relay(ThreadLocal<?>... threadLocals) {
        return new ContextTrampoline(Arrays.stream(threadLocals).map(ContextVar::from).collect(Collectors.toList()));
    }

    public static ContextTrampoline relay(List<? extends ContextVar<?>> contextVars) {
        return new ContextTrampoline(
            contextVars == null ? Collections.emptyList() : new ArrayList<>(contextVars)
        );
    }
    
    public static ContextTrampoline relayThreadLocals(List<? extends ThreadLocal<?>> threadLocals) {
        return new ContextTrampoline(
            threadLocals == null ? Collections.emptyList() : threadLocals
                .stream()
                .map(tl -> ContextVar.from((ThreadLocal<?>)tl))
                .collect(Collectors.toList())
        );
    }
}
