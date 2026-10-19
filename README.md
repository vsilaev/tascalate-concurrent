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
# Why a CompletableFuture is not enough?
There are several shortcomings associated with [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) implementation that complicate its usage for real-life asynchronous programming, especially when you have to work with I/O-bound interruptible tasks:
1.	`CompletableFuture.cancel()` [method](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html#cancel-boolean-) does not interrupt underlying thread; it merely puts future to exceptionally completed state. So even if you use any blocking calls inside functions passed to `thenApplyAsync` / `thenAcceptAsync` / etc - these functions will run till the end and never will be interrupted. Please see [CompletableFuture can't be interrupted](http://www.nurkiewicz.com/2015/03/completablefuture-cant-be-interrupted.html) by Tomasz Nurkiewicz.
2.	By default, all `*Async` composition methods of `CompletableFutrure` use `ForkJoinPool.commonPool()` ([see here](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html#commonPool--)) unless explicit [Executor](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Executor.html) is specified. This thread pool is shared between all [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html)-s and all parallel streams across all applications deployed on the same JVM. This hard-coded, unconfigurable thread pool is completely outside of application developers' control, hard to monitor and scale. Therefore, in robust real-life applications you should always specify your own `Executor`. With API enhancements in Java 9+, you can fix this drawback, but it will require some custom coding.
3.	Additionally, built-in Java 8 concurrency classes provides pretty inconvenient API to combine several [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html)-s. `CompletableFuture.allOf` / `CompletableFuture.anyOf` methods accept only [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) as arguments; you have no mechanism to combine arbitrary [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html)-s without converting them to [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) first. Also, the return type of the aforementioned `CompletableFuture.allOf` is declared as `CompletableFuture<Void>` - hence you are unable to extract conveniently individual results of the each future supplied. `CompletableFuture.anyOf` is even worse in this regard; for more details please read on here: [CompletableFuture in Action](http://www.nurkiewicz.com/2013/05/java-8-completablefuture-in-action.html) (see Shortcomings) by Tomasz Nurkiewicz.
4. Support for timeouts/delays was introduced to [CompletableFuture](https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/CompletableFuture.html) only in Java 9, so still widely supported applications running on Java 8 are left out without this important functionality. Plus, some design decisions like using delayed executors instead of 'delay' operator, are somewhat questionable.

There are numerous free open-source libraries that address some of the aforementioned shortcomings. However, none of them provides implementation of interruptible `CompletionStage` and no one solves _all_ of the issues coherently.

# How to use?
To use a library you have to add a single Maven dependency
```xml
<dependency>
    <groupId>net.tascalate</groupId>
    <artifactId>net.tascalate.concurrent</artifactId>
    <version>0.9.13</version>
</dependency>
```

# What is inside?
## 1.	Promise interface
This is the core interface of the Tascalate Concurrent library. It may be best described by the formula: 
```java
Promise == CompletionStage + Future
```
I.e., it combines both blocking [Future](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Future.html)’s API, including `cancel(boolean mayInterruptIfRunning)` [method](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Future.html#cancel-boolean-), _AND_ composition capabilities of [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html)’s API. Importantly, all composition methods of [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html) API (`thenAccept`, `thenCombine`, `whenComplete` etc.) are re-declared to return `Promise` as well.

The decision to introduce an interface that merges `CompletionStage` and `Future` is aligned with the design of `CompletableFuture` API.  In addition, several useful methods of `CompletableFuture` API are added as well:
```java
T getNow(T valueIfAbsent) throws CancellationException, CompletionException;
T getNow(Supplier<? extends T> valueIfAbsent) throws CancellationException, CompletionException;
T join() throws CancellationException, CompletionException;
```
So it should be pretty straightforward to use the `Promise` as a drop-in replacement for the `CompletableFuture` in many cases.

Although the library is compatible with JDK 1.8 there are methods in `Promise` interface that makes it "upfront compatible" with the higher JDK versions (available since version [0.8.2](https://github.com/vsilaev/tascalate-concurrent/releases/tag/0.8.2)):
```java
Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn);
Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor);
Promise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn);
Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn);
Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, 
                                     Executor executor);
```

Plus, there are several overloads of the filtering operator that complements canonical `map/flatMap` (or `thenApply/thenCompose` in terms of the `CompletionStage` API), available since version [0.9.0](https://github.com/vsilaev/tascalate-concurrent/releases/tag/0.9.1):
```java
Promise<T> thenFilter(Predicate<? super T> predicate);
Promise<T> thenFilter(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier);
Promise<T> thenFilterAsync(Predicate<? super T> predicate);
Promise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier);
Promise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor);
Promise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier,
                           Executor executor);
```

Besides this, there are numerous operators in the `Promise` API to work with timeouts and delays, to override default asynchronous executor and similar. All of them will be discussed later.

When discussing `Promise` interface, it's mandatory to mention the accompanying class `Promises` that provides several useful methods to adapt third-party `CompletionStage` (including the standard `CompletableFuture`) to the `Promise` API. First, there are two unit operations to create successfully/faulty settled `Promise`-es: 
```java
public final class Promises {
  ...
  static <T> Promise<T> success(T value)
  static <T> Promise<T> failure(Throwable exception)
  ...
}
```
Actually, release [0.9.0](https://github.com/vsilaev/tascalate-concurrent/releases/tag/0.9.0) adds the third unit operation:
```java
public final class Promises {
  ...
  static <T> Promise<T> maybe(Optional<T> maybeValue)
  ....
}
```
It converts the `Optional` supplied either to the successfully settled `Promise` or to the faulty settled one with [NoSuchElementException](https://docs.oracle.com/javase/8/docs/api/java/util//NoSuchElementException.html).

Second, there is an adapter method `from`:
```java
public final class Promises {
  ...
  static <T> Promise<T> from(CompletionStage<T> stage)
  ...
}
```
It behaves as the following: 
1. If the supplied `stage` is already a `Promise` then it is returned unchanged
2. If `stage` is a `CompletableFuture` then a specially-tailored wrapper is returned.
3. If `stage` additionally implements `Future` then specialized wrapper is returned that delegates all the blocking methods defined in `Future` API
4. Otherwise generic wrapper is created with good-enough implementation of blocking `Future` API atop of asynchronous `CompletionStage` API.

To summarize, the returned wrapper delegates as much as possible functionality to the supplied `stage` and _never_ resorts to [CompletionStage.toCompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html#toCompletableFuture--) because in Java 8 API it's an optional method. From documentation: "A CompletionStage implementation that does not choose to interoperate with others may throw UnsupportedOperationException." (this text was dropped in Java 9+). In general, Tascalate Concurrent library does not depend on this method and should be interoperable with any minimal (but valid) `CompletionStage` implementation.

It's important to emphasize, that `Promise`-s returned from `Promises.success`, `Promises.failure` and `Promises.from` methods are cancellable in the same way as `CompletableFuture`, but are not interruptible in general, while interruption depends on a concrete implementation. Next we discuss the concrete implementation of an interruptible `Promise` provided by the Tascalate Concurrent library -- the `CompletableTask` class.

## 2. CompletableTask
This is why this project was ever started. `CompletableTask` is the implementation of the `Promise` API for long-running blocking tasks.
Typically, to create a `CompletableTask`, you should submit [Supplier](https://docs.oracle.com/javase/8/docs/api/java/util/function/Supplier.html) / [Runnable](https://docs.oracle.com/javase/8/docs/api/java/lang/Runnable.html) to the [Executor](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Executor.html) right away, in a similar way as with [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html):

```java
Promise<SomeValue> p1 = CompletableTask.supplyAsync(() -> {
  return blockingCalculationOfSomeValue();
}, myExecutor);

Promise<Void> p2 = CompletableTask.runAsync(this::someIoBoundMethod, myExecutor);
```
`blockingCalculationOfSomeValue` and `someIoBoundMethod` in the example above can have I/O code, work with blocking queues, do blocking get on regular Java-s `Future`-s and alike. If at later time you decide to cancel either of the returned promises then corresponding `blockingCalculationOfSomeValue` and `someIoBoundMethod` will be interrupted (if not completed yet).

In the realm of I/O-related functionality, failures like connection time-outs, missing or locked files are pretty common, and checked exceptions mechanism is used frequently to signal failures. Therefore the library provides an entry point to the API that accepts [Callable](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Callable.html) instead of `Supplier`:
```java
// Notice the checked exception in the method signature
byte[] loadFile(File file) throws IOException {
    byte[] result = ... //load file content;
    return result;
}
...
ExecutorService executorService = Executors.newFixedThreadPool(6);
Promise<byte[]> contentPromise = CompletableTask.submit(
    () -> loadFile(new File("./myfile.dat")), 
    executorService
); 
```

Additionally, there are 2 unit operations to create a `CompletableTask`:

a.	`CompletableTask.asyncOn(Executor executor)`
Returns an already-completed null-valued `Promise` that is "bound" to the specified executor. I.e. any function passed to asynchronous composition methods of `Promise` (like `thenApplyAsync` / `thenAcceptAsync` / `whenCompleteAsync` etc.) will be executed using this executor unless executor is overridden via explicit composition method parameter. Moreover, any nested composition calls will use same executor, if it’s not redefined via explicit composition method parameter:
```java
CompletableTask
  .asyncOn(myExecutor)
  .thenApplyAsync(myValueGenerator)
  .thenAcceptAsync(myConsumer)
  .thenRunAsync(myAction);
```  
All of `myValueGenerator`, `myConsumer`, `myAction` will be executed using `myExecutor`.

b.	`CompletableTask.completed(T value, Executor executor)`
Same as above, but the starting point is a `Promise` completed with the specified value:
```java
CompletableTask
   .completed("Hello!", myExecutor)
   .thenApplyAsync(myMapper)
   .thenApplyAsync(myTransformer)   
   .thenAcceptAsync(myConsumer)
   .thenRunAsync(myAction);
```  
All of `myMapper`, `myTransformer`, `myConsumer`, `myAction` will be executed using `myExecutor`.
**WARNING** Before release [0.9.0](https://github.com/vsilaev/tascalate-concurrent/releases/tag/0.9.0) this method was named `CompletableTask.complete(...)`

Crucially, all composed promises support true cancellation (incl. interrupting thread) for the functions supplied as arguments:
```java
Promise<?> p1 = CompletableTask.asyncOn(myExecutor)
Promise<?> p2 = p1.thenApplyAsync(myValueGenerator)
Promise<?> p3 = p2.thenRunAsync(myAction);
  
...
p2.cancel(true);
```  
In the example above `myValueGenerator` will be interrupted if already in progress. Both `p2` and `p3` will be settled with failure: `p2` with a [CancellationException](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CancellationException.html) and `p3` with a [CompletionException](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionException.html).

You may notice, that above the term "asynchronous composition methods" is used, as well as `*Async` calls in examples (like `thenApplyAsync`, `thenRunAsync`. This is not accidental: non-asynchronous methods of `CompletionStage` API are not interruptible. The grounding beneath the design decision is that invoking asynchronous methods involves inevitable overhead of putting command to the queue of the executor, starting new threads implicitly, etc. And for simple, non-blocking methods, like small calculations, trivial transformations and alike this overhead might outweigh method's execution time itself. So the guideline is: use asynchronous composition methods for heavy I/O-bound blocking tasks, and use non-asynchronous composition methods for (typically lightweight) calculations.

Worth to mention, that `CompletableTask`-s and `Promise`-s composed out of it may be ever interruptible _only_ if the `Executor` used is interruptible by nature. For example, [ThreadPoolExecutor](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ThreadPoolExecutor.html) supports interruptible tasks, but [ForkJoinPool](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html) does not!

## 3. DependentPromise
As it mentioned above, once you cancel `Promise`, all `Promise`-s that depends on this promise are completed with [CompletionException](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionException.html) wrapping [CancellationException](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CancellationException.html). This is a standard behavior, and [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) works just like this.

However, when you cancel derived `Promise`, the original `Promise` is not cancelled: 
```java
Promise<?> original = CompletableTask.supplyAsync(() -> someIoBoundMethod(), myExecutor);
Promise<?> derivedA = original.thenRunAsync(() -> someMethodA() );
Promise<?> derivedB = original.thenRunAsync(() -> someMethodB() );
...
derivedB.cancel(true);
```
So if you cancel `derivedB` above it's [Runnable](https://docs.oracle.com/javase/8/docs/api/java/lang/Runnable.html) method, wrapping `someMethod`, is interrupted. However the `original` promise is not cancelled and `someIoBoundMethod` keeps running. Also, `derivedA` is not cancelled, and such behavior is expected. However, sometimes we have a linear chain of the promises and have a requirement to cancel the whole chain from a tail to the head. Consider the following method:

```java
public Promise<DataStructure> loadData(String url) {
   return CompletableTask.supplyAsync( () -> loadXml(url) ).thenApplyAsync( xml -> parseXml(xml) ); 
}

...
Promise<DataStructure> p = loadData("http://someserver.com/rest/ds");
...
if (someCondition()) {
  // Only second promise is canceled, parseXml.
  p.cancel(true);
}
```

Clients of this method see only derived promise, and once they decide to cancel it, it is expected that any of `loadXml` and `parseXml` will be interrupted if not completed yet. To address this issue the library provides [DependentPromise](https://github.com/vsilaev/tascalate-concurrent/blob/master/src/main/java/net/tascalate/concurrent/DependentPromise.java) class:
```java
public Promise<DataStructure> loadData(String url) {
   return DependentPromise
          .from(CompletableTask.supplyAsync( () -> loadXml(url) ))
          .thenApplyAsync( xml -> parseXml(xml), true ); 
}

...
Promise<DataStructure> p = loadData("http://someserver.com/rest/ds");
...
if (someCondition()) {
  // Now the whole chain is canceled.
  p.cancel(true);
}
```
[DependentPromise](https://github.com/vsilaev/tascalate-concurrent/blob/master/src/main/java/net/tascalate/concurrent/DependentPromise.java) overloads methods like `thenApply` / `thenRun` / `thenAccept` / `thenCombine` etc with additional argument:
- if method accepts no other [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html), like `thenApply` / `thenRun` / `thenAccept` etc, then it's a boolean flag `enlistOrigin` to specify whether or not the original `Promise` should be enlisted for the cancellation.
- if method accepts other [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html), like `thenCombine` / `applyToEither` / `thenAcceptBoth` etc, then it's a set of [PromiseOrigin](https://github.com/vsilaev/tascalate-concurrent/blob/master/src/main/java/net/tascalate/concurrent/PromiseOrigin.java) enum values, that specifies whether or not the original `Promise` and/or a `CompletionStage` supplied as argument should be enlisted for the cancellation along with the resulting promise, for example:

```java
public Promise<DataStructure> loadData(String url) {
   return DependentPromise
          .from(CompletableTask.supplyAsync( () -> loadXml(url + "/source1") ))
          .thenCombine( 
              CompletableTask.supplyAsync( () -> loadXml(url + "/source2") ), 
              (xml1, xml2) -> Arrays.asList(xml1, xml2),
              PromiseOrigin.ALL
          )          .
          .thenApplyAsync( xmls -> parseXmlsList(xmls), true ); 
}
```

Please note, then in release 0.5.4 there is a new default method `dependent` in interface [Promise](https://github.com/vsilaev/tascalate-concurrent/blob/master/src/main/java/net/tascalate/concurrent/Promise.java) that serves the same purpose and allows to write chained calls:

```java
public Promise<DataStructure> loadData(String url) {
   return CompletableTask
          .supplyAsync( () -> loadXml(url) )
          .dependent()
          .thenApplyAsync( xml -> parseXml(xml), true ); 
}
```

Attention: just calling `dependent()` on the `Promise` is not enough to change the behavior of the "default" `thenApply` / `thenRun` / `thenAccept` / `thenCombine` etc methods defined in `CompletionStage` - you have to use overloaded form with either `boolean` or `Set<PromiseOrigin>` last parameter _explicitly_. This is the intentional design decision: just recall, that the `Promise` returned in the example above can be used by the client code to create _independent_ promises (as per `CompletionStage` API), so the library has to follow the rule of the least surprise.

Below are 2 examples (wrong and right) to re-iterate the statement above in Java code.

Wrong code to cancel the whole chain:
```java
Promise<Xml> xml = CompletableTask.supplyAsync( () -> loadXml(url) )
Promise<Data> data = xml.dependent()
                        .thenApplyAsync( xml -> parseXml(xml) );  // Not overloaded method - 
                                                                  // no "true" as the last parameter
...
// This cancels only "data" promise, but will not cancel "xml" promise
data.cancel(true);
```
Right code to cancel the whole chain:
```java
Promise<Xml> xml = CompletableTask.supplyAsync( () -> loadXml(url) )
Promise<Data> data = xml.dependent()
                        .thenApplyAsync( xml -> parseXml(xml), true );  // Overloaded method - 
                                                                        // "true" as last parameter means 
                                                                        // enlist "origin" promise 
                                                                        // for cancellation
...
// This cancels both "data" promise AND the "xml" promise (if it's not completed yet)
data.cancel(true);
```

So far so good. And following `CompletionStage` API contract is good, no doubt. But what if you have to create locally a chain of 10 stages? 30 stages? It's both pretty boring and error-prone to put additional parameters to enforce overloaded method(s) usage. And error-prone means just this -- it's pretty easy to forget to type "true" as the latest parameter and break the cancellation chain. Tascalate Concurrent provides an option to handle this edge case: use overloaded form of `dependent()` method - `dependent(Set<PromiseOrigin>)`. Here is an example from the real-life issue raised in the real-life project:
```java
Promise<?> myPromise = 
CompletableTask.supplyAsync(() -> candidate, pool)
               .dependent(PromiseOrigin.ALL) //Key point - alter the behavior of CompletionStage API methds
               .thenApplyAsync(CheckIp::doWork)
               .thenApplyAsync(CheckType::doWork)
               .thenApplyAsync(CheckExclusion::doWork)
               .thenApplyAsync(AssignEntity::doWork)
               .thenCombineAsync(collectedStatsPromise, this:combineWithStats) // <-- pay attention here
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
Unlike the conservative form of the `dependent()` decorator, the overloaded `dependent(PromiseOrigin.ALL)` will enlist ALL the promises mentioned by composition methods as _dependent_ and will cancel them when `myPromise` is cancelled. Even the `collectedStatsPromise` that is passed from outside! Everything enlisted will form a single block of cancellation. By the way, if you don't need to cancel `collectedStatsPromise` above use `dependent(PromiseOrigin.THIS_ONLY)` decorator - it doesn't enlist "side-passed" promises.

Well, but what about obeying `CompletionStage` contract? Is it still safe to return `myPromise` to clients that are unaware of its overwritten behavior? It's not. You should revert the effect of the `dependent(PromiseOrigin.ALL)` decorator before passing `myPromise` outside:
```java
public Promise<?> forkWmiProcessingChain(Promise<Stats collectedStatsPromise) {
  Promise<?> myPromise = 
  CompletableTask.supplyAsync(() -> candidate, pool)
                 .dependent(PromiseOrigin.ALL) 
                 .thenApplyAsync(CheckIp::doWork)
                 .thenApplyAsync(CheckType::doWork)
                 ...
                 .exceptionally(ExceptionHandlerService::handle);
  return myPromise.unwrap(); // or myPromise.raw() in this case               
}
```

The final operator in the function above - `unwrap()` removes the latest decorator applied, the `dependent(...)`. Looking ahead, I should mention that `raw()` will remove _all_ the decorators applied. Yes, there are several possible - and the `dependent(...)` is just one of them. In the next chapters we discuss `defaultAsyncOn` decorator and later you will see custom decorators (the ones not directly bound to the `Promise` API as methods).

## 4. Wait! Wait! It's not cancellable!!!
One of the most common complains in the issue tracker of Tascalate Concurrent project is a variation of the report "code is not cancelled when requested". The symptoms are typical: `Promise` is cancelled, any dependent `Promise`-s are completed exceptionally (as expected) but threads keep running till the end of the code submitted to an executor. So the primary promise of the library is broken: execution is not interrupted.

Is library broken? No. Is user code correct? Well, yes. But library code and user code do not cooperate well to really cancel asynchronous execution.

The promise returned from `CompletableTask` methods like `supplyAsync` / `runAsync` as well as any derived `Promise` (via `thenApply` or `thenRun`) does the following two things:
1. Set own state to _cancelled_ and let transitions to any dependent promises run (same as `CompletableFuture`)
2. Interrupt the current `Thread` via `Thread.currentThread().interrupt()` (unlike `CompletableFuture`)

And it's responsibility of the user code - all of these `Runnable`, `Consumer<T>` and `Function<T,R>` passed - to react on the `Thread.currentThread().isInterrupted()` flag. Let us consider typical scenarios.
1. User code is a computational code and do not use I/O.

In this case you have to put explicit checks for `Thread.currentThread().isInterrupted()` to break execution via either breaking loops, early returns or throwing exception:

```java
outer:
for (int i = 0; i < SOME_LARGE_SIZE; i++) {
  for (int j = 0; j < SOME_EVEN_LARGER_SIZE; j++) {
    if (Thread.currentThread().isInterrupted()) {
      break outer;
    }
    // some calcs here
  }
}
```

It's up to library user to select how often to check the flag: there are always trade-offs between overhead for regular execution time (when execution is not cancelled) and the delay before thread react on cancellation. In the example above the check may be moved from the inner loop to the outer loop - this will speed-up normal execution path but the code will react on the cancellation with delay.

Some API provides good built-in points to check for early exit, the good example is [java.nio.file.FileVisitor](https://docs.oracle.com/javase/7/docs/api/java/nio/file/FileVisitor.html) + [java.nio.file.Files walkFileTree](https://docs.oracle.com/javase/7/docs/api/java/nio/file/Files.html#walkFileTree(java.nio.file.Path,%20java.nio.file.FileVisitor)). Here you can just put checks in `previsitDirectory` / `visitFile`:

```java
@Test
@SuppressWarnings("unchecked")
public void testReallife() throws Exception {
  ElapsedTime time = new ElapsedTime();
  Executor executor = Executors.getDefaultExecutor();
  Promise<Void> promise = CompletableTask.runAsync(() -> {
    try {
      Files.walkFileTree(Paths.get("d:/"), new FileVisitor<>() {
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
          System.out.printf("[%d ms] [%s] Task#%d: visiting dir %s%n", 
                            time.current(), Thread.currentThread(), id, dir);
          if (Thread.currentThread().isInterrupted()) {
            // STOP when interrupted
            return FileVisitResult.TERMINATE;
          } else {
            return FileVisitResult.CONTINUE;
          }
        }

      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {...}
      public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {...}
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {...}
  }, executor);
  Thread.sleep(3000);
  System.out.printf("[%d ms] canceling%n", time.current());
  promise.cancel(true);
  assertCanceled(promise);
}
```

2. You are using old blocking API of `java.io.InputStream` or `java.net.URLConnection`
Probably, your code uses old blocking streams from `java.io` like `java.io.FileInputStream` or `SocketInputStream` opened from `java.net.Socket`. Or you are using `java.net.URLConnection`. Or outdated blocking Apache HTTP Client. These all prevents running thread from termination once interrupted -- all of these API-s are blocking and do not react on interruptions. The options are:
- If you are using some third-party synchronous networking library then try to replace it with the asynchronous version. For HTTP protocol there is a plenty of options like AsyncHttpClient and OkHttp. Both returns `CompletableFuture` as operation results and therefore could be easily plugged with the rest of Tascalate Concurrent code.
- If you are using blocking file API try to replace it with NIO version, it's the best option:

```java
try (InputStream in = new FileInputStream("E:/Downloads/LargeFile.rar")) {
... // Work with input stream
}

==>
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
...
try (InputStream in = 
  Channels.newInputStream(FileChannel.open(Paths.get("E:/Downloads/LargeFile.rar"), 
                                           StandardOpenOption.READ)))) {
... // Work with input stream
} 
// Same could be done for sockets
```
- If don't like to use new NIO API, but sources with blocking IO may be altered, then `BlockingIO` class provided by Tascalate Concurrent can help:

```java
Promise<?> promise = CompletableTask.supplyAsync(
  BlockingIO.interruptible( // Wrap Supplier into interruptible Supplier
    () -> {
      // Register InputStream after creation as interruption target
      try (InputStream in = BlockingIO.register(new FileInputStream("E:/Downloads/LargeFile.rar"))) {
        ... // Work with input stream
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      } 
    }
  )
);
...
promise.cancel(true);
```
`BlockingIO.interruptible(...)` can wrap as interruptible all the functional interfaces used in `CompletionStage` / `Promise` API. The parameter to the `BlockingIO.register` should be any implementation of `java.lang.Closeable` that will be closed on thread interruption. For `InputStream` or `OutputStream` it is just the stream itself. For `java.net.HttpUrlConnection` you can use

```java
HttpURLConnection conn = (HttpURLConnection)(new URL(url).openConnection());
BlockingIO.register(conn::disconnect);
```

`BlockingIO` is a late addition to the Tascalate Concurrent, it's available only since version [0.9.5](https://github.com/vsilaev/tascalate-concurrent/releases/tag/0.9.5).

Unfortunately, it's necessary to put this statement straight at the end: if the blocking API is hidden behind third-party library code (like Spring RestTemplate) then you are out of luck - Tascalate Concurrent will not support true interruptions in this scenario. 

## 5. Overriding default asynchronous executor
One of the pitfalls of the `CompletableFuture` implementation is how it works with default asynchronous executor. Consider the following example:
```java
CompletionStage<String> p1 = CompletableFuture.supplyAsync(this::produceValue, executorInitial);
CompletionStage<String> p2 = p1.thenApplyAsync(this::transformValueA);
CompletionStage<String> p3 = p2.thenApplyAsync(this::transformValueB, executorNext);
CompletionStage<String> p4 = p3.thenApplyAsync(this::transformValueC);
```
The call to `produceValue` will be executed on the `executorInitial` - it is passed explicitly. However, the call to `transformValueA` will be excuted on... `ForkJoinPool.commonPool()`! Hmmmm... Probably this makes sense, but how to force using alternative executor by default? No way! Probably this is possible with deeper calls? The answer is "NO" again! The invocation to `transformValueB` ran on explicitly supplied `executorNext`. But next call, `transformValueC` will be executed on... you guess it... `ForkJoinPool.commonPool()`!

So, once you use `CompletableFuture` with JEE environment you must pass explicit instance of [ManagedExecutorService](https://docs.oracle.com/javaee/7/api/javax/enterprise/concurrent/ManagedExecutorService.html) to each and every method call. Not very convenient! To be fair, with Java 9+ API you can redefine this behavior via sub-classing `CompletableFuture` and overriding two methods: [defaultExecutor](https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/CompletableFuture.html#defaultExecutor--) and [newIncompleteFuture](https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/CompletableFuture.html#newIncompleteFuture--). Plus, you will have to define your own "entry points" instead of the standard `CompletableFuture.runAsync` and `CompletableFuture.supplyAsync`. 

With `CompletableTask` the situation is just the opposite. Let us rewrite the example above:
```java
CompletionStage<String> p1 = CompletableTask.supplyAsync(this::produceValue, executorInitial);
CompletionStage<String> p2 = p1.thenApplyAsync(this::transformValueA);
CompletionStage<String> p3 = p2.thenApplyAsync(this::transformValueB, executorNext);
CompletionStage<String> p4 = p3.thenApplyAsync(this::transformValueC);
```
The call to `produceValue` will be executed on the `executorInitial`, obviously. But now, the call to `transformValueA` will be executed also on `executorInitial`! What's about deeper calls? The invocation to `transformValueB` ran on explicitly supplied `executorNext`. And next call, `transformValueC` will be executed on... check your intuition... `executorNext`. The logic behinds this is the following: the latest explicitly specified `Executor` is what will be used for all nested asynchronous composition methods without an explicit `Executor` parameter.

Obviously, it's rarely the case when one size fits all. Since release [0.6.6](https://github.com/vsilaev/tascalate-concurrent/releases/tag/0.6.6) there are two new options to specify default asynchronous executor:
1. `CompletableTask` has an overloaded method:
```java
public static Promise<Void> asyncOn(Executor executor, boolean enforceDefaultAsync)
```
When `enforceDefaultAsync` is `true` then all nested asynchronous composition methods without explicit `Executor` parameter will use the provided executor, even if previous composition methods use alternative `Executor`. This is somewhat similar to `CompletableFuture` but with the ability to explicitly set the default asynchronous executor initially.

2. `Promise` interface has the following operation:
```java
Promise<T> defaultAsyncOn(Executor executor)
```
The returned decorator will use the specified executor for all nested asynchronous composition methods without explicit `Executor` parameter. So, at any point, you are able to switch to the desired default asynchronous executor and keep using it for all nested composition call.

To summarize, with Tascalate Concurrent you have the following options to control what is the default asynchronous executor:
1. The latest explicit `Executor` passed to `*Async` method is used for derived `Promise`-s - the default option.
2. Single default `Executor` passed to the root `CompletableTask.asyncOn(Executor executor, true)` call is propagated through the whole chain. This is the only variant supported with `CompletableFuture` in Java 9+, though, with custom coding.
3. Redefine `Executor` with `defaultAsyncOn(Executor executor)` for all derived `Promise`-s.
Having the best of three(!) worlds, the only responsibility of the library user is to use these options consistently! 

The last thing that should be mentioned is a typical task when you would like to start interruptible blocking method after completion of the standard `CompletableFuture`. The following utility method is defined in the `CompletableTask`:
```java
public static <T> Promise<T> waitFor(CompletionStage<T> stage, Executor executor)
```
Roughly, this is a shortcut for the following:
```java
CompletableTask.asyncOn(executor).thenCombine(stage, (u, v) -> v);
```
A typical usage of this method is:
```java
TaskExecutorService executorService = TaskExecutors.newFixedThreadPool(3);
CompletableFuture<String> replyUrlPromise = sendRequestAsync();
Promise<byte[]> dataPromise = CompletableTask.waitFor(replyUrlPromise, executorService)
    .thenApplyAsync(url -> loadDataInterruptibly(url));
```
The `dataPromise` returned may be cancelled later and `loadDataInterruptibly` will be interrupted if not completed by that time.

## 6. Timeouts
Any robust application requires certain level of functionality, that handles situations when things go wrong. An ability to cancel a hanged operation existed in the library from the day one, but, obviously, it is not enough. Cancellation per se defines "what" to do in face of the problem, but the responsibility "when" to do was left to an application code. Starting from release [0.5.4](https://github.com/vsilaev/tascalate-concurrent/releases/tag/0.5.4) the library fills the gap in this functionality with timeout-related stuff.

An application developer now has the following options to control execution time of the `Promise` (declared in `Promise` interface itself):
```java
<T> Promise<T> orTimeout(long timeout, TimeUnit unit[, boolean cancelOnTimeout = true])
<T> Promise<T> orTimeout(Duration duration[, boolean cancelOnTimeout = true])
```
These methods creates a new `Promise` that is either settled successfully/exceptionally when original promise is completed within a timeout given; or it is settled exceptionally with a [TimeoutException](https://docs.oracle.com/javase/8/docs/api/index.html?java/util/concurrent/TimeoutException.html) when time expired. In any case, handling code is executed on the default asynchronous [Executor](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Executor.html) of the original `Promise`.
```java
Executor myExecutor = ...; // Get an executor
Promise<String> callPromise = CompletableTask
    .supplyAsync( () -> someLongRunningIoBoundMehtod(), myExecutor )
    .orTimeout( Duration.ofSeconds(3) );
    
Promise<?> nextPromiseSync = callPromise.whenComplete((v, e) -> processResultSync(v, e));
Promise<?> nextPromiseAsync = callPromise.whenCompleteAsync((v,e) -> processResultAsync(v, e));
```
In the example above `callPromise` will be settled within 3 seconds either successfully/exceptionally as a result of the `someLongRunningIoBoundMehtod` execution, or exceptionally with a `TimeoutException`.

It's worth to mention, that _both_ `processResultSync` and `processResultAsync` will be executed with `myExecutor`, if timeout is triggered - this rule is true for all timeout-related methods.

The optional `cancelOnTimeout` parameter defines whether or not to cancel the original `Promise` when time is expired; it is implicitly true when omitted. So in example above `the someLongRunningIoBoundMehtod` will be interrupted if it takes more than 3 seconds to complete. Pay attention: any `Promise` is cancellable on timeout, even wrappers created via `Promises.from(stage)`, but only `CompletableTask` is interruptible!

Cancelling original promise on timeout is a desired behavior in most cases but not always. In reality, "Warn-first-Cancel-next"  scenarios are not rare, where "warn" may be logging, sending notification emails, showing messages to user on UI etc. The library provides an option to set several non-cancelling timeouts like in the example below:
```java
Executor myExecutor = ...; // Get an executor
Promise<String> resultPromise = CompletableTask
    .supplyAsync( () -> someLongRunningIoBoundMehtod(), executor );

// Show UI message to user to let him/her know that everything is under control
Promise<?> t1 = resultPromise
    .orTimeout( Duration.ofSeconds(2), false )
    .exceptionally( e -> {
      if (e instanceof TimeoutException) {
        UI.showMessage("The operation takes longer than expected, please wait...");
      }
      return null;
    }, false); 

// Show UI confirmation to user to let him/her cancel operation explicitly
Promise<?> t2 = resultPromise
    .orTimeout( Duration.ofSeconds(5), false )
    .exceptionally( e -> {
      if (e instanceof TimeoutException) {
        UI.clearMessages();
        UI.showConfirmation("Service does not respond. Do you whant to cancel (Y/N)?");
      }
      return null;
    }, false); 

// Cancel in 10 seconds
resultPromise.orTimeout( Duration.ofSeconds(10), true );
```
Please note that the timeout is started from the call to the `orTimeout` method. Hence, if you have a chain of unresolved promises ending with the `orTimeout` call then the whole chain should be completed within the time given:
```java
Executor myExecutor = ...; // Get an executor
Promise<String> parallelPromise = CompletableTask
    .supplyAsync( () -> someLongRunningDbCall(), executor );
Promise<List<String>> resultPromise = CompletableTask
    .supplyAsync( () -> someLongRunningIoBoundMehtod(), executor )
    .thenApplyAsync( v -> converterMethod(v) )
    .thenCombineAsync(parallelPromise, (u, v) -> Arrays.asList(u, v))
    .orTimeout( Duration.ofSeconds(5) );
```
In the latest example `resultPromise` will be resolved successfully if and only if all of `someLongRunningIoBoundMehtod`, `converterMethod` and even `someLongRunningDbCall` are completed within 5 seconds. If it's necessary to restrict execution time of the single step, please use standard `CompletionStage.thenCompose` method. Say, that in the previous example we have to restrict execution time of the `converterMethod` only. Then the modified chain will look like:
```java
Promise<List<String>> resultPromise = CompletableTask
    .supplyAsync( () -> someLongRunningIoBoundMehtod(), executor )
    // Restict only execution time of converterMethod
    // -- start of changes
    .thenCompose( v -> 
        CompletableTask.completed(v, executor)
                       .thenApplyAsync(vv -> converterMethod(vv))
                       .orTimeout( Duration.ofSeconds(5) )
    )
    // -- end of changes
    .thenCombineAsync(parallelPromise, (u, v) -> Arrays.asList(u, v))
    ;
```

Moreover, in the _original_ example only the call to the `thenCombineAsync` will be cancelled on timeout (the last in the chain), to cancel the whole chain please use the functionality of the `DependentPromise` interface (will be discussed later):
```java
Executor myExecutor = ...; // Get an executor
Promise<String> parallelPromise = CompletableTask
    .supplyAsync( () -> someLongRunningDbCall(), executor );
Promise<List<String>> resultPromise = CompletableTask
    .supplyAsync( () -> someLongRunningIoBoundMehtod(), executor )
    .dependent()
    // enlist promise of someLongRunningIoBoundMehtod for cancellation
    .thenApplyAsync( v -> converterMethod(), true )  
    // enlist result of thenApplyAsync and parallelPromise for cancellation
    .thenCombineAsync(parallelPromise, (u, v) -> Arrays.asList(u, v), PromiseOrigin.ALL)
    .orTimeout( Duration.ofSeconds(5) ); // now timeout will cancel the whole chain
```

Another useful timeout-related methods declared in `Promise` interface are:
```java
<T> Promise<T> onTimeout(T value, long timeout, TimeUnit unit[, boolean cancelOnTimeout = true])
<T> Promise<T> onTimeout(T value, Duration duration[, boolean cancelOnTimeout = true])
<T> Promise<T> onTimeout(Supplier<? extends T>, long timeout, TimeUnit unit[, boolean cancelOnTimeout = true])
<T> Promise<T> onTimeout(Supplier<? extends T>, Duration duration[, boolean cancelOnTimeout = true])
```
The `onTimeout` family of methods are similar in all regards to the `orTimeout` methods with the single obvious difference - instead of completing resulting `Promise` exceptionally with the `TimeoutException` when time is expired, they are settled successfully with the alternative `value` supplied (either directly or via [Supplier](https://docs.oracle.com/javase/8/docs/api/java/util/function/Supplier.html)):
```java
Executor myExecutor = ...; // Get an executor
Promise<String> callPromise = CompletableTask
    .supplyAsync( () -> someLongRunningIoBoundMehtod(), executor )
    .onTimeout( "Timed-out!", Duration.ofSeconds(3) );
```
The example shows, that `callPromise` will be settled within 3 seconds either successfully/exceptionally as a result of the `someLongRunningIoBoundMehtod` execution, or with a default value `"Timed-out!"` when time exceeded.

It's important to mention the crucial difference between `Promise.orTimeot / onTimeout` and `CompletableFuture.orTimeout / completeOnTimeout` in Java 9+. In Tascalate Concurrent both operations return a _new_ `Promise`, that is may be canceled individually, without cancelling the original `Promise`. Moreover, the original `Promise` will not be completed with `TimeoutException` when time expired but rather with the `CancellationException` (in the case of `orTimeout([duration], true)` or `orTimeout([duration])`). The behavior of `CompletableFuture` in Java 9+ is radically different: timeout-related operations are just "side-effects", and the returned value is the original `CompletableFuture` itself. So the call to `completableFuture.orTimeout(100, TimeUnit.MILLIS).cancel()` will cancel the `completableFuture` itself, and there is no way to revert the timeout once it's set. Correspondingly, when time expired the original `completableFuture` will be completed exceptionally with `TimeoutException`.

Finally, the `Promise` interface provides an option to insert delays into the call chain:
```java
<T> Promise<T> delay(long timeout, TimeUnit unit[, boolean delayOnError = true])
<T> Promise<T> delay(Duration duration[, boolean delayOnError = true])
```
The delay is started only after the original `Promise` is completed either successfully or exceptionally (unlike `orTimeout` / `onTimeout` methods where timeout is started immediately). The resulting delay `Promise` is resolved after the timeout specified with the same result as the original `Promise`. The latest methods' argument - `delayOnError` - specifies whether or not we should delay if original Promise is resolved exceptionally, by default this argument is `true`. If `false`, then delay `Promise` is completed immediately after the failed original `Promise`. 
```java
Executor myExecutor = ...; // Get an executor
Promise<String> callPromise1 = CompletableTask
    .supplyAsync( () -> someLongRunningIoBoundMehtod(), executor )
    .delay( Duration.ofSeconds(1) ) // Give a second for CPU to calm down :)
    .thenApply(v -> convertValue(v));
    
Promise<String> callPromise2 = CompletableTask
    .supplyAsync( () -> aletrnativeLongRunningIoBoundMehtod(), executor )
    .delay( Duration.ofSeconds(1), false ) // Give a second for CPU to calm down ONLY on success :)
    .thenApply(v -> convertValue(v));
```
Like with other timeout-related methods, `convertValue` is invoked on the default asynchronous `Executor` of the original `Promise`.  

You may notice, that delay may be introduced only in the middle of the chain, but what to do if you'd like to back-off the whole chain execution? Just start with a resolved promise!
```java
// Option 1
// Interruptible tasks chain on the executor supplied
CompletableTask.asyncOn(executor)
    .delay( Duration.ofSeconds(5) )
    .thenApplyAsync(ignore -> produceValue());

// Option2
// Computational tasks on ForkJoinPool.commonPool()
Promises.from(CompletableFuture.completedFuture(""))
    .delay( Duration.ofSeconds(5) )
    .thenApplyAsync(ignore -> produceValue());
```
As long as back-off execution is not a very rare case, the library provides the following convenient shortcuts in the `CompletableTask` class:
```java
static Promise<Duration> delay(long timeout, TimeUnit unit, Executor executor);
static Promise<Duration> delay(Duration duration, Executor executor);
```
Notice, that in Java 9+ a different approach is chosen to implement delays - there is no corresponding operation defined for the `CompletableFuture` object and you should use delayed `Executor`. Please read documentation on the [CompletableFuture.delayedExecutor](https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/CompletableFuture.html#delayedExecutor-long-java.util.concurrent.TimeUnit-java.util.concurrent.Executor-) method for details.


## 7. Combining multiple CompletionStages

The standard `CompletableFuture.allOf()` and `anyOf()` methods are notoriously limited: `allOf` returns `CompletableFuture<Void>` (discarding individual results), and `anyOf` returns `Object` (requiring unsafe casting). Furthermore, they strictly require `CompletableFuture` instances, ignoring the broader `CompletionStage` interface.

Tascalate Concurrent provides a rich, type-safe suite of combinators in the `Promises` utility class that solve these issues and add powerful features like partial success, strict/non-strict error handling, and eager cancellation.

### Why use `Promises` combinators?
1. **Interface Agnostic:** Works with *any* `CompletionStage` implementation without forcing conversion to `CompletableFuture`.
2. **Type-Safe Results:** Returns strongly-typed `List<T>`, `Map<K, T>`, or `T` instead of `Void` or `Object`.
3. **Flexible Inputs:** Accepts varargs arrays, `List`s, or `Map`s.
4. **Eager Cancellation:** Automatically cancels remaining stages once the final outcome is determined.
5. **Partial Success:** Supports "M out of N" success scenarios.
6. **Strict vs. Non-Strict Errors:** Choose whether to tolerate individual failures or fail fast.

### The Core Combinators

| Method | Success Condition | Failure Condition | Result Type |
| :--- | :--- | :--- | :--- |
| **`all`** | **All** N stages succeed | **Any 1** stage fails | `List<T>` or `Map<K,T>` |
| **`any`** | **Any 1** stage succeeds | **All** N stages fail | `T` (first success) |
| **`anyStrict`** | **Any 1** stage succeeds | **Any 1** fails *(before 1st success)* | `T` (first success) |
| **`atLeast(M)`** | **M** stages succeed | **N-M+1** stages fail | `List<T>` or `Map<K,T>` |
| **`atLeastStrict(M)`**| **M** stages succeed | **Any 1** fails *(before M successes)*| `List<T>` or `Map<K,T>` |
| **`atLeastOrdered(M)`**| **M** stages succeed | **N-M+1** stages fail | `List<Optional<T>>` |

#### 1. `all` — Wait for everything
Completes successfully only when every provided stage succeeds. If any stage fails, the combined promise fails immediately with a `MultitargetException`.
```java
Promise<List<User>> users = Promises.all(fetchUser(1), fetchUser(2), fetchUser(3));
```
#### 2. `any` vs `anyStrict` — Race for the first success

Both methods return the result of the _first_ stage that completes successfully.

-   **`any` (Non-Strict):** Ignores exceptions. If 3 stages fail but the 4th succeeds, the combined promise succeeds. It only fails if _all_ stages fail.
-   **`anyStrict` (Strict):** Fails fast. If _any_ stage throws an exception before a successful result is found, the combined promise immediately fails with that exact exception.

#### 3. `atLeast` — M out of N successes

A generalization of `any`. It succeeds if at least `M` stages complete normally. The resulting `List` contains the successful values at their original indices, with `null` in the positions of failed or uncompleted stages.
```java
// Succeeds if at least 2 out of 3 DNS servers respond
Promise<List<String>> dnsResults = Promises.atLeast(2, queryDns1(), queryDns2(), queryDns3());
```
#### 4. `atLeastOrdered` — Safe handling of `null` results

Because `atLeast` uses `null` to represent failed/uncompleted stages in the result list, it cannot distinguish between a stage that failed and a stage that successfully returned `null`. `atLeastOrdered` solves this by returning a `List<Optional<T>>`.
```java
Promise<List<Optional<CacheEntry>>> cacheLookups = 
    Promises.atLeastOrdered(2, cache1.get(), cache2.get());
```
### Collection Types: Arrays, Lists, and Maps

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
### Eager Cancellation (`cancelRemaining`)

Most combinators accept an optional `boolean cancelRemaining` parameter (which defaults to `true`). When the outcome of the combined operation is mathematically determined (e.g., the first success in `any`, or the first failure in `all`), the combinator will automatically invoke `.cancel(true)` on all remaining, uncompleted stages. This frees up network connections and thread pool resources immediately.

### Error Handling and `MultitargetException`

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

### Characteristics of the Returned Promise

1.  **Cancellation Propagation:** Manually cancelling the returned combined `Promise` will cascade and cancel _all_ underlying `CompletionStage` arguments.
2.  **Default Executor:** The default asynchronous executor for subsequent operations (like `.thenApplyAsync`) on the combined promise is undefined (it inherits from the underlying stages). To guarantee a specific executor for downstream operations, chain `.defaultAsyncOn(myExecutor)` immediately after the combinator.

## 8. Asynchronous Loops and Iteration

When working with asynchronous APIs, traditional `for` or `while` loops fall short because they do not wait for asynchronous operations to complete. If you need to process a sequence of items one by one (e.g., paginating through an API, processing files sequentially, or polling a resource), firing all requests at once with `Promises.all()` might overwhelm the target system or exhaust local resources.

Tascalate Concurrent provides two stack-safe, cancellable constructs for sequential asynchronous execution: `Promises.loop` and `Promises.forEach`.

### 1. `Promises.loop` — The Asynchronous `while` Loop

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
### 2. `Promises.forEach` — Sequential Iteration with Early Exit

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
### 3. Cancellation and Stack Safety

Both `loop` and `forEach` return a standard `Promise`, which means they fully support cancellation. If you cancel the returned promise, Tascalate Concurrent will automatically attempt to cancel the `CompletionStage` that is currently executing in the loop body.
```java
Promise<Void> longRunningTask = Promises.forEach(urls, url -> downloadAsync(url));

// Later, if the user cancels the operation:
longRunningTask.cancel(true); 
// The current download is interrupted, and subsequent URLs are skipped.
```
Furthermore, the underlying `AsyncLoop` implementation is **stack-safe**. If your asynchronous body happens to return an already-completed stage (e.g., a cached result, a synchronous computation wrapped in a promise, or an empty collection in `forEach`), the loop will "trampoline" to the next iteration without growing the call stack. This prevents `StackOverflowError` even for millions of synchronous iterations.


## 9. Chunked processing with bounded concurrency

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
### Execution model

-   **Bounded concurrency.** Values are pulled from the source lazily, at most `chunkSize` at a time; the next chunk is not pulled until the current one completes. At any moment at most `chunkSize` spawned tasks are in flight — this is the library's built-in backpressure.
-   **Ordered collection.** Chunks are processed sequentially and results within a chunk keep their positional order, so ordered collectors (`toList()`, `joining()`, `toMap()`, ...) produce results in the source encounter order. Each chunk is accumulated into a fresh container and merged via the collector's `combiner` — therefore the combiner must be correctly implemented (all standard `Collectors` are).
-   **Empty sources.** An empty input completes successfully with the collector's empty result (`supplier()` followed by `finisher()`).
-   **Failure and cancellation.** If a spawned task fails, the whole operation fails and the remaining in-flight tasks of the current chunk are cancelled. Cancelling the returned promise stops the loop — no further chunks are started.

### Example 1 — bounded parallel fetch

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
### Example 2 — collecting into a map

Any collector works; collisions and merge semantics follow the collector itself:
```java
Promise<Map<Long, Profile>> profileMap = Promises.chunked(
    userIds,
    50,
    id -> api.fetchProfile(id),
    Collectors.toMap(Profile::id, Function.identity())
);
```
### Example 3 — Stream source with automatic closing

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
### Example 4 — offloading accumulation

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
### Blocking Consumption: `AsyncCompletions`

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


## 10. Context variables & contextual Promises

Ah, those dreaded `ThreadLocal`s we all hate, love to hate, but, nevertheless, use everywhere. It's quite common to store some contextual data (like the authenticated user and current locale) inside thread-local variables. Sometimes it's custom code, sometimes it's from a third-party library we can't alter. 

Typically, we spawn asynchronous code from a thread with well-known characteristics, like an HTTP request thread. Here we can easily access contextual information. However, using thread-local variables from an asynchronous code block is hard because it's impossible to predict which thread from the pool will execute the code. It's necessary to capture the context of one thread and propagate it to the threads executing the asynchronous code. Furthermore, with the advent of Virtual Threads in modern Java, traditional `ThreadLocal`s can cause memory bloat, making immutable scoped values (`ScopedValue`) a much better choice.

To solve this issue, Tascalate Concurrent provides the `ContextVar` API. It serves as a unified, multi-release abstraction over context storage: it uses `ThreadLocal` on JDK 8–24 and `ScopedValue` on JDK 25+, while also allowing you to wrap custom context stores.

### 1. Defining Context Variables

You can define context variables in several ways depending on your needs:

#### Portable Variables (Recommended)
Uses `ThreadLocal` on JDK 8-24, and `ScopedValue` on JDK 25+. This is the default choice for new code.
```java
static final ContextVar<Principal> CURRENT_PRINCIPAL = ContextVar.newInstance("principal");
static final ContextVar<Locale> CURRENT_LOCALE = ContextVar.newInstance("locale");
```
#### Wrapping Existing ThreadLocals

If you need to integrate with a legacy third-party library that exposes a raw `ThreadLocal`:
```java
static final ThreadLocal<String> LEGACY_TRACE_ID = new ThreadLocal<>();
static final ContextVar<String> TRACE_ID = ThreadLocalVar.of("traceId", LEGACY_TRACE_ID);
```
#### Wrapping Custom Context Stores (e.g., Spring)

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

### 2. Propagation Modes: Strict vs. Optimized

When a context variable is applied to a block of code, what happens to the _existing_ (ambient) value already present on the current thread? Tascalate Concurrent offers two modes to handle this:

-   **Strict Mode (Default):** Before applying the captured context, the library saves the ambient value currently on the thread. After the code block completes (even if an exception is thrown), the original ambient value is restored. This is essential for shared thread pools (like Tomcat worker threads) where a thread might process multiple requests and must not leak or lose context between them.
-   **Optimized Mode:** The library simply overwrites the thread's value with the captured snapshot, and clears it completely when the block finishes. It skips the save/restore step, offering a slight performance gain, but it is **destructive** to any pre-existing context on the thread. Use this only on dedicated, clean worker threads that you know carry no ambient state.

You can switch modes on individual variables or on an entire `ContextRelay`:
```java
ContextRelay<?> optimizedRelay = ContextVar.relay(MY_VAR1, MY_VAR2, MY_VAR3).optimized();
```
_(Note: On JDK 25+, `ScopedValue` bindings are inherently immutable and automatically restoring, so `strict()` and `optimized()` are effectively identical and carry no performance difference.)_

### 2. Synchronous Execution

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
### 3. Asynchronous Execution & Contextual Promises

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

### 4. Undecorating Promises

Copying contextual variables and applying them to every callback carries a slight performance penalty. If you reach a point in your asynchronous chain where the context is no longer needed, you can stop the propagation by using `Promise.raw()` to undecorate the derived promises (as with any other decorator in Tascalate Concurrent):
```java
Promise<Void> afterHttpRequest =
    httpRequest.unwrap() // OR raw()
               .thenRun(() -> {
                   // MyService.CURRENT_PRINCIPAL, MyService.CURRENT_LOCALE are NOT available here
                   cleanupResources();
               });
```
### 5. Contextual Executors

If you prefer to contextualize the executor itself rather than individual promises, you can wrap any `Executor` or `ExecutorService`. The context will be captured dynamically at the exact moment a task is submitted:
```java
ExecutorService contextualExecutor = relay.contextual(myExecutor);

contextualExecutor.submit(() -> {
    // Context is automatically applied here based on the submitting thread's state
    processInBackground();
});
```

## 11. Polling and asynchronous retry functionality

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

### RetryContext — what the operation knows about the loop

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
### RetryPolicy — attempts, delays, timeouts, exceptions, results

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

### `retryOptional` — when "no result yet" is not a failure

`retryOptional` unwraps the `Optional` returned by the operation: an empty `Optional` is passed to the policy as a `null` result. Combined with `rejectNullResult()` this is exactly the "not ready yet — try again" semantics.

### `retryFuture` — retrying genuinely asynchronous operations

`retryFuture` accepts a _factory_ of `CompletionStage`s. A fresh stage is produced on every attempt; both an exception thrown by the factory and an exceptional completion of the produced stage are retryable subject to the policy. The per-attempt timeout applies to the produced stage. Since the asynchronous machinery belongs to the third-party API, no `Executor` parameter is needed.

### When the policy gives up

If the budget is exhausted or an abort rule matches, the returned promise fails with a `RetryException` carrying the attempt count, the duration of the last attempt, and the last error.

### Polling — retry as a first-class citizen

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

## 12. Asynchronous try-with-resources
TBD -- `Promises.tryApply` and `Promises.tryCompose`

## 13. Asynchronous locks
TBD -- `net.tascalate.concurrent.locks`

## 14. Asynchronous I/O extensions

## 15. Decorators for CompletionStage / CompletableFuture / Promise

## 16. Extensions to ExecutorService API

It’s not mandatory to use any specific subclasses of `Executor` with `CompletableTask` – you may use any implementation. However, someone may find beneficial to have a `Promise`-aware `ExecutorService` API. Below is a list of related classes/interfaces:

a. Interface `TaskExecutorService` - the specialization of [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) that uses `Promise` as a result of `submit(...)` methods.

b. Class `ThreadPoolTaskExecutor` - the subclass of the standard [ThreadPoolExecutor](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ThreadPoolExecutor.html) that implements `TaskExecutorService` interface.

c. Class `TaskExecutors` - a drop-in replacement for [Executors](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Executors.html) utility class that returns various useful implementations of `TaskExecutorService` instead of the standard [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html).

# Acknowledgements

Internal implementation details are greatly inspired [by the work](https://github.com/lukas-krecan/completion-stage) done by [Lukáš Křečan](https://github.com/lukas-krecan). The part of the polling / asynchronous retry functionality is adopted from the [async-retry](https://github.com/nurkiewicz/async-retry) library by [Tomasz Nurkiewicz](http://nurkiewicz.com/)

