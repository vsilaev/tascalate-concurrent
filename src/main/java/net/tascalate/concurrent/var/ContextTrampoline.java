/**
 * Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.tascalate.concurrent.DependentPromise;
import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.TaskExecutorService;
import net.tascalate.concurrent.decorators.CustomizableDependentPromiseDecorator;
import net.tascalate.concurrent.decorators.CustomizablePromiseDecorator;
import net.tascalate.concurrent.decorators.PromiseCustomizer;

public class ContextTrampoline<X> {
    private final ContextVar<X> contextVars;
    
    ContextTrampoline(ContextVar<X> contextVar) {
        this.contextVars = contextVar == null ? ContextVar.empty() : contextVar;
    }
    
    private Contextualization<X> captureContextualization() {
        return new Contextualization<>(contextVars, contextVars.get());
    }
    
    public Runnable contextual(Runnable action) {
        Contextualization<?> ctxz = captureContextualization();
        return () -> ctxz.runWith(action);
    }
    
    public <V> Callable<V> contextual(Callable<V> action) {
        Contextualization<?> ctxz = captureContextualization();
        return () -> ctxz.callWith(action);
    }    
    
    public <T> Supplier<T> contextual(Supplier<T> action) {
        Contextualization<?> ctxz = captureContextualization();
        return () -> ctxz.supplyWith(action);
    } 
    
    public <T> Consumer<T> contextual(Consumer<T> action) {
        Contextualization<?> ctxz = captureContextualization();
        return v -> ctxz.runWith(() -> action.accept(v));
    }     
    
    public <T, U> BiConsumer<T, U> contextual(BiConsumer<T, U> action) {
        Contextualization<?> ctxz = captureContextualization();
        return (t, u) -> ctxz.runWith(() -> action.accept(t, u));
    }  
    
    public <T, R> Function<T, R> contextual(Function<T, R> action) {
        Contextualization<?> ctxz = captureContextualization();
        return v -> ctxz.supplyWith(() -> action.apply(v));
    }  
    
    public <T, U, R> BiFunction<T, U, R> contextual(BiFunction<T, U, R> action) {
        Contextualization<?> ctxz = captureContextualization();
        return (t, u)-> ctxz.supplyWith(() -> action.apply(t, u));
    }  
    
    public <T> Predicate<T> contextual(Predicate<T> action) {
        Contextualization<?> ctxz = captureContextualization();
        return v -> ctxz.supplyWith(() -> action.test(v));
    }   
    
    public <T, U> BiPredicate<T, U> contextual(BiPredicate<T, U> action) {
        Contextualization<?> ctxz = captureContextualization();
        return (t, u) -> ctxz.supplyWith(() -> action.test(t, u));
    }   
    
    public <T> Function<Promise<T>, Promise<T>> boundPromises() {
        PromiseCustomizer customizer = new ContextualPromiseCustomizer(captureContextualization());
        return p -> p instanceof DependentPromise ?
            new CustomizableDependentPromiseDecorator<>((DependentPromise<T>)p, customizer)
            :
            new CustomizablePromiseDecorator<>(p, customizer);
    }
    
    public <T> Function<DependentPromise<T>, DependentPromise<T>> boundPromisesʹ() {
        PromiseCustomizer customizer = new ContextualPromiseCustomizer(captureContextualization());
        return p -> new CustomizableDependentPromiseDecorator<>(p, customizer);
    }

    public Executor bind(Executor executor) {
        return bindExecutor(executor, ContextualExecutor::new);
    }
    
    public ExecutorService bind(ExecutorService executorService) {
        return bindExecutor(executorService, ContextualExecutorService::new);
    }
    
    public TaskExecutorService bind(TaskExecutorService executorService) {
        return bindExecutor(executorService, ContextualTaskExecutorService::new);
    }
    
    public ScheduledExecutorService bind(ScheduledExecutorService executorService) {
        return bindExecutor(executorService, ContextualScheduledExecutorService::new);
    }
    
    public static <T> ContextTrampoline<T> relay(ContextVar<T> contextVar) {
        return new ContextTrampoline<>(contextVar);
    }
    
    public static <T> ContextTrampoline<T> relay(ThreadLocal<T> threadLocal) {
        return relay(ThreadLocalVar.of(threadLocal));
    }

    @SafeVarargs
    public static <T> ContextVar<List<? extends T>> genericVars(ContextVar<? extends T>... contextVars) {
        return ContextVar.of(contextVars);
    }
    
    public static <T> ContextVar<List<? extends T>> genericVars(List<? extends ContextVar<? extends T>> contextVars) {
        return ContextVar.of(contextVars);
    }
    
    @SafeVarargs
    public static <T> ContextVar<List<? extends T>> modifiableVars(ModifiableContextVar<? extends T>... contextVars) {
        return ModifiableContextVar.of(contextVars);
    }
    
    public static <T> ContextVar<List<? extends T>> modifiableVars(List<? extends ModifiableContextVar<? extends T>> contextVars) {
        return ModifiableContextVar.of(contextVars);
    }
    
    @SafeVarargs
    public static <T> ContextVar<List<? extends T>> threadLocals(ThreadLocal<? extends T>... contextVars) {
        return ThreadLocalVar.of(contextVars);
    }
    
    public static <T> ContextVar<List<? extends T>> threadLocals(List<? extends ThreadLocal<? extends T>> contextVars) {
        return ThreadLocalVar.of(contextVars);
    }
    
    private <D extends Executor> D bindExecutor(D delegate,
                                                BiFunction<D, Contextualization<?>, D> ctr) {
        return ctr.apply(delegate, captureContextualization());
    }
  
}
