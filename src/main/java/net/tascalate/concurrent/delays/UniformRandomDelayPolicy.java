/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * 
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/UniformRandomBackoff.java
 * 
 * Modified work: copyright 2015-2017 Valery Silaev (http://vsilaev.com)
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

public class UniformRandomDelayPolicy extends RandomDelayPolicy {
    /**
     * Randomly between +/- 100ms
     */
    public static final long DEFAULT_RANDOM_RANGE_MILLIS = 100;

    private final long range;

    public UniformRandomDelayPolicy(DelayPolicy target) {
        this(target, DEFAULT_RANDOM_RANGE_MILLIS);
    }

    public UniformRandomDelayPolicy(DelayPolicy target, Random random) {
        this(target, DEFAULT_RANDOM_RANGE_MILLIS, random);
    }

    public UniformRandomDelayPolicy(DelayPolicy target, final long range) {
        super(target);
        this.range = range;
    }

    public UniformRandomDelayPolicy(DelayPolicy target, final long range, Random random) {
        super(target, random);
        this.range = range;
    }

    @Override
    long addRandomJitter(long initialDelay) {
        final double uniformRandom = (1 - random().nextDouble() * 2) * range;
        return (long) (initialDelay + uniformRandom);
    }

}
