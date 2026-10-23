/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * 
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/BoundedMaxBackoff.java 
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
 * A delay policy decorator that enforces a maximum delay cap.
 * <p>
 * This policy wraps a target {@link DelayPolicy} and ensures that the computed 
 * delay never exceeds the specified {@code maxDelay}. This is essential when 
 * using exponential backoff to prevent the delay from growing infinitely large 
 * and causing unacceptably long wait times.
 * </p>
 *
 * @param <T> the type of the result being retried
 * @author vsilaev
 * @see DelayPolicy#withMaxDelay()
 */
public class BoundedMaxDelayPolicy<T> extends DelayPolicyWrapper<T> {

    /** The default maximum delay of 10,000 milliseconds (10 seconds). */
    public static final long DEFAULT_MAX_DELAY_MILLIS = 10_000;

    /** The enforced maximum delay duration. */
    private final Duration maxDelay;

    /**
     * Creates a bounded maximum delay policy with the default maximum delay (10s).
     *
     * @param target the underlying delay policy to wrap
     */
    public BoundedMaxDelayPolicy(DelayPolicy<? super T> target) {
        this(target, DEFAULT_MAX_DELAY_MILLIS);
    }

    /**
     * Creates a bounded maximum delay policy with a specified maximum delay in milliseconds.
     *
     * @param target         the underlying delay policy to wrap
     * @param maxDelayMillis the maximum delay in milliseconds
     */
    public BoundedMaxDelayPolicy(DelayPolicy<? super T> target, long maxDelayMillis) {
        this(target, Duration.ofMillis(maxDelayMillis));
    }

    /**
     * Creates a bounded maximum delay policy with a specified maximum duration.
     *
     * @param target   the underlying delay policy to wrap
     * @param maxDelay the maximum delay duration
     * @throws IllegalArgumentException if the maximum delay is not strictly positive
     */
    public BoundedMaxDelayPolicy(DelayPolicy<? super T> target, Duration maxDelay) {
        super(target);
        if (!DelayPolicy.isValid(maxDelay)) {
            throw new IllegalArgumentException("MaxDelay must be positive but was: " + maxDelay);
        }
        this.maxDelay = maxDelay;
    }

    /**
     * Calculates the delay, ensuring it does not exceed the configured maximum.
     *
     * @param context the current retry context
     * @return the minimum of the target policy's delay and the configured maximum delay
     */
    @Override
    public Duration delay(RetryContext<? extends T> context) {
        return min(target.delay(context), maxDelay);
    }
}