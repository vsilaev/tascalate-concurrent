/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * 
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/Backoff.java 
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
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import net.tascalate.concurrent.delays.BoundedMaxDelayPolicy;
import net.tascalate.concurrent.delays.BoundedMinDelayPolicy;
import net.tascalate.concurrent.delays.ExponentialDelayPolicy;
import net.tascalate.concurrent.delays.FirstRetryNoDelayPolicy;
import net.tascalate.concurrent.delays.FixedIntervalDelayPolicy;
import net.tascalate.concurrent.delays.OnFailureNoDelayPolicy;
import net.tascalate.concurrent.delays.OnSuccessNoDelayPolicy;
import net.tascalate.concurrent.delays.ProportionalRandomDelayPolicy;
import net.tascalate.concurrent.delays.UniformRandomDelayPolicy;

/**
 * The core contract for determining the backoff delay between retry attempts and timeouts 
 * for each iteration.
 * <p>
 * Implementations of this interface calculate the {@link Duration} to wait before 
 * the next retry based on the current {@link RetryContext}. This allows for sophisticated 
 * retry strategies such as fixed intervals, exponential backoff, and randomized jitter.
 * </p>
 * <p>
 * The interface provides a rich set of static factory methods and default decorator 
 * methods (e.g., {@link #withUniformJitter()}, {@link #withMaxDelay()}) to easily 
 * compose complex delay policies.
 * </p>
 *
 * @param <T> the type of the result being retried
 * @author vsilaev
 * @see RetryPolicy
 * @see RetryContext
 */
public interface DelayPolicy<T> {

    /**
     * The default delay policy: no delay for the first retry, followed by a fixed 
     * interval for subsequent retries.
     */
    public static final DelayPolicy<Object> DEFAULT = new FirstRetryNoDelayPolicy<Object>(new FixedIntervalDelayPolicy<>());

    /**
     * An invalid delay policy that always returns a negative duration, signaling 
     * that no further retries should be attempted.
     */
    public static final DelayPolicy<Object> INVALID = ctx -> RetryPolicy.NEGATIVE_DURATION;

    /**
     * Calculates the delay duration before the next retry attempt.
     *
     * @param retryContext the context containing information about the current retry 
     *                     attempt, including the retry count, previous result, and 
     *                     previous exception
     * @return the {@link Duration} to wait before the next attempt
     */
    Duration delay(RetryContext<? extends T> retryContext);

    /**
     * Creates a policy with a fixed delay interval (default 100ms).
     *
     * @param <T> the result type
     * @return a new fixed interval delay policy
     */
    public static <T> DelayPolicy<T> fixedInterval() {
        return new FixedIntervalDelayPolicy<>();
    }

    /**
     * Creates a policy with a specified fixed delay interval.
     *
     * @param <T>      the result type
     * @param interval the fixed delay duration
     * @return a new fixed interval delay policy
     */
    public static <T> DelayPolicy<T> fixedInterval(Duration interval) {
        return new FixedIntervalDelayPolicy<>(interval);
    }

    /**
     * Creates a policy with a specified fixed delay interval.
     *
     * @param <T>      the result type
     * @param interval the fixed delay amount
     * @param timeUnit the time unit of the delay amount
     * @return a new fixed interval delay policy
     */
    public static <T> DelayPolicy<T> fixedInterval(long interval, TimeUnit timeUnit) {
        return fixedInterval(AsyncInternals.toDuration(interval, timeUnit));
    }

    /**
     * Creates a policy with a specified fixed delay interval in milliseconds.
     *
     * @param <T>            the result type
     * @param intervalMillis the fixed delay in milliseconds
     * @return a new fixed interval delay policy
     */
    public static <T> DelayPolicy<T> fixedInterval(long intervalMillis) {
    	return fixedInterval(Duration.ofMillis(intervalMillis));
    }

    /**
     * Creates an exponential backoff policy with a default initial delay (100ms).
     *
     * @param <T>        the result type
     * @param multiplier the factor by which the delay is multiplied after each retry
     * @return a new exponential delay policy
     */
    public static <T> DelayPolicy<T> exponential(double multiplier) {
    	return new ExponentialDelayPolicy<>(multiplier);
    }

