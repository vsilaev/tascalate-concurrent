/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * 
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/ExponentialDelayBackoff.java 
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
package net.tascalate.concurrent.delays;

import java.time.Duration;

import net.tascalate.concurrent.DelayPolicy;
import net.tascalate.concurrent.RetryContext;

/**
 * A delay policy that implements exponential backoff.
 * <p>
 * The delay for each retry attempt is calculated as: 
 * {@code initialDelay * (multiplier ^ retryCount)}. 
 * This strategy is highly effective for handling transient failures, as it gives 
 * the failing system time to recover while progressively increasing the wait time 
 * between retries.
 * </p>
 * <p>
 * <b>Note:</b> Because exponential growth can quickly lead to impractically long 
 * delays, it is highly recommended to combine this policy with a 
 * {@link BoundedMaxDelayPolicy} (e.g., via {@link DelayPolicy#withMaxDelay()}).
 * </p>
 *
 * @param <T> the type of the result being retried
 * @author vsilaev
 * @see DelayPolicy#exponential(double)
 */
public class ExponentialDelayPolicy<T> implements DelayPolicy<T> {

    /** The base delay duration for the first retry attempt. */
    private final Duration initialDelay;

    /** The factor by which the delay is multiplied after each retry. */
    private final double multiplier;

    /**
     * Creates an exponential delay policy with a default initial delay (100ms).
     *
     * @param multiplier the factor by which the delay is multiplied after each retry
     */
    public ExponentialDelayPolicy(double multiplier) {
        this(FixedIntervalDelayPolicy.DEFAULT_PERIOD_MILLIS, multiplier);
    }

    /**
     * Creates an exponential delay policy with a specified initial delay in milliseconds.
     *
     * @param initialDelayMillis the delay in milliseconds for the first retry
     * @param multiplier         the factor by which the delay is multiplied after each retry
     */
    public ExponentialDelayPolicy(long initialDelayMillis, double multiplier) {
        this(Duration.ofMillis(initialDelayMillis), multiplier);
    }

    /**
     * Creates an exponential delay policy with a specified initial duration.
     *
     * @param initialDelay the delay duration for the first retry
     * @param multiplier   the factor by which the delay is multiplied after each retry
     * @throws IllegalArgumentException if the initial delay is not strictly positive 
     *                                  or if the multiplier is not strictly positive
     */
    public ExponentialDelayPolicy(Duration initialDelay, double multiplier) {
        if (!DelayPolicy.isValid(initialDelay)) {
            throw new IllegalArgumentException("Initial delay must be positive but was: " + initialDelay);
        }
        if (multiplier <= 0) {
            throw new IllegalArgumentException("Multiplier must be a positive number but was: " + multiplier);
        }
        this.initialDelay = initialDelay;
        this.multiplier = multiplier;
    }

    /**
     * Calculates the exponential backoff delay based on the current retry count.
     * <p>
     * Includes a safety check using {@link DurationCalcs} to prevent numeric overflow 
     * when the multiplier raised to the power of the retry count exceeds the maximum 
     * representable duration.
     * </p>
     *
     * @param context the current retry context, providing the retry count
     * @return the calculated exponential delay duration
     */
    @Override
    public Duration delay(RetryContext<? extends T> context) {
        double factor = Math.pow(multiplier, context.getRetryCount());
        return DurationCalcs.safeTransform(
            initialDelay, 
            (amount, dimIdx) -> DurationCalcs.toBoolean((double)Long.MAX_VALUE / Math.abs(amount) > Math.abs(factor)),
            (amount, dimIdx) -> (long)(amount * factor)
        );
    }
}