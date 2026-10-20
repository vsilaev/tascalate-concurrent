/**
 * Original work: copyright 2009-2015 Lukáš Křečan
 * https://github.com/lukas-krecan/completion-stage
 * 
 * This class is based on the work create by Lukáš Křečan 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/lukas-krecan/completion-stage/blob/completion-stage-0.0.9/src/main/java/net/javacrumbs/completionstage/CallbackRegistry.java 
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

class CallbackRegistry<T> {
    
    // Updater for the main state machine
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<CallbackRegistry, State> STATE_UPDATER =
        AtomicReferenceFieldUpdater.newUpdater(CallbackRegistry.class, State.class, "state");

    @SuppressWarnings("unused")
    private volatile State<T> state = InitialState.instance();

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

    boolean isFailure() {
        return STATE_UPDATER.get(this).isFailure();
    }

    boolean isCompleted() {
        return STATE_UPDATER.get(this).isCompleted();
    }

    // --- State Machine Classes ---

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

    static final class IntermediateState<S> extends State<S> {
        // Updater for the lock-free stack head
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<IntermediateState, CallbackNode> HEAD_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(IntermediateState.class, CallbackNode.class, "head");

        private static final CallbackNode<Object> CLOSED = new CallbackNode<>(null);
        
        // Replaced AtomicReference with a volatile field
        @SuppressWarnings("unused")
        private volatile CallbackNode<S> head;

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

    static final class CallbackNode<S> {
        final BiConsumer<? super S, ? super Throwable> callback;
        
        // Volatile is sufficient here; it's written once before publication via the head CAS
        volatile CallbackNode<S> next; 
        
        CallbackNode(BiConsumer<? super S, ? super Throwable> cb) { 
            this.callback = cb; 
        }
    }
    
    static abstract class CompletedState<S> extends State<S> {
        protected abstract <U> void fireCallback(BiConsumer<? super S, ? super Throwable> callback);

        @Override
        protected final boolean isCompleted() { 
            return true; 
        }
    }

    static final class SuccessState<S> extends CompletedState<S> {
        private final S result;
        
        SuccessState(S result) { this.result = result; }
        
        @Override
        protected <U> void fireCallback(BiConsumer<? super S, ? super Throwable> callback) {
            callback.accept(result, null);
        }
    }

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

    @FunctionalInterface
    static interface AsyncTask extends Runnable, CompletableFuture.AsynchronousCompletionTask {}
}