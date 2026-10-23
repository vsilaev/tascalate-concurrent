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
package net.tascalate.concurrent.decorators;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A strategy interface for intercepting and transforming arguments passed to
 * {@link net.tascalate.concurrent.Promise} composition methods.
 * <p>
 * Implementations of this interface are used by {@link CustomizablePromiseDecorator}
 * and {@link CustomizableDependentPromiseDecorator} to wrap callbacks, stages, and
 * executors before they are delegated to the underlying promise. This enables
 * cross-cutting concerns such as:
 * </p>
 * <ul>
 *   <li>Context propagation (e.g., capturing and restoring thread-local or scoped
 *       variables across asynchronous boundaries);</li>
 *   <li>Blocking I/O interruption (e.g., wrapping callbacks to register resources
 *       for forced closure upon thread interruption);</li>
 *   <li>Logging, metrics, or distributed tracing;</li>
 *   <li>Executor substitution or decoration.</li>
 * </ul>
 * <p>
 * All methods in this interface provide a default implementation that simply returns
 * the original argument unchanged. Implementations only need to override the specific
 * {@code wrapArgument} methods relevant to their use case.
 * </p>
 *
 * @author vsilaev
 * @see CustomizablePromiseDecorator
 * @see CustomizableDependentPromiseDecorator
 */
public interface PromiseCustomizer {

    /**
     * Wraps a {@link Runnable} argument before it is passed to the delegate.
     *
     * @param original the original action
     * @param async    {@code true} if the action will be executed asynchronously
     *                 (i.e., the {@code *Async} variant of the method was called)
     * @return the (possibly wrapped) action
     */
    default Runnable wrapArgument(Runnable original, boolean async) {
        return original;
    }

    /**
     * Wraps a {@link Function} argument before it is passed to the delegate.
     *
     * @param <U>       the input type of the function
     * @param <R>       the result type of the function
     * @param original  the original function
     * @param async     {@code true} if the function will be executed asynchronously
     * @param isCompose {@code true} if this is a flatMap-style operation
     *                  (e.g., {@code thenCompose}, {@code exceptionallyCompose});
     *                  {@code false} for map-style operations (e.g., {@code thenApply})
     * @return the (possibly wrapped) function
     */
    default <U, R> Function<U, R> wrapArgument(Function<U, R> original, boolean async, boolean isCompose) {
        return original;
    }

    /**
     * Wraps a {@link Consumer} argument before it is passed to the delegate.
     *
     * @param <U>       the input type of the consumer
     * @param original  the original consumer
     * @param async     {@code true} if the consumer will be executed asynchronously
     * @return the (possibly wrapped) consumer
     */
    default <U> Consumer<U> wrapArgument(Consumer<U> original, boolean async) {
        return original;
    }

    /**
     * Wraps a {@link Supplier} argument before it is passed to the delegate.
     *
     * @param <U>       the result type of the supplier
     * @param original  the original supplier
     * @param async     {@code true} if the supplier will be executed asynchronously
     * @return the (possibly wrapped) supplier
     */
    default <U> Supplier<U> wrapArgument(Supplier<U> original, boolean async) {
        return original;
    }

    /**
     * Wraps a {@link Predicate} argument before it is passed to the delegate.
     *
     * @param <U>       the input type of the predicate
     * @param original  the original predicate
     * @param async     {@code true} if the predicate will be executed asynchronously
     * @return the (possibly wrapped) predicate
     */
    default <U> Predicate<U> wrapArgument(Predicate<U> original, boolean async) {
        return original;
    }

    /**
     * Wraps a {@link BiFunction} argument before it is passed to the delegate.
     *
     * @param <U>       the first input type
     * @param <V>       the second input type
     * @param <R>       the result type
     * @param original  the original bi-function
     * @param async     {@code true} if the bi-function will be executed asynchronously
     * @return the (possibly wrapped) bi-function
     */
    default <U, V, R> BiFunction<U, V, R> wrapArgument(BiFunction<U, V, R> original, boolean async) {
        return original;
    }

    /**
     * Wraps a {@link BiConsumer} argument before it is passed to the delegate.
     *
     * @param <U>       the first input type
     * @param <V>       the second input type
     * @param original  the original bi-consumer
     * @param async     {@code true} if the bi-consumer will be executed asynchronously
     * @return the (possibly wrapped) bi-consumer
     */
    default <U, V> BiConsumer<U, V> wrapArgument(BiConsumer<U, V> original, boolean async) {
        return original;
    }

    /**
     * Wraps a secondary {@link CompletionStage} argument before it is passed
     * to a binary combinator (e.g., {@code thenCombine}, {@code applyToEither}).
     *
     * @param <U>       the result type of the stage
     * @param original  the original stage
     * @param async     {@code true} if the combinator will be executed asynchronously
     * @return the (possibly wrapped) stage
     */
    default <U> CompletionStage<U> wrapArgument(CompletionStage<U> original, boolean async) {
        return original;
    }

    /**
     * Wraps an {@link Executor} argument before it is passed to the delegate.
     * <p>
     * Subclasses can use this to substitute or decorate the executor used for
     * asynchronous execution.
     * </p>
     *
     * @param original the original executor
     * @return the (possibly wrapped) executor
     */
    default Executor wrapArgument(Executor original) {
        return original;
    }
}