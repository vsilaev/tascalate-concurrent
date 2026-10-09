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

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Try;

/**
 * A channel that is permanently blocked in both directions.
 * Mirrors Go's nil channel: sends and receives block forever.
 * <p>
 * Primary use: pass to {@link SelectCall} as a dynamically disabled case,
 * or hold as a placeholder before a real channel is assigned.
 * <p>
 * This class is a singleton. Use {@link #instance()} to obtain it.
 * <p>
 * Note that unlike a closed channel, a nil channel is not "closed";
 * {@link #isClosed()} returns {@code false} and {@link #close(CloseMode)}
 * is a no-op. Operations simply never complete.
 *
 * @param <T> the nominal element type (never actually produced)
 */
public final class NilChannel<T> implements Channel<T> {

    private static final NilChannel<?> INSTANCE = new NilChannel<>();

    private NilChannel() {}

    /**
     * Returns the shared {@code NilChannel} instance, cast to the requested
     * element type.
     *
     * @param <T> the nominal element type
     * @return the singleton nil channel
     */
    @SuppressWarnings("unchecked")
    public static <T> NilChannel<T> instance() {
        return (NilChannel<T>) INSTANCE;
    }

    /**
     * Never completes. Mirrors sending on a Go nil channel, which blocks
     * forever.
     *
     * @param value             ignored
     * @param selectCoordinator ignored
     * @return a promise that never completes
     */
    @Override
    public Promise<T> send(T value, SelectCoordinator selectCoordinator) {
        return incomplete(); // never completes
    }

    /**
     * Always returns {@code null}, indicating the send could not proceed.
     * A nil channel is never ready to accept a value.
     *
     * @param value ignored
     * @return {@code null}, meaning "not ready"
     */
    @Override
    public Try<T> trySend(T value) {
        return null;
    }

    /**
     * Never completes. Mirrors receiving on a Go nil channel, which blocks
     * forever.
     *
     * @param coordinator ignored
     * @return a promise that never completes
     */
    @Override
    public Promise<T> receive(SelectCoordinator coordinator) {
        return incomplete(); // never completes
    }

    /**
     * Always returns {@code null}, indicating no value is available.
     * A nil channel never has data to deliver.
     *
     * @return {@code null}, meaning "not ready"
     */
    @Override
    public Try<T> tryReceive() {
        return null;
    }

    /**
     * No-op. A nil channel cannot be closed.
     *
     * @param mode ignored
     */
    @Override
    public void close(CloseMode mode) {
    }

    /**
     * Returns {@code false}. A nil channel is not closed; it simply never
     * completes any operation.
     *
     * @return {@code false}
     */
    @Override
    public boolean isClosed() {
        return false;
    }

    /**
     * Returns {@code null}, indicating the channel has never been closed.
     *
     * @return {@code null}
     */
    @Override
    public CloseMode closedMode() {
        return null;
    }

    /**
     * Returns {@code 0}. A nil channel never buffers elements.
     *
     * @return {@code 0}
     */
    @Override
    public int size() {
        return 0;
    }

    /**
     * Returns {@code 0}. A nil channel has no buffer capacity.
     *
     * @return {@code 0}
     */
    @Override
    public int capacity() {
        return 0;
    }
    
    @Override
    public boolean isExhausted() {
        return false;
    }

    /**
     * Creates a promise that never completes. Used by {@link #send} and
     * {@link #receive} to model Go's forever-blocking nil channel.
     *
     * @param <T> the nominal element type
     * @return a promise that can never be completed
     */
    static <T> Promise<T> incomplete() {
        return new ChannelPromise.Incomplete<>();
    }
}