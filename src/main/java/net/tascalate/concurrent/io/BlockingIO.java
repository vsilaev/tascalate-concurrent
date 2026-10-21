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
package net.tascalate.concurrent.io;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
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
import net.tascalate.concurrent.var.ContextVar;

/**
 * Utility class for making legacy, non-interruptible blocking I/O operations 
 * responsive to {@link Promise} cancellation.
 *
 * <h2>The Problem</h2>
 * <p>
 * In pre-Loom Java (JDK 8–20), calling {@link Thread#interrupt()} on a thread 
 * blocked in standard I/O (e.g., {@link java.net.Socket} {@code read()}, 
 * {@link java.io.InputStream#read()}) does <b>not</b> unblock the thread. It merely 
 * sets the interrupt flag, leaving the thread permanently stuck until the I/O 
 * operation naturally completes or times out.
 * </p>
 *
 * <h2>The Solution</h2>
 * <p>
 * This class provides a mechanism to forcefully unblock such operations by 
 * closing the underlying resources when an interruption occurs. It works by:
 * </p>
 * <ol>
 *   <li>Registering the current thread with the JVM's internal interruption 
 *       mechanism via {@link BlockingThreadSelector#enter()} (which calls 
 *       {@link java.nio.channels.spi.AbstractSelector#begin()}).</li>
 *   <li>Tracking {@link AutoCloseable} resources (like Sockets or Channels) 
 *       via {@link #register(AutoCloseable)}.</li>
 *   <li>If the thread is interrupted (e.g., via {@link Promise#cancel(boolean)}), 
 *       the JVM invokes the selector's {@link java.nio.channels.Selector#wakeup()}, 
 *       which this class overrides to forcefully {@link AutoCloseable#close()} all 
 *       registered resources.</li>
 *   <li>Closing the resource unblocks the I/O operation, causing it to throw an 
 *       exception (e.g., {@link java.nio.channels.ClosedByInterruptException} or 
 *       {@link java.net.SocketException}), allowing the thread to proceed and 
 *       handle the cancellation.</li>
 * </ol>
 *
 * <h2>Caveats and Limitations</h2>
 * <ul>
 *   <li><b>JDK 21+ Virtual Threads:</b> In JDK 21+, Virtual Threads natively 
 *       handle I/O interruption. This hack is largely redundant and potentially 
 *       problematic if mixed with virtual threads. It is primarily intended for 
 *       pre-Loom JDKs (8–20) or when explicitly using platform threads.</li>
 *   <li><b>Thread Confinement:</b> The {@code BlockingContext} context is strictly 
 *       bound to the executing thread via a {@link ThreadLocal}. It does 
 *       <b>not</b> automatically propagate across thread boundaries (e.g., inside 
 *       {@code thenApplyAsync}). To make async composition stages interruptible, 
 *       you must explicitly wrap them using {@link #interruptiblePromises()} or 
 *       the functional wrappers provided here.</li>
 *   <li><b>Resource Closure Side Effects:</b> Closing a resource while another 
 *       thread is blocked on it will cause that blocking operation to throw an 
 *       exception. This is the <i>intended</i> behavior to unblock the thread, 
 *       but application code must be prepared to handle these exceptions as 
 *       part of the cancellation flow.</li>
 * </ul>
 *
 * @author vsilaev
 * @see BlockingThreadSelector
 * @see Promise#cancel(boolean)
 */
public final class BlockingIO {
    // Portable context variable: ThreadLocal on JDK 8-24, ScopedValue on JDK 25+
    private static final ContextVar<BlockingContext> CURRENT = ContextVar.newInstance("blocking-io-context");
    
    private static final PromiseCustomizer INTERRUPTIBLE_PROMISE_CUSTOMIZER = new InterruptiblePromiseCustomizer();
    
    /**
     * Internal context holder for the current thread's interruptible block.
     */
    final static class BlockingContext {
        // Switched to ArrayList: since BlockingContext is bound to a single thread's scope, 
        // it is only ever modified by that thread.
        private final List<AutoCloseable> closeables = new ArrayList<>();
        private final BlockingThreadSelector selector = new BlockingThreadSelector(this::interrupted);

        void enter() {
            selector.enter();
        }
        
        void exit() {
            selector.exit();
        }
        
        final void interrupted() {
            for (AutoCloseable closeable : closeables) {
                close(closeable);
            }
            closeables.clear();
        }

        void enlist(AutoCloseable closeOnInterruption) {
            closeables.add(closeOnInterruption);
            // Race condition check: if interrupted right after enlistment
            if (Thread.currentThread().isInterrupted()) {
                close(closeOnInterruption);
            }
        }

