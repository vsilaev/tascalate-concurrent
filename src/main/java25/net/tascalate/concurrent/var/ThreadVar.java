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
import java.util.function.Supplier;

/**
 * The JDK 25+ implementation of the portable context variable returned by
 * {@link ContextVar#newInstance()}, backed by {@link ScopedValue}.
 *
 * <h2>Multi-release replacement</h2>
 * <p>
 * This class replaces the base {@code ThreadVar} at runtime on JDK 25+. It keeps
 * the same package-private access flags and the same superclass,
 * {@link ModifiableContextVar}, as required for multi-release compatibility, even
 * though its storage is immutable. The covariant {@link #strict()} and
 * {@link #optimized()} declarations MUST be kept: code compiled against the base
 * version resolves them by the descriptor {@code ()LThreadVar;}, and falling back
 * to the {@link ContextVar} defaults would cause {@link NoSuchMethodError}.
 * </p>
 *
 * <h2>Immutable, scoped semantics</h2>
 * <p>
 * A {@link ScopedValue} cannot be mutated in place. Consequently {@link #set} and
 * {@link #remove} are unsupported. They are package-private and unreachable through
 * the public API: values are applied exclusively through
 * {@link #callWith(Object, Callable)} / {@link #runWith} / {@link #supplyWith}, which
 * use {@link ScopedValue#where(ScopedValue, Object)}. Grouping is routed through
 * {@link #groupFactory()} to {@code ThreadVarGroup}, which builds a single
 * {@link ScopedValue.Carrier} and never calls the mutating helpers.
 * </p>
 *
 * <h2>Behavioral differences from the base-JDK implementation</h2>
 * <ul>
 *   <li><b>Inherent strictness:</b> binding shadows any outer binding and restores it
 *       automatically when the scope completes, including exceptional exit.
 *       {@link #strict()} and {@link #optimized()} are therefore identity operations;
 *       there is no destructive mode and no save/restore bookkeeping overhead.</li>
 *   <li><b>Child-thread inheritance:</b> threads started while a value is bound observe
 *       that binding. Unlike {@link ThreadLocal}, scoped values propagate to child
 *       (including virtual) threads without explicit relaying. Use
 *       {@link ThreadLocalVar} when isolation from child threads is required.</li>
 *   <li><b>Virtual-thread friendliness:</b> no per-thread mutable map entry is created;
 *       bindings live on the execution scope.</li>
 * </ul>
 *
 * <h2>Null support</h2>
 * <p>
 * {@link ScopedValue} does not permit {@code null}. A private sentinel distinguishes
 * a <em>bound null</em> from an <em>unbound</em> variable, so {@link #get()} returns
 * {@code null} in both cases and binding {@code null} does not throw &mdash; matching
 * the observable contract of the {@link ThreadLocal}-backed base implementation.
 * </p>
 *
 * @param <T> the type of the value held by this context variable
 * @see ContextVar#newInstance()
 * @see ScopedValue
 */
class ThreadVar<T> extends ModifiableContextVar<T> {

    /** Diagnostic name of this variable; not an identity. */
    final String name;

    /**
     * The backing scoped value. Declared as {@code ScopedValue<Object>} so that the
     * null-sentinel can be stored in it; values are wrapped/unwrapped at the boundary.
     */
    final ScopedValue<Object> scopedValue;

    /**
     * Sentinel distinguishing a bound {@code null} from an unbound scoped value.
     * Package-private so that {@code ThreadVarGroup} can use the same representation
     * when reading a batch of values.
     */
    static final Object NULL_SENTINEL = new Object();

