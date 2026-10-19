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

/**
 * Propagates the value of a single {@link ContextVar} — or a merged group of context
 * variables — across execution boundaries: other threads, thread pools, and asynchronous
 * {@link Promise} chains.
 *
 * <p>While {@link ContextVar#bind(Object)} applies context to <b>synchronous</b> execution
 * blocks, {@code ContextRelay} transfers a context snapshot to code that runs later and/or
 * on a different thread. Relays are created via {@link ContextVar#relay()} or the static
 * factories {@link ContextVar#relay(ContextVar...)} and {@link ContextVar#relay(java.util.Set)},
 * which merge several context variables into a single relay.
 *
 * <h2>Capture semantics</h2>
 * The three method families differ in <b>when</b> the context snapshot is taken:
 * <ul>
 *   <li><b>Functional wrappers</b> ({@link #contextual(Runnable)}, {@link #contextual(Callable)},
 *       {@link #contextual(Supplier)}, etc.) — <b>eager</b> capture: the snapshot is taken at
 *       the moment the {@code contextual(...)} method is invoked, and every invocation of the
 *       returned action runs within that same snapshot.</li>
 *   <li><b>Executor wrappers</b> ({@link #contextual(Executor)}, {@link #contextual(ExecutorService)},
 *       {@link #contextual(TaskExecutorService)}, {@link #contextual(ScheduledExecutorService)}) —
 *       <b>dynamic</b> capture: nothing is captured when the executor is wrapped. Instead, the
 *       snapshot is taken on each {@code execute} / {@code submit} / {@code invokeAll} /
 *       {@code invokeAny} / {@code schedule} call, so every task inherits the context that is
 *       active in the submitting thread at submission time.</li>
 *   <li><b>Promise wrappers</b> — two flavors:
 *       {@link #contextualPromises()} / {@link #contextualDependentPromises()} capture
 *       <b>eagerly</b> when the method is invoked, while
 *       {@link #dynamicContextualPromises()} / {@link #dynamicContextualDependentPromises()}
 *       resolve the context <b>dynamically</b>, taking the snapshot each time a callback is
 *       registered on a decorated promise.</li>
 * </ul>
 *
 * @param <X> the type of the relayed context value ({@code List<?>} for merged groups)
 * @see ContextVar#relay()
 * @see ContextVar#relay(ContextVar...)
 */
public final class ContextRelay<X> {

    /** The relayed context variable (possibly a merged group of variables). */
    private final ContextVar<X> contextVar;

    /**
     * Creates a relay for the given context variable; a {@code null} variable
     * is replaced with the no-op {@link ContextVar#empty()}.
     *
     * @param contextVar the context variable to relay
     */
    ContextRelay(ContextVar<X> contextVar) {
         this.contextVar = contextVar == null ? ContextVar.empty() : contextVar;
     }

     /**
      * Returns a relay whose context variable applies <b>strict</b> (pessimistic)
      * propagation: pre-existing ambient values are saved before and restored after
      * each contextual execution.
      *
      * @return a strict-mode relay
      * @see ContextVar#strict()
      */
     public ContextRelay<X> strict() {
         return new ContextRelay<>(contextVar.strict());
     }

     /**
      * Returns a relay whose context variable applies <b>optimized</b> propagation:
      * values are applied before and reset after each contextual execution, without
      * saving/restoring pre-existing ambient values.
      *
      * @return an optimized-mode relay
      * @see ContextVar#optimized()
      */
     public ContextRelay<X> optimized() {
         return new ContextRelay<>(contextVar.optimized());
     }

     /**
      * Takes a snapshot of the relayed context variable <b>at the moment of the call</b>.
      *
      * @return an immutable {@link Contextualization} holding the captured value
      */
     private Contextualization<X> captureContextualization() {
         return Contextualization.create(contextVar, contextVar.get());
     }

     /**
      * Wraps the given action so that it runs within the context captured <b>eagerly,
      * at the moment this method is invoked</b>. All subsequent invocations of the
      * returned action reuse that same snapshot.
      *
      * @param action the action to contextualize
      * @return a context-bound copy of the action
      */
     public Runnable contextual(Runnable action) {
         Contextualization<?> ctxz = captureContextualization();
         return () -> ctxz.runWith(action);
     }

     /**
      * Wraps the given action so that it runs within the context captured <b>eagerly,
      * at the moment this method is invoked</b>.
      *
      * @param action the action to contextualize
      * @param <V>    the result type of the callable
      * @return a context-bound copy of the action
      */
     public <V> Callable<V> contextual(Callable<V> action) {
         Contextualization<?> ctxz = captureContextualization();
         return () -> ctxz.callWith(action);
     }

