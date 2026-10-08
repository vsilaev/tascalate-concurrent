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

import java.util.concurrent.CompletableFuture;

import net.tascalate.concurrent.CompletableFutureWrapper;

/**
 * A {@link net.tascalate.concurrent.Promise} implementation used as a waiter
 * in channel queues.
 * <p>
 * Each {@code ChannelPromise} represents a pending send or receive operation
 * that has been enqueued on a channel because it could not complete
 * immediately. The promise carries an optional {@link SelectCoordinator}
 * that governs whether the operation is still allowed to complete.
 * <p>
 * Before a channel completes a queued promise, it must call
 * {@link #tryClaim(Channel)} to verify that the coordinator (if any) has
 * not already been won by another channel participating in the same
 * {@code select} statement. This prevents double-completion when a single
 * select has cases on multiple channels and one of them wins first.
 * <p>
 * This class is package-private. It is an implementation detail of
 * {@link BufferedChannel} and {@link NilChannel} and is not part of the
 * public API.
 *
 * @param <T> the type of the value carried by this promise
 */
class ChannelPromise<T> extends CompletableFutureWrapper<T> {
    
    /**
     * The select coordinator that arbitrates completion of this promise,
     * or {@code null} if this promise belongs to a direct (non-select)
     * send or receive operation.
     */
    private final SelectCoordinator coordinator;

    /**
     * Creates a new channel promise bound to the given coordinator.
     *
     * @param coordinator the select coordinator that arbitrates completion,
     *                    or {@code null} for a direct, non-select operation
     */
    ChannelPromise(SelectCoordinator coordinator) {
        super(new CompletableFuture<>());
        this.coordinator = coordinator;
    }

    /**
     * Attempts to claim this promise for the given channel.
     * <p>
     * If this promise has no coordinator, the claim always succeeds.
     * Otherwise, the coordinator is consulted: the claim succeeds only if
     * the given channel is the first to claim, or if it has already
     * claimed successfully.
     * <p>
     * This method must be called before {@link #success(Object)} or
     * {@link #failure(Throwable)} to ensure that a select statement
     * completes at most one of its cases.
     *
     * @param owner the channel attempting to complete this promise
     * @return {@code true} if the claim succeeded and this promise may be
     *         completed; {@code false} if another channel already won the
     *         coordinator
     */
    boolean tryClaim(Channel<?> owner) {
        return null == coordinator || coordinator.tryClaim(owner);
    }

    /**
     * Completes this promise successfully with the given value.
     * <p>
     * Callers must invoke {@link #tryClaim(Channel)} before this method
     * to ensure the coordinator has granted permission.
     *
     * @param value the value to deliver to the waiting party
     * @return {@code true} if this call completed the promise;
     *         {@code false} if it was already completed
     */
    @Override
    public boolean success(T value) {
        return super.success(value);
    }

    /**
     * Completes this promise exceptionally with the given failure.
     * <p>
     * Callers must invoke {@link #tryClaim(Channel)} before this method
     * to ensure the coordinator has granted permission.
     *
     * @param failure the exception to deliver to the waiting party
     * @return {@code true} if this call completed the promise;
     *         {@code false} if it was already completed
     */
    @Override
    public boolean failure(Throwable failure) {
        return super.failure(failure);
    }

    /**
     * A {@code ChannelPromise} that can never be completed.
     * <p>
     * Both {@link #success(Object)} and {@link #failure(Throwable)} throw
     * {@link UnsupportedOperationException}. This is used by
     * {@link NilChannel} to represent operations that block forever,
     * mirroring Go's nil channel semantics.
     *
     * @param <T> the type of the value (never actually delivered)
     */
    static class Incomplete<T> extends ChannelPromise<T> {
        
        /**
         * Creates a promise that can never be completed. The coordinator
         * is set to {@code null} since no completion is ever attempted.
         */
        public Incomplete() {
            super(null);
        }

        /**
         * Always throws. An incomplete promise cannot succeed.
         *
         * @param value ignored
         * @return never returns normally
         * @throws UnsupportedOperationException always
         */
        @Override
        public boolean success(T value) {
            throw new UnsupportedOperationException();
        }

        /**
         * Always throws. An incomplete promise cannot fail.
         *
         * @param exception ignored
         * @return never returns normally
         * @throws UnsupportedOperationException always
         */
        @Override
        public boolean failure(Throwable exception) {
            throw new UnsupportedOperationException();
        }
    }
}