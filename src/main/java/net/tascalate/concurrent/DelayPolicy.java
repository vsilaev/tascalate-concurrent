/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * 
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/Backoff.java 
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
package net.tascalate.concurrent;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import net.tascalate.concurrent.delays.BoundedMaxDelayPolicy;
import net.tascalate.concurrent.delays.BoundedMinDelayPolicy;
import net.tascalate.concurrent.delays.ExponentialDelayPolicy;
import net.tascalate.concurrent.delays.FirstRetryNoDelayPolicy;
import net.tascalate.concurrent.delays.FixedIntervalDelayPolicy;
import net.tascalate.concurrent.delays.ProportionalRandomDelayPolicy;
import net.tascalate.concurrent.delays.UniformRandomDelayPolicy;

public interface DelayPolicy {
    public static final DelayPolicy DEFAULT = new FirstRetryNoDelayPolicy(new FixedIntervalDelayPolicy());
    public static final DelayPolicy INVALID = ctx -> Timeouts.NEGATIVE_DURATION;
    
    Duration delay(RetryContext context);
    
    public static DelayPolicy fixedInterval() {
    	return new FixedIntervalDelayPolicy();
    }
    
    public static DelayPolicy fixedInterval(Duration interval) {
        return new FixedIntervalDelayPolicy(interval);
    }
    
    public static DelayPolicy fixedInterval(long interval, TimeUnit timeUnit) {
        return fixedInterval(Timeouts.toDuration(interval, timeUnit));
    }
    
    public static DelayPolicy fixedInterval(long intervalMillis) {
    	return fixedInterval(Duration.ofMillis(intervalMillis));
    }
    
    public static DelayPolicy exponential(double multiplier) {
    	return new ExponentialDelayPolicy(multiplier);
    }
    
    public static DelayPolicy exponential(Duration initialDelay, double multiplier) {
        return new ExponentialDelayPolicy(initialDelay, multiplier);
    }

    public static DelayPolicy exponential(long initialDelay, TimeUnit timeUnit, double multiplier) {
        return exponential(Timeouts.toDuration(initialDelay, timeUnit), multiplier);
    }
    
    public static DelayPolicy exponential(long initialDelayMillis, double multiplier) {
    	return exponential(Duration.ofMillis(initialDelayMillis), multiplier);
    }
    
    default DelayPolicy withUniformJitter() {
        return new UniformRandomDelayPolicy(this);
    }

    default DelayPolicy withUniformJitter(long range) {
        return withUniformJitter(range, TimeUnit.MILLISECONDS);
    }
    
    default DelayPolicy withUniformJitter(long range, TimeUnit timeUnit) {
        return withUniformJitter(Timeouts.toDuration(range, timeUnit));
    }
    
    default DelayPolicy withUniformJitter(Duration range) {
        return new UniformRandomDelayPolicy(this, range);
    }

    default DelayPolicy withProportionalJitter() {
        return new ProportionalRandomDelayPolicy(this);
    }

    default DelayPolicy withProportionalJitter(double multiplier) {
        return new ProportionalRandomDelayPolicy(this, multiplier);
    }
    
    default DelayPolicy withMinDelay() {
        return new BoundedMinDelayPolicy(this);
    }

    default DelayPolicy withMinDelay(Duration minDelay) {
        return new BoundedMinDelayPolicy(this, minDelay);
    }

    default DelayPolicy withMinDelay(long minDelay, TimeUnit timeUnit) {
        return withMaxDelay(Timeouts.toDuration(minDelay, timeUnit));
    }
    
    default DelayPolicy withMinDelay(long minDelayMillis) {
        return withMinDelay(Duration.ofMillis(minDelayMillis));
    }

    default DelayPolicy withMaxDelay() {
        return new BoundedMaxDelayPolicy(this);
    }

    default DelayPolicy withMaxDelay(Duration maxDelay) {
        return new BoundedMaxDelayPolicy(this, maxDelay);
    }
    
    default DelayPolicy withMaxDelay(long maxDelay, TimeUnit timeUnit) {
        return withMaxDelay(Timeouts.toDuration(maxDelay, timeUnit));
    }
    
    default DelayPolicy withMaxDelay(long maxDelayMillis) {
        return withMaxDelay(Duration.ofMillis(maxDelayMillis));
    }

    default DelayPolicy withFirstRetryNoDelay() {
        return new FirstRetryNoDelayPolicy(this);
    }
    
    public static boolean isValid(Duration d) {
        return !(d.isNegative() || d.isZero());
    }

}
