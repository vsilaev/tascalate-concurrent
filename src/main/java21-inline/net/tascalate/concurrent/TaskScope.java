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
package net.tascalate.concurrent;

import java.time.Duration;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.tascalate.concurrent.var.ContextRelay;

/**
 * A structured-concurrency scope that manages a set of forked asynchronous tasks,
 * adapted to work seamlessly with the Tascalate Concurrent {@link Promise} API.
 *
 * <h2>Comparison with JDK {@code StructuredTaskScope} (JEP 525)</h2>
 * <p>
 * The JDK's {@code StructuredTaskScope} (introduced as an incubator in JEP 428,
 * previewed in JEP 453, and formalized in JEP 525) relies on rigid, imperative
 * "joiner" strategies such as {@code ShutdownOnSuccess} and {@code ShutdownOnFailure}.
 * While useful for simple scenarios, these joiners become cumbersome when complex
 * aggregation logic is required (e.g., "wait for the first 3 successes, ignore
 * failures, and cancel the rest").
 * </p>
 * <p>
 * {@code TaskScope} takes a fundamentally different approach: instead of predefined
 * joiners, it exposes completed tasks as a <b>lazy Java {@link Stream}</b> via
 * {@link #completions()}. Because {@link Promise.State} constants implement
 * {@link java.util.function.Predicate}, you can use them directly in
 * {@code Stream.filter()} for highly expressive, declarative combinator logic:
 * </p>
 * <pre>{@code
 * try (TaskScope scope = TaskScope.builder().build()) {
 *     for (String url : urls) {
 *         scope.fork(() -> fetchFromUrl(url));
 *     }
 *
 *     List<String> results = scope.completions()
 *         .filter(Promise.State.SUCCESS)   // Promise.State is a Predicate<Promise<?>>
 *         .map(Promise::join)
 *         .limit(3)                        // Short-circuits after 3 successes
 *         .toList();                       // JDK 16+ Stream API
 *
 * } // try-with-resources cancels all remaining uncompleted tasks here
 * }</pre>
 *
 * <h2>Key Characteristics</h2>
 * <ul>
 *   <li><b>Lazy Stream-based consumption:</b> {@link #completions()} yields tasks
 *       exactly as they finish. Short-circuiting stream operations ({@code limit},
 *       {@code findFirst}, {@code takeWhile}) stop consumption early, and the
 *       enclosing {@code try-with-resources} block automatically cancels remaining tasks.</li>
 *   <li><b>Single-threaded lifecycle:</b> {@link #close()} and {@link #reset()} are
 *       guaranteed to execute on the thread that created the scope. No background
 *       timer threads are used.</li>
 *   <li><b>Reusable via {@link #reset()}:</b> Unlike the JDK's single-use
 *       {@code StructuredTaskScope}, a {@code TaskScope} can be reset and reused
 *       for multiple independent batches of work within the same thread.</li>
 *   <li><b>Nested scopes via {@link #toBuilder()}:</b> A child scope can inherit
 *       the parent's executor and context relay while maintaining its own lifecycle.</li>
 * </ul>
 *
 * <h2>Timeout Support</h2>
 * <p>
 * A pragmatic scope-level timeout can be configured via
 * {@link Builder#timeout(Duration)}.
 * </p>
 * <p>
 * When the timeout expires, the scope is marked as timed out and all currently
 * tracked forked promises are cancelled. The timeout does <b>not</b> call
 * {@link #close()} or {@link #reset()}; {@link #completions()} remains usable
 * and will simply observe the cancelled tasks.
 * </p>
 * <p>
 * Any subsequent {@link #fork(Callable)} or {@link #fork(Runnable)} call after
 * the timeout has fired throws a {@link CompletionException} wrapping a
 * {@link TimeoutException}.
 * </p>
 * <p>
 * The timeout is scheduled through {@link AsyncInternals#delay(Duration)} and
 * therefore does not require the scope itself to own or manage a background
 * timer thread. The timeout does not break the single-threaded nature of
 * {@link #close()} and {@link #reset()}; those methods still perform the
 * structural cleanup exclusively on the owner thread.
 * </p>
 * <p>
 * {@link #reset()} does not restart or clear an already configured timeout.
 * </p>
 * <ul>
 *   <li><b>Per-task timeout:</b> Chain {@link Promise#orTimeout(java.time.Duration, boolean)}
 *       on the promise returned by {@link #fork(Callable)}. If the timeout expires, the
 *       task is cancelled with a {@link java.util.concurrent.CancellationException}.</li>
 *   <li><b>Global batch timeout:</b> Fork a "sleep" task first, then use
 *       {@code completions().takeWhile(p -> p != timeoutPromise)} to stop processing
 *       when the timeout task completes. This works naturally with virtual threads
 *       (the default), where a sleeping thread costs almost no resources.</li>
 * </ul>
 *
 * @author vsilaev
 * @see Promise
 * @see Promise.State
 * @see TaskCompletionService
 * @see ContextRelay
 */
