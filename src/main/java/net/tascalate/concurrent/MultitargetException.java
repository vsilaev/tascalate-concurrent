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
