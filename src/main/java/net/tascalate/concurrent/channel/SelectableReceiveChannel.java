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
 * A {@link ReceiveChannel} that can participate in a {@code select} statement.
 * <p>
 * This interface extends the plain receive contract with two capabilities
 * required by select multiplexing:
 * <ol>
 *   <li>A coordinator-aware {@link #receive(SelectCoordinator)} overload
 *       that lets the select engine arbitrate which of several competing
 *       cases actually consumes a value.</li>
 *   <li>A fluent {@link #receiving()} factory that produces a
 *       {@link Select.Receive} case object bound to this channel.</li>
 * </ol>
 * <p>
 * The coordinator-aware overload is not intended for direct use by
 * application code. It is invoked by the select engine during Phase 2
 * registration. Application code should call {@link #receive()} or build
 * a select case via {@link #receiving()}.
 *
 * @param <T> the element type produced by this channel
 */
public interface SelectableReceiveChannel<T> extends ReceiveChannel<T> {

    /**
     * Receives a value using the {@link SelectCoordinator#anyWins()} coordinator.
     * <p>
     * This default implementation is equivalent to calling
     * {@link #receive(SelectCoordinator)} with a coordinator that always
     * grants permission. It exists so that callers holding only a
     * {@link SelectableReceiveChannel} reference can perform ordinary
     * receives without manually supplying a coordinator.
     *
     * @return a promise that completes with the received value, or with
     *         {@code null} on end-of-stream
     */
    default Promise<T> receive() {
        return receive(SelectCoordinator.anyWins());
    }

    /**
     * Receives a value under the control of the given {@link SelectCoordinator}.
     * <p>
     * The coordinator arbitrates between multiple cases of the same select
     * statement. Before consuming a buffered value or completing a
     * rendezvous handoff, the implementation consults
     * {@link SelectCoordinator#tryClaim(Channel)}; if another case has
     * already won, this receive returns a cancelled promise instead of
     * consuming the value, preventing double-consumption across cases.
     * <p>
     * When the channel is empty and no sender is waiting, a receiver
     * promise is enqueued and returned. The coordinator is attached to
     * that promise so a later sender can verify the case is still active
     * before completing it.
     *
     * @param coordinator the select coordinator; {@code null} is treated
     *                    as {@link SelectCoordinator#anyWins()}
     * @return a promise that completes with the received value, or a
     *         cancelled promise if the coordinator was already won
     */
    Promise<T> receive(SelectCoordinator coordinator);

    /**
     * Creates a {@link Select.Receive} case bound to this channel.
     * <p>
     * This is the fluent alternative to calling
     * {@code Select.receive(channel)} directly:
     * <pre>
     *   Channel.select(
     *       ch1.sending(item),
     *       ch2.receiving()
     *   );
     * </pre>
     *
     * @return a new receive case object
     */
    default Select.Receive<T> receiving() {
        return Select.receive(this);
    }
}