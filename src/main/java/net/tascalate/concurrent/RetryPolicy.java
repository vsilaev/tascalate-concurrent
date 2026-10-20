/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * 
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/policy/RetryPolicy.java 
 * 
 *
 * Modified work: copyright 2015-2026 Valery Silaev (http://vsilaev.com)
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

/**
 * An immutable, fluent policy that governs the asynchronous retry loop: how many attempts
 * are allowed, which failures (exceptions or invalid results) are retryable, which abort
 * the loop immediately, how long to wait before the next attempt (<i>backoff</i>), and how
 * long a single attempt may run before it is considered hung (<i>timeout</i>).
 *
 * <h2>Decision pipeline</h2>
 * After every failed or invalid attempt the loop consults
 * {@link #shouldContinue(RetryContext)}:
 * <ol>
 *   <li>If the retry budget is exhausted ({@code retryCount > maxRetries}) — stop.</li>
 *   <li>If an <i>abort rule</i> matches — stop. Abort rules are the {@code abortOn}
 *       exception classes and the abort predicates.</li>
 *   <li>If a <i>retry rule</i> matches — retry. Retry rules are the retry predicates
 *       and the {@code retryOn} exception classes (an exception is retryable when it is
 *       assignable to some {@code retryOn} class and to no {@code abortOn} class).</li>
 *   <li>An attempt that completed normally but whose <b>result was rejected</b> by the
 *       result validator is always retryable (there is no exception to classify).</li>
 * </ol>
 *
 * <h2>Defaults that matter</h2>
 * <ul>
 *   <li><b>Nothing is retryable out of the box.</b> With no {@code retryOn} /
 *       {@code retryIf} rules configured, exceptional attempts are not retried. Either
 *       add rules or start from {@link #DEFAULT} (infinite retries on any
 *       {@link Exception}).</li>
 *   <li><b>{@code maxRetries} counts retries after the initial attempt:</b>
 *       {@link #withMaxRetries(int) withMaxRetries(3)} allows up to 4 attempts in total;
 *       {@link #retryOnce()} allows a single attempt with no retries;
 *       {@link #retryInfinitely()} removes the limit.</li>
 *   <li><b>Any result is valid by default</b> — including {@code null}. Use
 *       {@link #rejectNullResult()} or {@link #withResultValidator(Predicate)} to treat
 *       unsatisfactory results as retryable failures (the basis of polling loops).</li>
 *   <li>The {@code long}-based constructors install a fixed-interval backoff with
 *       <i>no delay before the first attempt</i>.</li>
 * </ul>
 *
 * <p>All mutators return new instances; a configured policy is thread-safe and can be
 * shared by any number of retry loops.
 * </p>
 *
 * @param <T> the type of the result this policy validates
 */
public class RetryPolicy<T> {
    
    static final Duration NEGATIVE_DURATION = Duration.ofNanos(-1);

    /**
     * The policy's verdict for the next attempt: whether it should be executed at all,
     * the backoff delay to wait before it, and the timeout to enforce on it.
     */
    public static interface Verdict {
         /** @return {@code true} if the next attempt should be executed */
         boolean shouldExecute();
         /** @return the delay before the next attempt; negative when not applicable */
         Duration backoffDelay();
         /** @return the timeout for the next attempt; negative when not applicable */
         Duration timeout();
     }

     /** Verdict that forbids any further attempts. */
     protected static final Verdict DONT_RETRY = new Verdict() {
         @Override
         public boolean shouldExecute() { return false; }
         @Override
         public Duration backoffDelay() { return NEGATIVE_DURATION; }
         @Override
         public Duration timeout() { return NEGATIVE_DURATION; }
     }; 

     /** Verdict that permits the next attempt with the given backoff and timeout. */
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

     /** Result validator that accepts any result, {@code null} included. */
     public static final Predicate<Object> ACCEPT_NULL_RESULT  = v -> true;

     /** Result validator that accepts any non-{@code null} result. */
     public static final Predicate<Object> REJECT_NULL_RESULT  = v -> v != null;

     /** Result validator that rejects every result, forcing retries until other rules stop the loop. */
     public static final Predicate<Object> IGNORE_RESULT       = v -> false;

     /**
      * The default policy: infinite retries on any {@link Exception} with the default
      * backoff schedule, no per-attempt timeout, and any result accepted.
      */
     public static final RetryPolicy<Object> DEFAULT = new RetryPolicy<>().retryOn(Exception.class);

     private final int maxRetries;
     private final Predicate<? super T> resultValidator;
     private final Set<Class<? extends Throwable>> retryOn;
     private final Set<Class<? extends Throwable>> abortOn;
     private final Predicate<RetryContext<? extends T>> retryPredicate;
     private final Predicate<RetryContext<? extends T>> abortPredicate;
     private final DelayPolicy<? super T> backoff;
     private final DelayPolicy<? super T> timeout;

     /**
      * Returns a copy of this policy that additionally retries when an attempt fails with
      * any of the given throwables (assignability is checked, so subclasses match).
      *
      * @param retryOnThrowables exception classes that make an attempt retryable
      * @return the augmented policy
      */
     @SafeVarargs
     public final RetryPolicy<T> retryOn(Class<? extends Throwable>... retryOnThrowables) {
         return retryOn(Arrays.asList(retryOnThrowables));
     }

     /**
      * Collection-based variant of {@link #retryOn(Class...)}.
      *
      * @param retryOnThrowables exception classes that make an attempt retryable
      * @return the augmented policy
      */
     public RetryPolicy<T> retryOn(Collection<Class<? extends Throwable>> retryOnThrowables) {
         return new RetryPolicy<T>(maxRetries, resultValidator,
                                   setPlusElems(retryOn, retryOnThrowables), abortOn, retryPredicate, abortPredicate, backoff, timeout);
     }

     /**
      * Returns a copy of this policy that immediately aborts (no further retries) when an
      * attempt fails with any of the given throwables. Abort rules take precedence over
      * retry rules.
      *
      * @param abortOnThrowables exception classes that abort the retry loop
      * @return the augmented policy
      */
     @SafeVarargs
     public final RetryPolicy<T> abortOn(Class<? extends Throwable>... abortOnThrowables) {
         return abortOn(Arrays.asList(abortOnThrowables));
     }

     /**
      * Collection-based variant of {@link #abortOn(Class...)}.
      *
      * @param abortOnThrowables exception classes that abort the retry loop
      * @return the augmented policy
      */
     public RetryPolicy<T> abortOn(Collection<Class<? extends Throwable>> abortOnThrowables) {
         return new RetryPolicy<>(maxRetries, resultValidator, 
                                  retryOn, setPlusElems(abortOn, abortOnThrowables), retryPredicate, abortPredicate, backoff, timeout);
     }

     /**
      * Returns a copy of this policy with an additional abort predicate, OR-combined with
      * the existing ones. The predicate inspects the {@link RetryContext} of the failed
      * attempt (attempt count, previous result/error, duration).
      *
      * @param abortPredicate condition under which the loop must stop
      * @return the augmented policy
      */
     public RetryPolicy<T> abortIf(Predicate<RetryContext<? extends T>> abortPredicate) {
         return new RetryPolicy<>(maxRetries, resultValidator,
                                  retryOn, abortOn, retryPredicate, this.abortPredicate.or(abortPredicate), backoff, timeout);
     }

     /**
      * Returns a copy of this policy with an additional retry predicate, OR-combined with
      * the existing ones. The predicate inspects the {@link RetryContext} of the failed
      * attempt and may implement adaptive rules (e.g., retry only while attempts are fast).
      *
      * @param retryPredicate condition under which the loop must continue
      * @return the augmented policy
      */
     public RetryPolicy<T> retryIf(Predicate<RetryContext<? extends T>> retryPredicate) {
         return new RetryPolicy<>(maxRetries, resultValidator,
                                  retryOn, abortOn, this.retryPredicate.or(retryPredicate), abortPredicate, backoff, timeout);
     }

     /**
      * Returns a copy of this policy with all abort rules removed (both exception classes
      * and predicates).
      *
      * @return the simplified policy
      */
     public RetryPolicy<T> withoutAbortRules() {
         return new RetryPolicy<>(maxRetries, resultValidator,
                                  retryOn, Collections.emptySet(), retryPredicate, predicateFalse(), backoff, timeout);
     }

     /**
      * Returns a copy of this policy with all retry rules removed (both exception classes
      * and predicates). Combined with the default classification this makes exceptional
      * attempts non-retryable.
      *
      * @return the simplified policy
      */
     public RetryPolicy<T> withoutRetryRules() {
         return new RetryPolicy<>(maxRetries, resultValidator,
                                  Collections.emptySet(), abortOn, predicateFalse(), abortPredicate, backoff, timeout);
     }

     /**
      * Returns a copy of this policy that performs a single attempt with no retries
      * ({@code maxRetries = 0}).
      *
      * @return the restricted policy
      */
     public RetryPolicy<T> retryOnce() {
         return new RetryPolicy<T>(0, resultValidator,
                                   retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
     }

     /**
      * Returns a copy of this policy with no limit on the number of retries
      * ({@code maxRetries = -1}).
      *
      * @return the unrestricted policy
      */
     public RetryPolicy<T> retryInfinitely() {
         return new RetryPolicy<>(-1, resultValidator,
                                  retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
     }

     /**
      * Returns a copy of this policy with the given retry budget. The budget counts
      * retries <em>after</em> the initial attempt: {@code withMaxRetries(n)} allows up to
      * {@code n + 1} attempts in total.
      *
      * @param maxRetries the maximum number of retries after the initial attempt
      * @return the restricted policy
      */
     public RetryPolicy<T> withMaxRetries(int maxRetries) {
         return new RetryPolicy<>(maxRetries, resultValidator,
                                  retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
     }

     /**
      * Returns a copy of this policy that accepts any result, {@code null} included.
      *
      * @return the relaxed policy
      */
     public RetryPolicy<T> acceptNullResult() {
         return new RetryPolicy<>(maxRetries, ACCEPT_NULL_RESULT,
                                  retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
     }

     /**
      * Returns a copy of this policy that treats a {@code null} result as a retryable
      * failure. Combined with {@code Promises.retryOptional(...)} this turns an empty
      * {@code Optional} into "not ready yet — poll again".
      *
      * @return the stricter policy
      */
     public RetryPolicy<T> rejectNullResult() {
         return new RetryPolicy<>(maxRetries, REJECT_NULL_RESULT,
                                  retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
     }

     /**
      * Returns a copy of this policy with a custom result validator. A result that fails
      * the validator makes the attempt retryable (subject to the retry budget and abort
      * rules), with the rejected result available via {@link RetryContext#getLastResult()}.
      *
      * @param resultValidator predicate that must return {@code true} for a result to be
      *                        considered valid
      * @return the augmented policy
      */
     public RetryPolicy<T> withResultValidator(Predicate<? super T> resultValidator) {
         return new RetryPolicy<>(maxRetries, resultValidator,
                                  retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
     }

     /**
      * Returns a copy of this policy with the given backoff schedule. The
      * {@link DelayPolicy} computes the delay before each retry from the current
      * {@link RetryContext}, so both fixed and adaptive schedules are supported.
      *
      * @param backoff the delay schedule between attempts
      * @return the augmented policy
      */
     public RetryPolicy<T> withBackoff(DelayPolicy<? super T> backoff) {
         return new RetryPolicy<>(maxRetries, resultValidator,
                                  retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
     }

     /**
      * Returns a copy of this policy with no backoff — retries happen immediately.
      *
      * @return the accelerated policy
      */
     public RetryPolicy<T> withoutBackoff() {
         return new RetryPolicy<>(maxRetries, resultValidator,
                                  retryOn, abortOn, retryPredicate, abortPredicate, DelayPolicy.INVALID, timeout);
     }

     /**
      * Returns a copy of this policy with a per-attempt timeout. An attempt that does not
      * complete within the timeout is failed and handled like any other failed attempt
      * (retry or abort, per the rules). The {@link DelayPolicy} may compute the timeout
      * dynamically from the current {@link RetryContext}.
      *
      * @param timeout the per-attempt timeout schedule
      * @return the augmented policy
      */
     public RetryPolicy<T> withTimeout(DelayPolicy<? super T> timeout) {
         return new RetryPolicy<>(maxRetries, resultValidator,
                                  retryOn, abortOn, retryPredicate, abortPredicate, backoff, timeout);
     }

     /**
      * Returns a copy of this policy with no per-attempt timeout.
      *
      * @return the relaxed policy
      */
     public RetryPolicy<T> withoutTimeout() {
         return new RetryPolicy<>(maxRetries, resultValidator,
                                  retryOn, abortOn, retryPredicate, abortPredicate, backoff, DelayPolicy.INVALID);
     }

     /**
      * Full constructor.
      *
      * @param maxRetries      maximum retries after the initial attempt; negative for unlimited
      * @param resultValidator predicate validating attempt results
      * @param retryOn         exception classes that make an attempt retryable
      * @param abortOn         exception classes that abort the loop
      * @param retryPredicate  additional retry condition on the {@link RetryContext}
      * @param abortPredicate  additional abort condition on the {@link RetryContext}
      * @param backoff         delay schedule between attempts
      * @param timeout         per-attempt timeout schedule
      */
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

     /** Unlimited retries, default backoff, no exception rules — configure before use. */
     public RetryPolicy() {
         this(-1, DelayPolicy.DEFAULT);
     }

     /** Unlimited retries with a fixed-interval backoff (in milliseconds). */
     public RetryPolicy(long backoff) {
         this(-1, backoff);
     }

     /** Unlimited retries with a result validator and the default backoff. */
     public RetryPolicy(Predicate<? super T> resultValidator) {
         this(-1, resultValidator, DelayPolicy.DEFAULT);
     }

     /** Unlimited retries with a result validator and a fixed-interval backoff. */
     public RetryPolicy(Predicate<? super T> resultValidator, long backoff) {
         this(-1, resultValidator, backoff);
     }

     /** Budgeted retries with a fixed-interval backoff; no delay before the first attempt. */
     public RetryPolicy(int maxRetries, long backoff) {
         this(maxRetries, DelayPolicy.fixedInterval(backoff).withFirstRetryNoDelay());
     }

     /** Budgeted retries with a result validator and a fixed-interval backoff. */
     public RetryPolicy(int maxRetries, Predicate<? super T> resultValidator, long backoff) {
         this(maxRetries, resultValidator, DelayPolicy.fixedInterval(backoff).withFirstRetryNoDelay());
     }

     /** Budgeted retries with a fixed backoff and a fixed per-attempt timeout (both in milliseconds). */
     public RetryPolicy(int maxRetries, long backoff, long timeout) {
         this(maxRetries, ACCEPT_NULL_RESULT, backoff, timeout);
     }

     /** Budgeted retries with a result validator, a fixed backoff and a fixed per-attempt timeout. */
     public RetryPolicy(int maxRetries, Predicate<? super T> resultValidator, long backoff, long timeout) {
         this(maxRetries, resultValidator, 
              DelayPolicy.fixedInterval(backoff).withFirstRetryNoDelay(),
              DelayPolicy.fixedInterval(timeout)
         );
     }

     /** Budgeted retries with the given backoff schedule and no timeout. */
     public RetryPolicy(int maxRetries, DelayPolicy<? super T> backoff) {
         this(maxRetries, backoff, DelayPolicy.INVALID);
     }

     /** Budgeted retries with a result validator, the given backoff schedule and no timeout. */
     public RetryPolicy(int maxRetries, Predicate<? super T> resultValidator, DelayPolicy<? super T> backoff) {
         this(maxRetries, resultValidator, backoff, DelayPolicy.INVALID);
     }    

     /** Budgeted retries with the given backoff and timeout schedules, accepting null results. */
     public RetryPolicy(int maxRetries, DelayPolicy<? super T> backoff, DelayPolicy<? super T> timeout) {
         this(maxRetries, ACCEPT_NULL_RESULT, backoff, timeout);
     }

     /** Budgeted retries with a result validator and the given backoff and timeout schedules. */
     public RetryPolicy(int maxRetries, Predicate<? super T> resultValidator, DelayPolicy<? super T> backoff, DelayPolicy<? super T> timeout) {
         this(maxRetries, resultValidator,
              Collections.emptySet(), Collections.emptySet(), 
              predicateFalse(), predicateFalse(),
              backoff,
              timeout
         );
     }

     /**
      * Tests an attempt result against the configured result validator.
      *
      * @param result the result to validate
      * @return {@code true} if the result is valid and the loop may complete successfully
      */
     protected boolean acceptResult(T result) {
         return resultValidator.test(result);
     }

     /**
      * Decides whether the retry loop should continue after a failed or invalid attempt,
      * applying the pipeline described in the class documentation, and computes the
      * backoff delay and the per-attempt timeout for the next attempt.
      *
      * @param context the state of the loop after the last attempt
      * @return a positive verdict with the delay/timeout for the next attempt, or a
      *         negative verdict when the loop must stop
      */
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

     /**
      * Classifies an exceptional attempt: an attempt with no error (rejected result) is
      * always retryable; otherwise the error class must match some {@code retryOn} class
      * and no {@code abortOn} class.
      *
      * @param context the state of the loop after the last attempt
      * @return {@code true} if the attempt may be retried
      */
     protected boolean exceptionClassRetryable(RetryContext<?> context) {
         if (context.getLastError() == null) {
             return true;
         }
         final Class<? extends Throwable> e = context.getLastError().getClass();
         return !matches(e, abortOn) && matches(e, retryOn); 
     }

     private boolean tooManyRetries(RetryContext<?> context) {
         return maxRetries >= 0 && context.getRetryCount() > maxRetries;
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