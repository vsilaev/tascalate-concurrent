/**
 * ﻿Copyright 2015-2017 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.concurrent;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Utility class to create a resolved (either successfully or faulty) {@link Promise}-s; 
 * to wrap an arbitrary {@link CompletionStage} interface to the {@link Promise} API;
 * to combine several {@link CompletionStage}-s into aggregating promise.
 *    
 * @author vsilaev
 *
 */
public class Promises {

    /**
     * Method to create a successfully resolved {@link Promise} with a value provided 
     * @param <T>
     *   a type of the value
     * @param value
     *   a value to wrap
     * @return 
     *   a successfully resolved {@link Promise} with a value provided
     */
    public static <T> Promise<T> success(T value) {
        CompletablePromise<T> result = new CompletablePromise<>();
        result.onSuccess(value);
        return result;
    }
    
    /**
     * Method to create a faulty resolved {@link Promise} with an exception provided 
     * @param exception
     *   an exception to wrap
     * @return 
     *   a faulty resolved {@link Promise} with an exception provided
     */    
    public static Promise<?> failure(Throwable exception) {
        CompletablePromise<?> result = new CompletablePromise<>();
        result.onFailure(exception);
        return result;
    }

    /**
     * Adapts a stage passed to the {@link Promise} API
     * @param <T>
     *   a type of the value
     * @param stage
     *   a {@link CompletionStage} to be wrapped
     * @return
     *   a {@link Promise}
     */
    public static <T> Promise<T> from(CompletionStage<T> stage) {
        if (stage instanceof Promise) {
            return (Promise<T>) stage;
        }

        if (stage instanceof CompletableFuture) {
            return new CompletablePromise<>((CompletableFuture<T>)stage);
        }
        
        CompletablePromise<T> result = createLinkedPromise(stage);
        stage.whenComplete(handler(result::onSuccess, result::onFailure));
        return result;
    }

