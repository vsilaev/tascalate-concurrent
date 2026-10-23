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
package net.tascalate.concurrent.locks;

import net.tascalate.concurrent.Promise;

/**
 * An asynchronously acquirable <b>mutual exclusion</b> lock &mdash; the non-blocking
 * analogue of {@link java.util.concurrent.locks.Lock}.
 *
 * <p>
 * At most one party holds the lock at any moment. Instead of blocking in
 * {@code lock()}, callers receive a {@link Promise} of a {@link Token}:
 * </p>
 *
 * <pre>{@code
 * AsyncLock lock = AsyncExclusiveLock.create();
 *
 * Promises.tryCompose(lock.acquire(), t -> updateSharedStateAsync());
 * 
 *
 * // or simply:
 * lock.guard(() -> updateSharedStateAsync());
 * }</pre>
 *
 * <p>
 * Waiting acquirers are served in the order they called {@link #acquire()}; the
 * returned promises are completed on the thread that releases the lock. Use
 * {@link #tryAcquire()} for a non-blocking acquisition attempt.
 * </p>
 *
 * @see AsyncLock
 * @see AsyncSemaphoreLock
 */
public interface AsyncExclusiveLock extends AsyncLock<AsyncExclusiveLock.Token> {

    /**
     * A token indicating that this lock has been exclusively acquired. Release the
     * lock by calling {@link #release()} (or {@link #close()}) once the protected
     * action completes.
     */
    interface Token extends AsyncLock.Token {}

    /**
     * Creates a new asynchronous mutual exclusion lock.
     *
     * @return a new free (unheld) {@code AsyncLock}
     */
    static AsyncExclusiveLock create() {
        return new DefaultAsyncExclusiveLock();
    }
}