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

/**
 * A bidirectional asynchronous channel, the Java analogue of Go's {@code chan T}.
 * <p>
 * A channel is a thread-safe, FIFO conduit for values exchanged between concurrent
 * tasks. Producers send values with {@link #send(Object)}; consumers receive them
 * with {@link #receive()}. Both operations are non-blocking at the call site: they
 * return a {@link Promise} that completes when the transfer has been accepted
 * (send) or when a value is available (receive).
 * <p>
 * Two structural variants exist:
 * <ul>
 *   <li><b>Rendezvous channels</b> (capacity 0) -- every send waits for a matching
 *       receive and vice versa. Values are handed over directly with no buffering.
 *       This corresponds to Go's default unbuffered channel.</li>
 *   <li><b>Buffered channels</b> (capacity &gt; 0) -- sends complete immediately while
 *       the buffer has space; receives complete immediately while the buffer holds
 *       data. Blocking occurs only at the buffer boundaries.</li>
 * </ul>
 * <p>
 * The special <b>nil channel</b> obtained via {@link #nil()} never completes any
 * operation. It mirrors Go's {@code nil} channel and is useful as a placeholder
 * for dynamically disabling cases in a select loop (see {@link Select.Disabled}).
 * <p>
 * This interface extends both {@link SelectableSendChannel} and
 * {@link SelectableReceiveChannel}, so a {@code Channel} can be used directly in
 * a {@link #select(Select.Op[]) select} statement or narrowed to a directional
 * view by passing it as a {@link SendChannel} or a {@link ReceiveChannel}.
 * <p>
 * All implementations are safe for concurrent use by multiple threads.
 *
 * @param <T> the element type carried by this channel
 * @see SendChannel
 * @see ReceiveChannel
 * @see SelectableSendChannel
 * @see SelectableReceiveChannel
 * @see Select
 */
public interface Channel<T> extends SelectableSendChannel<T>, SelectableReceiveChannel<T> {

    /**
     * Returns the shared nil channel instance.
     * <p>
     * The nil channel never completes any send or receive operation. Sending to it
     * returns a {@link Promise} that stays pending forever; receiving from it does
     * the same. This mirrors Go's {@code nil} channel semantics and is primarily
     * useful as a placeholder for a dynamically disabled select case.
     * <p>
     * The returned instance is a singleton shared across all callers.
     *
     * @param <T> the nominal element type
     * @return the singleton nil channel
     */
    public static <T> Channel<T> nil() {
        return NilChannel.instance();
    }

    /**
     * Returns the shared nil channel instance, with an explicit element type
     * witness for use in contexts where type inference is ambiguous.
     *
     * @param <T>         the nominal element type
     * @param elementType a class literal used only as a type witness;
     *                    may be {@code null}
     * @return the singleton nil channel
     */
    public static <T> Channel<T> nil(Class<T> elementType) {
        return NilChannel.instance();
    }

    /**
     * Creates a non-fair rendezvous (capacity 0) channel.
     * <p>
     * Every send waits for a matching receive, and every receive waits for a
     * matching send. Values are handed over directly with no buffering. This is
     * the strictest synchronization form and corresponds to Go's default
     * unbuffered channel.
     *
     * @param <T> the element type
     * @return a new rendezvous channel
     */
    public static <T> Channel<T> rendezvous() {
        return rendezvous(false);
    }

    /**
     * Creates a non-fair rendezvous (capacity 0) channel, with an explicit
     * element type witness.
     *
     * @param <T>         the element type
     * @param elementType a class literal used only as a type witness;
     *                    may be {@code null}
     * @return a new rendezvous channel
     */
    public static <T> Channel<T> rendezvous(Class<T> elementType) {
        return rendezvous(false);
    }

    /**
     * Creates a rendezvous (capacity 0) channel with the given fairness policy.
     * <p>
     * A fair channel grants access to waiting senders and receivers in FIFO
     * order, preventing starvation at the cost of throughput. A non-fair
     * channel allows barging and is faster under contention.
     *
     * @param <T>  the element type
     * @param fair {@code true} for FIFO ordering of waiters,
     *             {@code false} to allow barging
     * @return a new rendezvous channel
     */
    public static <T> Channel<T> rendezvous(boolean fair) {
        return new BufferedChannel<>(0, fair);
    }

