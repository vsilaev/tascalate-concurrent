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

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Try;

/**
 * A channel that is permanently blocked in both directions.
 * Mirrors Go's nil channel: sends and receives block forever.
 * <p>
 * Primary use: pass to {@link Select} as a dynamically disabled case,
 * or hold as a placeholder before a real channel is assigned.
 */
public final class NilChannel<T> implements Channel<T> {

    private static final NilChannel<?> INSTANCE = new NilChannel<>();

    private NilChannel() {}

    @SuppressWarnings("unchecked")
    public static <T> NilChannel<T> instance() {
        return (NilChannel<T>) INSTANCE;
    }

    @Override
    public Promise<T> send(T value) {
        return incomplete(); // never completes
    }

    @Override
    public Try<T> trySend(T value) {
        return null;
    }

    @Override
    public Promise<T> receive() {
        return incomplete(); // never completes
    }

    @Override
    public Try<T> tryReceive() {
        return null;
    }

    @Override 
    public void close(CloseMode mode) {
        
    }
    
    @Override 
    public boolean isClosed() { 
        return false; 
    }
    
    @Override 
    public CloseMode closedMode() { 
        return null; 
    }
    
    @Override 
    public int size() { 
        return 0; 
    }
    
    @Override 
    public int capacity() { 
        return 0; 
    }
    
    static <T> Promise<T> incomplete() {
        return new ChannelPromise<>();
    }
}