        private static void close(AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ex) {
                // Ignore exceptions during forced closure
            }            
        }
    }

    /**
     * Registers an {@link AutoCloseable} resource to be forcefully closed if the 
     * current thread is interrupted while inside an {@code interruptible} block.
     *
     * @param <T> the type of the resource
     * @param closeOnInterruption the resource to register
     * @return the same resource, for fluent chaining
     * @throws IllegalStateException if called outside of an {@code interruptible} block
     */
    public static <T extends AutoCloseable> T register(T closeOnInterruption) {
        BlockingContext current = CURRENT.get();
        if (current == null) {
            throw new IllegalStateException("BlockingContext closeables may be registered only within interruptible blocks");
        }
        current.enlist(closeOnInterruption);
        return closeOnInterruption;
    }
    
    // ========================================================================
    // Core execution helpers: bind ContextVar AND manage selector enter/exit
    // ========================================================================

    static void runWithContext(Runnable action) {
        BlockingContext ctx = new BlockingContext();
        ContextVar.bind(CURRENT, ctx).run(() -> {
            ctx.enter();
            try {
                action.run();
            } finally {
                ctx.exit();
            }
        });
    }

    static <V> V supplyWithContext(Supplier<V> action) {
        BlockingContext ctx = new BlockingContext();
        return ContextVar.bind(CURRENT, ctx).supply(() -> {
            ctx.enter();
            try {
                return action.get();
            } finally {
                ctx.exit();
            }
        });
    }

    static <V> V callWithContext(Callable<V> action) throws Exception {
        BlockingContext ctx = new BlockingContext();
        return ContextVar.bind(CURRENT, ctx).call(() -> {
            ctx.enter();
            try {
                return action.call();
            } finally {
                ctx.exit();
            }
        });
    }

    // ========================================================================
    // Functional Wrappers
    // ========================================================================

    /**
     * Wraps a {@link Runnable} so that it executes within an interruptible context.
     *
     * @param action the action to wrap
     * @return a new {@link Runnable} that manages the interruptible lifecycle
     */
    public static Runnable interruptible(Runnable action) {
        return () -> runWithContext(action);
    }

    /**
     * Wraps a {@link Consumer} so that it executes within an interruptible context.
     *
     * @param <T> the input type of the consumer
     * @param action the consumer to wrap
     * @return a new {@link Consumer} that manages the interruptible lifecycle
     */
    public static <T> Consumer<T> interruptible(Consumer<T> action) {
        return v -> runWithContext(() -> action.accept(v));
    }

    /**
     * Wraps a {@link BiConsumer} so that it executes within an interruptible context.
     *
     * @param <T> the first input type
     * @param <U> the second input type
     * @param action the bi-consumer to wrap
     * @return a new {@link BiConsumer} that manages the interruptible lifecycle
     */
    public static <T, U> BiConsumer<T, U> interruptible(BiConsumer<T, U> action) {
        return (t, u) -> runWithContext(() -> action.accept(t, u));
    }

    /**
     * Wraps a {@link Callable} so that it executes within an interruptible context.
     *
     * @param <V> the result type of the callable
     * @param action the callable to wrap
     * @return a new {@link Callable} that manages the interruptible lifecycle
     */
    public static <V> Callable<V> interruptibleCall(Callable<V> action) {
        return () -> callWithContext(action);
    }

    /**
     * Wraps a {@link Supplier} so that it executes within an interruptible context.
     *
     * @param <T> the result type of the supplier
     * @param action the supplier to wrap
     * @return a new {@link Supplier} that manages the interruptible lifecycle
     */
    public static <T> Supplier<T> interruptible(Supplier<T> action) {
        return () -> supplyWithContext(action);
    }

    /**
     * Wraps a {@link Function} so that it executes within an interruptible context.
     *
     * @param <T> the input type
     * @param <R> the result type
     * @param action the function to wrap
     * @return a new {@link Function} that manages the interruptible lifecycle
     */
    public static <T, R> Function<T, R> interruptible(Function<T, R> action) {
        return v -> supplyWithContext(() -> action.apply(v));
    }

    /**
     * Wraps a {@link BiFunction} so that it executes within an interruptible context.
     *
     * @param <T> the first input type
     * @param <U> the second input type
     * @param <R> the result type
     * @param action the bi-function to wrap
     * @return a new {@link BiFunction} that manages the interruptible lifecycle
     */
    public static <T, U, R> BiFunction<T, U, R> interruptible(BiFunction<T, U, R> action) {
        return (t, u) -> supplyWithContext(() -> action.apply(t, u));
    }

    /**
     * Wraps a {@link Predicate} so that it executes within an interruptible context.
     *
     * @param <T> the input type
     * @param action the predicate to wrap
     * @return a new {@link Predicate} that manages the interruptible lifecycle
     */
    public static <T> Predicate<T> interruptible(Predicate<T> action) {
        return v -> supplyWithContext(() -> action.test(v));
    }

    /**
     * Wraps a {@link BiPredicate} so that it executes within an interruptible context.
     *
     * @param <T> the first input type
     * @param <U> the second input type
     * @param action the bi-predicate to wrap
     * @return a new {@link BiPredicate} that manages the interruptible lifecycle
     */
    public static <T, U> BiPredicate<T, U> interruptible(BiPredicate<T, U> action) {
        return (t, u) -> supplyWithContext(() -> action.test(t, u));
    }
    
    // --- Promise decorators ---
    /**
     * Returns a decorator that automatically wraps all composition callbacks 
     * (e.g., {@code thenApply}, {@code thenAccept}) of a {@link Promise} with 
     * the {@link #interruptible(Runnable)} logic.
     * <p>
     * This ensures that any blocking I/O performed inside those async callbacks 
     * can be interrupted if the parent {@link Promise} is cancelled.
     * </p>
     *
     * @param <T> the result type of the promise
     * @return a function that decorates a {@link Promise} to be interruptible
     */
    public static <T> Function<Promise<T>, Promise<T>> interruptiblePromises() {
        return p -> p instanceof DependentPromise ?
            new CustomizableDependentPromiseDecorator<>((DependentPromise<T>) p, INTERRUPTIBLE_PROMISE_CUSTOMIZER) :
            new CustomizablePromiseDecorator<>(p, INTERRUPTIBLE_PROMISE_CUSTOMIZER);
    }

    /**
     * Type-preserving variant of {@link #interruptiblePromises()} for chains that 
     * operate on {@link DependentPromise} and must keep its extended API surface.
     *
     * @param <T> the result type of the promise
     * @return a function that decorates a {@link DependentPromise} to be interruptible
     */
    public static <T> Function<DependentPromise<T>, DependentPromise<T>> interruptibleDependentPromises() {
        return p -> new CustomizableDependentPromiseDecorator<>(p, INTERRUPTIBLE_PROMISE_CUSTOMIZER);
    }
}