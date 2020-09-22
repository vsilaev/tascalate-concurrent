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

import static net.tascalate.concurrent.SharedFunctions.cancelPromise;
import static net.tascalate.concurrent.SharedFunctions.unwrapCompletionException;
import static net.tascalate.concurrent.SharedFunctions.wrapCompletionException;
import static net.tascalate.concurrent.SharedFunctions.wrapExecutionException;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import net.tascalate.concurrent.decorators.AbstractPromiseLikeDecorator;
import net.tascalate.concurrent.decorators.AbstractPromiseDecorator;
import net.tascalate.concurrent.decorators.BlockingCompletionStageDecorator;

public class CompletionStageWrapper<T> 
    extends AbstractPromiseLikeDecorator<T, CompletionStage<T>> 
    implements Promise<T> {
    
    private final CountDownLatch whenDone;
    private volatile T result;
    private volatile Throwable fault;
    
    protected CompletionStageWrapper(CompletionStage<T> delegate) {
        super(delegate);
        whenDone = new CountDownLatch(1);
        result   = null;
        fault    = null;
        delegate.whenComplete((r, e) -> {
           // It's a responsibility of delegate to call 
           // this callback at most once -- any valid
           // implementation should not invoke callback
           // more than once, so result/fault will not
           // be overwritten
           result   = r;
           fault    = e;
           whenDone.countDown();
        });
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return cancelPromise(delegate, mayInterruptIfRunning);
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        whenDone.await();
        if (null == fault) {
            return result;
        } else if (fault instanceof CancellationException) {
            // If explicitly cancelled then fault is not wrapped
            throw (CancellationException)fault;
        } else {
            throw wrapExecutionException(unwrapCompletionException(fault));
        }
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        whenDone.await(timeout, unit);
        if (null == fault) {
            return result;
        } else if (fault instanceof CancellationException) {
         // If explicitly cancelled then fault is not wrapped
            throw (CancellationException)fault;
        } else {
            throw wrapExecutionException(unwrapCompletionException(fault));
        }
    }
    
    @Override
    public boolean isCompletedExceptionally() {
        return isDone() && fault != null;
    }

    @Override
    public boolean isCancelled() {
        return isDone() && fault instanceof CancellationException;
    }

    @Override
    public boolean isDone() {
        return whenDone.getCount() == 0;
    }
    
    @Override
    public T join() {
        try {
            whenDone.await();
        } catch (InterruptedException ex) {
            throw new CompletionException(ex);
        }
        if (null == fault) {
            return result;
        } else if (fault instanceof CancellationException) {
            throw (CancellationException)fault;
        } else {
            throw wrapCompletionException(fault);
        }        
    }

    @Override
    protected <U> Promise<U> wrap(CompletionStage<U> original) {
        return Promises.from(original);
    }

    public static <T> Promise<T> from(CompletionStage<T> stage) {
        return from(stage, true);
    }
    
    public static <T> Promise<T> from(CompletionStage<T> stage, boolean strictPromise) {
        Promise<T> result;
        if (stage instanceof Future) {
            // If we can delegate blocking Future API...
            result = BlockingCompletionStageDecorator.from(stage);
        } else {
            // Otherwise fallback to own implementation
            result = new CompletionStageWrapper<>(stage);
        }
        return strictPromise ? new StrictPromise<>(result) : result;
    }
    
    // By default CompletableFuture.cancel() doesn't interrupt a promise from thenCompose(fn)!
    // Moreover, exceptionallyAsync and exceptionallyCompose[Async] doesn't play well with cancellation.
    // Pessimistically assume this "feature" for all CompletionStage impls. and fix this
    static class StrictPromise<T> extends AbstractPromiseDecorator<T, Promise<T>> {
        StrictPromise(Promise<T> delegate) {
            super(delegate);
        }

        // Don't unwrap
        @Override
        public Promise<T> unwrap() {
            return this;
        }
        
        // Don't unwrap
        @Override
        public Promise<T> raw() {
            return this;
        }

        @Override
        protected <U> Promise<U> wrap(CompletionStage<U> original) {
            return new StrictPromise<>((Promise<U>)original);
        }
        
        @Override
        public <U> Promise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
            CompletionStageRef<U> ref = new CompletionStageRef<>();
            return super.thenCompose(ref.captureResult(fn)).onCancel(ref.cancel);
        }

        @Override
        public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
            CompletionStageRef<U> ref = new CompletionStageRef<>();
            return super.thenComposeAsync(ref.captureResult(fn)).onCancel(ref.cancel);
        }

        @Override
        public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, 
                                               Executor executor) {
            CompletionStageRef<U> ref = new CompletionStageRef<>();
            return super.thenComposeAsync(ref.captureResult(fn), executor).onCancel(ref.cancel);        
        }        
        
        // Default CompletionStage API implementation for exceptionallyAsync / exceptionallyCompose[Async]
        // doesn't handle cancellation well due to numerous orchestrated calls (handle->handleAsync->thenCompose
        // Use own implementation here for safety
        @Override
        public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
            return PromiseHelper.exceptionallyAsync(this, fn);
        }
        
        @Override
        public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
            return PromiseHelper.exceptionallyAsync(this, fn, executor);
        }
        
        @Override
        public Promise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
            return PromiseHelper.exceptionallyCompose(this, fn);
        }

        @Override
        public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
            return PromiseHelper.exceptionallyComposeAsync(this, fn);
        }
        
        @Override
        public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, 
                                                    Executor executor) {
            return PromiseHelper.exceptionallyComposeAsync(this, fn, executor);
        }        
    }
}
