/**
 * Provides a comprehensive hierarchy of decorators for {@link java.util.concurrent.CompletionStage},
 * {@link java.util.concurrent.Future}, and {@link net.tascalate.concurrent.Promise}, enabling
 * the transparent addition of cross-cutting behaviors without modifying the core interfaces.
 *
 * <h2>Overview</h2>
 * <p>
 * The decorator pattern is central to the extensibility of the Tascalate Concurrent library.
 * By wrapping an existing stage or promise in a decorator, developers can intercept method
 * calls, transform arguments, bind execution to specific executors, or preserve advanced
 * features like dependent cancellation across asynchronous boundaries.
 * </p>
 *
 * <h2>Decorator Hierarchy</h2>
 * <p>
 * The package is organized into a layered hierarchy, from basic {@code CompletionStage}
 * wrapping up to fully-featured {@code Promise} and {@code DependentPromise} decorators:
 * </p>
 * <pre>
 * AbstractCompletionStageDecorator  (Bridges CompletionStage composition)
 *     └── AbstractPromiseLikeDecorator  (Adds Promise return types + α-operator)
 *             └── AbstractFutureDecorator  (Adds Future blocking API)
 *                     ├── CompletableFutureDecorator
 *                     │       └── ExecutorBoundPromise
 *                     └── BlockingCompletionStageDecorator
 *
 * AbstractPromiseDecorator  (Adds Promise lifecycle, timeouts, filters)
 *     ├── ExecutorBoundPromise  (Binds *Async methods to a default executor)
 *     └── ExtendedPromiseDecorator  (Provides wrapArgument extension points)
 *             └── CustomizablePromiseDecorator  (Driven by a PromiseCustomizer)
 *
 * AbstractDependentPromiseDecorator  (Preserves DependentPromise API)
 *     ├── ExecutorBoundDependentPromise
 *     └── ExtendedDependentPromiseDecorator
 *             └── CustomizableDependentPromiseDecorator
 * </pre>
 *
 * <h2>Key Decorator Categories</h2>
 *
 * <h3>1. Executor Binding</h3>
 * <p>
 * {@link ExecutorBoundPromise} and {@link ExecutorBoundDependentPromise} bind all
 * asynchronous composition methods (e.g., {@code thenApplyAsync}) to a specific default
 * {@link java.util.concurrent.Executor}. This eliminates the need to pass the executor
 * explicitly at every step of a long promise chain.
 * </p>
 * <pre>{@code
 * Promise<String> p = somePromise.defaultAsyncOn(myExecutor);
 * p.thenApplyAsync(fn1)       // runs on myExecutor
 *  .thenComposeAsync(fn2)     // runs on myExecutor
 *  .whenCompleteAsync(action); // runs on myExecutor
 * }</pre>
 *
 * <h3>2. Argument Interception &amp; Customization</h3>
 * <p>
 * {@link ExtendedPromiseDecorator} and {@link CustomizablePromiseDecorator} provide
 * extension points ({@code wrapArgument}) to intercept and transform callbacks, stages,
 * and executors before they are passed to the underlying delegate. This is the foundation
 * for advanced features like:
 * </p>
 * <ul>
 *   <li><b>Context Propagation:</b> Using {@link net.tascalate.concurrent.var.ContextRelay}
 *       to capture and restore thread-local or scoped variables across async boundaries.</li>
 *   <li><b>Blocking I/O Interruption:</b> Using {@link net.tascalate.concurrent.io.BlockingIO}
 *       to wrap callbacks so that registered resources are forcefully closed if the
 *       executing thread is interrupted via {@code Promise.cancel(true)}.</li>
 * </ul>
 *
 * <h3>3. Dependent Cancellation</h3>
 * <p>
 * {@link AbstractDependentPromiseDecorator} and its subclasses ensure that the
 * {@link net.tascalate.concurrent.DependentPromise} API (which allows enlisting upstream
 * stages for cancellation) is preserved throughout the decoration chain. When a decorator
 * wraps a dependent promise, calling {@code dependent()} on the decorator returns another
 * dependent decorator, ensuring that cancellation propagation semantics are not lost.
 * </p>
 *
 * <h3>4. Blocking &amp; Future Adaptation</h3>
 * <p>
 * {@link AbstractFutureDecorator}, {@link CompletableFutureDecorator}, and
 * {@link BlockingCompletionStageDecorator} bridge the gap between the non-blocking
 * {@code CompletionStage} API and the blocking {@code Future} API. They allow third-party
 * stages that implement both interfaces to be seamlessly adapted to the {@code Promise}
 * contract, providing blocking {@code get()} methods and state inspection without relying
 * on {@code CountDownLatch}-based fallbacks.
 * </p>
 *
 * <h2>Applying Decorators</h2>
 * <p>
 * Decorators are typically applied using the {@link net.tascalate.concurrent.Promise#as(java.util.function.Function)}
 * method, or via dedicated convenience methods like {@code defaultAsyncOn(Executor)}.
 * </p>
 * <pre>{@code
 * // Using the as() method with a decorator factory
 * Promise<String> decorated = originalPromise.as(p -> new CustomizablePromiseDecorator<>(p, myCustomizer));
 *
 * // Using convenience methods
 * Promise<String> bound = originalPromise.defaultAsyncOn(myExecutor);
 * }</pre>
 * <p>
 * To remove decorators and access the underlying raw promise, use {@link net.tascalate.concurrent.Promise#unwrap()}
 * (removes one layer) or {@link net.tascalate.concurrent.Promise#raw()} (removes all layers).
 * </p>
 *
 * @author vsilaev
 * @see net.tascalate.concurrent.Promise
 * @see net.tascalate.concurrent.DependentPromise
 * @see PromiseCustomizer
 */
package net.tascalate.concurrent.decorators;