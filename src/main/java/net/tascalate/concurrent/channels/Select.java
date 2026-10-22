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

import java.util.Objects;
import java.util.Optional;

/**
 * Namespace and factory class for channel {@code select} statements.
 * <p>
 * This class provides the building blocks for defining asynchronous multiplexing
 * operations across multiple channels, inspired by Go's {@code select} statement.
 * It contains the operation types ({@link Op}, {@link Send}, {@link Receive},
 * {@link Default}, {@link Disabled}) and the result types ({@link Result},
 * {@link ValueResult}).
 * <p>
 * This is the pre-Java-17 base version. On Java 17+ (via Multi-Release JAR) the
 * {@link Op} and {@link Result} hierarchies are replaced by {@code sealed}
 * interfaces and {@code record} types, enabling exhaustive pattern-matching
 * {@code switch} for downstream callers.
 *
 * @see Channel#select
 */
public final class Select {

    /**
     * Private constructor to prevent instantiation of this namespace class.
     */
    private Select() {
    }

    // ------------------------------------------------------------------
    // Result hierarchy
    // ------------------------------------------------------------------

    /**
     * The outcome of a completed select statement.
     * <p>
     * Every result exposes the original case {@link #index()} and the winning
     * {@link #match()} operation. Subtypes that carry a payload additionally
     * implement {@link ValueResult}.
     *
     * @param <T> the type of the value associated with the result
     */
    public interface Result<T> {

        /**
         * Original index of the winning case in the caller's array.
         *
         * @return the zero-based index of the matched case
         */
        int index();

        /**
         * The operation object that won the select.
         *
         * @return the matched operation
         */
        Op<T> match();
        
        /**
         * Returns the payload of the winning case, if the operation carries one.
         * <p>
         * For payload-bearing operations ({@link Send} and {@link Receive}), this
         * returns a non-null {@link Optional} wrapping the produced value. For
         * operations without a payload ({@link Default}), this returns {@code null}.
         * <p>
         * This method provides a cast-free alternative to downcasting to
         * {@link ValueResult} and calling {@link ValueResult#value()}, which is
         * especially convenient on JDK versions prior to 17 where pattern-matching
         * {@code instanceof} is unavailable.
         *
         * @return a non-null {@link Optional} for payload-bearing results
         *         ({@link Send} and {@link Receive}), or {@code null} for results
         *         without a payload ({@link Default})
         */
        default Optional<T> payload() {
            return null;
        }
    }

    /**
     * A select result that carries a produced value.
     * <p>
     * This intermediate interface distinguishes results that yield a meaningful
     * payload ({@link Send.Result} and {@link Receive.Result}) from results that
     * do not ({@link Default.Result}). Checking {@code instanceof ValueResult}
     * lets callers safely invoke {@link #value()} without first narrowing to a
     * concrete operation type.
     *
     * @param <T> the type of the produced value
     */
    public interface ValueResult<T> extends Result<T> {

        /**
         * The value produced by the winning case. For a {@link Send} case this is
         * the value that was sent; for a {@link Receive} case it is the value that
         * was received.
         *
         * @return the produced value
         */
        T value();
        
        /**
         * Returns the payload of the winning case as a non-null {@link Optional}.
         * <p>
         * The returned {@link Optional} is always non-null. It wraps the value
         * produced by the winning {@link Send} or {@link Receive} case. If the
         * produced value is itself {@code null} (i.e., a {@code null} element was
         * sent or received), the returned {@link Optional} will be
         * {@link Optional#empty()}.
         *
         * @return a non-null {@link Optional} wrapping the produced value
         */
        @Override
        default Optional<T> payload() {
            return Optional.ofNullable(value());
        }
    }

    // ------------------------------------------------------------------
    // Op hierarchy
    // ------------------------------------------------------------------

    /**
     * Base interface for all operations (cases) that can be evaluated in a
     * {@code select} statement.
     * <p>
     * Implementations include {@link Send}, {@link Receive}, {@link Default}, and
     * {@link Disabled}.
     *
     * @param <T> the type of the value being sent or received
     */
    public interface Op<T> {}

    /**
     * Represents a send operation case within a {@code select} statement.
     * <p>
     * This case becomes ready when the target channel has capacity to accept the
     * value (either via buffer space or an immediate rendezvous with a receiver).
     *
     * @param <T> the type of the value being sent
     */
    public static final class Send<T> implements Op<T> {
        private final SelectableSendChannel<T> channel;
        private final T value;

