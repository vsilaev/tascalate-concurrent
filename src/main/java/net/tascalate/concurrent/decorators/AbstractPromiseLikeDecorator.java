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

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.core.Decorator;

/**
 * Abstract base class that bridges {@link CompletionStage} decoration to the 
 * {@link Promise} API.
 * <p>
 * This class extends {@link AbstractCompletionStageDecorator} and implements the 
 * {@link Decorator} interface. It overrides all {@link CompletionStage} composition 
 * methods to return {@link Promise} instead of {@link CompletionStage}, ensuring that 
 * the decoration chain is preserved and type-safe when working with the Tascalate 
 * Concurrent API.
 * </p>
 * <p>
 * It also implements the {@link #α()} (alpha) operator, which traverses the decorator 
 * chain to find the innermost, root origin stage. This is crucial for operations like 
 * cancellation propagation, which need to target the actual underlying execution rather 
 * than the decorator wrappers.
 * </p>
 * <h2>Class Hierarchy</h2>
 * <pre>{@code
 * AbstractCompletionStageDecorator
 *     ├── CompletionStageDecorator
 *     │       └── ExecutorBoundCompletionStage
 *     └── AbstractPromiseLikeDecorator  <-- YOU ARE HERE (Adds Promise return types + α-operator)
 *             └── AbstractFutureDecorator
 *                     ├── CompletableFutureDecorator
 *                     └── BlockingCompletionStageDecorator
 *
 * AbstractPromiseDecorator
 *     ├── ExecutorBoundPromise
 *     ├── ExtendedPromiseDecorator
 *     │       └── CustomizablePromiseDecorator
 *     └── AbstractDependentPromiseDecorator
 *             ├── ExecutorBoundDependentPromise
 *             └── ExtendedDependentPromiseDecorator
 *                     └── CustomizableDependentPromiseDecorator
 * }</pre>
 * @param <T> a type of the successfully resolved promise value
 * @param <D> a type of the concrete {@link CompletionStage} subclass being decorated
 * @author vsilaev
 * @see AbstractCompletionStageDecorator
 * @see AbstractFutureDecorator
 * @see Promise
 */
