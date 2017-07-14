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

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.RunnableFuture;

public class CompletableTask<T> extends AbstractCompletableTask<T> implements RunnableFuture<T> {

    protected CompletableTask(final Executor executor, Callable<T> callable) {
        super(executor, callable);
    }

    @Override
    public void run() {
        task.run();
    }

    public static <T> Promise<T> resolve(T value, Executor defaultExecutor) {
        CompletableTask<T> result = new CompletableTask<T>(defaultExecutor, () -> value);
        SAME_THREAD_EXECUTOR.execute(result);
        return result;
    }
    
    public static Promise<Void> asyncOn(Executor defaultExecutor) {
        return resolve(null, defaultExecutor);
    }

    @Override
    Runnable setupTransition(Callable<T> code) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected <U> AbstractCompletableTask<U> createCompletionStage(Executor executor) {
        return new CompletableSubTask<U>(executor);
    }
}
