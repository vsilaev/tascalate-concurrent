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

final class ImplicitDependentPromise<T> extends AbstractDependentPromise<T> {
    final private Set<PromiseOrigin> enlistOptions;
    
    ImplicitDependentPromise(Promise<T> delegate, Set<PromiseOrigin> enlistOptions, CompletionStage<?>[] cancellableOrigins) {
        super(delegate, cancellableOrigins);
        this.enlistOptions = enlistOptions;
    }
    
    static <U> Promise<U> from(Promise<U> source, Set<PromiseOrigin> enlistOptions) {
        if (null == enlistOptions || enlistOptions.isEmpty()) {
            // Nothing to enlist
            return source;
        } else if (source instanceof ImplicitDependentPromise) {
            ImplicitDependentPromise<U> isource = (ImplicitDependentPromise<U>)source;
            if (isource.enlistOptions.equals(enlistOptions)) {
                return source;
            } else {
                return doWrap(source, enlistOptions, null);
            }
        } else {
            return doWrap(source, enlistOptions, null);
        }
    }
    
    private <U> Promise<U> wrap(Promise<U> original, CompletionStage<?>[] cancellableOrigins) {
        return doWrap(original, enlistOptions, cancellableOrigins);
    }
    
    private static <U> Promise<U> doWrap(Promise<U> original, Set<PromiseOrigin> enlistOptions, CompletionStage<?>[] cancellableOrigins) {
        final ImplicitDependentPromise<U> result = new ImplicitDependentPromise<>(original, enlistOptions, cancellableOrigins);
        result.checkCanceledOnCreation();
        return result;
    }

    // All delay overloads delegate to this method
    @Override
    public Promise<T> delay(Duration duration, boolean delayOnError) {
        return wrap(delegate.delay(duration, delayOnError), self());
    }

    // All orTimeout overloads delegate to this method
    @Override
    public Promise<T> orTimeout(Duration duration, boolean cancelOnTimeout) {
        return wrap(delegate.orTimeout(duration, cancelOnTimeout), self());
    }
    
    // All onTimeout overloads delegate to this method
    @Override
    public Promise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout) {
        return wrap(delegate.onTimeout(supplier, duration, cancelOnTimeout), self());
    }
    
    @Override
    public <U> Promise<U> thenApply(Function<? super T, ? extends U> fn) {
        return wrap(delegate.thenApply(fn), self());
    }

    @Override
    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return wrap(delegate.thenApplyAsync(fn), self());
    }

    @Override
    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return wrap(delegate.thenApplyAsync(fn, executor), self());
    }    
    
    @Override
    public Promise<Void> thenAccept(Consumer<? super T> action) {
        return wrap(delegate.thenAccept(action), self());
    }

    @Override
    public Promise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return wrap(delegate.thenAcceptAsync(action), self());
    }

    @Override
    public Promise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return wrap(delegate.thenAcceptAsync(action, executor), self());
    }    

    @Override
    public Promise<Void> thenRun(Runnable action) {
        return wrap(delegate.thenRun(action), self());
    }

    @Override
    public Promise<Void> thenRunAsync(Runnable action) {
        return wrap(delegate.thenRunAsync(action), self());
    }

    @Override
    public Promise<Void> thenRunAsync(Runnable action, Executor executor) {
        return wrap(delegate.thenRunAsync(action, executor), self());
    }

    @Override
    public <U, V> Promise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return wrap(delegate.thenCombine(other, fn), selfAndParam(other));
    }

    @Override
    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return wrap(delegate.thenCombineAsync(other, fn), selfAndParam(other));
    }

    @Override
    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                              BiFunction<? super T, ? super U, ? extends V> fn, 
                                              Executor executor) {
        
        return wrap(delegate.thenCombineAsync(other, fn, executor), selfAndParam(other));
    }
    
    @Override
    public <U> Promise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return wrap(delegate.thenAcceptBoth(other, action), selfAndParam(other));
    }

    @Override
    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return wrap(delegate.thenAcceptBothAsync(other, action), selfAndParam(other));
    }

    @Override
    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                 BiConsumer<? super T, ? super U> action,
                                                 Executor executor) {
        
        return wrap(delegate.thenAcceptBothAsync(other, action, executor), selfAndParam(other));
    }    
    
    @Override
    public Promise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return wrap(delegate.runAfterBoth(other, action), selfAndParam(other));
    }

    @Override
    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return wrap(delegate.runAfterBothAsync(other, action), selfAndParam(other));
    }

    @Override
    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                           Runnable action, 
                                           Executor executor) {
        return wrap(delegate.runAfterBothAsync(other, action, executor), selfAndParam(other));
    }
    
    @Override
    public <U> Promise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return wrap(delegate.applyToEither(other, fn), selfAndParam(other));
    }

    @Override
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return wrap(delegate.applyToEitherAsync(other, fn), selfAndParam(other));
    }

    @Override
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                             Function<? super T, U> fn,
                                             Executor executor) {
        
        return wrap(delegate.applyToEitherAsync(other, fn, executor), selfAndParam(other));
    }    

    @Override
    public Promise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return wrap(delegate.acceptEither(other, action), selfAndParam(other));
    }

    @Override
    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return wrap(delegate.acceptEitherAsync(other, action), selfAndParam(other));
    }

    @Override
    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                           Consumer<? super T> action,
                                           Executor executor) {
        
        return wrap(delegate.acceptEitherAsync(other, action, executor), selfAndParam(other));
    }    

    @Override
    public Promise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return wrap(delegate.runAfterEither(other, action), selfAndParam(other));
    }

    @Override
    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return wrap(delegate.runAfterEitherAsync(other, action), selfAndParam(other));
    }

    @Override
    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                             Runnable action, 
                                             Executor executor) {
        
        return wrap(delegate.runAfterEitherAsync(other, action, executor), selfAndParam(other));
    }
    
    @Override
    public <U> Promise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return wrap(delegate.thenCompose(fn), self());
    }

    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return wrap(delegate.thenComposeAsync(fn), self());
    }

    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return wrap(delegate.thenComposeAsync(fn, executor), self());
    }

    @Override
    public Promise<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return wrap(delegate.exceptionally(fn), self());
    }
    
    @Override
    public Promise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return wrap(delegate.whenComplete(action), self());
    }

    @Override
    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return wrap(delegate.whenCompleteAsync(action), self());
    }

    @Override
    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return wrap(delegate.whenCompleteAsync(action, executor), self());
    }

    @Override
    public <U> Promise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return wrap(delegate.handle(fn), self());
    }

    @Override
    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return wrap(delegate.handleAsync(fn), self());
    }

    @Override
    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return wrap(delegate.handleAsync(fn, executor), self());
    }

    @Override
    public CompletableFuture<T> toCompletableFuture() {
        return toCompletableFuture(enlistOptions.contains(PromiseOrigin.THIS));
    }
    
    private CompletionStage<?>[] self() {
        boolean enlist = enlistOptions.contains(PromiseOrigin.THIS);
        if (enlist) {
            CompletionStage<?>[] result = new CompletionStage<?>[1];
            result[0] = this;
            return result;
        } else {
            return null;
        }
    }
    
    private CompletionStage<?>[] selfAndParam(CompletionStage<?> param) {
        final CompletionStage<?>[] result = new CompletionStage<?>[enlistOptions.size()];
        int idx = 0;
        if (enlistOptions.contains(PromiseOrigin.THIS)) {
            result[idx++] = this;
        }
        if (enlistOptions.contains(PromiseOrigin.PARAM) && param != null) {
            result[idx++] = param;
        }
        return result;
    }
    
    
}
