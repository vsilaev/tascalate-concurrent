/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * 
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/UniformRandomBackoff.java
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
import java.util.Random;

import net.tascalate.concurrent.DelayPolicy;

/**
 * A delay policy decorator that adds uniform random jitter within a fixed absolute range 
 * to the target policy's delay.
 * <p>
 * Unlike {@link ProportionalRandomDelayPolicy} (which scales jitter relative to the base delay), 
 * this policy adds a random value uniformly distributed between {@code -range} and {@code +range}. 
 * For example, with a default range of 100ms, a base delay of 1000ms will be jittered to a value 
 * between 900ms and 1100ms, and a base delay of 5000ms will also be jittered between 4900ms and 5100ms.
 * </p>
 * <p>
 * This is useful for preventing synchronized "thundering herd" retries across multiple clients 
 * when the base delay is relatively constant (e.g., fixed interval polling).
 * </p>
 *
 * @param <T> the type of the result being retried
 * @author vsilaev
 * @see DelayPolicy#withUniformJitter()
 * @see ProportionalRandomDelayPolicy
 */
public class UniformRandomDelayPolicy<T> extends RandomDelayPolicy<T> {

    /** The default uniform jitter range: +/- 100 milliseconds. */
    public static final long DEFAULT_RANDOM_RANGE_MILLIS = 100;

    /** The absolute maximum range for the uniform random jitter. */
    private final Duration range;

    /**
     * Creates a uniform random delay policy with the default range (100ms).
     *
     * @param target the underlying delay policy to wrap
     */
    public UniformRandomDelayPolicy(DelayPolicy<? super T> target) {
        this(target, DEFAULT_RANDOM_RANGE_MILLIS);
    }

    /**
     * Creates a uniform random delay policy with the default range (100ms) and a custom 
     * random number generator.
     *
     * @param target the underlying delay policy to wrap
     * @param random the random number generator to use for jitter
     */
    public UniformRandomDelayPolicy(DelayPolicy<? super T> target, Random random) {
        this(target, DEFAULT_RANDOM_RANGE_MILLIS, random);
    }

    /**
     * Creates a uniform random delay policy with the specified range in milliseconds.
     *
     * @param target the underlying delay policy to wrap
     * @param range  the maximum jitter range in milliseconds (jitter will be between {@code -range} and {@code +range})
     */
    public UniformRandomDelayPolicy(DelayPolicy<? super T> target, long range) {
        this(target, Duration.ofMillis(range));
    }

    /**
     * Creates a uniform random delay policy with the specified duration range.
     *
     * @param target the underlying delay policy to wrap
     * @param range  the maximum jitter duration range
     * @throws IllegalArgumentException if the range is not strictly positive
     */
    public UniformRandomDelayPolicy(DelayPolicy<? super T> target, Duration range) {
        super(target);
        if (!DelayPolicy.isValid(range)) {
            throw new IllegalArgumentException("Range must be positive but was: " + range);
        }
        this.range = range;
    }

    /**
     * Creates a uniform random delay policy with the specified range in milliseconds and a 
     * custom random number generator.
     *
     * @param target the underlying delay policy to wrap
     * @param range  the maximum jitter range in milliseconds
     * @param random the random number generator to use for jitter
     */
    public UniformRandomDelayPolicy(DelayPolicy<? super T> target, long range, Random random) {
        this(target, Duration.ofMillis(range), random);
    }

    /**
     * Creates a uniform random delay policy with the specified duration range and a custom 
     * random number generator.
     *
     * @param target the underlying delay policy to wrap
     * @param range  the maximum jitter duration range
     * @param random the random number generator to use for jitter
     * @throws IllegalArgumentException if the range is not strictly positive
     */
    public UniformRandomDelayPolicy(DelayPolicy<? super T> target, Duration range, Random random) {
        super(target, random);
        if (!DelayPolicy.isValid(range)) {
            throw new IllegalArgumentException("Range must be positive but was: " + range);
        }
        this.range = range;
    }

    /**
     * Adds uniform random jitter to the base delay amount.
     * <p>
     * The jitter is calculated by extracting the range in the current dimension, generating 
     * a uniform random value between {@code -range} and {@code +range}, and adding it to the 
     * base amount. The result is clamped to a minimum of 0.
     * </p>
     *
     * @param amount     the base delay amount
     * @param randomizer a random value between 0.0 and 1.0
     * @param dimIdx     the dimension index (unused directly, but passed to range extraction)
     * @return the jittered delay amount
     */
    @Override
    long addRandomJitter(long amount, double randomizer, int dimIdx) {
        long rangeNormalized = DurationCalcs.safeExtractAmount(range, dimIdx);
        double uniformRandom = (1 - randomizer * 2) * rangeNormalized;
        return Math.max(0, (long) (amount + uniformRandom));
    }

    /**
     * Checks whether adding the uniform random jitter would cause numeric overflow.
     *
     * @param amount     the base delay amount
     * @param randomizer a random value between 0.0 and 1.0
     * @param dimIdx     the dimension index
     * @return {@code true} if the jittered amount would not overflow, {@code false} otherwise
     */
    @Override
    boolean checkBounds(long amount, double randomizer, int dimIdx) {
        long rangeNormalized = DurationCalcs.safeExtractAmount(range, dimIdx);
        double uniformRandom = (1 - randomizer * 2) * rangeNormalized;
        if (uniformRandom < 0) {
            return -Long.MAX_VALUE + Math.abs(amount) < uniformRandom;
        } else {
            return Long.MAX_VALUE - Math.abs(amount) > uniformRandom;
        }
    }
}