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

import static net.tascalate.concurrent.SharedFunctions.cancelPromise;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

abstract class LinkedCompletion<T, F> extends CompletableFuture<T> {
    protected F dependency;
    
    LinkedCompletion<T, F> dependsOn(F dependency) {
        this.dependency = dependency;
        return this;
    }
    
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (cancelDependency(mayInterruptIfRunning)) {
            return super.cancel(mayInterruptIfRunning);
        } else {
            return false;
        }
    }
    
    final Promise<T> toPromise() {
        return new CompletableFutureWrapper<>(this);
    }
    
    abstract boolean cancelDependency(boolean mayInterruptIfRunning);
    

    static final class FutureCompletion<T> extends LinkedCompletion<T, Future<?>> {
        @Override
        boolean cancelDependency(boolean mayInterruptIfRunning) {
            return dependency.cancel(mayInterruptIfRunning);
        }
        
        @Override
        FutureCompletion<T> dependsOn(Future<?> dependency) {
            return (FutureCompletion<T>)super.dependsOn(dependency);
        }
    };
    
    
    static final class StageCompletion<T> extends LinkedCompletion<T, CompletionStage<?>> {
        @Override
        boolean cancelDependency(boolean mayInterruptIfRunning) {
            return cancelPromise(dependency, mayInterruptIfRunning);
        }
        
        @Override
        StageCompletion<T> dependsOn(CompletionStage<?> dependency) {
            return (StageCompletion<T>)super.dependsOn(dependency);
        }
    };
}


