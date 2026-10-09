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

/**
 * Base contract shared by both send and receive channel views.
 * <p>
 * Carries lifecycle, introspection, and close-mode semantics common to
 * every channel, regardless of whether it is used for sending, receiving,
 * or both. Implementations must be safe for concurrent use by multiple
 * threads.
 * <p>
 * The lifecycle of a channel is: OPEN =&gt; CLOSED. The transition is
 * irreversible and idempotent. Once closed, a channel never reopens.
 * The exact effect of closing on pending operations is governed by the
 * {@link CloseMode} used.
 * <p>
 * This interface extends {@link AutoCloseable} so channels can be used
 * in try-with-resources blocks. The no-arg {@link #close()} delegates to
 * {@link CloseMode#FAIL_ALL}, which is the strictest and most predictable
 * shutdown semantic for resource-scoped channels.
 */
public interface ChannelBase extends AutoCloseable {

    /**
     * Determines the behavior of {@link #close(CloseMode)} with respect
     * to pending operations and buffered data.
     */
    enum CloseMode {

        /**
         * Go semantics: waiting senders fail, waiting receivers complete
         * with {@code null}, buffered items can still be drained.
         * <p>
         * This mode is the graceful shutdown option. Receivers are able to
         * consume every element that was already buffered before the close,
         * and only then observe the end-of-stream marker ({@code null}).
         * Senders, however, are failed immediately because accepting new
         * data after a close request would violate the close contract.
         */
        DRAIN,

        /**
         * Java/Reactor semantics: all waiting senders and receivers fail
         * exceptionally, buffer is cleared.
         * <p>
         * This mode is the hard-stop option. Every pending operation,
         * regardless of direction, is failed with
         * {@link IllegalStateException}, and any buffered data is
         * discarded. Use this when the channel is scoped to a resource
         * that must be released promptly (e.g., try-with-resources).
         */
        FAIL_ALL
    }

    /**
     * Closes with {@link CloseMode#FAIL_ALL}.
     * <p>
     * This is the {@link AutoCloseable} contract - try-with-resources
     * stops everything immediately. All pending senders and receivers are
     * failed exceptionally, and the internal buffer is cleared.
     * <p>
     * This method is idempotent: if the channel is already closed, the
     * call has no effect and the original close mode is preserved.
     */
    @Override
    default void close() {
        close(CloseMode.FAIL_ALL);
    }

    /**
     * Closes the channel with the specified mode. Idempotent.
     * <p>
     * If the channel is already closed, this call is a no-op and the
     * original close mode is preserved. The supplied mode must not be
     * {@code null}; implementations throw {@link IllegalArgumentException}
     * for a null mode.
     * <p>
     * After this method returns:
     * <ul>
     *   <li>{@link #isClosed()} returns {@code true}.</li>
     *   <li>{@link #closedMode()} returns the supplied mode.</li>
     *   <li>New send attempts fail exceptionally.</li>
     *   <li>Receive behavior depends on the mode (see {@link CloseMode}).</li>
     * </ul>
     *
     * @param mode the close mode; must not be {@code null}
     * @throws IllegalArgumentException if {@code mode} is {@code null}
     */
    void close(CloseMode mode);

    /**
     * Returns {@code true} if the channel has been closed in any mode.
     *
     * @return {@code true} when closed, {@code false} when still open
     */
    boolean isClosed();

    /**
     * The mode the channel was closed with, or {@code null} if still open.
     *
     * @return the close mode, or {@code null} for an open channel
     */
    CloseMode closedMode();

    /**
     * Number of elements currently buffered. Go's {@code len(ch)}.
     * <p>
     * This is a snapshot value. In a concurrent setting it may be stale
     * by the time the caller acts on it. It is intended primarily for
     * monitoring and diagnostics, not for synchronization decisions.
     *
     * @return the number of buffered elements; 0 if empty or rendezvous
     */
    int size();

    /**
     * Buffer capacity. Go's {@code cap(ch)}. Always 0 for rendezvous channels.
     * <p>
     * Unlike {@link #size()}, capacity is immutable for the lifetime of
     * the channel and may be queried without synchronization.
     *
     * @return the buffer capacity
     */
    int capacity();
}