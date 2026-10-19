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
package net.tascalate.concurrent;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * An object that may hold resources that must be explicitly released, where the release 
 * operation itself is asynchronous.
 * <p>
 * This interface is the asynchronous counterpart to {@link AutoCloseable}. While 
 * {@code AutoCloseable.close()} blocks the calling thread until the resource is released, 
 * {@code AsyncCloseable.close()} returns a {@link CompletionStage} that completes when the 
 * release operation finishes.
 * </p>
 * <p>
 * This is particularly useful for resources like distributed locks, network sockets, 
 * or asynchronous file handles, where releasing the resource might involve network I/O 
 * or waiting for ongoing operations to drain, and blocking a thread (especially an 
 * event-loop or virtual thread) is undesirable.
 * </p>
 * <p>
 * It is designed to be used with the {@link Promises#tryApplyEx(CompletionStage, Function)} 
 * and {@link Promises#tryComposeEx(CompletionStage, Function)} methods, which emulate the 
 * behavior of a {@code try-with-resources} block for fully asynchronous pipelines.
 * </p>
 *
 * @see AutoCloseable
 * @see Promises#tryApplyEx(CompletionStage, Function)
 * @see Promises#tryComposeEx(CompletionStage, Function)
 */
@FunctionalInterface
public interface AsyncCloseable {
    /**
     * Releases any resources associated with this object asynchronously.
     * <p>
     * The returned {@link CompletionStage} completes normally when all resources have been 
     * successfully released, or completes exceptionally if the resources cannot be released.
     * </p>
     *
     * @return a {@link CompletionStage} representing the asynchronous close operation
     */
    CompletionStage<Void> close();
}