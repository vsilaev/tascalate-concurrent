/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * 
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/ExponentialDelayBackoff.java 
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

import net.tascalate.concurrent.DelayPolicy;
import net.tascalate.concurrent.RetryContext;

public class ExponentialDelayPolicy implements DelayPolicy {
    private final long initialDelayMillis;
    private final double multiplier;

    public ExponentialDelayPolicy(double multiplier) {
    	this(FixedIntervalDelayPolicy.DEFAULT_PERIOD_MILLIS, multiplier);
    }
    
    public ExponentialDelayPolicy(long initialDelayMillis, double multiplier) {
        if (initialDelayMillis <= 0) {
            throw new IllegalArgumentException("Initial delay must be positive but was: " + initialDelayMillis);
        }
        this.initialDelayMillis = initialDelayMillis;
        this.multiplier = multiplier;
    }

    @Override
    public long delayMillis(RetryContext context) {
        return (long) (initialDelayMillis * Math.pow(multiplier, context.getRetryCount()));
    }
}
