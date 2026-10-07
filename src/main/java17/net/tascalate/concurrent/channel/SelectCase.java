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

/**
 * A single case in a {@code select} statement.
 * <p>
 * Java 17+ version using {@code sealed interface} and {@code record}s,
 * enabling exhaustive pattern-matching {@code switch} for downstream callers.
 */
public sealed interface SelectCase<T> permits SelectCase.Send, SelectCase.Receive, SelectCase.Default, SelectCase.Disabled {

    record Send<T>(SelectableSendChannel<T> channel, T value) implements SelectCase<T> {}

    record Receive<T>(SelectableReceiveChannel<T> channel) implements SelectCase<T> {}

    record Default<T>() implements SelectCase<T> {}
    
    record Disabled<T>() implements SelectCase<T> {}
    
    // Factory helpers

    static <T, S extends T> Send<T> send(SelectableSendChannel<T> ch, S value) {
        return new Send<>(ch, value);
    }

    static <T> Receive<T> receive(SelectableReceiveChannel<T> ch) {
        return new Receive<>(ch);
    }

    static <T> Default<T> defaultCase() {
        return new Default<>();
    }
    
    static <T> Disabled<T> disabled() {
        return new Disabled<>();
    }
}