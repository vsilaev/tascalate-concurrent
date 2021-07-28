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
package net.tascalate.concurrent.io;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.tascalate.concurrent.DependentPromise;
import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.decorators.CustomizableDependentPromiseDecorator;
import net.tascalate.concurrent.decorators.CustomizablePromiseDecorator;
import net.tascalate.concurrent.decorators.PromiseCustomizer;

final public class BlockingIO {
    
    private final static ThreadLocal<Interruptible> CURRENT = new ThreadLocal<>();
    private final static PromiseCustomizer INTERRUPTIBLE_PROMISE_CUSTOMIZER = new InterruptiblePromiseCustomizer();

    final static class Interruptible {
        private final List<AutoCloseable> closeables = new CopyOnWriteArrayList<>();
        private final BlockingThreadSelector selector = new BlockingThreadSelector(this::interrupted);

        final Interruptible enter() {
            Interruptible previous = CURRENT.get();
            CURRENT.set(this);
            selector.enter();
            return previous;
        }

        final void exit(Interruptible previous) {
            selector.exit();
            closeables.clear();
            if (null != previous) {
                CURRENT.set(previous);
            } else {
                CURRENT.remove();
            }
        }

        void interrupted() {
            for (AutoCloseable closeable : closeables) {
                close(closeable);
            }
            closeables.clear();
        }

        void enlist(AutoCloseable closeOnInterruption) {
            closeables.add(closeOnInterruption);
            // For possible race - check for thread interruption after addition to the list
            // This way closeOnInterruption.close() might be called twice in worst case, 
            // but it will be always called at least once (in case of interruption)
            if (Thread.currentThread().isInterrupted()) {
                close(closeOnInterruption);
            }
        }
        
        private static void close(AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ex) {

            }            
        }
    }

    public static <T extends AutoCloseable> T register(T closeOnInterruption) {
        Interruptible current = CURRENT.get();
        if (null == current) {
            throw new IllegalStateException("Interruptible closeables may be registered only within interruptible blocks");
        }
        current.enlist(closeOnInterruption);
        return closeOnInterruption;
    }
    
    public static Runnable interruptible(Runnable action) {
        Interruptible current = new Interruptible();
        return () -> {
            Interruptible previous = current.enter();
            try {
                action.run();
            } finally {
                current.exit(previous);
            }            
        };
    }
    
    public static <V> Callable<V> interruptibleCall(Callable<V> action) {
        Interruptible current = new Interruptible();
        return () -> {
            Interruptible previous = current.enter();
            try {
                return action.call();
            } finally {
                current.exit(previous);
            }            
        };
    }
    
    public static <T> Supplier<T> interruptible(Supplier<T> action) {
        Interruptible current = new Interruptible();
        return () -> {
            Interruptible previous = current.enter();
            try {
                return action.get();
            } finally {
                current.exit(previous);
            }            
        };
    }
    
    public static <T> Consumer<T> interruptible(Consumer<T> action) {
        Interruptible current = new Interruptible();
        return v -> {
            Interruptible previous = current.enter();
            try {
                action.accept(v);
            } finally {
                current.exit(previous);
            }            
        };
    }
    
    public static <T, U> BiConsumer<T,U> interruptible(BiConsumer<T, U> action) {
        Interruptible current = new Interruptible();
        return (t,u) -> {
            Interruptible previous = current.enter();
            try {
                action.accept(t,u);
            } finally {
                current.exit(previous);
            }            
        };
    }

    public static <T, R> Function<T, R> interruptible(Function<T, R> action) {
        Interruptible current = new Interruptible();
        return v -> {
            Interruptible previous = current.enter();
            try {
                return action.apply(v);
            } finally {
                current.exit(previous);
            }            
        };
    }
    
    public static <T, U, R> BiFunction<T, U, R> interruptible(BiFunction<T, U, R> action) {
        Interruptible current = new Interruptible();
        return (t,u) -> {
            Interruptible previous = current.enter();
            try {
                return action.apply(t, u);
            } finally {
                current.exit(previous);
            }            
        };
    }
    
    public static <T> Predicate<T> interruptible(Predicate<T> action) {
        Interruptible current = new Interruptible();
        return v -> {
            Interruptible previous = current.enter();
            try {
                return action.test(v);
            } finally {
                current.exit(previous);
            }            
        };
    }
    
    public static <T,U> BiPredicate<T,U> interruptible(BiPredicate<T,U> action) {
        Interruptible current = new Interruptible();
        return (t,u) -> {
            Interruptible previous = current.enter();
            try {
                return action.test(t, u);
            } finally {
                current.exit(previous);
            }            
        };
    }
    
    public static <T> Function<Promise<T>, Promise<T>> interruptiblePromises() {
        return p -> p instanceof DependentPromise ?
            new CustomizableDependentPromiseDecorator<>((DependentPromise<T>)p, INTERRUPTIBLE_PROMISE_CUSTOMIZER)
            :
            new CustomizablePromiseDecorator<>(p, INTERRUPTIBLE_PROMISE_CUSTOMIZER);
    }
    
    public <T> Function<DependentPromise<T>, DependentPromise<T>> interruptiblePromisesʹ() {
        return p -> new CustomizableDependentPromiseDecorator<>(p, INTERRUPTIBLE_PROMISE_CUSTOMIZER);
    }
}
