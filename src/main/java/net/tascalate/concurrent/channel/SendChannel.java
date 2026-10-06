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
package net.tascalate.concurrent.channel;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Try;

/**
 * Write-only view of a channel.
 * Pass this to producers to prevent them from accidentally receiving.
 */
public interface SendChannel<T> extends ChannelBase {

    /**
     * Sends a value. Returns a completed future if accepted or buffered,
     * a pending future if the channel is full, or a failed future if closed.
     */
    Promise<T> send(T value);

    /**
     * Non-blocking send. Returns {@code true} if the value was buffered
     * or handed to a waiting receiver, {@code false} if full or closed.
     */
    Try<T> trySend(T value);
}
