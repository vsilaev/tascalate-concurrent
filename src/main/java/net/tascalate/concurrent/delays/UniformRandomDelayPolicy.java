/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * 
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/UniformRandomBackoff.java
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
package net.tascalate.concurrent.delays;

import java.time.Duration;
import java.util.Random;

import net.tascalate.concurrent.DelayPolicy;

public class UniformRandomDelayPolicy<T> extends RandomDelayPolicy<T> {
    /**
     * Randomly between +/- 100ms
     */
    public static final long DEFAULT_RANDOM_RANGE_MILLIS = 100;

    private final Duration range;

    public UniformRandomDelayPolicy(DelayPolicy<? super T> target) {
        this(target, DEFAULT_RANDOM_RANGE_MILLIS);
    }

    public UniformRandomDelayPolicy(DelayPolicy<? super T> target, Random random) {
        this(target, DEFAULT_RANDOM_RANGE_MILLIS, random);
    }

    public UniformRandomDelayPolicy(DelayPolicy<? super T> target, long range) {
        this(target, Duration.ofMillis(range));
    }
    
    public UniformRandomDelayPolicy(DelayPolicy<? super T> target, Duration range) {
        super(target);
        if (!DelayPolicy.isValid(range)) {
            throw new IllegalArgumentException("Range must be positive but was: " + range);
        }
        this.range = range;
    }

    public UniformRandomDelayPolicy(DelayPolicy<? super T> target, long range, Random random) {
        this(target, Duration.ofMillis(range), random);
    }
    
    public UniformRandomDelayPolicy(DelayPolicy<? super T> target, Duration range, Random random) {
        super(target, random);
        if (!DelayPolicy.isValid(range)) {
            throw new IllegalArgumentException("Range must be positive but was: " + range);
        }
        this.range = range;
    }

    @Override
    long addRandomJitter(long amount, double randomizer, int dimIdx) {
        long rangeNormalized = DurationCalcs.safeExtractAmount(range, dimIdx);
        double uniformRandom = (1 - randomizer * 2) * rangeNormalized;
        return Math.max(0, (long) (amount + uniformRandom));
    }

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
