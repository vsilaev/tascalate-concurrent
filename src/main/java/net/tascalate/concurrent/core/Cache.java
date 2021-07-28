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
package net.tascalate.concurrent.core;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;


final class Cache<K, V> {
    private final Map<Reference<K>, V> entries = new ConcurrentHashMap<>();
    private final ReferenceQueue<K> queue = new ReferenceQueue<K>();

    V get(K key, Function<? super K, ? extends V> valueFactory) {
        expungeStaleEntries();
        return entries.computeIfAbsent(new KeyReference<>(key), __ -> valueFactory.apply(key));
    }
    
    private void expungeStaleEntries() {
        Reference<? extends K> ref;
        while ( (ref = queue.poll()) != null ) {
            @SuppressWarnings("unchecked")
            Reference<K> keyRef = (Reference<K>) ref;
            // keyRef now is equal only to itself while referent is cleared already
            // however, it should be found in entries by reference
            entries.remove(keyRef);
        }
    }
    
    static final class KeyReference<K> extends WeakReference<K> {
        private final int referentHashCode;
        
        KeyReference(K key) {
            this(key, null);
        }

        KeyReference(K key, ReferenceQueue<K> queue) {
            super(key, queue);
            referentHashCode = key == null ? 0 : key.hashCode();
        }

        @Override
        public int hashCode() {
            return referentHashCode;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;
            if (!(other instanceof KeyReference))
                return false;
            Object r1 = this.get();
            Object r2 = ((KeyReference<?>) other).get();
            return null == r1 ? null == r2 : r1.equals(r2);
        }
    }
}
