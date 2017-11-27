[![Maven Central](https://img.shields.io/maven-central/v/net.tascalate.concurrent/net.tascalate.concurrent.lib.svg)](https://search.maven.org/#artifactdetails%7Cnet.tascalate.concurrent%7Cnet.tascalate.concurrent.lib%7C0.5.3%7Cpom) [![GitHub release](https://img.shields.io/github/release/vsilaev/tascalate-concurrent.svg)](https://github.com/vsilaev/tascalate-concurrent/releases/tag/0.5.3) [![license](https://img.shields.io/github/license/vsilaev/tascalate-concurrent.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)
# tascalate-concurrent
The library provides an implementation of the CompletionStage interface and related classes these are designed to support long-running blocking tasks (typically, I/O bound) - unlike Java 8 built-in implementation, [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html), that is primarily supports computational tasks.
# Why a CompletableFuture is not enough?
There are several shortcomings associated with [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) implementation that complicate its usage for blocking tasks:
1.	`CompletableFuture.cancel()` [method](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html#cancel-boolean-) does not interrupt underlying thread; it merely puts future to exceptionally completed state. So if you use any blocking calls inside functions passed to `thenApplyAsync` / `thenAcceptAsync` / etc these functions will run till the end and never will be interrupted. Please see [CompletableFuture can't be interrupted](http://www.nurkiewicz.com/2015/03/completablefuture-cant-be-interrupted.html) by Tomasz Nurkiewicz
2.	By default, all `*Async` composition methods use `ForkJoinPool.commonPool()` ([see here](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html#commonPool--)) unless explicit [Executor](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Executor.html) is specified. This thread pool shared between all [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html)-s, all parallel streams and all applications deployed on the same JVM. This hard-coded, unconfigurable thread pool is completely outside of our control, hard to monitor and scale. Therefore you should always specify your own Executor.
3.	Additionally, built-in Java 8 concurrency classes provides pretty inconvenient API to combine several [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html)-s. `CompletableFuture.allOf` / `CompletableFuture.anyOf` methods accept only [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) as arguments; you have no mechanism to combine arbitrary [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html)-s without converting them to [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) first. Also, the return type of the aforementioned `CompletableFuture.allOf` is declared as `CompletableFuture<Void>` - hence you are unable to extract conveniently individual results of the each future supplied. `CompletableFuture.anyOf` is even worse in this regard; for more details please read on here: [CompletableFuture in Action](http://www.nurkiewicz.com/2013/05/java-8-completablefuture-in-action.html) (see Shortcomings) by Tomasz Nurkiewicz

# How to use?
Add Maven dependency
```xml
<dependency>
    <groupId>net.tascalate.concurrent</groupId>
    <artifactId>net.tascalate.concurrent.lib</artifactId>
    <version>0.5.3</version>
</dependency>
```

# What is inside?
## 1.	Promise interface
The interface may be best described by the formula: 
```java
Promise == CompletionStage + Future
```

I.e., it combines both blocking [Future](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Future.html)’s API, including `cancel(boolean mayInterruptIfRunning)` [method](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Future.html#cancel-boolean-), AND composition capabilities of [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html)’s API. Importantly, all composition methods of [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html) API (`thenAccept`, `thenCombine`, `whenComplete` etc.) are re-declared to return `Promise` as well.

## 2. CompletableTask 
This is why this project was ever started. `CompletableTask` is the implementation of the `Promise` API for long-running blocking tasks. There are 2 unit operations to create a `CompletableTask`:

a.	`CompletableTask.asyncOn(Executor executor)`

Returns a resolved no-value `Promise` that is “bound” to the specified executor. I.e. any function passed to composition methods of `Promise` (like `thenApplyAsync` / `thenAcceptAsync` / `whenCompleteAsync` etc.) will be executed using this executor unless executor is overridden via explicit composition method parameter. Moreover, any nested composition calls will use same executor, if it’s not redefined via explicit composition method parameter:
```java
CompletableTask
  .asyncOn(myExecutor)
  .thenApplyAsync(myValueGenerator)
  .thenAcceptAsync(myConsumer)
  .thenRunAsync(myAction);
```  
All of `myValueGenerator`, `myConsumer`, `myActtion` will be executed using `myExecutor`.

b.	`CompletableTask.complete(T value, Executor executor)`

Same as above, but the starting point is a resolved `Promise` with the specified value:
```java
CompletableTask
   .complete("Hello!", myExecutor)
   .thenApplyAsync(myMapper)
   .thenApplyAsync(myTransformer)   
   .thenAcceptAsync(myConsumer)
   .thenRunAsync(myAction);
```  
All of `myMapper`, `myTransformer`, `myConsumer`, `myActtion` will be executed using `myExecutor`.

Additionally, you may submit [Supplier](https://docs.oracle.com/javase/8/docs/api/java/util/function/Supplier.html) / [Runnable](https://docs.oracle.com/javase/8/docs/api/java/lang/Runnable.html) to the [Executor](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Executor.html) right away, in a similar way as with [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html):

```java
Promise<SomeValue> p1 = CompletableTask.supplyAsync(() -> {
  return blockingCalculationOfSomeValue();
}, myExecutor);

Promise<Void> p2 = CompletableTask.runAsync(this::someIoBoundMethod, myExecutor);
```

Most importantly, all composed promises support true cancellation (incl. interrupting thread) for the functions supplied as arguments:
```java
Promise<?> p1 =
CompletableTask
  .asyncOn(myExecutor)
  .thenApplyAsync(myValueGenerator)
  .thenAcceptAsync(myConsumer);
  
Promise<?> p2 = p1.thenRunAsync(myAction);
...
p1.cancel(true);
```  
In the example above `myConsumer` will be interrupted if already in progress. Both `p1` and `p2` will be resolved faulty: `p1` with a [CancellationException](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CancellationException.html) and `p2` with a [CompletionException](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionException.html).

## 3. DependentPromise
As it mentioned above, once you cancel `Promise`, all `Promise`-s that depends on this promise are completed with [CompletionException](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionException.html) wrapping[CancellationException](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CancellationException.html). This is a standard behavior, and [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) works just like this.

However, then you cancel derrived `Promise`, the original `Promise` is not cancelled: 
```java
Promise<?> original = CompletableTask.supplyAsync(() -> someIoBoundMethod(), myExecutor);
Promise<?> derrived = original.thenRunAsync(() -> someMethod() );
...
derrived.cancel(true);
```
So if you cancel `derrived` above it's [Runnable](https://docs.oracle.com/javase/7/docs/api/java/lang/Runnable.html) method, wrapping `someMethod`, is interrupted. However `original` promise is not cancelled and `someIoBoundMethod` keeps running. This is not always a desired behavior, consider the following method:

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

Clients of this method see only derrived promise, and once they decide to cancel it, it is expected that any of `loadXml` and `parseXml` will be interrupted if not completed yet. To address this issue the library provides [DependentPromise](https://github.com/vsilaev/tascalate-concurrent/blob/master/src/main/java/net/tascalate/concurrent/DependentPromise.java) class:
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
- if method accepts no other [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html), like `thenApply` / `thenRun` / `thenAccept` etc, then it's a boolean flag `enlistOrigin` to specify whether or not original `Promise` should be enlisted for cancelation.
- if method accepts other [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html), like `thenCombine` / `applyToEither` / `thenAcceptBoth` etc, then it's a set of [PromiseOrigin](https://github.com/vsilaev/tascalate-concurrent/blob/master/src/main/java/net/tascalate/concurrent/PromiseOrigin.java) enum values, that specifies whether or not original `Promise` and/or `CompletionStage` supplied as argument should be enlisted for cancelation along with resulting promise, for example:

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

## 4. Utility class Promises
The class
provides convenient methods to combine several `CompletionStage`-s:

`public static <T> Promise<List<T>> all(CompletionStage<? extends T>... promises)`

Returns a promise that is completed normally when all `CompletionStage`-s passed as parameters are completed normally; if any promise completed exceptionally, then resulting promise is completed exceptionally as well

`public static <T> Promise<T> any(final CompletionStage<? extends T>... promises)`

Returns a promise that is completed normally when any `CompletionStage` passed as parameters is completed normally (race is possible); if all promises completed exceptionally, then resulting promise is completed exceptionally as well

`public static <T> Promise<T> anyStrict(CompletionStage<? extends T>... promises)`

Returns a promise that is completed normally when any `CompletionStage` passed as parameters is completed normally (race is possible); if any promise completed exceptionally before first result is available, then resulting promise is completed exceptionally as well (unlike non-Strict variant, where exceptions are ignored if result is available at all)

`public static <T> Promise<List<T>> atLeast(int minResultsCount, CompletionStage<? extends T>... promises)`

Generalization of the `any` method. Returns a promise that is completed normally when at least `minResultCount`
of `CompletionStage`-s passed as parameters are completed normally (race is possible); if less than `minResultCount` of promises completed normally, then resulting promise is completed exceptionally

`public static <T> Promise<List<T>> atLeastStrict(int minResultsCount, CompletionStage<? extends T>... promises)`

Generalization of the `anyStrict` method. Returns a promise that is completed normally when at least `minResultCount` of `CompletionStage`-s passed as parameters are completed normally (race is possible); if any promise completed exceptionally before `minResultCount` of results are available, then resulting promise is completed exceptionally as well (unlike non-Strict variant, where exceptions are ignored if `minResultsCount` of results are available at all)

Additionally, it's possible to convert to `Promise` API ready value:

`public static <T> Promise<T> success(T value)`

...exception:

`public static Promise<?> failure(Throwable exception)`

...or arbitrary `CompletionStage` implementation:

`public static <T> Promise<T> from(CompletionStage<T> stage)`

## 5. Extensions to ExecutorService API

It’s not mandatory to use any specific subclasses of `Executor` with `CompletableTask` – you may use any implementation. However, someone may find beneficial to have a `Promise`-aware `ExecutorService` API. Below is a list of related classes/interfaces:

a. Interface `TaskExecutorService`

Specialization of [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) that uses `Promise` as a result of `submit(...)` methods.

b. Class `ThreadPoolTaskExecutor`
A subclass of the standard [ThreadPoolExecutor](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ThreadPoolExecutor.html) that implements `TaskExecutorService` interface.

c. Class `TaskExecutors`

A drop-in replacement for [Executors](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Executors.html) utility class that returns various useful implementations of `TaskExecutorService` instead of the standard [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html).

# Acknowledgements

Internal implementation details are greatly inspired [by the work](https://github.com/lukas-krecan/completion-stage) done by [Lukáš Křečan](https://github.com/lukas-krecan)

