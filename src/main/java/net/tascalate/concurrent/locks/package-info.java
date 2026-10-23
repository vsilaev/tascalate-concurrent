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
/**
 * Asynchronous, non-blocking analogs of the standard {@code java.util.concurrent}
 * synchronizers &mdash; locks, semaphores, and countdown latches &mdash; built on the
 * {@link net.tascalate.concurrent.Promise} API.
 *
 * <h2>Core principle: acquisition never blocks</h2>
 * <p>
 * Every acquisition in this package returns immediately with either a result or a
 * promise of a result; no calling thread is ever parked waiting for a lock:
 * </p>
 * <ul>
 *   <li>{@code tryAcquire()} performs an immediate attempt and returns a
 *       token, or {@code null} when the lock is currently held;</li>
 *   <li>{@code acquire()} returns a {@link net.tascalate.concurrent.Promise} that
 *       completes when the lock or permits are granted &mdash; immediately if available,
 *       or later, when preceding holders release them.</li>
 * </ul>
 *
 * <h2>The token pattern</h2>
 * <p>
 * Instead of paired {@code lock()} / {@code unlock()} calls that must be matched
 * manually, a successful acquisition yields a <em>token</em>
 * ({@link net.tascalate.concurrent.locks.AsyncLock.Token}) that owns the
 * release responsibility. Tokens are {@link AutoCloseable}, so they compose with
 * the library's asynchronous try-with-resources combinators
 * ({@link net.tascalate.concurrent.Promises#tryCompose}) and with the
 * {@link net.tascalate.concurrent.locks.AsyncLock#guard guard(...)} shortcut,
 * which guarantees release even when the protected action fails or is cancelled:
 * </p>
 *
 * <pre>{@code
 * AsyncLock lock = AsyncExclusiveLock.create();
 *
 * // Concise form: acquire, run, release automatically
 * Promise<Result> r = lock.guard(() -> criticalAsyncWork());
 *
 * // Explicit form: full control over the token
 * Promises.tryCompose(lock.acquire(), t -> criticalAsyncWork());
 * }</pre>
 *
 * <h2>Provided synchronizers</h2>
 * <table>
 * <caption>Synchronizers and their JDK analogs</caption>
 * <tr><th>This package</th><th>JDK analog</th><th>Acquisition yields</th></tr>
 * <tr>
 *   <td>{@link net.tascalate.concurrent.locks.AsyncExclusiveLock}</td>
 *   <td>{@link java.util.concurrent.locks.Lock}</td>
 *   <td>{@code Promise<Token>} &mdash; exclusive ownership</td>
 * </tr>
 * <tr>
 *   <td>{@link net.tascalate.concurrent.locks.AsyncSemaphoreLock}</td>
 *   <td>{@link java.util.concurrent.Semaphore}</td>
 *   <td>{@code Promise<Token>} carrying the held permits</td>
 * </tr>
 * <tr>
 *   <td>{@link net.tascalate.concurrent.locks.AsyncSemaphore}</td>
 *   <td>{@link java.util.concurrent.Semaphore}</td>
 *   <td>{@code Promise<Long>} &mdash; the granted permit count</td>
 * </tr>
 * <tr>
 *   <td>{@link net.tascalate.concurrent.locks.AsyncCountDownLatch}</td>
 *   <td>{@link java.util.concurrent.CountDownLatch}</td>
 *   <td>the latch <em>is itself</em> a {@code Promise<Long>}</td>
 * </tr>
 * </table>
 *
 * <h2>Two semaphore styles</h2>
 * <ul>
 *   <li><b>Token-based ({@link net.tascalate.concurrent.locks.AsyncSemaphoreLock}):</b>
 *       each acquisition yields a self-releasing token that knows how many permits it
 *       holds ({@code Token.permits()}); releasing the token returns exactly those
 *       permits. Prefer this style when permits guard an asynchronous action &mdash;
 *       the token integrates directly with {@code guard(...)} and
 *       {@link net.tascalate.concurrent.Promises#tryCompose}.</li>
 *   <li><b>Permit-count-based ({@link net.tascalate.concurrent.locks.AsyncSemaphore}):</b>
 *       mirrors the JDK {@code Semaphore} API &mdash; {@code acquire(permits)} resolves
 *       to the granted count and the caller releases explicitly via
 *       {@code release(permits)}. Use it when permits are managed manually or released
 *       from a different scope than the acquisition.</li>
 * </ul>
 *
 * <pre>{@code
 * // Token-based: at most 10 concurrent users of the resource
 * AsyncSemaphoreLock sem = AsyncSemaphoreLock.create(10, true);
 * Promise<Data> data = sem.guard(() -> queryResourceAsync());
 *
 * // Permit-count-based: manual acquire/release
 * AsyncSemaphore permits = AsyncSemaphore.create(10, true);
 * permits.acquire(3).thenCompose(granted ->
 *     doWorkAsync().whenComplete((r, e) -> permits.release(granted))
 * );
 * }</pre>
 *
 * <h2>The asynchronous latch</h2>
 * <p>
 * {@link net.tascalate.concurrent.locks.AsyncCountDownLatch} replaces the blocking
 * {@code await()} of the JDK latch with subscription: the latch itself is a
 * {@link net.tascalate.concurrent.Promise} that completes with the initial count when
 * the count reaches zero. Like the JDK latch, it is one-shot and cannot be reset.
 * </p>
 *
 * <pre>{@code
 * AsyncCountDownLatch latch = new AsyncCountDownLatch(3);
 * latch.thenRun(() -> startProcessing()); // fires when the count reaches zero
 * ...
 * latch.countDown();                      // from any thread, as work completes
 * }</pre>
 *
 * <h2>Fairness and cancellation</h2>
 * <p>
 * Semaphores support an optional <em>fair</em> mode (factory flag): in fair mode
 * permits are granted strictly in arrival order and barging is prevented; in unfair
 * mode new requests may acquire permits ahead of queued waiters.
 * {@link net.tascalate.concurrent.locks.AsyncExclusiveLock} always serves waiters in FIFO order.
 * </p>
 * <p>
 * Pending acquisitions are fully cancellable: cancelling an acquisition promise removes
 * the waiter from the queue, and any permits tentatively reserved for it are returned
 * to the pool.
 * </p>
 *
 * <h2>Implementation notes</h2>
 * <p>
 * All implementations are lock-free: permit accounting uses compare-and-set operations,
 * waiter queues are based on {@link java.util.concurrent.ConcurrentLinkedQueue}, and
 * release operations are guarded by idempotent release-once flags, making double-release
 * a no-op. Default implementations are provided by {@code DefaultAsyncExclusiveLock},
 * {@code DefaultAsyncSemaphoreLock} and {@code DefaultAsyncSemaphore} (the latter two
 * sharing the {@code AsyncSemaphoreBase} permit/waiter machinery); obtain instances via
 * the static {@code create(...)} factories on the corresponding interfaces.
 * </p>
 *
 * @author vsilaev
 * @see net.tascalate.concurrent.locks.AsyncLock
 * @see net.tascalate.concurrent.locks.AsyncExclusiveLock
 * @see net.tascalate.concurrent.locks.AsyncSemaphoreLock
 * @see net.tascalate.concurrent.locks.AsyncSemaphore
 * @see net.tascalate.concurrent.locks.AsyncCountDownLatch
 */
package net.tascalate.concurrent.locks;