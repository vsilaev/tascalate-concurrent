package net.tascalate.concurrent;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public interface Promise<T> extends Future<T>, CompletionStage<T> {
   
    public <U> Promise<U> thenApply(Function<? super T, ? extends U> fn);

    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn);

    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor);
    
    public Promise<Void> thenAccept(Consumer<? super T> action);

    public Promise<Void> thenAcceptAsync(Consumer<? super T> action);

    public Promise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor);

    public Promise<Void> thenRun(Runnable action);

    public Promise<Void> thenRunAsync(Runnable action);

    public Promise<Void> thenRunAsync(Runnable action, Executor executor);

    public <U, V> Promise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn);

    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn);

    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                              BiFunction<? super T, ? super U, ? extends V> fn, 
                                              Executor executor);

    public <U> Promise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action);
    
    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action);

    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                 BiConsumer<? super T, ? super U> action, 
                                                 Executor executor);

    public Promise<Void> runAfterBoth(CompletionStage<?> other, Runnable action);

    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action);

    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor);

    public <U> Promise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn);
    
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn);

    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                             Function<? super T, U> fn,
                                             Executor executor);

    public Promise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action);

    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action);

    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                           Consumer<? super T> action,
                                           Executor executor);

    public Promise<Void> runAfterEither(CompletionStage<?> other, Runnable action);

    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action);

    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor);

    public <U> Promise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn);

    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn);

    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor);

    public Promise<T> exceptionally(Function<Throwable, ? extends T> fn);

    public Promise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action);

    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action);

    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor);

    public <U> Promise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn);

    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn);

    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor);

 }
