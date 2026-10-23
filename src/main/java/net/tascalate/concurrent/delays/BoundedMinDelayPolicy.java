/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/BoundedMinBackoff.java 
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
package net.tascalate.concurrent.delays;

import java.time.Duration;

import net.tascalate.concurrent.DelayPolicy;
import net.tascalate.concurrent.RetryContext;

/**
 * A delay policy decorator that enforces a minimum delay threshold.
 * <p>
 * This policy wraps a target {@link DelayPolicy} and ensures that the computed 
 * delay is never less than the specified {@code minDelay}. This is particularly 
 * useful when combining exponential backoff or random jitter with a base policy, 
 * preventing the delay from becoming impractically small.
 * </p>
 *
 * @param <T> the type of the result being retried
 * @author vsilaev
 * @see DelayPolicy#withMinDelay()
 */
public class BoundedMinDelayPolicy<T> extends DelayPolicyWrapper<T> {

    /** The default minimum delay of 100 milliseconds. */
    public static final long DEFAULT_MIN_DELAY_MILLIS = 100;

    /** The enforced minimum delay duration. */
    private final Duration minDelay;

    /**
     * Creates a bounded minimum delay policy with the default minimum delay (100ms).
     *
     * @param target the underlying delay policy to wrap
     */
    public BoundedMinDelayPolicy(DelayPolicy<? super T> target) {
        this(target, DEFAULT_MIN_DELAY_MILLIS);
    }

    /**
     * Creates a bounded minimum delay policy with a specified minimum delay in milliseconds.
     *
     * @param target         the underlying delay policy to wrap
     * @param minDelayMillis the minimum delay in milliseconds
     */
    public BoundedMinDelayPolicy(DelayPolicy<? super T> target, long minDelayMillis) {
        this(target, Duration.ofMillis(minDelayMillis));
    }

    /**
     * Creates a bounded minimum delay policy with a specified minimum duration.
     *
     * @param target   the underlying delay policy to wrap
     * @param minDelay the minimum delay duration
     * @throws IllegalArgumentException if the minimum delay is not strictly positive
     */
    public BoundedMinDelayPolicy(DelayPolicy<? super T> target, Duration minDelay) {
        super(target);
        if (!DelayPolicy.isValid(minDelay)) {
            throw new IllegalArgumentException("MinDelay must be positive but was: " + minDelay);
        }
        this.minDelay = minDelay;
    }

    /**
     * Calculates the delay, ensuring it is at least the configured minimum.
     *
     * @param context the current retry context
     * @return the maximum of the target policy's delay and the configured minimum delay
     */
    @Override
    public Duration delay(RetryContext<? extends T> context) {
        return max(target.delay(context), minDelay);
    }
}