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
package net.tascalate.concurrent.var;

import java.util.List;
import java.util.concurrent.Callable;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.TaskExecutorService;

public class ContextualTaskExecutorService<D extends TaskExecutorService> 
    extends ContextualExecutorService<D> 
    implements TaskExecutorService {
    protected ContextualTaskExecutorService(D delegate, 
                                            List<ContextVar<?>> contextVars, 
                                            ContextTrampoline.Propagation propagation, 
                                            List<Object> capturedContext) {
        super(delegate, contextVars, propagation, capturedContext);
    }
    
    @Override
    public <T> Promise<T> submit(Callable<T> task) {
        return (Promise<T>)super.submit(task);
    }

    @Override
    public <T> Promise<T> submit(Runnable task, T result) {
        return (Promise<T>)super.submit(task, result);
    }

    @Override
    public Promise<?> submit(Runnable task) {
        return (Promise<?>)super.submit(task);
    }
    
}
