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

import java.nio.channels.AsynchronousChannel;

/**
 * A marker interface for asynchronous channels in the Tascalate Concurrent library.
 * <p>
 * It extends the standard {@link AsynchronousChannel} to denote that the channel's 
 * operations can be composed using the library's {@link net.tascalate.concurrent.Promise} API,
 * rather than relying solely on callback-based {@link java.nio.channels.CompletionHandler}s.
 * </p>
 *
 * @author vsilaev
 * @see java.nio.channels.AsynchronousChannel
 */
public interface AsyncChannel extends AsynchronousChannel {
}