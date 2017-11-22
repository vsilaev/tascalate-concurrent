package net.tascalate.concurrent;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class RetryPolicy {
    public static final RetryPolicy DEFAULT = new RetryPolicy().retryOn(Exception.class);

    private final int maxRetries;
    private final Set<Class<? extends Throwable>> retryOn;
    private final Set<Class<? extends Throwable>> abortOn;
    private final Predicate<RetryContext> retryPredicate;
    private final Predicate<RetryContext> abortPredicate;
    private final Backoff backoff;

    @SafeVarargs
    public final RetryPolicy retryOn(Class<? extends Throwable>... retryOnThrowables) {
        return new RetryPolicy(maxRetries, setPlusElems(retryOn, retryOnThrowables), abortOn, retryPredicate, abortPredicate, backoff);
    }

    @SafeVarargs
    public final RetryPolicy abortOn(Class<? extends Throwable>... abortOnThrowables) {
        return new RetryPolicy(maxRetries, retryOn, setPlusElems(abortOn, abortOnThrowables), retryPredicate, abortPredicate, backoff);
    }

    public RetryPolicy abortIf(Predicate<RetryContext> abortPredicate) {
        return new RetryPolicy(maxRetries, retryOn, abortOn, retryPredicate, abortPredicate.or(abortPredicate), backoff);
    }

    public RetryPolicy retryIf(Predicate<RetryContext> retryPredicate) {
        return new RetryPolicy(maxRetries, retryOn, abortOn, this.retryPredicate.or(retryPredicate), abortPredicate, backoff);
    }

    public RetryPolicy dontRetry() {
        return new RetryPolicy(0, retryOn, abortOn, retryPredicate, abortPredicate, backoff);
    }

    public RetryPolicy withMaxRetries(int maxRetries) {
        return new RetryPolicy(maxRetries, retryOn, abortOn, retryPredicate, abortPredicate, backoff);
    }
    
    public RetryPolicy withBackoff(Backoff backoff) {
        return new RetryPolicy(maxRetries, retryOn, abortOn, retryPredicate, abortPredicate, backoff);
    }

    public RetryPolicy(int maxRetries, Set<Class<? extends Throwable>> retryOn, Set<Class<? extends Throwable>> abortOn, Predicate<RetryContext> retryPredicate, Predicate<RetryContext> abortPredicate, Backoff backoff) {
        this.maxRetries = maxRetries;
        this.retryOn = retryOn;
        this.abortOn = abortOn;
        this.retryPredicate = retryPredicate;
        this.abortPredicate = abortPredicate;
        this.backoff = backoff;
    }

    public RetryPolicy() {
        this(1000);
    }
    
    public RetryPolicy(long defaultDelay) {
        this(Integer.MAX_VALUE, 
             Collections.emptySet(), Collections.emptySet(), 
             ctx -> true, ctx -> false, 
             ctx -> ctx.getRetryCount() == 0 ? 0 : defaultDelay 
        );
    }

    public boolean shouldContinue(RetryContext context) {
        if (tooManyRetries(context)) {
            return false;
        }
        if (abortPredicate.test(context)) {
            return false;
        }
        if (retryPredicate.test(context)) {
            return true;
        }
        return exceptionClassRetryable(context);
    }
    
    public long delayInMillis(RetryContext context) {
        return backoff.delayMillis(context);
    }

    private boolean tooManyRetries(RetryContext context) {
        return context.getRetryCount() > maxRetries;
    }

    private boolean exceptionClassRetryable(RetryContext context) {
        if (context.getLastThrowable() == null) {
            return true;
        }
        final Class<? extends Throwable> e = context.getLastThrowable().getClass();
        return !matches(e, abortOn) && matches(e, retryOn); 
    }

    private static boolean matches(Class<? extends Throwable> throwable, Set<Class<? extends Throwable>> set) {
        return set.stream().anyMatch(c -> c.isAssignableFrom(throwable));
    }

    @SafeVarargs
    private static <T> Set<T> setPlusElems(Set<T> initial, T... newElement) {
        final HashSet<T> copy = new HashSet<>(initial);
        copy.addAll(Arrays.asList(newElement));
        return Collections.unmodifiableSet(copy);
    }
    
}
