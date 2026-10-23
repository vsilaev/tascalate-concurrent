/**
 * Original work: copyright 2009-2015 Lukáš Křečan
 * https://github.com/lukas-krecan/completion-stage
 * 
 * This class is based on the work create by Lukáš Křečan 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/lukas-krecan/completion-stage/blob/completion-stage-0.0.9/src/main/java/net/javacrumbs/completionstage/CallbackRegistry.java 
 * 
 *
 * Modified work: copyright 2015-2026 Valery Silaev (http://vsilaev.com)
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

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A lock-free state machine that tracks the completion state of an 
 * {@link AbstractCompletableTask} and manages its registered dependent callbacks.
 * <p>
 * This registry uses {@link AtomicReferenceFieldUpdater} to transition between 
 * states ({@link InitialState}, {@link IntermediateState}, {@link SuccessState}, 
 * {@link FailureState}) without requiring explicit synchronization. When the task 
 * completes, all callbacks registered during the {@link IntermediateState} are 
 * drained and executed on their respective executors.
 * </p>
 *
 * @param <T> the type of the successfully resolved promise value
 * @author vsilaev
 * @see AbstractCompletableTask
 */
class CallbackRegistry<T> {

    /** Updater for the main state machine. */
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<CallbackRegistry, State> STATE_UPDATER =
        AtomicReferenceFieldUpdater.newUpdater(CallbackRegistry.class, State.class, "state");

    /** The current state of the registry. */
    @SuppressWarnings("unused")
    private volatile State<T> state = InitialState.instance();

    /**
     * Registers success and failure callbacks to be invoked when this registry 
     * completes.
     * <p>
     * If the registry is already completed, the appropriate callback is fired 
     * immediately. Otherwise, the callbacks are added to a lock-free stack 
     * (the {@link IntermediateState}) and will be drained upon completion.
     * </p>
     *
     * @param <U>             the result type of the target stage
     * @param targetStage     the stage that will receive the outcome
     * @param successCallback the function to map a successful result
     * @param failureCallback the function to map an exception
     * @param executor        the executor to run the callback on
     */
    <U> void addCallbacks(AbstractCompletableTask<U> target,
                          Function<? super T, ? extends U> successCallback, 
                          Function<Throwable, ? extends U> failureCallback,
                          Executor executor) {
        Objects.requireNonNull(successCallback, "'successCallback' must not be null");
        Objects.requireNonNull(failureCallback, "'failureCallback' must not be null");
        Objects.requireNonNull(executor, "'executor' must not be null");

        BiConsumer<T, Throwable> callback = (r, e) -> {
            if (null == e) {
                callCallback(target, successCallback, r, executor);
            } else {
                callCallback(target, failureCallback, e, executor);
            }
        };

        while (true) {
            @SuppressWarnings("unchecked")
            State<T> current = STATE_UPDATER.get(this);
            if (current.isCompleted()) {
                ((CompletedState<T>)current).fireCallback(callback);
                return;
            }
            if (current.isInitial()) {
                IntermediateState<T> intermediate = new IntermediateState<>();
                intermediate.addCallback(callback); 
                if (STATE_UPDATER.compareAndSet(this, current, intermediate)) {
                    return;
                }
            } else {
                IntermediateState<T> intermediate = (IntermediateState<T>) current;
                if (intermediate.addCallback(callback)) {
                    return;
                }
            }
        }
    }

    /**
     * Transitions the registry to the success state with the given result.
     *
     * @param result the successful result value
     * @return {@code true} if this invocation successfully transitioned the state; 
     *         {@code false} if the registry was already completed
     */
    boolean success(T result) {
        while (true) {
            @SuppressWarnings("unchecked")
            State<T> current = STATE_UPDATER.get(this);
            if (current.isCompleted()) {
                return false;
            }
            if (current.isInitial()) {
                SuccessState<T> newState = new SuccessState<>(result);
                if (STATE_UPDATER.compareAndSet(this, current, newState)) {
                    return true;
                }
            } else {
                IntermediateState<T> intermediate = (IntermediateState<T>) current;
                SuccessState<T> newState = new SuccessState<>(result);
                if (STATE_UPDATER.compareAndSet(this, current, newState)) {
                    intermediate.drainAndClose(result, null);
                    return true;
                }
            }
        }
    }

    /**
     * Transitions the registry to the failure state with the given exception.
     *
     * @param failure the exception causing the failure
     * @return {@code true} if this invocation successfully transitioned the state; 
     *         {@code false} if the registry was already completed
     */
    boolean failure(Throwable failure) {
        while (true) {
            @SuppressWarnings("unchecked")
            State<T> current = STATE_UPDATER.get(this);
            if (current.isCompleted()) {
                return false;
            }
            if (current.isInitial()) {
                FailureState<T> newState = new FailureState<>(failure);
                if (STATE_UPDATER.compareAndSet(this, current, newState)) {
                    return true;
                }
            } else {
                IntermediateState<T> intermediate = (IntermediateState<T>) current;
                FailureState<T> newState = new FailureState<>(failure);
                if (STATE_UPDATER.compareAndSet(this, current, newState)) {
                    intermediate.drainAndClose(null, failure);
                    return true;
                }
            }
        }
    }

    /**
     * Checks if the registry has completed exceptionally.
     *
     * @return {@code true} if in the {@link FailureState}
     */
    boolean isFailure() {
        return STATE_UPDATER.get(this).isFailure();
    }

    /**
     * Checks if the registry has completed (either successfully or exceptionally).
     *
     * @return {@code true} if in a {@link CompletedState}
     */
    boolean isCompleted() {
        return STATE_UPDATER.get(this).isCompleted();
    }

