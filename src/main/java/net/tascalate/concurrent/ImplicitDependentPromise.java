package net.tascalate.concurrent;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

class ImplicitDependentPromise<T> extends ExplicitDependentPromise<T> {
    final private Set<PromiseOrigin> defaultEnlistOptions;
    
    ImplicitDependentPromise(Promise<T> delegate, Set<PromiseOrigin> defaultEnlistOptions, CompletionStage<?>[] cancellableOrigins) {
        super(delegate, cancellableOrigins);
        this.defaultEnlistOptions = defaultEnlistOptions;
    }
    
    static <U> DependentPromise<U> from(Promise<U> source, Set<PromiseOrigin> defaultEnlistOptions) {
        return doWrap(source, defaultEnlistOptions, null);
    }
    
    @Override
    protected <U> DependentPromise<U> wrap(Promise<U> original, CompletionStage<?>[] cancellableOrigins) {
        return doWrap(original, defaultEnlistOptions, cancellableOrigins);
    }
    
    private static <U> DependentPromise<U> doWrap(Promise<U> original, Set<PromiseOrigin> defaultEnlistOptions, CompletionStage<?>[] cancellableOrigins) {
        if (null == defaultEnlistOptions || defaultEnlistOptions.isEmpty()) {
            // Nothing to enlist implicitly, use explicit only class
            return ExplicitDependentPromise.doWrap(original, cancellableOrigins);
        }
        if (null == cancellableOrigins || cancellableOrigins.length == 0) {
            // Nothing to enlist additionally for this "original" instance
            if (original instanceof ImplicitDependentPromise) {
                ImplicitDependentPromise<U> source = (ImplicitDependentPromise<U>)original;
                if (source.defaultEnlistOptions.equals(defaultEnlistOptions)) {
                    // Same implicit enlist options, may reuse 
                    return source;
                }
            }
        }
        final ImplicitDependentPromise<U> result = new ImplicitDependentPromise<>(original, defaultEnlistOptions, cancellableOrigins);
        result.checkCanceledOnCreation();
        return result;
    }
    
    private boolean enlistSelf() {
        return defaultEnlistOptions.contains(PromiseOrigin.THIS);
    }
    
    // All delay overloads delegate to this method
    @Override
    public DependentPromise<T> delay(Duration duration, boolean delayOnError) {
        return delay(duration, delayOnError, enlistSelf());
    }

    // All orTimeout overloads delegate to this method
    @Override
    public DependentPromise<T> orTimeout(Duration duration, boolean cancelOnTimeout) {
        return orTimeout(duration, cancelOnTimeout, enlistSelf());
    }
    
    // All onTimeout overloads delegate to this method
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout) {
        return onTimeout(supplier, duration, cancelOnTimeout, enlistSelf());
    }
    
    @Override
    public <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn) {
        return thenApply(fn, enlistSelf());
    }

    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return thenApplyAsync(fn, enlistSelf());
    }

    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return thenApplyAsync(fn, executor, enlistSelf());
    }    
    
    @Override
    public DependentPromise<Void> thenAccept(Consumer<? super T> action) {
        return thenAccept(action, enlistSelf());
    }

    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return thenAcceptAsync(action, enlistSelf());
    }

    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return thenAcceptAsync(action, executor, enlistSelf());
    }    

    @Override
    public DependentPromise<Void> thenRun(Runnable action) {
        return thenRun(action, enlistSelf());
    }

    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action) {
        return thenRunAsync(action, enlistSelf());
    }

    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action, Executor executor) {
        return thenRunAsync(action, executor, enlistSelf());
    }

    @Override
    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombine(other, fn, defaultEnlistOptions);
    }

    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, fn, defaultEnlistOptions);
    }

    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                              BiFunction<? super T, ? super U, ? extends V> fn, 
                                              Executor executor) {
        
        return thenCombineAsync(other, fn, executor, defaultEnlistOptions);
    }
    
    @Override
    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBoth(other, action, defaultEnlistOptions);
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBothAsync(other, action, defaultEnlistOptions);
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                 BiConsumer<? super T, ? super U> action,
                                                 Executor executor) {
        
        return thenAcceptBothAsync(other, action, executor, defaultEnlistOptions);
    }    
    
    @Override
    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return runAfterBoth(other, action, defaultEnlistOptions);
    }

    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action, defaultEnlistOptions);
    }

    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                           Runnable action, 
                                           Executor executor) {
        return runAfterBothAsync(other, action, executor, defaultEnlistOptions);
    }
    
    @Override
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEither(other, fn, defaultEnlistOptions);
    }

    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEitherAsync(other, fn, defaultEnlistOptions);
    }

    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                             Function<? super T, U> fn,
                                             Executor executor) {
        
        return applyToEitherAsync(other, fn, executor, defaultEnlistOptions);
    }    

    @Override
    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEither(other, action, defaultEnlistOptions);
    }

    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEitherAsync(other, action, defaultEnlistOptions);
    }

    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                           Consumer<? super T> action,
                                           Executor executor) {
        
        return acceptEitherAsync(other, action, executor, defaultEnlistOptions);
    }    

    @Override
    public DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return runAfterEither(other, action, defaultEnlistOptions);
    }

    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action, defaultEnlistOptions);
    }

    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                             Runnable action, 
                                             Executor executor) {
        
        return runAfterEitherAsync(other, action, executor, defaultEnlistOptions);
    }
    
    @Override
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenCompose(fn, enlistSelf());
    }

    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenComposeAsync(fn, enlistSelf());
    }

    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return thenComposeAsync(fn, executor, enlistSelf());
    }

    @Override
    public DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return exceptionally(fn, enlistSelf());
    }
    
    @Override
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return whenComplete(action, enlistSelf());
    }

    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return whenCompleteAsync(action, enlistSelf());
    }

    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return whenCompleteAsync(action, executor, enlistSelf());
    }

    @Override
    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handle(fn, enlistSelf());
    }

    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handleAsync(fn, enlistSelf());
    }

    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return handleAsync(fn, executor, enlistSelf());
    }

    @Override
    public CompletableFuture<T> toCompletableFuture() {
        return toCompletableFuture(enlistSelf());
    }
}