public class TaskScope implements AutoCloseable {

    /** Monotonically increasing counter for auto-generated scope names. */
    private static final AtomicLong SCOPE_ID = new AtomicLong(0);

    /** Human-readable name for debugging and logging. */
    private final String name;

    /**
     * The executor used to run forked tasks. Always non-null.
     * <p>
     * The {@link #ownExecutor} flag determines whether {@link #close()} will
     * shut this executor down.
     * </p>
     */
    private final ExecutorService executorService;

    /** Whether this scope is responsible for shutting down the executor. */
    private final boolean ownExecutor;

    /** Completion service that tracks submitted tasks and their results. */
    private final TaskCompletionService<Object> completionService;

    /** Optional context relay applied to every forked task. */
    private final ContextRelay<?> contextualizer;

    /** Set of all currently tracked (not yet consumed) forked promises. */
    private final Set<Promise<?>> allFutures = ConcurrentHashMap.newKeySet();

    /** Optional scope-level timeout duration. */
    private final Duration timeout;

    /** Promise that fires when the scope-level timeout expires, or null. */
    private final Promise<Duration> timeoutPromise;

    /** Set to true once the scope-level timeout has fired. */
    private volatile boolean timedOut;

    /** The thread that created this scope. All mutating operations are confined to it. */
    private final Thread owner = Thread.currentThread();

    // =========================================================================
    // Construction
    // =========================================================================

    /**
     * Primary constructor. Use {@link #builder()} or {@link #toBuilder()} instead
     * of calling this directly.
     *
     * @param name            the scope name (must not be null)
     * @param executorService the executor to run forked tasks on (must not be null)
     * @param ownExecutor     if {@code true}, {@link #close()} will shut down the executor
     * @param contextualizer  optional context relay applied to every forked task (may be null)
     * @param timeout         optional scope-level timeout, or null for no timeout
     */
    protected TaskScope(String name,
                        ExecutorService executorService,
                        boolean ownExecutor,
                        ContextRelay<?> contextualizer,
                        Duration timeout) {
        Objects.requireNonNull(executorService, "executorService may not be null");
        this.name = name;
        this.executorService = executorService;
        this.ownExecutor = ownExecutor;
        this.completionService = new TaskExecutorCompletionService<>(executorService);
        this.contextualizer = contextualizer;
        this.timeout = timeout;

        if (timeout != null) {
            Promise<Duration> delay = AsyncInternals.delay(timeout);
            this.timeoutPromise = delay;

            delay.whenComplete((d, e) -> {
                // If the delay promise is cancelled, usually by close(), do not
                // treat this as a timeout.
                if (e == null) {
                    handleTimeout();
                }
            });
        } else {
            this.timeoutPromise = null;
        }
    }
    
    /**
     * Backward-compatible constructor without a timeout.
     */
    protected TaskScope(String name,
                        ExecutorService executorService,
                        boolean ownExecutor,
                        ContextRelay<?> contextualizer) {
        this(name, executorService, ownExecutor, contextualizer, null);
    }

