/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
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

/**
 * A base class for wrapping standard {@link AsynchronousFileChannel} instances to 
 * provide a {@link Promise}-based API.
 * <p>
 * This class delegates all operations to the underlying channel but adapts the 
 * callback-based methods to return {@code Promise}s. This enables fluent, composable 
 * asynchronous I/O pipelines without the boilerplate of nested {@link CompletionHandler}s.
 * </p>
 *
 * @param <F> the concrete subtype of this channel, used for fluent method chaining
 * @author vsilaev
 * @see AsynchronousFileChannel
 * @see Promise
 */
public class AbstractAsyncFileChannel<F extends AbstractAsyncFileChannel<F>>
        extends AsynchronousFileChannel
        implements AsyncChannel {

    /** The underlying delegated asynchronous file channel. */
    private final AsynchronousFileChannel delegate;

    /**
     * Creates a new wrapper around the given delegate channel.
     *
     * @param delegate the underlying {@link AsynchronousFileChannel} to delegate to
     */
    protected AbstractAsyncFileChannel(AsynchronousFileChannel delegate) {
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

    /**
     * Truncates this channel's file to the given size.
     *
     * @param size the new size, a non-negative byte count
     * @return this channel, for fluent chaining
     * @throws IOException if an I/O error occurs
     */
    @Override
    public F truncate(long size) throws IOException {
        delegate.truncate(size);
        @SuppressWarnings("unchecked")
        F self = (F) this;
        return self;
    }

    @Override
    public void force(boolean metaData) throws IOException {
        delegate.force(metaData);
    }

    /**
     * Acquires a lock on the entire file.
     *
     * @param shared {@code true} to request a shared lock, {@code false} for an exclusive lock
     * @return a promise that completes with the acquired {@link FileLock}
     */
    public Promise<FileLock> lock(boolean shared) {
        return doLock(0, Long.MAX_VALUE, shared);
    }

    /**
     * Acquires a lock on a region of this channel's file.
     *
     * @param position the position at which the locked region is to start
     * @param size the size of the locked region
     * @param shared {@code true} to request a shared lock, {@code false} for an exclusive lock
     * @return a promise that completes with the acquired {@link FileLock}
     */
    @Override
    public Promise<FileLock> lock(long position, long size, boolean shared) {
        return doLock(position, size, shared);
    }

    /**
     * Acquires a lock on the entire file using a callback handler.
     */
    public final <A> void lock(boolean shared, A attachment,
                               CompletionHandler<FileLock, ? super A> handler) {
        lock(0, Long.MAX_VALUE, shared, attachment, handler);
    }

    /**
     * Internal helper to bridge the callback-based lock acquisition to a Promise.
     */
    protected Promise<FileLock> doLock(long position, long size, boolean shared) {
        AsyncResult<FileLock> asyncResult = new AsyncResult<>();
        lock(position, size, shared, null, asyncResult.handler);
        return asyncResult;
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

    /**
     * Attempts to acquire a lock on the entire file without blocking.
     *
     * @param shared {@code true} to request a shared lock, {@code false} for an exclusive lock
     * @return the acquired {@link FileLock}, or {@code null} if the lock could not be acquired
     * @throws IOException if an I/O error occurs
     */
    public FileLock tryLock(boolean shared) throws IOException {
        return tryLock(0, Long.MAX_VALUE, shared);
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        return upgradeLock(delegate.tryLock(position, size, shared));
    }

    @Override
    public <A> void read(ByteBuffer dst, long position, A attachment, 
                         CompletionHandler<Integer, ? super A> handler) {
        delegate.read(dst, position, attachment, handler);
    }

    /**
     * Reads a sequence of bytes from this channel into the given buffer, starting at 
     * the given file position.
     *
     * @param dst the buffer into which bytes are to be transferred
     * @param position the position in the file at which the transfer is to begin
     * @return a promise that completes with the number of bytes read, or a negative 
     *         value if the end of the file has been reached
     */
    @Override
    public Promise<Integer> read(ByteBuffer dst, long position) {
        AsyncResult<Integer> asyncResult = new AsyncResult<>();
        read(dst, position, null, asyncResult.handler);
        return asyncResult;
    }

    @Override
    public <A> void write(ByteBuffer src, long position, A attachment, 
                          CompletionHandler<Integer, ? super A> handler) {
        delegate.write(src, position, attachment, handler);
    }

    /**
     * Writes a sequence of bytes to this channel from the given buffer, starting at 
     * the given file position.
     *
     * @param src the buffer from which bytes are to be retrieved
     * @param position the position in the file at which the transfer is to begin
     * @return a promise that completes with the number of bytes written
     */
    @Override
    public Promise<Integer> write(ByteBuffer src, long position) {
        AsyncResult<Integer> asyncResult = new AsyncResult<>();
        write(src, position, null, asyncResult.handler);
        return asyncResult;
    }

    /**
     * Wraps the delegate's {@link FileLock} in a channel-specific implementation 
     * to ensure the lock is associated with this wrapper channel.
     */
    protected FileLock upgradeLock(FileLock delegateLock) {
        return new FileLock(this, delegateLock.position(), delegateLock.size(), delegateLock.isShared()) {
            @Override
            public void release() throws IOException {
                delegateLock.release();
            }

            @Override
            public boolean isValid() {
                return delegateLock.isValid();
            }
        };
    }
}