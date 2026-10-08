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
package net.tascalate.concurrent.channel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Promises;
import net.tascalate.concurrent.Try;

final class SelectCall<T> {

    private final SelectCase<T>[] cases;
    private final Integer[] order;
    private final Set<Integer> failedIndexes = new HashSet<>();
    
    private SelectCall(SelectCase<T>[] cases) {
        this.cases = cases;
        this.order = new Integer[cases.length];

        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        
        // Fisher-Yates shuffle (Go picks randomly among ready cases)
        Collections.shuffle(Arrays.asList(order));
    }
    
    
    private Try<SelectResult<T>> phase1() {
        Throwable firstFailure = null;

        // Phase 1: non-blocking try
        int defaultOriginalIdx = -1;
        
        for (int idx = 0; idx < order.length; idx++) {
            int originalIdx = order[idx];
            SelectCase<T> c = cases[originalIdx];

            if (c instanceof SelectCase.Default) {
                if (defaultOriginalIdx >= 0) {
                    throw new IllegalArgumentException("Multiple default cases in select");
                }
                defaultOriginalIdx = originalIdx;
            } else if (c instanceof SelectCase.Disabled) {
                // Skip
            } else if (c instanceof SelectCase.Receive) {
                SelectCase.Receive<T> trc = (SelectCase.Receive<T>)c;
                Try<T> r = trc.channel().tryReceive();
                
                if (r == null) {
                    continue; // not ready, try next case
                } else if (r.isFailure()) {
                    if (null == firstFailure) {
                        firstFailure = r.error();
                    }
                    failedIndexes.add(idx);
                } else if (r.isSuccess()) {
                    return success(originalIdx, trc, r.value() /*isSend=false*/);
                } else {
                    throw new IllegalStateException();
                }
            } else if (c instanceof SelectCase.Send) {
                SelectCase.Send<T> tsc = (SelectCase.Send<T>)c; 
                
                Try<T> r = tsc.channel().trySend(tsc.value());
                
                if (r == null) {
                    continue; // Not ready (full), try next case
                } else if (r.isFailure()) {
                    if (firstFailure == null) {
                        firstFailure = r.error();
                    }
                    failedIndexes.add(idx);
                } else if (r.isSuccess()) {
                    // Successfully sent!
                    return success(originalIdx, tsc, r.value());
                }
            } else {
                throw new IllegalArgumentException("Unsupported SelectCase type: " + c.getClass().getName());
            }
        }

        // Nothing was immediately ready -> use default if present
        if (defaultOriginalIdx >= 0) {
            return success(defaultOriginalIdx, cases[defaultOriginalIdx], null /*isSend=false*/);
        } else if (firstFailure != null) {
            return Try.failure(firstFailure);
        } else {
            return null;
        }
    }
    
    private List<CompletionStage<SelectResultHolder<T>>> phase2() {
        // Phase 2: async wait using tascalate Promises
        List<CompletionStage<SelectResultHolder<T>>> stages = new ArrayList<>();
        
        SelectCoordinator coordinator = SelectCoordinator.createFirstWins();
        
        for (int idx = 0; idx < order.length; idx++) {
            if (failedIndexes.contains(idx)) {
                continue;
            }
            
            int originalIdx = order[idx];
            SelectCase<T> c = cases[originalIdx];

            if (c instanceof SelectCase.Default || c instanceof SelectCase.Disabled) {
                continue; 
            } 

            Promise<T> originalFuture;

            if (c instanceof SelectCase.Receive) {
                originalFuture = ((SelectCase.Receive<T>)c).channel().receive(coordinator);
            } else if (c instanceof SelectCase.Send) {
                SelectCase.Send<T> tsc = (SelectCase.Send<T>)c; 
                originalFuture = tsc.channel().send(tsc.value(), coordinator);
            } else {
                throw new IllegalArgumentException("Unsupported SelectCase type: " + c.getClass().getName());
            }

            CompletionStage<SelectResultHolder<T>> stage = 
            originalFuture.dependent()
                          .handle((val, ex) -> {
                              if (ex instanceof CancellationException) {
                                  throw (CancellationException)ex;
                              } else {
                                  return new SelectResultHolder<>(originalIdx, c, val, ex); 
                              }
                          }, true);
            stages.add(stage);
        }

        return stages;
      
    }
    
    Promise<SelectResult<T>> execute() {
        Try<SelectResult<T>> readyResult = phase1();
        if (readyResult != null && readyResult.isSuccess()) {
            return Promises.success(readyResult.value());
        }
        List<CompletionStage<SelectResultHolder<T>>> stages = phase2();
        if (stages.isEmpty()) {
            if (readyResult != null) {
                return Promises.failure(readyResult.error());
            } else {
                // All cases are Disabled (no Default present, otherwise Phase 1
                // would have returned). Go semantics: select{} blocks forever.
                return NilChannel.incomplete();
            }
        } else {
            // Promises.any races the stages. The first to complete wins.
            // It automatically calls .cancel() on the losing stages.
            // Thanks to dependent(true), this cancellation propagates all the way 
            // to the AsyncChannel, cleaning up its internal waitSenders/waitReceivers queues!
            return 
            Promises.any(stages)
                    .dependent()
                    .thenCompose(res -> 
                        res.error != null ? Promises.failure(res.error)
                                          : Promises.success(selectResult(res.index, res.match, res.value)), true)
                    .unwrap();
        }  
    }
    
    @SafeVarargs
    static <T> Promise<SelectResult<T>> select(SelectCase<T>... cases) {
        if (cases == null || cases.length == 0) {
            throw new IllegalArgumentException("At least one case required");
        }

        Set<Channel<?>> seen = new HashSet<>();
        for (SelectCase<T> c : cases) {
            Object ch;
            if (c instanceof SelectCase.Receive) {
                ch = ((SelectCase.Receive<?>)c).channel();
            } else if (c instanceof SelectCase.Send) {
                ch = ((SelectCase.Send<?>)c).channel();
            } else {
                ch = null;
            }
            if (ch != null && !seen.add((Channel<?>)ch)) {
                throw new IllegalArgumentException("Duplicate channel in select: " + ch);
            }
        }
        
        SelectCall<T> call = new SelectCall<>(cases);
        return call.execute();
    }
    
    // Internal wrapper to unify types for Promises.any and capture exceptions
    static final class SelectResultHolder<T> {
        final int index;
        final SelectCase<T> match;
        final T value;
        final Throwable error;

        public SelectResultHolder(int index, SelectCase<T> match, T value, Throwable error) {
            this.index = index;
            this.match = match;
            this.value = value;
            this.error = error;
        }
    }
 
    private static <T> Try<SelectResult<T>> success(int idx, SelectCase<T> selectCase, T value) {
        return Try.success(selectResult(idx, selectCase, value));
    }
    
    private static <T> SelectResult<T> selectResult(int idx, SelectCase<T> selectCase, T value) {
        return new SelectResult<>(idx, selectCase, value);
    }
}