    /**
     * Starts building a new {@link TaskScope}.
     * <p>
     * If no executor or thread factory is configured, the builder defaults to
     * {@link Executors#newVirtualThreadPerTaskExecutor()} (JDK 21+), which the
     * scope will own and shut down on {@link #close()}.
     * </p>
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new {@link Builder} pre-populated with the configuration of this scope,
     * suitable for creating <b>nested child scopes</b>.
     * <p>
     * The returned builder:
     * </p>
     * <ul>
     *   <li>Generates a <b>fresh unique name</b> for the child scope (the parent's
     *       name is not copied).</li>
     *   <li>Inherits the parent's {@link ContextRelay} so that forked tasks in the
     *       child scope automatically receive the same context propagation.</li>
     *   <li>Inherits the parent's executor but marks it as <b>unowned</b>
     *       ({@code ownExecutor = false}), regardless of whether the parent owns it.
     *       This prevents the child from accidentally shutting down the parent's
     *       executor when the child closes. The parent remains solely responsible
     *       for executor lifecycle.</li>
     * </ul>
     *
     * <h3>Example: Nested Scopes</h3>
     * <pre>{@code
     * try (TaskScope parent = TaskScope.builder()
     *         .name("ParentScope")
     *         .threadFactory(Thread.ofVirtual().name("agg-", 0).factory())
     *         .contextualizer(traceRelay)
     *         .build()) {
     *
     *     parent.fork(() -> fetchMetadata());
     *
     *     // Child inherits executor + contextualizer, gets a new auto-generated name
     *     try (TaskScope child = parent.toBuilder()
     *             .name("ChildDetailFetch")  // optional: override the auto name
     *             .build()) {
     *
     *         for (int i = 0; i < 50; i++) {
     *             child.fork(() -> fetchDetail(i));
     *         }
     *
     *         List<Object> details = child.completions()
     *             .filter(Promise.State.SUCCESS)
     *             .map(Promise::join)
     *             .toList();
     *
     *     } // child closes: cancels remaining child tasks, does NOT shut down executor
     *
     * } // parent closes: cancels remaining parent tasks AND shuts down executor
     * }</pre>
     *
     * @return a new builder pre-populated with this scope's configuration
     */
    public Builder toBuilder() {
        Builder b = new Builder();
        // name is left null → build() will auto-generate a fresh child name
        b.contextualizer = this.contextualizer;
        if (this.executorService != null) {
            // Child never owns the parent's executor
            b.executor(this.executorService, false);
        }
        return b;
    }

    // =========================================================================
    // Builder
    // =========================================================================

    /**
     * Fluent builder for {@link TaskScope}, eliminating the need for combinatorial
     * constructor overloads.
     * <p>
     * The builder supports the following configuration options:
     * </p>
     * <ul>
     *   <li>{@link #name(String)} — human-readable scope name for debugging.</li>
     *   <li>{@link #executor(ExecutorService)} — external executor, <b>not owned</b>
     *       by default (the scope will not shut it down).</li>
     *   <li>{@link #executor(ExecutorService, boolean)} — external executor with
     *       explicit ownership control.</li>
     *   <li>{@link #threadFactory(ThreadFactory)} — creates a thread-per-task executor
     *       from the given factory; the scope <b>owns</b> the resulting executor.</li>
     *   <li>{@link #contextualizer(ContextRelay)} — context relay applied to every
     *       forked task for automatic context propagation.</li>
     * </ul>
     * <p>
     * If neither an executor nor a thread factory is specified, {@link #build()}
     * defaults to {@link Executors#newVirtualThreadPerTaskExecutor()} (JDK 21+),
     * which the scope owns.
     * </p>
     */
    public static class Builder {

        /** Scope name; {@code null} means auto-generate in {@link #build()}. */
        private String name;

        /** Explicitly provided executor service. */
        private ExecutorService executorService;

        /** Thread factory used to create a thread-per-task executor. */
        private ThreadFactory threadFactory;

        /** Whether the scope should shut down the executor on close. */
        private boolean ownExecutor = false;

        /** Context relay applied to every forked task. */
        private ContextRelay<?> contextualizer;
        
        /** Optional scope-level timeout; null means no timeout. */
        private Duration timeout;

        /**
         * Creates a new builder with default settings.
         * <p>
         * The scope name is <b>not</b> generated here; it is generated lazily in
         * {@link #build()} only if no explicit name was set via {@link #name(String)}.
         * </p>
         */
        Builder() {
            // Intentionally empty: name is resolved lazily in build()
        }

        /**
         * Sets the name of the task scope, useful for debugging and logging.
         *
         * @param name the scope name (must not be null)
         * @return this builder
         */
        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name may not be null");
            return this;
        }

        /**
         * Sets the {@link ExecutorService} to be used for executing forked tasks.
         * <p>
         * By default, the scope will <b>not</b> own this executor and will not
         * shut it down when {@link TaskScope#close()} is called.
         * </p>
         *
         * @param executorService the executor service (must not be null)
         * @return this builder
         */
        public Builder executor(ExecutorService executorService) {
            this.executorService = Objects.requireNonNull(executorService, "executorService may not be null");
            this.threadFactory = null;
            this.ownExecutor = false;
            return this;
        }

