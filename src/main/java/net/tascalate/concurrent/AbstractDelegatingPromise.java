package net.tascalate.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

abstract public class AbstractDelegatingPromise<T, D extends CompletionStage<T>> implements Promise<T> {
    final protected D completionStage;

    protected AbstractDelegatingPromise(final D delegate) {
        this.completionStage = delegate;
    }
    
    abstract protected <U> Promise<U> wrap(CompletionStage<U> original);

    public <U> Promise<U> thenApply(Function<? super T, ? extends U> fn) {
        return wrap(completionStage.thenApply(fn));
    }

    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return wrap(completionStage.thenApplyAsync(fn));
    }

    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return wrap(completionStage.thenApplyAsync(fn, executor));
    }

    public Promise<Void> thenAccept(Consumer<? super T> action) {
        return wrap(completionStage.thenAccept(action));
    }

    public Promise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return wrap(completionStage.thenAcceptAsync(action));
    }

    public Promise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return wrap(completionStage.thenAcceptAsync(action, executor));
    }

    public Promise<Void> thenRun(Runnable action) {
        return wrap(completionStage.thenRun(action));
    }

    public Promise<Void> thenRunAsync(Runnable action) {
        return wrap(completionStage.thenRunAsync(action));
    }

    public Promise<Void> thenRunAsync(Runnable action, Executor executor) {
        return wrap(completionStage.thenRunAsync(action, executor));
    }

    public <U, V> Promise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return wrap(completionStage.thenCombine(other, fn));
    }

    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return wrap(completionStage.thenCombineAsync(other, fn));
    }

    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                              BiFunction<? super T, ? super U, ? extends V> fn, 
                                              Executor executor) {
        
        return wrap(completionStage.thenCombineAsync(other, fn, executor));
    }

    public <U> Promise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return wrap(completionStage.thenAcceptBoth(other, action));
    }

    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return wrap(completionStage.thenAcceptBothAsync(other, action));
    }

    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                 BiConsumer<? super T, ? super U> action, 
                                                 Executor executor) {
        
        return wrap(completionStage.thenAcceptBothAsync(other, action, executor));
    }

    public Promise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return wrap(completionStage.runAfterBoth(other, action));
    }

    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return wrap(completionStage.runAfterBothAsync(other, action));
    }

    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                           Runnable action, 
                                           Executor executor) {
        return wrap(completionStage.runAfterBothAsync(other, action, executor));
    }

    public <U> Promise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return wrap(completionStage.applyToEither(other, fn));
    }

    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return wrap(completionStage.applyToEitherAsync(other, fn));
    }

    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                             Function<? super T, U> fn,
                                             Executor executor) {
        
        return wrap(completionStage.applyToEitherAsync(other, fn, executor));
    }

    public Promise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return wrap(completionStage.acceptEither(other, action));
    }

    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return wrap(completionStage.acceptEitherAsync(other, action));
    }

    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                           Consumer<? super T> action,
                                           Executor executor) {
        
        return wrap(completionStage.acceptEitherAsync(other, action, executor));
    }

    public Promise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return wrap(completionStage.runAfterEither(other, action));
    }

    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return wrap(completionStage.runAfterEitherAsync(other, action));
    }

    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                             Runnable action, 
                                             Executor executor) {
        return wrap(completionStage.runAfterEitherAsync(other, action, executor));
    }

    public <U> Promise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return wrap(completionStage.thenCompose(fn));
    }

    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return wrap(completionStage.thenComposeAsync(fn));
    }

    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return wrap(completionStage.thenComposeAsync(fn, executor));
    }

    public Promise<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return wrap(completionStage.exceptionally(fn));
    }

    public Promise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return wrap(completionStage.whenComplete(action));
    }

    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return wrap(completionStage.whenCompleteAsync(action));
    }

    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return wrap(completionStage.whenCompleteAsync(action, executor));
    }

    public <U> Promise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return wrap(completionStage.handle(fn));
    }

    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return wrap(completionStage.handleAsync(fn));
    }

    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return wrap(completionStage.handleAsync(fn, executor));
    }

    public CompletableFuture<T> toCompletableFuture() {
        return completionStage.toCompletableFuture();
    }

}