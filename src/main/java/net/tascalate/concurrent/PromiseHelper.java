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
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;

class PromiseHelper {
    private PromiseHelper() {}
    
    abstract static class Either<A, B> {
        A left()  { throw new UnsupportedOperationException(); }
        B right() { throw new UnsupportedOperationException(); }

        abstract boolean isLeft();
        
        static <A, B> Either<A, B> lift(A a, B b) {
            if (null == b) {
                return new Either<A, B>() {
                    A left() { return a; }
                    boolean isLeft() { return true; }
                };
            } else {
                return new Either<A, B>() {
                    B right() { return b; }
                    boolean isLeft() { return false; }
                };
            }
        }
    }
    
    static <T> Promise<T> exceptionallyAsync(Promise<T> p, 
                                             Function<Throwable, ? extends T> fn) {
        
        DependentPromise<Either<T, Throwable>> h = p.dependent().handle(Either::lift, false);
        return
        h.thenCompose(e -> e.isLeft() ? p : h.thenApplyAsync(in -> fn.apply(in.right())), true)
         .unwrap(); 
    }

    static <T> Promise<T> exceptionallyAsync(Promise<T> p, 
                                             Function<Throwable, ? extends T> fn, 
                                             Executor executor) {
        DependentPromise<Either<T, Throwable>> h = p.dependent().handle(Either::lift, false);
        return
        h.thenCompose(e -> e.isLeft() ? p : h.thenApplyAsync(in -> fn.apply(in.right()), executor), true)
         .unwrap(); 
    }
    
    static <T> Promise<T> exceptionallyCompose(Promise<T> p, 
                                               Function<Throwable, ? extends CompletionStage<T>> fn) {
        
        DependentPromise<Either<T, Throwable>> h = p.dependent().handle(Either::lift, false);
        return
        h.thenCompose(e -> e.isLeft() ? p : fn.apply(e.right()), true)
         .unwrap(); 
    }

    static <T> Promise<T> exceptionallyComposeAsync(Promise<T> p,
                                                    Function<Throwable, ? extends CompletionStage<T>> fn) {
        
        DependentPromise<Either<T, Throwable>> h = p.dependent().handle(Either::lift, false);
        return
        h.thenCompose(e -> e.isLeft() ? p : h.thenComposeAsync(in -> fn.apply(in.right())), true)
         .unwrap(); 
    }
    
    static <T> Promise<T> exceptionallyComposeAsync(Promise<T> p,
                                                    Function<Throwable, ? extends CompletionStage<T>> fn,
                                                    Executor executor) {
        DependentPromise<Either<T, Throwable>> h = p.dependent().handle(Either::lift, false);
        return
        h.thenCompose(e -> e.isLeft() ? p : h.thenComposeAsync(in -> fn.apply(in.right()), executor), true)
         .unwrap();         
    }
    
    
    static <T, U> BiConsumer<T, U> timeoutsCleanup(Promise<T> self, Promise<?> timeout, boolean cancelOnTimeout) {
        return (_1, _2) -> {
            // Result comes from timeout and cancel-on-timeout is set
            // If both are done then cancel has no effect anyway
            if (cancelOnTimeout && timeout.isDone() && !timeout.isCancelled()) {
                self.cancel(true);
            }
            timeout.cancel(true);
        };
    }
}
