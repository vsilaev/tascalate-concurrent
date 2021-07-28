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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import net.tascalate.concurrent.DependentPromise;
import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.TaskExecutorService;
import net.tascalate.concurrent.decorators.CustomizableDependentPromiseDecorator;
import net.tascalate.concurrent.decorators.CustomizablePromiseDecorator;
import net.tascalate.concurrent.decorators.PromiseCustomizer;

public class ContextTrampoline {

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
    
    private final List<ContextVar<?>> contextVars;
    
    private ContextTrampoline(List<? extends ContextVar<?>> contextVars) {
        this.contextVars = contextVars == null ? Collections.emptyList() : 
                                                 Collections.unmodifiableList(contextVars);
    }
    
    private Contextualization captureContextualization(Propagation propagation) {
        return new Contextualization(contextVars, propagation, captureContext(contextVars));
    }
    
    public Runnable contextual(Runnable action) {
        return contextual(Propagation.OPTIMIZED, action);
    }
    
    public Runnable contextual(Propagation propagation, Runnable action) {
        Contextualization ctxz = captureContextualization(propagation);
        return () -> {
            List<Object> capturedContext = ctxz.enter(); 
            try {
                action.run();
            } finally {
                ctxz.exit(capturedContext);
            }
        };
    }
    
    public <V> Callable<V> contextualCall(Callable<V> action) {
        return contextualCall(Propagation.OPTIMIZED, action);
    }
    
    public <V> Callable<V> contextualCall(Propagation propagation, Callable<V> action) {
        Contextualization ctxz = captureContextualization(propagation);
        return () -> {
            List<Object> capturedContext = ctxz.enter(); 
            try {
                return action.call();
            } finally {
                ctxz.exit(capturedContext);
            }
        };
    }    
    
    public <T> Supplier<T> contextual(Supplier<T> action) {
        return contextual(Propagation.OPTIMIZED, action);
    }
    
    public <T> Supplier<T> contextual(Propagation propagation, Supplier<T> action) {
        Contextualization ctxz = captureContextualization(propagation);
        return () -> {
            List<Object> capturedContext = ctxz.enter(); 
            try {
                return action.get();
            } finally {
                ctxz.exit(capturedContext);
            }
        };
    } 
    
    public <T> Consumer<T> contextual(Consumer<T> action) {
        return contextual(Propagation.OPTIMIZED, action);
    }
    
    public <T> Consumer<T> contextual(Propagation propagation, Consumer<T> action) {
        Contextualization ctxz = captureContextualization(propagation);
        return v -> {
            List<Object> capturedContext = ctxz.enter(); 
            try {
                action.accept(v);
            } finally {
                ctxz.exit(capturedContext);
            }
        };
    }     
    
    public <T, U> BiConsumer<T, U> contextual(BiConsumer<T, U> action) {
        return contextual(Propagation.OPTIMIZED, action);
    }
    
    public <T, U> BiConsumer<T, U> contextual(Propagation propagation, BiConsumer<T, U> action) {
        Contextualization ctxz = captureContextualization(propagation);
        return (t, u) -> {
            List<Object> capturedContext = ctxz.enter(); 
            try {
                action.accept(t, u);
            } finally {
                ctxz.exit(capturedContext);
            }
        };
    }  
    
    public <T, R> Function<T, R> contextual(Function<T, R> action) {
        return contextual(Propagation.OPTIMIZED, action);
    }
    
    public <T, R> Function<T, R> contextual(Propagation propagation, Function<T, R> action) {
        Contextualization ctxz = captureContextualization(propagation);
        return v -> {
            List<Object> capturedContext = ctxz.enter(); 
            try {
                return action.apply(v);
            } finally {
                ctxz.exit(capturedContext);
            }
        };
    }  
    
    public <T, U, R> BiFunction<T, U, R> contextual(BiFunction<T, U, R> action) {
        return contextual(Propagation.OPTIMIZED, action);
    }
    
    public <T, U, R> BiFunction<T, U, R> contextual(Propagation propagation, BiFunction<T, U, R> action) {
        Contextualization ctxz = captureContextualization(propagation);
        return (t, u)-> {
            List<Object> capturedContext = ctxz.enter(); 
            try {
                return action.apply(t, u);
            } finally {
                ctxz.exit(capturedContext);
            }
        };
    }  
    
