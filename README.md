[![Maven Central](https://img.shields.io/maven-central/v/net.tascalate/net.tascalate.concurrent.svg)](https://search.maven.org/artifact/net.tascalate/net.tascalate.concurrent/1.0.0/jar) [![GitHub release](https://img.shields.io/github/release/vsilaev/tascalate-concurrent.svg)](https://github.com/vsilaev/tascalate-concurrent/releases/tag/1.0.0) [![license](https://img.shields.io/github/license/vsilaev/tascalate-concurrent.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)
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
    <version>1.0.0</version>
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
    <version>1.0.0</version>
</dependency>
```

# Documentation
Complete user documentation is available [HERE](https://github.com/vsilaev/tascalate-concurrent/blob/master/USER_MANUAL.md)

# Acknowledgements
Internal implementation details are greatly inspired [by the work](https://github.com/lukas-krecan/completion-stage) done by [Lukáš Křečan](https://github.com/lukas-krecan). The part of the polling / asynchronous retry functionality is adopted from the [async-retry](https://github.com/nurkiewicz/async-retry) library by [Tomasz Nurkiewicz](http://nurkiewicz.com/)
