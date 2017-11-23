/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * 
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/Backoff.java 
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
package net.tascalate.concurrent;

import net.tascalate.concurrent.delays.BoundedMaxDelayPolicy;
import net.tascalate.concurrent.delays.BoundedMinDelayPolicy;
import net.tascalate.concurrent.delays.FirstRetryNoDelayPolicy;
import net.tascalate.concurrent.delays.FixedIntervalDelayPolicy;
import net.tascalate.concurrent.delays.ProportionalRandomDelayPolicy;
import net.tascalate.concurrent.delays.UniformRandomDelayPolicy;

public interface DelayPolicy {
    public static final DelayPolicy DEFAULT = new FirstRetryNoDelayPolicy(new FixedIntervalDelayPolicy());
    public static final DelayPolicy INVALID_DELAY = ctx -> -1;
    
    abstract public long delayMillis(RetryContext context);
    
    default DelayPolicy withUniformJitter() {
        return new UniformRandomDelayPolicy(this);
    }

    default DelayPolicy withUniformJitter(long range) {
        return new UniformRandomDelayPolicy(this, range);
    }

    default DelayPolicy withProportionalJitter() {
        return new ProportionalRandomDelayPolicy(this);
    }

    default DelayPolicy withProportionalJitter(double multiplier) {
        return new ProportionalRandomDelayPolicy(this, multiplier);
    }

    default DelayPolicy withMinDelay(long minDelayMillis) {
        return new BoundedMinDelayPolicy(this, minDelayMillis);
    }

    default DelayPolicy withMinDelay() {
        return new BoundedMinDelayPolicy(this);
    }

    default DelayPolicy withMaxDelay(long maxDelayMillis) {
        return new BoundedMaxDelayPolicy(this, maxDelayMillis);
    }

    default DelayPolicy withMaxDelay() {
        return new BoundedMaxDelayPolicy(this);
    }

    default DelayPolicy withFirstRetryNoDelay() {
        return new FirstRetryNoDelayPolicy(this);
    }
}
