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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

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
 * <li>{@link #createFirstWins()} returns a coordinator that allows
 *   exactly one channel to win. Used by {@link SelectCall}.</li>
 * <li>{@link #anyWins()} returns a coordinator that always grants the
 *   claim. Used for direct, non-select send and receive calls.</li>
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
     *
     * @param candidate the channel requesting permission to complete
     * @return {@code true} if the claim is granted; {@code false} if
     *         another channel has already won
     */
    abstract boolean tryClaim(Channel<?> candidate);

    /**
     * Creates a coordinator that permits exactly one channel to win.
     *
     * @return a new first-wins coordinator
     */
    static SelectCoordinator createFirstWins() {
        return new FirstWins();
    }

    /**
     * Returns a coordinator that always grants the claim.
     *
     * @return the shared any-wins coordinator
     */
    static SelectCoordinator anyWins() {
        return ANY_WINS;
    }

    /** Shared singleton for {@link #anyWins()}. Stateless and thread-safe. */
    private static SelectCoordinator ANY_WINS = new SelectCoordinator() {
        @Override
        boolean tryClaim(Channel<?> candidate) {
            return true;
        }
    };

    /**
     * A coordinator that allows exactly one channel to win.
     */
    static final class FirstWins extends SelectCoordinator {
        
        /** VarHandle for lock-free CAS on the {@link #winner} field. */
        private static final VarHandle WINNER;
        
        static {
            try {
                WINNER = MethodHandles.lookup()
                    .findVarHandle(FirstWins.class, "winner", Channel.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        /**
         * The channel that has won this coordinator, or {@code null} if
         * no channel has claimed yet.
         */
        private volatile Channel<Object> winner = null;

        @Override
        boolean tryClaim(Channel<?> candidate) {
            return WINNER.compareAndSet(this, null, candidate) || winner == candidate;
        }
    }
}