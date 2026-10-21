/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.tascalate.concurrent.io;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;
import java.util.function.BiConsumer;
import net.tascalate.concurrent.Promise;

/**
 * An asynchronous byte channel that returns {@link Promise}s instead of relying
 * on {@link CompletionHandler} callbacks.
 * <p>
 * This interface extends the standard {@link AsynchronousByteChannel} and the
 * library's {@link AsyncChannel} marker interface, providing {@code read} and
 * {@code write} operations that resolve to a {@code Promise<Integer>} representing
 * the number of bytes read or written.
 * </p>
 *
 * @author vsilaev
 * @see AsyncChannel
 * @see java.nio.channels.AsynchronousByteChannel
 */
public interface AsyncByteChannel extends AsynchronousByteChannel, AsyncChannel {

    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     *
     * @param dst the buffer into which bytes are to be transferred
     * @return a promise that completes with the number of bytes read, or a negative
     *         value if the channel has reached end-of-stream
     */
    @Override
    Promise<Integer> read(ByteBuffer dst);

    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     *
     * @param src the buffer from which bytes are to be retrieved
     * @return a promise that completes with the number of bytes written
     */
    @Override
    Promise<Integer> write(ByteBuffer src);

    /**
     * Creates a {@link CompletionHandler} that delegates completion or failure
     * to the provided {@link BiConsumer}.
     * <p>
     * This is a convenience method for adapting legacy callback-based APIs to
     * functional consumers.
     * </p>
     *
     * @param <V> the type of the result
     * @param handler the consumer to invoke upon completion or failure
     * @return a {@link CompletionHandler} wrapping the consumer
     */
    static <V> CompletionHandler<V, Object> handler(BiConsumer<? super V, ? super Throwable> handler) {
        return new CompletionHandler<V, Object>() {
            @Override
            public void completed(V result, Object attachment) {
                handler.accept(result, null);
            }
            @Override
            public void failed(Throwable ex, Object attachment) {
                handler.accept(null, ex);
            }
        };
    }
}