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
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.NetworkChannel;
import java.nio.channels.spi.AsynchronousChannelProvider;
import java.util.Set;
import net.tascalate.concurrent.Promise;

/**
 * A base class for wrapping standard {@link AsynchronousServerSocketChannel} instances 
 * to provide a {@link Promise}-based API.
 * <p>
 * It delegates operations to the underlying channel, notably adapting the {@code accept} 
 * method to return a {@code Promise} of the accepted client channel. This enables fluent 
 * composition of server-side network I/O without deep callback nesting.
 * </p>
 *
 * @param <S> the concrete subtype of this server channel, used for fluent method chaining
 * @param <C> the type of the accepted client channel
 * @author vsilaev
 * @see AsynchronousServerSocketChannel
 * @see Promise
 */
abstract public class AbstractAsyncServerSocketChannel<S extends AbstractAsyncServerSocketChannel<S, C>, C extends AsynchronousSocketChannel>
        implements AsyncChannel, NetworkChannel {

    /** The underlying delegated asynchronous server socket channel. */
    private final AsynchronousServerSocketChannel delegate;

    /**
     * Creates a new wrapper around the given delegate channel.
     *
     * @param delegate the underlying {@link AsynchronousServerSocketChannel} to delegate to
     */
    protected AbstractAsyncServerSocketChannel(AsynchronousServerSocketChannel delegate) {
        this.delegate = delegate;
    }

    /**
     * Returns the provider that created this channel.
     *
     * @return the provider that created this channel
     */
    public AsynchronousChannelProvider provider() {
        return delegate.provider();
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
     * Binds the channel's socket to a local address with a default backlog.
     *
     * @param local the local address to bind the socket, or {@code null} to bind to an automatic local address
     * @return this channel, for fluent chaining
     * @throws IOException if an I/O error occurs
     */
    public S bind(SocketAddress local) throws IOException {
        return bind(local, 0);
    }

    /**
     * Binds the channel's socket to a local address with the specified backlog.
     *
     * @param local the local address to bind the socket, or {@code null} to bind to an automatic local address
     * @param backlog the maximum number of pending connections
     * @return this channel, for fluent chaining
     * @throws IOException if an I/O error occurs
     */
    //@Override
    public S bind(SocketAddress local, int backlog) throws IOException {
        delegate.bind(local, backlog);
        return self();
    }

    @Override
    public <T> S setOption(SocketOption<T> name, T value) throws IOException {
        delegate.setOption(name, value);
        return self();
    }

    /**
     * Accepts a connection made to this channel's socket.
     *
     * @return a promise that completes with the accepted {@link AsynchronousSocketChannel} 
     *         representing the new connection
     */
    public Promise<C> accept() {
        AsyncResult<C> asyncResult = new AsyncResult<>();
        accept(null, asyncResult.handler);
        return asyncResult;
    }

    /**
     * Accepts a connection made to this channel's socket using a callback handler.
     * The handler receives the wrapped client channel type {@code C}.
     */
    public <A> void accept(A attachment, CompletionHandler<? super C, ? super A> handler) {
        if (null == handler) {
            delegate.accept(attachment, null);
            return;
        }
        delegate.accept(attachment, new CompletionHandler<AsynchronousSocketChannel, A>() {
            @Override
            public void completed(AsynchronousSocketChannel result, A attachment) {
                handler.completed(wrap(result), attachment);
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                handler.failed(exc, attachment);
            }
        });
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return delegate.getLocalAddress();
    }

    /**
     * Wraps the raw accepted {@link AsynchronousSocketChannel} into the concrete 
     * client channel type {@code C} expected by this server channel implementation.
     *
     * @param channel the raw accepted channel
     * @return the wrapped client channel
     */
    abstract protected C wrap(AsynchronousSocketChannel channel);

    /**
     * Returns this channel instance cast to its concrete subtype for fluent chaining.
     */
    @SuppressWarnings("unchecked")
    private S self() {
        return (S) this;
    }
}