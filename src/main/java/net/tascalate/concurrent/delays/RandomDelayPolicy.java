/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * 
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/RandomBackoff.java
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import net.tascalate.concurrent.DelayPolicy;
import net.tascalate.concurrent.RetryContext;

/**
 * Abstract base class for delay policies that add random jitter to a target policy's delay.
 * <p>
 * This class wraps a target {@link DelayPolicy} and applies a random variation to its 
 * computed delay on every retry attempt. It uses {@link DurationCalcs#safeTransform} to 
 * ensure that the jittered delay never exceeds {@link Long#MAX_VALUE}, preventing numeric 
 * overflow even when combined with aggressive exponential backoff.
 * </p>
 * <p>
 * Subclasses must implement {@link #addRandomJitter(long, double, int)} to define the 
 * specific jitter algorithm (e.g., uniform or proportional) and {@link #checkBounds(long, double, int)} 
 * to provide the corresponding overflow check.
 * </p>
 *
 * @param <T> the type of the result being retried
 * @author vsilaev
 * @see UniformRandomDelayPolicy
 * @see ProportionalRandomDelayPolicy
 */
abstract public class RandomDelayPolicy<T> extends DelayPolicyWrapper<T> {

    /** Supplier for the random number generator, defaulting to {@link ThreadLocalRandom#current()}. */
    private final Supplier<Random> randomSource;

    /**
     * Creates a random delay policy using the default {@link ThreadLocalRandom} source.
     *
     * @param target the underlying delay policy to wrap
     */
    protected RandomDelayPolicy(DelayPolicy<? super T> target) {
        this(target, ThreadLocalRandom::current);
    }

    /**
     * Creates a random delay policy using a specific {@link Random} instance.
     *
     * @param target       the underlying delay policy to wrap
     * @param randomSource the random number generator to use for jitter
     */
    protected RandomDelayPolicy(DelayPolicy<? super T> target, Random randomSource) {
        this(target, () -> randomSource);
    }

    /**
     * Creates a random delay policy using a {@link Supplier} for the random number generator.
     * <p>
     * This constructor is useful for lazy initialization or when the random source needs 
     * to be resolved dynamically on each invocation.
     * </p>
     *
     * @param target       the underlying delay policy to wrap
     * @param randomSource the supplier providing the random number generator
     */
    private RandomDelayPolicy(DelayPolicy<? super T> target, Supplier<Random> randomSource) {
        super(target);
        this.randomSource = randomSource;
    }

    /**
     * Calculates the jittered delay by applying a random variation to the target policy's delay.
     * <p>
     * The method generates a randomizer value between 0.0 and 1.0, then delegates to the 
     * subclass's {@link #addRandomJitter} and {@link #checkBounds} methods via 
     * {@link DurationCalcs#safeTransform} to ensure the result is both randomized and 
     * overflow-safe.
     * </p>
     *
     * @param context the current retry context
     * @return the jittered delay duration
     */
    @Override
    public Duration delay(RetryContext<? extends T> context) {
        double randomizer = random().nextDouble();
        return DurationCalcs.safeTransform(
            target.delay(context), 
            (amount, dimIdx) -> DurationCalcs.toBoolean(checkBounds(amount, randomizer, (int)dimIdx)), 
            (amount, dimIdx) -> addRandomJitter(amount, randomizer, (int)dimIdx)
        );
    }

    /**
     * Applies the specific random jitter algorithm to the base delay amount.
     *
     * @param amount     the base delay amount (in the given dimension)
     * @param randomizer a random value between 0.0 and 1.0
     * @param dimIdx     the dimension index (e.g., seconds, milliseconds) of the amount
     * @return the jittered delay amount
     */
    abstract long addRandomJitter(long amount, double randomizer, int dimIdx);

    /**
     * Checks whether applying the random jitter to the base amount would cause numeric overflow.
     *
     * @param amount     the base delay amount (in the given dimension)
     * @param randomizer a random value between 0.0 and 1.0
     * @param dimIdx     the dimension index (e.g., seconds, milliseconds) of the amount
     * @return {@code true} if the jittered amount is safe from overflow, {@code false} otherwise
     */
    abstract boolean checkBounds(long amount, double randomizer, int dimIdx);

    /**
     * Returns the next {@link Random} instance from the configured supplier.
     *
     * @return the random number generator
     */
    protected Random random() {
        return randomSource.get();
    }
}