public abstract class AbstractPromiseLikeDecorator<T, D extends CompletionStage<T>>
    extends AbstractCompletionStageDecorator<T, D>
    implements CompletionStage<T>, Decorator<T> {
    
    /**
     * Creates a new promise-like decorator wrapping the specified delegate.
     *
     * @param delegate the {@link CompletionStage} to delegate calls to
     */
    protected AbstractPromiseLikeDecorator(D delegate) {
         super(delegate);
     }

     /**
      * {@inheritDoc}
      * <p>
      * Casts the result of {@link AbstractCompletionStageDecorator#wrap(CompletionStage)} 
      * to a {@link Promise}. This is safe because the underlying delegate is expected to 
      * be a {@link Promise}, and the decorator chain preserves this type.
      * </p>
      */
     @Override
     protected <U> Promise<U> wrap(CompletionStage<U> original) {
         return (Promise<U>)super.wrap(original);
     }

     /**
      * {@inheritDoc}
      * <p>
      * Subclasses must implement this to return a {@link Promise} wrapper for derived stages.
      * </p>
      */
     @Override
     abstract protected <U> Promise<U> wrapNew(CompletionStage<U> original);

     /**
      * Internal helper to safely cast a {@link CompletionStage} to a {@link Promise}.
      */
     private static <U> Promise<U> cast(CompletionStage<U> stage) {
         return (Promise<U>)stage;
     }

     // -- The following methods override the CompletionStage methods to return Promise --
     @Override
     public <U> Promise<U> thenApply(Function<? super T, ? extends U> fn) {
         return cast(super.thenApply(fn));
     }

     @Override
     public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
         return cast(super.thenApplyAsync(fn));
     }

     @Override
     public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
         return cast(super.thenApplyAsync(fn, executor));
     }

     @Override
     public Promise<Void> thenAccept(Consumer<? super T> action) {
         return cast(super.thenAccept(action));
     }

     @Override
     public Promise<Void> thenAcceptAsync(Consumer<? super T> action) {
         return cast(super.thenAcceptAsync(action));
     }

     @Override
     public Promise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
         return cast(super.thenAcceptAsync(action, executor));
     }

     @Override
     public Promise<Void> thenRun(Runnable action) {
         return cast(super.thenRun(action));
     }

     @Override
     public Promise<Void> thenRunAsync(Runnable action) {
         return cast(super.thenRunAsync(action));
     }

     @Override
     public Promise<Void> thenRunAsync(Runnable action, Executor executor) {
         return cast(super.thenRunAsync(action, executor));
     }

     @Override
     public <U, V> Promise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
         return cast(super.thenCombine(other, fn));
     }
    
     @Override
     public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
         return cast(super.thenCombineAsync(other, fn));
     }

     @Override
     public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                               BiFunction<? super T, ? super U, ? extends V> fn, 
                                              Executor executor) {
        
         return cast(super.thenCombineAsync(other, fn, executor));
     }

     @Override
     public <U> Promise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
         return cast(super.thenAcceptBoth(other, action));
     }

     @Override
     public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
         return cast(super.thenAcceptBothAsync(other, action));
     }

     @Override
     public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                  BiConsumer<? super T, ? super U> action, 
                                                  Executor executor) {
        
         return cast(super.thenAcceptBothAsync(other, action, executor));
     }

     @Override
     public Promise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
         return cast(super.runAfterBoth(other, action));
     }

     @Override
     public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
         return cast(super.runAfterBothAsync(other, action));
     }

     @Override
     public Promise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                            Runnable action, 
                                            Executor executor) {
         return cast(super.runAfterBothAsync(other, action, executor));
     }

     @Override
     public <U> Promise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
         return cast(super.applyToEither(other, fn));
     }

     @Override
     public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
         return cast(super.applyToEitherAsync(other, fn));
     }

     @Override
     public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                              Function<? super T, U> fn,
                                              Executor executor) {
        
         return cast(super.applyToEitherAsync(other, fn, executor));
     }

     @Override
     public Promise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
         return cast(super.acceptEither(other, action));
     }

     @Override
     public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
         return cast(super.acceptEitherAsync(other, action));
     }

     @Override
     public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                            Consumer<? super T> action,
                                            Executor executor) {
        
         return cast(super.acceptEitherAsync(other, action, executor));
     }

     @Override
     public Promise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
         return cast(super.runAfterEither(other, action));
     }

     @Override
     public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
         return cast(super.runAfterEitherAsync(other, action));
     }

     @Override
     public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                              Runnable action, 
                                              Executor executor) {
         return cast(super.runAfterEitherAsync(other, action, executor));
     }

     @Override
     public <U> Promise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
         return cast(super.thenCompose(fn));
     }

     @Override
     public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
         return cast(super.thenComposeAsync(fn));
     }

     @Override
     public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
         return cast(super.thenComposeAsync(fn, executor));
     }

     @Override
     public Promise<T> exceptionally(Function<Throwable, ? extends T> fn) {
         return cast(super.exceptionally(fn));
     }

     /* Since Java 12 */
     @Override
     public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
         return cast(super.exceptionallyAsync(fn));
     }
    
     /* Since Java 12 */
     @Override
     public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
         return cast(super.exceptionallyAsync(fn, executor));
     }
    
     /* Since Java 12 */
     @Override
     public Promise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
         return cast(super.exceptionallyCompose(fn));
     }
    
     /* Since Java 12 */
     @Override
     public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
         return cast(super.exceptionallyComposeAsync(fn));
     }

     /* Since Java 12 */
     @Override
     public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
         return cast(super.exceptionallyComposeAsync(fn, executor));
     }

     @Override
     public Promise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
         return cast(super.whenComplete(action));
     }

     @Override
     public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
         return cast(super.whenCompleteAsync(action));
     }

     @Override
     public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
         return cast(super.whenCompleteAsync(action, executor));
     }

     @Override
     public <U> Promise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
         return cast(super.handle(fn));
     }

     @Override
     public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
         return cast(super.handleAsync(fn));
     }

     @Override
     public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
         return cast(super.handleAsync(fn, executor));
     }
    
     /**
      * The "alpha" operator: finds the innermost, root origin of the decorator chain.
      * <p>
      * This method traverses through any nested {@link Decorator} or 
      * {@link AbstractCompletionStageDecorator} layers to find the actual underlying 
      * {@link CompletionStage} that represents the root of the computation. This is 
      * essential for operations like cancellation, which must target the root execution 
      * rather than the decorator wrappers.
      * </p>
      *
      * @return the innermost root {@link CompletionStage}
      */
     // Alpha-operator -- the origin of origins
     @Override
     public CompletionStage<T> α() {
         CompletionStage<T> p = delegate;
         if (p instanceof Decorator) {
             return decorator(p).α();
         } else {
             // Default path -- unroll
             while (p instanceof AbstractCompletionStageDecorator) {
                 @SuppressWarnings("unchecked")
                 AbstractCompletionStageDecorator<T, ? extends CompletionStage<T>> ap = 
                     (AbstractCompletionStageDecorator<T, ? extends CompletionStage<T>>)p;
                 p = ap.delegate;
                 if (p instanceof Decorator) {
                     return decorator(p).α();
                 }
             }
             return p;
         }
     }

     /**
      * Internal helper to safely cast a {@link CompletionStage} to a {@link Decorator}.
      */
     @SuppressWarnings("unchecked")
     private static <T> Decorator<T> decorator(CompletionStage<T> delegate) {
         return (Decorator<T>)delegate;
     }
}
