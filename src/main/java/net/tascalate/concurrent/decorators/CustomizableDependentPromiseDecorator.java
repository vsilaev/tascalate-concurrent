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

import net.tascalate.concurrent.DependentPromise;

/**
 * A concrete {@link DependentPromise} decorator that applies a {@link PromiseCustomizer}
 * to all arguments passed to composition methods, while preserving the dependent-promise
 * API (i.e., the ability to explicitly enlist upstream stages for cancellation).
 * <p>
 * This class is the dependent-promise counterpart of {@link CustomizablePromiseDecorator}.
 * It extends {@link ExtendedDependentPromiseDecorator} and overrides all
 * {@code wrapArgument(...)} methods to delegate to the provided customizer.
 * </p>
 *
 * <h2>Decoration Chain Preservation</h2>
 * <p>
 * The customizer is preserved across the composition chain:
 * {@link #wrapResult(CompletionStage)} wraps derived stages in a new
 * {@code CustomizableDependentPromiseDecorator} using the same customizer,
 * ensuring that cross-cutting concerns are applied consistently throughout
 * the entire dependent pipeline.
 * </p>
 *
 * @param <T> the type of the successfully resolved promise value
 * @author vsilaev
 * @see PromiseCustomizer
 * @see CustomizablePromiseDecorator
 * @see ExtendedDependentPromiseDecorator
 */
public class CustomizableDependentPromiseDecorator<T> extends ExtendedDependentPromiseDecorator<T> {

    /** The customizer used to transform composition arguments. */
    private final PromiseCustomizer customizer;

    /**
     * Creates a new customizable dependent-promise decorator.
     *
     * @param delegate  the {@link DependentPromise} to delegate calls to
     * @param customizer the customizer to apply to composition arguments
     */
    public CustomizableDependentPromiseDecorator(DependentPromise<T> delegate, PromiseCustomizer customizer) {
        super(delegate);
        this.customizer = customizer;
    }

    /** {@inheritDoc} */
    @Override
    protected Runnable wrapArgument(Runnable original, boolean async) {
        return customizer.wrapArgument(original, async);
    }

    /** {@inheritDoc} */
    @Override
    protected <U, R> Function<U, R> wrapArgument(Function<U, R> original, boolean async, boolean isCompose) {
        return customizer.wrapArgument(original, async, isCompose);
    }

    /** {@inheritDoc} */
    @Override
    protected <U> Consumer<U> wrapArgument(Consumer<U> original, boolean async) {
        return customizer.wrapArgument(original, async);
    }

    /** {@inheritDoc} */
    @Override
    protected <U> Supplier<U> wrapArgument(Supplier<U> original, boolean async) {
        return customizer.wrapArgument(original, async);
    }

    /** {@inheritDoc} */
    @Override
    protected <U> Predicate<U> wrapArgument(Predicate<U> original, boolean async) {
        return customizer.wrapArgument(original, async);
    }

    /** {@inheritDoc} */
    @Override
    protected <U, V, R> BiFunction<U, V, R> wrapArgument(BiFunction<U, V, R> original, boolean async) {
        return customizer.wrapArgument(original, async);
    }

    /** {@inheritDoc} */
    @Override
    protected <U, V> BiConsumer<U, V> wrapArgument(BiConsumer<U, V> original, boolean async) {
        return customizer.wrapArgument(original, async);
    }

    /** {@inheritDoc} */
    @Override
    protected <U> CompletionStage<U> wrapArgument(CompletionStage<U> original, boolean async) {
        return customizer.wrapArgument(original, async);
    }

    /** {@inheritDoc} */
    @Override
    protected Executor wrapArgument(Executor original) {
        return customizer.wrapArgument(original);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Wraps the result in a new {@code CustomizableDependentPromiseDecorator} using
     * the same customizer, preserving both the dependent-promise API and the
     * decoration chain.
     * </p>
     */
    @Override
    protected <U> DependentPromise<U> wrapResult(CompletionStage<U> original) {
        return new CustomizableDependentPromiseDecorator<>((DependentPromise<U>)original, customizer);
    }
}