    /**
     * Creates a variable backed by the given scoped value.
     *
     * @param name        the diagnostic name
     * @param scopedValue the backing scoped value; must not already be bound by another owner
     */
    ThreadVar(String name, ScopedValue<Object> scopedValue) {
        this.name = name;
        this.scopedValue = scopedValue;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns {@code null} both when the value is unbound and when {@code null} was
     * explicitly bound. Uses {@link ScopedValue#orElse(Object)} rather than
     * {@link ScopedValue#get()} so that reading an unbound variable never throws.
     * </p>
     */
    @Override
    public T get() {
        Object result = scopedValue.orElse(NULL_SENTINEL);
        return unwrap(result);
    }

    /**
     * Unsupported: scoped values are immutable.
     *
     * @param value ignored
     * @throws UnsupportedOperationException always
     * @implNote Not reachable from the public API. Application code must use
     * {@link ContextVar#bind(Object)}; the grouping machinery routes this
     * implementation to a scoped-value carrier instead of calling this method.
     */
    @Override
    void set(T value) {
        throw new UnsupportedOperationException();
    }

    /**
     * Unsupported: scoped values are immutable and are unbound automatically when
     * their binding scope completes.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    void remove() {
        throw new UnsupportedOperationException();
    }

    /**
     * Runs the code with this scoped value bound to the given value for the dynamic
     * extent of the call. The previous binding (if any) is restored automatically on
     * every exit path.
     * <p>A {@code null} value is supported and is distinguished from an unbound state
     * internally by {@link #NULL_SENTINEL}.</p>
     *
     * @param newValue the value to bind
     * @param code     the code to execute within the binding
     */
    @Override
    public void runWith(T newValue, Runnable code) {
        ScopedValue.where(scopedValue, wrap(newValue)).run(code);
    }

    /**
     * Supplies a value with this scoped value bound for the dynamic extent of the call.
     *
     * @param newValue the value to bind; may be {@code null}
     * @param supplier the code to execute within the binding
     * @param <V>      the result type
     * @return the result of the supplier
     */
    @Override
    public <V> V supplyWith(T newValue, Supplier<V> supplier) {
        return ScopedValue.where(scopedValue, wrap(newValue)).call(supplier::get);
    }

    /**
     * Executes the callable with this scoped value bound for its dynamic extent.
     * <p>
     * This is the single-variable execution path used by {@link ContextVar.Binding}
     * and by a {@link ContextRelay} wrapping one variable. Multiple portable variables
     * are bound together in one {@link ScopedValue.Carrier} via {@code ThreadVarGroup}.
     * </p>
     * <p>Threads started inside the call observe the binding; see the class-level notes.</p>
     *
     * @param newValue the value to bind; may be {@code null}
     * @param callable the code to execute within the binding
     * @param <V>      the result type
     * @return the result of the callable
     * @throws Exception if the callable throws; the binding is still restored
     */
    @Override
    public <V> V callWith(T newValue, Callable<V> callable) throws Exception {
        return ScopedValue.where(scopedValue, wrap(newValue)).call(callable::call);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Identity: scoped binding is inherently strict, so there is nothing to wrap
     * and no ambient value can be overwritten.
     * </p>
     */
    @Override
    public ThreadVar<T> strict() {
        return this;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Identity: scoped binding is already optimal and automatically restoring.
     * Calling {@code optimized()} on JDK 25+ is a no-op, not a performance downgrade.
     * </p>
     */
    @Override
    public ThreadVar<T> optimized() {
        return this;
    }

    /**
     * {@inheritDoc}
     * @implNote
     * Returns the scoped-value group factory, which is a <b>different grouping key</b>
     * from the base JDK's {@link ModifiableContextVarGroup#FACTORY_STRICT}. This is
     * essential: routing this immutable implementation into a mutable group would
     * invoke {@link #set(Object)} and throw. All portable variables on JDK 25+ are
     * therefore batched into a single {@link ScopedValue.Carrier}.
     */
    @Override
    public Function<List<? extends ContextVar<?>>, ? extends ContextVar<List<?>>> groupFactory() {
        return ThreadVarGroup.FACTORY;
    }

    /**
     * Returns a diagnostic representation including the name, the scoped value and
     * the current value.
     * <p><b>Caution:</b> the value may be sensitive; avoid logging this in production.</p>
     */
    @Override
    public String toString() {
        return String.format("<thread-var>[%s/%s] = %s", name, scopedValue, get());
    }

    /**
     * Creates a new portable (scoped-value backed) variable with a generated name.
     * <p>Package-private: the public entry point is {@link ContextVar#newInstance()}.</p>
     *
     * @param <T> the type of the value
     * @return a new, unbound variable
     */
    static <T> ThreadVar<T> newInstance() {
        return newInstance(ContextVarHelper.generateVarName());
    }

    /**
     * Creates a new portable (scoped-value backed) variable with an explicit name.
     * <p>Package-private: the public entry point is {@link ContextVar#newInstance(String)}.</p>
     *
     * @param <T>  the type of the value
     * @param name the diagnostic name
     * @return a new, unbound variable
     */
    static <T> ThreadVar<T> newInstance(String name) {
        return new ThreadVar<>(name, ScopedValue.newInstance());
    }

    /**
     * Encodes a potentially {@code null} value into a form accepted by
     * {@link ScopedValue.Carrier#where(ScopedValue, Object)}.
     *
     * @param value the value to bind, possibly {@code null}
     * @return the value itself, or {@link #NULL_SENTINEL} for {@code null}
     */
    static Object wrap(Object value) {
        return value == null ? NULL_SENTINEL : value;
    }

    /**
     * Decodes a raw scoped-value payload back into the variable's type, translating
     * {@link #NULL_SENTINEL} to {@code null}.
     *
     * @param raw the raw payload, possibly the sentinel
     * @param <T> the target type
     * @return the decoded value, or {@code null}
     */
    @SuppressWarnings("unchecked")
    static <T> T unwrap(Object raw) {
        return raw == NULL_SENTINEL ? null : (T) raw;
    }
}