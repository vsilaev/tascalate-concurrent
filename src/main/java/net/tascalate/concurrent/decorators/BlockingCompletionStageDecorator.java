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
import java.util.concurrent.Future;

import net.tascalate.concurrent.Promise;

/**
 * A {@link Promise} decorator for {@link CompletionStage} implementations that
 * <i>also</i> implement {@link Future}.
 * <p>
 * This decorator is used by
 * {@link net.tascalate.concurrent.CompletionStageWrapper#from(CompletionStage)}
 * to adapt third-party stages that happen to implement both interfaces. By
 * extending {@link AbstractFutureDecorator}, it delegates all blocking
 * {@link Future} operations ({@code get}, {@code cancel}, {@code isDone}, etc.)
 * directly to the underlying delegate, avoiding the need for a
 * {@link java.util.concurrent.CountDownLatch}-based blocking implementation.
 * </p>
 * <p>
 * This is a more efficient alternative to the generic
 * {@link net.tascalate.concurrent.CompletionStageWrapper} when the underlying
 * stage already supports the {@link Future} contract.
 * </p>
 *
 * @param <T> the type of the successfully resolved promise value
 * @param <D> the concrete type of the delegate (must implement both
 *            {@link CompletionStage} and {@link Future})
 * @author vsilaev
 * @see AbstractFutureDecorator
 * @see net.tascalate.concurrent.CompletionStageWrapper
 */
public class BlockingCompletionStageDecorator<T, D extends CompletionStage<T> & Future<T>>
extends AbstractFutureDecorator<T, D>
implements Promise<T> {

    /**
     * Creates a new decorator wrapping the specified delegate.
     *
     * @param delegate the {@link CompletionStage} and {@link Future} to delegate
     *                 calls to
     */
    protected BlockingCompletionStageDecorator(D delegate) {
        super(delegate);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Wraps the given {@link CompletionStage} in a new
     * {@code BlockingCompletionStageDecorator}, casting it to the expected
     * delegate type.
     * </p>
     *
     * @throws IllegalArgumentException if the {@code original} stage does not
     *                                  implement {@link Future}
     */
    @Override
    protected <U> Promise<U> wrapNew(CompletionStage<U> original) {
        return new BlockingCompletionStageDecorator<>(cast(original));
    }

    /**
     * Safely casts a {@link CompletionStage} to a type that implements both
     * {@link CompletionStage} and {@link Future}.
     *
     * @param <T>   the result type
     * @param <D>   the concrete delegate type
     * @param stage the stage to cast
     * @return the stage cast to the expected type
     * @throws IllegalArgumentException if the stage does not implement
     *                                  {@link Future}
     */
    private static <T, D extends CompletionStage<T> & Future<T>> D cast(CompletionStage<T> stage) {
        if (!(stage instanceof Future)) {
            throw new IllegalArgumentException(
                "Parameter must implement both " +
                CompletionStage.class.getName() + " and " +
                Future.class.getName()
            );
        }
        @SuppressWarnings("unchecked")
        D result = (D)stage;
        return result;
    }

    /**
     * Factory method that adapts a {@link CompletionStage} (which must also
     * implement {@link Future}) to the {@link Promise} API.
     *
     * @param <T>   the result type
     * @param stage the stage to adapt
     * @return a {@link Promise} wrapping the given stage
     * @throws IllegalArgumentException if the stage does not implement
     *                                  {@link Future}
     */
    public static <T> Promise<T> from(CompletionStage<T> stage) {
        return new BlockingCompletionStageDecorator<>(cast(stage));
    }
}