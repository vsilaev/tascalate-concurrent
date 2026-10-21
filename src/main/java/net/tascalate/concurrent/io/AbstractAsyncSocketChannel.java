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
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.tascalate.concurrent.Promise;

/**
 * A base class for wrapping standard {@link AsynchronousSocketChannel} instances to 
 * provide a {@link Promise}-based API.
 * <p>
 * It delegates operations to the underlying channel while adapting callback-based 
 * methods to return {@code Promise}s, facilitating fluent composition of network 
 * I/O operations such as connecting, reading, and writing.
 * </p>
 *
 * @param <C> the concrete subtype of this channel, used for fluent method chaining
 * @author vsilaev
 * @see AsynchronousSocketChannel
 * @see Promise
 */
public class AbstractAsyncSocketChannel<C extends AbstractAsyncSocketChannel<C>>
        extends AsynchronousSocketChannel
        implements AsyncByteChannel {

    /** The underlying delegated asynchronous socket channel. */
    private final AsynchronousSocketChannel delegate;

    /**
     * Creates a new wrapper around the given delegate channel.
     *
     * @param delegate the underlying {@link AsynchronousSocketChannel} to delegate to
     */
    protected AbstractAsyncSocketChannel(AsynchronousSocketChannel delegate) {
        super(delegate.provider());
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
    public <T> T getOption(SocketOption<T> name) throws IOException {
        return delegate.getOption(name);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return delegate.supportedOptions();
    }

    /**
     * Binds the channel's socket to a local address.
     *
     * @param local the local address to bind the socket, or {@code null} to bind to an automatic local address
     * @return this channel, for fluent chaining
     * @throws IOException if an I/O error occurs
     */
    @Override
    public C bind(SocketAddress local) throws IOException {
        delegate.bind(local);
        return self();
    }

    @Override
    public <T> C setOption(SocketOption<T> name, T value) throws IOException {
        delegate.setOption(name, value);
        return self();
    }

    /**
     * Shuts down the connection for reading.
     *
     * @return this channel, for fluent chaining
     * @throws IOException if an I/O error occurs
     */
    @Override
    public C shutdownInput() throws IOException {
        delegate.shutdownInput();
        return self();
    }

    /**
     * Shuts down the connection for writing.
     *
     * @return this channel, for fluent chaining
     * @throws IOException if an I/O error occurs
     */
    @Override
    public C shutdownOutput() throws IOException {
        delegate.shutdownOutput();
        return self();
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        return delegate.getRemoteAddress();
    }

    @Override
    public <A> void connect(SocketAddress remote, A attachment, 
                            CompletionHandler<Void, ? super A> handler) {
        delegate.connect(remote, attachment, handler);
    }

    /**
     * Connects this channel's socket to a remote address.
     *
     * @param remote the remote address to connect to
     * @return a promise that completes when the connection is established
     */
    @Override
    public Promise<Void> connect(SocketAddress remote) {
        AsyncResult<Void> asyncResult = new AsyncResult<>();
        connect(remote, null, asyncResult.handler);
        return asyncResult;
    }

    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     *
     * @param dst the buffer into which bytes are to be transferred
     * @return a promise that completes with the number of bytes read, or a negative 
     *         value if the connection has been closed by the remote peer
     */
    @Override
    public Promise<Integer> read(ByteBuffer dst) {
        AsyncResult<Integer> asyncResult = new AsyncResult<>();
        read(dst, null, asyncResult.handler);
        return asyncResult;
    }

    @Override
    public <A> void read(ByteBuffer dst, long timeout, TimeUnit unit, A attachment,
                         CompletionHandler<Integer, ? super A> handler) {
        delegate.read(dst, timeout, unit, attachment, handler);
    }

    @Override
    public <A> void read(ByteBuffer[] dsts, int offset, int length, long timeout, TimeUnit unit, 
                         A attachment, CompletionHandler<Long, ? super A> handler) {
        delegate.read(dsts, offset, length, timeout, unit, attachment, handler);
    }

    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     *
     * @param src the buffer from which bytes are to be retrieved
     * @return a promise that completes with the number of bytes written
     */
    @Override
    public Promise<Integer> write(ByteBuffer src) {
        AsyncResult<Integer> asyncResult = new AsyncResult<>();
        write(src, null, asyncResult.handler);
        return asyncResult;
    }

    @Override
    public <A> void write(ByteBuffer src, long timeout, TimeUnit unit, A attachment,
                          CompletionHandler<Integer, ? super A> handler) {
        delegate.write(src, timeout, unit, attachment, handler);
    }

    @Override
    public <A> void write(ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit, 
                          A attachment, CompletionHandler<Long, ? super A> handler) {
        delegate.write(srcs, offset, length, timeout, unit, attachment, handler);
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return delegate.getLocalAddress();
    }

    /**
     * Returns this channel instance cast to its concrete subtype for fluent chaining.
     */
    @SuppressWarnings("unchecked")
    private C self() {
        return (C) this;
    }
}