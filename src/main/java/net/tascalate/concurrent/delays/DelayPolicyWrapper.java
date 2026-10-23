/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * 
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/BackoffWrapper.java 
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
import java.util.Objects;

import net.tascalate.concurrent.DelayPolicy;

/**
 * Abstract base class for decorating or composing existing {@link DelayPolicy} instances.
 * <p>
 * This class implements the Decorator pattern, holding a reference to a target 
 * {@code DelayPolicy} and providing utility methods ({@link #max(Duration, Duration)} 
 * and {@link #min(Duration, Duration)}) that subclasses can use to modify the 
 * target's computed delay (e.g., to enforce minimum or maximum bounds).
 * </p>
 *
 * @param <T> the type of the result being retried
 * @author vsilaev
 * @see BoundedMinDelayPolicy
 * @see BoundedMaxDelayPolicy
 */
public abstract class DelayPolicyWrapper<T> implements DelayPolicy<T> {

    /** The underlying target delay policy being decorated. */
    protected final DelayPolicy<? super T> target;

    /**
     * Creates a new wrapper around the specified target policy.
     *
     * @param target the target delay policy to wrap; must not be {@code null}
     * @throws NullPointerException if the target is {@code null}
     */
    public DelayPolicyWrapper(DelayPolicy<? super T> target) {
        this.target = Objects.requireNonNull(target);
    }

    /**
     * Returns the greater of two durations.
     *
     * @param a the first duration
     * @param b the second duration
     * @return the maximum of {@code a} and {@code b}
     */
    protected static Duration max(Duration a, Duration b) {
        return a.compareTo(b) > 0 ? a : b;
    }

    /**
     * Returns the lesser of two durations.
     *
     * @param a the first duration
     * @param b the second duration
     * @return the minimum of {@code a} and {@code b}
     */
    protected static Duration min(Duration a, Duration b) {
        return a.compareTo(b) < 0 ? a : b;
    }
}