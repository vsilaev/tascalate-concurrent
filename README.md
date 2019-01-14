[![Maven Central](https://img.shields.io/maven-central/v/net.tascalate.concurrent/net.tascalate.concurrent.lib.svg)](https://search.maven.org/artifact/net.tascalate.concurrent/net.tascalate.concurrent.lib/0.7.1/jar) [![GitHub release](https://img.shields.io/github/release/vsilaev/tascalate-concurrent.svg)](https://github.com/vsilaev/tascalate-concurrent/releases/tag/0.7.1) [![license](https://img.shields.io/github/license/vsilaev/tascalate-concurrent.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)
# tascalate-concurrent
The library provides an implementation of the [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html) interface and related classes these are designed to support long-running blocking tasks (typically, I/O bound). This functionality augments the sole Java 8 built-in implementation, [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html), that is primarily supports computational tasks. Also, the library helps with numerous asynchronous programing challenges like handling timeouts, retry/poll functionality, orchestrating results of multiple concurrent computations and similar.

Since the version [0.7.0](https://github.com/vsilaev/tascalate-concurrent/releases/tag/0.7.0) the library is shipped as a multi-release JAR and may be used both with Java 8 as a classpath library or with Java 9+ as a module.

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
    <groupId>net.tascalate.concurrent</groupId>
    <artifactId>net.tascalate.concurrent.lib</artifactId>
    <version>0.7.1</version>
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

Besides this, there are numerous operators in the `Promise` API to work with timeouts and delays, to override default asynchronous executor and similar. All of them will be discussed later.

When discussing `Promise` interface, it's mandatory to mention the accompanying class `Promises` that provides several useful methods to adapt third-party `CompletionStage` (including the standard `CompletableFuture`) to the `Promise` API. First, there are two unit operations to create successfully/faulty settled `Promise`-es: 
```java
static <T> Promise<T> success(T value)
static <T> Promise<T> failure(Throwable exception)
```
Second, there is an adapter method `from`:
```java
static <T> Promise<T> from(CompletionStage<T> stage)
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

b.	`CompletableTask.complete(T value, Executor executor)`
Same as above, but the starting point is a `Promise` completed with the specified value:
```java
CompletableTask
   .complete("Hello!", myExecutor)
   .thenApplyAsync(myMapper)
   .thenApplyAsync(myTransformer)   
   .thenAcceptAsync(myConsumer)
   .thenRunAsync(myAction);
```  
All of `myMapper`, `myTransformer`, `myConsumer`, `myAction` will be executed using `myExecutor`.

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

## 3. Overriding default asynchronous executor
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

## 4. Timeouts
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
        CompletableTask.complete(v, executor)
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

## 5. Combining several CompletionStage-s.
The utility class `Promises` provides a rich set of methods to combine several `CompletionStage`-s, that lefts limited functionality of `CompletableFuter.allOf / anyOf` far behind:
1. The library works with any `CompletionStage` implementation without resorting to converting arguments to `CompletableFuture` first (and `CompletionStage.toCompletableFuture` is an optional operation, at least it's documented so in Java 8).
2. It's possible to pass either an array or a `List` of `CompletionStage`-s as arguments.
3. The resulting `Promise` let access individual results of the settled `CompletionStage`-s passed.
4. There is an option to cancel non-settled `CompletionStage`-s passed once the result of the operation is known.
5. Optionally you can specify whether or not to tolerate individual failures as long as they don't affect the final result.
6. General _M completed successfully out of N passed promises_ scenario is possible.

Let us review the relevant methods, from the simplest ones to the most advance.

```java
static <T> Promise<List<T>> all([boolean cancelRemaining=true,] CompletionStage<? extends T>... promises)
static <T> Promise<List<T>> all([boolean cancelRemaining=true,] 
                                List<? extends CompletionStage<? extends T>> promises)
````
Returns a promise that is completed normally when all `CompletionStage`-s passed as parameters are completed normally; if any promise completed exceptionally, then resulting promise is completed exceptionally as well.

```java
static <T> Promise<T> any([boolean cancelRemaining=true,] CompletionStage<? extends T>... promises)
static <T> Promise<T> any([boolean cancelRemaining=true,] 
                          List<? extends CompletionStage<? extends T>> promises)
```
Returns a promise that is completed normally when any `CompletionStage` passed as parameters is completed normally (race is possible); if all promises completed exceptionally, then resulting promise is completed exceptionally as well.

```java
static <T> Promise<T> anyStrict([boolean cancelRemaining=true,] CompletionStage<? extends T>... promises)
static <T> Promise<T> anyStrict([boolean cancelRemaining=true,] 
                                List<? extends CompletionStage<? extends T>> promises)
```
Returns a promise that is completed normally when any `CompletionStage` passed as parameters is completed normally (race is possible); if any promise completed exceptionally before the first result is available, then resulting promise is completed exceptionally as well (unlike non-Strict variant, where exceptions are ignored if any result is available at all).

```java
static <T> Promise<List<T>> atLeast(int minResultsCount, [boolean cancelRemaining=true,] 
                                    CompletionStage<? extends T>... promises)
static <T> Promise<List<T>> atLeast(int minResultsCount, [boolean cancelRemaining=true,] 
                                    List<? extends CompletionStage<? extends T>> promises)
```
Generalization of the `any` method. Returns a promise that is completed normally when at least `minResultCount`
of `CompletionStage`-s passed as parameters are completed normally (race is possible); if less than `minResultCount` of promises completed normally, then resulting promise is completed exceptionally.

```java
static <T> Promise<List<T>> atLeastStrict(int minResultsCount, [boolean cancelRemaining=true,] 
                                          CompletionStage<? extends T>... promises)
static <T> Promise<List<T>> atLeastStrict(int minResultsCount, [boolean cancelRemaining=true,] 
                                          List<? extends CompletionStage<? extends T>> promises)
```
Generalization of the `anyStrict` method. Returns a promise that is completed normally when at least `minResultCount` of `CompletionStage`-s passed as parameters are completed normally (race is possible); if any promise completed exceptionally before `minResultCount` of results are available, then resulting promise is completed exceptionally as well (unlike non-Strict variant, where exceptions are ignored if `minResultsCount` of successful results are available).

All methods above have an optional parameter `cancelRemaining` (since library's version [0.5.3](https://github.com/vsilaev/tascalate-concurrent/releases/tag/0.5.3)). When omitted, it means implicitly `cancelRemaining = true`. The `cancelRemaining` parameter defines whether or not to eagerly cancel remaining `promises` once the result of the operation is known, i.e. enough `promises` passed are settled successfully _or_ some `CompletionStage` completed exceptionally in strict version. 

Each operation to combine `CompletionStage`-s has overloaded versions that accept either a [List](https://docs.oracle.com/javase/8/docs/api/java/util/List.html) of `CompletionStage`-s (since version [0.5.4](https://github.com/vsilaev/tascalate-concurrent/releases/tag/0.5.4) of the library) or varagr array of `CompletionStage`-s.

Besides `any`/`anyStrict` methods that return single-valued promise, all other combinator methods return a list of values per every successfully completed promise, at the same indexed position. If the promise at the given position was not settled at all, or failed (in non-strict version), then corresponding item in the result list is `null`. If necessary number or `promises` was not completed successfully, or any one completed exceptionally in strict version, then resulting `Promise` is settled with a failure of the type `MultitargetException`. Application developer may examine `MultitargetException.getExceptions()` to check what is the exact failure per concrete `CompletionStage` passed.

The `Promise` returned has the following characteristics:
1. Cancelling resulting `Promise` will cancel all the `CompletionStage-s` passed as arguments.
2. Default asynchronous executor of the resulting `Promise` is undefined, i.e. it could be either `ForkJoin.commonPool` or whatever `Executor` is used by any of the `CompletionStage` passed as argument. To ensure that necessary default `Executor` is used for subsequent asynchronous operations, please apply `defaultAsyncOn(myExecutor)` on the result.

## 6. DependentPromise
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

## 7. Polling and asynchronous retry functionality
Once you departure from the pure algebraic calculations to the unreliable terrain of the I/O-related functionality you have to deal with failures. Network outage, insuffcient disk space, overloaded third-party servers, exhausted database connection pools - these and many similar infrastructure failures is what application have to cope with flawlessly. And many of the aforementioned issues are temporal by the nature, so it makes sense to re-try after small delay and keep fingers crossed that this time everything will run smoothly. So this is the primary use-case for the retry functionality, or better yet -- asynchronous retry functionality, while all we want our applications be as scalable as possible.

Another related area is polling functionality - unlike infrastructure failures these are sporadic, polling is built-in in certain asynchronous protocol communications. Say, an application sends an HTTP request to generate a report and waits for the known file on FTP server. There is no "asynchronous reply" expected from the third-party server, and the application has to `poll` periodically till the file will be available.

Both use-case are fully supported by the Tascalate Concurrent library. The library provides an API that is both unobtrusive and rich for a wide range of tasks. The following `retry*` methods are available in the `Promises` class:

Provided by utility class Promises but stands on its own
```java
static Promise<Void> retry(Runnable codeBlock, Executor executor, 
                           RetryPolicy<? super Void> retryPolicy);
static Promise<Void> retry(RetryRunnable codeBlock, Executor executor, 
                           RetryPolicy<? super Void> retryPolicy);

static <T> Promise<T> retry(Callable<T> codeBlock, Executor executor, 
                            RetryPolicy<? super T> retryPolicy);
static <T> Promise<T> retry(RetryCallable<T, T> codeBlock, Executor executor, 
                            RetryPolicy<? super T> retryPolicy);
    
static <T> Promise<T> retryOptional(Callable<Optional<T>> codeBlock, Executor executor, 
                                    RetryPolicy<? super T> retryPolicy);
static <T> Promise<T> retryOptional(RetryCallable<Optional<T>, T> codeBlock, Executor executor, 
                                    RetryPolicy<? super T> retryPolicy);
    
static <T> Promise<T> retryFuture(Callable<? extends CompletionStage<T>> invoker, 
                                  RetryPolicy<? super T> retryPolicy);
static <T> Promise<T> retryFuture(RetryCallable<? extends CompletionStage<T>, T> invoker, 
                                  RetryPolicy<? super T> retryPolicy);
```
All the methods from `retry` family share the same pattern. First, there is a block of code that is executed per every attempt. It could be either a full block of the asynchronous code (`retry` and `retryOptional`) or a method that returns a CompletionStage<T> from third-party API like Async Http library (`retryFuture`). Next, if we retry custom code block, then it's necessary to provide an `Executor` it should be run on. For `retryFuture` there is no explicit `Executor`, and it's up to the third-party library to provide scalable and robust `Executor` as a default asynchronous executor of the returned `CompletionStage`. Finally, `RetryPolicy` should be specified that provides a lot of customization options:
1. How much attempts should be made?
2. What is a time interval between attempts? Should it be fixed or dynamic?
3. What is a timeout before a single attempted is considered "hanged"? Should it be dynamic?
4. What exceptions are re-trieable and what are not?
5. What result is expected to be valid? Is a `null` result valid? Is any non-`null` result valid or some returned object properties should be examined?
    
All in all, `RetryPolicy` is provides an API with endless customizations per every imaginable use-case.
But before discussing it, it's necessary to explain a difference in each pair of methods. Why there are overloads with `Runnable` vs `RetryRunnable` and `Callable` vs `RetryCallable`? The reason is the following:
1. Contextless retriable operations are captured as `Runnable` or `Callable` lambdas - they behaves the same for every iteration, and hence do not need a context.
2. Methods with `RetryRunnable` and `RetryCallable` are contextual and may dynamically alter their behavior for the given iteration depending on the context passed. The `RetryContext` provides provides all necessary iteration-specific information. 


## 8. Extensions to ExecutorService API

It’s not mandatory to use any specific subclasses of `Executor` with `CompletableTask` – you may use any implementation. However, someone may find beneficial to have a `Promise`-aware `ExecutorService` API. Below is a list of related classes/interfaces:

a. Interface `TaskExecutorService`

Specialization of [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) that uses `Promise` as a result of `submit(...)` methods.

b. Class `ThreadPoolTaskExecutor`
A subclass of the standard [ThreadPoolExecutor](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ThreadPoolExecutor.html) that implements `TaskExecutorService` interface.

c. Class `TaskExecutors`

A drop-in replacement for [Executors](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Executors.html) utility class that returns various useful implementations of `TaskExecutorService` instead of the standard [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html).

# Acknowledgements

Internal implementation details are greatly inspired [by the work](https://github.com/lukas-krecan/completion-stage) done by [Lukáš Křečan](https://github.com/lukas-krecan). The part of the polling / asynchronous retry functionality is adopted from the [async-retry](https://github.com/nurkiewicz/async-retry) library by [Tomasz Nurkiewicz](http://nurkiewicz.com/)

