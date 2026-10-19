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

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.tascalate.concurrent.decorators.PromiseCustomizer;

/**
 * A {@link PromiseCustomizer} that propagates <b>dynamically resolved</b> context
 * to the callbacks of a decorated promise.
 * <p>
 * Unlike {@link StaticContextualPromiseCustomizer}, which holds a fixed snapshot,
 * this customizer holds the {@link ContextRelay} itself. Consequently, the context
 * snapshot is taken <b>each time a callback is wrapped</b> — i.e., at the moment each
 * dependent stage is created on the decorated promise (when {@code thenApply},
 * {@code thenAccept}, etc. is called). Each stage therefore observes the context that
 * was active in the code that chained it, letting context flow through long
 * asynchronous promise chains.
 * </p>
 * <p>
 * Use this flavor via {@link ContextRelay#dynamicContextualPromises()} or
 * {@link ContextRelay#dynamicContextualDependentPromises()}. If a single fixed snapshot
 * must be applied to all stages, use {@link StaticContextualPromiseCustomizer} instead.
 * </p>
 * <p>
 * The {@code async} flag supplied by the promise framework is intentionally ignored: context
 * must be applied to callbacks whether they run synchronously or asynchronously.
 * </p>
 *
 * @see StaticContextualPromiseCustomizer
 * @see ContextRelay#dynamicContextualPromises()
 * @see ContextRelay#dynamicContextualDependentPromises()
 */
final class DynamicContextualPromiseCustomizer implements PromiseCustomizer {

    /** The relay used to capture the context at each callback registration. */
    private final ContextRelay<?> ctxRelay;

    /**
     * Creates a customizer bound to the given relay.
     *
     * @param ctxRelay the relay capturing context each time a callback is wrapped
     */
    DynamicContextualPromiseCustomizer(ContextRelay<?> ctxRelay) {
         this.ctxRelay = ctxRelay;
     }

     /**
      * Wraps the callback with the context snapshot captured <b>at the moment of this call</b>.
      *
      * @param original the original callback
      * @param async    whether the callback is executed asynchronously (currently ignored)
      * @return a context-bound copy of the callback
      */
     @Override
     public Runnable wrapArgument(Runnable original, boolean async) {
         return ctxRelay.contextual(original);
     }

     /**
      * Wraps the callback with the context snapshot captured <b>at the moment of this call</b>.
      *
      * @param original  the original callback
      * @param async     whether the callback is executed asynchronously (currently ignored)
      * @param isCompose whether the callback participates in a compose-style operator (currently ignored)
      * @param <U>       the input type of the function
      * @param <R>       the result type of the function
      * @return a context-bound copy of the callback
      */
     @Override
     public <U, R> Function<U, R> wrapArgument(Function<U, R> original, boolean async, boolean isCompose) {
         return ctxRelay.contextual(original);
     }

     /**
      * Wraps the callback with the context snapshot captured <b>at the moment of this call</b>.
      *
      * @param original the original callback
      * @param async    whether the callback is executed asynchronously (currently ignored)
      * @param <U>      the input type of the consumer
      * @return a context-bound copy of the callback
      */
     @Override
     public <U> Consumer<U> wrapArgument(Consumer<U> original, boolean async) {
         return ctxRelay.contextual(original);
     }

     /**
      * Wraps the callback with the context snapshot captured <b>at the moment of this call</b>.
      *
      * @param original the original callback
      * @param async    whether the callback is executed asynchronously (currently ignored)
      * @param <U>      the result type of the supplier
      * @return a context-bound copy of the callback
      */
     @Override
     public <U> Supplier<U> wrapArgument(Supplier<U> original, boolean async) {
         return ctxRelay.contextual(original);
     }

     /**
      * Wraps the callback with the context snapshot captured <b>at the moment of this call</b>.
      *
      * @param original the original callback
      * @param async    whether the callback is executed asynchronously (currently ignored)
      * @param <U>      the input type of the predicate
      * @return a context-bound copy of the callback
      */
     @Override
     public <U> Predicate<U> wrapArgument(Predicate<U> original, boolean async) {
         return ctxRelay.contextual(original);
     }

     /**
      * Wraps the callback with the context snapshot captured <b>at the moment of this call</b>.
      *
      * @param original the original callback
      * @param async    whether the callback is executed asynchronously (currently ignored)
      * @param <U>      the first input type
      * @param <V>      the second input type
      * @param <R>      the result type
      * @return a context-bound copy of the callback
      */
     @Override
     public <U, V, R> BiFunction<U, V, R> wrapArgument(BiFunction<U, V, R> original, boolean async) {
         return ctxRelay.contextual(original);
     }

     /**
      * Wraps the callback with the context snapshot captured <b>at the moment of this call</b>.
      *
      * @param original the original callback
      * @param async    whether the callback is executed asynchronously (currently ignored)
      * @param <U>      the first input type
      * @param <V>      the second input type
      * @return a context-bound copy of the callback
      */
     @Override
     public <U, V> BiConsumer<U, V> wrapArgument(BiConsumer<U, V> original, boolean async) {
         return ctxRelay.contextual(original);
     }

     /*
     @Override
     public <U> CompletionStage<U> wrapArgument(CompletionStage<U> original, boolean async) {
         // TODO Option to wrap original CompletionStage?
         return PromiseCustomizer.super.wrapArgument(original, async);
     }
     */
}