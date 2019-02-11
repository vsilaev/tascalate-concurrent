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