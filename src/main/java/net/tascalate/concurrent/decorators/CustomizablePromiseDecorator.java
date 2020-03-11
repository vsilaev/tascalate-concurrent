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
package net.tascalate.concurrent.decorators;

import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.tascalate.concurrent.DependentPromise;
import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.PromiseOrigin;

public class CustomizablePromiseDecorator<T> extends ExtendedPromiseDecorator<T> {
    
    private final PromiseCustomizer customizer;
    
    public CustomizablePromiseDecorator(Promise<T> delegate, PromiseCustomizer customizer) {
        super(delegate);
        this.customizer = customizer;
    }
    
    @Override
    protected Runnable wrapArgument(Runnable original, boolean async) {
        return customizer.wrapArgument(original, async);
    }

    @Override
    protected <U, R> Function<U, R> wrapArgument(Function<U, R> original, boolean async, boolean isCompose) {
        return customizer.wrapArgument(original, async, isCompose);
    }
    
    @Override
    protected <U> Consumer<U> wrapArgument(Consumer<U> original, boolean async) {
        return customizer.wrapArgument(original, async);
    }
    
    @Override
    protected <U> Supplier<U> wrapArgument(Supplier<U> original, boolean async) {
        return customizer.wrapArgument(original, async);
    }
    
    @Override
    protected <U, V, R> BiFunction<U, V, R> wrapArgument(BiFunction<U, V, R> original, boolean async) {
        return customizer.wrapArgument(original, async);
    }
    
    @Override
    protected <U, V> BiConsumer<U, V> wrapArgument(BiConsumer<U, V> original, boolean async) {
        return customizer.wrapArgument(original, async);
    }
    
    @Override
    protected <U> CompletionStage<U> wrapArgument(CompletionStage<U> original, boolean async) {
        return customizer.wrapArgument(original, async);
    }
    
    @Override
    protected Executor wrapArgument(Executor original) {
        return customizer.wrapArgument(original);
    }
    
    protected <U> Promise<U> wrapResult(CompletionStage<U> original) {
        return new CustomizablePromiseDecorator<>((Promise<U>)original, customizer);
    }
    
    @Override
    public DependentPromise<T> dependent() {
        return new CustomizableDependentPromiseDecorator<>(
            delegate.dependent(), customizer
        );
    }

    @Override
    public DependentPromise<T> dependent(Set<PromiseOrigin> defaultEnlistOptions) {
        return new CustomizableDependentPromiseDecorator<>(
            delegate.dependent(defaultEnlistOptions), customizer
        );
    }
}
