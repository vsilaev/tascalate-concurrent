/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/FixedIntervalBackoff.java 
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
 * A delay policy that returns a fixed, constant delay between every retry attempt.
 * <p>
 * This is the simplest backoff strategy: regardless of how many retries have occurred
 * or what the previous outcome was, the delay remains the same. It is useful when
 * you want predictable, evenly-spaced retry intervals (e.g., polling a service every
 * 5 seconds).
 * </p>
 *
 * @param <T> the type of the result being retried
 * @author vsilaev
 * @see DelayPolicy#fixedInterval()
 */
public class FixedIntervalDelayPolicy<T> implements DelayPolicy<T> {

    /** The default fixed delay interval: 1000 milliseconds (1 second). */
    public static final long DEFAULT_PERIOD_MILLIS = 1000;

    /** The fixed delay duration returned by this policy. */
    private final Duration interval;

    /**
     * Creates a fixed interval delay policy with the default interval of 1000ms.
     */
    public FixedIntervalDelayPolicy() {
        this(DEFAULT_PERIOD_MILLIS);
    }

    /**
     * Creates a fixed interval delay policy with the specified interval in milliseconds.
     *
     * @param intervalMillis the fixed delay in milliseconds
     */
    public FixedIntervalDelayPolicy(long intervalMillis) {
        this(Duration.ofMillis(intervalMillis));
    }

    /**
     * Creates a fixed interval delay policy with the specified duration.
     *
     * @param interval the fixed delay duration
     */
    public FixedIntervalDelayPolicy(Duration interval) {
        this.interval = interval;
    }

    /**
     * Returns the fixed delay interval, regardless of the retry context.
     *
     * @param context the current retry context (ignored by this policy)
     * @return the fixed delay duration
     */
    @Override
    public Duration delay(RetryContext<? extends T> context) {
        return interval;
    }
}