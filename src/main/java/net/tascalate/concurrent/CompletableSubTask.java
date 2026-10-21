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

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * The {@link Promise} implementation for <i>intermediate</i> long-running blocking
 * tasks — the stages produced by the asynchronous composition methods of a
 * {@link CompletableTask} or of another {@code CompletableSubTask}.
 *
 * <p>
 * Unlike the root {@link CompletableTask}, whose action is known at construction time,
 * a sub-task's action is installed <i>later</i> by the composition machinery via
 * {@link #fireTransition(Callable)}. This is implemented with a
 * {@link DelegatingCallable} whose single delegate slot is filled exactly once
 * (guarded by a compare-and-set).
 * </p>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>A {@code CompletableSubTask} is created by
 *       {@link AbstractCompletableTask#createCompletionStage(Executor)} when a
 *       composition method is called on the previous stage.</li>
 *   <li>When the previous stage completes, the composition machinery invokes
 *       {@link #fireTransition(Callable)} with the next action; the action is
 *       installed into the delegating callable and the underlying task is run.</li>
 *   <li>When the action completes, the sub-task resolves and, in turn, triggers the
 *       next stage (if any).</li>
 * </ol>
 *
 * @param <T> the type of the task result
 * @see CompletableTask
 * @see AbstractCompletableTask
 */
class CompletableSubTask<T> extends AbstractCompletableTask<T> {

    /**
     * A {@link Callable} whose implementation is supplied later, exactly once.
     * <p>
     * The delegate is installed via {@link #setup(Callable)} using a CAS on a
     * {@link AtomicReferenceFieldUpdater}; any second attempt to set it throws
     * {@link IllegalStateException}. Calling {@link #call()} before a delegate is
     * installed also throws {@link IllegalStateException}.
     * </p>
     * <p>
     * The CAS-based slot uses a field updater rather than an {@link java.util.concurrent.atomic.AtomicReference}
     * to avoid the per-instance object allocation that {@code AtomicReference} would incur for every
     * composition step in a chain.
     * </p>
     *
     * @param <T> the result type of the delegated callable
     */
    private static class DelegatingCallable<T> implements Callable<T> {

        /**
         * Field updater for the {@link #delegate} slot, used for the one-time CAS setup.
         * Declared with raw types because the enclosing class is generic and field
         * updaters operate on the erased type.
         */
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<DelegatingCallable, Callable> DELEGATE_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(DelegatingCallable.class, Callable.class, "delegate");

        /**
         * The delegate callable, installed exactly once via {@link #setup(Callable)}.
         * {@code volatile} for visibility across threads; accessed through
         * {@link #DELEGATE_UPDATER} for CAS and directly for reads.
         */
        private volatile Callable<T> delegate = null;

        /**
         * Installs the delegate callable. May be called only once.
         *
         * @param delegate the callable to delegate to
         * @throws IllegalStateException if a delegate was already set
         */
        void setup(Callable<T> delegate) {
            boolean updated = DELEGATE_UPDATER.compareAndSet(this, null, (Callable<?>) delegate);
            if (!updated) {
                throw new IllegalStateException("Delegate may be set only once");
            }
        }

        /**
         * Delegates to the installed callable.
         *
         * @return the result of the delegate
         * @throws Exception            if the delegate throws
         * @throws IllegalStateException if no delegate has been installed
         */
        @Override
        public T call() throws Exception {
            Callable<T> d = delegate;
            if (null == d) {
                throw new IllegalStateException("Call is not configured");
            } else {
                return d.call();
            }
        }
    }

    /** The delegating action whose delegate is installed by {@link #fireTransition(Callable)}. */
    private final DelegatingCallable<T> action;

    /**
     * Creates a sub-task bound to the given default executor, with an as-yet-uninstalled
     * action.
     *
     * @param executor the default executor for asynchronous composition steps
     */
    CompletableSubTask(Executor executor) {
        this(executor, new DelegatingCallable<>());
    }

    /**
     * Internal constructor pairing the executor with a specific delegating action.
     *
     * @param executor the default executor for asynchronous composition steps
     * @param action   the delegating action to be installed later
     */
    private CompletableSubTask(Executor executor, DelegatingCallable<T> action) {
        super(executor, action);
        this.action = action;
    }

    /**
     * Installs the given action as this stage's callable (exactly once) and runs it.
     * Invoked by the composition machinery when the preceding stage completes.
     *
     * @param code the action to execute for this stage
     */
    @Override
    final void fireTransition(Callable<T> code) {
        action.setup(code);
        task.run();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Creates another {@code CompletableSubTask}, so composition chains remain sequences
     * of intermediate sub-tasks after the initial root.
     * </p>
     */
    @Override
    protected <U> AbstractCompletableTask<U> createCompletionStage(Executor executor) {
        return new CompletableSubTask<U>(executor);
    }
}