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

import java.util.Objects;

/**
 * A single case in a {@code select} statement.
 * <p>
 * On Java 17+ (via Multi-Release JAR) this is replaced by a
 * {@code sealed} interface hierarchy, enabling exhaustive
 * pattern-matching {@code switch} for downstream callers.
 */
public final class Select {
    
    private Select() {
        
    }
    
    public static interface Op<T> {}

    public static final class Send<T> implements Op<T> {
        private final SelectableSendChannel<T> channel;
        private final T value;

        public Send(SelectableSendChannel<T> channel, T value) {
            this.channel = channel;
            this.value   = value;
        }

        public SelectableSendChannel<T> channel() {
            return channel;
        }

        public T value() {
            return value;
        }

        @Override 
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Send)) {
                return false;
            }
            Send<?> s = (Send<?>) o;
            return Objects.equals(channel, s.channel) &&
                   Objects.equals(value,   s.value);
        }

        @Override 
        public int hashCode() {
            return Objects.hash(channel, value);
        }

        @Override 
        public String toString() {
            return "Send[channel=" + channel + ", value=" + value + "]";
        }
    }

    /**
     * Implements {@code SelectCase<Void>} (not {@code SelectCase<T>})
     * so that mixing receive with typed sends forces the select
     * result type to {@code Object}.
     */
    public static final class Receive<T> implements Op<T> {
        private final SelectableReceiveChannel<T> channel;

        public Receive(SelectableReceiveChannel<T> channel) {
            this.channel = channel;
        }

        public SelectableReceiveChannel<T> channel() {
            return channel;
        }

        @Override 
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            
            if (!(o instanceof Receive)) {
                return false;
            }
            return Objects.equals(channel, ((Receive<?>) o).channel);
        }

        @Override 
        public int hashCode() {
            return Objects.hash(channel);
        }

        @Override 
        public String toString() {
            return "Receive[channel=" + channel + "]";
        }
    }

    public static final class Default<T> implements Op<T> {

        public Default() {}

        @Override 
        public boolean equals(Object o) {
            return o instanceof Default;
        }

        @Override 
        public int hashCode() {
            return Default.class.hashCode();
        }

        @Override 
        public String toString() {
            return "Default[]";
        }
    }
    
    /**
     * A permanently inactive case. Never matches in Phase 1,
     * never registers in Phase 2. Used to dynamically remove a case
     * from a select loop (Go nil-channel idiom).
     */
    public static final class Disabled<T> implements Op<T> {

        public Disabled() {}

        @Override 
        public boolean equals(Object o) {
            return o instanceof Disabled;
        }

        @Override 
        public int hashCode() {
            return Disabled.class.hashCode();
        }

        @Override 
        public String toString() {
            return "Disabled[]";
        }
    }
    
    public static final class Result<T> {
        private final int index;
        private final Select.Op<T> match;
        private final T value;

        public Result(int index, Select.Op<T> match, T value) {
            this.index = index;
            this.match = match;
            this.value = value;
        }

        public int index() { 
            return index; 
        }
        
        public T value() { 
            return value; 
        }
        
        public Select.Op<T> match() { 
            return match; 
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            
            if (!(o instanceof Result)) {
                return false;
            }
            
            Result<?> result = (Result<?>) o;
            return index == result.index &&
                   Objects.equals(match, result.match) &&
                   Objects.equals(value, result.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(index, value, match);
        }

        @Override
        public String toString() {
            return "SelectResult{" +
                   "index=" + index + ", " +
                   "match=" + match + ", " +
                   "value=" + value +
                   '}';
        }
    }
    
    // Factory helpers

    public static <T, S extends T> Send<T> send(SelectableSendChannel<T> ch, S value) {
        return new Send<T>(ch, value);
    }

    public static <T> Receive<T> receive(SelectableReceiveChannel<T> ch) {
        return new Receive<>(ch);
    }

    public static <T> Default<T> otherwise() {
        return new Default<>();
    }
    
    public static <T> Disabled<T> disabled() {
        return new Disabled<>();
    }
}