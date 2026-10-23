/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * 
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/ProportionalRandomBackoff.java 
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

import java.util.Random;

import net.tascalate.concurrent.DelayPolicy;

/**
 * A delay policy decorator that adds proportional random jitter to the target policy's delay.
 * <p>
 * Unlike {@link UniformRandomDelayPolicy} (which adds a fixed-range jitter), this policy
 * adds jitter that is <i>proportional</i> to the base delay. For example, with a multiplier
 * of 0.1 (the default), a base delay of 1000ms will be jittered by ±100ms (i.e., between
 * 900ms and 1100ms), while a base delay of 5000ms will be jittered by ±500ms (i.e., between
 * 4500ms and 5500ms).
 * </p>
 * <p>
 * This is particularly useful when combined with exponential backoff: as the base delay grows,
 * the jitter range grows proportionally, preventing synchronized "thundering herd" retries
 * across multiple clients while maintaining the overall backoff curve.
 * </p>
 *
 * @param <T> the type of the result being retried
 * @author vsilaev
 * @see DelayPolicy#withProportionalJitter()
 * @see UniformRandomDelayPolicy
 */
public class ProportionalRandomDelayPolicy<T> extends RandomDelayPolicy<T> {

    /**
     * The default jitter multiplier: 0.1 (i.e., ±10% of the base delay).
     */
    public static final double DEFAULT_MULTIPLIER = 0.1;

    /** The jitter multiplier (e.g., 0.1 for ±10%). */
    private final double multiplier;

    /**
     * Creates a proportional random delay policy with the default multiplier (0.1).
     *
     * @param target the underlying delay policy to wrap
     */
    public ProportionalRandomDelayPolicy(DelayPolicy<? super T> target) {
        this(target, DEFAULT_MULTIPLIER);
    }

    /**
     * Creates a proportional random delay policy with the default multiplier (0.1)
     * and a custom random number generator.
     *
     * @param target the underlying delay policy to wrap
     * @param random the random number generator to use for jitter
     */
    public ProportionalRandomDelayPolicy(DelayPolicy<? super T> target, Random random) {
        this(target, DEFAULT_MULTIPLIER, random);
    }

    /**
     * Creates a proportional random delay policy with the specified multiplier.
     *
     * @param target     the underlying delay policy to wrap
     * @param multiplier the jitter multiplier (e.g., 0.1 for ±10%); must be positive
     * @throws IllegalArgumentException if the multiplier is not positive
     */
    public ProportionalRandomDelayPolicy(DelayPolicy<? super T> target, double multiplier) {
        super(target);
        if (multiplier <= 0) {
            throw new IllegalArgumentException("Multiplier must be a positive number but was: " + multiplier);
        }
        this.multiplier = multiplier;
    }

    /**
     * Creates a proportional random delay policy with the specified multiplier and
     * a custom random number generator.
     *
     * @param target     the underlying delay policy to wrap
     * @param multiplier the jitter multiplier (e.g., 0.1 for ±10%); must be positive
     * @param random     the random number generator to use for jitter
     * @throws IllegalArgumentException if the multiplier is not positive
     */
    public ProportionalRandomDelayPolicy(DelayPolicy<? super T> target, double multiplier, Random random) {
        super(target, random);
        if (multiplier <= 0) {
            throw new IllegalArgumentException("Multiplier must be a positive number but was: " + multiplier);
        }
        this.multiplier = multiplier;
    }

    /**
     * Adds proportional random jitter to the base delay amount.
     * <p>
     * The jitter is calculated as: {@code amount * (1 + (1 - 2 * randomizer) * multiplier)},
     * where {@code randomizer} is a value between 0.0 and 1.0. This produces a result
     * in the range {@code [amount * (1 - multiplier), amount * (1 + multiplier)]}.
     * </p>
     *
     * @param amount     the base delay amount
     * @param randomizer a random value between 0.0 and 1.0
     * @param dimIdx     the dimension index (unused)
     * @return the jittered delay amount
     */
    @Override
    long addRandomJitter(long amount, double randomizer, int dimIdx) {
        double randomMultiplier = (1 - 2 * randomizer) * multiplier;
        return Math.max(0, (long) (amount * (1 + randomMultiplier)));
    }

    /**
     * Checks whether applying the proportional jitter would cause numeric overflow.
     *
     * @param amount     the base delay amount
     * @param randomizer a random value between 0.0 and 1.0
     * @param dimIdx     the dimension index (unused)
     * @return {@code true} if the jittered amount would not overflow, {@code false} otherwise
     */
    @Override
    boolean checkBounds(long amount, double randomizer, int dimIdx) {
        double randomMultiplier = (1 - 2 * randomizer) * multiplier;
        return (double)Long.MAX_VALUE / Math.abs(amount) > Math.abs(1 + randomMultiplier);
    }
}