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

public class RetryPolicy {
    
    public static interface Outcome {
        boolean shouldExecute();
        Duration backoffDelay();
        Duration timeout();
    }
    
    public static final Outcome DONT_RETRY = new Outcome() {
        @Override
        public boolean shouldExecute() { return false; }

        @Override
        public Duration backoffDelay() { return Timeouts.NEGATIVE_DURATION; }
        
        @Override
        public Duration timeout() { return Timeouts.NEGATIVE_DURATION; }
    }; 
    
    private static class PositiveOutcome implements Outcome {
        private final Duration backoffDelay;
        private final Duration timeoutDelay;
        
        PositiveOutcome(Duration backoffDelay, Duration timeoutDelay) {
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
    
    private static final Predicate<RetryContext> PREDICATE_TRUE = ctx -> true;
    private static final Predicate<RetryContext> PREDICATE_FALSE = ctx -> false;
    
    public static final RetryPolicy DEFAULT = new RetryPolicy().retryOn(Exception.class);

    private final int maxRetries;
    private final Set<Class<? extends Throwable>> retryOn;
    private final Set<Class<? extends Throwable>> abortOn;
    private final Predicate<RetryContext> retryPredicate;
    private final Predicate<RetryContext> abortPredicate;
    private final DelayPolicy backoff;
    private final DelayPolicy timeout;

    @SafeVarargs
    public final RetryPolicy retryOn(Class<? extends Throwable>... retryOnThrowables) {
        return retryOn(Arrays.asList(retryOnThrowables));
    }
    
    public RetryPolicy retryOn(Collection<Class<? extends Throwable>> retryOnThrowables) {
        return new RetryPolicy(maxRetries, setPlusElems(retryOn, retryOnThrowables), abortOn, retryPredicate, abortPredicate, backoff, timeout);
    }

    @SafeVarargs
    public final RetryPolicy abortOn(Class<? extends Throwable>... abortOnThrowables) {
        return abortOn(Arrays.asList(abortOnThrowables));
    }

    public RetryPolicy abortOn(Collection<Class<? extends Throwable>> abortOnThrowables) {
        return new RetryPolicy(maxRetries, retryOn, setPlusElems(abortOn, abortOnThrowables), retryPredicate, abortPredicate, backoff, timeout);
    }
    
    
    public RetryPolicy abortIf(Predicate<RetryContext> abortPredicate) {
        return new RetryPolicy(maxRetries, retryOn, abortOn, retryPredicate, abortPredicate.or(abortPredicate), backoff, timeout);
    }

    public RetryPolicy retryIf(Predicate<RetryContext> retryPredicate) {
        return new RetryPolicy(maxRetries, retryOn, abortOn, this.retryPredicate.or(retryPredicate), abortPredicate, backoff, timeout);
    }

    public RetryPolicy dontRetry() {
        return new RetryPolicy(0, retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
    }

    public RetryPolicy withMaxRetries(int maxRetries) {
        return new RetryPolicy(maxRetries, retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
    }
    
    public RetryPolicy withBackoff(DelayPolicy backoff) {
        return new RetryPolicy(maxRetries, retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
    }
    
    public RetryPolicy withTimeout(DelayPolicy timeout) {
        return new RetryPolicy(maxRetries, retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
    }


    public RetryPolicy(int maxRetries, 
                       Set<Class<? extends Throwable>> retryOn, 
                       Set<Class<? extends Throwable>> abortOn, 
                       Predicate<RetryContext> retryPredicate, 
                       Predicate<RetryContext> abortPredicate, 
                       DelayPolicy backoff,
                       DelayPolicy timeout) {
        
        this.maxRetries = maxRetries;
        this.retryOn = retryOn;
        this.abortOn = abortOn;
        this.retryPredicate = retryPredicate;
        this.abortPredicate = abortPredicate;
        this.backoff = backoff;
        this.timeout = timeout;
    }

    public RetryPolicy() {
        this(Integer.MAX_VALUE, DelayPolicy.DEFAULT);
    }
    
    public RetryPolicy(long backoff) {
        this(Integer.MAX_VALUE, backoff);
    }
    
    public RetryPolicy(int maxRetries, long backoff) {
        this(maxRetries, DelayPolicy.fixedInterval(backoff).withFirstRetryNoDelay());
    }
    
    public RetryPolicy(int maxRetries, long backoff, long timeout) {
        this(maxRetries, 
             DelayPolicy.fixedInterval(backoff).withFirstRetryNoDelay(),
             DelayPolicy.fixedInterval(timeout)
        );
    }

    public RetryPolicy(int maxRetries, DelayPolicy backoff) {
        this(maxRetries, backoff, DelayPolicy.INVALID);
    }    
    
    public RetryPolicy(int maxRetries, DelayPolicy backoff, DelayPolicy timeout) {
        this(maxRetries, 
             Collections.emptySet(), Collections.emptySet(), 
             PREDICATE_TRUE, PREDICATE_FALSE, 
             backoff,
             timeout
        );
    }


    public Outcome shouldContinue(RetryContext context) {
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
        return result ? new PositiveOutcome(backoff.delay(context), timeout.delay(context)) : DONT_RETRY;
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

    private static <T> Set<T> setPlusElems(Set<T> initial, Collection<T> newElement) {
        final HashSet<T> copy = new HashSet<>(initial);
        copy.addAll(newElement);
        return Collections.unmodifiableSet(copy);
    }
    
}
