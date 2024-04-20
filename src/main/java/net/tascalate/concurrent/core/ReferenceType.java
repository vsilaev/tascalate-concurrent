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
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Objects;

enum ReferenceType {
    HARD() {
        <K> ReferenceQueue<K> createKeyReferenceQueue() {
            return null;
        }
        
        <K> Object createLookupKey(K key) {
            return key;
        }
        
        <K> Object createKeyReference(K key, ReferenceQueue<? super K> queue) {
            return key;
        }
        
        <V> Object createValueReference(V value) {
            return value;
        }
        
        @SuppressWarnings("unchecked")
        <T> T dereference(Object reference) {
            return (T)reference;
        }
    },
    
    SOFT() {
        <K> Object createLookupKey(K key) {
            return new LookupKey<K>(key);
        }
        
        <K> Object createKeyReference(K key, ReferenceQueue<? super K> queue) {
            return new SoftKey<K>(key, queue);
        }
        
        <V> Object createValueReference(V value) {
            return new SoftReference<>(value);
        }
    },
    
    WEAK() {
        <K> Object createLookupKey(K key) {
            return new LookupKey<K>(key);
        }
        
        <K> Object createKeyReference(K key, ReferenceQueue<? super K> queue) {
            return new WeakKey<K>(key, queue);
        }
        
        <V> Object createValueReference(V value) {
            return new WeakReference<>(value);
        }
    };
    
    <K> ReferenceQueue<K> createKeyReferenceQueue() {
        return new ReferenceQueue<>(); 
    }
    
    abstract <K> Object createLookupKey(K key);
    abstract <K> Object createKeyReference(K key, ReferenceQueue<? super K> queue);
    abstract <V> Object createValueReference(V value);
    
    @SuppressWarnings("unchecked")
    <T> T dereference(Object reference) {
        return ((Reference<T>)reference).get();
    }
    
    
    static final class LookupKey<K> {

        private final K key;
        private final int hashCode;

        LookupKey(K key) {
            this.key = key;
            this.hashCode = System.identityHashCode(key);
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof LookupKey<?>) {
                return key.equals(((LookupKey<?>)other).key);
            } else {
                return key.equals(((Reference<?>)other).get());
            }
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
    
    static final class WeakKey<K> extends WeakReference<K> {

        private final int hashCode;

        WeakKey(K key, ReferenceQueue<? super K> queue) {
            super(key, queue);
            hashCode = System.identityHashCode(key);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof WeakKey<?>) {
                return Objects.equals(((WeakKey<?>) other).get(), get());
            } else {
                return other.equals(this);
            }
        }

        @Override
        public String toString() {
            return String.valueOf(get());
        }
    }
    
    static final class SoftKey<K> extends SoftReference<K> {

        private final int hashCode;

        SoftKey(K key, ReferenceQueue<? super K> queue) {
            super(key, queue);
            hashCode = System.identityHashCode(key);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof SoftKey<?>) {
                return Objects.equals(((SoftKey<?>) other).get(), get());
            } else {
                return other.equals(this);
            }
        }

        @Override
        public String toString() {
            return String.valueOf(get());
        }
    }
}