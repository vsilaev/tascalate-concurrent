/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * 
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/BoundedMaxBackoff.java 
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

import java.time.Duration;

import net.tascalate.concurrent.DelayPolicy;
import net.tascalate.concurrent.RetryContext;

public class BoundedMaxDelayPolicy extends DelayPolicyWrapper {

    public static final long DEFAULT_MAX_DELAY_MILLIS = 10_000;

    private final Duration maxDelay;

    public BoundedMaxDelayPolicy(DelayPolicy target) {
        this(target, DEFAULT_MAX_DELAY_MILLIS);
    }

    public BoundedMaxDelayPolicy(DelayPolicy target, long maxDelayMillis) {
        this(target, Duration.ofMillis(maxDelayMillis));
    }
    
    public BoundedMaxDelayPolicy(DelayPolicy target, Duration maxDelay) {
        super(target);
        if (!DelayPolicy.isValid(maxDelay)) {
            throw new IllegalArgumentException("MaxDelay must be positive but was: " + maxDelay);
        }
        this.maxDelay = maxDelay;
    }

    @Override
    public Duration delay(RetryContext context) {
        return min(target.delay(context), maxDelay);
    }
}
