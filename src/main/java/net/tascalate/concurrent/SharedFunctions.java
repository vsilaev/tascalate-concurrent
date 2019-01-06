/**
 * ﻿Copyright 2015-2018 Valery Silaev (http://vsilaev.com)
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

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Supplier;

class SharedFunctions {
    
    private SharedFunctions() {}
    
    static Throwable unwrapCompletionException(Throwable ex) {
        Throwable nested = ex;
        while (nested instanceof CompletionException) {
            nested = nested.getCause();
        }
        return null == nested ? ex : nested;
    }

    static CompletionException wrapCompletionException(Throwable e) {
        if (e instanceof CompletionException) {
            return (CompletionException) e;
        } else {
            return new CompletionException(e);
        }
    }
    
    static Throwable unwrapExecutionException(Throwable ex) {
        Throwable nested = ex;
        while (nested instanceof ExecutionException) {
            nested = nested.getCause();
        }
        return null == nested ? ex : nested;
    }
    
    static ExecutionException wrapExecutionException(Throwable e) {
        if (e instanceof ExecutionException) {
            return (ExecutionException) e;
        } else {
            return new ExecutionException(e);
        }
    }
    
    
    static boolean cancelPromise(CompletionStage<?> promise, boolean mayInterruptIfRunning) {
        if (promise instanceof Future) {
            Future<?> future = (Future<?>) promise;
            return future.cancel(mayInterruptIfRunning);
        } else {
            Method m = completeExceptionallyMethodOf(promise);
            if (null != m) {
                try {
                    return (Boolean) m.invoke(promise, new CancellationException());
                } catch (final ReflectiveOperationException ex) {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    private static Method completeExceptionallyMethodOf(CompletionStage<?> promise) {
        try {
            Class<?> clazz = promise.getClass();
            return clazz.getMethod("completeExceptionally", Throwable.class);
        } catch (ReflectiveOperationException | SecurityException ex) {
            return null;
        }
    }
    
    
    static Set<PromiseOrigin> enlistParamOrAll(boolean enlistThis) {
        return enlistThis ? PromiseOrigin.ALL : PromiseOrigin.PARAM_ONLY;
    }
    
    static Set<PromiseOrigin> enlistParamOrNone(boolean enlistParam) {
        return enlistParam ? PromiseOrigin.PARAM_ONLY : PromiseOrigin.NONE;
    }

    
    @SuppressWarnings("unchecked")
    static <U, V> BiFunction<U, V, U> selectFirst() {
        return (BiFunction<U, V, U>)SELECT_FIRST;
    }
    
    @SuppressWarnings("unchecked")
    static <U, V> BiFunction<U, V, V> selectSecond() {
        return (BiFunction<U, V, V>)SELECT_SECOND;
    }
    
    static <T> Supplier<T> supply(T value) {
        return () -> value;
    }
    
    static <T> T dereferenceOr(ObjectRef<? extends T> ref, Supplier<? extends Throwable> error) {
        if (null != ref) {
            return ref.dereference();
        } else {
            return sneakyThrow(error.get());
        }
    }
    
    static Supplier<TimeoutException> timeout(Duration duration) {
        return supply(new TimeoutException("Timeout after " + duration));        
    }
    
    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }
    
    static class ObjectRef<T> {
        private final T reference;
        
        ObjectRef(T reference) {
            this.reference = reference;
        }
        
        T dereference() {
            return reference;
        }
    }
    
    private static final BiFunction<Object, Object, Object> SELECT_FIRST  = (u, v) -> u;
    private static final BiFunction<Object, Object, Object> SELECT_SECOND = (u, v) -> v;
}
