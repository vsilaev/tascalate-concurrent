/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * 
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/ExponentialDelayBackoff.java 
 * 
 * Modified work: copyright 2015-2020 Valery Silaev (http://vsilaev.com)
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

public class ExponentialDelayPolicy<T> implements DelayPolicy<T> {
    private final Duration initialDelay;
    private final double multiplier;

    public ExponentialDelayPolicy(double multiplier) {
    	this(FixedIntervalDelayPolicy.DEFAULT_PERIOD_MILLIS, multiplier);
    }
    
    public ExponentialDelayPolicy(long initialDelayMillis, double multiplier) {
        this(Duration.ofMillis(initialDelayMillis), multiplier);
    }
    
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
