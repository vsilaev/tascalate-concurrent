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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.tascalate.concurrent.decorators.AbstractCompletionStageDecorator;

public class PromiseByCompletionStage<T> 
    extends AbstractCompletionStageDecorator<T, CompletionStage<T>> 
    implements Promise<T> {
    
    private final CountDownLatch whenDone;
    private volatile T result;
    private volatile Throwable fault;
    
    protected PromiseByCompletionStage(CompletionStage<T> delegate) {
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
        return PromiseUtils.cancelPromise(delegate, mayInterruptIfRunning);
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
            throw PromiseUtils.wrapExecutionException(PromiseUtils.unwrapCompletionException(fault));
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
            throw PromiseUtils.wrapExecutionException(PromiseUtils.unwrapCompletionException(fault));
        }
    }
    
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
            throw PromiseUtils.wrapCompletionException(fault);
        }        
    }
    

    @Override
    protected <U> Promise<U> wrap(CompletionStage<U> original) {
        return Promises.from(original);
    }

}
