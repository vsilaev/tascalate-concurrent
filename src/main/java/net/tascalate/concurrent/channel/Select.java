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

import java.util.Objects;

/**
 * Namespace and factory class for channel {@code select} statements.
 * <p>
 * This class provides the building blocks for defining asynchronous multiplexing operations
 * across multiple channels, inspired by Go's {@code select} statement. It contains the
 * operation types ({@link Op}, {@link Send}, {@link Receive}, {@link Default}, {@link Disabled})
 * and the result type ({@link Result}).
 * <p>
 * On Java 17+ (via Multi-Release JAR), the {@link Op} hierarchy is intended to be replaced by a
 * {@code sealed} interface, enabling exhaustive pattern-matching {@code switch} for downstream callers.
 *
 * @see Channel#select(Op...)
 */
public final class Select {
    
    /**
     * Private constructor to prevent instantiation of this namespace class.
     */
    private Select() {
        
    }
    
    /**
     * Base interface for all operations (cases) that can be evaluated in a {@code select} statement.
     * <p>
     * Implementations include {@link Send}, {@link Receive}, {@link Default}, and {@link Disabled}.
     *
     * @param <T> the type of the value being sent or received
     */
    public static interface Op<T> {}

    /**
     * Represents a send operation case within a {@code select} statement.
     * <p>
     * This case becomes ready when the target channel has capacity to accept the value
     * (either via buffer space or an immediate rendezvous with a receiver).
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

        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
        @Override 
        public int hashCode() {
            return Objects.hash(channel, value);
        }

        /**
         * {@inheritDoc}
         */
        @Override 
        public String toString() {
            return "Send[channel=" + channel + ", value=" + value + "]";
        }
    }

    /**
     * Represents a receive operation case within a {@code select} statement.
     * <p>
     * This case becomes ready when the target channel has a value available to be consumed.
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

        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
        @Override 
        public int hashCode() {
            return Objects.hash(channel);
        }

        /**
         * {@inheritDoc}
         */
        @Override 
        public String toString() {
            return "Receive[channel=" + channel + "]";
        }
    }

    /**
     * Represents the default (fallback) case within a {@code select} statement.
     * <p>
     * If no other case ({@link Send} or {@link Receive}) is immediately ready during the
     * non-blocking phase (Phase 1), the {@code select} statement will immediately resolve
     * with this default case instead of blocking.
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

        /**
         * {@inheritDoc}
         */
        @Override 
        public boolean equals(Object o) {
            return o instanceof Default;
        }

        /**
         * {@inheritDoc}
         */
        @Override 
        public int hashCode() {
            return Default.class.hashCode();
        }

        /**
         * {@inheritDoc}
         */
        @Override 
        public String toString() {
            return "Default[]";
        }
    }
    
    /**
     * Represents a permanently inactive (disabled) case within a {@code select} statement.
     * <p>
     * A disabled case never matches in the non-blocking phase (Phase 1) and never registers
     * a waiter in the blocking phase (Phase 2). It is effectively ignored by the {@code select}
     * execution engine.
     * <p>
     * This is the Java equivalent of Go's nil-channel idiom, allowing cases to be dynamically
     * removed from a {@code select} loop without altering the array structure or shifting indices.
     *
     * @param <T> the type parameter (unused, present for type consistency)
     */
    public static final class Disabled<T> implements Op<T> {

        /**
         * Constructs a new disabled operation.
         */
        public Disabled() {}

        /**
         * {@inheritDoc}
         */
        @Override 
        public boolean equals(Object o) {
            return o instanceof Disabled;
        }

        /**
         * {@inheritDoc}
         */
        @Override 
        public int hashCode() {
            return Disabled.class.hashCode();
        }

        /**
         * {@inheritDoc}
         */
        @Override 
        public String toString() {
            return "Disabled[]";
        }
    }
    
    /**
     * Encapsulates the outcome of a completed {@code select} statement.
     * <p>
     * Contains the original index of the matched case in the provided array, the matched
     * operation object itself, and the value received (or {@code null} for send/default cases).
     *
     * @param <T> the type of the value associated with the result
     */
    public static final class Result<T> {
        private final int index;
        private final Select.Op<T> match;
        private final T value;

        /**
         * Constructs a new select result.
         *
         * @param index the original index of the matched case in the select array
         * @param match the matched operation object
         * @param value the value received (or {@code null} for send/default cases)
         */
        public Result(int index, Select.Op<T> match, T value) {
            this.index = index;
            this.match = match;
            this.value = value;
        }

        /**
         * Returns the original index of the matched case in the array passed to the select statement.
         *
         * @return the original index
         */
        public int index() { 
            return index; 
        }
        
        /**
         * Returns the value associated with the completed operation.
         * <p>
         * For {@link Receive} cases, this is the received value. For {@link Send} and 
         * {@link Default} cases, this is typically {@code null}.
         *
         * @return the value
         */
        public T value() { 
            return value; 
        }
        
        /**
         * Returns the matched operation object.
         *
         * @return the matched operation
         */
        public Select.Op<T> match() { 
            return match; 
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            
            if (!(o instanceof Result)) {
                return false;
            }
            
            Result<?> result = (Result<?>) o;
            return index == result.index &&
                   Objects.equals(match, result.match) &&
                   Objects.equals(value, result.value);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(index, value, match);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "SelectResult{" +
                   "index=" + index + ", " +
                   "match=" + match + ", " +
                   "value=" + value +
                   '}';
        }
    }
    
    // Factory helpers

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