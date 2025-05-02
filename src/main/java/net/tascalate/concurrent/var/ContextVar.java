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
package net.tascalate.concurrent.var;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public interface ContextVar<T> {
    T get();
    
    <V> V callWith(T capturedValue, Callable<V> code) throws Exception;

    default void runWith(T capturedValue, Runnable code) {
        try {
            callWith(capturedValue, () -> {
                code.run();
                return null;
            });
        } catch (Error | RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    
    default <V> V supplyWith(T capturedValue, Supplier<V> code) {
        try {
            return callWith(capturedValue, code::get);
        } catch (Error | RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    
    default ContextTrampoline<T> relay() {
        return new ContextTrampoline<T>(this);
    }
    
    /**
     * Convert this var to the variant with pessimistic propagation option.
     * Useful for rare cases when thread might have its own default values 
     * of the context variables and they must be restored. 
     * <p>The logic is the following:</p>
     * <ol>
     * <li>Save context variables from the current thread</li>
     * <li>Apply context variables from the snapshot</li>
     * <li>Execute code</li>
     * <li>Restore context variables saved in the step [1]</li>
     * </ol>
     */
    default ContextVar<T> strict() {
        return this;
    }
    
    /**
     * Convert this var to the variant that is optimized for performance
     * <p>The logic is the following:</p>
     * <ol>
     * <li>Apply context variables from the snapshot</li>
     * <li>Execute code</li>
     * <li>Reset context variables</li>
     * </ol>
     */
    default ContextVar<T> optimized() {
        return this;
    }


    @SuppressWarnings("unchecked")
    public static <T> ContextVar<T> empty() {
        return (ContextVar<T>)ContextVarHelper.EMPTY;
    }
    
    @SafeVarargs
    public static <T> ContextVar<List<? extends T>> of(ContextVar<? extends T>... vars) {
        return of(Arrays.asList(vars));
    }
    
    public static <T> ContextVar<List<? extends T>> of(List<? extends ContextVar<? extends T>> vars) {
        if (null == vars || vars.isEmpty()) {
            return ContextVar.empty();
        } else {
            return new ContextVarGroup<>(vars);
        }
    }
    
    @SafeVarargs
    public static <T> ContextVar<List<? extends T>> ofMorphing(ContextVar<? extends T>... vars) {
        return ofMorphing(Arrays.asList(vars));
    }
    
    public static <T> ContextVar<List<? extends T>> ofMorphing(List<? extends ContextVar<? extends T>> vars) {
        if (null == vars || vars.isEmpty()) {
            return ContextVar.empty();
        } else {
            return new ContextVarGroup.Optimized<>(vars);
        }
    }
}