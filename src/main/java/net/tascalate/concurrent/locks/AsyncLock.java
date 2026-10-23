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

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Promises;

/**
 * The base contract for all asynchronously acquirable locks in this package &mdash;
 * the non-blocking counterpart of the {@code java.util.concurrent.locks} family.
 *
 * <h2>Acquisition never blocks</h2>
 * <ul>
 *   <li>{@link #tryAcquire()} attempts an <b>immediate</b> acquisition and returns an
 *       {@link Token}: populated on success, or {@code null} when the lock is
 *       currently held. It never waits.</li>
 *   <li>{@link #acquire()} returns a {@link Promise} of a {@link Token} that completes
 *       once the lock is exclusively acquired &mdash; immediately if the lock is free,
 *       or later, when preceding holders release it. The calling thread is never
 *       blocked while waiting.</li>
 * </ul>
 *
 * <h2>The token pattern</h2>
 * <p>
 * Instead of paired {@code lock()} / {@code unlock()} calls that must be matched
 * manually, acquisition yields a {@link Token} object that owns the release
 * responsibility. The token <b>must</b> be released via {@link Token#release()}
 * (or {@link Token#close()}) after the protected action completes. Because tokens
 * implement {@link AutoCloseable}, they integrate directly with the library's
 * asynchronous try-with-resources support:
 * </p>
 *
 * <pre>{@code
 * AsyncLock lock = AsyncLock.create();
 *
 * // Explicit token handling
 * Promise<Result> r1 = Promises.tryCompose(
 *     lock.acquire(),          // Promise<Token>
 *     token -> doWorkAsync()   // runs while the lock is held
 * );                           // token.close() releases the lock afterwards
 *
 * // Or, concisely:
 * Promise<Result> r2 = lock.guard(() -> doWorkAsync());
 * }</pre>
 *
 * <p>
 * In both forms the lock is released even if the protected action fails or the
 * resulting promise is cancelled.
 * </p>
 *
 * @param <T> the concrete token type of the lock implementation
 * @see AsyncExclusiveLock
 * @see AsyncSemaphoreLock
 */
public interface AsyncLock<T extends AsyncLock.Token> {

    /**
     * Attempts to immediately acquire the lock, without blocking or waiting.
     *
     * @return an {@link Token} if the lock was acquired;
     *         an {@code null} if the lock is currently held
     */
    T tryAcquire();

    /**
     * Exclusively acquires this lock asynchronously.
     * <p>
     * The returned promise completes when the lock is exclusively acquired by the
     * caller. It may already be complete if the lock was not held. The promised
     * {@link Token} must be released once the protected action completes.
     * </p>
     *
     * @return a {@link Promise} that completes with a {@link Token} when the lock
     *         has been exclusively acquired
     */
    Promise<T> acquire();

    /**
     * A lock token indicating that the associated lock has been exclusively acquired.
     * <p>
     * Once the protected action is completed, the lock must be released by calling
     * {@link #release()} (or {@link #close()}). Implementing {@link AutoCloseable}
     * lets tokens participate in {@link Promises#tryCompose} and related
     * asynchronous try-with-resources combinators.
     * </p>
     */
    interface Token extends AutoCloseable {

        /**
         * Releases the lock, allowing other parties to acquire it.
         */
        void release();

        /**
         * Releases the lock; identical to {@link #release()}.
         *
         * <p>
         * {@inheritDoc}
         * </p>
         */
        @Override
        default void close() {
            release();
        }
    }

    /**
     * Runs the given asynchronous action while holding this lock, releasing the lock
     * automatically when the action's stage completes &mdash; successfully,
     * exceptionally, or via cancellation.
     * <p>
     * Equivalent to {@code Promises.tryCompose(acquire(), $ -> action.get())}.
     * </p>
     *
     * @param <V>    the result type of the action
     * @param action a supplier of the asynchronous action to run under the lock
     * @return a promise that completes with the action's result after the lock has
     *         been released
     */
    default <V> Promise<V> guard(Supplier<? extends CompletionStage<V>> action) {
        return Promises.tryCompose(this.acquire(), $ -> action.get());
    }
}