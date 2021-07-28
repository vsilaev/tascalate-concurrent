/**
 * Original work: copyright 2009-2015 Lukáš Křečan
 * https://github.com/lukas-krecan/completion-stage
 * 
 * This class is based on the work create by Lukáš Křečan 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/lukas-krecan/completion-stage/blob/completion-stage-0.0.9/src/main/java/net/javacrumbs/completionstage/CallbackRegistry.java 
 * 
 * Modified work: copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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

import java.util.Collection;
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Function;

class CallbackRegistry<T> {
    private final Object mutex = new Object();
    private State<T> state = InitialState.instance();
    
    /**
     * Adds the given callbacks to this registry.
     */
    <U> void addCallbacks(AbstractCompletableTask<U> target,
                          Function<? super T, ? extends U> successCallback, 
                          Function<Throwable, ? extends U> failureCallback,
                          Executor executor) {

        Objects.requireNonNull(successCallback, "'successCallback' must not be null");
        Objects.requireNonNull(failureCallback, "'failureCallback' must not be null");
        Objects.requireNonNull(executor, "'executor' must not be null");

        synchronized (mutex) {
            state = state.addCallbacks(target, successCallback, failureCallback, executor);
        }
    }

    /**
     * To be called to set the result value.
     *
     * @param result
     *            the result value
     * @return true if this result will be used (first result registered)
     */
    boolean success(T result) {
        State<T> oldState;
        synchronized (mutex) {
            if (state.isCompleted()) {
                return false;
            }
            oldState = state;
            state = state.getSuccessState(result);
        }
        oldState.callSuccessCallbacks(result);
        return true;

    }

    /**
     * To be called to set the failure exception
     *
     * @param failure
     *            the exception
     * @return true if this result will be used (first result registered)
     */
    boolean failure(Throwable failure) {
        State<T> oldState;
        synchronized (mutex) {
            if (state.isCompleted()) {
                return false;
            }
            oldState = state;
            state = state.getFailureState(failure);
        }
        oldState.callFailureCallbacks(failure);
        return true;
    }

    boolean isFailure() {
        synchronized (mutex) {
            return state.isFailure();
        }
    }
    
    boolean isCompleted() {
        synchronized (mutex) {
            return state.isCompleted();
        }
    }
    
    /**
     * State of the registry. All subclasses are meant to be used form a
     * synchronized block and are NOT thread safe on their own.
     */
    static abstract class State<S> {
        protected abstract <U> State<S> addCallbacks(AbstractCompletableTask<U> target,
                                                     Function<? super S, ? extends U> successCallback, 
                                                     Function<Throwable, ? extends U> failureCallback, 
                                                     Executor executor);

        protected State<S> getSuccessState(S result) {
            throw new IllegalStateException("success method should not be called multiple times");
        }

        protected void callSuccessCallbacks(S result) {
        }

        protected State<S> getFailureState(Throwable failure) {
            throw new IllegalStateException("failure method should not be called multiple times");
        }

        protected void callFailureCallbacks(Throwable failure) {
        }

        protected boolean isCompleted() {
            return true;
        }
        
        protected boolean isFailure() {
            return false;
        }
    }

    /**
     * Result is not known yet and no callbacks registered. Using shared
     * instance so we do not allocate instance where it may not be needed.
     */
    static class InitialState<S> extends State<S> {
        private static final InitialState<Object> instance = new InitialState<>();

        @Override
        protected <U> State<S> addCallbacks(AbstractCompletableTask<U> target,
                                            Function<? super S, ? extends U> successCallback, 
                                            Function<Throwable, ? extends U> failureCallback, 
                                            Executor executor) {
            
            IntermediateState<S> intermediateState = new IntermediateState<>();
            intermediateState.addCallbacks(target, successCallback, failureCallback, executor);
            return intermediateState;
        }

        @Override
        protected State<S> getSuccessState(S result) {
            return new SuccessState<>(result);
        }

        @Override
        protected State<S> getFailureState(Throwable failure) {
            return new FailureState<>(failure);
        }

        @Override
        protected boolean isCompleted() {
            return false;
        }

        @SuppressWarnings("unchecked")
        static <T> State<T> instance() {
            return (State<T>) instance;
        }
    }

    /**
     * Result is not known yet.
     */
    static class IntermediateState<S> extends State<S> {
        private final Collection<BiConsumer<? super S, ? super Throwable>> callbacks = new LinkedList<>();

        @Override
        protected <U> State<S> addCallbacks(AbstractCompletableTask<U> target,
                                            Function<? super S, ? extends U> successCallback, 
                                            Function<Throwable, ? extends U> failureCallback, 
                                            Executor executor) {
            
            callbacks.add((r, e) -> {
                if (null == e) {
                    callCallback(target, successCallback, r, executor);
                } else {
                    callCallback(target, failureCallback, e, executor);
                }
            });
            return this;
        }

        @Override
        protected State<S> getSuccessState(S result) {
            return new SuccessState<>(result);
        }

        @Override
        protected void callSuccessCallbacks(S result) {
            for (BiConsumer<? super S, ? super Throwable> callback : callbacks) {
                callback.accept(result, null);
            }
        }

        @Override
        protected State<S> getFailureState(Throwable failure) {
            return new FailureState<>(failure);
        }

        @Override
        protected void callFailureCallbacks(Throwable failure) {
            for (BiConsumer<? super S, ? super Throwable> callback : callbacks) {
                callback.accept(null, failure);
            }
        }

        @Override
        protected boolean isCompleted() {
            return false;
        }
    }

    /**
     * Holds the result.
     */
    static final class SuccessState<S> extends State<S> {
        private final S result;

        SuccessState(S result) {
            this.result = result;
        }

        @Override
        protected <U> State<S> addCallbacks(AbstractCompletableTask<U> target,
                                            Function<? super S, ? extends U> successCallback, 
                                            Function<Throwable, ? extends U> failureCallback, 
                                            Executor executor) {
            callCallback(target, successCallback, result, executor);
            return this;
        }
    }

    /**
     * Holds the failure.
     */
    static final class FailureState<S> extends State<S> {
        private final Throwable failure;

        FailureState(Throwable failure) {
            this.failure = failure;
        }

        @Override
        protected <U> State<S> addCallbacks(AbstractCompletableTask<U> target,
                                            Function<? super S, ? extends U> successCallback, 
                                            Function<Throwable, ? extends U> failureCallback, 
                                            Executor executor) {
            callCallback(target, failureCallback, failure, executor);
            return this;
        }
        
        protected boolean isFailure() {
            return true;
        }
    }

    static <T, U> void callCallback(AbstractCompletableTask<U> target,
                                    Function<? super T, ? extends U> callback, 
                                    T value,  
                                    Executor executor) {

        Callable<U> callable = () -> callback.apply(value);
        try {
            executor.execute( (AsyncTask)() -> target.fireTransition(callable) );
        } catch (RejectedExecutionException ex) {
            // Propagate error in-place
            Callable<U> propagateError = () -> { throw ex; };
            target.fireTransition(propagateError);
        }
    }

    @FunctionalInterface
    static interface AsyncTask extends Runnable, CompletableFuture.AsynchronousCompletionTask {}

}