        /**
         * Sets the {@link ExecutorService} and explicitly specifies ownership.
         *
         * @param executorService the executor service (must not be null)
         * @param ownExecutor     if {@code true}, the scope will shut down this
         *                        executor when {@link TaskScope#close()} is called
         * @return this builder
         */
        public Builder executor(ExecutorService executorService, boolean ownExecutor) {
            this.executorService = Objects.requireNonNull(executorService, "executorService may not be null");
            this.threadFactory = null;
            this.ownExecutor = ownExecutor;
            return this;
        }

        /**
         * Sets the {@link ThreadFactory} to create a new thread-per-task executor.
         * <p>
         * When a thread factory is provided, the scope <b>will</b> own the resulting
         * executor and will shut it down when {@link TaskScope#close()} is called.
         * </p>
         *
         * @param threadFactory the thread factory (must not be null)
         * @return this builder
         */
        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = Objects.requireNonNull(threadFactory, "threadFactory may not be null");
            this.executorService = null;
            this.ownExecutor = true;
            return this;
        }

        /**
         * Sets the {@link ContextRelay} to propagate context variables to forked tasks.
         * <p>
         * Every task forked within the resulting scope will automatically have the
         * context snapshot applied before execution and cleared afterward.
         * </p>
         *
         * @param contextualizer the context relay (may be null to disable propagation)
         * @return this builder
         */
        public Builder contextualizer(ContextRelay<?> contextualizer) {
            this.contextualizer = contextualizer;
            return this;
        }
        
        /**
         * Sets a scope-level timeout.
         * <p>
         * When the timeout expires, the scope is marked as timed out and all
         * currently tracked forked promises are cancelled. The timeout does
         * <b>not</b> call {@link TaskScope#close()} or {@link TaskScope#reset()}.
         * The {@link TaskScope#completions()} stream remains usable and will simply
         * observe the cancelled tasks.
         * </p>
         * <p>
         * Any subsequent {@link TaskScope#fork(Callable)} or
         * {@link TaskScope#fork(Runnable)} call after the timeout has fired will
         * throw a {@link CompletionException} wrapping a {@link TimeoutException}.
         * </p>
         * <p>
         * Passing {@code null} clears a previously configured timeout.
         * </p>
         *
         * @param timeout the timeout duration, or null to disable the timeout
         * @return this builder
         * @throws IllegalArgumentException if the timeout is negative
         */
        public Builder timeout(Duration timeout) {
            if (timeout != null && timeout.isNegative()) {
                throw new IllegalArgumentException("timeout may not be negative");
            }
            this.timeout = timeout;
            return this;
        }

        /**
         * Builds and returns a new {@link TaskScope} instance.
         * <p>
         * Resolution order for the executor:
         * </p>
         * <ol>
         *   <li>If an {@link ExecutorService} was set via {@link #executor}, it is used
         *       with the configured ownership flag.</li>
         *   <li>Else if a {@link ThreadFactory} was set via {@link #threadFactory}, a
         *       thread-per-task executor is created from it and owned by the scope.</li>
         *   <li>Otherwise, a virtual-thread-per-task executor is created
         *       ({@link Executors#newVirtualThreadPerTaskExecutor()}, JDK 21+) and
         *       owned by the scope.</li>
         * </ol>
         * <p>
         * If no name was set via {@link #name(String)}, a unique name is auto-generated
         * in the form {@code "scope_N"}.
         * </p>
         *
         * @return the configured task scope
         */
        public TaskScope build() {
            String actualName = (name != null) ? name : nextGeneratedName();

            ExecutorService actualExecutor;
            boolean actualOwnExecutor;

            if (executorService != null) {
                actualExecutor = executorService;
                actualOwnExecutor = ownExecutor;
            } else if (threadFactory != null) {
                actualExecutor = Executors.newThreadPerTaskExecutor(threadFactory);
                actualOwnExecutor = true;
            } else {
                // Default to virtual threads (JDK 21+)
                actualExecutor = Executors.newVirtualThreadPerTaskExecutor();
                actualOwnExecutor = true;
            }

            return new TaskScope(
                actualName,
                actualExecutor,
                actualOwnExecutor,
                contextualizer,
                timeout
            );
        }
    }

    // =========================================================================
    // Forking Tasks
    // =========================================================================

    /**
     * Forks a new {@link Runnable} task within this scope.
     * <p>
     * The task is submitted to the scope's executor and tracked for lifecycle
     * management. If a {@link ContextRelay} was configured, the runnable is
     * automatically wrapped to propagate context variables.
     * </p>
     * <p>
     * If the scope-level timeout has already fired, this method throws a
     * {@link CompletionException} wrapping a {@link TimeoutException}.
     * </p>
     *
     * @param code the runnable to execute
     * @return a {@link Promise}{@code <Void>} representing the forked task
     * @throws IllegalStateException if called from a thread other than the scope owner
     * @throws CompletionException   if the scope-level timeout has already fired
     */
    @SuppressWarnings("unchecked")
    public Promise<Void> fork(Runnable code) {
        checkOwner();
        checkTimeout(); // Fast-path: throw immediately if already timed out

        Runnable contextualized = (null == contextualizer) ? code : contextualizer.contextual(code);
        
        // Execute and enlist
        Promise<?> result = completionService.submit(contextualized, null);
        allFutures.add(result);

        // Double-check: if timeout fired exactly between our early check and enlistment,
        // the timer thread's iteration might have missed this task. We cancel it here.
        if (timedOut) {
            result.cancel(true);
        }
        
        return (Promise<Void>) result;
    }

    /**
     * Forks a new {@link Callable} task within this scope.
     * <p>
     * The task is submitted to the scope's executor and tracked for lifecycle
     * management. If a {@link ContextRelay} was configured, the callable is
     * automatically wrapped to propagate context variables.
     * </p>
     * <p>
     * If the scope-level timeout has already fired, this method throws a
     * {@link CompletionException} wrapping a {@link TimeoutException}.
     * </p>
     *
     * @param <T>  the result type of the callable
     * @param code the callable to execute
     * @return a {@link Promise}{@code <T>} representing the forked task
     * @throws IllegalStateException if called from a thread other than the scope owner
     * @throws CompletionException   if the scope-level timeout has already fired
     */
    @SuppressWarnings("unchecked")
    public <T> Promise<T> fork(Callable<T> code) {
        checkOwner();
        checkTimeout(); // Fast-path: throw immediately if already timed out

        Callable<T> contextualized = (null == contextualizer) ? code : contextualizer.contextual(code);
        
        // Execute and enlist
        Promise<?> result = completionService.submit((Callable<Object>) contextualized);
        allFutures.add(result);

        // Double-check: catch the race window where timer fired but missed this task
        if (timedOut) {
            result.cancel(true);
        }
        
        return (Promise<T>) result;
    }

    // =========================================================================
    // Consuming Completions
    // =========================================================================

    /**
     * Returns a <b>lazy</b> {@link Stream} of {@link Promise} objects, yielding tasks
     * exactly as they complete (in completion order, not fork order).
     * <p>
     * Because {@link Promise.State} constants implement {@link java.util.function.Predicate},
     * they can be used directly in {@code Stream.filter()} for highly expressive
     * combinator logic:
     * </p>
     * <pre>{@code
     * // Emulates Promises.any: first successful result wins
     * Optional<Object> result = scope.completions()
     *     .filter(Promise.State.SUCCESS)
     *     .map(Promise::join)
     *     .findFirst();
     *
     * // Emulates Promises.atLeast(N): collect N successes, tolerate failures
     * List<Object> quorum = scope.completions()
     *     .filter(Promise.State.SUCCESS)
     *     .map(Promise::join)
     *     .limit(5)
     *     .toList();
     *
     * // Emulates Promises.all: fail-fast on first error
     * List<Object> all = scope.completions()
     *     .map(Promise::join)  // throws CompletionException on first failure
     *     .toList();
     * }</pre>
     * <p>
     * Short-circuiting operations ({@code limit}, {@code findFirst}, {@code takeWhile})
     * stop consuming the stream early. When the enclosing {@code try-with-resources}
     * block exits, {@link #close()} cancels all remaining uncompleted tasks.
     * </p>
     * <p>
     * <b>Global batch timeout pattern:</b> Fork a "sleep" task first, then use
     * {@code takeWhile} to stop when it completes:
     * </p>
     * <pre>{@code
     * Promise<Object> timeout = scope.fork(() -> {
     *     Thread.sleep(Duration.ofSeconds(5));
     *     return "TIMEOUT";
     * });
     *
     * for (int i = 0; i < 100; i++) {
     *     scope.fork(() -> doWork(i));
     * }
     *
     * List<Object> results = scope.completions()
     *     .takeWhile(p -> p != timeout)  // stops when timeout task completes
     *     .filter(Promise.State.SUCCESS)
     *     .map(Promise::join)
     *     .toList();
     * }</pre>
     *
     * @return a lazy stream of completed promises
     * @throws IllegalStateException if consumed from a thread other than the scope owner
     */
    public Stream<Promise<Object>> completions() {
        Iterator<Promise<Object>> iterator = new Iterator<Promise<Object>>() {
            @Override
            public Promise<Object> next() {
                try {
                    checkOwner();
                    if (allFutures.isEmpty()) {
                        throw new ConcurrentModificationException();
                    }
                    Promise<Object> result = completionService.take();
                    allFutures.remove(result);
                    return (Promise<Object>) result;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(ex);
                }
            }

            @Override
            public boolean hasNext() {
                // Don't check owner here — non-destructive operation
                return !allFutures.isEmpty();
            }
        };
        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(iterator, Spliterator.DISTINCT & Spliterator.NONNULL),
            false
        );
    }

    // =========================================================================
    // Lifecycle Management
    // =========================================================================

    /**
     * Cancels all forked tasks, clears the internal tracking set, and drains the
     * completion queue, resetting the scope to its initial state.
     * <p>
     * This allows the scope to be reused for multiple independent batches of work
     * within the same thread:
     * </p>
     * <pre>{@code
     * try (TaskScope scope = TaskScope.builder().build()) {
     *     // Batch 1
     *     scope.fork(() -> processBatch1());
     *     scope.completions().map(Promise::join).toList();
     *
     *     scope.reset();  // Reset for the next batch
     *
     *     // Batch 2
     *     scope.fork(() -> processBatch2());
     *     scope.completions().map(Promise::join).toList();
     * }
     * }</pre>
     *
     * @throws IllegalStateException if called from a thread other than the scope owner
     */
    public void reset() {
        checkOwner();
        // Cancel all forks first
        allFutures.forEach(f -> f.cancel(true));
        // Remove enlisted futures
        allFutures.clear();
        // Drain the completion queue
        while (completionService.poll() != null) {
            // discard
        }
    }

    /**
     * Closes this scope: cancels all remaining forked tasks via {@link #reset()},
     * cancels the scope-level timeout if it has not fired yet, and shuts down the
     * executor <b>only if this scope owns it</b>.
     * <p>
     * Ownership is determined at construction time:
     * </p>
     * <ul>
     *   <li>Executors created from a {@link ThreadFactory} or the default
     *       virtual-thread executor are <b>owned</b> and will be shut down.</li>
     *   <li>Externally provided executors are <b>not owned</b> by default and
     *       will <b>not</b> be shut down, unless explicitly marked as owned
     *       via {@link Builder#executor(ExecutorService, boolean)}.</li>
     *   <li>Child scopes created via {@link #toBuilder()} never own the inherited
     *       executor, so closing a child never affects the parent's executor.</li>
     * </ul>
     */
    @Override
    public void close() {
        if (timeoutPromise != null) {
            timeoutPromise.cancel(true);
        }

        reset();

        if (ownExecutor && null != executorService) {
            executorService.shutdownNow();
        }
    }
    
    /**
     * Returns the configured scope-level timeout, or {@code null} if no timeout
     * was configured.
     *
     * @return the configured timeout duration, or null
     */
    public Duration timeout() {
        return timeout;
    }

    /**
     * Returns {@code true} if the scope-level timeout has fired.
     *
     * @return {@code true} if the scope has timed out
     */
    public boolean isTimedOut() {
        return timedOut;
    }
    
    /**
     * Returns the {@link ContextRelay} configured for this scope, or {@code null}
     * if no context propagation is configured.
     *
     * @return the context relay, or null
     */
    public ContextRelay<?> contextualizer() {
        return contextualizer;
    }

    /**
     * Returns the human-readable name of this scope.
     *
     * @return the scope name
     */
    @Override
    public String toString() {
        return name;
    }

    // =========================================================================
    // Blocking Take Methods
    // =========================================================================

    /**
     * Blocks until the next forked task completes and returns its {@link Promise}.
     * <p>
     * Returns {@code null} if all forked tasks have already been consumed
     * (i.e., the completion queue is exhausted). This is consistent with the
     * library-wide idiom where {@code null} signifies "no more elements".
     * </p>
     *
     * @return the next completed promise, or {@code null} if the queue is exhausted
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @throws IllegalStateException if called from a thread other than the scope owner
     */
    public Promise<Object> take() throws InterruptedException {
        return take(ANY);
    }

    /**
     * Blocks until the next forked task matching the given filter completes.
     *
     * @param filter a predicate to test each completed promise
     * @return the next matching completed promise, or {@code null} if the queue is exhausted
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @throws IllegalStateException if called from a thread other than the scope owner
     */
    public Promise<Object> take(Predicate<Promise<?>> filter) throws InterruptedException {
        checkOwner();
        while (!allFutures.isEmpty()) {
            Promise<Object> result = (Promise<Object>) completionService.take();
            allFutures.remove(result);
            if (filter.test(result)) {
                return result;
            }
        }
        return null;
    }

    /**
     * Blocks until the next forked task completes and returns its result value
     * wrapped in an {@link Optional}.
     * <p>
     * Returns {@code null} if all forked tasks have already been consumed
     * (i.e., the completion queue is exhausted). Otherwise, returns an
     * {@link Optional} containing the result (which may itself be
     * {@code Optional.empty()} if the task's result is {@code null}).
     * </p>
     *
     * @return an {@link Optional} with the result, or {@code null} if the queue is exhausted
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @throws IllegalStateException if called from a thread other than the scope owner
     */
    public Optional<Object> takeValue() throws InterruptedException {
        return takeValue(ANY);
    }

    /**
     * Blocks until the next forked task matching the given filter completes and
     * returns its result value wrapped in an {@link Optional}.
     *
     * @param filter a predicate to test each completed promise
     * @return an {@link Optional} with the result, or {@code null} if the queue is exhausted
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @throws IllegalStateException if called from a thread other than the scope owner
     */
    public Optional<Object> takeValue(Predicate<Promise<?>> filter) throws InterruptedException {
        checkOwner();
        while (!allFutures.isEmpty()) {
            Promise<Object> result = (Promise<Object>) completionService.take();
            allFutures.remove(result);
            if (filter.test(result)) {
                return Optional.ofNullable(result.resultNow());
            }
        }
        return null;
    }

    // =========================================================================
    // Internals
    // =========================================================================
    /**
     * Checks whether the scope-level timeout has fired and throws the standard
     * timeout exception if so.
     *
     * @throws CompletionException if the scope-level timeout has already fired
     */
    private void checkTimeout() {
        if (timedOut) {
            throw new CompletionException(
                new TimeoutException(
                    "Task scope '" + name + "' timed out" +
                    (timeout != null ? " after " + timeout : "")
                )
            );
        }
    }

    /**
     * Handles timeout expiration.
     * <p>
     * This method intentionally does <b>not</b> call {@link #close()} or
     * {@link #reset()}. It only marks the scope as timed out and cancels the
     * promises currently tracked by the scope.
     * </p>
     * <p>
     * The {@link #completions()} stream remains usable: cancelled tasks will be
     * observed as cancelled promises.
     * </p>
     */
    private void handleTimeout() {
        if (timedOut) {
            return;
        }
        timedOut = true;
        
        // Iterate over the concurrent set. Any task added concurrently by the owner 
        // thread will either be seen by this iterator, or the owner thread will see 
        // timedOut == true in its double-check and cancel the task itself.
        for (Promise<?> promise : allFutures) {
            try {
                promise.cancel(true);
            } catch (RuntimeException | Error ignored) {
                // Continue cancelling the remaining tasks even if one cancellation
                // unexpectedly fails.
            }
        }
    }

    /**
     * Generates the next unique scope name in the form {@code "scope_N"}.
     *
     * @return a unique scope name
     */
    static String nextGeneratedName() {
        return "scope_" + SCOPE_ID.getAndIncrement();
    }

    /**
     * Verifies that the calling thread is the thread that created this scope.
     *
     * @throws IllegalStateException if called from a different thread
     */
    private void checkOwner() {
        if (owner != Thread.currentThread()) {
            throw new IllegalStateException("Scope must be accessed from the single thread");
        }
    }

    /** A predicate that matches any promise. Used as the default filter for take methods. */
    private static final Predicate<Promise<?>> ANY = p -> true;
}