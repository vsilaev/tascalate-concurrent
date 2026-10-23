/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * This class is based on the work create by Tomasz Nurkiewicz
 * under the Apache License, Version 2.0. Please see
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/OnFailureNoDelayBackoff.java
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

import net.tascalate.concurrent.DelayPolicy;
import net.tascalate.concurrent.RetryContext;

/**
 * A delay policy decorator that skips the delay when the previous attempt failed
 * with an exception.
 * <p>
 * This is useful in "fail-fast" retry scenarios where you want to retry immediately
 * after an exception (e.g., a connection reset), but apply normal backoff delays
 * when the previous attempt succeeded but returned an invalid result. It wraps a
 * target {@link DelayPolicy} and returns {@link Duration#ZERO} when the retry
 * context contains a non-null last error.
 * </p>
 *
 * @param <T> the type of the result being retried
 * @author vsilaev
 * @see DelayPolicy#withOnFailureNoDelay()
 */
public class OnFailureNoDelayPolicy<T> extends DelayPolicyWrapper<T> {

    /**
     * Creates an on-failure-no-delay policy wrapping the specified target policy.
     *
     * @param target the underlying delay policy to wrap
     */
    public OnFailureNoDelayPolicy(DelayPolicy<? super T> target) {
        super(target);
    }

    /**
     * Returns {@link Duration#ZERO} if the previous attempt failed with an exception,
     * otherwise delegates to the target policy.
     *
     * @param context the current retry context
     * @return zero duration if the last error is non-null, or the target policy's delay otherwise
     */
    @Override
    public Duration delay(RetryContext<? extends T> context) {
        return context.getLastError() != null ? Duration.ZERO : target.delay(context);
    }
}