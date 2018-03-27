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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MultitargetException extends Exception {
    final private static long serialVersionUID = 1L;

    final private List<Throwable> exceptions;

    public MultitargetException(final List<Throwable> exceptions) {
        this.exceptions = exceptions;
    }

    public List<Throwable> getExceptions() {
        return Collections.unmodifiableList(exceptions);
    }
    
    Optional<Throwable> getFirstException() {
        return exceptions.stream().filter(ex -> ex != null).findFirst();
    }

    public static MultitargetException of(final Throwable exception) {
        return new MultitargetException(Collections.singletonList(exception));
    }
}