        /**
         * Constructs a new send operation.
         *
         * @param channel the target channel
         * @param value   the value to send
         */
        public Send(SelectableSendChannel<T> channel, T value) {
            this.channel = channel;
            this.value   = value;
        }

        /**
         * Returns the target channel for this send operation.
         *
         * @return the target channel
         */
        public SelectableSendChannel<T> channel() {
            return channel;
        }

        /**
         * Returns the value to be sent.
         *
         * @return the value
         */
        public T value() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Send)) {
                return false;
            }
            Send<?> s = (Send<?>) o;
            return Objects.equals(channel, s.channel) &&
                   Objects.equals(value,   s.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(channel, value);
        }

        @Override
        public String toString() {
            return "Send[channel=" + channel + ", value=" + value + "]";
        }

        /**
         * Result of a winning {@link Send} case. Carries the sent value.
         *
         * @param <T> the type of the sent value
         */
        public static final class Result<T> implements ValueResult<T> {
            private final int index;
            private final T value;
            private final Send<T> match;

            /**
             * Constructs a new send result.
             *
             * @param index the original index of the matched case
             * @param value the value that was sent
             * @param match the winning send operation
             */
            public Result(int index, T value, Send<T> match) {
                this.index = index;
                this.value = value;
                this.match = match;
            }

            @Override public int index() { 
                return index; 
            }
            
            @Override public T value() { 
                return value; 
            }
            
            @Override public Send<T> match() { 
                return match; 
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                
                if (!(o instanceof Result)) {
                    return false;
                }
                
                Result<?> r = (Result<?>) o;
                return index == r.index
                    && Objects.equals(value, r.value)
                    && Objects.equals(match, r.match);
            }

            @Override
            public int hashCode() {
                return Objects.hash(index, value, match);
            }

            @Override
            public String toString() {
                return "Send.Result[index=" + index + ", value=" + value
                    + ", match=" + match + "]";
            }
        }
    }

    /**
     * Represents a receive operation case within a {@code select} statement.
     * <p>
     * This case becomes ready when the target channel has a value available to be
     * consumed.
     *
     * @param <T> the type of the value being received
     */
    public static final class Receive<T> implements Op<T> {
        private final SelectableReceiveChannel<T> channel;

        /**
         * Constructs a new receive operation.
         *
         * @param channel the target channel to receive from
         */
        public Receive(SelectableReceiveChannel<T> channel) {
            this.channel = channel;
        }

        /**
         * Returns the target channel for this receive operation.
         *
         * @return the target channel
         */
        public SelectableReceiveChannel<T> channel() {
            return channel;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            
            if (!(o instanceof Receive)) {
                return false;
            }
            return Objects.equals(channel, ((Receive<?>) o).channel);
        }

        @Override
        public int hashCode() {
            return Objects.hash(channel);
        }

        @Override
        public String toString() {
            return "Receive[channel=" + channel + "]";
        }

        /**
         * Result of a winning {@link Receive} case. Carries the received value.
         *
         * @param <T> the type of the received value
         */
        public static final class Result<T> implements ValueResult<T> {
            private final int index;
            private final T value;
            private final Receive<T> match;

            /**
             * Constructs a new receive result.
             *
             * @param index the original index of the matched case
             * @param value the value that was received
             * @param match the winning receive operation
             */
            public Result(int index, T value, Receive<T> match) {
                this.index = index;
                this.value = value;
                this.match = match;
            }

            @Override public int index() { 
                return index; 
            }
            
            @Override public T value() { 
                return value; 
            }
            
            @Override public Receive<T> match() { 
                return match; 
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                
                if (!(o instanceof Result)) {
                    return false;
                }
                
                Result<?> r = (Result<?>) o;
                return index == r.index
                    && Objects.equals(value, r.value)
                    && Objects.equals(match, r.match);
            }

            @Override
            public int hashCode() {
                return Objects.hash(index, value, match);
            }

            @Override
            public String toString() {
                return "Receive.Result[index=" + index + ", value=" + value
                    + ", match=" + match + "]";
            }
        }
    }

    /**
     * Represents the default (fallback) case within a {@code select} statement.
     * <p>
     * If no other case ({@link Send} or {@link Receive}) is immediately ready during
     * the non-blocking phase (Phase 1), the {@code select} statement resolves with
     * this default case instead of blocking.
     * <p>
     * A {@code select} statement may contain at most one {@code Default} case.
     *
     * @param <T> the type parameter (unused, present for type consistency)
     */
    public static final class Default<T> implements Op<T> {

        /**
         * Constructs a new default operation.
         */
        public Default() {}

        @Override
        public boolean equals(Object o) {
            return o instanceof Default;
        }

        @Override
        public int hashCode() {
            return Default.class.hashCode();
        }

        @Override
        public String toString() {
            return "Default[]";
        }

        /**
         * Result of a winning {@link Default} case.
         * <p>
         * Implements {@link Select.Result} directly rather than {@link ValueResult},
         * because a default case does not produce a meaningful payload. The
         * {@code value} accessor is retained for structural uniformity and always
         * returns {@code null}.
         *
         * @param <T> the nominal value type (always {@code null} here)
         */
        public static final class Result<T> implements Select.Result<T> {
            private final int index;
            private final Default<T> match;

            /**
             * Constructs a new default result.
             *
             * @param index the original index of the matched case
             * @param match the winning default operation
             */
            public Result(int index, Default<T> match) {
                this.index = index;
                this.match = match;
            }

            @Override public int index() { 
                return index; 
            }
            
            @Override public Default<T> match() { 
                return match; 
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                
                if (!(o instanceof Result)) {
                    return false;
                }
                
                Result<?> r = (Result<?>) o;
                return index == r.index && Objects.equals(match, r.match);
            }

            @Override
            public int hashCode() {
                return Objects.hash(index, match);
            }

            @Override
            public String toString() {
                return "Default.Result[index=" + index + ", match=" + match + "]";
            }
        }
    }

    /**
     * Represents a permanently inactive (disabled) case within a {@code select}
     * statement.
     * <p>
     * A disabled case never matches in the non-blocking phase (Phase 1) and never
     * registers a waiter in the blocking phase (Phase 2). It is effectively ignored
     * by the {@code select} execution engine.
     * <p>
     * This is the Java equivalent of Go's nil-channel idiom, allowing cases to be
     * dynamically removed from a {@code select} loop without altering the array
     * structure or shifting indices. Because a disabled case can never win, it has
     * no corresponding {@code Result} type.
     *
     * @param <T> the type parameter (unused, present for type consistency)
     */
    public static final class Disabled<T> implements Op<T> {

        /**
         * Constructs a new disabled operation.
         */
        public Disabled() {}

        @Override
        public boolean equals(Object o) {
            return o instanceof Disabled;
        }

        @Override
        public int hashCode() {
            return Disabled.class.hashCode();
        }

        @Override
        public String toString() {
            return "Disabled[]";
        }
        // Disabled has no result: it can never win a select.
    }

    // ------------------------------------------------------------------
    // Factory helpers
    // ------------------------------------------------------------------

    /**
     * Creates a new {@link Send} operation case.
     *
     * @param <T>   the base type of the channel
     * @param <S>   the specific type of the value being sent (must extend {@code T})
     * @param ch    the channel to send the value to
     * @param value the value to send
     * @return a new {@link Send} instance
     */
    public static <T, S extends T> Send<T> send(SelectableSendChannel<T> ch, S value) {
        return new Send<T>(ch, value);
    }

    /**
     * Creates a new {@link Receive} operation case.
     *
     * @param <T> the type of the value to receive
     * @param ch  the channel to receive the value from
     * @return a new {@link Receive} instance
     */
    public static <T> Receive<T> receive(SelectableReceiveChannel<T> ch) {
        return new Receive<>(ch);
    }

    /**
     * Creates a new {@link Default} (fallback) operation case.
     * <p>
     * Named {@code otherwise} because {@code default} is a reserved keyword in Java.
     *
     * @param <T> the type parameter for consistency
     * @return a new {@link Default} instance
     */
    public static <T> Default<T> otherwise() {
        return new Default<>();
    }

    /**
     * Creates a new {@link Disabled} (inactive) operation case.
     *
     * @param <T> the type parameter for consistency
     * @return a new {@link Disabled} instance
     */
    public static <T> Disabled<T> disabled() {
        return new Disabled<>();
    }
}