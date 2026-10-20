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
 * A fluent builder for {@link ThreadGroup}s, intended for configuring the thread
 * groups used by custom thread factories.
 *
 * <p>
 * Obtain instances via {@link TaskExecutors#newThreadGroup()}. All properties are
 * optional: an unset property keeps the JVM default of the parent group.
 * </p>
 *
 * <pre>{@code
 * ThreadGroup group = TaskExecutors.newThreadGroup()
 *     .withName("async-io-workers")
 *     .withDaemonFlag(true)
 *     .withMaxPriority(Thread.NORM_PRIORITY)
 *     .build();
 * }</pre>
 *
 * <p>
 * The built group can then be passed to a thread factory, for example:
 * </p>
 *
 * <pre>{@code
 * ThreadFactory factory = TaskExecutors.newThreadFactory()
 *     .withThreadGroup(group)
 *     .build();
 * }</pre>
 *
 * @see ThreadFactoryBuilder
 * @see TaskExecutors#newThreadGroup()
 */
public final class ThreadGroupBuilder {

    private String name;
    private Boolean isDaemon;
    private ThreadGroup parent;
    private Integer maxPriority;

    /** Package-private constructor; use {@link TaskExecutors#newThreadGroup()}. */
    ThreadGroupBuilder() {
    }

    /**
     * Sets the name of the thread group to be created.
     *
     * @param name the group name
     * @return this builder
     */
    public ThreadGroupBuilder withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Marks the thread group as a daemon group.
     *
     * @param isDaemon {@code true} to make the group a daemon group
     * @return this builder
     */
    public ThreadGroupBuilder withDaemonFlag(boolean isDaemon) {
        this.isDaemon = isDaemon;
        return this;
    }

    /**
     * Resets the daemon flag setting; the group inherits the default flag of its parent.
     *
     * @return this builder
     */
    public ThreadGroupBuilder withDefaultDaemonFlag() {
        this.isDaemon = null;
        return this;
    }

    /**
     * Sets the maximum priority that threads in the group may have.
     *
     * @param maxPriority the maximum priority, within
     *                    [{@link Thread#MIN_PRIORITY}..{@link Thread#MAX_PRIORITY}]
     * @return this builder
     * @throws IllegalArgumentException if the priority is outside the valid range
     */
    public ThreadGroupBuilder withMaxPriority(int maxPriority) {
        if (maxPriority < Thread.MIN_PRIORITY || maxPriority > Thread.MAX_PRIORITY ) {
            throw new IllegalArgumentException(String.format(
                "ThreadGroup max priority (%d) must be within [%d..%d]", 
                maxPriority, Thread.MIN_PRIORITY, Thread.MAX_PRIORITY));
        }
        this.maxPriority = maxPriority;
        return this;
    }

    /**
     * Resets the maximum priority setting; the group inherits the default of its parent.
     *
     * @return this builder
     */
    public ThreadGroupBuilder withDefaultMaxPriority() {
        this.maxPriority = null;
        return this;
    }

    /**
     * Sets the parent thread group. When not set, the thread group of the thread
     * calling {@link #build()} is used as the parent.
     *
     * @param parent the parent thread group
     * @return this builder
     */
    public ThreadGroupBuilder withParent(ThreadGroup parent) {
        this.parent = parent;
        return this;
    }

    /**
     * Creates the {@link ThreadGroup} with the configured properties.
     * <p>
     * Properties left unset retain the defaults inherited from the parent group;
     * {@code setDaemon} / {@code setMaxPriority} are invoked only when the configured
     * value actually differs from the inherited one.
     * </p>
     *
     * @return the newly created thread group
     */
    public ThreadGroup build() {
        ThreadGroup g = new ThreadGroup(parent == null ? Thread.currentThread().getThreadGroup() : parent, name);
        if (null != isDaemon && g.isDaemon() != isDaemon) {
            g.setDaemon(isDaemon);
        }
        if (null != maxPriority && g.getMaxPriority() != maxPriority) { 
            g.setMaxPriority(maxPriority);
        }
        return g;
    }
}