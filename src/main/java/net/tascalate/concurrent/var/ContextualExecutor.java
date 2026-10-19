/**
 * Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.tascalate.concurrent.var;

import java.util.concurrent.Executor;

/**
 * An {@link Executor} decorator that propagates relayed context variables to executed tasks.
 * <p>
 * <b>Dynamic resolution:</b> the context snapshot is <em>not</em> fixed when the executor
 * is wrapped. Instead, each {@link #execute(Runnable)} call captures the context that is
 * active in the submitting thread at that moment, and the command is executed within that
 * snapshot. Consequently, tasks submitted from different contexts inherit different snapshots.
 * </p>
 *
 * @param <D> the type of the delegate executor
 * @see ContextRelay#contextual(Executor)
 */
class ContextualExecutor<D extends Executor> implements Executor {

    /** The wrapped executor receiving contextualized tasks. */
    protected final D delegate;

    /** The relay used to capture context at submission time. */
    protected final ContextRelay<?> ctxRelay;

    /**
     * Creates a decorator for the given executor.
     *
     * @param delegate the executor to wrap
     * @param ctxRelay the relay capturing context on each submission
     */
    ContextualExecutor(D delegate, ContextRelay<?> ctxRelay) {
        this.delegate = delegate;
        this.ctxRelay = ctxRelay;
    }

    /**
     * Captures the current context of the calling (submitting) thread and submits
     * the command wrapped with that snapshot to the delegate executor.
     *
     * @param command the task to execute
     */
    @Override
    public void execute(Runnable command) {
        delegate.execute(contextualRunnable(command));
    }

    /**
     * Wraps the given task with the context snapshot taken <b>at the moment of this call</b>.
     *
     * @param original the original task
     * @return a task that applies the captured context before running the original
     */
    Runnable contextualRunnable(Runnable original) {
        return ctxRelay.contextual(original);
    }
}