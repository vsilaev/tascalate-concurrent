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
public interface SelectCase<T> {

    /** Marker for cases that carry a typed payload (Send, Default). */
    public static interface Typed<T> extends SelectCase<T> {}

    public static final class Send<T> implements Typed<T> {
        private final AsyncSendChannel<? super T> channel;
        private final T value;

        public Send(AsyncSendChannel<? super T> channel, T value) {
            this.channel = channel;
            this.value   = value;
        }

        public AsyncSendChannel<? super T> channel() {
            return channel;
        }

        public T value() {
            return value;
        }

        @Override public boolean equals(Object o) {
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

        @Override public int hashCode() {
            return Objects.hash(channel, value);
        }

        @Override public String toString() {
            return "Send[channel=" + channel + ", value=" + value + "]";
        }
    }

    /**
     * Implements {@code SelectCase<Void>} (not {@code SelectCase<T>})
     * so that mixing receive with typed sends forces the select
     * result type to {@code Object}.
     */
    public static final class Receive<T> implements SelectCase<Void> {
        private final AsyncReceiveChannel<T> channel;

        public Receive(AsyncReceiveChannel<T> channel) {
            this.channel = channel;
        }

        public AsyncReceiveChannel<T> channel() {
            return channel;
        }

        @Override public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Receive)) {
                return false;
            }
            return Objects.equals(channel, ((Receive<?>) o).channel);
        }

        @Override public int hashCode() {
            return Objects.hash(channel);
        }

        @Override public String toString() {
            return "Receive[channel=" + channel + "]";
        }
    }

    public static final class Default<T> implements Typed<T> {

        public Default() {}

        @Override public boolean equals(Object o) {
            return o instanceof Default;
        }

        @Override public int hashCode() {
            return Default.class.hashCode();
        }

        @Override public String toString() {
            return "Default[]";
        }
    }
    
    /**
     * A permanently inactive case. Never matches in Phase 1,
     * never registers in Phase 2. Used to dynamically remove a case
     * from a select loop (Go nil-channel idiom).
     */
    public static final class Disabled<T> implements Typed<T> {

        public Disabled() {}

        @Override public boolean equals(Object o) {
            return o instanceof Disabled;
        }

        @Override public int hashCode() {
            return Disabled.class.hashCode();
        }

        @Override public String toString() {
            return "Disabled[]";
        }
    }
    
    // Factory helpers

    public static <T> Send<T> send(AsyncSendChannel<? super T> ch, T value) {
        return new Send<T>(ch, value);
    }

    public static <T> Receive<T> receive(AsyncReceiveChannel<T> ch) {
        return new Receive<>(ch);
    }

    public static <T> Default<T> defaultCase() {
        return new Default<>();
    }
    
    public static <T> Disabled<T> disabled() {
        return new Disabled<>();
    }
}