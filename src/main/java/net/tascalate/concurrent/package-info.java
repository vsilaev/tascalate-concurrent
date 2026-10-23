/**
 * Core package of the Tascalate Concurrent library, providing advanced asynchronous 
 * programming constructs that extend and enhance the standard Java 
 * {@link java.util.concurrent.CompletionStage} and {@link java.util.concurrent.Future} APIs.
 *
 * <h2>Overview</h2>
 * <p>
 * The standard Java concurrency API separates the non-blocking composition capabilities 
 * of {@link java.util.concurrent.CompletionStage} from the blocking access and cancellation 
 * semantics of {@link java.util.concurrent.Future}. This package bridges that gap by 
 * introducing the {@link Promise} interface, which combines both contracts into a single, 
 * fluent, and highly capable API.
 * </p>
 * <p>
 * Designed for robust, real-world applications, this library provides first-class support 
 * for true task cancellation (including thread interruption for blocking I/O), executor 
 * binding and propagation, advanced retry mechanisms, and stack-safe asynchronous loops.
 * </p>
 *
 * <h2>JDK Compatibility</h2>
 * <p>
 * The library targets <b>JDK 1.8</b> as its baseline. However, it proactively backports 
 * many useful features introduced in Java 9, 12, and beyond (such as 
 * {@link Promise#orTimeout}, {@link Promise#exceptionallyCompose}, 
 * {@link Promise#resultNow}, and {@link Promise#exceptionNow}). 
 * Furthermore, the library is fully compatible with modern Java runtimes, including 
 * JDK 21+ Virtual Threads, ensuring seamless integration with Project Loom without 
 * requiring code changes.
 * </p>
 *
 * <h2>Core Interfaces</h2>
 * <ul>
 *   <li>{@link Promise}: The central interface of the library, merging 
 *       {@link java.util.concurrent.CompletionStage} and {@link java.util.concurrent.Future}. 
 *       All composition methods are overridden to return a {@code Promise}, enabling 
 *       seamless, type-safe chaining.</li>
 *   <li>{@link DependentPromise}: A specialized {@code Promise} that tracks its upstream 
 *       "origins". When a dependent promise is cancelled, it can propagate that 
 *       cancellation to the stages it depends on, preventing resource leaks in long 
 *       pipelines.</li>
 *   <li>{@link AsyncCloseable}: An extension of {@link java.lang.AutoCloseable} for 
 *       resources that require asynchronous cleanup, returning a {@code Promise} or 
 *       {@code CompletionStage} upon closure.</li>
 * </ul>
 *
 * <h2>Core Implementations &amp; Adapters</h2>
 * <ul>
 *   <li>{@link CompletableTask}: The primary entry point for executing long-running, 
 *       blocking, or I/O-bound tasks. It wraps a {@link java.util.concurrent.RunnableFuture} 
 *       to ensure that {@link Promise#cancel(boolean)} can genuinely interrupt the 
 *       underlying thread.</li>
 *   <li>{@link CompletablePromise}: A manually completable promise, useful for bridging 
 *       legacy callback-based APIs into the {@code Promise} ecosystem.</li>
 *   <li>{@link CompletableFutureWrapper}: A lightweight adapter that wraps an existing 
 *       {@link java.util.concurrent.CompletableFuture} to expose the {@code Promise} API 
 *       while fixing standard JDK shortcomings (e.g., proper cancellation propagation in 
 *       {@code thenCompose}).</li>
 *   <li>{@link CompletionStageWrapper}: A generic adapter for arbitrary 
 *       {@link java.util.concurrent.CompletionStage} implementations that do not natively 
 *       implement {@link java.util.concurrent.Future}, providing blocking access via an 
 *       internal {@link java.util.concurrent.CountDownLatch}.</li>
 *   <li>{@link ConfigurableDependentPromise}: The concrete implementation of 
 *       {@link DependentPromise}, managing the enlistment and cancellation of upstream 
 *       stages.</li>
 *   <li>{@link RunnablePromise}: A specialized promise tailored for {@code Runnable} 
 *       executions, yielding {@code Promise<Void>}.</li>
 * </ul>
 *
 * <h2>Utilities &amp; Combinators</h2>
 * <ul>
 *   <li>{@link Promises}: The central factory and utility class. It provides methods to 
 *       create resolved promises ({@link Promises#success}, {@link Promises#failure}, 
 *       {@link Promises#nothing}, {@link Promises#canceled}, {@link Promises#maybe}), 
 *       adapt external stages ({@link Promises#from}), and combine multiple stages 
 *       ({@link Promises#all}, {@link Promises#any}, {@link Promises#atLeast}). It also 
 *       includes advanced control flow like {@link Promises#tryCompose} (asynchronous 
 *       try-with-resources) and {@link Promises#chunked} (bounded-concurrency collection).</li>
 *   <li>{@link PromiseOperations}: A collection of static utility methods designed to be 
 *       used as extension methods (e.g., via Lombok's {@code @ExtensionMethod}). It 
 *       provides a more fluent, natural syntax for applying decorators like context 
 *       propagation, interruptible I/O, and asynchronous resource management.</li>
 *   <li>{@link AsyncCompletions}: Helper methods for completing stages and handling 
 *       exceptional outcomes.</li>
 *   <li>{@link Try}: A monadic wrapper representing either a successful computation 
 *       ({@link Try.Success}) or a failure ({@link Try.Failure}), useful for functional 
 *       error handling without throwing exceptions.</li>
 *   <li>{@link MultitargetException}: An exception type that aggregates multiple failures, 
 *       typically used by combinator methods like {@link Promises#all} when multiple 
 *       constituent stages fail.</li>
 * </ul>
 *
 * <h2>Execution &amp; Thread Management</h2>
 * <ul>
 *   <li>{@link TaskExecutorService}: An extension of {@link java.util.concurrent.ExecutorService} 
 *       where all submission methods return {@link Promise} instead of 
 *       {@link java.util.concurrent.Future}, enabling fluent composition of submitted tasks.</li>
 *   <li>{@link ThreadPoolTaskExecutor}: A concrete implementation of 
 *       {@link TaskExecutorService} backed by a standard thread pool, with enhanced 
 *       configuration options.</li>
 *   <li>{@link TaskExecutors}: A factory class for creating various 
 *       {@link TaskExecutorService} instances, including fixed, cached, and virtual-thread 
 *       backed executors (on JDK 21+).</li>
 *   <li>{@link TaskExecutorCompletionService} &amp; {@link TaskCompletionService}: 
 *       Variants of the standard {@link java.util.concurrent.ExecutorCompletionService} 
 *       that work with {@link Promise} and {@link TaskExecutorService}.</li>
 *   <li>{@link ThreadFactoryBuilder}, {@link AbstractThreadFactoryBuilder}, &amp; 
 *       {@link ThreadGroupBuilder}: Fluent builders for creating customized 
 *       {@link java.util.concurrent.ThreadFactory} instances with controlled naming, 
 *       daemon status, priority, and thread group assignment.</li>
 *   <li>{@link RejectedExecutionHandler}: Custom strategies for handling task rejection 
 *       in thread pools, extending the standard JDK capabilities.</li>
 * </ul>
 *
 * <h2>Resilience &amp; Control Flow</h2>
 * <ul>
 *   <li>{@link RetryPolicy}: Defines the rules for asynchronous retry loops, including 
 *       maximum attempts, backoff delays (via {@link DelayPolicy}), per-attempt timeouts, 
 *       and result validation.</li>
 *   <li>{@link RetryContext}: Provides contextual information to a retry loop, including 
 *       the current attempt number, the duration of the previous attempt, and the last 
 *       result or exception.</li>
 *   <li>{@link RetryCallable} &amp; {@link RetryRunnable}: Functional interfaces for 
 *       operations that can be retried, optionally receiving the {@link RetryContext} 
 *       to adapt their behavior per attempt.</li>
 *   <li>{@link RetryException}: The exception thrown when a retry loop exhausts its 
 *       configured attempts or encounters a non-retryable error, containing the history 
 *       of failures.</li>
 *   <li>{@link AsyncLoop}: Provides stack-safe, cancellable asynchronous iteration 
 *       (e.g., {@code while} loops), preventing {@link StackOverflowError} when 
 *       chaining many asynchronous steps.</li>
 *   <li>{@link DelayPolicy}: The core interface for calculating backoff delays between 
 *       retry attempts, with built-in implementations for fixed, exponential, and 
 *       randomized jitter strategies (located in the {@code net.tascalate.concurrent.delays} 
 *       subpackage).</li>
 * </ul>
 *
 * <h2>Related Subpackages</h2>
 * <ul>
 *   <li>{@code net.tascalate.concurrent.decorators}: The decorator hierarchy used to 
 *       add cross-cutting concerns (like executor binding or context propagation) to 
 *       promises without altering the core implementations.</li>
 *   <li>{@code net.tascalate.concurrent.io}: Utilities for making legacy, blocking 
 *       I/O operations (like {@link java.io.InputStream} or {@link java.net.Socket}) 
 *       responsive to {@link Promise} cancellation via thread interruption.</li>
 *   <li>{@code net.tascalate.concurrent.var}: Portable context variable propagation 
 *       ({@link net.tascalate.concurrent.var.ContextVar} and 
 *       {@link net.tascalate.concurrent.var.ContextRelay}) that works seamlessly with 
 *       {@link java.lang.ThreadLocal} on JDK 8–24 and {@code java.lang.ScopedValue} 
 *       on JDK 25+.</li>
 *   <li>{@code net.tascalate.concurrent.delays}: Concrete implementations of 
 *       {@link DelayPolicy} for retry backoff strategies.</li>
 * </ul>
 *
 * @author Valery Silaev
 * @see <a href="https://github.com/vsilaev/tascalate-concurrent">Tascalate Concurrent on GitHub</a>
 */
package net.tascalate.concurrent;