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

/**
 * Go-inspired asynchronous channels and CSP (Communicating Sequential Processes)
 * primitives for Java, built on top of the {@code tascalate-concurrent} Promise API.
 *
 * <p>This package provides a comprehensive implementation of Go-style channels,
 * allowing concurrent tasks to communicate and synchronize safely without explicit
 * locking. All channel operations are non-blocking at the call site, returning
 * {@link net.tascalate.concurrent.Promise} instances that complete when the
 * underlying send or receive operation is accepted.
 *
 * <h2>Core Concepts</h2>
 * <ul>
 *   <li><b>{@link net.tascalate.concurrent.channels.Channel}:</b> The primary
 *       bidirectional conduit. Supports both rendezvous (capacity 0) and buffered
 *       modes.</li>
 *   <li><b>Directional Views:</b> {@link net.tascalate.concurrent.channels.SendChannel}
 *       and {@link net.tascalate.concurrent.channels.ReceiveChannel} restrict operations
 *       at compile-time, mirroring Go's {@code chan<- T} and {@code <-chan T}.</li>
 *   <li><b>Multiplexing:</b> The {@link net.tascalate.concurrent.channels.Select} namespace
 *       and {@link net.tascalate.concurrent.channels.Channel#select channels.select(...)}
 *       method provide a Go-like {@code select} statement for waiting on multiple
 *       channel operations simultaneously.</li>
 *   <li><b>Lifecycle:</b> Channels can be closed gracefully
 *       ({@link net.tascalate.concurrent.channels.ChannelBase.CloseMode#DRAIN}) or
 *       forcefully ({@link net.tascalate.concurrent.channels.ChannelBase.CloseMode#FAIL_ALL}).</li>
 *   <li><b>Iteration:</b> The {@link net.tascalate.concurrent.channels.ReceiveChannel#forEach forEach(...)}
 *       and {@link net.tascalate.concurrent.channels.ReceiveChannel#receiveAll receiveAll(...)}
 *       methods provide asynchronous, high-throughput consumption loops with synchronous
 *       fast-paths for buffered data.</li>
 * </ul>
 *
 * <h2>Multi-Release JAR and Modern Java</h2>
 * <p>The base implementation of this package targets <b>Java 8</b>, using standard
 * interfaces and final classes for the {@code Select} operation and result hierarchies.
 * This ensures broad compatibility and allows the library to be used in legacy environments.
 *
 * <p>However, when running on <b>Java 17+</b>, the library leverages a Multi-Release JAR
 * to replace the {@code Select} hierarchy with {@code sealed} interfaces and {@code record}
 * types. This transforms the select operations into a strict Algebraic Data Type (ADT).
 * On <b>Java 21+</b>, this enables exhaustive pattern-matching {@code switch} statements,
 * allowing the compiler to statically verify that all channel outcomes (Send, Receive,
 * Default) are handled without needing a fallback {@code default} branch.
 *
 * <h2>Idiom Mapping</h2>
 * <table border="1" cellpadding="4" cellspacing="0">
 *   <caption>Mapping of Go channel idioms to their Tascalate Concurrent equivalents</caption>
 *   <tr><th>Go Idiom</th><th>Java Equivalent</th></tr>
 *   <tr><td>{@code make(chan T)}</td><td>{@code Channel.rendezvous()}</td></tr>
 *   <tr><td>{@code make(chan T, N)}</td><td>{@code Channel.buffered(N)}</td></tr>
 *   <tr><td>{@code var ch chan T} (nil)</td><td>{@code Channel.nil()}</td></tr>
 *   <tr><td>{@code ch <- v}</td><td>{@code ch.send(v)}</td></tr>
 *   <tr><td>{@code v := <-ch}</td><td>{@code ch.receive()}</td></tr>
 *   <tr><td>{@code v, ok := <-ch}</td><td>{@code ch.tryReceive()}</td></tr>
 *   <tr><td>{@code close(ch)}</td><td>{@code ch.close(CloseMode.DRAIN)}</td></tr>
 *   <tr><td>{@code for v := range ch}</td><td>{@code ch.forEach(v -> ...)}</td></tr>
 *   <tr><td>{@code select { ... }}</td><td>{@code Channel.select(...)}</td></tr>
 * </table>
 *
 * @see net.tascalate.concurrent.channels.Channel
 * @see net.tascalate.concurrent.channels.Select
 * @see net.tascalate.concurrent.channels.ChannelBase.CloseMode
 */
package net.tascalate.concurrent.channels;