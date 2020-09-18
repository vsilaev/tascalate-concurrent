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
package net.tascalate.concurrent.core;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class CancelMethodsCache {
    
    @FunctionalInterface
    public static interface Cancellation { 
        boolean apply(CompletionStage<?> p, boolean b);
    }
    
    private CancelMethodsCache() {}
    
    public static Cancellation cancellationOf(Class<?> stageClass) {
        return CANCEL_METHODS.get(stageClass, LOOKUP_CANCEL_METHOD);
    }
    
    private static final Cache<Class<?>, Cancellation> CANCEL_METHODS = new Cache<>();
    private static final Cancellation NO_CANCELATION = (p, b) -> false;
    private static final Function<Class<?>, Cancellation> LOOKUP_CANCEL_METHOD = c -> {
        Stream<Function<Class<?>, ExceptionalCancellation>> options = Stream.of(
            CancelMethodsCache::cancelInterruptibleMethodOf,     
            CancelMethodsCache::cancelMethodOf,
            CancelMethodsCache::completeExceptionallyMethodOf
        );
        return options.map(option -> Optional.ofNullable( option.apply(c) ))
                      .filter(Optional::isPresent)
                      .map(Optional::get)
                      .map(ExceptionalCancellation::unchecked)
                      .findFirst()
                      .orElse(NO_CANCELATION);
    };
    
    private static ExceptionalCancellation cancelInterruptibleMethodOf(Class<?> clazz) {
        try {
            Method m = firstUnreflectableMethod( clazz.getMethod("cancel", boolean.class) );
            if (null == m) {
                return null;
            }
            MethodHandle mh = unreflect(m).asType(MethodType.methodType(boolean.class, CompletionStage.class, boolean.class));            
            return (p, b) -> (boolean)mh.invokeExact(p, b);
            //return (p, b) -> (Boolean)m.invoke(p, b);
        } catch (ReflectiveOperationException | SecurityException ex) {
            return null;
        }
    }
    
    private static ExceptionalCancellation cancelMethodOf(Class<?> clazz) {
        try {
            Method m = firstUnreflectableMethod( clazz.getMethod("cancel") );
            if (null == m) {
                return null;
            }
            MethodHandle mh = unreflect(m).asType(MethodType.methodType(boolean.class, CompletionStage.class));
            return (p, b) -> (boolean)mh.invokeExact(p);
            //return (p, b) -> (Boolean)m.invoke(p);
        } catch (ReflectiveOperationException | SecurityException ex) {
            return null;
        }
    }
    
    private static ExceptionalCancellation completeExceptionallyMethodOf(Class<?> clazz) {
        try {
            Method m = firstUnreflectableMethod( clazz.getMethod("completeExceptionally", Throwable.class) );
            if (null == m) {
                return null;
            }
            MethodHandle mh = unreflect(m).asType(MethodType.methodType(boolean.class, CompletionStage.class, CancellationException.class));
            return (p, b) -> (boolean)mh.invokeExact(p, new CancellationException());
            //return (p, b) -> (Boolean)m.invoke(p, new CancellationException());
        } catch (ReflectiveOperationException | SecurityException ex) {
            return null;
        }
    }
    
    private static Method firstUnreflectableMethod(Method m) {
        return firstUnreflectableMethod(m.getDeclaringClass(), m, new HashSet<>());
    }
    
    private static Method firstUnreflectableMethod(Class<?> clazz, Method m, Set<Class<?>> visited) {
        if (visited.contains(clazz)) {
            return null;
        }
        visited.add(clazz);
        if ((clazz.getModifiers() & Modifier.PUBLIC) != 0) {
            try {
                Method parent = clazz.getDeclaredMethod(m.getName(), m.getParameterTypes());
                if ((parent.getModifiers() & Modifier.PUBLIC) != 0) {
                    return parent;
                } else {
                    return null;
                }
            } catch (NoSuchMethodException ex) {
                // Ok, will check parents
            }
        }
        return
        Stream.concat( Stream.of(clazz.getSuperclass()), Stream.of(clazz.getInterfaces()) )
              .filter(c -> c != null && !visited.contains(c))
              .map(superClazz -> firstUnreflectableMethod(superClazz, m, visited))
              .filter(Objects::nonNull)
              .findFirst()
              .orElse(null)
        ;
    }
    
    private static MethodHandle unreflect(Method m) throws IllegalAccessException {
        return MethodHandles.publicLookup().unreflect(m);
    }
    
    @FunctionalInterface
    static interface ExceptionalCancellation {
        boolean apply(CompletionStage<?> p, boolean b) throws Throwable;
        default Cancellation unchecked() {
            return (a, b) -> { 
                try {
                    return this.apply(a, b);
                } catch (Error | RuntimeException ex) {
                    throw ex;
                } catch (Throwable ex) {
                    throw new RuntimeException(ex);
                }
            };            
        }
    }
}
