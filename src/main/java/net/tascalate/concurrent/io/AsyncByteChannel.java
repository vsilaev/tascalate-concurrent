/**
 * Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;
import java.util.function.BiConsumer;

import net.tascalate.concurrent.Promise;

public interface AsyncByteChannel extends AsynchronousByteChannel, AsyncChannel {
    @Override
    Promise<Integer> read(ByteBuffer dst);
    @Override
    Promise<Integer> write(ByteBuffer src);
    
    public static <V> CompletionHandler<V, Object> handler(BiConsumer<? super V, ? super Throwable > handler) {
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
