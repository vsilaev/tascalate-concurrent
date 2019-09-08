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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import net.tascalate.concurrent.DependentPromise;
import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.decorators.CustomizableDependentPromiseDecorator;
import net.tascalate.concurrent.decorators.CustomizablePromiseDecorator;
import net.tascalate.concurrent.decorators.PromiseCustomizer;

class ContextualPromiseCustomizer implements PromiseCustomizer {
    
    private final List<? extends ContextVar<?>> contextVars;
    
    private List<Object> capturedContext;
    
    private ContextualPromiseCustomizer(List<? extends ContextVar<?>> contextVars) {
        this.contextVars = null == contextVars ? 
            Collections.emptyList() : 
            Collections.unmodifiableList(contextVars);
    }
    
    ContextualPromiseCustomizer captureCurrentContext() {
        capturedContext = captureContextVars();
        return this;
    }
    
    
    
    @Override
    public Runnable wrapArgument(Runnable original, boolean async) {
        return () -> {
            List<Object> originalContext = captureContextVars(); 
            restoreContextVars(capturedContext);
            try {
                original.run();
            } finally {
                restoreContextVars(originalContext);
            }
        };

    }

    @Override
    public <U, R> Function<U, R> wrapArgument(Function<U, R> original, boolean async, boolean isCompose) {
        return u -> {
            List<Object> originalContext = captureContextVars(); 
            restoreContextVars(capturedContext);
            try {
                return original.apply(u);
            } finally {
                restoreContextVars(originalContext);
            }
        };
    }

    @Override
    public <U> Consumer<U> wrapArgument(Consumer<U> original, boolean async) {
        return u -> {
            List<Object> originalContext = captureContextVars(); 
            restoreContextVars(capturedContext);
            try {
                original.accept(u);
            } finally {
                restoreContextVars(originalContext);
            }
        };
    }

    @Override
    public <U> Supplier<U> wrapArgument(Supplier<U> original, boolean async) {
        return () -> {
            List<Object> originalContext = captureContextVars(); 
            restoreContextVars(capturedContext);
            try {
                return original.get();
            } finally {
                restoreContextVars(originalContext);
            }
        };
    }

    @Override
    public <U, V, R> BiFunction<U, V, R> wrapArgument(BiFunction<U, V, R> original, boolean async) {
        return (u, v) -> {
            List<Object> originalContext = captureContextVars(); 
            restoreContextVars(capturedContext);
            try {
                return original.apply(u, v);
            } finally {
                restoreContextVars(originalContext);
            }
        };
    }

    @Override
    public <U, V> BiConsumer<U, V> wrapArgument(BiConsumer<U, V> original, boolean async) {
        return (u, v) -> {
            List<Object> originalContext = captureContextVars(); 
            restoreContextVars(capturedContext);
            try {
                original.accept(u, v);
            } finally {
                restoreContextVars(originalContext);
            }
        };
    }

    /*
    @Override
    public <U> CompletionStage<U> wrapArgument(CompletionStage<U> original, boolean async) {
        // TODO Option to wrap original CompletionStage?
        return PromiseCustomizer.super.wrapArgument(original, async);
    }
    */

    static <T> Function<? super Promise<T>, Promise<T>> relayContextVars(List<? extends ContextVar<?>> contextVars) {
        if (null == contextVars || contextVars.isEmpty()) {
            return Function.identity();
        }
        
        PromiseCustomizer customizer = new ContextualPromiseCustomizer(contextVars).captureCurrentContext();
        return p ->
            p instanceof DependentPromise ?
                new CustomizableDependentPromiseDecorator<>((DependentPromise<T>)p, customizer)
                :
                new CustomizablePromiseDecorator<>(p, customizer);
    }
    
    private List<Object> captureContextVars() {
        return contextVars.stream().map(v -> v.get()).collect(Collectors.toList());
    }
    
    private void restoreContextVars(List<Object> contextState) {
        Iterator<? extends ContextVar<?>> vars = contextVars.iterator();
        Iterator<Object> values = contextState.iterator();
        while (vars.hasNext() && values.hasNext()) {
            @SuppressWarnings("unchecked")
            ContextVar<Object> contextVar = (ContextVar<Object>)vars.next();
            Object contextVal = values.next();
            if (null == contextVal) {
                contextVar.remove();
            } else {
                contextVar.set(contextVal);
            }
        }
    }
    
    static String generateVarName() {
        return "<anonymous" + COUNTER.getAndIncrement() + ">";
    }
    
    private static final AtomicLong COUNTER = new AtomicLong();
}
