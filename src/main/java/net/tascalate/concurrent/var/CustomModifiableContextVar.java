
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

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;

abstract class CustomModifiableContextVar<T> implements ModifiableContextVar<T> {
    final String name;
    
    final Supplier<? extends T> reader;
    final Consumer<? super T> writer; 
    final Runnable eraser;
    
    CustomModifiableContextVar(String name, Supplier<? extends T> reader, Consumer<? super T> writer, Runnable eraser) {
        this.name = name;
        this.reader = reader;
        this.writer = writer;
        this.eraser = eraser;
    }
    
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
    
    static final class Strict<T> extends CustomModifiableContextVar<T> {
        Strict(String name, Supplier<? extends T> reader, Consumer<? super T> writer, Runnable eraser) {
            super(name, reader, writer, eraser);
        }
        
        @Override
        public <V> V callWith(T capturedValue, Callable<V> code) throws Exception {
            T prevValue =  this.get();
            try {
                return code.call();
            } finally {
                if (null == prevValue) {
                    remove();
                } else {
                    set(prevValue);
                }
            }
        }
        
        @Override
        public Strict<T> strict() {
            return this;
        }
        
        @Override
        public Optimized<T> optimized() {
            return new Optimized<>(name, reader, writer, eraser);
        }
        
        @Override
        public String toString() {
            return String.format("<custom-ctx-var-strict>[%s]", name);
        }
    }
    
    static final class Optimized<T> extends CustomModifiableContextVar<T> {
        Optimized(String name, Supplier<? extends T> reader, Consumer<? super T> writer, Runnable eraser) {
            super(name, reader, writer, eraser);
        }
        
        @Override
        public <V> V callWith(T capturedValue, Callable<V> code) throws Exception {
            try {
                return code.call();
            } finally {
                remove();
            }
        }
        
        @Override
        public Strict<T> strict() {
            return new Strict<>(name, reader, writer, eraser);
        }
        
        @Override
        public Optimized<T> optimized() {
            return this;
        }
        
        @Override
        public String toString() {
            return String.format("<custom-ctx-var-optimized>[%s]", name);
        }
    }
}
