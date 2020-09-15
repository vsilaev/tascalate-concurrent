/**
 * Copyright 2015-2020 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.concurrent;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

class ComposedFutureRef<U> extends AtomicReference<CompletionStage<U>> {
    private static final long serialVersionUID = 1L;

    <T> Function<T, CompletionStage<U>> captureResult(Function<? super T, ? extends CompletionStage<U>> fn) {
        return v -> {
            CompletionStage<U> result = fn.apply(v);
            set(result);
            return result;
        };
    }    

    Runnable cancelCaptured = () -> {
        CompletionStage<U> stage = get();
        if (null != stage) {
            SharedFunctions.cancelPromise(stage, true);
        }
    };
}
