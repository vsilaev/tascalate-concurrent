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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class MultitargetException extends Exception {
    private final static long serialVersionUID = 1L;

    private final List<Throwable> exceptions;

    public MultitargetException(List<Throwable> exceptions) {
        this.exceptions = exceptions == null ? 
            Collections.emptyList() 
            : 
            Collections.unmodifiableList(exceptions);
    }

    public List<Throwable> getExceptions() {
        return exceptions;
    }
    
    Optional<Throwable> getFirstException() {
        return exceptions.stream().filter(ex -> ex != null).findFirst();
    }

    public static MultitargetException of(final Throwable exception) {
        return new MultitargetException(Collections.singletonList(exception));
    }
    
    @Override
    public void printStackTrace(PrintStream s) {
        synchronized (s) {
            super.printStackTrace(s);
            printExceptions(s, (ex, padding) -> {
                PrintStream ps = new PrintStream(new PaddedOutputStream(s, padding)); 
                ex.printStackTrace(ps);       
            });
        }
    }
    
    @Override
    public void printStackTrace(PrintWriter w) {
        synchronized (w) {
            super.printStackTrace(w);
            printExceptions(w, (ex, padding) -> {
                PrintWriter pw = new PrintWriter(new PaddedWriter(w, padding), true); 
                ex.printStackTrace(pw);          
            });
        }
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
    
    private static final String NEW_LINE = System.lineSeparator();
}
