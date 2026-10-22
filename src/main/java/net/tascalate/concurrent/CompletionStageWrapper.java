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

import static net.tascalate.concurrent.AsyncInternals.cancelPromise;
import static net.tascalate.concurrent.AsyncInternals.unwrapCompletionException;
import static net.tascalate.concurrent.AsyncInternals.wrapCompletionException;
import static net.tascalate.concurrent.AsyncInternals.wrapExecutionException;

import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import net.tascalate.concurrent.decorators.AbstractPromiseLikeDecorator;
import net.tascalate.concurrent.decorators.AbstractPromiseDecorator;
import net.tascalate.concurrent.decorators.BlockingCompletionStageDecorator;

/**
 * A wrapper that adapts an arbitrary {@link CompletionStage} to the {@link Promise} API.
 * <p>
 * Since {@link CompletionStage} lacks blocking access methods (like {@link Future#get()}),
 * this wrapper provides a thread-safe blocking mechanism using a {@link CountDownLatch}
 * that is released when the underlying stage completes.
 * </p>
 * <p>
 * It also includes a {@link StrictPromise} inner class to enforce strict cancellation
 * semantics for composition methods (like {@code thenCompose}), which the standard
 * {@link CompletionStage} API often handles poorly.
 * </p>
 *
 * @param <T> the type of the successfully resolved promise value
 * @author vsilaev
 * @see AbstractPromiseLikeDecorator
 * @see Promise
 */
