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
     * Returns {@code true} when the channel has reached a terminal state
     * and no further values can be received.
     * <p>
     * A channel is exhausted when:
     * <ul>
     *   <li>it is closed in {@link CloseMode#FAIL_ALL}, or</li>
     *   <li>it is closed in {@link CloseMode#DRAIN} and the buffer is empty.</li>
     * </ul>
     * <p>
     * This is the authoritative end-of-stream check. Unlike inspecting the
     * value returned by {@code receive()}, it cannot be confused with a
     * legitimate {@code null} element.
     *
     * @return {@code true} if no more values will ever be produced
     */
    boolean isExhausted();

    /**
     * Asynchronously consumes all elements from the channel until it is closed and drained.
     * Mirrors Go's {@code for v := range ch { action(v) }}.
     * <p>
     * Equivalent to calling {@link #forEach(Consumer, Predicate, long)} with a condition that
     * always returns {@code true} and a batch size of {@code 0} (greedy synchronous drain).
     *
     * @param action the callback invoked for each received element; must not be {@code null}
     * @return a promise that completes when the channel is fully drained
     */
    default Promise<Void> forEach(Consumer<? super T> action) {
        return forEach(action, v -> true, 0L);
    }
    
    /**
     * Asynchronously consumes all elements from the channel until it is closed and drained,
     * processing elements in synchronous batches to maximize throughput.
     * <p>
     * Equivalent to calling {@link #forEach(Consumer, Predicate, long)} with a condition that
     * always returns {@code true}.
     *
     * @param action    the callback invoked for each received element; must not be {@code null}
     * @param batchSize the maximum number of elements to process synchronously in a single batch
     *                  before yielding control back to the event loop. If {@code <= 0}, drains all 
     *                  available elements synchronously until a wait is required.
     * @return a promise that completes when the channel is fully drained
     */
    default Promise<Void> forEach(Consumer<? super T> action, long batchSize) {
        return forEach(action, v -> true, batchSize);
    }

    /**
     * Asynchronously consumes elements until the channel is closed, drained,
     * or the condition returns {@code false}.
     * <p>
     * Equivalent to calling {@link #forEach(Consumer, Predicate, long)} with a batch size of 
     * {@code 0} (greedy synchronous drain).
     *
     * @param action            the callback invoked for each received element; must not be {@code null}
     * @param continueCondition the predicate tested before each action; must not be {@code null}
     * @return a promise that completes when the loop terminates
     */
    default Promise<Void> forEach(Consumer<? super T> action, Predicate<? super T> continueCondition) {
        return forEach(action, continueCondition, 0L);
    }
    
    /**
     * Asynchronously consumes elements until the channel is closed, drained,
     * or the condition returns {@code false}, processing elements in synchronous batches.
     * <p>
     * The condition is evaluated <i>before</i> the action. If it returns
     * {@code false}, the loop terminates immediately and the action is NOT
     * executed for that specific element (similar to {@code Stream.takeWhile}).
     * <p>
     * To maximize throughput for buffered channels, this method employs a synchronous 
     * fast-path (even on initial invocation, before returning). It will greedily process 
     * elements synchronously as long as they are immediately available in the buffer. 
     * <ul>
     *   <li>If {@code batchSize <= 0}, it drains <i>all</i> currently buffered elements 
     *       synchronously, only yielding control back to the event loop when the channel 
     *       is empty and an asynchronous wait is required.</li>
     *   <li>If {@code batchSize > 0}, it yields control back to the event loop after 
     *       processing the specified number of elements, preventing carrier-thread 
     *       starvation in highly contested environments.</li>
     * </ul>
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
     * @param batchSize         the maximum number of elements to process synchronously
     *                          before yielding. If {@code <= 0}, drains all available 
     *                          elements synchronously until a wait is required.
     * @return a promise that completes when the loop terminates
     */
    default Promise<Void> forEach(Consumer<? super T> action, Predicate<? super T> continueCondition, long batchSize) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(continueCondition, "continueCondition");

        // Micro-optimizing Promises.loop as well as controlling batchSzie
        // Otherwise Promises.loop may itself run completed promises without async
        return Promises.loop(
            Boolean.TRUE,
            continueLoop -> continueLoop,
            continueLoop -> {
                // Greedy sync drain: 
                // If batchSize <= 0, loop until we hit a pending Promise (empty buffer).
                // If batchSize > 0, loop up to batchSize, then yield to prevent starvation.
                for (long processed = 0; batchSize <= 0 || processed < batchSize; processed++) {

                    Promise<T> next = receive();
                    
                    // 1. ASYNC PATH: Promise is not yet complete (buffer is empty)
                    if (!next.isDone()) {
                        // Yield to the asynchronous event loop. When the promise completes,
                        // evaluate the EOF and break conditions, then signal Promises.loop.
                        return next.dependent().thenApply(value -> {
                            if (value == null && isExhausted()) {
                                return Boolean.FALSE; // EOF
                            }
                            if (!continueCondition.test(value)) {
                                return Boolean.FALSE; // Break condition met
                            }
                            action.accept(value);
                            return Boolean.TRUE; // Continue ASYNC loop
                        }, true);
                    } else {
                        // 2. SYNC FAST-PATH: Promise is already complete (data is in buffer)
                    
                        // Handle FAIL_ALL close or other exceptional completions
                        if (next.isCompletedExceptionally()) {
                            // Returning the failed promise aborts Promises.loop exceptionally.
                            // We use thenApply to satisfy the Promise<Boolean> return type without
                            // actually executing the lambda, as the promise is already failed.
                            return next.thenApply($ -> Boolean.FALSE); 
                            // not dependent while settled already 
                        }
                    
                        // Safe to extract value synchronously (won't block because isDone() is true)
                        T value = next.join(); 
                        
                        if (null == value && isExhausted()) {
                            return ChannelPromise.ASYNC_FALSE; // Channel is over
                        } else if (!continueCondition.test(value)) {
                            return ChannelPromise.ASYNC_FALSE; // Break condition met
                        } else {
                            action.accept(value);
                            // Loop continues synchronously to grab the next buffered item!
                        }
                    }
               }
               
               // Batch limit reached. Yield control back to Promises.loop to prevent 
               // carrier-thread starvation, then resume processing the next batch.
               return ChannelPromise.ASYNC_TRUE; 
            }
        ).dependent()
         .thenAccept(b -> {}, true)
         .unwrap();
    }
}