    // ========================================================================
    // State Machine Classes
    // ========================================================================

    /**
     * Base class for the registry's state machine states.
     */
    static abstract class State<S> {
        protected boolean isInitial() {
            return false;
        }
        protected boolean isCompleted() {
            return false; 
        }
        protected boolean isFailure() { 
            return false; 
        }
    }

    /**
     * The initial state of the registry before any callbacks are registered 
     * and before completion.
     */
    static final class InitialState<S> extends State<S> {
        private static final InitialState<Object> instance = new InitialState<>();

        @Override
        protected boolean isInitial() {
            return true;
        }

        @SuppressWarnings("unchecked")
        static <T> State<T> instance() { 
            return (State<T>) instance; 
        }
    }

    /**
     * The intermediate state where callbacks are being registered but the 
     * registry has not yet completed. Uses a lock-free stack to store callbacks.
     */
    static final class IntermediateState<S> extends State<S> {
        /** Updater for the lock-free stack head. */
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<IntermediateState, CallbackNode> HEAD_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(IntermediateState.class, CallbackNode.class, "head");

        /** Sentinel node indicating the stack is closed and drained. */
        private static final CallbackNode<Object> CLOSED = new CallbackNode<>(null);

        /** The head of the lock-free callback stack. */
        @SuppressWarnings("unused")
        private volatile CallbackNode<S> head;

        /**
         * Pushes a callback onto the lock-free stack.
         *
         * @param callback the callback to add
         * @return {@code true} if successfully added; {@code false} if the stack was closed
         */
        boolean addCallback(BiConsumer<? super S, ? super Throwable> callback) {
            CallbackNode<S> newHead = new CallbackNode<>(callback);
            while (true) {
                @SuppressWarnings("unchecked")
                CallbackNode<S> current = (CallbackNode<S>) HEAD_UPDATER.get(this);
                if (current == CLOSED) {
                    return false; // Stack is closed
                }
                newHead.next = current;
                if (HEAD_UPDATER.compareAndSet(this, current, newHead)) {
                    return true;
                }
            }
        }

        /**
         * Closes the stack and drains all registered callbacks, invoking them 
         * with the final result or failure.
         *
         * @param result  the successful result (or {@code null} if failed)
         * @param failure the exception (or {@code null} if successful)
         */
        void drainAndClose(S result, Throwable failure) {
            CallbackNode<S> current;
            // Emulating getAndSet via CAS loop for Java 8 compatibility
            while (true) {
                @SuppressWarnings("unchecked")
                CallbackNode<S> oldHead = (CallbackNode<S>) HEAD_UPDATER.get(this);
                if (HEAD_UPDATER.compareAndSet(this, oldHead, CLOSED)) {
                    current = oldHead;
                    break;
                }
            }
            // Traverse and invoke
            while (current != null) {
                current.callback.accept(result, failure);
                current = current.next;
            }
        }
    }

    /**
     * A node in the lock-free callback stack.
     */
    static final class CallbackNode<S> {
        /** The callback to invoke. */
        final BiConsumer<? super S, ? super Throwable> callback;
        /** The next node in the stack. Volatile is sufficient here. */
        volatile CallbackNode<S> next; 

        CallbackNode(BiConsumer<? super S, ? super Throwable> cb) { 
            this.callback = cb; 
        }
    }

    /**
     * Base class for states representing a completed registry.
     */
    static abstract class CompletedState<S> extends State<S> {
        /**
         * Fires the given callback with the completed state's result or failure.
         *
         * @param callback the callback to invoke
         */
        protected abstract <U> void fireCallback(BiConsumer<? super S, ? super Throwable> callback);

        @Override
        protected final boolean isCompleted() { 
            return true; 
        }
    }

    /**
     * The state representing successful completion.
     */
    static final class SuccessState<S> extends CompletedState<S> {
        private final S result;

        SuccessState(S result) { this.result = result; }

        @Override
        protected <U> void fireCallback(BiConsumer<? super S, ? super Throwable> callback) {
            callback.accept(result, null);
        }
    }

    /**
     * The state representing exceptional completion.
     */
    static final class FailureState<S> extends CompletedState<S> {
        private final Throwable failure;

        FailureState(Throwable failure) { this.failure = failure; }

        @Override
        protected <U> void fireCallback(BiConsumer<? super S, ? super Throwable> callback) {
            callback.accept(null, failure);
        }

        @Override
        protected boolean isFailure() { 
            return true; 
        }
    }

    /**
     * Executes a callback on the specified executor, firing the target stage's 
     * transition with the mapped result.
     *
     * @param <T>      the original result type
     * @param <U>      the target result type
     * @param target   the stage to transition
     * @param callback the mapping function
     * @param value    the value to map
     * @param executor the executor to run the transition on
     */
    static <T, U> void callCallback(AbstractCompletableTask<U> target,
                                    Function<? super T, ? extends U> callback, 
                                    T value,  
                                    Executor executor) {
        Callable<U> callable = () -> callback.apply(value);
        try {
            executor.execute( (AsyncTask)() -> target.fireTransition(callable) );
        } catch (/*RejectedExecutionException*/ Throwable ex) {
            Callable<U> propagateError = () -> { throw ex; };
            target.fireTransition(propagateError);
        }
    }

    /**
     * Marker interface for tasks submitted to the executor, allowing them to be 
     * identified as asynchronous completion tasks by the JDK.
     */
    @FunctionalInterface
    static interface AsyncTask extends Runnable, CompletableFuture.AsynchronousCompletionTask {}
}