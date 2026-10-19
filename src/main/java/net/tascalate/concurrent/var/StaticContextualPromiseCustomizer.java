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
 * A {@link PromiseCustomizer} that propagates a <b>statically captured</b> context snapshot
 * to all callbacks of a decorated promise.
 * <p>
 * This customizer holds an immutable {@link Contextualization} — a context snapshot taken
 * <b>once, at construction time</b> (i.e., when {@link ContextRelay#contextualPromises()} or
 * {@link ContextRelay#contextualDependentPromises()} is invoked). Every callback wrapped by
 * this customizer runs within that same snapshot, regardless of when or where the callback
 * is registered or executed.
 * </p>
 * <p>
 * Use this flavor for reusable promise decorators created ahead of time. If the context must
 * instead be resolved at the moment each callback is chained, use
 * {@link DynamicContextualPromiseCustomizer} (via {@link ContextRelay#dynamicContextualPromises()}).
 * </p>
 * <p>
 * The {@code async} flag supplied by the promise framework is intentionally ignored: context
 * must be applied to callbacks whether they run synchronously or asynchronously.
 * </p>
 *
 * @see DynamicContextualPromiseCustomizer
 * @see ContextRelay#contextualPromises()
 * @see ContextRelay#contextualDependentPromises()
 */
final class StaticContextualPromiseCustomizer implements PromiseCustomizer {

    /** The fixed context snapshot applied to every wrapped callback. */
    private final Contextualization<?> ctxz;

    /**
     * Creates a customizer bound to the given context snapshot.
     *
     * @param ctxz the snapshot captured when the enclosing promise decorator was created
     */
    StaticContextualPromiseCustomizer(Contextualization<?> ctxz) {
         this.ctxz = ctxz;
     }

     /**
      * Wraps the callback so that it runs within the captured context snapshot.
      *
      * @param original the original callback
      * @param async    whether the callback is executed asynchronously (currently ignored)
      * @return a context-bound copy of the callback
      */
     @Override
     public Runnable wrapArgument(Runnable original, boolean async) {
         return () -> ctxz.runWith(original);
     }

     /**
      * Wraps the callback so that it runs within the captured context snapshot.
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
         return u -> ctxz.supplyWith(() -> original.apply(u));
     }

     /**
      * Wraps the callback so that it runs within the captured context snapshot.
      *
      * @param original the original callback
      * @param async    whether the callback is executed asynchronously (currently ignored)
      * @param <U>      the input type of the consumer
      * @return a context-bound copy of the callback
      */
     @Override
     public <U> Consumer<U> wrapArgument(Consumer<U> original, boolean async) {
         return u -> ctxz.runWith(() -> original.accept(u));
     }

     /**
      * Wraps the callback so that it runs within the captured context snapshot.
      *
      * @param original the original callback
      * @param async    whether the callback is executed asynchronously (currently ignored)
      * @param <U>      the result type of the supplier
      * @return a context-bound copy of the callback
      */
     @Override
     public <U> Supplier<U> wrapArgument(Supplier<U> original, boolean async) {
         return () -> ctxz.supplyWith(original);
     }

     /**
      * Wraps the callback so that it runs within the captured context snapshot.
      *
      * @param original the original callback
      * @param async    whether the callback is executed asynchronously (currently ignored)
      * @param <U>      the input type of the predicate
      * @return a context-bound copy of the callback
      */
     @Override
     public <U> Predicate<U> wrapArgument(Predicate<U> original, boolean async) {
         return u -> ctxz.supplyWith(() -> original.test(u));
     }

     /**
      * Wraps the callback so that it runs within the captured context snapshot.
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
         return (u, v) -> ctxz.supplyWith(() -> original.apply(u, v));
     }

     /**
      * Wraps the callback so that it runs within the captured context snapshot.
      *
      * @param original the original callback
      * @param async    whether the callback is executed asynchronously (currently ignored)
      * @param <U>      the first input type
      * @param <V>      the second input type
      * @return a context-bound copy of the callback
      */
     @Override
     public <U, V> BiConsumer<U, V> wrapArgument(BiConsumer<U, V> original, boolean async) {
         return (u, v) -> ctxz.runWith(() -> original.accept(u, v));
     }

     /*
     @Override
     public <U> CompletionStage<U> wrapArgument(CompletionStage<U> original, boolean async) {
         // TODO Option to wrap original CompletionStage?
         return PromiseCustomizer.super.wrapArgument(original, async);
     }
     */
}