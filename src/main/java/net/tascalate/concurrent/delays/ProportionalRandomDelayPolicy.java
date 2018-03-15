/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * 
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/ProportionalRandomBackoff.java 
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

public class ProportionalRandomDelayPolicy extends RandomDelayPolicy {
    /**
     * Randomly up to +/- 10%
     */
    public static final double DEFAULT_MULTIPLIER = 0.1;

    private final double multiplier;

    public ProportionalRandomDelayPolicy(DelayPolicy target) {
        this(target, DEFAULT_MULTIPLIER);
    }

    public ProportionalRandomDelayPolicy(DelayPolicy target, Random random) {
        this(target, DEFAULT_MULTIPLIER, random);
    }

    public ProportionalRandomDelayPolicy(DelayPolicy target, double multiplier) {
        super(target);
        if (multiplier <= 0) {
            throw new IllegalArgumentException("Multiplier must be a positive number but was: " + multiplier);
        }
        this.multiplier = multiplier;
    }

    public ProportionalRandomDelayPolicy(DelayPolicy target, double multiplier, Random random) {
        super(target, random);
        if (multiplier <= 0) {
            throw new IllegalArgumentException("Multiplier must be a positive number but was: " + multiplier);
        }
        this.multiplier = multiplier;
    }

    @Override
    long addRandomJitter(long initialDelay, double randomizer, int dimIdx) {
        double randomMultiplier = (1 - 2 * randomizer) * multiplier;
        return Math.max(0, (long) (initialDelay * (1 + randomMultiplier)));
    }
    
    @Override
    boolean checkBounds(long initialDelay, double randomizer, int dimIdx) {
        double randomMultiplier = (1 - 2 * randomizer) * multiplier;
        return Long.MAX_VALUE / initialDelay > 1 + randomMultiplier;
    }
}