    /**
     * Creates a rendezvous (capacity 0) channel with the given fairness policy
     * and an explicit element type witness.
     *
     * @param <T>         the element type
     * @param elementType a class literal used only as a type witness;
     *                    may be {@code null}
     * @param fair        {@code true} for FIFO ordering of waiters
     * @return a new rendezvous channel
     */
    public static <T> Channel<T> rendezvous(Class<T> elementType, boolean fair) {
        return new BufferedChannel<>(0, fair);
    }

    /**
     * Creates a non-fair buffered channel with the given capacity.
     *
     * @param <T>      the element type
     * @param capacity the maximum number of buffered elements; must be at
     *                 least 1
     * @return a new buffered channel
     * @throws IllegalArgumentException if {@code capacity} is less than 1
     */
    public static <T> Channel<T> buffered(int capacity) {
        return buffered(capacity, false);
    }

    /**
     * Creates a non-fair buffered channel with the given capacity and an
     * explicit element type witness.
     *
     * @param <T>         the element type
     * @param elementType a class literal used only as a type witness;
     *                    may be {@code null}
     * @param capacity    the maximum number of buffered elements; must be at
     *                    least 1
     * @return a new buffered channel
     * @throws IllegalArgumentException if {@code capacity} is less than 1
     */
    public static <T> Channel<T> buffered(Class<T> elementType, int capacity) {
        return buffered(capacity, false);
    }

    /**
     * Creates a buffered channel with the given capacity and fairness policy.
     * <p>
     * A buffered channel decouples producers from consumers up to the buffer
     * capacity. Sends complete immediately while the buffer has space;
     * receives complete immediately while the buffer holds data.
     * <p>
     * Note that a capacity of 0 is not valid here; use {@link #rendezvous()}
     * for unbuffered channels.
     *
     * @param <T>      the element type
     * @param capacity the maximum number of buffered elements; must be at
     *                 least 1
     * @param fair     {@code true} for FIFO ordering of waiters,
     *                 {@code false} to allow barging
     * @return a new buffered channel
     * @throws IllegalArgumentException if {@code capacity} is less than 1
     */
    public static <T> Channel<T> buffered(int capacity, boolean fair) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be > 0 for buffered channel");
        }
        return new BufferedChannel<>(capacity, fair);
    }

    /**
     * Creates a buffered channel with the given capacity, fairness policy, and
     * an explicit element type witness.
     *
     * @param <T>         the element type
     * @param elementType a class literal used only as a type witness;
     *                    may be {@code null}
     * @param capacity    the maximum number of buffered elements; must be at
     *                    least 1
     * @param fair        {@code true} for FIFO ordering of waiters
     * @return a new buffered channel
     * @throws IllegalArgumentException if {@code capacity} is less than 1
     */
    public static <T> Channel<T> buffered(Class<T> elementType, int capacity, boolean fair) {
        return buffered(capacity, fair);
    }

    /**
     * Executes a select statement over the given cases.
     * <p>
     * This is the Java analogue of Go's {@code select} statement. The
     * evaluation proceeds in two phases:
     * <ol>
     *   <li><b>Phase 1 (non-blocking):</b> all cases are tried in a random
     *       order via {@code trySend} / {@code tryReceive}. If any case is
     *       immediately ready, the returned promise completes with that
     *       case's result. If a {@link Select#otherwise() default} case is
     *       present and no case is ready, the default wins.</li>
     *   <li><b>Phase 2 (blocking):</b> if no case is ready and no default
     *       exists, waiters are registered on every active case. The returned
     *       promise completes when the first case becomes ready, and all
     *       losing cases are cancelled.</li>
     * </ol>
     * <p>
     * Cases are built with the factory methods on {@link Select}:
     * <pre>
     *   Channel.select(
     *       ch1.sending(item),
     *       ch2.receiving(),
     *       Select.otherwise()
     *   );
     * </pre>
     * <p>
     * The select validates its arguments eagerly: duplicate channels, multiple
     * default cases, and unsupported case types are rejected with
     * {@link IllegalArgumentException} before any asynchronous work begins.
     *
     * @param <T>   the select result type
     * @param cases the cases to evaluate; must contain at least one entry and
     *              must not contain duplicate channels or multiple defaults
     * @return a promise that completes with the winning case's result
     * @throws IllegalArgumentException if the case array is null or empty,
     *         contains duplicate channels, or contains multiple default cases
     */
    @SafeVarargs
    public static <T> Promise<Select.Result<T>> select(Select.Op<T>... cases) {
        return SelectCall.select(cases);
    }
}