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

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Promises;
import net.tascalate.concurrent.Try;

/**
 * Read-only view of a channel.
 * Pass this to consumers to prevent them from accidentally sending.
 * <p>
 * This is the Java analogue of Go's directional channel type
 * {@code <-chan T}: holders of this reference can only receive, never
 * send. Use {@link Channel} when both directions are needed, or
 * {@link SendChannel} for the write-only view.
 * <p>
 * All receive operations are asynchronous and return a {@link Promise}.
 * The non-blocking variant {@link #tryReceive()} returns immediately
 * without registering a waiter, making it suitable for Phase 1 (polling)
 * evaluation of a select statement.
 *
 * @param <T> the type of the elements this channel produces
 * @see SendChannel
 * @see Channel
 */
public interface ReceiveChannel<T> extends ChannelBase {

    /**
     * Receives a value. Returns a completed future with the value if available,
     * a pending future if the channel is empty, or a failed future if closed
     * in {@link CloseMode#FAIL_ALL}.
     * <p>
     * In {@link CloseMode#DRAIN} mode, returns {@code null} once fully drained.
     *
     * @return a promise that completes with the received value, or with
     *         {@code null} on end-of-stream, or fails if the channel is
     *         closed in {@code FAIL_ALL} mode
     */
    Promise<T> receive();

    /**
     * Receives a value, timing out if none arrives within the given number
     * of milliseconds.
     * <p>
     * This is a convenience overload of {@link #receive(Duration)}.
     *
     * @param millis the maximum time to wait, in milliseconds
     * @return a promise that completes with the received value, or fails
     *         with a timeout exception if the deadline elapses
     * @see #receive(Duration)
     */
    default Promise<T> receive(long millis) {
        return receive(Duration.ofMillis(millis));
    }

    /**
     * Receives a value, timing out if none arrives within the given duration.
     * If the timeout elapses, the returned Promise fails with a TimeoutException,
     * and the underlying receive operation is automatically cancelled, removing
     * the waiter from the channel's internal queue.
     *
     * @param timeout the maximum time to wait; must not be {@code null}
     * @return a promise that completes with the received value, or fails
     *         with a timeout exception if the deadline elapses
     */
    default Promise<T> receive(Duration timeout) {
        return receive().orTimeout(timeout, true);
    }

    /**
     * Non-blocking receive. Returns immediately without registering a waiter.
     * <p>
     * The returned {@link Try} is one of:
     * <ul>
     *   <li>a successful {@code Try} holding the received value,</li>
     *   <li>a failed {@code Try} if the channel is closed in
     *       {@link CloseMode#FAIL_ALL}, or</li>
     *   <li>{@code null} if the channel is empty and no value is available.</li>
     * </ul>
     *
     * @return a {@code Try} with the value, a failure, or {@code null}
     *         when no value is ready
     */
    Try<T> tryReceive();

    /**
     * Asynchronously consumes all elements from the channel until it is closed and drained.
     * Mirrors Go's {@code for v := range ch { action(v) }}.
     *
     * @param action the callback invoked for each received element; must
     *               not be {@code null}
     * @return a promise that completes when the channel is fully drained
     */
    default Promise<Void> forEach(Consumer<? super T> action) {
        return forEach(action, v -> true);
    }

    /**
     * Asynchronously consumes elements until the channel is closed, drained,
     * or the condition returns {@code false}.
     * <p>
     * The condition is evaluated <i>before</i> the action. If it returns
     * {@code false}, the loop terminates immediately and the action is NOT
     * executed for that specific element (similar to {@code Stream.takeWhile}).
     * <p>
     * Mirrors Go's:
     * <pre>{@code
     * for v := range ch {
     *     if !condition(v) { break }
     *     action(v)
     * }
     * }</pre>
     *
     * @param action            the callback invoked for each received element;
     *                          must not be {@code null}
     * @param continueCondition the predicate tested before each action;
     *                          must not be {@code null}
     * @return a promise that completes when the loop terminates
     */
    default Promise<Void> forEach(Consumer<? super T> action, Predicate<? super T> continueCondition) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(continueCondition, "continueCondition");

        return Promises.loop(
            Boolean.TRUE,
            continueLoop -> continueLoop,
            continueLoop -> receive().dependent().thenApply(value -> {
                if (value == null && isClosed()) {
                    return Boolean.FALSE; // EOF
                }
                if (!continueCondition.test(value)) {
                    return Boolean.FALSE; // Break condition met
                }
                action.accept(value);
                return Boolean.TRUE; // Continue loop
            }, true)
        ).dependent()
         .thenAccept(b -> {}, true)
         .unwrap();
    }
}