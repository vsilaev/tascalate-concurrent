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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import net.tascalate.concurrent.DependentPromise;
import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.TaskExecutorService;
import net.tascalate.concurrent.decorators.CustomizableDependentPromiseDecorator;
import net.tascalate.concurrent.decorators.CustomizablePromiseDecorator;
import net.tascalate.concurrent.decorators.PromiseCustomizer;

public final class ContextTrampoline {

    private final List<ContextVar<?>> contextVars;
    
    ContextTrampoline(List<? extends ContextVar<?>> contextVars) {
        this.contextVars = contextVars == null ? Collections.emptyList() : 
                                                 Collections.unmodifiableList(contextVars);
    }
    
    public <T> Function<Promise<T>, Promise<T>> captureContextForPromises() {
        return captureContextForPromises(ContextVar.Propagation.OPTIMIZED);
    }
    
    public <T> Function<Promise<T>, Promise<T>> captureContextForPromises(ContextVar.Propagation propagation) {
        if (null == contextVars || contextVars.isEmpty()) {
            return Function.identity();
        }
        
        PromiseCustomizer customizer = new ContextualPromiseCustomizer(
            contextVars, propagation, ContextualObject.captureContextVars(contextVars)
        );
        
        return p ->
            p instanceof DependentPromise ?
                new CustomizableDependentPromiseDecorator<>((DependentPromise<T>)p, customizer)
                :
                new CustomizablePromiseDecorator<>(p, customizer);
    }
    
    public Executor withCapturedContext(Executor executor) {
        return withCapturedContext(executor, ContextVar.Propagation.OPTIMIZED);
    }
    
    public Executor withCapturedContext(Executor executor, ContextVar.Propagation propagation) {
        return captureAndWrapExecutor(executor, propagation, ContextualExecutor::new);
    }
    
    public ExecutorService withCapturedContext(ExecutorService executorService) {
        return withCapturedContext(executorService, ContextVar.Propagation.OPTIMIZED);
    }
    
    public ExecutorService withCapturedContext(ExecutorService executorService, ContextVar.Propagation propagation) {
        return captureAndWrapExecutor(executorService, propagation, ContextualExecutorService::new);
    }
    
    public TaskExecutorService withCapturedContext(TaskExecutorService executorService) {
        return withCapturedContext(executorService, ContextVar.Propagation.OPTIMIZED);
    }
    
    public TaskExecutorService withCapturedContext(TaskExecutorService executorService, ContextVar.Propagation propagation) {
        return captureAndWrapExecutor(executorService, propagation, ContextualTaskExecutorService::new);
    }
    
    public ScheduledExecutorService withCapturedContext(ScheduledExecutorService executorService) {
        return withCapturedContext(executorService, ContextVar.Propagation.OPTIMIZED);
    }
    
    public ScheduledExecutorService withCapturedContext(ScheduledExecutorService executorService, ContextVar.Propagation propagation) {
        return captureAndWrapExecutor(executorService, propagation, ContextualScheduledExecutorService::new);
    }
    
    private <D extends Executor> D captureAndWrapExecutor(D delegate, 
                                                          ContextVar.Propagation propagation, 
                                                          ContextualExecutorConstructor<D> ctr) {
        if (null == contextVars || contextVars.isEmpty()) {
            return delegate;
        } 
        return ctr.apply(delegate, contextVars, propagation, ContextualObject.captureContextVars(contextVars));
    }
    
    private static interface ContextualExecutorConstructor<D extends Executor> {
        D apply(D delegate, List<ContextVar<?>> contextVars, ContextVar.Propagation propagation, List<Object> capturedContext);
    }
    
    static String generateVarName() {
        return "<anonymous" + COUNTER.getAndIncrement() + ">";
    }
    
    private static final AtomicLong COUNTER = new AtomicLong();
}
