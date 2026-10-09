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
package net.tascalate.concurrent.channels;

import java.time.Duration;
import java.util.Iterator;
import java.util.Objects;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Promises;
import net.tascalate.concurrent.Try;

/**
 * Write-only view of a channel.
 * <p>
 * Pass this to producers to prevent them from accidentally receiving.
 * This is the Java analogue of Go's directional channel type
 * {@code chan<- T}: the compiler enforces that holders of this reference
 * can only send, never receive.
 * <p>
 * Sending is asynchronous. Every send returns a {@link Promise} that
 * completes when the value has either been handed to a waiting receiver
 * or placed into the buffer. On a rendezvous channel (capacity 0) the
 * promise remains pending until a receiver arrives.
 * <p>
 * A failed send (e.g., to a closed channel) is reported through the
 * returned promise rather than thrown synchronously, keeping the API
 * uniformly asynchronous.
 *
 * @param <T> the element type accepted by this channel
 */
public interface SendChannel<T> extends ChannelBase {

    /**
     * Sends a value. Returns a completed future if accepted or buffered,
     * a pending future if the channel is full, or a failed future if closed.
     * <p>
     * The returned promise completes with the sent value on success, which
     * allows fluent chaining and makes it easy to confirm which value was
     * delivered. The promise never completes with {@code null} for a
     * successful send of a non-null value.
     * <p>
     * If the channel is closed before the send can be accepted, the
     * promise fails with {@link IllegalStateException}.
     *
     * @param value the value to send; may be {@code null} if the channel
     *              supports null elements
     * @return a promise that completes when the value is accepted
     */
    Promise<T> send(T value);

    /**
     * Sends a value with a timeout expressed in milliseconds.
     * <p>
     * If the value cannot be accepted within the given time, the returned
     * promise fails with a timeout exception. This is a convenience
     * overload delegating to {@link #send(Object, Duration)}.
     *
     * @param value  the value to send
     * @param millis the maximum time to wait, in milliseconds
     * @return a promise that completes on acceptance or fails on timeout
     * @see #send(Object, Duration)
     */
    default Promise<T> send(T value, long millis) {
        return send(value, Duration.ofMillis(millis));
    }

    /**
     * Sends a value with a timeout expressed as a {@link Duration}.
     * <p>
     * If the value cannot be accepted within the given time, the returned
     * promise fails with a timeout exception. The underlying send attempt
     * is cancelled when the timeout fires, so no value is delivered after
     * the deadline.
     * <p>
     * This is the preferred overload for expressing deadlines in a
     * unit-agnostic way.
     *
     * @param value   the value to send
     * @param timeout the maximum time to wait; must not be {@code null}
     * @return a promise that completes on acceptance or fails on timeout
     */
    default Promise<T> send(T value, Duration timeout) {
        return send(value).orTimeout(timeout, true);
    }

    /**
     * Non-blocking send. Returns a successful {@link Try} if the value was
     * buffered or handed to a waiting receiver, {@code null} if the channel
     * is full, or a failed {@link Try} if closed.
     * <p>
     * This is the Java analogue of Go's two-value comma-ok send used
     * inside a {@code select} with a {@code default} branch. It never
     * blocks and never returns a pending future, making it suitable for
     * Phase 1 (non-blocking) evaluation of a select statement.
     * <p>
     * Return value semantics:
     * <ul>
     *   <li>Success: the value was delivered or buffered.</li>
     *   <li>{@code null}: the channel is full; a later send may succeed.</li>
     *   <li>Failure: the channel is closed; no future send will succeed.</li>
     * </ul>
     *
     * @param value the value to send
     * @return a successful {@link Try}, a failed {@link Try}, or
     *         {@code null} when the channel is full
     */
    Try<T> trySend(T value);
    
    
    /**
     * Asynchronously sends all elements from the given iterable to the channel.
     * <p>
     * Equivalent to calling {@link #sendAll(Iterable, long)} with a batch size of {@code 0}
     * (greedy synchronous send).
     *
     * @param items the elements to send; must not be {@code null}
     * @return a promise that completes with the total number of successfully sent elements 
     *         when all elements have been sent, or fails exceptionally if the channel 
     *         is closed or an error occurs
     */
    default Promise<Integer> sendAll(Iterable<T> items) {
        return sendAll(items, 0L);
    }
    
