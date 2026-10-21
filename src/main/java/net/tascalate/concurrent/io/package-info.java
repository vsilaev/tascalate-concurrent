/**
 * Asynchronous I/O utilities and wrappers that bridge standard Java NIO channels
 * with the Tascalate Concurrent {@link net.tascalate.concurrent.Promise} API.
 *
 * <h2>Overview</h2>
 * <p>
 * This package provides promise-based alternatives to the callback-driven
 * {@link java.nio.channels.AsynchronousChannel} hierarchy. Instead of dealing
 * with {@link java.nio.channels.CompletionHandler}s, developers can chain I/O
 * operations fluently using {@code Promise.thenApply}, {@code Promise.thenCompose},
 * and other combinators.
 * </p>
 *
 * <h2>Core Interfaces and Classes</h2>
 * <ul>
 *   <li>{@link net.tascalate.concurrent.io.AsyncChannel}: A marker interface
 *       denoting that an NIO channel has been adapted to return {@code Promise}s.</li>
 *   <li>{@link net.tascalate.concurrent.io.AsyncByteChannel}: Extends
 *       {@link java.nio.channels.AsynchronousByteChannel} with {@code Promise}-returning
 *       {@code read} and {@code write} methods.</li>
 *   <li>{@link net.tascalate.concurrent.io.AsyncFileChannel}: A concrete wrapper
 *       around {@link java.nio.channels.AsynchronousFileChannel} providing
 *       promise-based file I/O and locking.</li>
 *   <li>{@link net.tascalate.concurrent.io.AsyncSocketChannel} and
 *       {@link net.tascalate.concurrent.io.AsyncServerSocketChannel}: Concrete
 *       wrappers for TCP socket channels, enabling fluent, promise-based network
 *       communication.</li>
 * </ul>
 *
 * <h2>Interruptible Blocking I/O (Pre-Loom)</h2>
 * <p>
 * For scenarios where standard blocking I/O must be used (e.g., legacy libraries
 * or pre-Java 21 environments), this package provides the {@link net.tascalate.concurrent.io.BlockingIO}
 * utility class. It leverages an internal {@link java.nio.channels.spi.AbstractSelector}
 * mechanism to make legacy blocking streams and sockets responsive to
 * {@link Thread#interrupt()}.
 * </p>
 * <p>
 * By wrapping functional interfaces (e.g., {@code Supplier}, {@code Callable}) with
 * {@link net.tascalate.concurrent.io.BlockingIO#interruptible(java.util.function.Supplier)},
 * any {@link java.lang.AutoCloseable} resource registered via
 * {@link net.tascalate.concurrent.io.BlockingIO#register(java.lang.AutoCloseable)} will be
 * forcefully closed if the executing thread is interrupted (e.g., via {@code Promise.cancel(true)}).
 * This unblocks the I/O operation and allows the cancellation to propagate.
 * </p>
 * <p>
 * <b>Note for Java 21+:</b> If your application runs on Virtual Threads (Project Loom),
 * the JVM natively handles I/O interruption. In such environments, the {@code BlockingIO}
 * mechanism is largely redundant, though it remains safe to use.
 * </p>
 *
 * @author vsilaev
 * @see net.tascalate.concurrent.Promise
 * @see net.tascalate.concurrent.io.BlockingIO
 */
package net.tascalate.concurrent.io;