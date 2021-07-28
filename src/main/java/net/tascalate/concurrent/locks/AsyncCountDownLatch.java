/**
 * Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.concurrent.locks;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.LongBinaryOperator;

import net.tascalate.concurrent.CompletableFutureWrapper;

public class AsyncCountDownLatch extends CompletableFutureWrapper<Long> {
    private static final AtomicLongFieldUpdater<AsyncCountDownLatch> COUNT_UPDATER =
        AtomicLongFieldUpdater.newUpdater(AsyncCountDownLatch.class, "count");
    private static final LongBinaryOperator DECREMENT = 
        (prev, change) -> Math.max(0, prev - change);
    
    private final long initial;
    
    @SuppressWarnings("unused")
    private volatile long count;
    
    public AsyncCountDownLatch(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count < 0 : " + count);
        }
        this.count = this.initial = count;
        if (count == 0) {
            success(count);
        }
    }

    public long countDown() {
        return countDown(1);
    }
    
    public long countDown(long delta) {
        if (delta <= 0) {
            throw new IllegalArgumentException("delta <= 0 : " + delta);
        }
        long current = COUNT_UPDATER.getAndAccumulate(this, delta, DECREMENT);
        if (current == 0) {
          success(initial);
        }
        return current;
    }
}
