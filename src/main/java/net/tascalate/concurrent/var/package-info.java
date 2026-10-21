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
/**
 * Provides a uniform API for <i>context variables</i> &mdash; values associated with the current
 * execution context (thread, scoped execution, request) &mdash; along with facilities to propagate
 * them across synchronous code blocks, {@link java.util.concurrent.Executor}s, and asynchronous
 * {@link net.tascalate.concurrent.Promise} chains.
 *
 * <h2>Context variables</h2>
 *
 * Every context variable implements the core interface
 * {@link net.tascalate.concurrent.var.ContextVar} and holds a value that is visible only within
 * its execution context. The following implementations are provided:
 *
 * <table>
 * <caption>Context variable implementations</caption>
 * <tr><th>Class</th><th>Backing storage</th><th>Notes</th></tr>
 * <tr>
 *   <td>{@link net.tascalate.concurrent.var.ThreadVar}</td>
 *   <td>Portable (multi-release)</td>
 *   <td>Backed by {@link java.lang.ThreadLocal} on JDK 1.8&ndash;24 and by
 *       {@code java.lang.ScopedValue} on JDK 25+. The recommended default choice.</td>
 * </tr>
 * <tr>
 *   <td>{@link net.tascalate.concurrent.var.ThreadLocalVar}</td>
 *   <td>{@link java.lang.ThreadLocal}</td>
 *   <td>Explicit thread-local storage on every JDK; can also wrap an existing
 *       {@code ThreadLocal} via {@code ThreadLocalVar.of(...)}.</td>
 * </tr>
 * <tr>
 *   <td>{@code net.tascalate.concurrent.var.ScopedValueVar}</td>
 *   <td>{@code java.lang.ScopedValue}</td>
 *   <td>Available only in the Java 25+ source set. Immutable scoped binding; inherently
 *       safe for virtual threads.</td>
 * </tr>
 * <tr>
 *   <td>{@link net.tascalate.concurrent.var.CustomContextVar}</td>
 *   <td>User-supplied accessors</td>
 *   <td>Integrates arbitrary external context stores (e.g., Spring
 *       {@code RequestContextHolder} / request attributes, SLF4J MDC, security contexts)
 *       via {@code Supplier} / {@code Consumer} / {@code Runnable} delegates.</td>
 * </tr>
 * </table>
 *
 * <p>{@code ThreadLocalVar}, {@code ThreadVar} (on JDK 1.8&ndash;24) and {@code CustomContextVar}
 * are <i>modifiable</i> ({@link net.tascalate.concurrent.var.ModifiableContextVar}); values are
 * written directly into the underlying storage. {@code ScopedValueVar} and {@code ThreadVar} on
 * JDK 25+ are immutable: a value can only be <i>bound</i> for the duration of an execution scope.
 *
 * <h2>Synchronous execution: the {@code bind} operator</h2>
 *
 * Values are assigned to context variables for the duration of a synchronous block via chains of
 * {@code bind} operators, terminated by {@code run(...)}, {@code supply(...)} or {@code call(...)}:
 *
 * <pre>{@code
 * ContextVar<String> currentUser = ThreadVar.newInstance("currentUser");
 * ContextVar<String> traceId     = ThreadVar.newInstance("traceId");
 *
 * ContextVar.bind(currentUser, "alice")
 *           .bind(traceId, "tx-123")
 *           .run(() -> {
 *               System.out.println(currentUser.get()); // "alice"
 *           });
 * // outside the block the previous values are restored (strict) or cleared (optimized)
 * }</pre>
 *
 * Variables bound together in one chain are applied as a single unit; see
 * <a href="#grouping">Grouping</a> below.
 *
 * <h2>Asynchronous execution: {@code ContextRelay}</h2>
 *
 * To transfer context across thread boundaries, capture the variables into a
 * {@link net.tascalate.concurrent.var.ContextRelay} and attach it to an executor or a promise
 * chain:
 *
 * <pre>{@code
 * ContextRelay<?> relay = ContextVar.relay(currentUser, traceId);
 * ExecutorService pooled = relay.contextual(Executors.newCachedThreadPool());
 *
 * pooled.submit(task); // the task runs with the context active at submit() time
 * }</pre>
 *
 * For {@link net.tascalate.concurrent.Promise} chains, use
 * {@link net.tascalate.concurrent.var.ContextRelay#contextualPromises()} or
 * {@link net.tascalate.concurrent.var.ContextRelay#dynamicContextualPromises()}:
 *
 * <pre>{@code
 * Promise<String> bound = relay
 *     .dynamicContextualPromises()
 *     .apply(originalPromise)
 *     .thenApply(v -> transform(v));
 * }</pre>
 *
 * <h2>Capture semantics: eager snapshot vs dynamic resolution</h2>
 *
 * The entry points differ in <b>when</b> the context snapshot is taken:
 *
 * <table>
 * <caption>Snapshot capture timing</caption>
 * <tr><th>Entry point</th><th>Snapshot taken</th></tr>
 * <tr>
 *   <td>{@code ContextVar.bind(...).run/supply/call}</td>
 *   <td>Values are fixed by the binding chain and applied for the duration of the block
 *       (synchronous execution).</td>
 * </tr>
 * <tr>
 *   <td>{@code ContextRelay.contextual(Runnable / Callable / Supplier / Function / ...)}</td>
 *   <td><b>Eagerly</b> &mdash; at the moment the {@code contextual(...)} method is invoked;
 *       every invocation of the returned action reuses that snapshot.</td>
 * </tr>
 * <tr>
 *   <td>{@code ContextRelay.contextual(Executor / ExecutorService / TaskExecutorService /
 *       ScheduledExecutorService)}</td>
 *   <td><b>Dynamically</b> &mdash; nothing is captured when the executor is wrapped; the snapshot
 *       is taken on each {@code execute}, {@code submit}, {@code invokeAll}, {@code invokeAny}
 *       and {@code schedule} call, so every task inherits the context of the submitting thread
 *       at submission time.</td>
 * </tr>
 * <tr>
 *   <td>{@code ContextRelay.contextualPromises()}</td>
 *   <td><b>Eagerly</b> &mdash; when the method is invoked; all callbacks of all decorated
 *       promises run within that one snapshot.</td>
 * </tr>
 * <tr>
 *   <td>{@code ContextRelay.dynamicContextualPromises()}</td>
 *   <td><b>Dynamically</b> &mdash; each time a callback is registered on a decorated promise
 *       (i.e., when each dependent stage is created), letting context flow through long
 *       asynchronous chains.</td>
 * </tr>
 * </table>
 *
 * <p>For periodic tasks submitted to a contextual {@link java.util.concurrent.ScheduledExecutorService},
 * the single snapshot captured at scheduling time is reused for every repetition of the task.
 *
 * <h2>Strict vs optimized propagation</h2>
 *
 * Each context variable (and relay) can be switched between two propagation modes:
 * <ul>
 *   <li><b>{@link net.tascalate.concurrent.var.ContextVar#strict() strict}</b> (default) &mdash;
 *       saves the ambient values of the executing thread, applies the snapshot, executes the code,
 *       then restores the saved values. Use when threads may carry pre-existing context that must
 *       not be lost.</li>
 *   <li><b>{@link net.tascalate.concurrent.var.ContextVar#optimized() optimized}</b> &mdash;
 *       applies the snapshot, executes the code, then clears the variables without saving anything.
 *       Faster, but destructive to ambient values.</li>
 * </ul>
 *
 * <p>Scoped-value backed variables ({@code ScopedValueVar}, and {@code ThreadVar} on JDK 25+) are
 * inherently strict: binding a scoped value shadows the outer binding and restores it automatically
 * when the scope completes, so {@code strict()} / {@code optimized()} are identity transformations
 * for them.
 *
 * <h2><a id="grouping">Grouping</a></h2>
 *
 * When several variables are relayed together ({@code ContextVar.relay(var1, var2, ...)}) or bound
 * in one chain, variables of the same kind are merged into a single group variable via each
 * variable's {@code groupFactory()}:
 * <ul>
 *   <li>{@code ThreadLocalVar}s &rarr; {@link net.tascalate.concurrent.var.ThreadLocalVarGroup}</li>
 *   <li>{@code ScopedValueVar}s &rarr; {@code net.tascalate.concurrent.var.ScopedValueVarGroup},
 *       which binds all values within a single {@code java.lang.ScopedValue.Carrier}</li>
 *   <li>other {@code ModifiableContextVar}s &rarr;
 *       {@link net.tascalate.concurrent.var.ModifiableContextVarGroup}</li>
 *   <li>arbitrary mixed variables &rarr; {@link net.tascalate.concurrent.var.ContextVarGroup}</li>
 * </ul>
 * Grouping applies, saves, restores and clears all member variables in one batch, minimizing the
 * overhead of context switching.
 *
 * <h2>Portability across JDKs</h2>
 *
 * This package is part of a multi-release JAR:
 * <ul>
 *   <li><b>JDK 1.8&ndash;24:</b> {@code ThreadVar} is backed by {@code ThreadLocal}.</li>
 *   <li><b>JDK 25+:</b> {@code ThreadVar} is backed by {@code ScopedValue} (mutation via
 *       {@code set}/{@code remove} is unsupported; values are bound per scope), and the
 *       {@code ScopedValueVar} / {@code ScopedValueVarGroup} classes become available.</li>
 * </ul>
 * Application code should depend on {@code ThreadVar} (or on {@code ContextVar} directly) to stay
 * portable; use {@code ThreadLocalVar} to pin thread-local semantics on every JDK, or
 * {@code ScopedValueVar} to opt explicitly into scoped values on JDK 25+.
 *
 * <h2>Integrating custom context stores</h2>
 *
 * External context holders are adapted with {@code CustomContextVar.define(...)}:
 *
 * <pre>{@code
 * ModifiableContextVar<RequestAttributes> requestVars = CustomContextVar.define(
 *     "requestAttributes",
 *     () -> RequestContextHolder.getRequestAttributes(),
 *     attrs -> RequestContextHolder.setRequestAttributes(attrs),
 *     () -> RequestContextHolder.resetRequestAttributes()
 * );
 * }</pre>
 *
 * Such variables participate in bindings, relays, grouping, and strict/optimized modes exactly
 * like built-in ones.
 *
 * <p>The remaining classes of the package ({@code Contextualization}, {@code VarAssignment},
 * {@code ContextVarHelper}, executor decorators, promise customizers) are internal machinery and
 * are not intended for direct use.
 *
 * @author Valery Silaev
 * @see net.tascalate.concurrent.var.ContextVar
 * @see net.tascalate.concurrent.var.ContextRelay
 * @see net.tascalate.concurrent.var.ThreadVar
 * @see net.tascalate.concurrent.var.ThreadLocalVar
 * @see net.tascalate.concurrent.var.CustomContextVar
 */
package net.tascalate.concurrent.var;