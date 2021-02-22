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

import net.tascalate.concurrent.Promise;

public class AbstractAsyncFileChannel<F extends AbstractAsyncFileChannel<F>> 
    extends AsynchronousFileChannel 
    implements AsyncChannel {
    
    private final AsynchronousFileChannel delegate;
    
    AbstractAsyncFileChannel(AsynchronousFileChannel delegate) {
        this.delegate = delegate;
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
    public F truncate(long size) throws IOException {
        delegate.truncate(size);
        @SuppressWarnings("unchecked")
        F self = (F)this;
        return self;
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
}