    static <T, R> Promise<R> from(CompletionStage<T> stage, 
                                  Function<? super T, ? extends R> resultConverter,
                                  Function<? super Throwable, ? extends Throwable> errorConverter) {
        
        CompletablePromise<R> result = createLinkedPromise(stage);
        stage.whenComplete(handler(
            acceptConverted(result::onSuccess, resultConverter),
            acceptConverted(result::onFailure, errorConverter)
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
    
    /**
     * <p>Returns a promise that is resolved successfully when all {@link CompletionStage}-s passed as parameters are completed normally; 
     * if any promise completed exceptionally, then resulting promise is resolved faulty as well.
     * <p>The resolved result of this promise contains a list of the resolved results of the {@link CompletionStage}-s passed as an 
     * argument at corresponding positions.
     * <p>When resulting promise is resolved faulty, all remaining incomplete {@link CompletionStage}-s are cancelled.  
     * @param <T>
     *   a common supertype of the resulting values
     * @param promises
     *   an array of {@link CompletionStage}-s to combine
     * @return
     *   a combined promise
     */
    @SafeVarargs
    public static <T> Promise<List<T>> all(CompletionStage<? extends T>... promises) {
        return atLeast(promises.length, 0, true, promises);
    }

    /**
     * <p>Returns a promise that is resolved successfully when any {@link CompletionStage} passed as parameters is completed normally (race is possible); 
     * if all promises completed exceptionally, then resulting promise is resolved faulty as well.
     * <p>The resolved result of this promise contains a value of the first resolved result of the {@link CompletionStage}-s passed as an 
     * argument.
     * <p>When resulting promise is resolved successfully, all remaining incomplete {@link CompletionStage}-s are cancelled.
     * 
     * @param <T>
     *   a common supertype of the resulting values 
     * @param promises
     *   an array of {@link CompletionStage}-s to combine
     * @return
     *   a combined promise
     */
    @SafeVarargs
    public static <T> Promise<T> any(CompletionStage<? extends T>... promises) {
        return unwrap(atLeast(1, promises.length - 1, true, promises), false);
    }

    /**
     * <p>Returns a promise that is resolved successfully when any {@link CompletionStage} passed as parameters is completed normally (race is possible); 
     * if any promise completed exceptionally before first result is available, then resulting promise is resolved faulty as well 
     * (unlike non-Strict variant, where exceptions are ignored if result is available at all).
     * <p>The resolved result of this promise contains a value of the first resolved result of the {@link CompletionStage}-s passed as an 
     * argument.
     * <p>When resulting promise is resolved either successfully or faulty, all remaining incomplete {@link CompletionStage}-s are cancelled. 
     * @param <T>
     *   a common supertype of the resulting values 
     * @param promises
     *   an array of {@link CompletionStage}-s to combine
     * @return
     *   a combined promise
     */
    @SafeVarargs
    public static <T> Promise<T> anyStrict(CompletionStage<? extends T>... promises) {
        return unwrap(atLeast(1, 0, true, promises), true);
    }

    /**
     * <p>Generalization of the {@link Promises#any(CompletionStage...)} method.</p>
     * <p>Returns a promise that is resolved successfully when at least <code>minResultCount</code> of {@link CompletionStage}-s passed as parameters 
     * are completed normally (race is possible); if less than <code>minResultCount</code> of promises completed normally, then resulting promise 
     * is resolved faulty.
     * <p>The resolved result of this promise contains a list of the resolved results of the {@link CompletionStage}-s passed as an 
     * argument at corresponding positions. Non-completed or completed exceptionally promises have <code>null</code> values.
     * <p>When resulting promise is resolved successfully, all remaining incomplete {@link CompletionStage}-s are cancelled. 
     * 
     * @param <T>
     *   a common supertype of the resulting values 
     * @param minResultsCount
     *   a minimum number of promises that should be completed normally to resolve resulting promise successfully 
     * @param promises
     *   an array of {@link CompletionStage}-s to combine 
     * @return
     *   a combined promise 
     */
    @SafeVarargs
    public static <T> Promise<List<T>> atLeast(int minResultsCount, CompletionStage<? extends T>... promises) {
        return atLeast(minResultsCount, promises.length - minResultsCount, true, promises);
    }

    /**
     * <p>Generalization of the {@link Promises#anyStrict(CompletionStage...)} method.</p>
     * <p>Returns a promise that is resolved successfully when at least <code>minResultCount</code> of {@link CompletionStage}-s passed as parameters 
     * are completed normally (race is possible); if less than <code>minResultCount</code> of promises completed normally, then resulting promise 
     * is resolved faulty. If any promise completed exceptionally <em>before</em> <code>minResultCount</code> of results are available, then 
     * resulting promise is resolved faulty as well. 
     * <p>The resolved result of this promise contains a list of the resolved results of the {@link CompletionStage}-s passed as an 
     * argument at corresponding positions. Non-completed promises have <code>null</code> values.
     * <p>When resulting promise is resolved either successfully or faulty, all remaining incomplete {@link CompletionStage}-s are cancelled.
     *  
     * @param <T>
     *   a common supertype of the resulting values 
     * @param minResultsCount
     *   a minimum number of promises that should be completed normally to resolve resulting promise successfully 
     * @param promises
     *   an array of {@link CompletionStage}-s to combine 
     * @return
     *   a combined promise 
     */    
    @SafeVarargs
    public static <T> Promise<List<T>> atLeastStrict(int minResultsCount, CompletionStage<? extends T>... promises) {
        return atLeast(minResultsCount, 0, true, promises);
    }

    /**
     * <p>General method to combine several {@link CompletionStage}-s passed as arguments into single promise.</p>
     * <p>The resulting promise is resolved successfully when at least <code>minResultCount</code> of {@link CompletionStage}-s passed as parameters 
     * are completed normally (race is possible).
     * <p>If less than <code>minResultCount</code> of promises completed normally, then resulting promise is resolved faulty. 
     * <p>If <code>maxErrorsCount</code> of promises completed exceptionally <em>before</em> <code>minResultCount</code> of results are available, then 
     * resulting promise is resolved faulty as well. 
     * <p>The resolved result of this promise contains a list of the resolved results of the {@link CompletionStage}-s passed as an 
     * argument at corresponding positions. Non-completed promises and promises completed exceptionally have <code>null</code> values.
     * <p>When resulting promise is resolved either successfully or faulty, all remaining incomplete {@link CompletionStage}-s are cancelled <em>if</em>
     * <code>cancelRemaining</code> parameter is <code>true</code>. 
     * 
     * @param <T>
     *   a common supertype of the resulting values 
     * @param minResultsCount
     *   a minimum number of promises that should be completed normally to resolve resulting promise successfully
     * @param maxErrorsCount
     *   a maximum number of promises that may be completed exceptionally before resolving resulting promise faulty
     * @param cancelRemaining
     *   a flag that indicates (if true) whether or not all remaining incomplete {@link CompletionStage}-s should be cancelled
     *   once a resulting promise outcome is known. 
     * @param promises
     *   an array of {@link CompletionStage}-s to combine 
     * @return
     *   a combined promise 
     */    
    @SafeVarargs
    public static <T> Promise<List<T>> atLeast(int minResultsCount, int maxErrorsCount, boolean cancelRemaining, 
                                               CompletionStage<? extends T>... promises) {
        
        if (minResultsCount > promises.length) {
            throw new IllegalArgumentException(
                    "The number of futures supplied is less than a number of futures to await");
        } else if (minResultsCount == 0) {
            return success(Collections.emptyList());
        } else if (promises.length == 1) {
            return from(promises[0], Collections::singletonList, Function.<Throwable> identity());
        } else {
            return new AggregatingPromise<>(minResultsCount, maxErrorsCount, cancelRemaining, promises);
        }
    }

    private static <T> Promise<T> unwrap(CompletionStage<List<T>> original, boolean unwrapException) {
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
                } catch (Exception ex) {
                    onError.accept(ex);
                }
            }
        };
    }

    private static <T, U> Consumer<? super T> acceptConverted(Consumer<? super U> target, Function<? super T, ? extends U> converter) {
        return t -> target.accept(converter.apply(t));
    }

}