    public <T> Predicate<T> contextual(Predicate<T> action) {
        return contextual(Propagation.OPTIMIZED, action);
    }
    
    public <T> Predicate<T> contextual(Propagation propagation, Predicate<T> action) {
        Contextualization ctxz = captureContextualization(propagation);
        return v -> {
            List<Object> capturedContext = ctxz.enter(); 
            try {
                return action.test(v);
            } finally {
                ctxz.exit(capturedContext);
            }
        };
    }   
    
    public <T, U> BiPredicate<T, U> contextual(BiPredicate<T, U> action) {
        return contextual(Propagation.OPTIMIZED, action);
    }
    
    public <T, U> BiPredicate<T, U> contextual(Propagation propagation, BiPredicate<T, U> action) {
        Contextualization ctxz = captureContextualization(propagation);
        return (t, u) -> {
            List<Object> capturedContext = ctxz.enter(); 
            try {
                return action.test(t, u);
            } finally {
                ctxz.exit(capturedContext);
            }
        };
    }   
    
    public <T> Function<Promise<T>, Promise<T>> boundPromises() {
        return boundPromises(Propagation.OPTIMIZED);
    }
    
    public <T> Function<DependentPromise<T>, DependentPromise<T>> boundPromisesʹ() {
        return boundPromisesʹ(Propagation.OPTIMIZED);
    }
    
    public <T> Function<Promise<T>, Promise<T>> boundPromises(Propagation propagation) {
        PromiseCustomizer customizer = new ContextualPromiseCustomizer(captureContextualization(propagation));
        return p -> p instanceof DependentPromise ?
            new CustomizableDependentPromiseDecorator<>((DependentPromise<T>)p, customizer)
            :
            new CustomizablePromiseDecorator<>(p, customizer);
    }
    
    public <T> Function<DependentPromise<T>, DependentPromise<T>> boundPromisesʹ(Propagation propagation) {
        PromiseCustomizer customizer = new ContextualPromiseCustomizer(captureContextualization(propagation));
        return p -> new CustomizableDependentPromiseDecorator<>(p, customizer);
    }
    
    public Executor bind(Executor executor) {
        return bind(executor, Propagation.OPTIMIZED);
    }
    
    public Executor bind(Executor executor, Propagation propagation) {
        return bindExecutor(executor, propagation, ContextualExecutor::new);
    }
    
    public ExecutorService bind(ExecutorService executorService) {
        return bind(executorService, Propagation.OPTIMIZED);
    }
    
    public ExecutorService bind(ExecutorService executorService, Propagation propagation) {
        return bindExecutor(executorService, propagation, ContextualExecutorService::new);
    }
    
    public TaskExecutorService bind(TaskExecutorService executorService) {
        return bind(executorService, Propagation.OPTIMIZED);
    }
    
    public TaskExecutorService bind(TaskExecutorService executorService, Propagation propagation) {
        return bindExecutor(executorService, propagation, ContextualTaskExecutorService::new);
    }
    
    public ScheduledExecutorService bind(ScheduledExecutorService executorService) {
        return bind(executorService, Propagation.OPTIMIZED);
    }
    
    public ScheduledExecutorService bind(ScheduledExecutorService executorService, Propagation propagation) {
        return bindExecutor(executorService, propagation, ContextualScheduledExecutorService::new);
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
        if (null == contextVars || contextVars.isEmpty()) {
            return NOP;
        } else {
            return new ContextTrampoline(new ArrayList<>(contextVars));
        }
    }
    
    public static ContextTrampoline relayThreadLocals(List<? extends ThreadLocal<?>> threadLocals) {
        if (null == threadLocals || threadLocals.isEmpty()) {
            return NOP;
        } else {
            return relay(
                threadLocals.stream()
                            .map(tl -> ContextVar.from((ThreadLocal<?>)tl))
                            .collect(Collectors.toList())
            );
        }
    }
    
    static List<Object> captureContext(List<? extends ContextVar<?>> contextVars) {
        return contextVars.stream().map(v -> v.get()).collect(Collectors.toList());
    }
    
