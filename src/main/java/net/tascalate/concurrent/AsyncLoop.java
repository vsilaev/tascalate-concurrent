/**
 * Copyright 2015-2020 Valery Silaev (http://vsilaev.com)
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

import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

class AsyncLoop<T> extends CompletablePromise<T> {
    private final Predicate<? super T> loopCondition;
    private final Function<? super T, ? extends CompletionStage<T>> loopBody;
    
    AsyncLoop(Predicate<? super T> loopCondition, Function<? super T, ? extends CompletionStage<T>> loopBody) {
        this.loopCondition = loopCondition;
        this.loopBody      = loopBody;
    }
    
    void run(T resolvedValue, Thread initialThread, AsyncLoop.IterationState<T> initialState) {
        Thread currentThread = Thread.currentThread();
        if (currentThread.equals(initialThread) && initialState.running) {
            initialState.put(resolvedValue);
            return;
        }
        AsyncLoop.IterationState<T> currentState = new AsyncLoop.IterationState<>();
        T currentValue = resolvedValue;
        try {
            do {
                try {
                    if (isDone()) {
                        break;
                    } else if (loopCondition.test(currentValue)) {
                        loopBody.apply(currentValue).whenComplete((next, ex) -> {
                            if (ex != null) {
                                onFailure(ex);
                            } else {
                                run(next, currentThread, currentState);
                            }
                        });
                    } else {
                        onSuccess(currentValue);
                        break;
                    }
                } catch (final Throwable ex) {
                    onFailure(ex);
                    break;
                }
            } while ((currentValue = currentState.take()) != IterationState.END);
        } finally {
            currentState.running = false;
        }
    }
    
    private static final class IterationState<T> {
        static final Object END = new Object();
        
        boolean running = true;
        
        @SuppressWarnings("unchecked")
        private T value = (T)END;
        
        @SuppressWarnings("unchecked")
        T take() {
            T result = value;
            value = (T)END;
            return result;
        }
        
        void put(T value) {
            this.value = value;
        }
    }
}