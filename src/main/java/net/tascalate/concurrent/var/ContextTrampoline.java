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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
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
    
    public <T> Function<Promise<T>, Promise<T>> boundPromises() {
        return boundPromises(Propagation.OPTIMIZED);
    }
    
    public <T> Function<Promise<T>, Promise<T>> boundPromises(Propagation propagation) {
        PromiseCustomizer customizer = new ContextualPromiseCustomizer(
            contextVars, propagation, captureContext(contextVars)
        );
        
        return p -> p instanceof DependentPromise ?
            new CustomizableDependentPromiseDecorator<>((DependentPromise<T>)p, customizer)
            :
            new CustomizablePromiseDecorator<>(p, customizer);
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
                                                ContextualExecutorConstructor<D> ctr) {
        return ctr.apply(delegate, contextVars, propagation, captureContext(contextVars));
    }
    
    private static interface ContextualExecutorConstructor<D extends Executor> {
        D apply(D delegate, List<ContextVar<?>> contextVars, Propagation propagation, List<Object> capturedContext);
    }
    
    static String generateVarName() {
        return "<anonymous" + COUNTER.getAndIncrement() + ">";
    }
    
    private static final AtomicLong COUNTER = new AtomicLong();
    
    private static final ContextTrampoline NOP = new ContextTrampoline(null) {

        @Override
        public <T> Function<Promise<T>, Promise<T>> boundPromises() {
            return Function.identity();
        }

        @Override
        public <T> Function<Promise<T>, Promise<T>> boundPromises(Propagation propagation) {
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