    private <D extends Executor> D bindExecutor(D delegate, 
                                                Propagation propagation, 
                                                BiFunction<D, Contextualization, D> ctr) {
        return ctr.apply(delegate, captureContextualization(propagation));
    }
    
    static String generateVarName() {
        return "<anonymous" + COUNTER.getAndIncrement() + ">";
    }
    
    private static final AtomicLong COUNTER = new AtomicLong();
    
    private static final ContextTrampoline NOP = new ContextTrampoline(null) {

        @Override
        public Runnable contextual(Runnable action) {
            return action;
        }

        @Override
        public Runnable contextual(Propagation propagation, Runnable action) {
            return action;
        }

        @Override
        public <V> Callable<V> contextualCall(Callable<V> action) {
            return action;
        }

        @Override
        public <V> Callable<V> contextualCall(Propagation propagation, Callable<V> action) {
            return action;
        }

        @Override
        public <T> Supplier<T> contextual(Supplier<T> action) {
            return action;
        }

        @Override
        public <T> Supplier<T> contextual(Propagation propagation, Supplier<T> action) {
            return action;
        }

        @Override
        public <T> Consumer<T> contextual(Consumer<T> action) {
            return action;
        }

        @Override
        public <T> Consumer<T> contextual(Propagation propagation, Consumer<T> action) {
            return action;
        }

        @Override
        public <T, U> BiConsumer<T, U> contextual(BiConsumer<T, U> action) {
            return action;
        }

        @Override
        public <T, U> BiConsumer<T, U> contextual(Propagation propagation, BiConsumer<T, U> action) {
            return action;
        }

        @Override
        public <T, R> Function<T, R> contextual(Function<T, R> action) {
            return action;
        }

        @Override
        public <T, R> Function<T, R> contextual(Propagation propagation, Function<T, R> action) {
            return action;
        }

        @Override
        public <T, U, R> BiFunction<T, U, R> contextual(BiFunction<T, U, R> action) {
            return action;
        }

        @Override
        public <T, U, R> BiFunction<T, U, R> contextual(Propagation propagation, BiFunction<T, U, R> action) {
            return action;
        }

        @Override
        public <T> Predicate<T> contextual(Predicate<T> action) {
            return action;
        }

        @Override
        public <T> Predicate<T> contextual(Propagation propagation, Predicate<T> action) {
            return action;
        }

        @Override
        public <T, U> BiPredicate<T, U> contextual(BiPredicate<T, U> action) {
            return action;
        }

        @Override
        public <T, U> BiPredicate<T, U> contextual(Propagation propagation, BiPredicate<T, U> action) {
            return action;
        }        

        @Override
        public <T> Function<Promise<T>, Promise<T>> boundPromises() {
            return Function.identity();
        }
        
        @Override
        public <T> Function<DependentPromise<T>, DependentPromise<T>> boundPromisesʹ() {
            return Function.identity();
        }

        @Override
        public <T> Function<Promise<T>, Promise<T>> boundPromises(Propagation propagation) {
            return Function.identity();
        }
        
        @Override
        public <T> Function<DependentPromise<T>, DependentPromise<T>> boundPromisesʹ(Propagation propagation) {
            return Function.identity();
        }

        @Override
        public Executor bind(Executor executor) {
            return executor;
        }

        @Override
        public Executor bind(Executor executor, Propagation propagation) {
            return executor;
        }

        @Override
        public ExecutorService bind(ExecutorService executorService) {
            return executorService;
        }

        @Override
        public ExecutorService bind(ExecutorService executorService, Propagation propagation) {
            return executorService;
        }

        @Override
        public TaskExecutorService bind(TaskExecutorService executorService) {
            return executorService;
        }

        @Override
        public TaskExecutorService bind(TaskExecutorService executorService, Propagation propagation) {
            return executorService;
        }

        @Override
        public ScheduledExecutorService bind(ScheduledExecutorService executorService) {
            return executorService;
        }

        @Override
        public ScheduledExecutorService bind(ScheduledExecutorService executorService, Propagation propagation) {
            return executorService;
        }
    };
}
