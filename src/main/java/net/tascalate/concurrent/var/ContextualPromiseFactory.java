
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
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import net.tascalate.concurrent.DependentPromise;
import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.decorators.CustomizableDependentPromiseDecorator;
import net.tascalate.concurrent.decorators.CustomizablePromiseDecorator;
import net.tascalate.concurrent.decorators.PromiseCustomizer;

public final class ContextualPromiseFactory {

    private final List<ContextVar<?>> contextVars;
    
    ContextualPromiseFactory(List<? extends ContextVar<?>> contextVars) {
        this.contextVars = contextVars == null ? Collections.emptyList() : 
                                                 Collections.unmodifiableList(contextVars);
    }
    
    public <T> Function<Promise<T>, Promise<T>> captureContext() {
        if (null == contextVars || contextVars.isEmpty()) {
            return Function.identity();
        }
        
        PromiseCustomizer customizer = new ContextualPromiseCustomizer(
            contextVars, ContextualPromiseCustomizer.captureContextVars(contextVars)
        );
        
        return p ->
            p instanceof DependentPromise ?
                new CustomizableDependentPromiseDecorator<>((DependentPromise<T>)p, customizer)
                :
                new CustomizablePromiseDecorator<>(p, customizer);
    }
    
    
    static String generateVarName() {
        return "<anonymous" + COUNTER.getAndIncrement() + ">";
    }
    
    private static final AtomicLong COUNTER = new AtomicLong();
}
