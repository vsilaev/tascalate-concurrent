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

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import net.tascalate.concurrent.decorators.AbstractPromiseDecorator;

abstract class AbstractDependentPromise<T> implements Promise<T> {
    final protected Promise<T> delegate;
    final protected CompletionStage<?>[] cancellableOrigins;

    protected AbstractDependentPromise(Promise<T> delegate, CompletionStage<?>[] cancellableOrigins) {
        this.delegate = delegate;
        this.cancellableOrigins = cancellableOrigins;
    }
    
    protected void cancelOrigins(boolean mayInterruptIfRunning) {
        if (null != cancellableOrigins) {
            Arrays.stream(cancellableOrigins).filter(p -> p != null).forEach(p -> cancelPromise(p, mayInterruptIfRunning));
        }
    }
    
    protected boolean cancelPromise(CompletionStage<?> promise, boolean mayInterruptIfRunning) {
        return CompletablePromise.cancelPromise(promise, mayInterruptIfRunning);
    }
    
    protected void checkCanceledOnCreation() {
        if (isCancelled()) {
            // Wrapped over already cancelled Promise
            // So result.cancel() has no effect
            // and we have to cancel origins explicitly
            // right after construction
            cancelOrigins(true);
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (delegate.cancel(mayInterruptIfRunning)) {
            cancelOrigins(mayInterruptIfRunning);
            return true;
        } else {
            return false;
        }
    }
    
    @Override
    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    @Override
    public boolean isDone() {
        return delegate.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return delegate.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.get(timeout, unit);
    }

    @Override
    public T getNow(T valueIfAbsent) {
        return delegate.getNow(valueIfAbsent);
    }
    
    public T getNow(Supplier<? extends T> valueIfAbsent) {
        return delegate.getNow(valueIfAbsent);
    }

    @Override
    public Promise<T> raw() {
        if (null == cancellableOrigins || cancellableOrigins.length == 0) {
            // No state collected, may optimize away own reference
            return delegate.raw();
        } else {
            final CompletionStage<?>[] dependent = cancellableOrigins;
            return new AbstractPromiseDecorator<T, Promise<T>>(delegate.raw()) {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    if (super.cancel(mayInterruptIfRunning)) {
                        if (null != dependent) {
                            Arrays.stream(dependent).filter(p -> p != null).forEach(p -> cancelPromise(p, mayInterruptIfRunning));
                        }
                        return true;
                    } else {
                        return false;
                    }
                }
                
                @Override
                public Promise<T> raw() {
                    // May not unwrap further
                    return this;
                }

                @Override
                protected <U> Promise<U> wrap(CompletionStage<U> original) {
                    return (Promise<U>)original;
                }
            };
        }
    }
    

    protected CompletableFuture<T> toCompletableFuture(boolean enlistOrigin) {
        if (!enlistOrigin) {
            return delegate.toCompletableFuture();
        } else {
            CompletableFuture<T> result = new CompletableFuture<T>() {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    if (super.cancel(mayInterruptIfRunning)) {
                        AbstractDependentPromise.this.cancel(mayInterruptIfRunning);
                        return true;
                    } else {
                        return false;
                    }
                }
            };
            whenComplete((r, e) -> {
               if (null != e) {
                   result.completeExceptionally(e);
               } else {
                   result.complete(r);
               }
            });
            return result;
        }
    }

}
