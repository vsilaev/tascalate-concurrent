/**
 * Copyright 2015-2020 Valery Silaev (http://vsilaev.com)
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

import java.util.Optional;

import net.tascalate.concurrent.Promise;

public interface AbstractAsyncLock<T extends AbstractAsyncLock.Token> {
    /**
     * Attempts to immediately acquire the lock, returning a populated {@link Optional} if the lock is
     * not currently held.
     *
     * @return An {@link Optional} holding a {@link Token} if the lock is not held; otherwise an
     *         empty Optional
     */
    Optional<T> tryAcquire();
    
    /**
     * Exclusively acquires this lock. The returned stage will complete when the
     * lock is exclusively acquired by this caller. The stage may already be
     * complete if the lock was not held.
     *
     * <p>
     * The {@link Token} held by the returned stage is used to release the
     * lock after it has been acquired and the lock-protected action has
     * completed.
     *
     * @return A {@link Promise} which will complete with a
     *         {@link Token} when the lock has been exclusively acquired
     */
    Promise<T> acquire();

    /**
     * A lock token indicating that the associated lock has been exclusively
     * acquired. Once the protected action is completed, the lock may be
     * released by calling {@link Token#release()}
     */
    interface Token extends AutoCloseable {
        /** Releases this lock, allowing others to acquire it. */
        void release();

        /**
         * Releases this lock, allowing others to acquire it.
         *
         * <p>
         * {@inheritDoc}
         */
        @Override
        default void close() {
            release();
        }
    }
}
