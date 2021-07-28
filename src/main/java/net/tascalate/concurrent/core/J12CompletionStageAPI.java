/**
 * Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.concurrent.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

class J12CompletionStageAPI implements CompletionStageAPI {
    
    J12CompletionStageAPI() {}
    
    @Override
    public boolean defaultExecutorOverridable() {
        return true;
    }
    
    @Override
    public Executor defaultExecutorOf(CompletableFuture<?> completableFuture) {
        return completableFuture.defaultExecutor();
    }
    
    @Override
    public <T> CompletionStage<T> exceptionallyAsync(CompletionStage<T> delegate, 
                                                     Function<Throwable, ? extends T> fn) {
        return delegate.exceptionallyAsync(fn);        
    }
    
    @Override
    public <T> CompletionStage<T> exceptionallyAsync(CompletionStage<T> delegate, 
                                                     Function<Throwable, ? extends T> fn, Executor executor) {
        return delegate.exceptionallyAsync(fn, executor);        
    }
    
    @Override
    public <T> CompletionStage<T> exceptionallyCompose(CompletionStage<T> delegate, 
                                                       Function<Throwable, ? extends CompletionStage<T>> fn) {
        return delegate.exceptionallyCompose(fn);
    }
    
    @Override
    public <T> CompletionStage<T> exceptionallyComposeAsync(CompletionStage<T> delegate, 
                                                            Function<Throwable, ? extends CompletionStage<T>> fn) {
        return delegate.exceptionallyComposeAsync(fn);
    }
    
    @Override
    public <T> CompletionStage<T> exceptionallyComposeAsync(CompletionStage<T> delegate, 
                                                            Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        return delegate.exceptionallyComposeAsync(fn, executor);
    }    
}
