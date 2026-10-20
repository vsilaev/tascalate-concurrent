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

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * The base fluent builder for {@link ThreadFactory} instances, providing uniform
 * configuration of thread naming, grouping, class loading, security context, and
 * thread attributes.
 *
 * <h2>Configuration options</h2>
 * <ul>
 *   <li><b>Name format</b> ({@link #withNameFormat(String)}) &mdash; a
 *       {@link String#format} pattern with up to three positional arguments:
 *       <ul>
 *         <li>{@code %1$d} &mdash; the sequential thread number within the factory;</li>
 *         <li>{@code %2$s} &mdash; the name of the thread group;</li>
 *         <li>{@code %3$d} &mdash; the global pool counter (unique per created factory).</li>
 *       </ul>
 *       The default pattern is {@code "pool-%3$d-thread-%1$d"}.</li>
 *   <li><b>Thread group</b> ({@link #withThreadGroup(ThreadGroup)}) &mdash; an explicit
 *       group, or the {@code SecurityManager} / current-thread group when unset.</li>
 *   <li><b>Context class loader</b> ({@link #withContextClassLoader(ClassLoader)}) &mdash;
 *       assigned to every created thread before it runs its task.</li>
 *   <li><b>Privileged access</b> ({@link #withPriviledgedAccess(boolean)}) &mdash; when
 *       enabled, tasks run inside
 *       {@link AccessController#doPrivileged(PrivilegedAction, AccessControlContext)}
 *       with the access context captured at factory creation time.</li>
 *   <li><b>Daemon flag, priority, uncaught exception handler</b> &mdash; applied to every
 *       created thread; the corresponding {@code withDefault...} methods restore JVM
 *       defaults.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * ThreadFactory factory = TaskExecutors.newThreadFactory()
 *     .withNameFormat("my-pool-%3$d-worker-%1$d")
 *     .withDaemonFlag(true)
 *     .withContextClassLoader(myClassLoader)
 *     .withUncaughtExceptionHandler((t, e) -> log.error("Thread " + t + " failed", e))
 *     .build();
 * }</pre>
 *
 * <h2>Extension</h2>
 * <p>
 * Subclasses must implement {@link #createThread(ThreadGroup, Runnable, String)} to define
 * what kind of thread is actually produced (see {@link ThreadFactoryBuilder} for the
 * standard platform-thread implementation). The builder applies all configured decorators
 * <em>around</em> the subclass-supplied thread creation, so custom thread types inherit the
 * full configuration pipeline.
 * </p>
 *
 * <p>
 * Instances are not thread-safe during configuration; the {@link ThreadFactory} produced
 * by {@link #build()} is thread-safe.
 * </p>
 *
 * @param <T> the self-type of the concrete builder, for fluent chaining
 * @see ThreadFactoryBuilder
 * @see ThreadGroupBuilder
 * @see TaskExecutors#newThreadFactory()
 */
abstract public class AbstractThreadFactoryBuilder<T extends AbstractThreadFactoryBuilder<T>> {

    /** Global counter assigning a unique number to every factory created. */
    private static final AtomicInteger POOL_COUNTER = new AtomicInteger();

    /** The default thread name pattern: pool counter and per-factory thread number. */
    private static final String DEFAULT_NAME_FORMAT = "pool-%3$d-thread-%1$d";

    private String nameFormat = DEFAULT_NAME_FORMAT;
    private ThreadGroup threadGroup = null;
    private ClassLoader contextClassLoader = null;
    private Boolean isDaemon = null;
    private boolean isPriviledged = false;
    private Integer priority = null;
    private Thread.UncaughtExceptionHandler uncaughtExceptionHandler = null;

    /** For subclasses only; use {@link TaskExecutors#newThreadFactory()} for instances. */
    protected AbstractThreadFactoryBuilder() { }

    /** Returns this builder typed as the concrete subclass for fluent chaining. */
    @SuppressWarnings("unchecked")
    private T self() {
        return (T)this;
    }

    /**
     * Sets the {@link String#format} pattern used to generate thread names.
     * <p>
     * Supported positional arguments: {@code %1$d} &mdash; thread number within this
     * factory, {@code %2$s} &mdash; thread group name, {@code %3$d} &mdash; the global
     * factory counter.
     * </p>
     *
     * @param nameFormat the naming pattern; {@code null} or empty restores the default
     * @return this builder
     */
    public T withNameFormat(String nameFormat) {
        this.nameFormat = nameFormat == null || nameFormat.isEmpty() ? DEFAULT_NAME_FORMAT : nameFormat;
        return self();
    }

    /**
     * Restores the default thread name format ({@code "pool-%3$d-thread-%1$d"}).
     *
     * @return this builder
     */
    public T withDefaultNameFormat() {
        return withNameFormat(DEFAULT_NAME_FORMAT);
    }

    /**
     * Sets the {@link ThreadGroup} for created threads. When unset, the group is taken
     * from the {@code SecurityManager} (if present) or from the thread that creates
     * the threads.
     *
     * @param threadGroup the thread group to use
     * @return this builder
     */
    public T withThreadGroup(ThreadGroup threadGroup) {
        this.threadGroup = threadGroup;
        return self();
    }

    /**
     * Sets the context {@link ClassLoader} assigned to every created thread before its
     * task is run.
     *
     * @param contextClassLoader the class loader to install on created threads
     * @return this builder
     */
    public T withContextClassLoader(ClassLoader contextClassLoader) {
        this.contextClassLoader = contextClassLoader;
        return self();
    }

    /**
     * Enables or disables privileged execution of thread tasks.
     * <p>
     * When enabled, each task runs inside
     * {@link AccessController#doPrivileged(PrivilegedAction, AccessControlContext)} with
     * the {@link AccessControlContext} captured at the moment the factory is
     * {@linkplain #build() built}. This preserves the permissions of the factory creator
     * for threads that would otherwise run with reduced privileges.
     * </p>
     *
     * @param isPriviledged {@code true} to run tasks with the captured privileged context
     * @return this builder
     */
    public T withPriviledgedAccess(boolean isPriviledged) {
        this.isPriviledged = isPriviledged;
        return self();
    }

    /**
     * Marks all created threads as daemon threads.
     *
     * @param daemon {@code true} for daemon threads
     * @return this builder
     */
    public T withDaemonFlag(boolean daemon) {
        this.isDaemon = daemon;
        return self();
    }

    /**
     * Resets the daemon flag setting; created threads keep their default (non-daemon) state.
     *
     * @return this builder
     */
    public T withDefaultDaemonFlag() {
        this.isDaemon = null;
        return self();
    }

    /**
     * Sets the priority of all created threads.
     *
     * @param priority the thread priority, within
     *                 [{@link Thread#MIN_PRIORITY}..{@link Thread#MAX_PRIORITY}]
     * @return this builder
     * @throws IllegalArgumentException if the priority is outside the valid range
     */
    public T withPriority(int priority) {
        if (priority < Thread.MIN_PRIORITY || priority > Thread.MAX_PRIORITY ) {
            throw new IllegalArgumentException(String.format(
                "Thread priority (%d) must be within [%d..%d]", 
                priority, Thread.MIN_PRIORITY, Thread.MAX_PRIORITY));
        }
        this.priority = priority;
        return self();
    }

    /**
     * Resets the priority setting; created threads keep the default priority.
     *
     * @return this builder
     */
    public T withDefaultPriority() {
        this.priority = null;
        return self();
    }

    /**
     * Installs the given {@link Thread.UncaughtExceptionHandler} on every created thread.
     *
     * @param uncaughtExceptionHandler the handler for uncaught exceptions; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if the handler is {@code null}
     */
    public T withUncaughtExceptionHandler(Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = Objects.requireNonNull(uncaughtExceptionHandler);
        return self();
    }

    /**
     * Builds a configured {@link ThreadFactory}.
     * <p>
     * The factory is assembled as a pipeline of decorators: default naming and grouping
     * first, then (optionally) privileged execution with context class loader handling,
     * and finally the thread attributes (daemon flag, priority, uncaught exception
     * handler). Settings left at their defaults add no decorator overhead.
     * </p>
     *
     * @return a new thread factory reflecting all configured options
     */
    public ThreadFactory build() {
        return 
            makeConfigured(
                makePriviledged(
                    makeDefault(POOL_COUNTER.getAndIncrement(), nameFormat, threadGroup), 
                    isPriviledged, contextClassLoader
                ), 
                isDaemon, priority, uncaughtExceptionHandler
            );
    }

    /**
     * Creates the actual thread instance. Implemented by concrete builders to define
     * what kind of thread is produced.
     *
     * @param threadGroup the thread group for the new thread
     * @param runnable    the task the thread will execute
     * @param name        the generated name of the thread
     * @return a new thread
     */
    abstract protected Thread createThread(ThreadGroup threadGroup, Runnable runnable, String name);

    /**
     * Creates the base factory that resolves the thread group and generates thread names
     * according to the configured format.
     *
     * @param poolCounter the unique number of this factory (used as {@code %3$d})
     * @param nameFormat  the thread name format pattern
     * @param threadGroup the explicitly configured group, or {@code null} to resolve it
     * @return the base thread factory
     */
    protected ThreadFactory makeDefault(int poolCounter, String nameFormat, ThreadGroup threadGroup) {
        ThreadGroup actualThreadGroup;
        if (null == threadGroup) {
            SecurityManager sm = System.getSecurityManager();
            if (null != sm) {
                actualThreadGroup = sm.getThreadGroup();
            } else {
                actualThreadGroup = Thread.currentThread().getThreadGroup();
            }
        } else {
            actualThreadGroup = threadGroup;
        }
        AtomicInteger threadCounter = new AtomicInteger(0);
        return r -> createThread(
            actualThreadGroup, r,
            String.format(Locale.getDefault(), nameFormat, threadCounter.getAndIncrement(), actualThreadGroup.getName(), poolCounter)
        );
    }

    /**
     * Wraps the given factory with privileged execution and/or context class loader setup.
     * <p>
     * When {@code isPriviledged} is {@code true}, tasks are wrapped in
     * {@link AccessController#doPrivileged} with the access context captured at the time
     * of this call, and the class loader defaults to the current thread's context class
     * loader (checked against the {@code SecurityManager} eagerly to fail fast).
     * </p>
     *
     * @param origin             the factory to decorate
     * @param isPriviledged      whether to run tasks in a privileged context
     * @param contextClassLoader the class loader to install, or {@code null} for the default
     * @return the decorated factory
     */
    protected ThreadFactory makePriviledged(ThreadFactory origin, boolean isPriviledged, ClassLoader contextClassLoader) {
        if (isPriviledged) {
            ClassLoader actualClassLoader;
            if (null == contextClassLoader) {
                SecurityManager sm = System.getSecurityManager();
                if (sm != null) {
                    // Fail fast
                    sm.checkPermission(new RuntimePermission("getClassLoader"));
                }
                actualClassLoader = Thread.currentThread().getContextClassLoader();
            } else {
                actualClassLoader = contextClassLoader;
            }
            AccessControlContext ctx = AccessController.getContext();
            return makeContextual(
                origin, actualClassLoader, r -> () -> AccessController.doPrivileged(
                    new PrivilegedAction<Void>() {
                        public Void run() {
                            r.run();
                            return null;
                        }
                    }, ctx)
                );
        } else {
            return makeContextual(origin, contextClassLoader);
        }
    }

    /**
     * Wraps the given factory so that created threads receive the specified context
     * class loader; returns the factory unchanged when no class loader is configured.
     *
     * @param origin             the factory to decorate
     * @param contextClassLoader the class loader to install, or {@code null} for no change
     * @return the decorated (or original) factory
     */
    private ThreadFactory makeContextual(ThreadFactory origin, ClassLoader contextClassLoader) {
        return makeContextual(origin, contextClassLoader, Function.identity());
    }

    /**
     * Wraps the given factory so that created threads receive the specified context
     * class loader and their tasks pass through the given wrapper (used to inject
     * privileged execution).
     * <p>
     * The required {@code setContextClassLoader} permission is checked eagerly against
     * the {@code SecurityManager} to fail fast at factory construction time.
     * </p>
     *
     * @param origin             the factory to decorate
     * @param contextClassLoader the class loader to install, or {@code null} for no change
     * @param wrapper            a function transforming the task before execution
     * @return the decorated (or original) factory
     */
    protected ThreadFactory makeContextual(ThreadFactory origin, 
                                           ClassLoader contextClassLoader, 
                                           Function<? super Runnable, ? extends Runnable> wrapper) {
        if (null == contextClassLoader) {
            return origin;
        } else {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                // Fail fast
                sm.checkPermission(new RuntimePermission("setContextClassLoader"));
            }
            return r -> {
                Runnable contextualRunnable = () -> {
                    Thread.currentThread().setContextClassLoader(contextClassLoader);
                     r.run();
                };
                return origin.newThread(wrapper.apply(contextualRunnable)); 
            };
        }
    }

    /**
     * Wraps the given factory so that every created thread receives the configured
     * daemon flag, priority, and uncaught exception handler.
     * <p>
     * Returns the factory unchanged when no thread attributes were configured, so the
     * common case adds no decorator overhead.
     * </p>
     *
     * @param origin                     the factory to decorate
     * @param isDaemon                   the daemon flag, or {@code null} to keep the default
     * @param priority                   the thread priority, or {@code null} to keep the default
     * @param uncaughtExceptionHandler   the exception handler, or {@code null} to keep the default
     * @return the decorated (or original) factory
     */
    protected ThreadFactory makeConfigured(ThreadFactory origin, 
                                           Boolean isDaemon, 
                                           Integer priority, 
                                           Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
        if (null == isDaemon && null == priority && null == uncaughtExceptionHandler) {
            return origin;
        } else {
            return r -> {
              Thread t = origin.newThread(r);
              if (null != priority && priority != t.getPriority()) {
                  t.setPriority(priority);
              }
              if (null != isDaemon && isDaemon != t.isDaemon()) {
                  t.setDaemon(isDaemon);
              }
              if (null != uncaughtExceptionHandler) {
                  t.setUncaughtExceptionHandler(uncaughtExceptionHandler);
              }
              return t;
            };
        }
    }
}