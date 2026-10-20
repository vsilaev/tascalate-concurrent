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

/**
 * A concrete {@link AbstractThreadFactoryBuilder} that produces standard platform
 * {@link Thread}s.
 *
 * <p>
 * Obtain instances via {@link TaskExecutors#newThreadFactory()} and configure them
 * with the fluent builder API:
 * </p>
 *
 * <pre>{@code
 * ThreadFactory factory = TaskExecutors.newThreadFactory()
 *     .withNameFormat("my-pool-%3$d-worker-%1$d")
 *     .withDaemonFlag(true)
 *     .withPriority(Thread.NORM_PRIORITY)
 *     .build();
 *
 * TaskExecutorService executor = TaskExecutors.newFixedThreadPool(4, factory);
 * }</pre>
 *
 * <p>
 * Subclass {@link AbstractThreadFactoryBuilder} instead when threads of a different
 * kind must be produced (e.g., custom {@code Thread} subclasses or alternative
 * thread implementations).
 * </p>
 *
 * @see AbstractThreadFactoryBuilder
 * @see ThreadGroupBuilder
 * @see TaskExecutors#newThreadFactory()
 */
public class ThreadFactoryBuilder extends AbstractThreadFactoryBuilder<ThreadFactoryBuilder> {

    /** Package-private constructor; use {@link TaskExecutors#newThreadFactory()}. */
    protected ThreadFactoryBuilder() {}

    /**
     * Creates a new standard platform {@link Thread}.
     *
     * @param threadGroup the thread group for the new thread
     * @param runnable    the task the thread will execute
     * @param name        the generated name of the thread
     * @return a new {@link Thread} instance
     */
    @Override
    protected Thread createThread(ThreadGroup threadGroup, Runnable runnable, String name) {
        return new Thread(threadGroup, runnable, name);
    }
}