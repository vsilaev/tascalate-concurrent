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

/**
 * A concrete, non-abstract implementation of {@link AbstractCompletionStageDecorator}.
 * <p>
 * This class provides a straightforward wrapper around any {@link CompletionStage}. 
 * It is primarily useful as a base class for custom decorators that need to intercept 
 * only specific methods, or for simple type adaptation where a raw 
 * {@link CompletionStage} needs to be treated uniformly within a decorator chain.
 * </p>
 *
 * @param <T> a type of the successfully resolved promise value
 * @author vsilaev
 * @see AbstractCompletionStageDecorator
 */
public class CompletionStageDecorator<T> extends AbstractCompletionStageDecorator<T, CompletionStage<T>> {
    
    /**
     * Creates a new decorator wrapping the specified {@link CompletionStage}.
     *
     * @param delegate the {@link CompletionStage} to delegate calls to
     */
    public CompletionStageDecorator(CompletionStage<T> delegate) {
        super(delegate);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Creates a new {@link CompletionStageDecorator} wrapping the provided stage.
     * </p>
     */
    @Override
    protected <U> CompletionStage<U> wrapNew(CompletionStage<U> original) {
        return new CompletionStageDecorator<>(original);
    }
}