     /**
      * Wraps the given action so that it runs within the context captured <b>eagerly,
      * at the moment this method is invoked</b>.
      *
      * @param action the action to contextualize
      * @param <T>    the result type of the supplier
      * @return a context-bound copy of the action
      */
     public <T> Supplier<T> contextual(Supplier<T> action) {
         Contextualization<?> ctxz = captureContextualization();
         return () -> ctxz.supplyWith(action);
     }

     /**
      * Wraps the given action so that it runs within the context captured <b>eagerly,
      * at the moment this method is invoked</b>.
      *
      * @param action the action to contextualize
      * @param <T>    the input type of the consumer
      * @return a context-bound copy of the action
      */
     public <T> Consumer<T> contextual(Consumer<T> action) {
         Contextualization<?> ctxz = captureContextualization();
         return v -> ctxz.runWith(() -> action.accept(v));
     }

     /**
      * Wraps the given action so that it runs within the context captured <b>eagerly,
      * at the moment this method is invoked</b>.
      *
      * @param action the action to contextualize
      * @param <T>    the first input type
      * @param <U>    the second input type
      * @return a context-bound copy of the action
      */
     public <T, U> BiConsumer<T, U> contextual(BiConsumer<T, U> action) {
         Contextualization<?> ctxz = captureContextualization();
         return (t, u) -> ctxz.runWith(() -> action.accept(t, u));
     }

     /**
      * Wraps the given action so that it runs within the context captured <b>eagerly,
      * at the moment this method is invoked</b>.
      *
      * @param action the action to contextualize
      * @param <T>    the input type
      * @param <R>    the result type
      * @return a context-bound copy of the action
      */
     public <T, R> Function<T, R> contextual(Function<T, R> action) {
         Contextualization<?> ctxz = captureContextualization();
         return v -> ctxz.supplyWith(() -> action.apply(v));
     }

     /**
      * Wraps the given action so that it runs within the context captured <b>eagerly,
      * at the moment this method is invoked</b>.
      *
      * @param action the action to contextualize
      * @param <T>    the first input type
      * @param <U>    the second input type
      * @param <R>    the result type
      * @return a context-bound copy of the action
      */
     public <T, U, R> BiFunction<T, U, R> contextual(BiFunction<T, U, R> action) {
         Contextualization<?> ctxz = captureContextualization();
         return (t, u)-> ctxz.supplyWith(() -> action.apply(t, u));
     }

     /**
      * Wraps the given action so that it runs within the context captured <b>eagerly,
      * at the moment this method is invoked</b>.
      *
      * @param action the action to contextualize
      * @param <T>    the input type
      * @return a context-bound copy of the action
      */
     public <T> Predicate<T> contextual(Predicate<T> action) {
         Contextualization<?> ctxz = captureContextualization();
         return v -> ctxz.supplyWith(() -> action.test(v));
     }

     /**
      * Wraps the given action so that it runs within the context captured <b>eagerly,
      * at the moment this method is invoked</b>.
      *
      * @param action the action to contextualize
      * @param <T>    the first input type
      * @param <U>    the second input type
      * @return a context-bound copy of the action
      */
     public <T, U> BiPredicate<T, U> contextual(BiPredicate<T, U> action) {
         Contextualization<?> ctxz = captureContextualization();
         return (t, u) -> ctxz.supplyWith(() -> action.test(t, u));
     }

     /**
      * Returns a promise decorator that propagates this relay's context to all callbacks
      * of the decorated promise.
      * <p>
      * <b>Static capture:</b> the context snapshot is taken <b>once, when this method is
      * called</b>. Every callback of every promise transformed by the returned function runs
      * within that same snapshot. Suitable for reusable decorators created ahead of time.
      * </p>
      * <p>
      * The returned function detects {@link DependentPromise} instances at runtime; use
      * {@link #contextualDependentPromises()} when the static {@code DependentPromise}
      * type must be preserved by the chain.
      * </p>
      *
      * @param <T> the promise result type
      * @return a function wrapping promises with the statically captured context
      * @see #dynamicContextualPromises()
      */
     public <T> Function<Promise<T>, Promise<T>> contextualPromises() {
         PromiseCustomizer customizer = new StaticContextualPromiseCustomizer(captureContextualization());
         return p -> p instanceof DependentPromise ?
             new CustomizableDependentPromiseDecorator<>((DependentPromise<T>)p, customizer)
             :
             new CustomizablePromiseDecorator<>(p, customizer);
     }

     /**
      * Type-preserving variant of {@link #contextualPromises()} for chains that operate on
      * {@link DependentPromise} and must keep its extended API surface.
      *
      * @param <T> the promise result type
      * @return a function wrapping dependent promises with the statically captured context
      * @see #contextualPromises()
      */
     public <T> Function<DependentPromise<T>, DependentPromise<T>> contextualDependentPromises() {
         PromiseCustomizer customizer = new StaticContextualPromiseCustomizer(captureContextualization());
         return p -> new CustomizableDependentPromiseDecorator<>(p, customizer);
     }

