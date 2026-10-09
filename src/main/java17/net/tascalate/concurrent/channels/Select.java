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
 * Namespace and factory class for channel {@code select} statements.
 * <p>
 * This class provides the building blocks for defining asynchronous multiplexing operations
 * across multiple channels, inspired by Go's {@code select} statement. 
 * <p>
 * This is the Java 17+ Multi-Release JAR version. It utilizes {@code sealed} interfaces 
 * and {@code record} types to form a strict Algebraic Data Type (ADT), enabling exhaustive 
 * pattern-matching {@code switch} statements for downstream callers (available in Java 21+).
 *
 * @see Channel#select
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
     * This is a {@code sealed} interface, meaning it can only be implemented by the permitted
     * records: {@link Send}, {@link Receive}, {@link Default}, and {@link Disabled}.
     *
     * @param <T> the type of the value being sent or received
     */
    public sealed interface Op<T> permits Send, Receive, Default, Disabled {}

    /**
     * Represents a send operation case within a {@code select} statement.
     * <p>
     * This case becomes ready when the target channel has capacity to accept the value
     * (either via buffer space or an immediate rendezvous with a receiver).
     *
     * @param <T>     the type of the value being sent
     * @param channel the target channel
     * @param value   the value to send
     */
    public record Send<T>(SelectableSendChannel<T> channel, T value) implements Op<T> {}

    /**
     * Represents a receive operation case within a {@code select} statement.
     * <p>
     * This case becomes ready when the target channel has a value available to be consumed.
     *
     * @param <T>     the type of the value being received
     * @param channel the target channel to receive from
     */
    public record Receive<T>(SelectableReceiveChannel<T> channel) implements Op<T> {}

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
    public record Default<T>() implements Op<T> {}
    
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
    public record Disabled<T>() implements Op<T> {}
    
    /**
     * Encapsulates the outcome of a completed {@code select} statement.
     *
     * @param <T>   the type of the value associated with the result
     * @param index the original index of the matched case in the select array
     * @param match the matched operation object
     * @param value the value received (or {@code null} for send/default cases)
     */
    public record Result<T>(int index, Op<T> match, T value) {}
    
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
        return new Send<>(ch, value);
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