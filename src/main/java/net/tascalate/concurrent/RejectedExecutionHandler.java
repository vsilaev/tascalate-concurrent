/**
 * Copyright 2015-2025 Valery Silaev (http://vsilaev.com)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:

 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.

 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.tascalate.concurrent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;

@FunctionalInterface
public interface RejectedExecutionHandler<E extends ExecutorService> {
    public void rejectedExecution(Runnable r, E e);
    
    public static class CallerRunsPolicy implements RejectedExecutionHandler<ExecutorService> {
        public void rejectedExecution(Runnable r, ExecutorService e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }
    
    public static class AbortPolicy implements RejectedExecutionHandler<ExecutorService> {
        public void rejectedExecution(Runnable r, ExecutorService e) {
            throw new RejectedExecutionException("Task " + r.toString() +
                                                 " rejected from " +
                                                 e.toString());
        }
    }
    
    public static class DiscardPolicy implements RejectedExecutionHandler<ExecutorService> {
        public void rejectedExecution(Runnable r, ExecutorService e) {
        }
    }
    
    public static abstract class QueueDependentRejectedExecutionHandler<E extends ExecutorService>
                                 implements RejectedExecutionHandler<E> {
        private final Function<? super E, ? extends BlockingQueue<Runnable>> queueGetter;
        
        public QueueDependentRejectedExecutionHandler(Function<? super E, ? extends BlockingQueue<Runnable>> queueGetter) {
            this.queueGetter = queueGetter;
        }
        
        @Override
        public final void rejectedExecution(Runnable r, E e) {
            rejectedExecution(r, e, queueGetter.apply(e));
        }
        
        protected abstract void rejectedExecution(Runnable r, E e, BlockingQueue<Runnable> q);
    }
    
    public static class DiscardOldestPolicy<E extends ExecutorService> 
                       extends QueueDependentRejectedExecutionHandler<E> {

        public DiscardOldestPolicy(Function<? super E, ? extends BlockingQueue<Runnable>> queueGetter) {
            super(queueGetter);
        }

        protected void rejectedExecution(Runnable r, E e, BlockingQueue<Runnable> q) {
            if (!e.isShutdown()) {
                q.poll();
                e.execute(r);
            } 
        }
    }
    
    public static class BlockPolicy<E extends ExecutorService> 
                        extends QueueDependentRejectedExecutionHandler<E> {

        public BlockPolicy(Function<? super E, ? extends BlockingQueue<Runnable>> queueGetter) {
            super(queueGetter);
        }
        
        protected void rejectedExecution(Runnable r, E e, BlockingQueue<Runnable> q) {
            if (!e.isShutdown()) {
                try {
                    q.put(r);
                } catch (InterruptedException ex) {
                    throw new RejectedExecutionException(ex);
                }
            }
        }
    }
    
    public static <E extends ExecutorService> BlockPolicy<E> blockPolicy(Function<? super E, ? extends BlockingQueue<Runnable>> queueGetter) {
        return new BlockPolicy<>(queueGetter);
    }
    
    public static <E extends ExecutorService> DiscardOldestPolicy<E> discardOldestPolicy(Function<? super E, ? extends BlockingQueue<Runnable>> queueGetter) {
        return new DiscardOldestPolicy<>(queueGetter);
    }
    
    public static final class TPE {
        private TPE() {
        }
        
        public static class AbortPolicy extends ThreadPoolExecutor.AbortPolicy 
                                        implements RejectedExecutionHandler<ThreadPoolExecutor> { }
        
        public static class CallerRunsPolicy extends ThreadPoolExecutor.CallerRunsPolicy
                                             implements RejectedExecutionHandler<ThreadPoolExecutor> { }
        
        public static class DiscardPolicy extends ThreadPoolExecutor.DiscardPolicy
                                          implements RejectedExecutionHandler<ThreadPoolExecutor> { }
        
        public static class DiscardOldestPolicy extends ThreadPoolExecutor.DiscardOldestPolicy
                                                implements RejectedExecutionHandler<ThreadPoolExecutor> { }
        
        public static class BlockPolicy extends RejectedExecutionHandler.BlockPolicy<ThreadPoolExecutor>
                                        implements java.util.concurrent.RejectedExecutionHandler {

            public BlockPolicy() {
                super(ThreadPoolExecutor::getQueue);
            }
        }

        public static final CallerRunsPolicy CALLER_RUNS_POLICY = new CallerRunsPolicy();
        public static final AbortPolicy ABORT_POLICY = new AbortPolicy();
        public static final DiscardPolicy DISCARD_POLICY = new DiscardPolicy();
        public static final DiscardOldestPolicy DISCARD_OLDEST_POLICY = new DiscardOldestPolicy();
        public static final BlockPolicy BLOCK_POLICY = new BlockPolicy();
    }
    
    public static final RejectedExecutionHandler<ExecutorService> CALLER_RUNS_POLICY = new CallerRunsPolicy();
    public static final RejectedExecutionHandler<ExecutorService> ABORT_POLICY = new AbortPolicy();
    public static final RejectedExecutionHandler<ExecutorService> DISCARD_POLICY = new DiscardPolicy();
}
