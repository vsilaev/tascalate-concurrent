/**
 * Copyright 2015-2019 Valery Silaev (http://vsilaev.com)
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

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

abstract class Try<R> {

    abstract R done();
    
    static final class Success<R> extends Try<R> {
        
        private final R result;
        
        Success(R result) {
            this.result = result;
        }
        
        @Override
        R done() {
            return result;
        }
    }
    
    static final class Failure<R> extends Try<R> {
        
        private final Throwable error;
        
        Failure(Throwable error) {
            this.error = error;
        }
        
        @Override
        R done() {
            return SharedFunctions.sneakyThrow(error);
        }
        
    }
    
    static <R> Try<R> success(R result) {
        return new Success<R>(result);
    }

    static <R> Try<R> failure(Throwable error) {
        return new Failure<R>(error);
    }
    
    static <R> Supplier<Try<R>> with(Supplier<? extends R> supplier) {
        return () -> call(supplier);
    }
    
    static <R> R doneOrTimeout(Try<R> result, Duration duration) {
        Try<R> checkedResult = null != result ? result: failure(new TimeoutException("Timeout after " + duration));
        return checkedResult.done();        
    }
    
    @SuppressWarnings("unchecked")
    static <R> Try<R> nothing() {
        return (Try<R>)NOTHING;
    }
    
    private static <R> Try<R> call(Supplier<? extends R> supplier) {
        try {
            return success(supplier.get());
        } catch (Throwable ex) {
            return failure(ex);
        }
    }
    
    private static final Try<Object> NOTHING = success(null); 
}