public class CompletionStageWrapper<T> extends AbstractPromiseLikeDecorator<T, CompletionStage<T>>
                                       implements Promise<T> {

    /** Latch used to block until the underlying stage completes. */
    private final CountDownLatch whenDone;
    
    /** The successful result of the stage, populated upon completion. */
    private volatile T result;
    
    /** The exceptional failure of the stage, populated upon completion. */
    private volatile Throwable fault;

    /**
     * Creates a new wrapper around the specified {@link CompletionStage}.
     *
     * @param delegate the stage to wrap
     */
    protected CompletionStageWrapper(CompletionStage<T> delegate) {
        super(delegate);
        whenDone = new CountDownLatch(1);
        result   = null;
        fault    = null;
        delegate.whenComplete((r, e) -> {
           // It's a responsibility of delegate to call 
           // this callback at most once -- any valid
           // implementation should not invoke callback
           // more than once, so result/fault will not
           // be overwritten
           result   = r;
           fault    = e;
           whenDone.countDown();
        });
    }

    /**
     * {@inheritDoc}
     * <p>
     * Delegates cancellation to the underlying stage via {@link AsyncInternals#cancelPromise}.
     * </p>
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return cancelPromise(delegate, mayInterruptIfRunning);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Blocks until the stage completes, then returns the result or throws the
     * appropriate exception.
     * </p>
     */
    @Override
    public T get() throws InterruptedException, ExecutionException {
        whenDone.await();
        if (null == fault) {
            return result;
        } else if (fault instanceof CancellationException) {
            // If explicitly cancelled then fault is not wrapped
            throw (CancellationException)fault;
        } else {
            throw wrapExecutionException(unwrapCompletionException(fault));
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Blocks until the stage completes or the timeout elapses, then returns the
     * result or throws the appropriate exception.
     * </p>
     */
    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        whenDone.await(timeout, unit);
        if (null == fault) {
            return result;
        } else if (fault instanceof CancellationException) {
         // If explicitly cancelled then fault is not wrapped
            throw (CancellationException)fault;
        } else {
            throw wrapExecutionException(unwrapCompletionException(fault));
        }
    }

    /** {@inheritDoc} */
    @Override 
    public boolean isIn(State state) {
        if (null == state) {
            return false;
        }
        if (isDone()) {
            if (null == fault) {
                return state == Promise.State.SUCCESS;
            } else if (isCancelled()) {
                return state == Promise.State.CANCELLED;
            } else {
                return state == Promise.State.FAILED;
            }
        } else {
            return state == Promise.State.RUNNING;
        }
    }

    /** {@inheritDoc} */
    @Override 
    public boolean isIn(Set<State> states) {
        if (null == states || states.isEmpty()) {
            return false;
        }
        if (isDone()) {
            if (null == fault) {
                return states.contains(Promise.State.SUCCESS);
            } else if (isCancelled()) {
                return states.contains(Promise.State.CANCELLED);
            } else {
                return states.contains(Promise.State.FAILED);
            }
        } else {
            return states.contains(Promise.State.RUNNING);
        }
    }

    /** {@inheritDoc} */
    @Override 
    public T resultNow() {
        if (isDone()) {
            if (null == fault) {
                return result;
            } else if (isCancelled()) {
                throw new IllegalStateException("Task was cancelled");
            } else {
                throw new IllegalStateException("Task completed with exception");
            }
        } else {
            throw new IllegalStateException("Task has not completed");
        }
    }

    /** {@inheritDoc} */
    @Override 
    public Throwable exceptionNow() {
        if (isDone()) {
            if (null == fault) {
                throw new IllegalStateException("Task completed with a result");
            } else if (isCancelled()) {
                throw new IllegalStateException("Task was cancelled");
            } else {
                return fault;
            }
        } else {
            throw new IllegalStateException("Task has not completed");
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCompletedExceptionally() {
        return isDone() && fault != null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCancelled() {
        return isDone() && fault instanceof CancellationException;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDone() {
        return whenDone.getCount() == 0;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Blocks until completion and returns the result, wrapping any exception
     * in a {@link CompletionException}.
     * </p>
     */
    @Override
    public T join() {
        try {
            whenDone.await();
        } catch (InterruptedException ex) {
            throw new CompletionException(ex);
        }
        if (null == fault) {
            return result;
        } else if (fault instanceof CancellationException) {
            throw (CancellationException)fault;
        } else {
            throw wrapCompletionException(fault);
        }        
    }

    /**
     * {@inheritDoc}
     * <p>
     * Delegates to {@link Promises#from(CompletionStage)} to ensure consistent
     * wrapping behavior for derived stages.
     * </p>
     */
    @Override
    protected final <U> Promise<U> wrapNew(CompletionStage<U> original) {
        return Promises.from(original);
    }

    /**
     * Adapts the given {@link CompletionStage} to a {@link Promise}.
     *
     * @param <T>   the result type
     * @param stage the stage to adapt
     * @return the adapted promise
     */
    public static <T> Promise<T> from(CompletionStage<T> stage) {
        return from(stage, true);
    }

    /**
     * Adapts the given {@link CompletionStage} to a {@link Promise}, with an option
     * to enforce strict cancellation semantics.
     *
     * @param <T>           the result type
     * @param stage         the stage to adapt
     * @param strictPromise if {@code true}, wraps the result in a {@link StrictPromise}
     *                      to ensure proper cancellation propagation
     * @return the adapted promise
     */
    public static <T> Promise<T> from(CompletionStage<T> stage, boolean strictPromise) {
        Promise<T> result;
        if (stage instanceof Future) {
            // If we can delegate blocking Future API...
            result = BlockingCompletionStageDecorator.from(stage);
        } else {
            // Otherwise fallback to own implementation
            result = new CompletionStageWrapper<>(stage);
        }
        return strictPromise ? new StrictPromise<>(result) : result;
    }

    /**
     * A strict wrapper that enforces proper cancellation propagation for composition
     * methods like {@code thenCompose} and {@code exceptionallyCompose}, which the
     * default {@link CompletionStage} API often handles poorly.
     *
     * @param <T> the result type
     */
    static class StrictPromise<T> extends AbstractPromiseDecorator<T, Promise<T>> {
        
        /**
         * Creates a new strict promise wrapping the given delegate.
         *
         * @param delegate the promise to wrap
         */
        StrictPromise(Promise<T> delegate) {
            super(delegate);
        }

        // Don't unwrap
        /** {@inheritDoc} */
        @Override
        public Promise<T> unwrap() {
            return this;
        }

        // Don't unwrap
        /** {@inheritDoc} */
        @Override
        public Promise<T> raw() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        protected <U> Promise<U> wrapNew(CompletionStage<U> original) {
            return new StrictPromise<>((Promise<U>)original);
        }

        /**
         * {@inheritDoc}
         * <p>
         * Overridden to capture the resulting stage and attach an {@code onCancel} hook,
         * ensuring cancellation propagates correctly.
         * </p>
         */
        @Override
        public <U> Promise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
            CompletionStageRef<U> ref = new CompletionStageRef<>();
            return super.thenCompose(ref.captureResult(fn)).onCancel(ref.cancel);
        }

        /** {@inheritDoc} */
        @Override
        public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
            CompletionStageRef<U> ref = new CompletionStageRef<>();
            return super.thenComposeAsync(ref.captureResult(fn)).onCancel(ref.cancel);
        }

        /** {@inheritDoc} */
        @Override
        public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, 
                                               Executor executor) {
            CompletionStageRef<U> ref = new CompletionStageRef<>();
            return super.thenComposeAsync(ref.captureResult(fn), executor).onCancel(ref.cancel);        
        }        

        /**
         * {@inheritDoc}
         * <p>
         * The default {@link CompletionStage} API implementation for {@code exceptionallyAsync} /
         * {@code exceptionallyCompose[Async]} does not handle cancellation well due to numerous
         * orchestrated internal calls. This implementation uses a safer, custom approach.
         * </p>
         */
        @Override
        public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
            return AsyncInternals.exceptionallyAsync(this, fn);
        }

        /** {@inheritDoc} */
        @Override
        public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
            return AsyncInternals.exceptionallyAsync(this, fn, executor);
        }

        /** {@inheritDoc} */
        @Override
        public Promise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
            return AsyncInternals.exceptionallyCompose(this, fn);
        }

        /** {@inheritDoc} */
        @Override
        public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
            return AsyncInternals.exceptionallyComposeAsync(this, fn);
        }

        /** {@inheritDoc} */
        @Override
        public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, 
                                                    Executor executor) {
            return AsyncInternals.exceptionallyComposeAsync(this, fn, executor);
        }        
    }
}