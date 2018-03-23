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
package net.tascalate.concurrent.decorators;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import net.tascalate.concurrent.Promise;

/**
 * Helper class to create a concrete {@link Promise} subclass via delegation
 * to the wrapped {@link Promise} 
 *  
 * @author vsilaev
 *
 * @param <T>
 *   a type of the successfully resolved promise value   
 * @param <D>
 *   a type of the concrete {@link Promise} subclass
 */
abstract public class AbstractPromiseDecorator<T, D extends Promise<T>>
    extends AbstractFutureDecorator<T, D> 
    implements Promise<T> {
	
    protected AbstractPromiseDecorator(D delegate) {
        super(delegate);
    }
    
    @Override
    public T getNow(T valueIfAbsent) throws CancellationException, CompletionException {
        return delegate.getNow(valueIfAbsent);
    }
    
    @Override
    public T getNow(Supplier<? extends T> valueIfAbsent) throws CancellationException, CompletionException {
        return delegate.getNow(valueIfAbsent);
    }
    
    @Override
    public T join() throws CancellationException, CompletionException {
        return delegate.join();
    }
    
    @Override
    public Promise<T> raw() {
        return delegate.raw();
    }
	
    @Override
    public Promise<T> delay(long timeout, TimeUnit unit) {
        return wrap(delegate.delay(timeout, unit));
    }
    
    @Override
    public Promise<T> delay(long timeout, TimeUnit unit, boolean delayOnError) {
        return wrap(delegate.delay(timeout, unit, delayOnError));
    }
    
    @Override
    public Promise<T> delay(Duration duration) {
        return wrap(delegate.delay(duration));
    }
    
    @Override
    public Promise<T> delay(Duration duration, boolean delayOnError) {
        return wrap(delegate.delay(duration, delayOnError));
    }

    @Override
    public Promise<T> orTimeout(long timeout, TimeUnit unit) {
        return wrap(delegate.orTimeout(timeout, unit));
    }
    
    @Override
    public Promise<T> orTimeout(long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return wrap(delegate.orTimeout(timeout, unit, cancelOnTimeout));
    }
    
    @Override
    public Promise<T> orTimeout(Duration duration) {
        return wrap(delegate.orTimeout(duration));
    }
    
    @Override
    public Promise<T> orTimeout(Duration duration, boolean cancelOnTimeout) {
        return wrap(delegate.orTimeout(duration, cancelOnTimeout));
    }
    
    @Override
    public Promise<T> onTimeout(T value, long timeout, TimeUnit unit) {
        return wrap(delegate.onTimeout(value, timeout, unit));
    }
    
    @Override
    public Promise<T> onTimeout(T value, long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return wrap(delegate.onTimeout(value, timeout, unit, cancelOnTimeout));
    }

    @Override
    public Promise<T> onTimeout(T value, Duration duration) {
        return wrap(delegate.onTimeout(value, duration));
    }
    
    @Override
    public Promise<T> onTimeout(T value, Duration duration, boolean cancelOnTimeout) {
        return wrap(delegate.onTimeout(value, duration, cancelOnTimeout));
    }
    
    @Override
    public Promise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit) {
        return wrap(delegate.onTimeout(supplier, timeout, unit));
    }
    
    @Override
    public Promise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return wrap(delegate.onTimeout(supplier, timeout, unit, cancelOnTimeout));
    }
    
    @Override
    public Promise<T> onTimeout(Supplier<? extends T> supplier, Duration duration) {
        return wrap(delegate.onTimeout(supplier, duration));
    }
    
    @Override
    public Promise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout) {
        return wrap(delegate.onTimeout(supplier, duration, cancelOnTimeout));
    }
}
