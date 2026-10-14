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

public class ContextRelay<X> {
    private final ContextVar<X> contextVar;
    
    ContextRelay(ContextVar<X> contextVar) {
        this.contextVar = contextVar == null ? ContextVar.empty() : contextVar;
    }
    
    public ContextRelay<X> strict() {
        return new ContextRelay<>(contextVar.strict());
    }
    
    public ContextRelay<X> optimized() {
        return new ContextRelay<>(contextVar.optimized());
    }
    
    private Contextualization<X> captureContextualization() {
        return Contextualization.create(contextVar, contextVar.get());
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
    
    public <T> Function<Promise<T>, Promise<T>> staticBoundPromises() {
        PromiseCustomizer customizer = new StaticContextualPromiseCustomizer(captureContextualization());
        return p -> p instanceof DependentPromise ?
            new CustomizableDependentPromiseDecorator<>((DependentPromise<T>)p, customizer)
            :
            new CustomizablePromiseDecorator<>(p, customizer);
    }
    
    public <T> Function<DependentPromise<T>, DependentPromise<T>> staticBoundPromisesʹ() {
        PromiseCustomizer customizer = new StaticContextualPromiseCustomizer(captureContextualization());
        return p -> new CustomizableDependentPromiseDecorator<>(p, customizer);
    }
    
    public <T> Function<Promise<T>, Promise<T>> dynamicBoundPromises() {
        PromiseCustomizer customizer = new DynamicContextualPromiseCustomizer(this);
        return p -> p instanceof DependentPromise ?
            new CustomizableDependentPromiseDecorator<>((DependentPromise<T>)p, customizer)
            :
            new CustomizablePromiseDecorator<>(p, customizer);
    }
    
    public <T> Function<DependentPromise<T>, DependentPromise<T>> dynamicBoundPromisesʹ() {
        PromiseCustomizer customizer = new DynamicContextualPromiseCustomizer(this);
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
    
    private <D extends Executor> D bindExecutor(D delegate,
                                                BiFunction<D, ContextRelay<?>, D> ctr) {
        return ctr.apply(delegate, this);
    }
  
    
    static final ContextVar<String>  TL_VAR_S = ThreadLocalVar.newInstance("tl_s");
    static final ContextVar<Integer> TL_VAR_I = ThreadLocalVar.newInstance("tl_i");
    static final ContextVar<String>  SL_VAR_S = ThreadLocalVar.newInstance("sl_s");//ScopedValueVar.newInstance("sl_s");
    static final ContextVar<Integer> SL_VAR_I = ThreadLocalVar.newInstance("sl_i");//ScopedValueVar.newInstance("sl_i");
    static final ContextVar<String>  T_VAR_S  = ThreadVar.newInstance("tvar_s");
    static final ContextVar<Integer> T_VAR_I  = ThreadVar.newInstance("tvar_i");
    
    static final ContextVar<String> cxS = new ContextVar<String>() {
        @Override
        public String get() {
            return "SUPER";
        }

        @Override
        public <V> V callWith(String capturedValue, Callable<V> code) throws Exception {
            return code.call();
        }
    };
    
    
    public static void main(String[] args) {
        ContextRelay<?> tx = ContextVar.relay(TL_VAR_S, TL_VAR_I, SL_VAR_S, SL_VAR_I, T_VAR_S, T_VAR_I, cxS);
        System.out.println(tx);
        ContextVar.bind(TL_VAR_S, "ABC")
                  .bind(TL_VAR_I, 1234)
                  .bind(SL_VAR_S, "XYZ")
                  .bind(SL_VAR_I, 999)
                  .bind(T_VAR_S, "@@@")
                  .bind(T_VAR_I, 777)
                  .bind(cxS, "IGNORE")
                  .bind(ContextVar.empty(), "ZZZ")
                  .run(() -> {

                      System.out.println(TL_VAR_S + " = " + TL_VAR_S.get()); 
                      System.out.println(TL_VAR_I + " = " + TL_VAR_I.get());
                      System.out.println(SL_VAR_S + " = " + SL_VAR_S.get()); 
                      System.out.println(SL_VAR_I + " = " + SL_VAR_I.get());
                      System.out.println(T_VAR_S + " = " + T_VAR_S.get()); 
                      System.out.println(T_VAR_I + " = " + T_VAR_I.get());                      
                      
                      System.out.println("+++++++++++++++++++++++++++");
                      
                      new Thread(tx.contextual(() -> {
                          System.out.println(TL_VAR_S + " = " + TL_VAR_S.get()); 
                          System.out.println(TL_VAR_I + " = " + TL_VAR_I.get());
                          System.out.println(SL_VAR_S + " = " + SL_VAR_S.get()); 
                          System.out.println(SL_VAR_I + " = " + SL_VAR_I.get());
                          System.out.println(T_VAR_S + " = " + T_VAR_S.get()); 
                          System.out.println(T_VAR_I + " = " + T_VAR_I.get());
                          T_VAR_I.strict().bind(10000).run(() -> {
                              System.out.println("ALTERED:: " + T_VAR_I + " = " + T_VAR_I.get());
                          });
                          System.out.println(T_VAR_I + " = " + T_VAR_I.get());

                     })).run();
                  });
    }
}
