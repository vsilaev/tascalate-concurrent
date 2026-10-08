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
 * A {@link SendChannel} that can participate in a {@code select} statement.
 * <p>
 * This interface extends the plain send contract with two capabilities
 * required by select multiplexing:
 * <ol>
 *   <li>A coordinator-aware {@link #send(Object, SelectCoordinator)}
 *       overload that lets the select engine arbitrate which of several
 *       competing cases actually delivers its value.</li>
 *   <li>A fluent {@link #sending(Object)} factory that produces a
 *       {@link Select.Send} case object bound to this channel.</li>
 * </ol>
 * <p>
 * The coordinator-aware overload is not intended for direct use by
 * application code. It is invoked by the select engine during Phase 2
 * registration. Application code should call {@link #send(Object)} or
 * build a select case via {@link #sending(Object)}.
 *
 * @param <T> the element type accepted by this channel
 */
public interface SelectableSendChannel<T> extends SendChannel<T> {

    /**
     * Sends a value using the {@link SelectCoordinator#anyWins()} coordinator.
     * <p>
     * This default implementation is equivalent to calling
     * {@link #send(Object, SelectCoordinator)} with a coordinator that
     * always grants permission. It exists so that callers holding only a
     * {@link SelectableSendChannel} reference can perform ordinary sends
     * without manually supplying a coordinator.
     *
     * @param value the value to send
     * @return a promise that completes when the value is accepted
     */
    default Promise<T> send(T value) {
        return send(value, SelectCoordinator.anyWins());
    }

    /**
     * Sends a value under the control of the given {@link SelectCoordinator}.
     * <p>
     * The coordinator arbitrates between multiple cases of the same select
     * statement. Before completing a handoff or buffering the value, the
     * implementation consults
     * {@link SelectCoordinator#tryClaim(Channel)}; if another case has
     * already won, this send returns a cancelled promise instead of
     * delivering the value, preventing double-delivery across cases.
     * <p>
     * When the channel is full and no receiver is waiting, a
     * {@code WaitingSender} is enqueued and its future returned. The
     * coordinator is attached to that future so a later receiver can
     * verify the case is still active before completing it.
     *
     * @param value       the value to send
     * @param coordinator the select coordinator; {@code null} is treated
     *                    as {@link SelectCoordinator#anyWins()}
     * @return a promise that completes when the value is accepted, or a
     *         cancelled promise if the coordinator was already won
     */
    Promise<T> send(T value, SelectCoordinator coordinator);

    /**
     * Creates a {@link Select.Send} case bound to this channel and the
     * given value.
     * <p>
     * This is the fluent alternative to calling
     * {@code Select.send(channel, value)} directly:
     * <pre>
     *   Channel.select(
     *       ch1.sending(item),
     *       ch2.receiving()
     *   );
     * </pre>
     *
     * @param <S>   the concrete value type, must extend {@code T}
     * @param value the value to send when this case is selected
     * @return a new send case object
     */
    default <S extends T> Select.Send<T> sending(S value) {
        return Select.send(this, value);
    }
}