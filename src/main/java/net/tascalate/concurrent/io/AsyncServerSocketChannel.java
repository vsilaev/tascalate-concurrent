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
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Objects;

import net.tascalate.concurrent.Promise;

/**
 * An asynchronous server socket channel that provides a {@link Promise}-based API.
 * <p>
 * This class is a concrete implementation of {@link AbstractAsyncServerSocketChannel},
 * wrapping a standard JDK {@link AsynchronousServerSocketChannel}. It inherits the
 * promise-returning {@code accept} operation, which yields an {@link AsyncSocketChannel}
 * upon a successful client connection.
 * </p>
 *
 * @author vsilaev
 * @see AbstractAsyncServerSocketChannel
 * @see java.nio.channels.AsynchronousServerSocketChannel
 */
public class AsyncServerSocketChannel extends AbstractAsyncServerSocketChannel<AsyncServerSocketChannel, AsyncSocketChannel> {

    /**
     * Creates a new wrapper around the given delegate channel.
     *
     * @param delegate the underlying {@link AsynchronousServerSocketChannel} to delegate to
     */
    protected AsyncServerSocketChannel(AsynchronousServerSocketChannel delegate) {
        super(delegate);
    }

    /**
     * Opens an asynchronous server socket channel.
     *
     * @param group the channel group to which the newly constructed channel will belong,
     *              or {@code null} to associate the channel with the default channel group
     * @return a new asynchronous server socket channel
     * @throws IOException if an I/O error occurs
     */
    public static AsyncServerSocketChannel open(AsynchronousChannelGroup group) throws IOException {
        Objects.requireNonNull(group, "ChannelGroup should be specified");
        return new AsyncServerSocketChannel(AsynchronousServerSocketChannel.open(group));
    }

    /**
     * Wraps the raw accepted {@link AsynchronousSocketChannel} into an {@link AsyncSocketChannel}.
     *
     * @param channel the raw accepted channel
     * @return the wrapped {@link AsyncSocketChannel}
     */
    @Override
    protected AsyncSocketChannel wrap(AsynchronousSocketChannel channel) {
        return new AsyncSocketChannel(channel);
    }
}