    /**
     * Creates an exponential backoff policy with a specified initial delay.
     *
     * @param <T>          the result type
     * @param initialDelay the delay duration for the first retry
     * @param multiplier   the factor by which the delay is multiplied after each retry
     * @return a new exponential delay policy
     */
    public static <T> DelayPolicy<T> exponential(Duration initialDelay, double multiplier) {
        return new ExponentialDelayPolicy<>(initialDelay, multiplier);
    }

    /**
     * Creates an exponential backoff policy with a specified initial delay.
     *
     * @param <T>          the result type
     * @param initialDelay the delay amount for the first retry
     * @param timeUnit     the time unit of the delay amount
     * @param multiplier   the factor by which the delay is multiplied after each retry
     * @return a new exponential delay policy
     */
    public static <T> DelayPolicy<T> exponential(long initialDelay, TimeUnit timeUnit, double multiplier) {
        return exponential(AsyncInternals.toDuration(initialDelay, timeUnit), multiplier);
    }

    /**
     * Creates an exponential backoff policy with a specified initial delay in milliseconds.
     *
     * @param <T>              the result type
     * @param initialDelayMillis the delay in milliseconds for the first retry
     * @param multiplier       the factor by which the delay is multiplied after each retry
     * @return a new exponential delay policy
     */
    public static <T> DelayPolicy<T> exponential(long initialDelayMillis, double multiplier) {
    	return exponential(Duration.ofMillis(initialDelayMillis), multiplier);
    }

    /**
     * Decorates this policy to add uniform random jitter. The jitter is uniformly 
     * distributed between {@code -range/2} and {@code +range/2} (default range: 100ms).
     *
     * @return a new delay policy with uniform jitter applied
     */
    default DelayPolicy<T> withUniformJitter() {
        return new UniformRandomDelayPolicy<>(this);
    }

    /**
     * Decorates this policy to add uniform random jitter with a specified range in milliseconds.
     *
     * @param range the maximum jitter range in milliseconds
     * @return a new delay policy with uniform jitter applied
     */
    default DelayPolicy<T> withUniformJitter(long range) {
        return withUniformJitter(range, TimeUnit.MILLISECONDS);
    }

    /**
     * Decorates this policy to add uniform random jitter with a specified range.
     *
     * @param range    the maximum jitter range amount
     * @param timeUnit the time unit of the range amount
     * @return a new delay policy with uniform jitter applied
     */
    default DelayPolicy<T> withUniformJitter(long range, TimeUnit timeUnit) {
        return withUniformJitter(AsyncInternals.toDuration(range, timeUnit));
    }

    /**
     * Decorates this policy to add uniform random jitter with a specified duration range.
     *
     * @param range the maximum jitter duration range
     * @return a new delay policy with uniform jitter applied
     */
    default DelayPolicy<T> withUniformJitter(Duration range) {
        return new UniformRandomDelayPolicy<>(this, range);
    }

    /**
     * Decorates this policy to add proportional random jitter. The jitter is 
     * proportional to the base delay (default multiplier: 0.5, meaning ±50%).
     *
     * @return a new delay policy with proportional jitter applied
     */
    default DelayPolicy<T> withProportionalJitter() {
        return new ProportionalRandomDelayPolicy<>(this);
    }

    /**
     * Decorates this policy to add proportional random jitter with a specified multiplier.
     *
     * @param multiplier the jitter multiplier (e.g., 0.5 for ±50%)
     * @return a new delay policy with proportional jitter applied
     */
    default DelayPolicy<T> withProportionalJitter(double multiplier) {
        return new ProportionalRandomDelayPolicy<>(this, multiplier);
    }

    /**
     * Decorates this policy to enforce a minimum delay (default: 100ms).
     *
     * @return a new delay policy with a minimum delay bound
     */
    default DelayPolicy<T> withMinDelay() {
        return new BoundedMinDelayPolicy<>(this);
    }

