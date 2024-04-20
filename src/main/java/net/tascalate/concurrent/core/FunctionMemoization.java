/**
 * Copyright 2015-2024 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.concurrent.core;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

class FunctionMemoization<K, V> implements Function<K, V> {
    private final KeyedLocks<K> producerMutexes = new KeyedLocks<>();
    private final ConcurrentMap<Object, Object> valueMap = new ConcurrentHashMap<>();
    
    private final Function<? super K, ? extends V> fn;
    private final ReferenceType keyRefType;
    private final ReferenceType valueRefType;
    private final ReferenceQueue<K> queue;
    
    FunctionMemoization(Function<? super K, ? extends V> fn) {
        this(ReferenceType.WEAK, ReferenceType.SOFT, fn);
    }
    
    FunctionMemoization(ReferenceType keyRefType, ReferenceType valueRefType, Function<? super K, ? extends V> fn) {
        this.fn = fn;
        this.keyRefType = keyRefType;
        this.valueRefType = valueRefType;
        this.queue = keyRefType.createKeyReferenceQueue();
    }

    @Override
    public V apply(K key) {
        expungeStaleEntries();

        Object lookupKeyRef = keyRefType.createLookupKey(key);
        Object valueRef;

        // Try to get a cached value.
        valueRef = valueMap.get(lookupKeyRef);
        V value;
        
        if (valueRef != null) {
            value = valueRefType.dereference(valueRef);
            if (value != null) {
                // A cached value was found.
                return value;
            }
        }

        try (KeyedLocks.Lock lock = producerMutexes.acquire(key)) {
            // Double-check after getting mutex
            valueRef = valueMap.get(lookupKeyRef);
            value = valueRef == null ? null : valueRefType.dereference(valueRef);
            if (value == null) {
                value = fn.apply(key);
                valueMap.put(
                    keyRefType.createKeyReference(key, queue), 
                    valueRefType.createValueReference(value)
                );
            }
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        return value;
    }
    
    public V forget(K key) {
        try (KeyedLocks.Lock lock = producerMutexes.acquire(key)) {
            Object valueRef = valueMap.remove(keyRefType.createLookupKey(key));
            return valueRef == null ? null : valueRefType.dereference(valueRef);            
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private void expungeStaleEntries() {
        if (null == queue) {
            return;
        }
        for (Reference<? extends K> ref; (ref = queue.poll()) != null;) {
            @SuppressWarnings("unchecked")
            Reference<K> keyRef = (Reference<K>) ref;
            // keyRef now is equal only to itself while referent is cleared already
            // so it's safe to remove it without ceremony (like getOrCreateMutex(keyRef) usage)
            valueMap.remove(keyRef);
        }
    }
}