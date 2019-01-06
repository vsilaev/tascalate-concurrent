/**
 * ﻿Copyright 2015-2018 Valery Silaev (http://vsilaev.com)
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

import java.util.function.Supplier;

final class Reference<T> {
    private final T reference;
    
    Reference(T reference) {
        this.reference = reference;
    }
    
    T get() {
        return reference;
    }
    
    static <T> T getOrElse(Reference<? extends T> ref, Supplier<? extends Throwable> error) {
        if (null != ref) {
            return ref.get();
        } else {
            return SharedFunctions.sneakyThrow(error.get());
        }
    }
}