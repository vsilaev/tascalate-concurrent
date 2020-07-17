/**
 * Copyright 2015-2020 Valery Silaev (http://vsilaev.com)
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

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

final class CompletionIterator<T> implements Iterator<T>, AutoCloseable {
    
    protected static enum CancelStrategy {
        NONE {
            @Override
            void apply(Set<CompletionStage<?>> enlistedPromises, Iterator<? extends CompletionStage<?>> pendingPromises) {
                
            }
        },
        ENLISTED {
            @Override
            void apply(Set<CompletionStage<?>> enlistedPromises, Iterator<? extends CompletionStage<?>> pendingPromises) {
                enlistedPromises.forEach(p -> SharedFunctions.cancelPromise(p, true));
            }
        },
        ALL {
            @Override
            void apply(Set<CompletionStage<?>> enlistedPromises, Iterator<? extends CompletionStage<?>> pendingPromises) {
                ENLISTED.apply(enlistedPromises, pendingPromises);
                while (pendingPromises.hasNext()) {
                    CompletionStage<?> nextPromise = pendingPromises.next();
                    SharedFunctions.cancelPromise(nextPromise, true);
                }
            }            
        };
        
        abstract void apply(Set<CompletionStage<?>> enlistedPromises, Iterator<? extends CompletionStage<?>> pendingPromises);
    }
    
    private final int chunkSize;
    private final CancelStrategy cancelStrategy;
    private final Iterator<? extends CompletionStage<? extends T>> pendingPromises;
    private final BlockingQueue<Try<T>> settledResults;
    private final Set<CompletionStage<?>> enlistedPromises;
    
    private final AtomicInteger inProgress = new AtomicInteger(0);
    
    CompletionIterator(Iterator<? extends CompletionStage<? extends T>> pendingValues, int chunkSize) {
        this(pendingValues, chunkSize, CancelStrategy.NONE);
    }
    
    protected CompletionIterator(Iterator<? extends CompletionStage<? extends T>> pendingValues, 
                                 int chunkSize, 
                                 CancelStrategy cancelStrategy) {
        this.chunkSize        = chunkSize;
        this.cancelStrategy   = cancelStrategy == null ? CancelStrategy.NONE : cancelStrategy;
        this.pendingPromises  = pendingValues;
        this.settledResults   = chunkSize > 0 ? new LinkedBlockingQueue<>(chunkSize) 
                                              : new LinkedBlockingQueue<>(); 
        
        this.enlistedPromises = Collections.newSetFromMap(new ConcurrentHashMap<>()); 
    }
    
    @Override
    public boolean hasNext() {
        int unprocessed = inProgress.get();
        if (unprocessed < 0) {
            // Forcibly closed
            return false;
        } else {
            if (!settledResults.isEmpty()) {
                // There are some resolved results available
                return true; 
            } else {
                if (unprocessed > 0) {
                    // If we are still producing then there are more...            
                    return true;
                } else {
                    // More was enlisted - then some available
                    return enlistPending();
                }
            }
        }
    }

    @Override
    public T next() {
        while (true) {
            int unprocessed = inProgress.get();
            if (unprocessed < 0) {
                // Forcibly closed
                throw new NoSuchElementException("This sequence was closed");
            } else {
                if (!settledResults.isEmpty()) {
                    // There are some resolved results available
                    return settledResults.poll().done(); 
                } else {
                    if (unprocessed > 0) {
                        // If we are still producing then await for any result...  
                        try {
                            return settledResults.take().done();
                        } catch (InterruptedException ex) {
                            throw new NoSuchElementException(ex.getMessage());
                        }
                    } else {
                        if (enlistPending()) {
                            // More was enlisted
                            continue; //recursion via loop
                        } else {
                            // ...or stop when over
                            throw new NoSuchElementException();
                        }
                    }
                }
            }
        }
    }
    
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void close() {
        inProgress.set(Integer.MIN_VALUE);
        settledResults.clear();
        cancelStrategy.apply(enlistedPromises, pendingPromises);
    }
    
    private boolean enlistPending() {
        boolean enlisted = false;
        int i = 0;
        while (pendingPromises.hasNext()) {
            // +1 before setting completion handler -- 
            // while stage may be completed already
            // we should increment step-by-step 
            // instead of setting the value at once
            int isClosed = inProgress.getAndIncrement(); 
            if (isClosed < 0) {
                break;
            }
            CompletionStage<? extends T> nextPromise = pendingPromises.next();
            enlistedPromises.add(nextPromise);
            nextPromise.whenComplete(enlistResolved(nextPromise));
            enlisted = true;
            
            i++;
            if (chunkSize > 0 && i >= chunkSize) {
                break;
            }
        };  
        return enlisted;
    }
    
    private BiConsumer<T, Throwable> enlistResolved(CompletionStage<? extends T> promise) {
        return (v, ex) -> {
            enlistedPromises.remove(promise);
            enlistResolved(v, ex);
        };
    }
    
    private void enlistResolved(T resolvedValue, Throwable ex) {
        try {
            if (ex == null) {
                settledResults.put(Try.success(resolvedValue));
            } else {
                settledResults.put(Try.failure(SharedFunctions.wrapCompletionException(ex)));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e); // Shouldn't happen for the queue with an unlimited size
        }
        inProgress.decrementAndGet();
    }
}
