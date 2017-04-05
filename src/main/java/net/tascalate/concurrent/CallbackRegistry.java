package net.tascalate.concurrent;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Function;

class CallbackRegistry<T> {
    private State<T> state = InitialState.instance();

    private final Object mutex = new Object();

    /**
     * Adds the given callbacks to this registry.
     */
    <U> void addCallbacks(Function<? super Callable<U>, ? extends Runnable> targetSetup,
                          Function<? super T, ? extends U> successCallback, 
                          Function<Throwable, ? extends U> failureCallback,
                          Executor executor) {

        Objects.requireNonNull(successCallback, "'successCallback' must not be null");
        Objects.requireNonNull(failureCallback, "'failureCallback' must not be null");
        Objects.requireNonNull(executor, "'executor' must not be null");

        @SuppressWarnings("unchecked")
        Function<? super Callable<?>, ? extends Runnable> typedTargetSetup = 
            (Function<? super Callable<?>, ? extends Runnable>) targetSetup;

        synchronized (mutex) {
            state = state.addCallbacks(typedTargetSetup, successCallback, failureCallback, executor);
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

    /**
     * State of the registry. All subclasses are meant to be used form a
     * synchronized block and are NOT thread safe on their own.
     */
    private static abstract class State<S> {
        protected abstract State<S> addCallbacks(Function<? super Callable<?>, ? extends Runnable> targetSetup,
                                                 Function<? super S, ?> successCallback, 
                                                 Function<Throwable, ?> failureCallback, 
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
    }

    /**
     * Result is not known yet and no callbacks registered. Using shared
     * instance so we do not allocate instance where it may not be needed.
     */
    private static class InitialState<S> extends State<S> {
        private static final InitialState<Object> instance = new InitialState<>();

        @Override
        protected State<S> addCallbacks(Function<? super Callable<?>, ? extends Runnable> targetSetup,
                                        Function<? super S, ?> successCallback, 
                                        Function<Throwable, ?> failureCallback, 
                                        Executor executor) {
            
            IntermediateState<S> intermediateState = new IntermediateState<>();
            intermediateState.addCallbacks(targetSetup, successCallback, failureCallback, executor);
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
        private static <T> State<T> instance() {
            return (State<T>) instance;
        }
    }

    /**
     * Result is not known yet.
     */
    private static class IntermediateState<S> extends State<S> {
        private final Queue<CallbackHolder<? super S>> callbacks = new LinkedList<>();

        @Override
        protected State<S> addCallbacks(Function<? super Callable<?>, ? extends Runnable> targetSetup,
                                        Function<? super S, ?> successCallback, 
                                        Function<Throwable, ?> failureCallback, 
                                        Executor executor) {
            
            callbacks.add(new CallbackHolder<>(targetSetup, successCallback, failureCallback, executor));
            return this;
        }

        @Override
        protected State<S> getSuccessState(S result) {
            return new SuccessState<>(result);
        }

        @Override
        protected void callSuccessCallbacks(S result) {
            // no need to remove callbacks from the queue, this instance will be
            // thrown away at once
            for (CallbackHolder<? super S> callback : callbacks) {
                callback.callSuccessCallback(result);
            }
        }

        @Override
        protected State<S> getFailureState(Throwable failure) {
            return new FailureState<>(failure);
        }

        @Override
        protected void callFailureCallbacks(Throwable failure) {
            // no need to remove callbacks from the queue, this instance will be
            // thrown away at once
            for (CallbackHolder<? super S> callback : callbacks) {
                callback.callFailureCallback(failure);
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
    private static final class SuccessState<S> extends State<S> {
        private final S result;

        private SuccessState(S result) {
            this.result = result;
        }

        @Override
        protected State<S> addCallbacks(Function<? super Callable<?>, ? extends Runnable> targetSetup,
                                        Function<? super S, ?> successCallback, 
                                        Function<Throwable, ?> failureCallback, 
                                        Executor executor) {
            
            callCallback(targetSetup, successCallback, result, executor);
            return this;
        }
    }

    /**
     * Holds the failure.
     */
    private static final class FailureState<S> extends State<S> {
        private final Throwable failure;

        private FailureState(Throwable failure) {
            this.failure = failure;
        }

        @Override
        protected State<S> addCallbacks(Function<? super Callable<?>, ? extends Runnable> targetSetup,
                                        Function<? super S, ?> successCallback, 
                                        Function<Throwable, ?> failureCallback, 
                                        Executor executor) {
            
            callCallback(targetSetup, failureCallback, failure, executor);
            return this;
        }
    }

    private static final class CallbackHolder<S> {
        private final Function<? super Callable<?>, ? extends Runnable> targetSetup;
        private final Function<? super S, ?> successCallback;
        private final Function<Throwable, ?> failureCallback;
        private final Executor executor;

        private CallbackHolder(Function<? super Callable<?>, ? extends Runnable> targetSetup,
                               Function<? super S, ?> successCallback, 
                               Function<Throwable, ?> failureCallback, 
                               Executor executor) {

            this.targetSetup = targetSetup;
            this.successCallback = successCallback;
            this.failureCallback = failureCallback;
            this.executor = executor;
        }

        void callSuccessCallback(S result) {
            callCallback(targetSetup, successCallback, result, executor);
        }

        void callFailureCallback(Throwable failure) {
            callCallback(targetSetup, failureCallback, failure, executor);
        }
    }

    static <S, U> void callCallback(Function<? super Callable<U>, ? extends Runnable> targetSetup,
                                    Function<? super S, ? extends U> callback, 
                                    S value, 
                                    Executor executor) {

        Callable<U> callable = () -> callback.apply(value);
        executor.execute(targetSetup.apply(callable));
    }

}