    /**
     * Decorates this policy to enforce a specified minimum delay.
     *
     * @param minDelay the minimum delay duration
     * @return a new delay policy with a minimum delay bound
     */
    default DelayPolicy<T> withMinDelay(Duration minDelay) {
        return new BoundedMinDelayPolicy<>(this, minDelay);
    }

    /**
     * Decorates this policy to enforce a specified minimum delay.
     *
     * @param minDelay the minimum delay amount
     * @param timeUnit the time unit of the delay amount
     * @return a new delay policy with a minimum delay bound
     */
    default DelayPolicy<T> withMinDelay(long minDelay, TimeUnit timeUnit) {
        return withMinDelay(AsyncInternals.toDuration(minDelay, timeUnit));
    }

    /**
     * Decorates this policy to enforce a specified minimum delay in milliseconds.
     *
     * @param minDelayMillis the minimum delay in milliseconds
     * @return a new delay policy with a minimum delay bound
     */
    default DelayPolicy<T> withMinDelay(long minDelayMillis) {
        return withMinDelay(Duration.ofMillis(minDelayMillis));
    }

    /**
     * Decorates this policy to enforce a maximum delay cap (default: 10,000ms).
     *
     * @return a new delay policy with a maximum delay bound
     */
    default DelayPolicy<T> withMaxDelay() {
        return new BoundedMaxDelayPolicy<>(this);
    }

    /**
     * Decorates this policy to enforce a specified maximum delay cap.
     *
     * @param maxDelay the maximum delay duration
     * @return a new delay policy with a maximum delay bound
     */
    default DelayPolicy<T> withMaxDelay(Duration maxDelay) {
        return new BoundedMaxDelayPolicy<>(this, maxDelay);
    }

    /**
     * Decorates this policy to enforce a specified maximum delay cap.
     *
     * @param maxDelay the maximum delay amount
     * @param timeUnit the time unit of the delay amount
     * @return a new delay policy with a maximum delay bound
     */
    default DelayPolicy<T> withMaxDelay(long maxDelay, TimeUnit timeUnit) {
        return withMaxDelay(AsyncInternals.toDuration(maxDelay, timeUnit));
    }

    /**
     * Decorates this policy to enforce a specified maximum delay cap in milliseconds.
     *
     * @param maxDelayMillis the maximum delay in milliseconds
     * @return a new delay policy with a maximum delay bound
     */
    default DelayPolicy<T> withMaxDelay(long maxDelayMillis) {
        return withMaxDelay(Duration.ofMillis(maxDelayMillis));
    }

    /**
     * Decorates this policy to skip the delay for the very first retry attempt.
     *
     * @return a new delay policy with no delay on the first retry
     */
    default DelayPolicy<T> withFirstRetryNoDelay() {
        return new FirstRetryNoDelayPolicy<>(this);
    }

    /**
     * Decorates this policy to skip the delay if the previous attempt failed.
     *
     * @return a new delay policy with no delay on failure
     */
    default DelayPolicy<T> withOnFailureNoDelay() {
        return new OnFailureNoDelayPolicy<>(this);
    }

    /**
     * Decorates this policy to skip the delay if the previous attempt succeeded 
     * (but was rejected by the result validator).
     *
     * @return a new delay policy with no delay on success
     */
    default DelayPolicy<T> withOnSuccessNoDelay() {
        return new OnSuccessNoDelayPolicy<>(this);
    }

    /**
     * Applies a custom transformation to this delay policy.
     *
     * @param <D> the type of the resulting delay policy
     * @param fn  the function to apply to this policy
     * @return the transformed delay policy
     */
    default <D extends DelayPolicy<T>> D withCusomizer(Function<? super DelayPolicy<T>, D> fn) {
        return fn.apply(this);
    }

    /**
     * Checks if the given duration is valid for a retry delay.
     * A valid delay must be strictly positive (greater than zero).
     *
     * @param d the duration to check
     * @return {@code true} if the duration is positive, {@code false} otherwise
     */
    public static boolean isValid(Duration d) {
        return !(d.isNegative() || d.isZero());
    }
}