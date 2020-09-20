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

import net.tascalate.concurrent.decorators.AbstractDependentPromiseDecorator;

class ExtraCancellationDependentPromise<T> extends AbstractDependentPromiseDecorator<T> {
    final Runnable code;
    ExtraCancellationDependentPromise(DependentPromise<T> origin, Runnable code) {
        super(origin);
        this.code = code;
    }
    
    DependentPromise<T> postConstruct() {
        if (isCancelled()) {
            code.run();
        } else if (isDone()) {
            //
        } else {
            delegate.whenComplete((r, e) -> {
               if (isCancelled()) {
                   code.run();
               }
            });
        }
        return this;
    }
    
    @Override
    protected <U> DependentPromise<U> wrap(CompletionStage<U> original) {
        return (DependentPromise<U>)original;
    }
    
    @Override
    public Promise<T> unwrap() {
        return new Unwrapped<>(delegate, code).postConstruct();
    }
    
    @Override
    public Promise<T> raw() {
        return unwrap(Promise::raw);
    }
    
    Promise<T> unwrap(Function<DependentPromise<T>, Promise<T>> fn) {
        Promise<T> unwrapped = fn.apply(delegate);
        if (delegate == unwrapped) {
            return this;
        } else {
            return new ExtraCancellationPromise.Unwrapped<>(unwrapped, code).postConstruct();
        }
    }
    
    
    static class Unwrapped<T> extends ExtraCancellationDependentPromise<T> {
        Unwrapped(DependentPromise<T> delegate,  Runnable code) {
            super(delegate, code);
        }
        
        @Override
        public Promise<T> unwrap() {
            return unwrap(Promise::unwrap);
        }
    }

}