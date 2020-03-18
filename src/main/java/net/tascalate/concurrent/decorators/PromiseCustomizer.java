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

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface PromiseCustomizer {
    
    default Runnable wrapArgument(Runnable original, boolean async) {
        return original;
    }

    default <U, R> Function<U, R> wrapArgument(Function<U, R> original, boolean async, boolean isCompose) {
        return original;
    }
    
    default <U> Consumer<U> wrapArgument(Consumer<U> original, boolean async) {
        return original;
    }
    
    default <U> Supplier<U> wrapArgument(Supplier<U> original, boolean async) {
        return original;
    }
    
    default <U> Predicate<U> wrapArgument(Predicate<U> original, boolean async) {
        return original;
    }
    
    default <U, V, R> BiFunction<U, V, R> wrapArgument(BiFunction<U, V, R> original, boolean async) {
        return original;
    }
    
    default <U, V> BiConsumer<U, V> wrapArgument(BiConsumer<U, V> original, boolean async) {
        return original;
    }
    
    default <U> CompletionStage<U> wrapArgument(CompletionStage<U> original, boolean async) {
        return original;
    }
    
    default Executor wrapArgument(Executor original) {
        return original;
    }
}
