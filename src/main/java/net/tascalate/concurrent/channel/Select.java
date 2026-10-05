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
import java.util.List;
import java.util.concurrent.CompletionStage;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Promises;
import net.tascalate.concurrent.Try;

final class Select {

    private Select() {}
    
    // Internal wrapper to unify types for Promises.any and capture exceptions
    static final class SelectResultHolder {
        final int index;
        final SelectCase<?> selectCase;
        final Object value;
        final Throwable error;

        public SelectResultHolder(int index, SelectCase<?> selectCase, Object value, Throwable error) {
            this.index = index;
            this.selectCase = selectCase;
            this.value = value;
            this.error = error;
        }
    }

    @SuppressWarnings("unchecked")
    static <T> Promise<SelectResult<T>> select(SelectCase.Typed<T>... cases) {
        Promise<SelectResult<Object>> future = select((SelectCase<T>[])cases);
        return (Promise<SelectResult<T>>)(Object)future;
    }
    
    static <T> Promise<SelectResult<Object>> select(@SuppressWarnings("unchecked") SelectCase<T>... cases) {
        if (cases == null || cases.length == 0) {
            return Promises.failure(new IllegalArgumentException("At least one case required"));
        }

        // Keep a mapping from shuffled position → original index
        int[] originalIndex = new int[cases.length];
        for (int i = 0; i < cases.length; i++) originalIndex[i] = i;

        // Fisher-Yates shuffle (Go picks randomly among ready cases)
        List<SelectCase<T>> shuffled = new ArrayList<>(Arrays.asList(cases));
        Collections.shuffle(shuffled);

        // Phase 1: non-blocking try
        int defaultOriginalIdx = -1;

        for (int i = 0; i < shuffled.size(); i++) {
            SelectCase<T> c = shuffled.get(i);

            if (c instanceof SelectCase.Default) {
                defaultOriginalIdx = originalIndex[i];
                continue;
            }
            
            if (c instanceof SelectCase.Disabled) {
                continue;
            }

            if (c instanceof SelectCase.Receive) {
                @SuppressWarnings("unchecked")
                SelectCase.Receive<T> trc = (SelectCase.Receive<T>)c;
                Try<T> r = trc.channel().tryReceive();
                
                if (r == null) {
                    continue; // not ready, try next case
                } else if (r.isFailure()) {
                    return Promises.failure(r.getCause());
                } else if (r.isSuccess()) {
                    return success(originalIndex[i], trc, r.get() /*isSend=false*/);
                } else {
                    throw new IllegalStateException();
                }
            } else if (c instanceof SelectCase.Send) {
                SelectCase.Send<T> tsc = (SelectCase.Send<T>)c; 
                if (tsc.channel().trySend(tsc.value())) {
                    return success(originalIndex[i], tsc, null /*isSend=true*/);
                }
            }
        }

        // Nothing was immediately ready → use default if present
        if (defaultOriginalIdx >= 0) {
            return success(defaultOriginalIdx, cases[defaultOriginalIdx], null /*isSend=false*/);
        }

        // Phase 2: async wait using tascalate Promises
        List<CompletionStage<SelectResultHolder>> stages = new ArrayList<>();

        for (int i = 0; i < shuffled.size(); i++) {
            SelectCase<T> c = shuffled.get(i);
            if (c instanceof SelectCase.Default) {
                continue;
            }
            
            if (c instanceof SelectCase.Disabled) {
                continue;
            }

            int origIdx = originalIndex[i];
            Promise<?> originalFuture;

            if (c instanceof SelectCase.Receive) {
                originalFuture = ((SelectCase.Receive<?>)c).channel().receive();
            } else if (c instanceof SelectCase.Send) {
                SelectCase.Send<T> tsc = (SelectCase.Send<T>)c; 
                originalFuture = tsc.channel().send(tsc.value());
            } else {
                continue;
            }

            CompletionStage<SelectResultHolder> stage = 
            originalFuture.dependent()
                          .handle((val, ex) -> new SelectResultHolder(origIdx, c, val, ex), true);
            // No need to unwrap above - not exposed to the clients

            stages.add(stage);
        }

        if (stages.isEmpty()) {
            // All cases are Disabled (no Default present, otherwise Phase 1
            // would have returned). Go semantics: select{} blocks forever.
            return NilChannel.incomplete();
        }

        // Promises.any races the stages. The first to complete wins.
        // It automatically calls .cancel() on the losing stages.
        // Thanks to dependent(true), this cancellation propagates all the way 
        // to the AsyncChannel, cleaning up its internal waitSenders/waitReceivers queues!
        return 
        Promises.any(stages)
                .dependent()
                .thenCompose(res -> 
                    res.error != null ? Promises.failure(res.error)
                                      : success(res.index, res.selectCase, res.value), true)
                .unwrap();
    }
    
    private static <T> Promise<SelectResult<T>> success(int idx, SelectCase<?> selectCase, T value) {
        return Promises.success(new SelectResult<>(idx, selectCase, value));
    }
}