    /**
     * Asynchronously sends all elements from the given iterable to the channel,
     * processing elements in synchronous batches to maximize throughput.
     * <p>
     * To maximize throughput for buffered channels, this method employs a synchronous 
     * fast-path. It will greedily send elements synchronously as long as the channel 
     * accepts them immediately. 
     * <ul>
     *   <li>If {@code batchSize <= 0}, it sends <i>all</i> elements synchronously until 
     *       the channel's buffer is full (or a rendezvous wait is required), at which point 
     *       it yields control back to the event loop.</li>
     *   <li>If {@code batchSize > 0}, it yields control back to the event loop after 
     *       sending the specified number of elements, preventing carrier-thread 
     *       starvation in highly contested environments.</li>
     * </ul>
     * <p>
     * If the channel is closed or an error occurs during the operation, the returned 
     * promise fails exceptionally with the underlying cause. The exact number of items 
     * successfully sent prior to the failure is not exposed via the failed promise.
     *
     * @param items     the elements to send; must not be {@code null}
     * @param batchSize the maximum number of elements to send synchronously before yielding.
     *                  If {@code <= 0}, sends all available elements synchronously until a wait is required.
     * @return a promise that completes with the total number of successfully sent elements, 
     *         or fails exceptionally if the channel is closed or an error occurs
     */
    default Promise<Integer> sendAll(Iterable<T> items, long batchSize) {
        Objects.requireNonNull(items, "items");
        
        // Array is used instead of AtomicInteger to avoid object allocation overhead on every increment.
        // This is thread-safe because Promises.loop guarantees sequential execution of the step function.
        int[] sent = {0};
        
        return Promises.loop(
            items.iterator(),
            Iterator::hasNext,
            iterator -> {
                // Greedy sync send: 
                // If batchSize <= 0, loop until we hit a pending Promise (buffer full / rendezvous wait) or run out of items.
                // If batchSize > 0, loop up to batchSize, then yield to prevent starvation.
                for (long processed = 0; (batchSize <= 0 || processed < batchSize) && iterator.hasNext(); processed++) {

                    T item = iterator.next();
                    Promise<T> next = send(item);
                    
                    // 1. ASYNC PATH: Promise is not yet complete (buffer is full or waiting for receiver)
                    if (!next.isDone()) {
                        // Yield to the asynchronous event loop. When the promise completes,
                        // increment the counter and signal Promises.loop to continue with the iterator.
                        // If the send failed (e.g., channel closed), thenApply is skipped and 
                        // the failure propagates automatically, aborting the loop.
                        return next.dependent().thenApply(value -> {
                            sent[0]++;
                            return iterator;
                        }, true);
                    } else {
                        // 2. SYNC FAST-PATH: Promise is already complete (buffered or handed off)
                    
                        // Handle closed channel or other exceptional completions
                        if (next.isCompletedExceptionally()) {
                            // Returning the failed promise aborts Promises.loop exceptionally.
                            // We use thenApply to satisfy the CompletionStage<Iterator> return type 
                            // without actually executing the lambda, as the promise is already failed.
                            return next.thenApply($ -> iterator); 
                        }
                        
                        // Successfully sent synchronously. Increment counter and loop to grab the next item!
                        sent[0]++;
                    }
               }
               
               // Batch limit reached or iterator exhausted. 
               // Yield control back to Promises.loop to prevent carrier-thread starvation, 
               // then resume processing the next batch (or terminate if !hasNext).
               return Promises.success(iterator); 
            }
        ).dependent()
         .thenApply(b -> sent[0], true)
         .unwrap();
    }
}