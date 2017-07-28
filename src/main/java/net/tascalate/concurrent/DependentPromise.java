package net.tascalate.concurrent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class DependentPromise<T> implements Promise<T> {
    final private Promise<T> completionStage;
    final private List<? extends CompletionStage<?>> cancellableOrigins;
    
    protected DependentPromise(Promise<T> delegate, List<? extends CompletionStage<?>> cancellableOrigins) {
        this.completionStage = delegate;
        this.cancellableOrigins = cancellableOrigins; 
    }
    
    public static <U> DependentPromise<U> from(Promise<U> source) {
        return doWrap(source, Collections.emptyList());
    }
    
    protected void cancelOrigins(boolean mayInterruptIfRunning) {
        cancellableOrigins.stream().forEach(p -> cancelPromise(p, mayInterruptIfRunning));
    }
    
    protected boolean cancelPromise(CompletionStage<?> promise, boolean mayInterruptIfRunning) {
        return CompletablePromise.cancelPromise(promise, mayInterruptIfRunning);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (completionStage.cancel(mayInterruptIfRunning)) {
            cancelOrigins(mayInterruptIfRunning);
            return true;
        } else {
            return false;
        }
    }
    
    @Override
    public boolean isCancelled() {
        return completionStage.isCancelled();
    }

    @Override
    public boolean isDone() {
        return completionStage.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return completionStage.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return completionStage.get(timeout, unit);
    }

    @Override
    public T getNow(T valueIfAbsent) {
        return completionStage.getNow(valueIfAbsent);
    }
    
    public T getNow(Supplier<T> valueIfAbsent) {
        return completionStage.getNow(valueIfAbsent);
    }

    protected <U> DependentPromise<U> wrap(CompletionStage<U> original, List<? extends CompletionStage<?>> cancellableOrigins) {
        return doWrap(original, cancellableOrigins);
    }
    
    private static <U> DependentPromise<U> doWrap(CompletionStage<U> original, List<? extends CompletionStage<?>> cancellableOrigins) {
        final DependentPromise<U> result = new DependentPromise<>((Promise<U>)original, cancellableOrigins);
        if (result.isCancelled()) {
            // Wrapped over already cancelled Promise
            // So result.cancel() has no effect
            // and we have to cancel explicitly right
            // after construction
            result.cancelOrigins(true);
        }
        return result;
    }
    
    public <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn) {
        return thenApply(fn, true);
    }

    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return thenApplyAsync(fn, true);
    }

    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return thenApplyAsync(fn, executor, true);
    }

    public <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return wrap(completionStage.thenApply(fn), self(enlistOrigin));
    }

    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return wrap(completionStage.thenApplyAsync(fn), self(enlistOrigin));
    }

    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor, boolean enlistOrigin) {
        return wrap(completionStage.thenApplyAsync(fn, executor), self(enlistOrigin));
    }    
    
    public DependentPromise<Void> thenAccept(Consumer<? super T> action) {
        return thenAccept(action, true);
    }

    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return thenAcceptAsync(action, true);
    }

    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return thenAcceptAsync(action, executor, true);
    }
    
    public DependentPromise<Void> thenAccept(Consumer<? super T> action, boolean enlistOrigin) {
        return wrap(completionStage.thenAccept(action), self(enlistOrigin));
    }

    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, boolean enlistOrigin) {
        return wrap(completionStage.thenAcceptAsync(action), self(enlistOrigin));
    }

    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor, boolean enlistOrigin) {
        return wrap(completionStage.thenAcceptAsync(action, executor), self(enlistOrigin));
    }    

    public DependentPromise<Void> thenRun(Runnable action) {
        return thenRun(action, true);
    }

    public DependentPromise<Void> thenRunAsync(Runnable action) {
        return thenRunAsync(action, true);
    }

    public DependentPromise<Void> thenRunAsync(Runnable action, Executor executor) {
        return thenRunAsync(action, executor, true);
    }
    
    public DependentPromise<Void> thenRun(Runnable action, boolean enlistOrigin) {
        return wrap(completionStage.thenRun(action), self(enlistOrigin));
    }

    public DependentPromise<Void> thenRunAsync(Runnable action, boolean enlistOrigin) {
        return wrap(completionStage.thenRunAsync(action), self(enlistOrigin));
    }

    public DependentPromise<Void> thenRunAsync(Runnable action, Executor executor, boolean enlistOrigin) {
        return wrap(completionStage.thenRunAsync(action, executor), self(enlistOrigin));
    }

    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombine(other, fn, PromiseOrigin.THIS_ONLY);
    }

    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, fn, PromiseOrigin.THIS_ONLY);
    }

    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn, 
                                                       Executor executor) {
        
        return thenCombineAsync(other, fn, executor, PromiseOrigin.THIS_ONLY);
    }
    
    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, 
                                                  BiFunction<? super T, ? super U, ? extends V> fn,
                                                  Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.thenCombine(other, fn), selfAndParam(other, enlistOptions));
    }

    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, 
                                                       BiFunction<? super T, ? super U, ? extends V> fn,
                                                       Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.thenCombineAsync(other, fn), selfAndParam(other, enlistOptions));
    }

    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn, 
                                                       Executor executor,
                                                       Set<PromiseOrigin> enlistOptions) {
        
        return wrap(completionStage.thenCombineAsync(other, fn, executor), selfAndParam(other, enlistOptions));
    }
    

    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBoth(other, action, PromiseOrigin.THIS_ONLY);
    }

    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBothAsync(other, action, PromiseOrigin.THIS_ONLY);
    }

    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                          BiConsumer<? super T, ? super U> action, 
                                                          Executor executor) {
        
        return thenAcceptBothAsync(other, action, executor, PromiseOrigin.THIS_ONLY);
    }

    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, 
                                                     BiConsumer<? super T, ? super U> action,
                                                     Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.thenAcceptBoth(other, action), selfAndParam(other, enlistOptions));
    }

    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                          BiConsumer<? super T, ? super U> action,
                                                          Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.thenAcceptBothAsync(other, action), selfAndParam(other, enlistOptions));
    }

    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                          BiConsumer<? super T, ? super U> action, 
                                                          Executor executor,
                                                          Set<PromiseOrigin> enlistOptions) {
        
        return wrap(completionStage.thenAcceptBothAsync(other, action, executor), selfAndParam(other, enlistOptions));
    }    
    
    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return runAfterBoth(other, action, PromiseOrigin.THIS_ONLY);
    }

    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action, PromiseOrigin.THIS_ONLY);
    }

    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                                    Runnable action, 
                                                    Executor executor) {
        return runAfterBothAsync(other, action, executor, PromiseOrigin.THIS_ONLY);
    }

    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.runAfterBoth(other, action), selfAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.runAfterBothAsync(other, action), selfAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                                    Runnable action, 
                                                    Executor executor,
                                                    Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.runAfterBothAsync(other, action, executor), selfAndParam(other, enlistOptions));
    }
    
    
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEither(other, fn, PromiseOrigin.THIS_ONLY);
    }

    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEitherAsync(other, fn, PromiseOrigin.THIS_ONLY);
    }

    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Executor executor) {
        
        return applyToEitherAsync(other, fn, executor, PromiseOrigin.THIS_ONLY);
    }
    
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, 
                                                 Function<? super T, U> fn,
                                                 Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.applyToEither(other, fn), selfAndParam(other, enlistOptions));
    }

    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.applyToEitherAsync(other, fn), selfAndParam(other, enlistOptions));
    }

    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Executor executor,
                                                      Set<PromiseOrigin> enlistOptions) {
        
        return wrap(completionStage.applyToEitherAsync(other, fn, executor), selfAndParam(other, enlistOptions));
    }    

    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEither(other, action, PromiseOrigin.THIS_ONLY);
    }

    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEitherAsync(other, action, PromiseOrigin.THIS_ONLY);
    }

    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Executor executor) {
        
        return acceptEitherAsync(other, action, executor, PromiseOrigin.THIS_ONLY);
    }

    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, 
                                               Consumer<? super T> action,
                                               Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.acceptEither(other, action), selfAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.acceptEitherAsync(other, action), selfAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Executor executor,
                                                    Set<PromiseOrigin> enlistOptions) {
        
        return wrap(completionStage.acceptEitherAsync(other, action, executor), selfAndParam(other, enlistOptions));
    }    
    
    public DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return runAfterEither(other, action, PromiseOrigin.THIS_ONLY);
    }

    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action, PromiseOrigin.THIS_ONLY);
    }

    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                                      Runnable action, 
                                                      Executor executor) {
        return runAfterEitherAsync(other, action, executor, PromiseOrigin.THIS_ONLY);
    }

    public DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.runAfterEither(other, action), selfAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.runAfterEitherAsync(other, action), selfAndParam(other, enlistOptions));
    }

    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                                      Runnable action, 
                                                      Executor executor,
                                                      Set<PromiseOrigin> enlistOptions) {
        return wrap(completionStage.runAfterEitherAsync(other, action, executor), selfAndParam(other, enlistOptions));
    }
    
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenCompose(fn, true);
    }

    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenComposeAsync(fn, true);
    }

    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return thenComposeAsync(fn, executor, true);
    }
    
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return wrap(completionStage.thenCompose(fn), self(enlistOrigin));
    }

    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return wrap(completionStage.thenComposeAsync(fn), self(enlistOrigin));
    }

    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor, boolean enlistOrigin) {
        return wrap(completionStage.thenComposeAsync(fn, executor), self(enlistOrigin));
    }

    public DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return exceptionally(fn, true);
    }

    public DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn, boolean enlistOrigin) {
        return wrap(completionStage.exceptionally(fn), self(enlistOrigin));
    }
    
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return whenComplete(action, true);
    }

    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return whenCompleteAsync(action, true);
    }

    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return whenCompleteAsync(action, executor, true);
    }
    
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return wrap(completionStage.whenComplete(action), self(enlistOrigin));
    }

    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return wrap(completionStage.whenCompleteAsync(action), self(enlistOrigin));
    }

    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor, boolean enlistOrigin) {
        return wrap(completionStage.whenCompleteAsync(action, executor), self(enlistOrigin));
    }

    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handle(fn, true);
    }

    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handleAsync(fn, true);
    }

    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return handleAsync(fn, executor, true);
    }
    
    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return wrap(completionStage.handle(fn), self(enlistOrigin));
    }

    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return wrap(completionStage.handleAsync(fn), self(enlistOrigin));
    }

    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor, boolean enlistOrigin) {
        return wrap(completionStage.handleAsync(fn, executor), self(enlistOrigin));
    }


    public CompletableFuture<T> toCompletableFuture() {
        return completionStage.toCompletableFuture();
    }
    
    private List<DependentPromise<T>> self(boolean enlist) {
        return enlist ? Collections.emptyList() : Collections.singletonList(this);
    }
    
    private List<CompletionStage<?>> selfAndParam(CompletionStage<?> param, Set<PromiseOrigin> enlistOptions) {
        final List<CompletionStage<?>> result = new ArrayList<>();
        if (enlistOptions.contains(PromiseOrigin.THIS)) {
            result.add(this);
        }
        if (enlistOptions.contains(PromiseOrigin.PARAM) && param != null) {
            result.add(param);
        }
        return Collections.unmodifiableList(result);
    }
    
}
