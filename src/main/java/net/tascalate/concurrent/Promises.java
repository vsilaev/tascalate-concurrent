package net.tascalate.concurrent;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class Promises {

    public static <T> Promise<T> readyValue(T value) {
        final CompletablePromise<T> result = new CompletablePromise<>();
        result.onSuccess(value);
        return result;
    }

    public static <T> Promise<T> from(CompletionStage<T> stage) {
        if (stage instanceof Promise) {
            return (Promise<T>) stage;
        }

        if (stage instanceof CompletableFuture) {
            return new CompletablePromise<>((CompletableFuture<T>)stage);
        }
        
        final CompletablePromise<T> result = createLinkedPromise(stage);
        stage.whenComplete(handler(result::onSuccess, result::onError));
        return result;
    }

    static <T, R> Promise<R> from(CompletionStage<T> stage, 
                                  Function<? super T, ? extends R> resultConverter,
                                  Function<? super Throwable, ? extends Throwable> errorConverter) {
        
        final CompletablePromise<R> result = createLinkedPromise(stage);
        stage.whenComplete(handler(
            acceptConverted(result::onSuccess, resultConverter),
            acceptConverted(result::onError, errorConverter)
        ));
        return result;
    }

    private static <T, R> CompletablePromise<R> createLinkedPromise(CompletionStage<T> stage) {
        return new CompletablePromise<R>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (super.cancel(mayInterruptIfRunning)) {
                    cancelPromise(stage, mayInterruptIfRunning);
                    return true;
                } else {
                    return false;
                }
            }
        };
    }

    @SafeVarargs
    public static <T> Promise<List<T>> all(final CompletionStage<? extends T>... promises) {
        return atLeast(promises.length, 0, true, promises);
    }

    @SafeVarargs
    public static <T> Promise<T> any(final CompletionStage<? extends T>... promises) {
        return unwrap(atLeast(1, promises.length - 1, true, promises), false);
    }

    @SafeVarargs
    public static <T> Promise<T> anyStrict(final CompletionStage<? extends T>... promises) {
        return unwrap(atLeast(1, 0, true, promises), true);
    }

    @SafeVarargs
    public static <T> Promise<List<T>> atLeast(final int minResultsCount, final CompletionStage<? extends T>... promises) {
        return atLeast(minResultsCount, promises.length - minResultsCount, true, promises);
    }

    @SafeVarargs
    public static <T> Promise<List<T>> atLeastStrict(final int minResultsCount, final CompletionStage<? extends T>... promises) {
        return atLeast(minResultsCount, 0, true, promises);
    }

    @SafeVarargs
    public static <T> Promise<List<T>> atLeast(final int minResultsCount, final int maxErrorsCount, final boolean cancelRemaining, 
                                               final CompletionStage<? extends T>... promises) {
        
        if (minResultsCount > promises.length) {
            throw new IllegalArgumentException(
                    "The number of futures supplied is less than a number of futures to await");
        } else if (minResultsCount == 0) {
            return readyValue(Collections.emptyList());
        } else if (promises.length == 1) {
            return from(promises[0], Collections::singletonList, Function.<Throwable> identity());
        } else {
            return new AggregatingPromise<>(minResultsCount, maxErrorsCount, cancelRemaining, promises);
        }
    }

    private static <T> Promise<T> unwrap(final CompletionStage<List<T>> original, final boolean unwrapException) {
        return from(
            original,
            c -> c.stream().filter(el -> null != el).findFirst().get(),
            unwrapException ? 
                ex -> ex instanceof MultitargetException ? MultitargetException.class.cast(ex).getFirstException().get() : ex
                :
                Function.identity()
        );
    }

    private static <T> BiConsumer<T, ? super Throwable> handler(Consumer<? super T> onResult, Consumer<? super Throwable> onError) {
        return (r, e) -> {
            if (null != e) {
                onError.accept(e);
            } else {
                try {
                    onResult.accept(r);
                } catch (final Exception ex) {
                    onError.accept(ex);
                }
            }
        };
    }

    private static <T, U> Consumer<? super T> acceptConverted(Consumer<? super U> target, Function<? super T, ? extends U> converter) {
        return t -> target.accept(converter.apply(t));
    }

}
