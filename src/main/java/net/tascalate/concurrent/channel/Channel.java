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

public interface Channel<T> extends SelectableSendChannel<T>, SelectableReceiveChannel<T> {
    public static <T> Channel<T> nil() {
        return NilChannel.instance();
    }
    
    public static <T> Channel<T> nil(Class<T> elementType) {
        return NilChannel.instance();
    }
    
    public static <T> Channel<T> rendezvous() {
        return rendezvous(false);
    }
    
    public static <T> Channel<T> rendezvous(Class<T> elementType) {
        return rendezvous(false);
    }

    
    public static <T> Channel<T> rendezvous(boolean fair) {
        return new BufferedChannel<>(0, fair);
    }
    
    public static <T> Channel<T> rendezvous(Class<T> elementType, boolean fair) {
        return new BufferedChannel<>(0, fair);
    }
    
    public static <T> Channel<T> buffered(int capacity) {
        return buffered(capacity, false);
    }
    
    public static <T> Channel<T> buffered(Class<T> elementType, int capacity) {
        return buffered(capacity, false);
    }
    
    public static <T> Channel<T> buffered(int capacity, boolean fair) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be > 0 for buffered channel");
        }
        return new BufferedChannel<>(capacity, fair);
    }
    
    public static <T> Channel<T> buffered(Class<T> elementType, int capacity, boolean fair) {
        return buffered(capacity, fair);
    }
    
    @SafeVarargs
    public static <T> Promise<SelectResult<T>> select(SelectCase<T>... cases) {
        return Select.select(cases);
    }
}
