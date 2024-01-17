package net.tascalate.concurrent.core;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

class FunctionMemoization<K, V> implements Function<K, V> {
    private final ConcurrentMap<K, Object> producerMutexes = new ConcurrentHashMap<>();
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

        Object mutex = getOrCreateMutex(key);
        synchronized (mutex) {
            try {
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
            } finally {
                producerMutexes.remove(key, mutex);
            }
        }

        return value;
    }
    
    public V forget(K key) {
        Object mutex = getOrCreateMutex(key);
        synchronized (mutex) {
            try {
                Object valueRef = valueMap.remove(keyRefType.createLookupKey(key));
                return valueRef == null ? null : valueRefType.dereference(valueRef);
            } finally {
                producerMutexes.remove(key, mutex);
            }
        }       
    }

    private Object getOrCreateMutex(K key) {
        Object createdMutex = new byte[0];
        Object existingMutex = producerMutexes.putIfAbsent(key, createdMutex);
        if (existingMutex != null) {
            return existingMutex;
        } else {
            return createdMutex;
        }
    }
    
    private void expungeStaleEntries() {
        for (Reference<? extends K> ref; (ref = queue.poll()) != null;) {
            @SuppressWarnings("unchecked")
            Reference<K> keyRef = (Reference<K>) ref;
            // keyRef now is equal only to itself while referent is cleared already
            // so it's safe to remove it without ceremony (like getOrCreateMutex(keyRef) usage)
            valueMap.remove(keyRef);
        }
    }
}