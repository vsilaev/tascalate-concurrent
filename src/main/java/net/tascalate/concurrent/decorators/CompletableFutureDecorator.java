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
package net.tascalate.concurrent.decorators;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.decorators.AbstractFutureDecorator;

public class CompletableFutureDecorator<T> 
    extends AbstractFutureDecorator<T, CompletableFuture<T>> 
    implements Promise<T> {

    protected CompletableFutureDecorator() {
        this(new CompletableFuture<T>());
    }
    
    protected CompletableFutureDecorator(CompletableFuture<T> delegate) {
        super(delegate);
    }

    @Override
    public T getNow(T valueIfAbsent) {
        return delegate.getNow(valueIfAbsent);
    }
    
    @Override
    public T join() throws CancellationException, CompletionException {
        return delegate.join();
    }
    
    @Override
    public boolean isCompletedExceptionally() {
        return delegate.isCompletedExceptionally();
    }

    @Override
    protected <U> Promise<U> wrap(CompletionStage<U> original) {
        return new CompletableFutureDecorator<>((CompletableFuture<U>)original);
    }

}
