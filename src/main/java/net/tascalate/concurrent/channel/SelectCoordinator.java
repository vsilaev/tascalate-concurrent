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

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Arbitrates which channel wins the right to complete an operation when
 * multiple channels compete within a single {@code select} statement.
 * <p>
 * When a {@code select} registers waiters on several channels, each waiter
 * carries a reference to a shared coordinator. When any channel becomes
 * ready to complete a waiter, it first calls {@link #tryClaim(Channel)} on
 * the coordinator. Only the first channel to claim succeeds; all others
 * are rejected, and the corresponding waiters are cancelled. This ensures
 * that a select statement completes exactly one of its cases, matching
 * Go's semantics.
 * <p>
 * Two factory methods are provided:
 * <ul>
 *   <li>{@link #createFirstWins()} returns a coordinator that allows
 *       exactly one channel to win. Used by {@link SelectCall}.</li>
 *   <li>{@link #anyWins()} returns a coordinator that always grants the
 *       claim. Used for direct, non-select send and receive calls.</li>
 * </ul>
 * <p>
 * The constructor is package-private. Use the factory methods to obtain
 * instances. This class is not intended to be subclassed outside this
 * package.
 *
 * @see SelectCall
 * @see ChannelPromise#tryClaim(Channel)
 */
public abstract class SelectCoordinator {
    
    /**
     * Package-private constructor. Use {@link #createFirstWins()} or
     * {@link #anyWins()} to obtain instances.
     */
    SelectCoordinator() {
    }

    /**
     * Attempts to claim the right to complete an operation on behalf of
     * the given channel.
     * <p>
     * Implementations define the arbitration policy. A successful claim
     * means the given channel is allowed to proceed with completion. A
     * failed claim means another channel has already won, and the caller
     * should abandon the operation and clean up the corresponding waiter.
     *
     * @param candidate the channel requesting permission to complete
     * @return {@code true} if the claim is granted; {@code false} if
     *         another channel has already won
     */
    abstract boolean tryClaim(Channel<?> candidate);

    /**
     * Creates a coordinator that permits exactly one channel to win.
     * <p>
     * The first channel to call {@link #tryClaim(Channel)} wins.
     * Subsequent calls from the same channel succeed (idempotent), while
     * calls from any other channel fail. This is the coordinator used by
     * {@link SelectCall} to ensure a select completes at most one case.
     *
     * @return a new first-wins coordinator
     */
    static SelectCoordinator createFirstWins() {
        return new FirstWins();
    }

    /**
     * Returns a coordinator that always grants the claim.
     * <p>
     * This is used for direct send and receive calls that are not part of
     * a select statement. Since there is no competition between channels,
     * every claim succeeds.
     *
     * @return the shared any-wins coordinator
     */
    static SelectCoordinator anyWins() {
        return ANY_WINS;
    }

    /**
     * Shared singleton for {@link #anyWins()}. Stateless and thread-safe.
     */
    private static SelectCoordinator ANY_WINS = new SelectCoordinator() {
        @Override
        boolean tryClaim(Channel<?> candidate) {
            return true;
        }
    }; 

    /**
     * A coordinator that allows exactly one channel to win.
     * <p>
     * The winning channel is recorded via a lock-free compare-and-set on
     * the {@code winner} field. Once a channel wins, it may call
     * {@link #tryClaim(Channel)} again and still succeed (idempotent).
     * Any other channel is rejected.
     * <p>
     * This idempotency is important because a channel may need to claim
     * the coordinator multiple times during retries or multi-step
     * completion paths within a single select evaluation.
     */
    static final class FirstWins extends SelectCoordinator {
        
        /**
         * Lock-free updater for the {@link #winner} field.
         */
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<FirstWins, Channel> UPDATER = 
                AtomicReferenceFieldUpdater.newUpdater(FirstWins.class, Channel.class, "winner");

        /**
         * The channel that has won this coordinator, or {@code null} if
         * no channel has claimed yet. Written atomically via {@link #UPDATER}.
         */
        private volatile Channel<Object> winner = null;

        /**
         * Attempts to claim this coordinator for the given channel.
         * <p>
         * Returns {@code true} if this channel is the first to claim
         * (the CAS succeeds) or if this channel has already claimed
         * ({@code winner == candidate}). Returns {@code false} if a
         * different channel has already won.
         *
         * @param candidate the channel requesting permission
         * @return {@code true} if the claim is granted
         */
        @Override
        boolean tryClaim(Channel<?> candidate) {
            return UPDATER.compareAndSet(this, null, candidate) || winner == candidate;
        }
    }
}