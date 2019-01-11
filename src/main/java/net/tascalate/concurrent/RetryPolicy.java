/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * 
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/policy/RetryPolicy.java 
 * 
 * Modified work: copyright 2015-2018 Valery Silaev (http://vsilaev.com)
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

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class RetryPolicy<T> {
    
    public static interface Verdict {
        boolean shouldExecute();
        Duration backoffDelay();
        Duration timeout();
    }
    
    protected static final Verdict DONT_RETRY = new Verdict() {
        @Override
        public boolean shouldExecute() { return false; }

        @Override
        public Duration backoffDelay() { return Timeouts.NEGATIVE_DURATION; }
        
        @Override
        public Duration timeout() { return Timeouts.NEGATIVE_DURATION; }
    }; 
    
    protected static final class PositiveVerdict implements Verdict {
        private final Duration backoffDelay;
        private final Duration timeoutDelay;
        
        PositiveVerdict(Duration backoffDelay, Duration timeoutDelay) {
            this.backoffDelay = backoffDelay;
            this.timeoutDelay = timeoutDelay;
        }
        
        @Override
        public boolean shouldExecute() { return true; }
        
        @Override
        public Duration backoffDelay() { return backoffDelay; }

        @Override
        public Duration timeout() { return timeoutDelay; }
        
    }
    
    private static final Predicate<RetryContext<Object>> PREDICATE_FALSE = ctx -> false;
    
    public static final Predicate<Object> ACCEPT_NULL_RESULT = v -> true;
    public static final Predicate<Object> REJECT_NULL_RESULT = v -> v != null;
    
    public static final RetryPolicy<Object> DEFAULT = new RetryPolicy<>().retryOn(Exception.class);

    private final int maxRetries;
    private final Predicate<? super T> resultValidator;
    private final Set<Class<? extends Throwable>> retryOn;
    private final Set<Class<? extends Throwable>> abortOn;
    private final Predicate<RetryContext<? extends T>> retryPredicate;
    private final Predicate<RetryContext<? extends T>> abortPredicate;
    private final DelayPolicy<? super T> backoff;
    private final DelayPolicy<? super T> timeout;

    @SafeVarargs
    public final RetryPolicy<T> retryOn(Class<? extends Throwable>... retryOnThrowables) {
        return retryOn(Arrays.asList(retryOnThrowables));
    }
    
    public RetryPolicy<T> retryOn(Collection<Class<? extends Throwable>> retryOnThrowables) {
        return new RetryPolicy<T>(maxRetries, resultValidator,
                                  setPlusElems(retryOn, retryOnThrowables), abortOn, retryPredicate, abortPredicate, backoff, timeout);
    }

    @SafeVarargs
    public final RetryPolicy<T> abortOn(Class<? extends Throwable>... abortOnThrowables) {
        return abortOn(Arrays.asList(abortOnThrowables));
    }

    public RetryPolicy<T> abortOn(Collection<Class<? extends Throwable>> abortOnThrowables) {
        return new RetryPolicy<>(maxRetries, resultValidator, 
                                 retryOn, setPlusElems(abortOn, abortOnThrowables), retryPredicate, abortPredicate, backoff, timeout);
    }
    
    
    public RetryPolicy<T> abortIf(Predicate<RetryContext<? extends T>> abortPredicate) {
        return new RetryPolicy<>(maxRetries, resultValidator,
                                 retryOn, abortOn, retryPredicate, this.abortPredicate.or(abortPredicate), backoff, timeout);
    }

    public RetryPolicy<T> retryIf(Predicate<RetryContext<? extends T>> retryPredicate) {
        return new RetryPolicy<>(maxRetries, resultValidator,
                                 retryOn, abortOn, this.retryPredicate.or(retryPredicate), abortPredicate, backoff, timeout);
    }

    public RetryPolicy<T> withoutAbortRules() {
        return new RetryPolicy<>(maxRetries, resultValidator,
                                 retryOn, Collections.emptySet(), retryPredicate, predicateFalse(), backoff, timeout);
    }
    
    public RetryPolicy<T> withoutRetryRules() {
        return new RetryPolicy<>(maxRetries, resultValidator,
                                 Collections.emptySet(), abortOn, predicateFalse(), abortPredicate, backoff, timeout);
    }
    
    public RetryPolicy<T> retryOnce() {
        return new RetryPolicy<T>(0, resultValidator,
                                  retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
    }
    
    public RetryPolicy<T> retryInfinitely() {
        return new RetryPolicy<>(0, resultValidator,
                                 retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
    }

    public RetryPolicy<T> withMaxRetries(int maxRetries) {
        return new RetryPolicy<>(maxRetries, resultValidator,
                                 retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
    }
    
    public RetryPolicy<T> acceptNullResult() {
        return new RetryPolicy<>(maxRetries, ACCEPT_NULL_RESULT,
                                 retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
    }
    
    public RetryPolicy<T> rejectNullResult() {
        return new RetryPolicy<>(maxRetries, REJECT_NULL_RESULT,
                                 retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
    }
    
    public RetryPolicy<T> withResultValidator(Predicate<? super T> resultValidator) {
        return new RetryPolicy<>(maxRetries, resultValidator,
                                 retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
    }
    
    public RetryPolicy<T> withBackoff(DelayPolicy<? super T> backoff) {
        return new RetryPolicy<>(maxRetries, resultValidator,
                                 retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
    }
    
    public RetryPolicy<T> withoutBackoff() {
        return new RetryPolicy<>(maxRetries, resultValidator,
                                 retryOn, abortOn, retryPredicate, abortPredicate, DelayPolicy.INVALID, timeout);
    }
    
    public RetryPolicy<T> withTimeout(DelayPolicy<? super T> timeout) {
        return new RetryPolicy<>(maxRetries, resultValidator,
                                 retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
    }

    public RetryPolicy<T> withoutTimeout() {
        return new RetryPolicy<>(maxRetries, resultValidator,
                                 retryOn, abortOn, retryPredicate, abortPredicate, backoff, DelayPolicy.INVALID);
    }

    public RetryPolicy(int maxRetries, 
                       Predicate<? super T> resultValidator, 
                       Set<Class<? extends Throwable>> retryOn, 
                       Set<Class<? extends Throwable>> abortOn, 
                       Predicate<RetryContext<? extends T>> retryPredicate, 
                       Predicate<RetryContext<? extends T>> abortPredicate, 
                       DelayPolicy<? super T> backoff,
                       DelayPolicy<? super T> timeout) {
        
        this.maxRetries = maxRetries;
        this.resultValidator = resultValidator;
        this.retryOn = retryOn;
        this.abortOn = abortOn;
        this.retryPredicate = retryPredicate;
        this.abortPredicate = abortPredicate;
        this.backoff = backoff;
        this.timeout = timeout;
    }

    public RetryPolicy() {
        this(-1, DelayPolicy.DEFAULT);
    }
    
    public RetryPolicy(long backoff) {
        this(-1, backoff);
    }
    
    public RetryPolicy(Predicate<? super T> resultValidator) {
        this(-1, resultValidator, DelayPolicy.DEFAULT);
    }
    
    public RetryPolicy(Predicate<? super T> resultValidator, long backoff) {
        this(-1, resultValidator, backoff);
    }
    
    public RetryPolicy(int maxRetries, long backoff) {
        this(maxRetries, DelayPolicy.fixedInterval(backoff).withFirstRetryNoDelay());
    }
    
    public RetryPolicy(int maxRetries, Predicate<? super T> resultValidator, long backoff) {
        this(maxRetries, resultValidator, DelayPolicy.fixedInterval(backoff).withFirstRetryNoDelay());
    }
    
    public RetryPolicy(int maxRetries, long backoff, long timeout) {
        this(maxRetries, ACCEPT_NULL_RESULT, backoff, timeout);
    }
    
    public RetryPolicy(int maxRetries, Predicate<? super T> resultValidator, long backoff, long timeout) {
        this(maxRetries, resultValidator, 
             DelayPolicy.fixedInterval(backoff).withFirstRetryNoDelay(),
             DelayPolicy.fixedInterval(timeout)
        );
    }

    public RetryPolicy(int maxRetries, DelayPolicy<? super T> backoff) {
        this(maxRetries, backoff, DelayPolicy.INVALID);
    }
    
    public RetryPolicy(int maxRetries, Predicate<? super T> resultValidator, DelayPolicy<? super T> backoff) {
        this(maxRetries, resultValidator, backoff, DelayPolicy.INVALID);
    }    

    
    public RetryPolicy(int maxRetries, DelayPolicy<? super T> backoff, DelayPolicy<? super T> timeout) {
        this(maxRetries, ACCEPT_NULL_RESULT, backoff, timeout);
    }
    
    public RetryPolicy(int maxRetries, Predicate<? super T> resultValidator, DelayPolicy<? super T> backoff, DelayPolicy<? super T> timeout) {
        this(maxRetries, resultValidator,
             Collections.emptySet(), Collections.emptySet(), 
             predicateFalse(), predicateFalse(),
             backoff,
             timeout
        );
    }

    protected boolean acceptResult(T result) {
        return resultValidator.test(result);
    }

    protected Verdict shouldContinue(RetryContext<? extends T> context) {
        final boolean result;
        if (tooManyRetries(context)) {
            result = false;
        } else if (abortPredicate.test(context)) {
            result = false;
        } else if (retryPredicate.test(context)) {
            result = true;
        } else {
            result = exceptionClassRetryable(context);
        }
        return result ? new PositiveVerdict(backoff.delay(context), timeout.delay(context)) : DONT_RETRY;
    }

    private boolean tooManyRetries(RetryContext<?> context) {
        return maxRetries >= 0 && context.getRetryCount() > maxRetries;
    }

    private boolean exceptionClassRetryable(RetryContext<?> context) {
        if (context.getLastError() == null) {
            return true;
        }
        final Class<? extends Throwable> e = context.getLastError().getClass();
        return !matches(e, abortOn) && matches(e, retryOn); 
    }

    private static boolean matches(Class<? extends Throwable> throwable, Set<Class<? extends Throwable>> set) {
        return set.stream().anyMatch(c -> c.isAssignableFrom(throwable));
    }

    private static <T> Set<T> setPlusElems(Set<T> initial, Collection<T> newElement) {
        final HashSet<T> copy = new HashSet<>(initial);
        copy.addAll(newElement);
        return Collections.unmodifiableSet(copy);
    }
    
    @SuppressWarnings("unchecked")
    private static <T> Predicate<RetryContext<? extends T>> predicateFalse() {
        return (Predicate<RetryContext<? extends T>>)(Predicate<?>)PREDICATE_FALSE;
    }
}
