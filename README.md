[![Maven Central](https://img.shields.io/maven-central/v/net.tascalate/net.tascalate.concurrent.svg)](https://search.maven.org/artifact/net.tascalate/net.tascalate.concurrent/0.9.13/jar) [![GitHub release](https://img.shields.io/github/release/vsilaev/tascalate-concurrent.svg)](https://github.com/vsilaev/tascalate-concurrent/releases/tag/0.9.13) [![license](https://img.shields.io/github/license/vsilaev/tascalate-concurrent.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)
# tascalate-concurrent
The library provides an implementation of the [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html) interface and related classes these are designed to support long-running blocking tasks (typically, I/O bound). This functionality augments the sole Java 8 built-in implementation, [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html), that is primarily supports computational tasks. Also, the library helps with numerous asynchronous programing challenges like handling timeouts, retry/poll functionality, orchestrating results of multiple concurrent computations and similar.

Since the version [0.7.0](https://github.com/vsilaev/tascalate-concurrent/releases/tag/0.7.0) the library is shipped as a multi-release JAR and may be used both with Java 8 as a classpath library or with Java 9+ as a module.

**IMPORTANT!**

In the version [0.8.0](https://github.com/vsilaev/tascalate-concurrent/releases/tag/0.8.0) the artifact was renamed:
New name:
```xml
<dependency>
    <groupId>net.tascalate</groupId>
    <artifactId>net.tascalate.concurrent</artifactId>
    <version>0.9.13</version> <!-- Any version above 0.8.0, the latest one is recommended -->
</dependency>
```
Old Name
```xml
<dependency>
    <groupId>net.tascalate.concurrent</groupId>
    <artifactId>net.tascalate.concurrent.lib</artifactId>
    <version>0.7.1</version>
</dependency>
```

# Why `CompletableFuture` is Not Enough?

There are several shortcomings in the [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) implementation that complicate its use in real-world asynchronous programming, especially when dealing with I/O-bound, interruptible tasks:

1.  **Lack of true interruptibility**: The `CompletableFuture.cancel()`  [method](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html#cancel-boolean-) does not interrupt the underlying thread; it merely transitions the future to an exceptionally completed state. Consequently, even if you use blocking calls inside functions passed to `thenApplyAsync`, `thenAcceptAsync`, etc., these functions will run to completion and cannot be interrupted. For more details, see [CompletableFuture can't be interrupted](http://www.nurkiewicz.com/2015/03/completablefuture-cant-be-interrupted.html) by Tomasz Nurkiewicz.
2.  **Unconfigurable default executor**: By default, all `*Async` composition methods of `CompletableFuture` use the `ForkJoinPool.commonPool()` ([see here](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html#commonPool--)) unless an explicit [Executor](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Executor.html) is specified. This thread pool is shared among all [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) instances and parallel streams across all applications deployed on the same JVM. This hard-coded, unconfigurable thread pool is completely outside the application developer's control, making it difficult to monitor and scale. Therefore, in robust real-life applications, you should always specify your own `Executor`. While Java 9+ introduced API enhancements to mitigate this, addressing it still requires custom coding.
3.  **Inconvenient composition API**: The built-in Java concurrency API provides a rather limited way to combine multiple [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html)s. The `CompletableFuture.allOf` and `CompletableFuture.anyOf` methods accept only [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) arguments, meaning there is no built-in mechanism to combine arbitrary [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html)s without first converting them to [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html). Furthermore, `CompletableFuture.allOf` returns a `CompletableFuture<Void>`, making it inconvenient to extract the individual results of each supplied future. `CompletableFuture.anyOf` is even more restrictive in this regard; for more details, please read [CompletableFuture in Action](http://www.nurkiewicz.com/2013/05/java-8-completablefuture-in-action.html) (see "Shortcomings") by Tomasz Nurkiewicz.
4.  **Late addition of timeouts and delays**: Support for timeouts and delays was introduced to [CompletableFuture](https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/CompletableFuture.html) only in Java 9, leaving widely used Java 8 applications without this crucial functionality. Furthermore, certain design decisions, such as relying on delayed executors instead of a dedicated `delay` operator, are somewhat questionable.

While numerous open-source libraries address some of these shortcomings, none provide a fully interruptible `CompletionStage` implementation, and none solve _all_ of these issues coherently.

# How to use?
To use a library you have to add a single Maven dependency
```xml
<dependency>
    <groupId>net.tascalate</groupId>
    <artifactId>net.tascalate.concurrent</artifactId>
    <version>0.9.13</version>
</dependency>
```

# 1. The `Promise` Interface

This is the core interface of the Tascalate Concurrent library. Its design philosophy can be best described by the following formula:

```java
Promise == CompletionStage + Future
```
It seamlessly merges the blocking API of `java.util.concurrent.Future` (including the crucial `cancel(boolean mayInterruptIfRunning)` method) **and** the rich composition capabilities of `java.util.concurrent.CompletionStage`. Importantly, all composition methods inherited from `CompletionStage` (such as `thenApply`, `thenCombine`, `whenComplete`, etc.) are re-declared in this interface to return a `Promise`, allowing for fluent, uninterrupted chaining.

## Forward-Compatible with Java 9+ and Beyond

Although the library is fully compatible with JDK 1.8, the `Promise` interface has been meticulously designed to be forward-compatible. **All enhancements introduced to `CompletionStage` and `Future` in Java 9+ (and even Java 12+) have been backported to the `Promise` API.** This ensures you get modern, expressive concurrency primitives regardless of your baseline JDK version.

Key backported methods include:
```java
// Java 9+ Future/CompletionStage enhancements
boolean isCompletedExceptionally();
T resultNow() throws IllegalStateException;
T exceptionNow() throws IllegalStateException;
T getNow(Supplier<? extends T> valueIfAbsent) throws CancellationException, CompletionException;
T join() throws CancellationException, CompletionException;

// Java 12+ CompletionStage enhancements
Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn);
Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor);
Promise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn);
Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn);
Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor);
```
## Advanced Filtering and Lifecycle Management

Since version 0.9.0, the API also includes a powerful filtering operator that complements the canonical `map`/`flatMap` (or `thenApply`/`thenCompose`) operations. It allows you to short-circuit a pipeline if a condition is not met, optionally providing a custom error:
```java
Promise<T> thenFilter(Predicate<? super T> predicate);
Promise<T> thenFilter(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier);
Promise<T> thenFilterAsync(Predicate<? super T> predicate);
Promise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier);
Promise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor);
Promise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, Executor executor);
```
Additionally, the `Promise` interface provides numerous operators for lifecycle management, timeouts, and decorator patterns, such as `onCancel(Runnable)`, `delay(...)`, `orTimeout(...)`, `onTimeout(...)`, `defaultAsyncOn(Executor)`, `unwrap()`, and `raw()`. These will be explored in detail in their respective dedicated sections.

----------

## The `Promises` Utility Class

When working with the `Promise` interface, the accompanying `Promises` utility class is indispensable. It provides factory and adapter methods to create and bridge asynchronous computations.

### 1. Unit Operations and Constants

  

The `Promises` utility class provides factory methods to create pre-resolved promises, as well as highly optimized shared constants for the most common terminal states. Using these avoids unnecessary object allocation.

```java
public final class Promises {
	// --- Shared Constants (Zero Allocation) ---
	
	/** A shared, pre-resolved promise yielding {@link Boolean#TRUE}. */
	public static final Promise<Boolean> TRUE = Promises.success(Boolean.TRUE);

	/** A shared, pre-resolved promise yielding {@link Boolean#FALSE}. */
	public static final Promise<Boolean> FALSE = Promises.success(Boolean.FALSE);

	// --- Factory Methods ---
	
	/** Creates a new promise successfully completed with the given value. */
	public static <T> Promise<T> success(T value);

	/** Creates a new promise completed exceptionally with the given throwable. */
	public static <T> Promise<T> failure(Throwable exception);

	/**
	 * Adapts an {@link Optional} into a {@link Promise}.
	 * If present, it returns {@code success(value)}; if empty, it returns
	 * {@code failure(new NoSuchElementException())}.
	 */
	public static <T> Promise<T> maybe(Optional<T> maybeValue);

	/**
	 * Returns a shared, pre-resolved promise that completes with {@code null}.
	 * Using this avoids allocating a new promise when a null result is acceptable.
	 */
	public static <T> Promise<T> nothing();

	/**
 	 * Returns a shared promise that is already in the cancelled state.
	 * Useful when you need to immediately return a cancelled promise without
	 * allocating a new one.
	 */
	public static <T> Promise<T> canceled();
}
```
**When to use which:**

-   **`Promises.TRUE` / `Promises.FALSE`**: Always prefer these over `Promises.success(true)` or `Promises.success(false)` when returning a boolean result. They are singletons and incur zero allocation overhead.
-   **`Promises.nothing()`**: Ideal for `Promise<Void>` chains or when a method signature requires a `Promise<T>` but you have no meaningful value to return (equivalent to `success(null)` but without allocation).
-   **`Promises.canceled()`**: Use this when a method needs to short-circuit and return an already-cancelled promise (e.g., when a feature flag is disabled or a request is preemptively rejected), allowing downstream `onCancel` handlers to fire immediately.
-   **`Promises.maybe(...)`**: Perfect for bridging legacy APIs that return `Optional<T>` into your asynchronous `Promise` pipelines without writing verbose `if/else` blocks.

### 2. Adapting External Stages

The `from` method is the primary bridge for integrating third-party `CompletionStage` implementations (including the standard `CompletableFuture`) into the Tascalate Concurrent ecosystem:
```java
public final class Promises {
    public static <T> Promise<T> from(CompletionStage<T> stage);
}
```
The `from` method employs a smart, multi-step resolution strategy to ensure maximum performance and compatibility:

1.  If the supplied `stage` is already a `Promise`, it is returned **unchanged** (zero overhead).
2.  If the `stage` is a `CompletableFuture`, a specially tailored, lightweight wrapper is returned.
3.  If the `stage` additionally implements `Future`, a specialized wrapper is returned that delegates all blocking `Future` API methods directly to the underlying instance.
4.  Otherwise, a generic wrapper is created, providing a robust implementation of the blocking `Future` API atop the asynchronous `CompletionStage` API.

**Crucially, this method _never_ resorts to `CompletionStage.toCompletableFuture()`.** In the Java 8 API, `toCompletableFuture()` is an optional operation, and the documentation explicitly states: _"A CompletionStage implementation that does not choose to interoperate with others may throw UnsupportedOperationException."_ By avoiding it, Tascalate Concurrent guarantees seamless interoperability with any minimal, yet valid, `CompletionStage` implementation.

## A Note on Cancellation vs. Interruption

It is important to emphasize that `Promise`s returned from `Promises.success`, `Promises.failure`, `Promises.maybe`, and `Promises.from` are **cancellable** (calling `cancel(true)` will propagate the cancellation to dependent stages). However, they are **not inherently interruptible** at the thread level. True thread interruption depends on the concrete implementation executing the task.

In the next section, we will discuss the library's primary concrete implementation of an interruptible, blocking `Promise`: the `CompletableTask` class.

# 2. CompletableTask

This is the foundational building block of the library. `CompletableTask` is the implementation of the `Promise` API designed specifically for **long-running, blocking tasks** (e.g., I/O operations, database queries, blocking queue reads). 

Unlike standard `CompletableFuture`, which is optimized for non-blocking, CPU-bound computations, `CompletableTask` wraps a `RunnableFuture` (specifically a `FutureTask`). This design ensures that the task runs on a dedicated thread from an `Executor`, making it fully responsive to cancellation and interruption.

## 1. Submitting Blocking Tasks

You can submit blocking work to an `Executor` using familiar entry points, similar to `CompletableFuture`:

```java
// 1. Returning a value (using Supplier)
Promise<SomeValue> p1 = CompletableTask.supplyAsync(() -> {
    return blockingCalculationOfSomeValue();
}, myExecutor);

// 2. Side-effect only (using Runnable)
Promise<Void> p2 = CompletableTask.runAsync(this::someIoBoundMethod, myExecutor);
```
Because I/O-related functionality frequently uses checked exceptions, the library provides a dedicated `submit` method that accepts a `Callable`. This saves you from wrapping checked exceptions in `RuntimeException`:
```java
// Notice the checked exception in the method signature
byte[] loadFile(File file) throws IOException {
    return Files.readAllBytes(file.toPath());
}

ExecutorService executorService = Executors.newFixedThreadPool(6);

// submit() accepts a Callable, propagating checked exceptions cleanly
Promise<byte[]> contentPromise = CompletableTask.submit(
    () -> loadFile(new File("./myfile.dat")), 
    executorService
); 
```
## 2. Executor Binding and Propagation

A unique feature of `CompletableTask` is **executor binding**. When you create a task, it becomes "bound" to the provided executor. Any subsequent asynchronous composition method (`thenApplyAsync`, `thenAcceptAsync`, etc.) will automatically use this bound executor, **unless** you explicitly override it.

This eliminates the boilerplate of passing the executor to every single step of a long promise chain:
```java
CompletableTask
  .completed("Hello!", myExecutor) // Starting point bound to myExecutor
  .thenApplyAsync(myMapper)        // Runs on myExecutor
  .thenApplyAsync(myTransformer)   // Runs on myExecutor
  .thenAcceptAsync(myConsumer)     // Runs on myExecutor
  .thenRunAsync(myAction);         // Runs on myExecutor
  ```
### Enforcing Executor Propagation

By default, if a step in the chain explicitly specifies a _different_ executor, subsequent steps without an explicit executor will revert to that _new_ executor. If you want to guarantee that your original executor is **always** used for async steps (unless explicitly overridden at that specific step), use the `enforceDefaultAsync` flag:
```java
Promise<String> robustChain = CompletableTask
    .asyncOn(myPrimaryExecutor, true) // true = enforce propagation
    .thenApplyAsync(data -> fetchData(data)) // Uses myPrimaryExecutor
    .thenApplyAsync(data -> transform(data), otherExecutor) // Explicitly uses otherExecutor
    .thenApplyAsync(data -> save(data)); // Reverts to myPrimaryExecutor (enforced!)
```
## 3. True Cancellation and Interruption

Because `CompletableTask` is backed by a `FutureTask`, it supports **true cancellation**. If you cancel the returned `Promise` with `mayInterruptIfRunning = true`, the underlying thread executing the blocking code will be interrupted.
```java
Promise<byte[]> downloadPromise = CompletableTask.submit(
    () -> downloadLargeFile(url), 
    threadPoolExecutor
);

// ... later, user cancels the operation
boolean wasCancelled = downloadPromise.cancel(true);

if (wasCancelled) {
    // The thread running downloadLargeFile() receives Thread.interrupt()
    // If the I/O operation is interruptible (e.g., NIO channels, blocking queues), 
    // it will abort immediately and throw an InterruptedException or ClosedByInterruptException.
}
```
> **Important Caveat:** Cancellation via interruption **only works if the underlying `Executor` supports it**. For example, `ThreadPoolExecutor` respects interruptions and will interrupt the worker thread. However, `ForkJoinPool` generally ignores interruptions for running tasks, meaning a cancelled `CompletableTask` running on a `ForkJoinPool` may continue executing until it naturally completes.

## 4. Async vs. Non-Async Composition

You may notice the emphasis on `*Async` methods (e.g., `thenApplyAsync`) in the examples above. This is intentional:

-   **Non-async methods** (e.g., `thenApply`) execute the function _inline_ in the thread that completes the previous stage. They **cannot** be interrupted via `Promise.cancel()`, because there is no separate `Future` to cancel.
-   **Async methods** (e.g., `thenApplyAsync`) submit the function as a _new_  `CompletableTask` to the bound executor. This new task **can** be interrupted if the overall promise chain is cancelled.

**Guideline:** Use `*Async` composition methods when the function itself performs heavy I/O or blocking operations that you might want to cancel. Use non-async methods for lightweight, non-blocking CPU calculations (like simple mappings or filtering) where the overhead of executor queuing would outweigh the execution time.


## 5. A Note on TaskExecutorService

While `CompletableTask` is the standard way to wrap standard Java `Executor` instances, the Tascalate Concurrent library also provides a specialized `TaskExecutorService` interface. This interface natively returns `Promise`s from its `submit` methods, completely eliminating the need to manually wrap tasks with `CompletableTask`.

We will cover this advanced executor integration, along with custom thread factories and completion services, in detail in **Chapter 16**.

# 3. Dependent Promises

As mentioned earlier, when you cancel a `Promise`, all `Promise`s that depend on it are completed exceptionally with a `CompletionException` wrapping a `CancellationException`. This is the standard behavior, and `CompletableFuture` works exactly the same way.

However, when you cancel a derived `Promise`, the original `Promise` is **not** cancelled:

```java
Promise<?> original = CompletableTask.supplyAsync(() -> someIoBoundMethod(), myExecutor);
Promise<?> derivedA = original.thenRunAsync(() -> someMethodA());
Promise<?> derivedB = original.thenRunAsync(() -> someMethodB());
...
derivedB.cancel(true);
```
If you cancel `derivedB` above, its `Runnable` method wrapping `someMethodB` is interrupted. However, the `original` promise is not cancelled, and `someIoBoundMethod` keeps running. Additionally, `derivedA` is not cancelled, which is the expected behavior.

Sometimes, however, we have a linear chain of promises and a requirement to cancel the entire chain from the tail back to the head. Consider the following method:
```java
public Promise<DataStructure> loadData(String url) {
   return CompletableTask.supplyAsync(() -> loadXml(url))
                         .thenApplyAsync(xml -> parseXml(xml)); 
}

...
Promise<DataStructure> p = loadData("http://someserver.com/rest/ds");
...
if (someCondition()) {
  // Only the second promise (parseXml) is canceled.
  p.cancel(true);
}
```
Clients of this method only see the derived promise. If they decide to cancel it, they expect that both `loadXml` and `parseXml` will be interrupted if not yet completed. To address this, the library provides the `DependentPromise` interface:
```java
public Promise<DataStructure> loadData(String url) {
   return DependentPromise
          .from(CompletableTask.supplyAsync(() -> loadXml(url)))
          .thenApplyAsync(xml -> parseXml(xml), true); 
}

...
Promise<DataStructure> p = loadData("http://someserver.com/rest/ds");
...
if (someCondition()) {
  // Now the whole chain is canceled.
  p.cancel(true);
}
```
`DependentPromise` overloads methods like `thenApply`, `thenRun`, `thenAccept`, and `thenCombine` with additional arguments:

-   If the method accepts no other `CompletionStage` (e.g., `thenApply`, `thenRun`, `thenAccept`), it takes a boolean flag `enlistOrigin` to specify whether the original `Promise` should be enlisted for cancellation.
-   If the method accepts another `CompletionStage` (e.g., `thenCombine`, `applyToEither`, `thenAcceptBoth`), it takes a `Set<Promise.Origin>` to specify whether the original `Promise` and/or the argument `CompletionStage` should be enlisted for cancellation along with the resulting promise. For example:
```java
public Promise<List<DataStructure>> loadData(String url) {
   return DependentPromise
          .from(CompletableTask.supplyAsync(() -> loadXml(url + "/source1")))
          .thenCombine( 
              CompletableTask.supplyAsync(() -> loadXml(url + "/source2")), 
              (xml1, xml2) -> Arrays.asList(xml1, xml2),
              Promise.Origin.ALL
          )
          .thenApplyAsync(xmls -> parseXmlsList(xmls), true); 
}
```
Please note that since release 0.5.4, the `Promise` interface includes a default `dependent()` method that serves the same purpose and allows for fluent chained calls:
```java
public Promise<DataStructure> loadData(String url) {
   return CompletableTask
          .supplyAsync(() -> loadXml(url))
          .dependent()
          .thenApplyAsync(xml -> parseXml(xml), true); 
}
```
**Attention:** Merely calling `dependent()` on a `Promise` is not enough to change the behavior of the default `thenApply`, `thenRun`, `thenAccept`, or `thenCombine` methods defined in `CompletionStage`. You must explicitly use the overloaded forms that take either a `boolean` or a `Set<Promise.Origin>` as the last parameter. This is an intentional design decision: the `Promise` returned in the example above can still be used by client code to create _independent_ promises (as per the `CompletionStage` API), so the library adheres to the principle of least surprise.

Below are two examples (wrong and right) to reiterate this point in Java code.

**Wrong code to cancel the whole chain:**
```java
Promise<Xml> xml = CompletableTask.supplyAsync(() -> loadXml(url));
Promise<Data> data = xml.dependent()
                        .thenApplyAsync(x -> parseXml(x));  // Not the overloaded method - 
                                                            // no "true" as the last parameter
...
// This cancels only the "data" promise, but will NOT cancel the "xml" promise
data.cancel(true);
```
**Right code to cancel the whole chain:**
```java
Promise<Xml> xml = CompletableTask.supplyAsync(() -> loadXml(url));
Promise<Data> data = xml.dependent()
                        .thenApplyAsync(x -> parseXml(x), true);  // Overloaded method - 
                                                                  // "true" as the last parameter means 
                                                                  // enlist the "origin" promise 
                                                                  // for cancellation
...
// This cancels both the "data" promise AND the "xml" promise (if it's not completed yet)
data.cancel(true);
```
So far, so good. Following the `CompletionStage` API contract is undeniably important. But what if you need to create a local chain of 10 or 30 stages? It becomes tedious and error-prone to explicitly pass additional parameters to every overloaded method. "Error-prone" means exactly this: it is all too easy to forget to type `true` as the last parameter, inadvertently breaking the cancellation chain.

Tascalate Concurrent provides an elegant solution for this edge case: use the overloaded `dependent(Set<Promise.Origin>)` method. Here is an example adapted from a real-world project:
```java
Promise<?> myPromise = 
    CompletableTask.supplyAsync(() -> candidate, pool)
                   .dependent(Promise.Origin.ALL) // Key point: alters the behavior of CompletionStage API methods
                   .thenApplyAsync(CheckIp::doWork)
                   .thenApplyAsync(CheckType::doWork)
                   .thenApplyAsync(CheckExclusion::doWork)
                   .thenApplyAsync(AssignEntity::doWork)
                   .thenCombineAsync(collectedStatsPromise, this::combineWithStats) // <-- pay attention here
                   .thenApplyAsync(DecideWmi::doWork)
                   .thenApplyAsync(ObtainWmiConnection::doWork)
                   .thenApplyAsync(ObtainRegistryWmiConnection::doWork)
                   .thenApplyAsync(RunCompliance::doWork)
                   .thenApplyAsync(DecideComplianceResult::doWork)
                   .thenApplyAsync(CreateAlert::doWork)
                   .thenApplyAsync(ApplyFailureManager::doWork)
                   .thenApplyAsync(ApplyDisconnectManager::doWork)
                   .thenApplyAsync(EnforceCompliance::doWork)
                   .exceptionally(ExceptionHandlerService::handle);
```
Unlike the conservative form of the `dependent()` decorator, the overloaded `dependent(Promise.Origin.ALL)` will enlist ALL promises referenced by the composition methods as _dependent_ and will cancel them when `myPromise` is cancelled—even the `collectedStatsPromise` passed from the outside! Everything enlisted forms a single cancellation block. By the way, if you do not need to cancel `collectedStatsPromise`, you can use the `dependent(Promise.Origin.THIS_ONLY)` decorator, which enlists only the originating promises and ignores "side-passed" arguments.

But what about obeying the `CompletionStage` contract? Is it still safe to return `myPromise` to clients who are unaware of its modified behavior? No, it is not. You should revert the effect of the `dependent(Promise.Origin.ALL)` decorator before passing `myPromise` outside:
```java
public Promise<?> forkWmiProcessingChain(Promise<Stats> collectedStatsPromise) {
    Promise<?> myPromise = 
        CompletableTask.supplyAsync(() -> candidate, pool)
                       .dependent(Promise.Origin.ALL) 
                       .thenApplyAsync(CheckIp::doWork)
                       .thenApplyAsync(CheckType::doWork)
                       // ...
                       .exceptionally(ExceptionHandlerService::handle);
                       
    return myPromise.unwrap(); // or myPromise.raw() in this case               
}
```
The final operation in the function above—`unwrap()`—removes the most recently applied decorator (in this case, `dependent(...)`). Looking ahead, it is worth mentioning that `raw()` will remove _all_ applied decorators. There are several possible decorators, and `dependent(...)` is just one of them. In the next chapters, we will discuss the `defaultAsyncOn` decorator, and later you will see how to apply custom decorators (which are not directly bound to the `Promise` API as methods).

# 4. Wait! Why isn't my task cancelling?

One of the most common complaints in the issue tracker is a variation of the report: *"My code is not cancelled when requested."* The symptoms are typical: the `Promise` is cancelled, any dependent `Promise`s complete exceptionally (as expected), but the underlying thread keeps running until the end of the submitted code. It feels like the primary promise of the library—interruptible execution—is broken.

Is the library broken? No. Is the user code correct? Technically, yes. But the library and the user code are not cooperating to actually halt the asynchronous execution.

When you call `cancel(true)` on a `Promise` returned by `CompletableTask` (or any derived `Promise`), the library does exactly two things:
1. It sets its own state to *cancelled* and propagates this failure to any dependent promises (standard `CompletableFuture` behavior).
2. It calls `Thread.currentThread().interrupt()` on the thread currently executing the task.

**It is the responsibility of your user code** (the `Runnable`, `Supplier`, `Consumer`, or `Function` you passed to the executor) to react to the `Thread.currentThread().isInterrupted()` flag. Let's look at typical scenarios and how to handle them.

## Scenario 1: Computational Tasks (No I/O)

If your code performs heavy calculations, you must explicitly check the interruption flag to break execution via early returns, loop breaks, or by throwing an exception:

```java
outer:
for (int i = 0; i < SOME_LARGE_SIZE; i++) {
    for (int j = 0; j < SOME_EVEN_LARGER_SIZE; j++) {
        if (Thread.currentThread().isInterrupted()) {
            break outer; // Exit cleanly when interrupted
        }
        // ... perform calculations here ...
    }
}
```
It is up to you to decide how often to check the flag. There is always a trade-off between the overhead of checking during normal execution and the delay before the thread reacts to cancellation. In the example above, moving the check to the outer loop speeds up the normal path but increases the cancellation reaction time.

Some APIs provide natural hooks for early exit. A great example is `java.nio.file.Files.walkFileTree` combined with `FileVisitor`:
```java
Executor executor = Executors.newFixedThreadPool(4);
Promise<Void> promise = CompletableTask.runAsync(() -> {
    try {
        Files.walkFileTree(Paths.get("/some/large/directory"), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (Thread.currentThread().isInterrupted()) {
                    return FileVisitResult.TERMINATE; // STOP when interrupted
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (Thread.currentThread().isInterrupted()) {
                    return FileVisitResult.TERMINATE;
                }
                // ... process file ...
                return FileVisitResult.CONTINUE;
            }
        });
    } catch (IOException e) {
        throw new RuntimeException(e);
    }
}, executor);

// ... later, from another thread ...
promise.cancel(true);
```
## Scenario 2: Legacy Blocking I/O

If your code uses legacy blocking APIs like `java.io.FileInputStream`, `java.net.Socket`, `java.net.URLConnection`, or outdated synchronous HTTP clients, **the thread will not terminate when interrupted**. These APIs block at the OS level and simply ignore the Java thread interrupt flag.

You have three options to resolve this:

### Option A: Migrate to Asynchronous APIs (Recommended)

Replace synchronous networking libraries with modern asynchronous alternatives. For HTTP, options like Java 11+ `java.net.http.HttpClient`, OkHttp, or AsyncHttpClient return `CompletableFuture` natively and integrate seamlessly with Tascalate Concurrent.

### Option B: Migrate to NIO

Replace legacy `java.io` streams with their NIO counterparts, which are inherently interruptible:
```java
// Legacy (NOT interruptible)
try (InputStream in = new FileInputStream("E:/Downloads/LargeFile.rar")) {
    // ... work with stream ...
}

// NIO (Interruptible)
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
// ...
try (InputStream in = Channels.newInputStream(
        FileChannel.open(Paths.get("E:/Downloads/LargeFile.rar"), StandardOpenOption.READ))) {
    // ... work with stream ...
}
// The same principle applies to SocketChannel for network I/O.
```
### Option C: Use the `BlockingIO` Utility

If you cannot migrate to NIO but can alter the source code, Tascalate Concurrent provides the `BlockingIO` utility class (available since version 0.9.5). It wraps functional interfaces and forcefully closes registered resources when the thread is interrupted, unblocking the I/O operation.
```java
Promise<byte[]> promise = CompletableTask.supplyAsync(
    BlockingIO.interruptible(() -> {
        // Register the stream as an interruption target immediately after creation
        try (InputStream in = BlockingIO.register(new FileInputStream("E:/Downloads/LargeFile.rar"))) {
            return in.readAllBytes(); // This will throw if the thread is interrupted
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } 
    }), 
    executor
);

// ... later ...
promise.cancel(true); // This will close the FileInputStream, unblocking the read
```
`BlockingIO.interruptible(...)` can wrap any functional interface used in the `Promise` API. The argument to `BlockingIO.register()` can be any `AutoCloseable`. For example, with `HttpURLConnection`:
```java
HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
BlockingIO.register((AutoCloseable) conn::disconnect);
```
## ⚠️ The Java 21+ Virtual Threads Caveat

If you are running on **JDK 21 or higher** and your tasks are executed on **Virtual Threads** (e.g., via `ThrottledExecutorService` or a custom `Thread.ofVirtual()` factory), **you generally do not need `BlockingIO`**.

Project Loom natively handles I/O interruption for Virtual Threads. When a Virtual Thread performing blocking I/O is interrupted, the JVM automatically unmounts it and aborts the blocking operation (typically throwing a `ClosedByInterruptException`). The `BlockingIO` hack is primarily intended for pre-Loom JDKs (8–20) or when you are explicitly using traditional Platform Thread pools.

## The Hard Truth

If the blocking I/O is hidden deep inside a third-party library that you cannot modify (e.g., Spring's legacy `RestTemplate`, old JDBC drivers, or proprietary SDKs), **true interruption is generally impossible**. Tascalate Concurrent can mark the `Promise` as cancelled, but it cannot magically force a black-box native blocking call to return. In such cases, you must rely on application-level timeouts or migrate to libraries that support modern asynchronous or interruptible patterns.

# 5. Overriding the Default Asynchronous Executor

One of the well-known pitfalls of the standard `CompletableFuture` API is how it handles the default asynchronous executor. Consider the following example:

```java
CompletionStage<String> p1 = CompletableFuture.supplyAsync(this::produceValue, executorInitial);
CompletionStage<String> p2 = p1.thenApplyAsync(this::transformValueA); // Uses ForkJoinPool.commonPool()!
CompletionStage<String> p3 = p2.thenApplyAsync(this::transformValueB, executorNext);
CompletionStage<String> p4 = p3.thenApplyAsync(this::transformValueC); // Uses ForkJoinPool.commonPool()!
```
The initial call to `produceValue` runs on `executorInitial`. However, because `transformValueA` does not specify an executor, it silently falls back to `ForkJoinPool.commonPool()`. Even worse, after explicitly providing `executorNext` for `transformValueB`, the subsequent `transformValueC`  _also_ falls back to the common pool.

To fix this in standard Java, you are forced to pass an explicit `Executor` to every single `*Async` method. While Java 9+ introduced `defaultExecutor()` and `newIncompleteFuture()` to allow subclassing `CompletableFuture` to change this behavior, it requires significant boilerplate and custom entry-point methods.

## The `CompletableTask` Advantage

As discussed in **Chapter 2**, `CompletableTask` was designed specifically to solve this and other blocking-task pitfalls. With `CompletableTask`, the behavior is much more intuitive and predictable:
```java
Promise<String> p1 = CompletableTask.supplyAsync(this::produceValue, executorInitial);
Promise<String> p2 = p1.thenApplyAsync(this::transformValueA);          // Uses executorInitial
Promise<String> p3 = p2.thenApplyAsync(this::transformValueB, executorNext);
Promise<String> p4 = p3.thenApplyAsync(this::transformValueC);          // Uses executorNext
```
Here, the rule is simple: **the latest explicitly specified `Executor` becomes the new default** for all subsequent nested asynchronous composition methods that do not provide their own executor.

## Fine-Grained Executor Control

Since "one size fits all" is rarely true in complex applications, Tascalate Concurrent (since release 0.6.6) provides two additional mechanisms to precisely control executor propagation:

**1. Enforced Default Executor at the Root**  
`CompletableTask` provides an overloaded `asyncOn` method:
```java
public static Promise<Void> asyncOn(Executor executor, boolean enforceDefaultAsync)
```
When `enforceDefaultAsync` is `true`, the provided executor is "pinned" as the default for the entire chain. Even if an intermediate step explicitly uses a different executor, subsequent steps without an explicit executor will revert to this pinned default. This mimics the Java 9+ `CompletableFuture` subclassing behavior, but without the boilerplate.

**2. Mid-Chain Executor Redefinition**  
The `Promise` interface provides the `defaultAsyncOn` decorator:
```java
Promise<T> defaultAsyncOn(Executor executor)
```
This allows you to switch the default executor at _any point_ in the chain. All subsequent nested asynchronous composition methods (without an explicit executor) will use this newly defined default.

## Summary of Executor Propagation Rules

With Tascalate Concurrent, you have three powerful, composable options to control asynchronous execution:

1.  **Latest Explicit Executor (Default):** The most recently explicitly passed `Executor` to an `*Async` method is inherited by all derived `Promise`s.
2.  **Pinned Root Executor:** A single default `Executor` passed to `CompletableTask.asyncOn(executor, true)` is propagated through the whole chain, overriding intermediate defaults.
3.  **Mid-Chain Redefinition:** You can redefine the default `Executor` at any point using `defaultAsyncOn(executor)` for all subsequent derived `Promise`s.

Having the best of all three worlds, the only responsibility of the library user is to apply these options consistently.

## Bridging Standard `CompletableFuture` to Interruptible Tasks

A common scenario is needing to start an interruptible, blocking operation _after_ a standard, non-interruptible `CompletableFuture` completes. For this, `CompletableTask` provides a dedicated utility method:
```java
public static <T> Promise<T> waitFor(CompletionStage<T> stage, Executor executor)
```
This is essentially a robust shortcut for `CompletableTask.asyncOn(executor).thenCombine(stage, (u, v) -> v)`, but optimized specifically for this bridging pattern.

**Typical Usage:**
```java
ExecutorService executorService = Executors.newFixedThreadPool(3);
CompletableFuture<String> replyUrlPromise = sendRequestAsync(); // Standard, non-interruptible

// Bridge to an interruptible Promise chain
Promise<byte[]> dataPromise = CompletableTask.waitFor(replyUrlPromise, executorService)
    .thenApplyAsync(url -> loadDataInterruptibly(url));

// If dataPromise is cancelled later, loadDataInterruptibly() will be properly interrupted 
// if it has not yet completed.
```
This pattern ensures that your blocking, interruptible work is correctly isolated on a dedicated thread pool and remains fully responsive to `Promise.cancel(true)`.

# 6. Timeouts

Any robust application requires a certain level of functionality to handle situations when things go wrong. While the ability to cancel a hung operation has existed in the library since day one, it is obviously not enough on its own. Cancellation defines *what* to do in the face of a problem, but the responsibility of *when* to do it was previously left entirely to application code. Starting from release [0.5.4](https://github.com/vsilaev/tascalate-concurrent/releases/tag/0.5.4), the library fills this gap with comprehensive timeout-related features.

Application developers now have the following options to control the execution time of a `Promise` (declared directly in the `Promise` interface):
```java
<T> Promise<T> orTimeout(long timeout, TimeUnit unit[, boolean cancelOnTimeout = true])
<T> Promise<T> orTimeout(Duration duration[, boolean cancelOnTimeout = true])
```
These methods create a new `Promise` that is settled successfully or exceptionally when the original promise completes within the given timeout. If the time expires first, the new promise is settled exceptionally with a `TimeoutException`. In all cases, the timeout handling code is executed on the default asynchronous `Executor` of the original `Promise`.
```java
Executor myExecutor = ...; // Get an executor
Promise<String> callPromise = CompletableTask
    .supplyAsync(() -> someLongRunningIoBoundMethod(), myExecutor)
    .orTimeout(Duration.ofSeconds(3));
    
Promise<?> nextPromiseSync = callPromise.whenComplete((v, e) -> processResultSync(v, e));
Promise<?> nextPromiseAsync = callPromise.whenCompleteAsync((v, e) -> processResultAsync(v, e));
```
In the example above, `callPromise` will be settled within 3 seconds either as a result of the `someLongRunningIoBoundMethod` execution, or exceptionally with a `TimeoutException`.

It is worth mentioning that _both_  `processResultSync` and `processResultAsync` will be executed using `myExecutor` if the timeout is triggered. This rule applies to all timeout-related methods in the library.

The optional `cancelOnTimeout` parameter defines whether the original `Promise` should be cancelled when time expires; it defaults to `true` when omitted. Therefore, in the example above, `someLongRunningIoBoundMethod` will be interrupted if it takes more than 3 seconds to complete.

> **Important:** Any `Promise` can be cancelled on timeout, including wrappers created via `Promises.from(stage)`. However, only tasks submitted via `CompletableTask` are actually _interruptible_ at the thread level.

Cancelling the original promise on timeout is the desired behavior in most cases, but not always. In reality, "Warn-first, Cancel-next" scenarios are common, where "warn" might involve logging, sending notification emails, or showing messages to the user on a UI. The library allows you to set several non-cancelling timeouts, as shown below:
```java
Executor myExecutor = ...; // Get an executor
Promise<String> resultPromise = CompletableTask
    .supplyAsync(() -> someLongRunningIoBoundMethod(), myExecutor);

// 1. Show a UI message to let the user know the operation is still under control
Promise<?> t1 = resultPromise
    .orTimeout(Duration.ofSeconds(2), false)
    .exceptionally(e -> {
        if (e instanceof TimeoutException) {
            UI.showMessage("The operation takes longer than expected, please wait...");
        }
        return null;
    }); 

// 2. Show a UI confirmation to let the user cancel the operation explicitly
Promise<?> t2 = resultPromise
    .orTimeout(Duration.ofSeconds(5), false)
    .exceptionally(e -> {
        if (e instanceof TimeoutException) {
            UI.clearMessages();
            UI.showConfirmation("Service is not responding. Do you want to cancel (Y/N)?");
        }
        return null;
    }); 

// 3. Hard cancel in 10 seconds
resultPromise.orTimeout(Duration.ofSeconds(10), true);
```
Please note that the timeout starts from the exact moment the `orTimeout` method is called. Hence, if you have a chain of unresolved promises ending with an `orTimeout` call, the _entire chain_ must complete within the given time:
```java
Executor myExecutor = ...; // Get an executor
Promise<String> parallelPromise = CompletableTask
    .supplyAsync(() -> someLongRunningDbCall(), myExecutor);

Promise<List<String>> resultPromise = CompletableTask
    .supplyAsync(() -> someLongRunningIoBoundMethod(), myExecutor)
    .thenApplyAsync(v -> converterMethod(v))
    .thenCombineAsync(parallelPromise, (u, v) -> Arrays.asList(u, v))
    .orTimeout(Duration.ofSeconds(5)); // Applies to the WHOLE chain above
```
In this latest example, `resultPromise` will be resolved successfully if and only if `someLongRunningIoBoundMethod`, `converterMethod`, _and_  `someLongRunningDbCall` all complete within 5 seconds.

If it is necessary to restrict the execution time of a _single step_ instead of the whole chain, you can use the standard `CompletionStage.thenCompose` method. For instance, if we only want to restrict `converterMethod`:
```java
Promise<List<String>> resultPromise = CompletableTask
    .supplyAsync(() -> someLongRunningIoBoundMethod(), myExecutor)
    // Restrict ONLY the execution time of converterMethod
    // -- start of changes
    .thenCompose(v -> 
        CompletableTask.completed(v, myExecutor)
                       .thenApplyAsync(vv -> converterMethod(vv))
                       .orTimeout(Duration.ofSeconds(5))
    )
    // -- end of changes
    .thenCombineAsync(parallelPromise, (u, v) -> Arrays.asList(u, v));
```
Moreover, in the original example, only the final `thenCombineAsync` stage would be cancelled on timeout. To cancel the _entire chain_ on timeout, you should use the functionality of the `DependentPromise` interface (discussed in detail in Section 3):
```java
Executor myExecutor = ...; // Get an executor
Promise<String> parallelPromise = CompletableTask
    .supplyAsync(() -> someLongRunningDbCall(), myExecutor);

Promise<List<String>> resultPromise = CompletableTask
    .supplyAsync(() -> someLongRunningIoBoundMethod(), myExecutor)
    .dependent()
    // Enlist the promise of someLongRunningIoBoundMethod for cancellation
    .thenApplyAsync(v -> converterMethod(v), true)  
    // Enlist the result of thenApplyAsync AND parallelPromise for cancellation
    .thenCombineAsync(parallelPromise, (u, v) -> Arrays.asList(u, v), Promise.Origin.ALL)
    .orTimeout(Duration.ofSeconds(5)); // Now the timeout will cancel the whole chain
```
## Fallback Values on Timeout (`onTimeout`)

Another useful family of timeout-related methods declared in the `Promise` interface provides fallback values instead of exceptions:
```java
<T> Promise<T> onTimeout(T value, long timeout, TimeUnit unit[, boolean cancelOnTimeout = true])
<T> Promise<T> onTimeout(T value, Duration duration[, boolean cancelOnTimeout = true])
<T> Promise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit[, boolean cancelOnTimeout = true])
<T> Promise<T> onTimeout(Supplier<? extends T> supplier, Duration duration[, boolean cancelOnTimeout = true])
```
The `onTimeout` methods are similar in all regards to `orTimeout`, with one obvious difference: instead of completing the resulting `Promise` exceptionally with a `TimeoutException` when time expires, they settle it _successfully_ with the alternative `value` supplied (either directly or via a `Supplier`):
```java
Executor myExecutor = ...; // Get an executor
Promise<String> callPromise = CompletableTask
    .supplyAsync(() -> someLongRunningIoBoundMethod(), myExecutor)
    .onTimeout("Timed-out! Using default cache.", Duration.ofSeconds(3));
```
In this example, `callPromise` will be settled within 3 seconds either as a result of the method execution, or successfully with the default string `"Timed-out! Using default cache."` if the time is exceeded.

> **Crucial difference from Java 9+ `CompletableFuture`:** In Tascalate Concurrent, both `orTimeout` and `onTimeout` return a _new_  `Promise` that can be cancelled individually, without cancelling the original `Promise`. Furthermore, if the original promise is cancelled due to a timeout, it completes with a `CancellationException`, not a `TimeoutException`.
> 
> By contrast, Java 9+ `CompletableFuture` timeout operations are mere "side effects" that mutate and return the original `CompletableFuture` itself. Calling `completableFuture.orTimeout(100, TimeUnit.MILLIS).cancel()` cancels the `completableFuture` itself, and there is no way to revert the timeout once set. Correspondingly, when time expires, the original `completableFuture` is completed exceptionally with a `TimeoutException`.

## Introducing Delays (`delay`)

Finally, the `Promise` interface provides an option to insert delays into the call chain:
```java
<T> Promise<T> delay(long timeout, TimeUnit unit[, boolean delayOnError = true])
<T> Promise<T> delay(Duration duration[, boolean delayOnError = true])
```
The delay starts _only after_ the original `Promise` is completed (either successfully or exceptionally), unlike `orTimeout`/`onTimeout` where the timer starts immediately. The resulting delayed `Promise` is resolved after the specified timeout with the exact same result as the original `Promise`.

The `delayOnError` argument specifies whether the delay should be applied if the original `Promise` resolves exceptionally. By default, this is `true`. If set to `false`, the delayed `Promise` completes immediately upon the failure of the original `Promise`, bypassing the wait.
```java
Executor myExecutor = ...; // Get an executor

// Always delay by 1 second before converting, giving the CPU a moment to calm down
Promise<String> callPromise1 = CompletableTask
    .supplyAsync(() -> someLongRunningIoBoundMethod(), myExecutor)
    .delay(Duration.ofSeconds(1)) 
    .thenApply(v -> convertValue(v));
    
// Delay by 1 second ONLY on success; fail immediately on error
Promise<String> callPromise2 = CompletableTask
    .supplyAsync(() -> alternativeLongRunningIoBoundMethod(), myExecutor)
    .delay(Duration.ofSeconds(1), false) 
    .thenApply(v -> convertValue(v));
```
Like other timeout-related methods, `convertValue` is invoked on the default asynchronous `Executor` of the original `Promise`.

You may notice that a delay is typically introduced in the middle of a chain. But what if you want to back off the execution of the _entire_ chain from the start? Simply begin with a resolved promise:
```java
// Option 1: Interruptible tasks chain on the supplied executor
CompletableTask.asyncOn(myExecutor)
               .delay(Duration.ofSeconds(5))
               .thenApplyAsync(ignore -> produceValue());

// Option 2: Computational tasks on ForkJoinPool.commonPool()
Promises.from(CompletableFuture.completedFuture(""))
        .delay(Duration.ofSeconds(5))
        .thenApplyAsync(ignore -> produceValue());
```
Since back-off execution is a common requirement, the library also provides convenient static shortcuts in the `CompletableTask` class:
```java
static Promise<Duration> delay(long timeout, TimeUnit unit, Executor executor);
static Promise<Duration> delay(Duration duration, Executor executor);
```
_(Note: In Java 9+, a different approach is chosen for delays. There is no corresponding operation defined directly on the `CompletableFuture` object; instead, you must use a delayed executor. Please refer to the documentation for [`CompletableFuture.delayedExecutor`](https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/CompletableFuture.html#delayedExecutor-long-java.util.concurrent.TimeUnit-java.util.concurrent.Executor-) for details.)_

# 7. Combining multiple CompletionStages

The standard `CompletableFuture.allOf()` and `anyOf()` methods are notoriously limited: `allOf` returns `CompletableFuture<Void>` (discarding individual results), and `anyOf` returns `Object` (requiring unsafe casting). Furthermore, they strictly require `CompletableFuture` instances, ignoring the broader `CompletionStage` interface.

Tascalate Concurrent provides a rich, type-safe suite of combinators in the `Promises` utility class that solve these issues and add powerful features like partial success, strict/non-strict error handling, and eager cancellation.

### Why use `Promises` combinators?
1. **Interface Agnostic:** Works with *any* `CompletionStage` implementation without forcing conversion to `CompletableFuture`.
2. **Type-Safe Results:** Returns strongly-typed `List<T>`, `Map<K, T>`, or `T` instead of `Void` or `Object`.
3. **Flexible Inputs:** Accepts varargs arrays, `List`s, or `Map`s.
4. **Eager Cancellation:** Automatically cancels remaining stages once the final outcome is determined.
5. **Partial Success:** Supports "M out of N" success scenarios.
6. **Strict vs. Non-Strict Errors:** Choose whether to tolerate individual failures or fail fast.

## The Core Combinators

| Method | Success Condition | Failure Condition | Result Type |
| :--- | :--- | :--- | :--- |
| **`all`** | **All** N stages succeed | **Any 1** stage fails | `List<T>` or `Map<K,T>` |
| **`any`** | **Any 1** stage succeeds | **All** N stages fail | `T` (first success) |
| **`anyStrict`** | **Any 1** stage succeeds | **Any 1** fails *(before 1st success)* | `T` (first success) |
| **`atLeast(M)`** | **M** stages succeed | **N-M+1** stages fail | `List<T>` or `Map<K,T>` |
| **`atLeastStrict(M)`**| **M** stages succeed | **Any 1** fails *(before M successes)*| `List<T>` or `Map<K,T>` |
| **`atLeastOrdered(M)`**| **M** stages succeed | **N-M+1** stages fail | `List<Optional<T>>` |

### 1. `all` — Wait for everything
Completes successfully only when every provided stage succeeds. If any stage fails, the combined promise fails immediately with a `MultitargetException`.
```java
Promise<List<User>> users = Promises.all(fetchUser(1), fetchUser(2), fetchUser(3));
```
### 2. `any` vs `anyStrict` — Race for the first success

Both methods return the result of the _first_ stage that completes successfully.

-   **`any` (Non-Strict):** Ignores exceptions. If 3 stages fail but the 4th succeeds, the combined promise succeeds. It only fails if _all_ stages fail.
-   **`anyStrict` (Strict):** Fails fast. If _any_ stage throws an exception before a successful result is found, the combined promise immediately fails with that exact exception.

### 3. `atLeast` — M out of N successes

A generalization of `any`. It succeeds if at least `M` stages complete normally. The resulting `List` contains the successful values at their original indices, with `null` in the positions of failed or uncompleted stages.
```java
// Succeeds if at least 2 out of 3 DNS servers respond
Promise<List<String>> dnsResults = Promises.atLeast(2, queryDns1(), queryDns2(), queryDns3());
```
### 4. `atLeastOrdered` — Safe handling of `null` results

Because `atLeast` uses `null` to represent failed/uncompleted stages in the result list, it cannot distinguish between a stage that failed and a stage that successfully returned `null`. `atLeastOrdered` solves this by returning a `List<Optional<T>>`.
```java
Promise<List<Optional<CacheEntry>>> cacheLookups = 
    Promises.atLeastOrdered(2, cache1.get(), cache2.get());
```
## Collection Types: Arrays, Lists, and Maps

Every combinator provides three input overloads:

1.  **Varargs:**  `Promises.all(stage1, stage2, stage3)`
2.  **List:**  `Promises.all(List<CompletionStage<T>> stages)`
3.  **Map:**  `Promises.all(Map<K, CompletionStage<T>> stages)`

The `Map` overloads are incredibly useful for keyed concurrent operations. The resulting promise yields a `Map<K, T>` containing only the successfully completed entries.
```java
Map<String, CompletionStage<Integer>> stockFetchers = Map.of(
    "AAPL", fetchStock("AAPL"),
    "GOOG", fetchStock("GOOG")
);

Promise<Map<String, Integer>> stockPrices = Promises.all(stockFetchers);
```
## Eager Cancellation (`cancelRemaining`)

Most combinators accept an optional `boolean cancelRemaining` parameter (which defaults to `true`). When the outcome of the combined operation is mathematically determined (e.g., the first success in `any`, or the first failure in `all`), the combinator will automatically invoke `.cancel(true)` on all remaining, uncompleted stages. This frees up network connections and thread pool resources immediately.

## Error Handling and `MultitargetException`

When a non-strict combinator fails (e.g., `all` fails because one stage failed, or `any` fails because _all_ stages failed), it wraps the underlying exceptions in a `MultitargetException`.

You can inspect the individual failures using `getExceptions()`:
```java
Promises.all(stage1, stage2)
        .exceptionally(ex -> {
            if (ex instanceof MultitargetException) {
                MultitargetException mte = (MultitargetException) ex;
                mte.getExceptions().forEach(err -> log.error("Stage failed", err));
            }
            return null;
        });
```
_(Note: `anyStrict` and `atLeastStrict` bypass `MultitargetException` and throw the exact exception of the first stage that caused the strict failure)._

## Characteristics of the Returned Promise

1.  **Cancellation Propagation:** Manually cancelling the returned combined `Promise` will cascade and cancel _all_ underlying `CompletionStage` arguments.
2.  **Default Executor:** The default asynchronous executor for subsequent operations (like `.thenApplyAsync`) on the combined promise is undefined (it inherits from the underlying stages). To guarantee a specific executor for downstream operations, chain `.defaultAsyncOn(myExecutor)` immediately after the combinator.

# 8. Asynchronous Loops and Iteration

When working with asynchronous APIs, traditional `for` or `while` loops fall short because they do not wait for asynchronous operations to complete. If you need to process a sequence of items one by one (e.g., paginating through an API, processing files sequentially, or polling a resource), firing all requests at once with `Promises.all()` might overwhelm the target system or exhaust local resources.

Tascalate Concurrent provides two stack-safe, cancellable constructs for sequential asynchronous execution: `Promises.loop` and `Promises.forEach`.

## 1. `Promises.loop` — The Asynchronous `while` Loop

`Promises.loop` is the asynchronous equivalent of a `while` loop. It takes an initial state, a synchronous condition, and an asynchronous body that produces the next state.

```java
public static <T> Promise<T> loop(
    T initialValue, 
    Predicate<? super T> loopCondition,
    Function<? super T, ? extends CompletionStage<T>> loopBody
)
```
**Example: Fetching Paginated Data** Imagine an API that returns a page of results and a cursor for the next page. We can use `loop` to fetch all pages sequentially without blocking a thread. To accumulate results, we pass a wrapper object as the state.
```java
record Page(List<String> items, String nextCursor) {}
record State(List<String> accumulated, String nextCursor) {}

Promise<List<String>> allItems = Promises.loop(
    new State(new ArrayList<>(), "start_cursor"), // Initial state
    state -> state.nextCursor() != null,          // Condition: continue if there is a next cursor
    state -> apiClient.fetchPage(state.nextCursor()) // Body: fetch the next page asynchronously
             .thenApply(page -> new State(
                 concat(state.accumulated(), page.items()), 
                 page.nextCursor()
             ))
).thenApply(State::accumulated);
```
## 2. `Promises.forEach` — Sequential Iteration with Early Exit

`Promises.forEach` is designed for iterating over an `Iterable` sequentially. The body returns a `CompletionStage<Boolean>`. If the stage completes with `true`, the loop continues to the next element; if `false`, the loop terminates early.
```java
public static <T> Promise<Void> forEach(
    Iterable<? extends T> source, 
    Function<? super T, ? extends CompletionStage<Boolean>> loopBody
)
```
**Example: Processing Files Until a Condition is Met** Suppose you have a list of log files and you want to search them sequentially for a specific error, stopping as soon as you find it.
```java
List<Path> logFiles = getLogFiles();

Promise<Void> search = Promises.forEach(logFiles, file -> 
    fileService.searchForError(file, "FATAL_ERROR")
               .thenApply(found -> {
                   if (found) {
                       log.info("Found fatal error in {}", file);
                       return false; // Stop iteration
                   }
                   return true;      // Continue to next file
               })
);

search.thenRun(() -> log.info("Search completed."));
```
## 3. Cancellation and Stack Safety

Both `loop` and `forEach` return a standard `Promise`, which means they fully support cancellation. If you cancel the returned promise, Tascalate Concurrent will automatically attempt to cancel the `CompletionStage` that is currently executing in the loop body.
```java
Promise<Void> longRunningTask = Promises.forEach(urls, url -> downloadAsync(url));

// Later, if the user cancels the operation:
longRunningTask.cancel(true); 
// The current download is interrupted, and subsequent URLs are skipped.
```
Furthermore, the underlying `AsyncLoop` implementation is **stack-safe**. If your asynchronous body happens to return an already-completed stage (e.g., a cached result, a synchronous computation wrapped in a promise, or an empty collection in `forEach`), the loop will "trampoline" to the next iteration without growing the call stack. This prevents `StackOverflowError` even for millions of synchronous iterations.


# 9. Chunked processing with bounded concurrency

When a large collection of items must be processed asynchronously, two naive strategies compete:

* **Fire everything at once** — map all elements to stages and combine them with `Promises.all(...)`. Fast, but it may overwhelm the target system, trip rate limits, or exhaust local resources such as a database connection pool.
* **One by one** — `Promises.forEach(...)` processes elements strictly sequentially. Safe, but it underutilizes available parallelism.

`Promises.chunked` is the middle ground: an asynchronous, **concurrency-bounded** analogue of `Stream.collect(...)`. It processes the source in chunks of at most `chunkSize` elements: tasks within a chunk run concurrently, chunks run one after another, and the results are gathered with an ordinary `java.util.stream.Collector`.

```java
// Iterable source
static <S, T, A, R> Promise<R> chunked(Iterable<? extends S> values, int chunkSize,
                                       Function<? super S, CompletionStage<? extends T>> spawner,
                                       Collector<T, A, R> downstream);

// Same, with collector invocations confined to a dedicated executor
static <S, T, A, R> Promise<R> chunked(Iterable<? extends S> values, int chunkSize,
                                       Function<? super S, CompletionStage<? extends T>> spawner,
                                       Collector<T, A, R> downstream,
                                       Executor downstreamExecutor);

// Stream source (closed automatically when processing completes)
static <S, T, A, R> Promise<R> chunked(Stream<? extends S> values, int chunkSize,
                                       Function<? super S, CompletionStage<? extends T>> spawner,
                                       Collector<T, A, R> downstream);
// ...and the Stream + downstreamExecutor combination
```
## Execution model

-   **Bounded concurrency.** Values are pulled from the source lazily, at most `chunkSize` at a time; the next chunk is not pulled until the current one completes. At any moment at most `chunkSize` spawned tasks are in flight — this is the library's built-in backpressure.
-   **Ordered collection.** Chunks are processed sequentially and results within a chunk keep their positional order, so ordered collectors (`toList()`, `joining()`, `toMap()`, ...) produce results in the source encounter order. Each chunk is accumulated into a fresh container and merged via the collector's `combiner` — therefore the combiner must be correctly implemented (all standard `Collectors` are).
-   **Empty sources.** An empty input completes successfully with the collector's empty result (`supplier()` followed by `finisher()`).
-   **Failure and cancellation.** If a spawned task fails, the whole operation fails and the remaining in-flight tasks of the current chunk are cancelled. Cancelling the returned promise stops the loop — no further chunks are started.

## Example 1 — bounded parallel fetch

Fetch user profiles with at most 10 concurrent requests:
```java
List<Long> userIds = ...;

Promise<List<Profile>> profiles = Promises.chunked(
    userIds,
    10,                               // at most 10 concurrent requests
    id -> api.fetchProfile(id),       // S -> CompletionStage<T>
    Collectors.toList()               // downstream collector
);

profiles.thenAccept(list -> render(list));
```
## Example 2 — collecting into a map

Any collector works; collisions and merge semantics follow the collector itself:
```java
Promise<Map<Long, Profile>> profileMap = Promises.chunked(
    userIds,
    50,
    id -> api.fetchProfile(id),
    Collectors.toMap(Profile::id, Function.identity())
);
```
## Example 3 — Stream source with automatic closing

`Stream` sources are consumed lazily and **closed automatically** when the processing completes (successfully or not) — do not close them yourself:
```java
// The stream over a large DB result set is closed by chunked(...) when done
Promise<Statistics> stats = Promises.chunked(
    orderRepository.streamAll(),
    100,
    order -> enrichAsync(order),
    Statistics.collector()
);
```
## Example 4 — offloading accumulation

If accumulation is expensive or must run on a particular thread, pass a downstream executor. The collector's supplier/accumulator/combiner/finisher then run on that executor instead of the threads completing the spawned tasks (e.g., your HTTP I/O threads):
```java
Promise<Report> report = Promises.chunked(
    transactions,
    25,
    tx -> validateAsync(tx),
    Report.collector(),
    accumulationExecutor
);
```
## Blocking Consumption: `AsyncCompletions`

Every combinator discussed so far (`all`, `any`, `chunked`, `forEach`, `loop`) is **non-blocking**: it accepts `CompletionStage`s and returns a `Promise`, keeping your code fully asynchronous. `AsyncCompletions` is the deliberate exception — a **blocking, pull-based bridge** from the asynchronous world into synchronous code. 

It turns a sequence of stages into a plain `Iterator<T>` or `Stream<T>` whose `next()` blocks the calling thread until the next result settles. Reach for it when asynchronous producers must feed synchronous consumers: a legacy `for` loop, a transactional boundary, or a sequential `Stream` pipeline.

```java
static <T> Iterator<T> iterate(
  Iterable<? extends CompletionStage<? extends T>> pendingPromises, int chunkSize); 
static <T> Stream<T> stream(
  Iterable<? extends CompletionStage<? extends T>> pendingPromises, int chunkSize); 
static <T> Stream<T> stream(
  Iterable<? extends CompletionStage<? extends T>> pendingPromises, 
  int chunkSize, 
  AsyncCompletions.Cancel cancelOption); // ...plus Stream-based overloads for both families
```

**Key characteristics**

* **Blocking `next()`.** `hasNext()` never blocks (it may enlist more stages), while `next()` blocks until a result settles. **Never drive this class from an event-loop, virtual thread pool, or otherwise non-blocking thread.**
* **Completion order.** Results arrive in the order stages *complete*, not in source order.
* **Bounded concurrency & backpressure.** At most `chunkSize` stages are enlisted at once; pending stages wait for free slots. Note that with `chunkSize > 0` the completion callbacks themselves may briefly block on enqueueing while the consumer is slow, providing natural backpressure to fast producers.
* **Fail-fast errors.** An exceptionally completed stage throws from `next()` the moment it is consumed.
* **Cancellation.** Closing the sequence cancels outstanding work according to the `Cancel` policy: `NONE`, `ENLISTED` (the default for streams), or `ALL`. Streams support try-with-resources.
* **Single consumer.** Drive the iteration from exactly one thread.

**Example — bounded in-flight requests with blocking consumption**

```java
List<CompletionStage<HttpResponse>> requests = urls.stream()
        .map(httpClient::fetchAsync)
        .collect(Collectors.toList());

// At most 16 requests in flight; results processed as they complete
try (Stream<HttpResponse> responses = AsyncCompletions.stream(requests, 16)) {
    responses.forEach(response -> store(response));
} // close() cancels whatever is still in flight (Cancel.ENLISTED)
```
**Example — early termination cancels the rest**
```java
try (Stream<Record> records = AsyncCompletions.stream(pendingPages, 4, 
                                                      AsyncCompletions.Cancel.ALL)) {
    Optional<Record> match = 
    records.filter(Record::isRelevant)
           .findFirst(); // short-circuits the pipeline
    // closing the stream cancels all in-flight AND not-yet-started pages (Cancel.ALL)
}
```
### Summary: Choosing between the combinators

| Method | Consumption | Concurrency | Result order | Result / errors | Use when... |
|---|---|---|---|---|---|
| `Promises.all(...)` | non-blocking `Promise` | unbounded | source order | list/map of results; fails on first error | the number of tasks is small and known |
| `Promises.chunked(...)` | non-blocking `Promise` | bounded by `chunkSize` | source order | any `Collector` result | large sources; the backend must not be overwhelmed; results must be collected |
| `Promises.forEach(...)` | non-blocking `Promise` | strictly 1 | source order | `Void`, with early exit | order-sensitive side effects or early termination |
| `AsyncCompletions.stream(...)` / `iterate(...)` | **blocking** `Iterator` / `Stream` | bounded by `chunkSize` | **completion order** | pull-based; throws on the first consumed error | synchronous consumers (loops, stream pipelines) must process asynchronous results |

**Rule of thumb:** stay with the `Promise`-based combinators whenever possible — they never occupy a thread while waiting. Use `AsyncCompletions` only at the boundary where asynchronous results must re-enter the blocking world.


# 10. Context variables & contextual Promises

Ah, those dreaded `ThreadLocal`s we all hate, love to hate, but, nevertheless, use everywhere. It's quite common to store some contextual data (like the authenticated user and current locale) inside thread-local variables. Sometimes it's custom code, sometimes it's from a third-party library we can't alter. 

Typically, we spawn asynchronous code from a thread with well-known characteristics, like an HTTP request thread. Here we can easily access contextual information. However, using thread-local variables from an asynchronous code block is hard because it's impossible to predict which thread from the pool will execute the code. It's necessary to capture the context of one thread and propagate it to the threads executing the asynchronous code. Furthermore, with the advent of Virtual Threads in modern Java, traditional `ThreadLocal`s can cause memory bloat, making immutable scoped values (`ScopedValue`) a much better choice.

To solve this issue, Tascalate Concurrent provides the `ContextVar` API. It serves as a unified, multi-release abstraction over context storage: it uses `ThreadLocal` on JDK 8–24 and `ScopedValue` on JDK 25+, while also allowing you to wrap custom context stores.

## 1. Defining Context Variables

You can define context variables in several ways depending on your needs:

### Portable Variables (Recommended)
Uses `ThreadLocal` on JDK 8-24, and `ScopedValue` on JDK 25+. This is the default choice for new code.
```java
static final ContextVar<Principal> CURRENT_PRINCIPAL = ContextVar.newInstance("principal");
static final ContextVar<Locale> CURRENT_LOCALE = ContextVar.newInstance("locale");
```
### Wrapping Existing ThreadLocals

If you need to integrate with a legacy third-party library that exposes a raw `ThreadLocal`:
```java
static final ThreadLocal<String> LEGACY_TRACE_ID = new ThreadLocal<>();
static final ContextVar<String> TRACE_ID = ThreadLocalVar.of("traceId", LEGACY_TRACE_ID);
```
### Wrapping Custom Context Stores (e.g., Spring)

Sometimes context isn't stored in a simple `ThreadLocal`, but rather in a framework-specific registry. For example, Spring Web stores request-scoped attributes in `RequestContextHolder`. You can adapt any custom context store using `CustomContextVar.define(...)` by providing a reader, a writer, and a cleanup action:
```java
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestAttributes;

static final ContextVar<RequestAttributes> SPRING_REQUEST_ATTRS = CustomContextVar.define(
    "springRequestAttributes",
    () -> RequestContextHolder.getRequestAttributes(),            // Reader
    attrs -> RequestContextHolder.setRequestAttributes(attrs),    // Writer
    () -> RequestContextHolder.resetRequestAttributes()           // Cleanup/Eraser
);
```

## 2. Propagation Modes: Strict vs. Optimized

When a context variable is applied to a block of code, what happens to the _existing_ (ambient) value already present on the current thread? Tascalate Concurrent offers two modes to handle this:

-   **Strict Mode (Default):** Before applying the captured context, the library saves the ambient value currently on the thread. After the code block completes (even if an exception is thrown), the original ambient value is restored. This is essential for shared thread pools (like Tomcat worker threads) where a thread might process multiple requests and must not leak or lose context between them.
-   **Optimized Mode:** The library simply overwrites the thread's value with the captured snapshot, and clears it completely when the block finishes. It skips the save/restore step, offering a slight performance gain, but it is **destructive** to any pre-existing context on the thread. Use this only on dedicated, clean worker threads that you know carry no ambient state.

You can switch modes on individual variables or on an entire `ContextRelay`:
```java
ContextRelay<?> optimizedRelay = ContextVar.relay(MY_VAR1, MY_VAR2, MY_VAR3).optimized();
```
_(Note: On JDK 25+, `ScopedValue` bindings are inherently immutable and automatically restoring, so `strict()` and `optimized()` are effectively identical and carry no performance difference.)_

## 3. Synchronous Execution

For synchronous blocks, you can bind values to context variables using a fluent chain. The context is applied for the duration of the block and automatically restored (or cleared) afterward:
```java
ContextVar.bind(MyService.CURRENT_PRINCIPAL, principal)
          .bind(MyService.CURRENT_LOCALE, Locale.US)
          .bind(MyService.SPRING_REQUEST_ATTRS, requestAttributes)
          .run(() -> {
              // Context is available here
              processRequest();
          });
```
## 4. Asynchronous Execution & Contextual Promises

To propagate context across thread boundaries (e.g., to thread pools or asynchronous `Promise` chains), you create a `ContextRelay`.

There are two main strategies for promises:

-   **Static capture (`contextualPromises()`)**: The context snapshot is taken _once_ when the decorator is created. Every callback in the chain uses that same snapshot.
-   **Dynamic resolution (`dynamicContextualPromises()`)**: The context snapshot is taken _each time a callback is registered_ on the promise chain. This allows context to "flow" and update as the chain progresses through different threads.

#### Example: Context-Aware Promises
```java
// [1] Define a ContextRelay holding the variables you want to propagate.
// This is immutable, thread-safe, and may be shared globally.
ContextRelay<?> relay = ContextVar.relay(
    MyService.CURRENT_PRINCIPAL, 
    MyService.CURRENT_LOCALE,
    MyService.TRACE_ID
);

// [2] Create a promise decorator function. 
// We use dynamicContextualPromises() so that context is captured when each stage is chained.
Function<Promise<String>, Promise<String>> contextualPromise = relay.dynamicContextualPromises();

// [3] Apply the decorator to your Promise chain.
// Assuming you are in an HTTP request thread where the context variables are already bound:
Promise<Void> httpRequest = 
    CompletableTask.supplyAsync(() -> getDownloadUrlTemplate(), myExecutor)
                   .as(contextualPromise) // <--- HERE the context-aware conversion is applied
                   .thenApply(url -> 
                       url + 
                       "?user=" + MyService.CURRENT_PRINCIPAL.get().getName() + 
                       "&locale=" + MyService.CURRENT_LOCALE.get().toString()   
                   )
                   .thenAccept(url -> {
                       log.info("Get data for user " + MyService.CURRENT_PRINCIPAL.get().getName());
                       executeHttpRequest(url);
                   });
```
In the example above, after applying `.as(contextualPromise)`, all derived promises (i.e., code blocks in `thenApply` / `thenAccept`) will have access to the contextual information captured from the originating thread even that task submitted to `supplyAsync` is not contextual. _(Note the use of `.get()` to read the `ContextVar` value inside the asynchronous blocks)._

## 5. Undecorating Promises

Copying contextual variables and applying them to every callback carries a slight performance penalty. If you reach a point in your asynchronous chain where the context is no longer needed, you can stop the propagation by using `Promise.unwrap()` or `Promise.raw()` to undecorate the derived promises (as with any other decorator in Tascalate Concurrent):
```java
Promise<Void> afterHttpRequest =
    httpRequest.unwrap() // OR raw()
               .thenRun(() -> {
                   // MyService.CURRENT_PRINCIPAL, MyService.CURRENT_LOCALE are NOT available here
                   cleanupResources();
               });
```
## 6. Contextual Executors

If you prefer to contextualize the executor itself rather than individual promises, you can wrap any `Executor` or `ExecutorService`. The context will be captured dynamically at the exact moment a task is submitted:
```java
ExecutorService contextualExecutor = relay.contextual(myExecutor);

contextualExecutor.submit(() -> {
    // Context is automatically applied here based on the submitting thread's state
    processInBackground();
});
```

# 11. Polling and asynchronous retry functionality

Once you depart from pure algebraic calculations into the unreliable terrain of I/O-related functionality, you have to deal with failures. Network outages, insufficient disk space, overloaded third-party servers, exhausted database connection pools — these and many similar infrastructure failures are what applications have to cope with flawlessly. Many of the aforementioned issues are temporary by nature, so it makes sense to retry after a small delay and keep fingers crossed that this time everything will run smoothly. This is the primary use case for the retry functionality — or better yet, the *asynchronous* retry functionality, since we want our applications to be as scalable as possible.

Another related area is polling functionality. Unlike infrastructure failures, which are sporadic, polling is built into certain asynchronous protocol communications. Say an application sends an HTTP request to generate a report and then waits for a known file on an FTP server. There is no "asynchronous reply" expected from the third-party server, and the application has to poll periodically until the file becomes available.

Both use cases are fully supported by the Tascalate Concurrent library. The library provides an API that is both unobtrusive and rich enough for a wide range of tasks. The following `retry*` methods are available in the `Promises` class:

```java
// Void-returning operations (context-free and contextual)
static Promise<Void> retry(Runnable codeBlock, Executor executor, 
                           RetryPolicy<? super Void> retryPolicy);
static Promise<Void> retry(RetryRunnable codeBlock, Executor executor, 
                           RetryPolicy<? super Void> retryPolicy);

// Operations with a result (context-free and contextual)
static <T> Promise<T> retry(Callable<T> codeBlock, Executor executor, 
                            RetryPolicy<? super T> retryPolicy);
static <T extends C, C> Promise<T> retry(RetryCallable<T, C> codeBlock, 
                                         Executor executor, 
                                         RetryPolicy<? super C> retryPolicy);

// Operations returning Optional — an empty Optional may trigger a retry
static <T> Promise<T> retryOptional(Callable<Optional<T>> codeBlock, Executor executor, 
                                    RetryPolicy<? super T> retryPolicy);
static <T extends C, C> Promise<T> retryOptional(RetryCallable<Optional<T>, C> codeBlock, 
                                                 Executor executor, 
                                                 RetryPolicy<? super C> retryPolicy);

// Operations that are asynchronous by themselves — no explicit Executor needed
static <T> Promise<T> retryFuture(Callable<? extends CompletionStage<T>> invoker, 
                                  RetryPolicy<? super T> retryPolicy);
static <T extends C, C> Promise<T> retryFuture(RetryCallable<? extends CompletionStage<T>, C> invoker, 
                                               RetryPolicy<? super C> retryPolicy);
```
All the methods of the `retry` family share the same pattern. First, there is a block of code that is executed on every attempt. It could be either a full block of synchronous code (`retry` and `retryOptional`) or a factory that returns a `CompletionStage<T>` from a third-party API, like an async HTTP library (`retryFuture`). Next, if we retry a custom synchronous code block, it's necessary to provide an `Executor` it should run on. For `retryFuture` there is no explicit `Executor`: it's up to the third-party library to provide a scalable and robust default asynchronous executor for the returned `CompletionStage`. Finally, a `RetryPolicy` must be specified. It answers a lot of customization questions:

1.  How many attempts should be made?
2.  What is the time interval between attempts? Should it be fixed or dynamic?
3.  What is the timeout before a single attempt is considered "hung"? Should it be dynamic?
4.  What exceptions are retryable and what are not?
5.  What result is expected to be valid? Is a `null` result valid? Is any non-`null` result valid, or should some properties of the returned object be examined?

All in all, `RetryPolicy` provides an API with customizations for every imaginable use case. But before discussing it, it's necessary to explain the difference within each pair of methods. Why are there overloads with `Runnable` vs `RetryRunnable` and `Callable` vs `RetryCallable`? The reason is the following:

1.  Context-less retriable operations are captured as `Runnable` or `Callable` lambdas — they behave the same on every iteration and hence do not need a context.
2.  Methods with `RetryRunnable` and `RetryCallable` are _contextual_: they may dynamically alter their behavior for the given iteration depending on the context passed.

## RetryContext — what the operation knows about the loop

The contextual variants receive an immutable `RetryContext<T>` before every attempt:
```java
public final class RetryContext<T> {
    public int getRetryCount();          // 0 before the first attempt, 1 before the first retry, ...
    public Duration getLastCallDuration(); // how long the previous attempt ran
    public T getLastResult();            // previous result, if it was rejected by the policy (else null)
    public Throwable getLastError();     // previous error, if the attempt threw (else null)
}
```
Before the very first attempt the retry count is zero and both the last result and the last error are `null`. On subsequent attempts the context carries the outcome of the _previous_ attempt — the result and the error are mutually exclusive. This allows adaptive strategies: change the request payload after a partial result, back off harder after repeated errors of the same kind, or give up when attempts become too slow:
```java
Promise<RowSet> rows = Promises.retry(ctx -> {
    // Widen the query window on every retry after a timeout
    int window = 100 * (ctx.getRetryCount() + 1);
    return dao.fetchRows(window);
}, dbExecutor, policy);
```
## RetryPolicy — attempts, delays, timeouts, exceptions, results

`RetryPolicy` is immutable and fluent — every mutator returns a new instance, so a configured policy is thread-safe and shareable:
```java
RetryPolicy<PaymentResult> policy = new RetryPolicy<PaymentResult>()
    .withMaxRetries(5)                                  // up to 6 attempts in total
    .retryOn(IOException.class, TimeoutException.class) // only these are retryable...
    .abortOn(AuthenticationException.class)             // ...and never these
    .rejectNullResult()                                 // null result => retry
    .withBackoff(DelayPolicy.fixedInterval(1000)
                            .withFirstRetryNoDelay())   // wait 1s between retries
    .withTimeout(DelayPolicy.fixedInterval(10_000));    // hang detection per attempt

Promise<PaymentResult> payment = Promises.retry(() -> gateway.charge(order), httpExecutor, policy);
```
The essential semantics to keep in mind:

-   **Attempt counting.**  `withMaxRetries(n)` counts retries _after_ the initial attempt — up to `n + 1` attempts in total. `retryOnce()` means a single attempt with no retries; `retryInfinitely()` removes the limit entirely.
-   **Nothing is retryable by default.** A fresh policy has empty retry rules, so exceptional attempts are _not_ retried until you call `retryOn(...)` / `retryIf(...)`. The ready-made `RetryPolicy.DEFAULT` retries infinitely on any `Exception`.
-   **Abort beats retry.**  `abortOn(...)` / `abortIf(...)` rules are checked first and stop the loop immediately.
-   **Result validation.** By default any result — including `null` — is valid. Use `rejectNullResult()` or `withResultValidator(predicate)` to treat an unsatisfactory result as a retryable failure; the rejected result remains visible via `RetryContext.getLastResult()`.
-   **Backoff and timeout are schedules, not constants.** Both are defined by a `DelayPolicy`, which receives the current `RetryContext` — so delays and timeouts may adapt to the attempt number, the previous error, or the duration of the previous call. The `long`-based constructors install a fixed interval with _no delay before the first attempt_.

## `retryOptional` — when "no result yet" is not a failure

`retryOptional` unwraps the `Optional` returned by the operation: an empty `Optional` is passed to the policy as a `null` result. Combined with `rejectNullResult()` this is exactly the "not ready yet — try again" semantics.

## `retryFuture` — retrying genuinely asynchronous operations

`retryFuture` accepts a _factory_ of `CompletionStage`s. A fresh stage is produced on every attempt; both an exception thrown by the factory and an exceptional completion of the produced stage are retryable subject to the policy. The per-attempt timeout applies to the produced stage. Since the asynchronous machinery belongs to the third-party API, no `Executor` parameter is needed.

## When the policy gives up

If the budget is exhausted or an abort rule matches, the returned promise fails with a `RetryException` carrying the attempt count, the duration of the last attempt, and the last error.

## Polling — retry as a first-class citizen

Polling is expressed with the same tools: the polling operation returns `null` (or an empty `Optional`) while the awaited resource is unavailable, and the policy turns that into a retryable condition:
```java
RetryPolicy<Path> pollingPolicy = new RetryPolicy<Path>()
    .retryOn(IOException.class)
    .rejectNullResult()  // null = file not there yet => poll again
    .withMaxRetries(120) // give up after ~10 minutes
    .withBackoff(DelayPolicy.fixedInterval(5000));

Promise<Path> reportFile = Promises.retry(
    () -> ftp.findFile("/reports/" + reportId + ".pdf"),  // returns null until the file appears
    pollingExecutor,
    pollingPolicy);

reportFile.thenAccept(path -> deliver(path))
          .exceptionally(ex -> { 
              // RetryException after 120 polls — escalate to the operator
              return null; 
          });
```

Below is a complete example how retry functionality of the library might be used with AsyncHttpClient:
```java
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tascalate.concurrent.DelayPolicy;
import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Promises;
import net.tascalate.concurrent.RetryPolicy;
import net.tascalate.concurrent.var.ContextRelay;
import net.tascalate.concurrent.var.ContextVar;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * Example client demonstrating nested asynchronous retries:
 * <ol>
 *   <li><b>Inner Retry (Transport):</b> Handles network-level failures like timeouts or dropped connections.</li>
 *   <li><b>Outer Retry (Business Logic):</b> Handles application-level failures, such as a missing or invalid 
 *       XML payload in an otherwise successful HTTP 200 response.</li>
 * </ol>
 */
public class ProviderClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderClient.class);
    
    // Modernized ContextVar and ContextRelay setup
    private static final ContextVar<String> CURRENT_AGENCY = ContextVar.newInstance("agency");
    private static final ContextRelay<?> CONTEXT_RELAY = ContextVar.relay(CURRENT_AGENCY);

    private final AsyncHttpClient httpClient;
    private final ProviderSettings settings;
    private final RequestLogger requestLogger;

    public ProviderClient(AsyncHttpClient httpClient, ProviderSettings settings, RequestLogger requestLogger) {
        this.httpClient = httpClient;
        this.settings = settings;
        this.requestLogger = requestLogger;
    }

    // ======================================================================
    // Retry Policies
    // ======================================================================

    /**
     * Base policy for HTTP transport errors.
     */
    private static <T> RetryPolicy<T> httpRetryPolicy() {
        return new RetryPolicy<T>()
            .withMaxRetries(5)
            .rejectNullResult()
            .withoutTimeout()
            .withBackoff(DelayPolicy.fixedInterval(Duration.ofSeconds(2))
                                    .withUniformJitter()
                                    .withFirstRetryNoDelay())
            .retryOn(TimeoutException.class);
    }

    private static final RetryPolicy<Response> RETRY_HTTP_REQUEST = httpRetryPolicy();
    
    private static final RetryPolicy<ProviderObject> RETRY_MISSING_RESULT = new RetryPolicy<ProviderObject>()
        .withMaxRetries(5)
        .withoutTimeout()
        .withBackoff(DelayPolicy.fixedInterval(Duration.ofSeconds(1))
                                .withProportionalJitter(2)
                                .withFirstRetryNoDelay())
        .retryOn(NoSuchElementException.class);

    // ======================================================================
    // HTTP Execution Utilities
    // ======================================================================

    public static Promise<Response> execute(BoundRequestBuilder request) {
        return Promises.from(request.execute().toCompletableFuture());
    }

    public static Promise<Response> execute(BoundRequestBuilder request, RetryPolicy<Response> retryPolicy) {
        return Promises.retryFuture(() -> execute(request), retryPolicy);
    }

    // ======================================================================
    // Core Business Logic
    // ======================================================================

    /**
     * Executes a Provider request asynchronously with nested retry policies and context propagation.
     */
    public <E extends ProviderObject> Promise<E> invokeSingleAsync(String requestPayload, Class<? extends E> clazz) {
        @SuppressWarnings("unchecked")
        RetryPolicy<E> retryMissingResult = (RetryPolicy<E>) RETRY_MISSING_RESULT;
        
        return Promises.retryFuture(() -> 
            
            // 1. INNER RETRY: Execute HTTP request with transport-level retry policy
            execute(httpRequest(requestPayload), RETRY_HTTP_REQUEST)
            .dependent()
            .thenApply(r -> {
                requestLogger.record(requestPayload, r);
                return r;
            }, true)
            .thenApply(r -> {
                // 2. Parse and validate the response
                try (XmlDataStream<? extends E> items = new XmlDataStream<>(
                        r.getResponseBodyAsStream(), 
                        ProviderMarshallingContext.createUnmarshaller(), 
                        ProviderMetadata.get(clazz).tag(), 
                        clazz)) {
                    
                    E result = items.stream().findFirst().orElse(null);
                    
                    if (null == result) {
                        // Throwing this triggers the OUTER retry policy
                        throw new NoSuchElementException("Missing expected result after Provider request:\n" + requestPayload);
                    } else {
                        if (result.isValid()) {
                            return result;
                        } else {
                            result.failed(requestPayload);
                            return null; 
                        }
                    }
                } catch (ProviderIOException ex) {
                    requestLogger.record(requestPayload, ex, false);
                    log.error("Provider gateway error while executing single async query: {} \nGA = {}, Office = {}", 
                              ex.getMessage(), CURRENT_AGENCY.get(), settings.office());
                    return sneakyThrow(ex);
                } catch (JAXBException | IOException ex) {
                    requestLogger.record(requestPayload, ex, false);
                    log.error("Error executing single async query: ", ex);
                    return sneakyThrow(ex);
                }
            }, true),
            
            // 3. OUTER RETRY: Apply business-logic retry policy
            retryMissingResult
            
        )
        // 4. CONTEXT PROPAGATION: Ensure CURRENT_AGENCY is available in all async callbacks
        .as(CONTEXT_RELAY.dynamicContextualPromises()); 
    }

    private BoundRequestBuilder httpRequest(String requestPayload) {
        return httpClient.preparePost(settings.endpoint())
                         .setBody(requestPayload);
    }

    // ======================================================================
    // Helper / Mock Interfaces for Compilation Context
    // ======================================================================

    @SuppressWarnings("unchecked")
    private static <T extends Throwable, R> R sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }

    interface ProviderObject { 
        boolean isValid(); 
        void failed(String req); 
    }
    
    interface ProviderSettings { 
        String endpoint(); 
        String office(); 
    }
    
    interface RequestLogger { 
        void record(String req, Response r); 
        void record(String req, Exception e, boolean b); 
    }
    
    interface ProviderMarshallingContext { 
        static Object createUnmarshaller() { return null; } 
    }
    
    interface ProviderMetadata { 
        static <E> ProviderMetadata get(Class<E> c) { return null; } 
        String tag(); 
    }
    
    static class XmlDataStream<T> implements AutoCloseable { 
        XmlDataStream(Object... args) {} 
        public void close() {} 
        public Stream<T> stream() { return Stream.empty(); }
    }
    
    static class ProviderIOException extends IOException {}
}
```


# 12. Asynchronous Try-With-Resources

In synchronous Java, the `try-with-resources` statement guarantees that `AutoCloseable` resources (like streams, connections, or locks) are released immediately after use, even if an exception occurs. 

In asynchronous pipelines, standard `try-with-resources` fails because the `try` block exits immediately, long before the asynchronous operation finishes. If you close the resource synchronously at the end of the block, you destroy it while the async operation is still using it. 

Tascalate Concurrent solves this with the `Promises.tryApply`, `tryCompose`, `tryApplyEx`, and `tryComposeEx` methods. They defer the closing of the resource until the asynchronous pipeline completes.

## The `AsyncCloseable` Interface

Some modern resources cannot be closed synchronously without blocking a thread. For example, releasing a distributed Redis lock or flushing an asynchronous network buffer requires I/O. For these, Tascalate Concurrent provides `AsyncCloseable`:

```java
@FunctionalInterface
public interface AsyncCloseable {
    CompletionStage<Void> close();
}
```
### The `try*` Methods Matrix

Depending on whether your business logic is synchronous/asynchronous, and whether your resource closes synchronously/asynchronously, choose the appropriate method:


| Method | Business Logic | Resource Closing | Use Case |
| :--- | :--- | :--- | :--- |
| **`tryApply`** | Synchronous (`Function`) | Synchronous (`AutoCloseable`) | Parsing a file synchronously after async fetch. |
| **`tryCompose`** | Asynchronous (`Function<..., Stage>`) | Synchronous (`AutoCloseable`) | Querying a DB connection asynchronously. |
| **`tryApplyEx`** | Synchronous (`Function`) | Asynchronous (`AsyncCloseable`) | Reading a cached value, then releasing a distributed lock asynchronously. |
| **`tryComposeEx`**| Asynchronous (`Function<..., Stage>`) | Asynchronous (`AsyncCloseable`) | Fully async I/O operations requiring async cleanup. |

## Example 1: Async Operation, Sync Close (`tryCompose`)

Imagine an asynchronous database connection pool. You acquire the connection asynchronously, perform an async query, and the connection must be returned to the pool (synchronously) only _after_ the query finishes.
```java
Promise<User> userPromise = Promises.tryCompose(
    connectionPool.acquireAsync(), // Returns Promise<Connection>
    connection -> {
        // Asynchronous DB query
        return dbClient.executeQueryAsync(connection, "SELECT * FROM users WHERE id = 1");
    }
);

// connection.close() is automatically called AFTER executeQueryAsync completes, 
// regardless of whether the query succeeded or failed.
```
## Example 2: Fully Asynchronous (`tryComposeEx`)

Suppose you are interacting with a distributed lock that requires a network call to acquire and another network call to release.
```java
public class DistributedLock implements AsyncCloseable {
    private final String lockId;
    
    @Override
    public CompletionStage<Void> close() {
        // Release the lock over the network asynchronously
        return redisClient.unlockAsync(lockId);
    }
}

Promise<Report> report = Promises.tryComposeEx(
    lockService.acquireLockAsync("critical-section"), // Returns Promise<DistributedLock>
    lock -> {
        // Do some async work while holding the lock
        return generateReportAsync();
    }
);

// The lock is released asynchronously ONLY AFTER generateReportAsync() finishes.
```
## Cancellation Semantics

The promises returned by the `try*` methods are fully cancellable. If you cancel the resulting promise (or if a downstream stage cancels it):

1.  The underlying asynchronous operation (`fn`) is cancelled.
2.  **The resource is still closed.** The library ensures that `close()` (or `close().toCompletableFuture()`) is invoked to prevent resource leaks, even during cancellation.


# 13. Asynchronous locks

Standard `java.util.concurrent` synchronizers — `Lock`, `Semaphore`, `CountDownLatch` — were designed for blocking code: a thread that cannot acquire a lock is *parked* until it can. In a non-blocking, promise-based application this is exactly what you must not do: a parked thread is a wasted thread (and on an event-loop thread, a parked loop is a dead application).

The `net.tascalate.concurrent.locks` package provides asynchronous analogs of the standard synchronizers. Their core principle is simple:

> **Acquisition never blocks.** Instead of parking the calling thread, an acquisition returns either an immediate result or a `Promise` that completes when the lock/permits are granted. Waiting parties are promises in a queue, not threads in a waiting state.

## 1. The token pattern

Instead of the error-prone paired `lock()` / `unlock()` calls that must be matched manually, a successful acquisition yields a **token** — an object that owns the release responsibility:

* `tryAcquire()` performs an immediate, non-waiting attempt and returns an `Token` — populated on success, `null` when the lock is held;
* `acquire()` returns a `Promise<Token>` that completes when the lock is granted (immediately if it is free, or later when preceding holders release it).

Tokens implement `AutoCloseable`, so they integrate directly with the library's asynchronous try-with-resources combinators (Section 12). Releasing is guaranteed even if the protected action fails or is cancelled:

```java
AsyncLock lock = AsyncLock.create();

// Recommended: acquire, run, release automatically
Promise<Result> r1 = lock.guard(() -> criticalAsyncWork());

// Equivalent explicit form
Promise<Result> r2 = Promises.tryCompose(lock.acquire(), $ -> criticalAsyncWork());
```
Both forms call `token.close()` (i.e., `token.release()`) exactly once, after the protected stage completes — successfully, exceptionally, or via cancellation.

## 2. `AsyncLock` — mutual exclusion

`AsyncLock` is the asynchronous analogue of `java.util.concurrent.locks.Lock`. At most one party holds it at any moment, and waiting acquirers are served **in arrival order** (FIFO).
```java
AsyncLock cacheLock = AsyncLock.create();

Promise<CacheEntry> refreshed = cacheLock.guard(() ->
    cache.getAsync(key).thenCompose(entry ->
        entry != null
            ? Promises.success(entry)
            : loadFromOriginAsync(key).thenApply(loaded -> cache.putAsync(key, loaded))
    )
);
```
If the lock must be released from a _different_ scope than the acquisition (e.g., the token is passed around), hold the token explicitly and call `token.release()` once — releasing a token more than once is a harmless no-op.

## 3. `AsyncSemaphoreLock` — counting semaphore with tokens

`AsyncSemaphoreLock` is the asynchronous analogue of `java.util.concurrent.Semaphore`, in token style. It manages a fixed number of permits; acquiring a token consumes permits, and releasing the token returns exactly the permits it holds (`token.permits()`).
```java
// At most 10 concurrent database queries; fair ordering
AsyncSemaphoreLock dbPermits = AsyncSemaphoreLock.create(10, true);

Promise<RowSet> rows = dbPermits.guard(() -> dao.queryAsync(sql));
```
Multi-permit acquisitions and inspection methods are available:
```java
Promise<AsyncSemaphoreLock.Token> bulk = dbPermits.acquire(3); // wait for 3 permits

long free    = dbPermits.availablePermits();   // permits available right now
int  waiting = dbPermits.getQueueLength();     // queued acquisition requests
AsyncSemaphoreLock.Token all = dbPermits.drainPermits(); // take whatever is free
```
## 4. `AsyncSemaphore` — counting semaphore, JDK style

`AsyncSemaphore` offers the same permit accounting with an API that mirrors the JDK `Semaphore` directly: acquisitions resolve to the _granted permit count_, and releasing is an explicit call made by you:
```java
AsyncSemaphore permits = AsyncSemaphore.create(10, true);

permits.acquire(3).thenCompose(granted ->
    doWorkAsync().whenComplete((r, e) -> permits.release(granted))
);
```
When in doubt, prefer the token-based `AsyncSemaphoreLock`: the token makes leaks structurally impossible.

**Choosing between the two semaphore flavors:**

| | `AsyncSemaphoreLock` (tokens) | `AsyncSemaphore` (permit counts) |
| :--- | :--- | :--- |
| Acquisition yields | `Promise<Token>` carrying its permits | `Promise<Long>` — the granted count |
| Release | `token.release()` — self-contained | explicit `release(n)` — caller's duty |
| Best for | permits guarding an async action — use with `guard(...)` / `tryCompose` | permits managed manually, or released from a different scope than the acquisition |

## 5. `AsyncCountDownLatch` — the latch _is_ the promise

`AsyncCountDownLatch` replaces the blocking `await()` of the JDK latch with subscription: the latch itself **is a `Promise<Long>`** that completes with the initial count when the count reaches zero. Like the JDK latch, it is one-shot and cannot be reset.
```java
// Start gate: release N workers simultaneously
AsyncCountDownLatch startGate = new AsyncCountDownLatch(1);
for (int i = 0; i < 5; i++) {
    startGate.thenRun(() -> workerAsync(i));  // no thread waits — just subscribed
}
...
startGate.countDown();                        // all five workers start

// Completion gate: fire when N async tasks finish
AsyncCountDownLatch allDone = new AsyncCountDownLatch(tasks.size());
tasks.forEach(task ->
    runAsync(task).whenComplete((r, e) -> allDone.countDown())
);
Promise<Void> report = allDone.dependent().thenAccept($ -> generateReport());
```
A latch created with a count of zero is already complete; `countDown(long delta)` decrements by the given amount, and the count never goes below zero.

## 6. Fairness and cancellation

-   **Fairness.** Semaphores accept a `fair` flag at creation. In fair mode permits are granted strictly in arrival order — barging is prevented because immediate acquisitions refuse to proceed while any waiter is queued. In unfair mode (and always for `AsyncLock`'s FIFO queue) the overhead of strict ordering is avoided where appropriate.
-   **Cancellation.** Pending acquisitions are ordinary promises and are fully cancellable: cancelling an acquisition removes the waiter from the queue, and any permits tentatively reserved for it are returned to the pool. No leaked permits, no stranded waiters.

## 7. Implementation notes

All implementations are lock-free: permit accounting uses compare-and-set operations, waiter queues are based on `ConcurrentLinkedQueue`, and release operations are guarded by idempotent release-once flags (double-release is a no-op). Instances are obtained through the static factories on the interfaces:
| Class | JDK analog | Acquisition yields | Created via |
| :--- | :--- | :--- | :--- |
| **`AsyncLock`** | `Lock` | `Promise<Token>` (exclusive) | `AsyncLock.create()` |
| **`AsyncSemaphoreLock`** | `Semaphore` | `Promise<Token>` carrying permits | `AsyncSemaphoreLock.create(permits, fair)` |
| **`AsyncSemaphore`** | `Semaphore` | `Promise<Long>` (granted count) | `AsyncSemaphore.create(permits, fair)` |
| **`AsyncCountDownLatch`** | `CountDownLatch` | the latch itself is a `Promise<Long>` | `new AsyncCountDownLatch(count)` |

# 14. Asynchronous I/O extensions

Standard Java NIO provides `AsynchronousChannel` implementations (`AsynchronousFileChannel`, `AsynchronousSocketChannel`, etc.) that operate using `CompletionHandler` callbacks. While non-blocking, this callback-driven API quickly leads to deeply nested, hard-to-maintain code ("callback hell") when composing multiple I/O operations.

The `net.tascalate.concurrent.io` package solves this by providing Promise-based wrappers around the standard NIO asynchronous channels. These wrappers adapt the callback-based methods to return `Promise`s, allowing you to use the fluent, composable API of `Promise` for all your asynchronous I/O needs.

## Core Interfaces
* **`AsyncChannel`**: A marker interface extending `java.nio.channels.AsynchronousChannel` that denotes a channel adapted for the Promise API.
* **`AsyncByteChannel`**: Extends `AsynchronousByteChannel` and `AsyncChannel`, overriding `read` and `write` to return `Promise<Integer>` representing the number of bytes transferred.

## Asynchronous File I/O
`AsyncFileChannel` wraps the standard `AsynchronousFileChannel`. It is opened using the familiar static `open` methods, requiring an `ExecutorService` to dispatch I/O completion events.

```java
ExecutorService ioExecutor = Executors.newFixedThreadPool(4);
Path filePath = Paths.get("/path/to/large/file.dat");

// Open the file channel
AsyncFileChannel fileChannel = AsyncFileChannel.open(filePath, ioExecutor, StandardOpenOption.READ);

// Read asynchronously using Promises
ByteBuffer buffer = ByteBuffer.allocate(1024);
Promise<Integer> bytesReadPromise = fileChannel.read(buffer, 0);

bytesReadPromise.thenAccept(bytesRead -> {
    System.out.println("Read " + bytesRead + " bytes");
    buffer.flip();
    // Process buffer...
}).exceptionally(ex -> {
    System.err.println("I/O failed: " + ex.getMessage());
    return null;
});
```
**File Locking**  `AsyncFileChannel` also provides Promise-based methods for acquiring file locks, avoiding the blocking `lock()` methods of the standard API:
```java
Promise<FileLock> lockPromise = fileChannel.lock(true); // shared lock
lockPromise.thenAccept(lock -> {
    // Lock acquired, safe to read/write
    lock.release(); // Release the lock
});
```
## Asynchronous Network I/O (Client)

`AsyncSocketChannel` wraps `AsynchronousSocketChannel`, enabling fluent TCP client operations. It is typically opened via an `AsynchronousChannelGroup`.
```java
AsynchronousChannelGroup group = AsynchronousChannelGroup.withThreadPool(ioExecutor);
AsyncSocketChannel socket = AsyncSocketChannel.open(group);

// Connect, then write, then read, all chained fluently
SocketAddress remote = new InetSocketAddress("example.com", 80);
ByteBuffer requestBuffer = ByteBuffer.wrap("GET / HTTP/1.1\r\nHost: example.com\r\n\r\n".getBytes());
ByteBuffer responseBuffer = ByteBuffer.allocate(4096);

socket.connect(remote)
      .thenCompose(v -> socket.write(requestBuffer))
      .thenCompose(bytesWritten -> socket.read(responseBuffer))
      .thenAccept(bytesRead -> {
          responseBuffer.flip();
          System.out.println("Received " + bytesRead + " bytes from server");
      })
      .exceptionally(ex -> {
          ex.printStackTrace();
          return null;
      });
```
## Asynchronous Network I/O (Server)

`AsyncServerSocketChannel` wraps `AsynchronousServerSocketChannel` for building non-blocking TCP servers. The `accept()` method returns a `Promise<AsyncSocketChannel>`, making it easy to set up continuous accept loops without callback nesting.
```java
AsynchronousChannelGroup group = AsynchronousChannelGroup.withThreadPool(ioExecutor);
AsyncServerSocketChannel server = AsyncServerSocketChannel.open(group);

// Bind to port 8080
server.bind(new InetSocketAddress(8080));

// Recursive accept loop using Promises
Runnable acceptLoop = new Runnable() {
    @Override
    public void run() {
        server.accept().thenAccept(clientSocket -> {
            System.out.println("Accepted connection from: " + clientSocket.getRemoteAddress());
            
            // Handle the client socket (e.g., read/write)
            handleClient(clientSocket);
            
            // Continue accepting the next connection
            this.run(); 
        }).exceptionally(ex -> {
            System.err.println("Accept failed: " + ex.getMessage());
            return null;
        });
    }
};
acceptLoop.run();
```
## Extensibility

If you need to create custom channel wrappers or adapt third-party asynchronous channels, the library provides abstract base classes:

-   `AbstractAsyncFileChannel<F>`
-   `AbstractAsyncSocketChannel<C>`
-   `AbstractAsyncServerSocketChannel<S, C>`

These classes handle the delegation to the underlying JDK channel and the boilerplate of bridging `CompletionHandler` to `Promise` (via the internal `AsyncResult` bridge), allowing you to focus purely on your custom channel logic while preserving the fluent Promise API.


# 15. Decorators for `CompletionStage` / `CompletableFuture` / `Promise`

The Tascalate Concurrent library heavily utilizes the **Decorator pattern** to add advanced, cross-cutting behaviors to asynchronous pipelines without altering the core `Promise` or `CompletionStage` implementations. This ensures that features like executor binding, context propagation, and interruptible I/O compose seamlessly with the standard fluent API.

All decorators in the `net.tascalate.concurrent.decorators` package implement the `Promise` interface (or `CompletionStage`). They delegate the actual computation to an underlying "delegate" while intercepting and transforming the arguments or results of composition methods.

## 1. Decorator Hierarchy Overview

The decorator architecture is organized into a layered hierarchy, progressing from basic `CompletionStage` wrapping up to fully-featured `Promise` and `DependentPromise` decorators. Understanding this hierarchy clarifies which decorator provides which capabilities:
```
AbstractCompletionStageDecorator       <-- Bridges standard CompletionStage composition
    ├── CompletionStageDecorator       <-- Generic concrete wrapper
    │       └── ExecutorBoundCompletionStage (Package-private executor binder)
    └── AbstractPromiseLikeDecorator   <-- Adds Promise return types + α-operator (origin tracing)
            └── AbstractFutureDecorator  <-- Adds Future blocking API (get, cancel, isDone)
                    ├── CompletableFutureDecorator
                    └── BlockingCompletionStageDecorator

AbstractPromiseDecorator               <-- Adds Promise lifecycle, timeouts, and filters
    ├── ExecutorBoundPromise           <-- Binds *Async methods to a default executor
    ├── ExtendedPromiseDecorator       <-- Provides wrapArgument(...) extension points
    │       └── CustomizablePromiseDecorator (Driven by a PromiseCustomizer)
    └── AbstractDependentPromiseDecorator <-- Preserves DependentPromise API (cancellation propagation)
            ├── ExecutorBoundDependentPromise
            └── ExtendedDependentPromiseDecorator
                    └── CustomizableDependentPromiseDecorator
```
**Key Layers Explained:**

1.  **`AbstractCompletionStageDecorator`**: The foundation. It intercepts all standard `CompletionStage` composition methods and wraps their results to maintain the decorator chain. the `Promise` contract.
2.  **`AbstractFutureDecorator`**: Adds blocking capabilities (`get()`, `isDone()`, `cancel()`) for delegates that also implement `java.util.concurrent.Future`.
3.  **`AbstractPromiseDecorator`**: The workhorse for standard `Promise` features, delegating timeouts, delays, and filtering while preserving the wrapper chain.
4.  **`Extended*Decorator` & `Customizable*Decorator`**: The most flexible layer. They expose `wrapArgument(...)` hooks, allowing you to inject custom logic (like context relay or I/O interruption) into every callback of the chain.

## 2. `DependentPromise`: Cancellation Propagation as a Decorator

As discussed in earlier sections, the standard `CompletionStage` API does not propagate cancellation upstream. The `DependentPromise` interface (implemented by `ConfigurableDependentPromise`) is fundamentally a decorator.

When you call `promise.dependent()`, it wraps the original promise, intercepting composition methods (like `thenApply` or `thenCombine`) to optionally register the upstream stages as cancellation targets. If the resulting chain is cancelled, the decorator ensures those registered origins are cancelled as well, preventing resource leaks.

## 3. Executor Binding (`asyncOn`)

A common pitfall in standard Java is that `*Async` methods without an explicit `Executor` argument silently fall back to `ForkJoinPool.commonPool()`, which is rarely desirable in enterprise applications.

The `ExecutorBoundPromise` and `ExecutorBoundDependentPromise` decorators solve this "executor leakage" problem. When you call `promise.defaultAsyncOn(myExecutor)` (or use `CompletableTask.asyncOn(myExecutor)`), the returned promise is wrapped in an executor-bound decorator.

-   Every subsequent `*Async` call (e.g., `thenApplyAsync`, `whenCompleteAsync`) that omits the executor parameter is automatically redirected to use `myExecutor`.
-   The decorator ensures this binding is preserved down the entire composition chain.
```java
Promise<String> p = CompletableTask.asyncOn(myExecutor)
    .thenApplyAsync(this::step1)      // Runs on myExecutor
    .thenComposeAsync(this::step2)    // Runs on myExecutor
    .whenCompleteAsync(this::step3);  // Runs on myExecutor
```
## 4. Context Propagation with `ContextRelay`

In modern applications, contextual data (like trace IDs, security principals, or tenant IDs) must flow across asynchronous thread boundaries. The `ContextRelay` class achieves this by returning a `CustomizablePromiseDecorator`.

When you apply a relay to a promise chain, the decorator intercepts every callback (e.g., `Function`, `Consumer`, `Runnable`) passed to composition methods. It wraps each callback so that, just before execution on the worker thread, the captured `ContextVar` snapshot is applied, and it is cleared immediately after.
```java
ContextVar<String> TRACE_ID = ContextVar.newInstance("trace-id");

Promise<String> result = originalPromise
    .as(TRACE_ID.relay().contextualPromises()) // Wraps all subsequent callbacks
    .thenApplyAsync(id -> processWithTrace(id)) // TRACE_ID is automatically restored here
    .thenAcceptAsync(res -> logResult(res));    // And here
```
## 5. Interruptible Blocking I/O (`BlockingIO`)

As detailed in the _"Wait! Why isn't my task cancelling?"_ section, standard blocking I/O ignores thread interruptions. The `BlockingIO` utility provides a `PromiseCustomizer` (`InterruptiblePromiseCustomizer`) that can be applied to an entire promise chain.

When you use `BlockingIO.interruptiblePromises()`, it returns a decorator that wraps every callback in the chain with the `BlockingIO` interruption context. If `Promise.cancel(true)` is called, the decorator ensures that any registered `AutoCloseable` resources (like `InputStream`s or `Socket`s) are forcefully closed, unblocking the I/O operation and allowing the cancellation to propagate cleanly.
```java
Promise<byte[]> data = fetchUrlAsync(url)
    .as(BlockingIO.interruptiblePromises()) // Makes the whole chain interruptible
    .thenApplyAsync(bytes -> parse(bytes))
    .thenAcceptAsync(result -> store(result));

// Later: data.cancel(true) will forcefully close any registered streams in the chain
```
## 6. Building Your Own Decorators

The decorator architecture is highly extensible. You can create custom decorators by implementing the `PromiseCustomizer` interface and applying it via `ExtendedPromiseDecorator` or `CustomizablePromiseDecorator`.

For example, to add execution time logging to every functional step in a chain:
```java
public class LoggingCustomizer implements PromiseCustomizer {
    @Override
    public <U, R> Function<U, R> wrapArgument(Function<U, R> original, boolean async, boolean isCompose) {
        return input -> {
            long start = System.nanoTime();
            try {
                return original.apply(input);
            } finally {
                System.out.println("Async step took " + (System.nanoTime() - start) + " ns");
            }
        };
    }
}

// Apply to a promise chain using the as() method
Promise<T> loggedPromise = originalPromise.as(p -> 
    new CustomizablePromiseDecorator<>(p, new LoggingCustomizer())
);
```
By leveraging these decorators, Tascalate Concurrent allows you to compose complex, production-ready asynchronous workflows with clean, declarative code, while keeping cross-cutting concerns neatly separated from your core business logic. To strip away these layers when needed, simply call `.unwrap()` (removes one layer) or `.raw()` (removes all layers) on the `Promise`.

## 7. Fluent Decorators via Extension Methods
While the `Promise.as(Function)` method provides a powerful way to apply decorators, chaining multiple decorators can sometimes feel verbose and disrupt the natural flow of the code:
```java
Promise<Data> result = fetchData()
.as(BlockingIO::interruptiblePromise)
.as(p -> TRACE_ID.relay().contextualPromise(p))
.as(p -> Promises.tryComposeEx(p,  this::processResource));
```
To make the API feel more natural and fluent, the Tascalate Concurrent library provides the `PromiseOperations` class. This class is specifically designed to be used with **Lombok's `@ExtensionMethod`** (or similar compiler plugins that support extension methods).

By registering `PromiseOperations` as an extension method provider, all its static methods become available as instance methods on any `Promise` or `DependentPromise`. This allows you to chain decorators seamlessly, as if they were native methods of the `Promise` interface:
```java
@ExtensionMethod({PromiseOperations.class})
public class MyService {
    
    public Promise<Data> fetchData() {
        return fetchRawData()
            .interruptible()                      // Applies BlockingIO decorator
            .contextual(TRACE_ID.relay())         // Applies ContextRelay decorator (static capture)
            .tryComposeEx(resource -> process(resource)); // Async try-with-resources
    }
}
```
### Available Extension Operations

The `PromiseOperations` class provides extension methods for several common scenarios:

-   **Context Propagation:**  `.contextual(relay)` and `.dynamicContextual(relay)` apply a `ContextRelay` to the promise chain, ensuring thread-local or scoped variables flow across asynchronous boundaries.
-   **Interruptible I/O:**  `.interruptible()` wraps the promise with the `BlockingIO` decorator, making legacy blocking operations responsive to `Promise.cancel(true)`.
-   **Asynchronous Try-With-Resources:**  `.tryApply(fn)`, `.tryCompose(fn)`, and their `Ex` (async close) variants automatically enlist the resource promise for cancellation, preventing resource leaks if the pipeline is aborted.
-   **Chunked Processing:**  `.chunkedItems(batchSize, spawner, collector)` and `.chunkedStream(...)` apply bounded-concurrency processing to a promise yielding an `Iterable` or `Stream`.
-   **Type Conversions:**  `.asStream()`, `.asOptional()`, `.asTry()`, `.lift()`, and `.drop()` allow seamless conversion between `Promise` and standard Java container types or monadic structures.
-   **Debugging:**  `.peek(consumer)` allows you to inspect or log the promise itself in the middle of a chain without altering its state or type.

Using `PromiseOperations` with Lombok not only reduces boilerplate but also makes the intent of your asynchronous pipelines immediately clear to anyone reading the code, providing a highly expressive, domain-specific language for reactive programming.

# 16. Promise-Aware Executors and Thread Builders

While the Tascalate Concurrent library works seamlessly with any standard `java.util.concurrent.Executor`, using standard executors means their `submit(...)` methods return blocking `Future<T>` objects. To fully leverage the non-blocking, composable `Promise` API without constantly wrapping results in `Promises.from(...)`, the library provides a suite of Promise-aware executor interfaces, implementations, and fluent thread builders.

## 1. `TaskExecutorService` and `ThreadPoolTaskExecutor`

`TaskExecutorService` is a specialization of the standard `ExecutorService` interface. The key difference is that its `submit(...)` methods return a `Promise<T>` instead of a `Future<T>`. This allows you to immediately chain asynchronous callbacks (`thenApply`, `thenCompose`, etc.) without blocking a thread to wait for the result.

`ThreadPoolTaskExecutor` is the primary concrete implementation. It extends the standard `java.util.concurrent.ThreadPoolExecutor` and overrides the internal `newTaskFor(...)` hooks to ensure that submitted tasks are wrapped in a `RunnablePromise` — a hybrid object that acts as both a `Runnable` (for the executor's worker threads) and a `Promise` (for the caller).

```java
TaskExecutorService executor = TaskExecutors.newFixedThreadPool(4);

// submit() returns a Promise<User>, not a Future<User>
Promise<User> userPromise = executor.submit(() -> fetchUserFromDb(123));

userPromise.thenAccept(user -> sendWelcomeEmail(user))
           .exceptionally(ex -> logError(ex));
```
## 2. `TaskExecutors` Factory

`TaskExecutors` is a drop-in replacement for the standard `java.util.concurrent.Executors` utility class. It provides familiar factory methods that return `TaskExecutorService` instances:

-   `newFixedThreadPool(int nThreads)`
-   `newCachedThreadPool()`
-   `newSingleThreadExecutor()`

**Adapting Existing Executors:** If you already have a standard `ExecutorService` (e.g., from a third-party library, a framework, or a Spring bean) and want to use it with the `Promise` API, you can wrap it using `TaskExecutors.adapt(...)`:
```java
ExecutorService standardExecutor = Executors.newFixedThreadPool(10);
TaskExecutorService promiseExecutor = TaskExecutors.adapt(standardExecutor);

// The adapter intercepts submit() to return Promises
Promise<String> result = promiseExecutor.submit(() -> "Hello, Promises!");
```
## 3. `TaskCompletionService`

The standard `CompletionService` is useful when you want to submit a batch of tasks and process their results in the order they _complete_, rather than the order they were submitted.

Tascalate Concurrent provides `TaskCompletionService` and its implementation `TaskExecutorCompletionService`, which function exactly like their JDK counterparts but yield `Promise<V>` objects from `submit()`, `take()`, and `poll()`.
```java
TaskExecutorService executor = TaskExecutors.newCachedThreadPool();
TaskCompletionService<String> completionService = 
    new TaskExecutorCompletionService<>(executor);

// Submit multiple tasks
completionService.submit(() -> fetchFromServerA());
completionService.submit(() -> fetchFromServerB());

// Process results as they finish
for (int i = 0; i < 2; i++) {
    Promise<String> completedTask = completionService.take();
    completedTask.thenAccept(result -> System.out.println("Got: " + result));
}
```
## 4. Fluent Thread and ThreadGroup Builders

Configuring thread pools in plain Java often requires writing verbose, anonymous `ThreadFactory` classes. The library provides fluent builders to make this clean, expressive, and highly configurable.

### `ThreadFactoryBuilder`

Allows you to configure thread names, daemon status, priority, context class loaders, and uncaught exception handlers.

-   **Name Formatting:** The `withNameFormat(String)` method accepts a `String.format` pattern with three positional arguments:
    -   `%1$d` — the sequential thread number within the factory.
    -   `%2$s` — the name of the thread group.
    -   `%3$d` — a global pool counter (unique per factory created).
```java
ThreadFactory factory = TaskExecutors.newThreadFactory()
    .withNameFormat("my-app-worker-%1$d")
    .withDaemonFlag(true)
    .withPriority(Thread.NORM_PRIORITY)
    .withUncaughtExceptionHandler((t, e) -> log.error("Thread " + t + " died", e))
    .build();

TaskExecutorService executor = TaskExecutors.newFixedThreadPool(4, factory);
```
### `ThreadGroupBuilder`

Allows you to create and configure a `ThreadGroup` to organize your worker threads, which can then be passed to the `ThreadFactoryBuilder`.
```java
ThreadGroup ioGroup = TaskExecutors.newThreadGroup()
    .withName("async-io-workers")
    .withDaemonFlag(true)
    .withMaxPriority(Thread.NORM_PRIORITY)
    .build();

ThreadFactory factory = TaskExecutors.newThreadFactory()
    .withThreadGroup(ioGroup)
    .withNameFormat("io-pool-%3$d-thread-%1$d")
    .build();
```
## Summary of Executor Classes
| Class / Interface | JDK Equivalent | Tascalate Concurrent Advantage |
| :--- | :--- | :--- |
| **`TaskExecutorService`** | `ExecutorService` | `submit(...)` returns `Promise<T>` instead of `Future<T>`. |
| **`ThreadPoolTaskExecutor`**| `ThreadPoolExecutor`| Drop-in replacement that produces `Promise`s natively. |
| **`TaskExecutors`** | `Executors` | Factory methods for `TaskExecutorService` + `adapt(...)` utility. |
| **`TaskCompletionService`** | `CompletionService` | `take()` and `poll()` return `Promise<V>`. |
| **`ThreadFactoryBuilder`** | *None* | Fluent API for creating highly configured `ThreadFactory` instances. |
| **`ThreadGroupBuilder`** | *None* | Fluent API for creating `ThreadGroup` instances. |

# 17. Throttled Executors and Virtual Threads (Java 21+)

With the introduction of Virtual Threads in Java 21, the traditional "thread pool" paradigm — where a fixed number of long-lived platform threads pull tasks from a queue — is no longer the only (or best) way to handle massive concurrency. Virtual threads are so lightweight that you can spawn a new one for every single task. However, you still need to prevent overwhelming downstream resources (like a database connection pool or a third-party API rate limit).

To solve this, Tascalate Concurrent provides a specialized executor for Java 21+ environments: `ThrottledExecutorService` (and its Promise-returning counterpart, `ThrottledTaskExecutorService`).

## The Concurrency Model: Semaphore-Bounded Virtual Threads

Instead of maintaining a pool of reusable threads, `ThrottledExecutorService` uses a **`Semaphore`** to enforce a strict limit on concurrent executions (`maxConcurrentThreads`):

1. When a task is submitted, the executor attempts to acquire a permit from the semaphore.
2. If a permit is acquired, a **new virtual thread** is spawned to run the task immediately.
3. If no permits are available, the task is placed in a standard `BlockingQueue`.
4. When a running task finishes, its thread checks the queue. If there is another task waiting, the thread "passes" its semaphore permit directly to a **newly spawned virtual thread** for the next task. If the queue is empty, the permit is released back to the semaphore.

This gives you the exact concurrency guarantees of a fixed-size thread pool (e.g., "never more than 50 concurrent DB queries"), but with the immense scalability of virtual threads, allowing thousands of pending tasks to wait in the queue without consuming OS-level thread resources.

## Usage

Because these classes rely on the `Thread.ofVirtual()` API, they are packaged in the Java 21+ section of the Tascalate Concurrent multi-release JAR. 

```java
// Limit to 50 concurrent virtual threads, with an unbounded queue for waiting tasks
ThrottledExecutorService throttledExecutor = 
    new ThrottledExecutorService("my-virtual-workers-", 50);

throttledExecutor.execute(() -> {
    // This runs on a virtual thread. 
    // At most 50 of these will run simultaneously.
    callRemoteApi(); 
});
```
## Promise Integration: `ThrottledTaskExecutorService`

If you want to combine virtual-thread throttling with the Tascalate Concurrent `Promise` API, use `ThrottledTaskExecutorService`. It extends the throttled executor and implements `TaskExecutorService`, ensuring that `submit(...)` returns a `Promise<T>`.
```java
ThrottledTaskExecutorService taskExecutor = 
    new ThrottledTaskExecutorService("api-workers-", 20);

// submit() returns a Promise, not a Future
Promise<HttpResponse> responsePromise = taskExecutor.submit(() -> httpClient.send(request));

responsePromise.thenAccept(response -> process(response))
               .exceptionally(ex -> handleError(ex));
```
# 18. Go-Inspired Asynchronous Channels

Tascalate Concurrent provides a comprehensive implementation of Go-style asynchronous channels for Java, built directly on top of the library's `Promise` API. Channels are thread-safe, FIFO conduits for values exchanged between concurrent tasks, implementing the core ideas of Communicating Sequential Processes (CSP)&nbsp;<a href="https://en.wikipedia.org/wiki/Communicating_sequential_processes" target="_blank">https://en.wikipedia.org/wiki/Communicating_sequential_processes</a> as popularized by the Go programming language&nbsp;<a href="https://go.dev/tour/concurrency/2" target="_blank">https://go.dev/tour/concurrency/2</a>.

Here and after, unless specified explicitly, all channel operations are non-blocking at the call site: they return a `Promise` that completes when the operation is accepted, rather than blocking the calling thread. This design allows channels to integrate seamlessly with the library's `DependentPromise` cancellation propagation, timeout support, and `Promises` combinators.

The channel package is available in the `net.tascalate.concurrent.channel` namespace and requires no additional dependencies beyond the core library. On Java 17+ (via Multi-Release JAR), the `Select` ADT uses `sealed` interfaces and `record` types, enabling exhaustive pattern-matching `switch` statements in Java 21+.

## Creating Channels

Channels are created through static factory methods on the `Channel` interface. Two structural variants exist:

**Rendezvous channels** (capacity 0) -- every send waits for a matching receive and vice versa. Values are handed over directly with no buffering. This corresponds to Go's default unbuffered channel.

**Buffered channels** (capacity > 0) -- sends complete immediately while the buffer has space; receives complete immediately while the buffer holds data. Blocking (i.e., a pending `Promise`) occurs only at the buffer boundaries. 
```java
import net.tascalate.concurrent.channels.Channel;
import net.tascalate.concurrent.channels.ChannelBase.CloseMode;

// Rendezvous (unbuffered) channel
Channel<String> rendezvous = Channel.rendezvous();

// Buffered channel with capacity 16
Channel<Integer> buffered = Channel.buffered(16);

// Buffered channel with pre-allocated backing array (avoids resizing)
Channel<Integer> preallocated = Channel.buffered(1024, 256);

// Fair variants (FIFO waiter ordering, prevents starvation)
Channel<String> fairRendezvous = Channel.rendezvous(true);
Channel<Integer> fairBuffered  = Channel.buffered(16, true);

// Nil channel -- never completes any operation (Go's nil channel)
Channel<Object> nil = Channel.nil();
```
The `initialCapacity` parameter on `buffered(...)` is a sizing hint for the underlying `ArrayDeque` storage. It does not change the channel's logical capacity but can reduce or eliminate internal array resizing when the expected working set size is known in advance.

The **nil channel** mirrors Go's `nil` channel semantics: sends and receives return promises that stay pending forever. It is primarily useful as a placeholder for dynamically disabling cases in a select loop.

## Basic Send and Receive

All send and receive operations return a `Promise` that completes when the transfer is accepted:
```java
import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.channels.Channel;

Channel<String> ch = Channel.buffered(4);

// Send -- completes when buffered or handed to a receiver
Promise<String> sent = ch.send("hello");
sent.thenAccept(v -> System.out.println("Delivered: " + v));

// Receive -- completes when a value is available
Promise<String> received = ch.receive();
received.thenAccept(v -> System.out.println("Got: " + v));

// Timed send -- fails with TimeoutException if not accepted within 5s
ch.send("urgent", Duration.ofSeconds(5))
  .thenAccept(v -> System.out.println("Sent in time"))
  .exceptionally(ex -> {
      System.err.println("Timed out: " + ex);
      return null;
   });

// Non-blocking variants (for select Phase 1 or polling)
Try<String> tryResult = ch.tryReceive(); // null if not ready
Try<String> trySent   = ch.trySend("quick"); // null if full
```
The timed variants (`send(value, Duration)` and `receive(Duration)`) automatically cancel the underlying operation when the deadline elapses, removing any registered waiter from the channel's internal queue via `DependentPromise` cancellation propagation.

## Channel Lifecycle and Close Modes

Channels are closed through the `close(CloseMode)` method, which is idempotent. Two modes govern the behavior of pending and future operations:

**`CloseMode.DRAIN`** (Go semantics) -- waiting senders fail, waiting receivers complete with `null`, and buffered items can still be consumed. This is the graceful shutdown option.

**`CloseMode.FAIL_ALL`** (Java/Reactor semantics) -- all waiting senders and receivers fail exceptionally, and the buffer is cleared. This is the hard-stop option, also used by the default `AutoCloseable.close()` for try-with-resources.

```java
Channel<Integer> ch = Channel.buffered(4);
ch.send(1).join();
ch.send(2).join();

// Graceful: consumers can still drain buffered items
ch.close(CloseMode.DRAIN);

// After DRAIN close:
//   ch.receive()  -> completes with 1, then 2, then null (EOF)
//   ch.send(99)   -> fails with IllegalStateException
//   ch.tryReceive() -> Try.success(1), Try.success(2), Try.success(null)

// Hard stop: everything fails immediately
ch.close(CloseMode.FAIL_ALL);
```
The `isExhausted()` method on `ReceiveChannel` is the authoritative end-of-stream check. Unlike inspecting the value returned by `receive()`, it cannot be confused with a legitimate `null` element sent through the channel.

**Performance and Usage Note:** If your channel is strictly used for **non-null values**, a simple `null` check on the received value is entirely sufficient to detect EOF. Furthermore, because `isExhausted()` requires acquiring the channel's internal lock, it is significantly more performant to check for `null` first and only invoke `isExhausted()` when a `null` is actually encountered. This avoids lock contention on every single receive operation:
```java
String value = ch.receive().join();

if (value == null) {
    // Only check the channel state if we actually got a null
    if (ch.isExhausted()) {
        System.out.println("End of stream reached");
        break;
    } else {
        System.out.println("Received a legitimate null value");
    }
} else {
    process(value);
}
```

## Directional Views

The channel type hierarchy provides directional views that restrict what a caller can do, mirroring Go's `chan<- T` (send-only) and `<-chan T` (receive-only) types:

-   **`SendChannel<T>`** -- write-only view. Pass to producers to prevent accidental receiving.
-   **`ReceiveChannel<T>`** -- read-only view. Pass to consumers to prevent accidental sending.
-   **`SelectableSendChannel<T>`** / **`SelectableReceiveChannel<T>`** -- extend the directional views with select participation capabilities.
-   **`Channel<T>`** -- full bidirectional channel, extends both selectable views.
```java
void producer(SendChannel<Integer> out) {
    // Can only send; compiler prevents out.receive()
    out.send(42).join();
}

void consumer(ReceiveChannel<Integer> in) {
    // Can only receive; compiler prevents in.send(...)
    in.receive().thenAccept(System.out::println);
}
```
## Iterating Over Channels: `forEach`

The `forEach` method on `ReceiveChannel` asynchronously consumes elements until the channel is closed and drained, or until a condition returns `false`. It mirrors Go's `for v := range ch` loop:
```java
Channel<Integer> ch = Channel.buffered(8);
// ... producer sends items and closes ...

// Consume all elements until EOF
ch.forEach(value -> {
    System.out.println("Processing: " + value);
}).thenAccept(done -> {
    System.out.println("Channel fully drained");
});

// Consume with a takeWhile-style condition
ch.forEach(
    value -> System.out.println("Got: " + value),
    value -> value < 100  // stop when value >= 100
).join();
```
The condition is evaluated **before** the action. If it returns `false`, the loop terminates immediately and the action is NOT executed for that element, matching `Stream.takeWhile` semantics.

Internally, `forEach` employs a synchronous fast-path: when buffered data is immediately available, the underlying `Promises.loop` executes iterations without yielding to the event loop, maximizing throughput. When the buffer is empty and a wait is required, control yields asynchronously.

**Pre-check guards for bounded consumption.** When the termination condition depends on the number of items received (rather than the value itself), `forEach` supports a `ReceiveGuard` mechanism. Because `ReceiveGuard` extends `Predicate<T>`, it actually supports a two-phase evaluation:

-   **Pre-check (`mayReceive()`)**: Evaluated **before** calling `receive()`. If it returns `false`, the loop terminates immediately without pulling an element from the channel buffer, preventing the "read-ahead" consumption of an extra element.
-   **Post-check (`test(T)`)**: Evaluated **after** the element is received, exactly like a standard `Predicate`. This allows you to elegantly combine count-based limits with value-based conditions (e.g., stopping at a maximum count _or_ upon receiving a specific poison-pill value).
```java
import net.tascalate.concurrent.channels.ReceiveGuard;

List<Integer> result = new ArrayList<>();
int maxItems = 10;

ReceiveGuard<Integer> guard = new ReceiveGuard<Integer>() {
    @Override
    boolean mayReceive() {
        // Pre-check: stop BEFORE consuming the 11th item
        return result.size() < maxItems; 
    }
    
    @Override
    public boolean test(Integer value) {
        // Post-check: also stop early if we receive a poison pill
        return value != -1; 
    }
};

ch.forEach(result::add, guard).join();
// result contains at most maxItems elements, and will never contain -1.
// If -1 is in the channel, it remains unconsumed.
```

## Bulk Operations: `sendAll` and `receiveAll`

**`sendAll`** asynchronously sends all elements from an `Iterable` to the channel and returns the count of successfully sent items:
```java
Channel<Integer> ch = Channel.buffered(64);
List<Integer> items = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

Promise<Integer> sentCount = ch.sendAll(items);
sentCount.thenAccept(n -> System.out.println("Sent " + n + " items"));
```
The method uses the same synchronous fast-path as `forEach`: elements are sent in a tight loop as long as the channel accepts them immediately. When the buffer fills (or a rendezvous wait is required), control yields to the event loop. If the channel is closed mid-stream, the returned promise fails exceptionally.

**`receiveAll`** asynchronously receives elements into a `List` until the channel is exhausted or a maximum item count is reached:
```java
Channel<String> ch = Channel.buffered(16);
// ... producer sends items and closes with DRAIN ...

// Receive everything
Promise<List<String>> all = ch.receiveAll();

// Receive at most 5 items (remaining items stay in the channel)
Promise<List<String>> first5 = ch.receiveAll(5);

List<String> items = all.join();
System.out.println("Received: " + items);
```
The `receiveAll(maxItems)` variant uses `ReceiveGuard` internally to ensure the `maxItems + 1` element is NOT consumed from the buffer, avoiding the read-ahead bug inherent in value-based predicates.

## Select: Multiplexing Over Channels

The `select` statement is the Java analogue of Go's `select`. It multiplexes across multiple send and receive operations, completing with whichever case becomes ready first. Evaluation proceeds in two phases:

1.  **Phase 1 (non-blocking):** all cases are tried in random order via `trySend` / `tryReceive`. If any case is immediately ready, the select resolves with that case's result. If a `Default` case is present and no case is ready, the default wins.
2.  **Phase 2 (blocking):** if no case is ready and no default exists, waiters are registered on every active case. The returned promise completes when the first case becomes ready, and all losing cases are cancelled.

Cases are built with factory methods on the `Select` class or the fluent methods on selectable channels:
```java
import net.tascalate.concurrent.channels.Channel;
import net.tascalate.concurrent.channels.Select;

Channel<String>  ch1 = Channel.buffered(4);
Channel<Integer> ch2 = Channel.buffered(4);
Channel<Integer> ch3 = Channel.rendezvous();

Promise<Select.Result<Object>> result = Channel.select(
    Select.receive(ch1),          // receive from ch1
    ch2.sending(42),              // send 42 to ch2 (fluent)
    Select.receive(ch3),          // receive from ch3
    Select.otherwise()            // default: resolve immediately if nothing ready
);
```
## Select Results and Pattern Matching (Java 17+)

On Java 17+, the `Select` hierarchy is a strict Algebraic Data Type built from `sealed` interfaces and `record` types. The result type `Select.Result<T>` is sealed, permitting `ValueResult` (for `Send.Result` and `Receive.Result`) and `Default.Result`. This enables exhaustive pattern-matching `switch` in Java 21+:
```java
Select.Result<Object> result = Channel.select(
    ch1.receiving(),
    ch2.sending(42),
    Select.otherwise()
).join();

// Java 21+ -- exhaustive, no default branch needed
switch (result) {
    case Select.Send.Result(var idx, var val, _) ->
        System.out.printf("Case %d: sent %s%n", idx, val);

    case Select.Receive.Result(var idx, var val, _) ->
        System.out.printf("Case %d: received %s%n", idx, val);

    case Select.Default.Result(var idx, _) ->
        System.out.printf("Case %d: default fired%n", idx);
}
```
The compiler guarantees exhaustiveness because `Select.Op` is sealed. If a new operation type is added in the future, all existing switches will fail to compile, forcing callers to handle the new case.

The intermediate `ValueResult` interface distinguishes results that carry a payload from those that do not, enabling safe polymorphic access:
```java
Select.Result<Object> result = Channel.select(cases).join();

if (result instanceof Select.ValueResult<?> vr) {
    System.out.println("Payload: " + vr.value());
}
```
## Select Results (Pre-Java 17)

On JDK versions prior to 17, the `Select` types are plain classes and interfaces. Pattern matching is unavailable, so results are inspected via `instanceof` checks on the **Result hierarchy** followed by explicit casts, or via the cast-free `payload()` method:
```java
Select.Result<Object> result = Channel.select(cases).join();

// Option 1: instanceof on specific Result subclasses + explicit cast
if (result instanceof Select.Send.Result) {
    Select.Send.Result<Object> sendResult = (Select.Send.Result<Object>) result;
    System.out.println("Sent: " + sendResult.value());
} else if (result instanceof Select.Receive.Result) {
    Select.Receive.Result<Object> recvResult = (Select.Receive.Result<Object>) result;
    System.out.println("Received: " + recvResult.value());
} else if (result instanceof Select.Default.Result) {
    System.out.println("Default fired");
}

// Option 2: instanceof on the ValueResult interface + explicit cast
// (Useful when you don't care whether it was a Send or Receive, just that it has a payload)
if (result instanceof Select.ValueResult) {
    Select.ValueResult<Object> valueResult = (Select.ValueResult<Object>) result;
    System.out.println("Payload: " + valueResult.value());
} else {
    System.out.println("Default fired (no payload)");
}

// Option 3: payload() -- completely cast-free
Optional<Object> payload = result.payload();
if (payload != null) {
    // Send or Receive case (payload is a non-null Optional)
    System.out.println("Value: " + payload.orElse(null));
} else {
    // Default case (payload() returns literal null)
    System.out.println("Default fired");
}
```
The `payload()` method returns a non-null `Optional` for payload-bearing operations (`Send` and `Receive`) and `null` for operations without a payload (`Default`). This distinction lets callers differentiate "no payload concept" from "payload is null" without downcasting.


## Disabled Cases: Dynamic Select Loops

The `Select.Disabled` case represents a permanently inactive case that never matches in Phase 1 and never registers in Phase 2. It is the Java equivalent of Go's nil-channel idiom, allowing cases to be dynamically removed from a select loop without altering the array structure or shifting indices:
```java
Channel<Integer> ch1 = Channel.buffered(4);
Channel<Integer> ch2 = Channel.buffered(4);

// 1. Define an immutable state record to hold loop variables
record SelectState(boolean ch1Active, boolean running) {}

// 2. Execute the asynchronous loop
Promises.loop(
    new SelectState(true, true),   // Initial state: ch1 is active, loop is running
    SelectState::running,          // Condition: keep looping while 'running' is true
    state -> {
        // Build the select cases based on the current state
        @SuppressWarnings("unchecked")
        Select.Op<Integer>[] cases = new Select.Op[] {
            state.ch1Active() ? Select.receive(ch1) : Select.disabled(),
            Select.receive(ch2)
        };

        // Execute select and map the result to the NEXT state
        return Channel.select(cases).thenApply(result -> {
            if (result.index() == 0) {
                // Received from ch1; disable it for future iterations, keep looping
                return new SelectState(false, true);
            } else {
                // Received from ch2; terminate the loop
                return new SelectState(state.ch1Active(), false);
            }
        });
    }
).thenAccept(finalState -> {
    System.out.println("Loop terminated gracefully.");
}).join(); // Only block at the very end if you need to wait for the whole process to finish
```
Below is a Go version of the code:
```go
package main

import "fmt"

func main() {
	// Create two buffered channels, mirroring Channel.buffered(4)
	ch1 := make(chan int, 4)
	ch2 := make(chan int, 4)

	// Seed some data
	ch1 <- 42
	ch2 <- 99

	// In Go, setting a channel variable to nil disables it in a select statement.
	// This is the exact equivalent of our Java Select.disabled() case.
	activeCh1 := ch1 

	for {
		select {
		case v := <-activeCh1:
			fmt.Printf("Received %d from ch1. Disabling ch1.\n", v)
			// Disable ch1 for all future iterations by setting it to nil
			activeCh1 = nil 

		case v := <-ch2:
			fmt.Printf("Received %d from ch2. Exiting loop.\n", v)
			return // Break out of the loop entirely
		}
	}
}
```
Here is how to achieve the exact same behavior in Java. By swapping the active channel for `Channel.nil()` inside an asynchronous `Promises.loop`, we elegantly disable the case without mutating shared state:
```java
Channel<Integer> ch2 = Channel.buffered(4);

// Mutable state: the channel variable itself, exactly like Go
record LoopState(Channel<Integer> ch1, boolean running) {}

Promises.loop(
    new LoopState(Channel.<Integer>buffered(4), true),
    LoopState::running,
    state -> Channel.select(
        Select.receive(state.ch1()),   // ch1, or Channel.nil() once disabled
        Select.receive(ch2)
    ).thenApply(result ->
        result.index() == 0
            ? new LoopState(Channel.nil(), true)   // ch1 = nil
            : new LoopState(state.ch1(), false)    // break
    )
).join();
```

## Helper Methods on Selectable Channels

The `SelectableSendChannel` and `SelectableReceiveChannel` interfaces provide fluent factory methods for building select cases directly from a channel reference:
```java
// Equivalent to Select.send(ch, value)
Select.Send<String> sendCase = ch.sending("hello");

// Equivalent to Select.receive(ch)
Select.Receive<String> recvCase = ch.receiving();
```
These are purely syntactic sugar and produce the same objects as the static factories on `Select`. They are provided for readability in select-heavy code.


## Summary of the Channel Type Hierarchy
```
ChannelBase (lifecycle, close modes, size, capacity)
  |
  +-- SendChannel<T> (send, trySend, sendAll)
  |     +-- SelectableSendChannel<T> (+ select participation, sending())
  |
  +-- ReceiveChannel<T> (receive, tryReceive, forEach, receiveAll, isExhausted)
  |     +-- SelectableReceiveChannel<T> (+ select participation, receiving())
  |
  +-- Channel<T> extends SelectableSendChannel<T>, SelectableReceiveChannel<T>
        (full bidirectional: nil(), rendezvous(), buffered(), select())
```
The implementation class `BufferedChannel<T>` handles both rendezvous (capacity 0) and buffered (capacity > 0) modes through a unified internal design guarded by a single `ReentrantLock`. A `NilChannel<T>` singleton provides the never-completing nil channel.

## Go to Java Idiom Mapping

| Go Idiom | Tascalate Concurrent Equivalent | Notes |
| :--- | :--- | :--- |
| `make(chan T)` | `Channel.<T>rendezvous()` | Unbuffered, strict synchronization. |
| `make(chan T, N)` | `Channel.<T>buffered(N)` | Buffered up to `N` elements. |
| `var ch chan T` (nil channel) | `Channel.<T>nil()` | Blocks forever; useful for disabling select cases. |
| `chan<- T` (send-only) | `SendChannel<T>` | Compiler-enforced write-only view. |
| `<-chan T` (receive-only) | `ReceiveChannel<T>` | Compiler-enforced read-only view. |
| `ch <- v` | `ch.send(v)` | Returns a `Promise<T>` that completes on acceptance. |
| `v := <-ch` | `ch.receive()` | Returns a `Promise<T>` that completes with the value. |
| `v, ok := <-ch` (non-blocking) | `ch.tryReceive()` | Returns a `Try<T>` (or `null` if not ready). |
| `close(ch)` | `ch.close(CloseMode.DRAIN)` | Go semantics: drains buffer, fails pending senders. |
| N/A (hard stop) | `ch.close(CloseMode.FAIL_ALL)` | Java/Reactor semantics: fails all waiters immediately. |
| `for v := range ch { ... }` | `ch.forEach(v -> ...)` | Asynchronous loop until channel is exhausted. |
| `len(ch)` | `ch.size()` | Current number of buffered elements. |
| `cap(ch)` | `ch.capacity()` | Maximum buffer capacity (0 for rendezvous). |
| `select { ... }` | `Channel.select(...)` | Multiplexes across multiple channel operations. |
| `default:` in select | `Select.otherwise()` | Non-blocking fallback if no case is ready. |
| Disabling a case (`ch = nil`) | `Select.disabled()` | Dynamically removes a case from the select loop. |
| `case <-time.After(d):` | `ch.receive(Duration)` | Built-in timeout support; auto-cancels the underlying waiter. |

# Acknowledgements

Internal implementation details are greatly inspired [by the work](https://github.com/lukas-krecan/completion-stage) done by [Lukáš Křečan](https://github.com/lukas-krecan). The part of the polling / asynchronous retry functionality is adopted from the [async-retry](https://github.com/nurkiewicz/async-retry) library by [Tomasz Nurkiewicz](http://nurkiewicz.com/)