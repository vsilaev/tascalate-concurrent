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

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinTask;

public class ExecutorAwareThread extends Thread {
    private final Executor executor;

    ExecutorAwareThread(Executor executor, ThreadGroup group, String name) {
        this(executor, group, null, name, 0);
    }
    
    protected ExecutorAwareThread(Executor executor, ThreadGroup group, Runnable target, String name) {
        this(executor, group, target, name, 0);
    }

    
    public ExecutorAwareThread(Executor executor,
                               ThreadGroup group, 
                               Runnable target, 
                               String name,
                               long stackSize) {
        super(group, target, name, stackSize);
        this.executor = executor;
    }
    
    public static Executor currentExecutor() {
        Thread t = Thread.currentThread();
        if (t instanceof ExecutorAwareThread) {
            ExecutorAwareThread eat = (ExecutorAwareThread)t;
            return eat.executor;
        } else {
            return ForkJoinTask.getPool();
        }
    }
    
    public static ThreadFactoryBuilder newThreadFactory(Executor executor) {
        return new ThreadFactoryBuilder() {
            @Override
            protected Thread createThread(ThreadGroup threadGroup, Runnable runnable, String name) {
                return new ExecutorAwareThread(executor, threadGroup, runnable, name);
            }
        };
    }
}
