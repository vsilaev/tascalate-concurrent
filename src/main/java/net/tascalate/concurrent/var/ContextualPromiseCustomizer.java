/**
 * Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.tascalate.concurrent.decorators.PromiseCustomizer;

class ContextualPromiseCustomizer implements PromiseCustomizer {
    private final Contextualization ctxz;
    
    ContextualPromiseCustomizer(Contextualization ctxz) {
        this.ctxz = ctxz;
    }
    
    @Override
    public Runnable wrapArgument(Runnable original, boolean async) {
        return () -> {
            List<Object> originalContext = ctxz.enter();
            try {
                original.run();
            } finally {
                ctxz.exit(originalContext);
            }
        };

    }

    @Override
    public <U, R> Function<U, R> wrapArgument(Function<U, R> original, boolean async, boolean isCompose) {
        return u -> {
            List<Object> originalContext = ctxz.enter();
            try {
                return original.apply(u);
            } finally {
                ctxz.exit(originalContext);
            }
        };
    }

    @Override
    public <U> Consumer<U> wrapArgument(Consumer<U> original, boolean async) {
        return u -> {
            List<Object> originalContext = ctxz.enter();
            try {
                original.accept(u);
            } finally {
                ctxz.exit(originalContext);
            }
        };
    }

    @Override
    public <U> Supplier<U> wrapArgument(Supplier<U> original, boolean async) {
        return () -> {
            List<Object> originalContext = ctxz.enter();
            try {
                return original.get();
            } finally {
                ctxz.exit(originalContext);
            }
        };
    }
    
    @Override
    public <U> Predicate<U> wrapArgument(Predicate<U> original, boolean async) {
        return u -> {
            List<Object> originalContext = ctxz.enter();
            try {
                return original.test(u);
            } finally {
                ctxz.exit(originalContext);
            }
        };
    }

    @Override
    public <U, V, R> BiFunction<U, V, R> wrapArgument(BiFunction<U, V, R> original, boolean async) {
        return (u, v) -> {
            List<Object> originalContext = ctxz.enter();
            try {
                return original.apply(u, v);
            } finally {
                ctxz.exit(originalContext);
            }
        };
    }

    @Override
    public <U, V> BiConsumer<U, V> wrapArgument(BiConsumer<U, V> original, boolean async) {
        return (u, v) -> {
            List<Object> originalContext = ctxz.enter();
            try {
                original.accept(u, v);
            } finally {
                ctxz.exit(originalContext);
            }
        };
    }

    /*
    @Override
    public <U> CompletionStage<U> wrapArgument(CompletionStage<U> original, boolean async) {
        // TODO Option to wrap original CompletionStage?
        return PromiseCustomizer.super.wrapArgument(original, async);
    }
    */
}
