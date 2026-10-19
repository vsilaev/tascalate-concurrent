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
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * The base-JDK implementation of the portable context variable returned by
 * {@link ContextVar#newInstance()}. On JDK 1.8&ndash;24 it is backed by a
 * {@link ThreadLocal}.
 *
 * <h2>Internal implementation type</h2>
 * <p>
 * This class is deliberately <b>package-private</b>. Application code obtains
 * portable variables through {@link ContextVar#newInstance()} and programs against
 * {@link ContextVar}, so it never depends on a JDK-specific implementation type.
 * </p>
 *
 * <h2>Multi-release contract</h2>
 * <p>
 * On JDK 25+ this class is replaced at runtime by the version under
 * {@code META-INF/versions/25/}, which is backed by {@code ScopedValue}.
 * Both versions MUST preserve:
 * </p>
 * <ul>
 *   <li>the same class access flags (package-private);</li>
 *   <li>the same superclass, {@link ModifiableContextVar};</li>
 *   <li>the same public/protected signatures &mdash; in particular the covariant
 *       {@link #strict()} and {@link #optimized()} return types. Removing those
 *       declarations in the versioned class causes {@link NoSuchMethodError} in
 *       code compiled against this base version.</li>
 * </ul>
 * <p>
 * This class must <b>not</b> reference Java-25-only types ({@code ScopedValueVar},
 * {@code ScopedValueVarGroup}, {@code ThreadVarGroup}), since it is loaded and
 * linked on pre-25 JDKs.
 * </p>
 *
 * <h2>Propagation modes</h2>
 * <ul>
 *   <li>The default instance is <b>strict</b>: {@link #callWith} saves the ambient
 *       value, applies the captured value, and restores the ambient value in a
 *       {@code finally} block (removing the entry when there was no previous value).</li>
 *   <li>{@link #optimized()} returns an {@link Optimized} view that applies the
 *       captured value and then <em>removes</em> it, without saving anything.</li>
 * </ul>
 * <p>
 * Both views share the <b>same</b> underlying {@link ThreadLocal}; switching modes
 * neither copies nor resets any values.
 * </p>
 *
 * <h2>Grouping</h2>
 * <p>
 * This implementation inherits {@link ModifiableContextVar#groupFactory()}, i.e.
 * {@link ModifiableContextVarGroup#FACTORY_STRICT}, so it can be batched together
 * with other genuinely mutable variables (such as {@link CustomContextVar}).
 * The JDK 25 replacement overrides {@code groupFactory()} to route portable vars
 * into a scoped-value carrier instead. That override is what keeps the immutable
 * JDK 25 implementation out of the mutating group machinery.
 * </p>
 *
 * @param <T> the type of the value held by this context variable
 * @see ContextVar#newInstance()
 * @see ContextVar#newInstance(String)
 */
class ThreadVar<T> extends ModifiableContextVar<T> {

    /** Diagnostic name of this variable; not an identity (see {@link ContextVar#newInstance(String)}). */
    final String name;

    /** The backing storage, shared by the strict and optimized views of this variable. */
    final ThreadLocal<T> threadLocal;

    /**
     * Creates a variable backed by the given thread local.
     *
     * @param name        the diagnostic name
     * @param threadLocal the backing storage; ownership is transferred to this variable
     *                    for the purposes of apply/restore/remove
     */
    ThreadVar(String name, ThreadLocal<T> threadLocal) {
        this.name = name;
        this.threadLocal = threadLocal;
    }

    /**
     * {@inheritDoc}
     * <p>Returns {@code null} when no value is bound in the current thread.</p>
     */
    @Override
    public T get() {
        return threadLocal.get();
    }

    /**
     * Writes a value directly into the backing thread local.
     * <p>Package-private: reachable only from the grouping machinery, never from
     * application code. Prefer {@link ContextVar#bind(Object)}.</p>
     *
     * @param value the value to write; {@code null} is represented by removing the entry
     */
    @Override
    void set(T value) {
        threadLocal.set(value);
    }

    /**
     * Removes this variable's entry from the current thread's storage, releasing
     * the reference held by the thread local.
     */
    @Override
    void remove() {
        threadLocal.remove();
    }

    /**
     * Executes the callable <b>strictly</b>: saves the ambient value, applies the
     * captured value, and restores the ambient value in a {@code finally} block.
     * <p>
     * When there was no previous value (or it was {@code null}), the entry is removed
     * rather than left bound to {@code null}. Exceptions and errors propagate unchanged;
     * restoration happens on every exit path.
     * </p>
     * <p>
     * This is the single-variable execution path used by {@link ContextVar.Binding}
     * and by a {@link ContextRelay} wrapping one variable. Multiple variables are
     * applied in batch through {@link ModifiableContextVarGroup} instead.
     * </p>
     *
     * @param capturedValue the value to apply for the duration of the call
     * @param code          the code to execute within the applied context
     * @param <V>           the result type
     * @return the result of the callable
     * @throws Exception if the callable throws
     */
    @Override
    public <V> V callWith(T capturedValue, Callable<V> code) throws Exception {
        T prevValue = threadLocal.get();
        threadLocal.set(capturedValue);
        try {
            return code.call();
        } finally {
            if (null == prevValue) {
                threadLocal.remove();
            } else {
                threadLocal.set(prevValue);
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>This implementation is already strict, so it returns {@code this}.</p>
     */
    @Override
    public ThreadVar<T> strict() {
        return this;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns an {@link Optimized} view sharing the same backing {@link ThreadLocal}.
     * No values are copied or reset by this conversion.
     * </p>
     */
    @Override
    public ThreadVar<T> optimized() {
        return new Optimized<>(name, threadLocal);
    }

    /**
     * Declared explicitly (rather than inherited) to document the mutable-group
     * contract. Must remain {@code FACTORY_STRICT} on the base JDK: the portable
     * variable is genuinely mutable here and can be batched with other
     * {@link ModifiableContextVar}s.
     *
     * @return the strict mutable-group factory
     */
    @Override
    public Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> groupFactory() {
        return ModifiableContextVarGroup.FACTORY_STRICT;
    }

    /**
     * Returns a diagnostic representation including the name, the backing thread
     * local and the <b>current value</b>.
     * <p><b>Caution:</b> the value may be sensitive; avoid logging this in production.</p>
     */
    @Override
    public String toString() {
        return String.format("<thread-var-strict>[%s/%s] = %s", name, threadLocal, get());
    }

    /**
     * The optimized (non-restoring) view of a {@link ThreadVar}.
     * <p>
     * Applies the captured value, executes the code, then removes the entry in a
     * {@code finally} block. Any ambient value that existed before the call is
     * <b>lost</b>; use this only on threads that carry no ambient context.
     * </p>
     * <p>
     * {@link #groupFactory()} preserves the optimized mode when this variable is
     * merged or re-grouped, so a batch containing optimized variables does not
     * silently degrade to strict restoration.
     * </p>
     *
     * @param <T> the type of the value
     */
    static final class Optimized<T> extends ThreadVar<T> {

        /**
         * Creates an optimized view over an existing backing storage.
         *
         * @param name     the diagnostic name
         * @param delegate the thread local shared with the strict view
         */
        Optimized(String name, ThreadLocal<T> delegate) {
            super(name, delegate);
        }

        /**
         * Applies the captured value, runs the callable, then removes the entry.
         * Does not save or restore any pre-existing ambient value.
         *
         * @param capturedValue the value to apply
         * @param code          the code to execute
         * @param <V>           the result type
         * @return the result of the callable
         * @throws Exception if the callable throws
         */
        @Override
        public <V> V callWith(T capturedValue, Callable<V> code) throws Exception {
            threadLocal.set(capturedValue);
            try {
                return code.call();
            } finally {
                threadLocal.remove();
            }
        }

        /** {@inheritDoc} Returns a strict view sharing the same backing storage. */
        @Override
        public ThreadVar<T> strict() {
            return new ThreadVar<T>(name, threadLocal);
        }

        /** {@inheritDoc} This view is already optimized. */
        @Override
        public ThreadVar<T> optimized() {
            return this;
        }

        /** {@inheritDoc} Preserves optimized semantics under grouping. */
        @Override
        public Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> groupFactory() {
            return ModifiableContextVarGroup.FACTORY_OPTIMIZED;
        }

        /** {@inheritDoc} Includes the current value; may contain sensitive data. */
        @Override
        public String toString() {
            return String.format("<thread-var-optimized>[%s/%s] = %s", name, threadLocal, get());
        }
    }

    /**
     * Creates a new portable variable with a generated diagnostic name.
     * <p>Package-private by design: the public entry point is {@link ContextVar#newInstance()}.</p>
     *
     * @param <T> the type of the value
     * @return a new, unbound strict variable
     */
    static <T> ThreadVar<T> newInstance() {
        return newInstance(ContextVarHelper.generateVarName());
    }

    /**
     * Creates a new portable variable with an explicit diagnostic name.
     * <p>Package-private by design: the public entry point is {@link ContextVar#newInstance(String)}.</p>
     *
     * @param <T>  the type of the value
     * @param name the diagnostic name
     * @return a new, unbound strict variable
     */
    static <T> ThreadVar<T> newInstance(String name) {
        return new ThreadVar<>(name, new ThreadLocal<>());
    }
}