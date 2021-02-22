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
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.TaskExecutorService;

public class AsyncFileChannel extends AsynchronousFileChannel 
                              implements AsyncChannel {
    private final AsynchronousFileChannel delegate;
    
    AsyncFileChannel(AsynchronousFileChannel delegate) {
        this.delegate = delegate;
    }
    
    public static AsyncFileChannel open(Path file, TaskExecutorService executor, OpenOption... options) throws IOException {
        Set<OpenOption> set;
        if (options.length == 0) {
            set = Collections.emptySet();
        } else {
            set = new HashSet<>();
            Collections.addAll(set, options);
        }
        return open(file, executor, set, NO_ATTRIBUTES);
    }

    public static AsyncFileChannel open(Path file,
                                      TaskExecutorService executor,
                                      Set<? extends OpenOption> options,
                                      FileAttribute<?>... attrs) throws IOException {
        Objects.requireNonNull(executor, "Executor should be specified");
        return new AsyncFileChannel(AsynchronousFileChannel.open(file, options, executor, attrs));
    }
    
    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public long size() throws IOException {
        return delegate.size();
    }

    @Override
    public AsyncFileChannel truncate(long size) throws IOException {
        delegate.truncate(size);
        return this;
    }

    @Override
    public void force(boolean metaData) throws IOException {
        delegate.force(metaData);
    }

    public Promise<FileLock> lock(boolean shared) {
        return lock(0, Long.MAX_VALUE, shared);
    }
    
    @Override
    public Promise<FileLock> lock(long position, long size, boolean shared) {
        Promise<FileLock> promise = (Promise<FileLock>)delegate.lock(position, size, shared);
        return promise.dependent()
                      .thenApply(this::upgradeLock, true)
                      .unwrap();
    }

    public final <A> void lock(boolean shared, A attachment,
                               CompletionHandler<FileLock, ? super A> handler) {
        lock(0, Long.MAX_VALUE, shared, attachment, handler);
    }
    
    @Override
    public <A> void lock(long position, long size, boolean shared, A attachment,
                         CompletionHandler<FileLock, ? super A> handler) {
        if (null == handler) {
            delegate.lock(position, size, shared, attachment, null);
            return;
        }
        delegate.lock(position, size, shared, attachment, new CompletionHandler<FileLock, A>() {
            @Override
            public void completed(FileLock result, A attachment) {
                handler.completed(upgradeLock(result), attachment);
            }

            @Override
            public void failed(Throwable ex, A attachment) {
                handler.failed(ex, attachment);
            }
        });
    }

    public FileLock tryLock(boolean shared) throws IOException {
        return tryLock(0, Long.MAX_VALUE, shared);
    }
    
    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        return upgradeLock(delegate.tryLock(position, size, shared));
    }
    
    @Override
    public <A> void read(ByteBuffer dst, long position, A attachment, CompletionHandler<Integer, ? super A> handler) {
        delegate.read(dst, position, attachment, handler);
    }

    @Override
    public Promise<Integer> read(ByteBuffer dst, long position) {
        return (Promise<Integer>)delegate.read(dst, position);
    }

    @Override
    public <A> void write(ByteBuffer src, long position, A attachment, CompletionHandler<Integer, ? super A> handler) {
        delegate.write(src, position, attachment, handler);
    }

    @Override
    public Promise<Integer> write(ByteBuffer src, long position) {
        return (Promise<Integer>)delegate.write(src, position);
    }
    
    protected FileLock upgradeLock(FileLock delegate) {
        return new FileLock(this, delegate.position(), delegate.size(), delegate.isShared()) {
            @Override
            public void release() throws IOException {
                delegate.release();
            }
            
            @Override
            public boolean isValid() {
                return delegate.isValid();
            }
        };
    }

    private static final FileAttribute<?>[] NO_ATTRIBUTES = new FileAttribute[0];
}
