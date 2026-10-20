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
package net.tascalate.concurrent;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * An aggregate exception reported by the multi-target combinators of the
 * {@link Promises} class ({@code all}, {@code any}, {@code atLeast}, and their
 * strict variants) when the combined operation fails.
 * <p>
 * Unlike a plain exception, a {@code MultitargetException} carries the outcomes of
 * <em>all</em> contributing {@link java.util.concurrent.CompletionStage}s:
 * </p>
 * <ul>
 *   <li>{@link #getExceptions()} returns the per-target outcomes in the original
 *       order; positions of targets that completed successfully contain {@code null}.</li>
 *   <li>The standard {@link #getCause()} / {@link #getSuppressed()} wiring follows the
 *       number of actual failures: a single failure becomes the {@code cause}, while
 *       several failures are attached as {@code suppressed} exceptions.</li>
 * </ul>
 * <p>
 * Application code that needs the exact failure of a concrete target should inspect
 * {@link #getExceptions()}. For convenient, human-readable diagnostics of every
 * contained failure, use the {@code printExceptions(...)} family instead of the
 * regular {@link #printStackTrace()}.
 * </p>
 *
 * @see Promises
 * @see Promises#unwrapCompletionException(Throwable)
 */
public class MultitargetException extends Exception {

    private final static long serialVersionUID = 1L;

    /** Per-target outcomes in original order; {@code null} marks successful targets. */
    private final List<Throwable> exceptions;

    /**
     * Creates an aggregate exception with the given message and per-target outcomes.
     * <p>
     * The standard cause/suppressed wiring is derived from the non-{@code null} entries:
     * exactly one failure becomes the {@linkplain #getCause() cause}; several failures
     * are attached as {@linkplain #getSuppressed() suppressed} exceptions; no failures
     * leaves both empty.
     * </p>
     *
     * @param message    the detail message
     * @param exceptions the per-target outcomes; {@code null} entries denote targets that
     *                   completed successfully; a {@code null} list is treated as empty
     */
    public MultitargetException(String message, List<Throwable> exceptions) {
        super(message);
        this.exceptions = exceptions == null ? 
            Collections.emptyList() 
            : 
            Collections.unmodifiableList(exceptions);
        List<Throwable> causes = this.exceptions
                                     .stream()
                                     .filter(Objects::nonNull)
                                     .collect(Collectors.toList());
        // Need to report back internal details in some standard way
        // If there is a single error - then it's the cause
        // Otherwie no dedicated cause and a list of suppressed exceptions
        switch (causes.size()) {
            case 0: 
                break;
            case 1:
                initCause(causes.get(0));
                break;
            default:
                for (Throwable cause : causes) {
                    addSuppressed(cause);
                }
        }
    }

    /**
     * Returns the per-target outcomes in the original order.
     * <p>
     * Positions of targets that completed successfully contain {@code null}. The
     * returned list is immutable.
     * </p>
     *
     * @return the list of per-target exceptions (with {@code null} for successes)
     */
    public List<Throwable> getExceptions() {
        return exceptions;
    }

    /**
     * Returns the first non-{@code null} contained exception, if any.
     *
     * @return an {@link Optional} with the first failure, or empty if all targets succeeded
     */
    Optional<Throwable> getFirstException() {
        return exceptions.stream().filter(Objects::nonNull).findFirst();
    }

    /**
     * Prints this aggregate exception and every contained exception to
     * {@link System#err}, using a numbered, indented layout.
     *
     * @see #printExceptions(PrintStream)
     */
    public void printExceptions() {
        printExceptions(System.err);
    }

    /**
     * Prints this aggregate exception and every contained exception to the given
     * {@link PrintStream}.
     * <p>
     * Each contained exception is printed with a zero-padded {@code [index]} marker;
     * {@code null} entries (targets that completed successfully) are printed as
     * {@code <NO ERROR>}. Circular references between aggregate exceptions are
     * detected and reported as {@code [CIRCULAR REFERENCE:...]} instead of recursing
     * indefinitely.
     * </p>
     *
     * @param s the print stream to write to
     */
    public void printExceptions(PrintStream s) {
        synchronized (s) {
            printExceptions(s, newDejavueSet());
        }
    }

    /**
     * Internal entry point that threads the circular-reference tracking set through
     * recursive printing of nested {@code MultitargetException}s.
     *
     * @param s       the print stream to write to
     * @param visited the identity set of already-visited throwables
     */
    void printExceptions(PrintStream s, Set<Throwable> visited) {
        visited.add(this);
        //super.printStackTrace(s);
        // Print our stack trace
        s.println(this);
        for (StackTraceElement trace : getStackTrace())
            s.println("\tat " + trace);
        printExceptions(s, (ex, padding) -> {
            PrintStream ps = new PrintStream(new PaddedOutputStream(s, padding));
            if (visited.contains(ex)) {
                ps.println("\t[CIRCULAR REFERENCE:" + ex + "]");
            } else {
                if (ex instanceof MultitargetException) {
                    ((MultitargetException)ex).printExceptions(ps, visited);
                } else {
                    ex.printStackTrace(ps);
                }
            }
        });
    }

    /**
     * Prints this aggregate exception and every contained exception to the given
     * {@link PrintWriter}.
     * <p>
     * Behaves identically to {@link #printExceptions(PrintStream)}, including the
     * {@code [index]} markers, {@code <NO ERROR>} placeholders, and circular-reference
     * detection.
     * </p>
     *
     * @param w the print writer to write to
     */
    public void printExceptions(PrintWriter w) {
        synchronized (w) {
            printExceptions(w, newDejavueSet());
        }
    }

    /**
     * Internal entry point that threads the circular-reference tracking set through
     * recursive printing of nested {@code MultitargetException}s.
     *
     * @param w       the print writer to write to
     * @param visited the identity set of already-visited throwables
     */
    void printExceptions(PrintWriter w, Set<Throwable> visited) {
        visited.add(this);
        //super.printStackTrace(s);
        // Print our stack trace
        w.println(this);
        for (StackTraceElement trace : getStackTrace())
            w.println("\tat " + trace);
        printExceptions(w, (ex, padding) -> {
            PrintWriter pw = new PrintWriter(new PaddedWriter(w, padding), true); 
            if (visited.contains(ex)) {
                pw.println("\t[CIRCULAR REFERENCE:" + ex + "]");
            } else {
                if (ex instanceof MultitargetException) {
                    ((MultitargetException)ex).printExceptions(pw, visited);
                } else {
                    ex.printStackTrace(pw);
                }
            }
        });
    }

    /**
     * Generic printing core: iterates over the contained exceptions, emitting a
     * zero-padded {@code [index]} marker for each and delegating the body to the
     * supplied {@code nestedExceptionPrinter}.
     *
     * @param <O>                    the appendable output type
     * @param out                    the output to write to
     * @param nestedExceptionPrinter a callback rendering one contained exception with
     *                               the given indentation padding
     */
    private <O extends Appendable> void printExceptions(O out, BiConsumer<Throwable, String> nestedExceptionPrinter) {
        int idx = 0;
        int n = ((int)Math.log10(exceptions.size()) + 1);
        String idxPadder = "%0" + n + "d";
        String padding =  String.format("\t %1$-" + n + "s  ... ", ""); 
        Consumer<Throwable> printer = ex -> nestedExceptionPrinter.accept(ex, padding);
        for (Throwable e : exceptions) {
            if (null == e) {
                idx++;
                continue;
            }
            try {
                printException(String.format(idxPadder, idx++), e, out, printer);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }        
    }

    /**
     * Prints a single contained exception with its index marker; a {@code null}
     * exception is rendered as {@code <NO ERROR>}.
     */
    private static <O extends Appendable> void printException(String idx, Throwable ex, O out, Consumer<Throwable> nestedExceptionPrinter) throws IOException {
        out.append("\t[");
        out.append(idx);
        out.append("] -> ");
        if (null == ex) {
            out.append("<NO ERROR>");
            out.append(NEW_LINE);
        } else {
            nestedExceptionPrinter.accept(ex);
        }
    }

    /**
     * Creates an identity-based set used to detect circular references while printing.
     *
     * @param <T> the element type
     * @return a new identity set
     */
    private static <T> Set<T> newDejavueSet() {
        return Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());        
    }

    /** Platform line separator used by the pretty-printer. */
    private static final String NEW_LINE = System.lineSeparator();
}