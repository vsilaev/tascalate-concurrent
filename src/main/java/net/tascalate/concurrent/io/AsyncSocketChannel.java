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
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Objects;

import net.tascalate.concurrent.Promise;

/**
 * An asynchronous socket channel that provides a {@link Promise}-based API.
 * <p>
 * This class is a concrete implementation of {@link AbstractAsyncSocketChannel},
 * wrapping a standard JDK {@link AsynchronousSocketChannel}. It inherits all
 * promise-returning network operations such as {@code connect}, {@code read},
 * and {@code write}.
 * </p>
 *
 * @author vsilaev
 * @see AbstractAsyncSocketChannel
 * @see java.nio.channels.AsynchronousSocketChannel
 */
public class AsyncSocketChannel extends AbstractAsyncSocketChannel<AsyncSocketChannel> {

    /**
     * Creates a new wrapper around the given delegate channel.
     *
     * @param delegate the underlying {@link AsynchronousSocketChannel} to delegate to
     */
    protected AsyncSocketChannel(AsynchronousSocketChannel delegate) {
        super(delegate);
    }

    /**
     * Opens an asynchronous socket channel.
     *
     * @param group the channel group to which the newly constructed channel will belong,
     *              or {@code null} to associate the channel with the default channel group
     * @return a new asynchronous socket channel
     * @throws IOException if an I/O error occurs
     */
    public static AsyncSocketChannel open(AsynchronousChannelGroup group) throws IOException {
        Objects.requireNonNull(group, "ChannelGroup should be specified");
        return new AsyncSocketChannel(AsynchronousSocketChannel.open(group));
    }
}