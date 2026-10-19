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
package net.tascalate.concurrent.var;

import java.util.List;
import java.util.function.Function;

/**
 * An abstract base class for context variables that can be directly modified
 * within the current execution thread or scope.
 * <p>
 * While {@link ContextVar} provides read-only access via {@link ContextVar#get()},
 * {@code ModifiableContextVar} adds the ability to {@link #set(Object)} a new value
 * or {@link #remove()} the current value entirely. This is typically used for variables
 * backed by mutable thread-local storage (like {@link ThreadLocal}) or custom mutable
 * context holders.
 * </p>
 *
 * @param <T> the type of the value held by this context variable
 * @see CustomContextVar
 * @see ThreadLocalVar
 */
abstract class ModifiableContextVar<T> implements ContextVar<T> {
    
    /**
     * Sets the value of this context variable for the current scope.
     *
     * @param value the new value to set, or {@code null} if the variable should be cleared
     */
    abstract void set(T value);
    
    /**
     * Removes the value of this context variable for the current scope,
     * effectively resetting it to its default or unset state.
     * <p>
     * The default implementation simply calls {@link #set(Object)} with {@code null}.
     * Subclasses may override this to provide a more specific clearing mechanism
     * (e.g., {@code ThreadLocal.remove()}).
     * </p>
     */
    void remove() {
        set(null);
    }

    /**
     * Returns the factory used to group multiple modifiable context variables together.
     * Defaults to the strict factory of {@link ModifiableContextVarGroup}.
     *
     * @return a function that creates a grouped context var from a list of variables
     */
    @Override
    public Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> groupFactory() {
        return ModifiableContextVarGroup.FACTORY_STRICT;
    }
}