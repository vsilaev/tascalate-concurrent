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
package net.tascalate.concurrent.io;

import java.io.IOException;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.spi.AsynchronousChannelProvider;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import net.tascalate.concurrent.TaskExecutorService;
import net.tascalate.concurrent.TaskExecutors;

public class AsyncChannelGroup extends AsynchronousChannelGroup {
    private final AsynchronousChannelGroup delegate;
    
    AsyncChannelGroup(AsynchronousChannelProvider provider, AsynchronousChannelGroup delegate) {
        super(provider);
        this.delegate = delegate;
    }
    
    public static AsyncChannelGroup withFixedThreadPool(int nThreads, ThreadFactory threadFactory) throws IOException {
        return withThreadPool(TaskExecutors.newFixedThreadPool(nThreads, threadFactory), 0);
    }
    
    public static AsyncChannelGroup withThreadPool(TaskExecutorService executor) throws IOException {
        return withThreadPool(executor, 0);
    }
    
    public static AsyncChannelGroup withThreadPool(TaskExecutorService executor, int initialSize) throws IOException {
        Objects.requireNonNull(executor, "Executor should be specified");
        AsynchronousChannelProvider provider = AsynchronousChannelProvider.provider();
        return new AsyncChannelGroup(provider, provider.openAsynchronousChannelGroup(executor, initialSize));
    }

    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    public void shutdown() {
        delegate.shutdown();
    }

    public void shutdownNow() throws IOException {
        delegate.shutdownNow();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
}