     /**
      * Returns a promise decorator that propagates this relay's context to all callbacks
      * of the decorated promise.
      * <p>
      * <b>Dynamic resolution:</b> unlike {@link #contextualPromises()}, no snapshot is taken
      * when this method is called. Instead, the context is captured <b>each time a callback
      * is registered</b> on a decorated promise (i.e., when each dependent stage is created).
      * This lets context flow through long asynchronous chains: every stage observes the
      * context that was active in the code that chained it.
      * </p>
      *
      * @param <T> the promise result type
      * @return a function wrapping promises with dynamically resolved context
      * @see #contextualPromises()
      */
     public <T> Function<Promise<T>, Promise<T>> dynamicContextualPromises() {
         PromiseCustomizer customizer = new DynamicContextualPromiseCustomizer(this);
         return p -> p instanceof DependentPromise ?
             new CustomizableDependentPromiseDecorator<>((DependentPromise<T>)p, customizer)
             :
             new CustomizablePromiseDecorator<>(p, customizer);
     }

     /**
      * Type-preserving variant of {@link #dynamicContextualPromises()} for chains that operate
      * on {@link DependentPromise} and must keep its extended API surface.
      *
      * @param <T> the promise result type
      * @return a function wrapping dependent promises with dynamically resolved context
      * @see #dynamicContextualPromises()
      */
     public <T> Function<DependentPromise<T>, DependentPromise<T>> dynamicContextualDependentPromises() {
         PromiseCustomizer customizer = new DynamicContextualPromiseCustomizer(this);
         return p -> new CustomizableDependentPromiseDecorator<>(p, customizer);
     }

     /**
      * Returns a context-aware view of the given executor.
      * <p>
      * <b>Dynamic resolution:</b> no snapshot is taken when the executor is wrapped.
      * On every {@link Executor#execute(Runnable) execute(...)} call the context of the
      * <em>submitting</em> thread is captured at that moment, and the task runs within
      * that snapshot. Tasks submitted from different contexts therefore inherit
      * different snapshots.
      * </p>
      *
      * @param executor the executor to decorate
      * @return a context-aware executor delegating to the given one
      */
     public Executor contextual(Executor executor) {
         return contextualExecutor(executor, ContextualExecutor::new);
     }

     /**
      * Returns a context-aware view of the given executor service.
      * <p>
      * <b>Dynamic resolution:</b> the context snapshot is taken on each
      * {@code submit} / {@code invokeAll} / {@code invokeAny} call, so every task
      * inherits the context active at its submission time.
      * </p>
      *
      * @param executorService the executor service to decorate
      * @return a context-aware executor service delegating to the given one
      */
     public ExecutorService contextual(ExecutorService executorService) {
         return contextualExecutor(executorService, ContextualExecutorService::new);
     }

     /**
      * Returns a context-aware view of the given task executor service
      * (promise-returning variant of {@link ExecutorService}).
      * <p>
      * <b>Dynamic resolution:</b> the context snapshot is taken on each
      * {@code submit} / {@code invokeAll} / {@code invokeAny} call.
      * </p>
      *
      * @param executorService the task executor service to decorate
      * @return a context-aware task executor service delegating to the given one
      */
     public TaskExecutorService contextual(TaskExecutorService executorService) {
         return contextualExecutor(executorService, ContextualTaskExecutorService::new);
     }

     /**
      * Returns a context-aware view of the given scheduled executor service.
      * <p>
      * <b>Dynamic resolution:</b> the context snapshot is taken on each
      * {@code schedule} / {@code scheduleAtFixedRate} / {@code scheduleWithFixedDelay}
      * call. Note that for periodic tasks the snapshot captured at scheduling time is
      * reused for every repetition of the task.
      * </p>
      *
      * @param executorService the scheduled executor service to decorate
      * @return a context-aware scheduled executor service delegating to the given one
      */
     public ScheduledExecutorService contextual(ScheduledExecutorService executorService) {
         return contextualExecutor(executorService, ContextualScheduledExecutorService::new);
     }

     /**
      * Instantiates a contextual executor decorator via the supplied constructor reference.
      *
      * @param delegate the executor to decorate
      * @param ctr      constructor reference of the decorator class
      * @param <D>      the executor type
      * @return the decorated executor
      */
     private <D extends Executor> D contextualExecutor(D delegate,
                                                       BiFunction<D, ContextRelay<?>, D> ctr) {
         return ctr.apply(delegate, this);
     }
}