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

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MultitargetException extends Exception {
    private final static long serialVersionUID = 1L;

    private final List<Throwable> exceptions;

    public MultitargetException(List<Throwable> exceptions) {
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

    public List<Throwable> getExceptions() {
        return exceptions;
    }
    
    Optional<Throwable> getFirstException() {
        return exceptions.stream().filter(Objects::nonNull).findFirst();
    }

    public static MultitargetException of(final Throwable exception) {
        return new MultitargetException(Collections.singletonList(exception));
    }
    
    @Override
    public String toString() {
        return getClass().getName();
    }
    
    @Override
    public String getMessage() {
        StringWriter w = new StringWriter();
        printDetails(new PrintWriter(w), newDejavueSet());
        return w.toString();
    }
    
    void printDetails(PrintWriter w, Set<Throwable> visited) {
        visited.add(this);
        w.println(toStringSafe());
        printExceptions(w, (ex, padding) -> {
            PrintWriter pw = new PrintWriter(new PaddedWriter(w, padding), true); 
            if (visited.contains(ex)) {
                String message = ex instanceof MultitargetException ? 
                    ((MultitargetException)ex).toStringSafe() 
                    : 
                    ex.toString();
                pw.println("\t[CIRCULAR REFERENCE:" + message + "]");
            } else {
                if (ex instanceof MultitargetException) {
                    ((MultitargetException)ex).printDetails(pw, visited);
                } else {
                    pw.println(ex.toString());
                }
            }
        });        
        
    }
    
    public void printExceptions() {
        printExceptions(System.err);
    }
    
    public void printExceptions(PrintStream s) {
        synchronized (s) {
            printExceptions(s, newDejavueSet());
        }
    }
    
    void printExceptions(PrintStream s, Set<Throwable> visited) {
        visited.add(this);
        
        //super.printStackTrace(s);
        // Print our stack trace
        s.println(toStringSafe());
        for (StackTraceElement trace : getStackTrace())
            s.println("\tat " + trace);
        
        printExceptions(s, (ex, padding) -> {
            PrintStream ps = new PrintStream(new PaddedOutputStream(s, padding));
            if (visited.contains(ex)) {
                String message = ex instanceof MultitargetException ? 
                    ((MultitargetException)ex).toStringSafe() 
                    : 
                    ex.toString();
                ps.println("\t[CIRCULAR REFERENCE:" + message + "]");
            } else {
                if (ex instanceof MultitargetException) {
                    ((MultitargetException)ex).printExceptions(ps, visited);
                } else {
                    ex.printStackTrace(ps);
                }
            }
        });
    }
    
    public void printExceptions(PrintWriter w) {
        synchronized (w) {
            printExceptions(w, newDejavueSet());
        }
    }
    
    void printExceptions(PrintWriter w, Set<Throwable> visited) {
        visited.add(this);
        
        //super.printStackTrace(s);
        // Print our stack trace
        w.println(toStringSafe());
        for (StackTraceElement trace : getStackTrace())
            w.println("\tat " + trace);
        
        printExceptions(w, (ex, padding) -> {
            PrintWriter pw = new PrintWriter(new PaddedWriter(w, padding), true); 
            if (visited.contains(ex)) {
                String message = ex instanceof MultitargetException ? 
                    ((MultitargetException)ex).toStringSafe() 
                    : 
                    ex.toString();
                pw.println("\t[CIRCULAR REFERENCE:" + message + "]");
            } else {
                if (ex instanceof MultitargetException) {
                    ((MultitargetException)ex).printExceptions(pw, visited);
                } else {
                    ex.printStackTrace(pw);
                }
            }
        });
    }
    
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
    
    String toStringSafe() {
        return getClass().getName();
    }
    
    private static <T> Set<T> newDejavueSet() {
        return Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());        
    }
    
    private static final String NEW_LINE = System.lineSeparator();
}
