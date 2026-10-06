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

public sealed interface SelectCase<T> permits SelectCase.Typed, SelectCase.Receive {

    public sealed interface Typed<T> extends SelectCase<T>
           permits Send, Default, Disabled {}

    public record Send<T>(SelectableSendChannel<T> channel, T value)
           implements Typed<T> {}

    public record Receive<T>(SelectableReceiveChannel<T> channel)
           implements SelectCase<Void> {}

    public record Default<T>()
           implements Typed<T> {}

    public record Disabled<T>()
           implements Typed<T> {}

    // Factory helpers

    public static <T, S extends T> Send<T> send(SelectableSendChannel<T> ch, S value) {
        return new Send<T>(ch, value);
    }

    public static <T> Receive<T> receive(SelectableReceiveChannel<T> ch) {
        return new Receive<>(ch);
    }

    public static <T> Default<T> defaultCase() {
        return new Default<>();
    }
    
    public static <T> Disabled<T> disabled() {
        return new Disabled<>();
    }
}