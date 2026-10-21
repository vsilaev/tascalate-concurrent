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

import java.nio.channels.CompletionHandler;
import net.tascalate.concurrent.CompletablePromise;

/**
 * A utility class that bridges the standard NIO {@link CompletionHandler} with the 
 * Tascalate Concurrent {@link net.tascalate.concurrent.Promise} API.
 * <p>
 * This class extends {@link CompletablePromise} and exposes a public {@link #handler} 
 * field. This handler can be passed directly to standard asynchronous NIO methods 
 * (e.g., {@code AsynchronousFileChannel.read(..., handler)}). When the NIO operation 
 * completes, the handler automatically resolves or rejects the underlying promise, 
 * allowing the caller to compose the result using the fluent {@code Promise} API.
 * </p>
 *
 * @param <V> the type of the result produced by the asynchronous operation
 * @author vsilaev
 * @see CompletablePromise
 * @see java.nio.channels.CompletionHandler
 */
class AsyncResult<V> extends CompletablePromise<V> {

    /**
     * The completion handler that resolves or rejects this promise based on the 
     * outcome of the underlying NIO operation.
     */
    final CompletionHandler<V, Object> handler = new CompletionHandler<V, Object>() {
        @Override
        public void completed(final V result, final Object attachment) {
            success(result);
        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            failure(exc);     
        }
    };

    /**
     * Creates a new, uncompleted {@code AsyncResult}.
     */
    AsyncResult() {
    }
}