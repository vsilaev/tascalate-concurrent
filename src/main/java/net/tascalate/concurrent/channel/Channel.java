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

/**
 * Base contract shared by both send and receive channel views.
 * Carries lifecycle, introspection, and close-mode semantics.
 */
public interface Channel extends AutoCloseable {

    /**
     * Determines the behavior of {@link #close(CloseMode)}.
     */
    enum CloseMode {
        /**
         * Go semantics: waiting senders fail, waiting receivers complete
         * with {@code null}, buffered items can still be drained.
         */
        DRAIN,

        /**
         * Java/Reactor semantics: all waiting senders and receivers fail
         * exceptionally, buffer is cleared.
         */
        FAIL_ALL
    }

    /**
     * Closes with {@link CloseMode#FAIL_ALL}.
     * This is the {@link AutoCloseable} contract — try-with-resources
     * stops everything immediately.
     */
    @Override
    default void close() {
        close(CloseMode.FAIL_ALL);
    }

    /** Closes with the specified mode. Idempotent. */
    void close(CloseMode mode);

    /** {@code true} if the channel has been closed in any mode. */
    boolean isClosed();

    /** The mode the channel was closed with, or {@code null} if still open. */
    CloseMode closedMode();

    /** Number of elements currently buffered. Go's {@code len(ch)}. */
    int size();

    /** Buffer capacity. Go's {@code cap(ch)}. Always 0 for rendezvous channels. */
    int capacity();
}