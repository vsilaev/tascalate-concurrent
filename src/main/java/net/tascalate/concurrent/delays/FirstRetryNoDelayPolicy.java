/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/FirstRetryNoDelayBackoff.java
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
 * A delay policy decorator that skips the delay for the initial retry attempt.
 * <p>
 * This is useful when you want to execute initial iteration immediately, but 
 * apply normal backoff delays for subsequent retries.
 * It wraps a target {@link DelayPolicy} and returns {@link Duration#ZERO} when the
 * retry count is 0, delegating to the target policy (with an adjusted retry count)
 * for all subsequent attempts.
 * </p>
 *
 * @param <T> the type of the result being retried
 * @author vsilaev
 * @see DelayPolicy#withFirstRetryNoDelay()
 */
public class FirstRetryNoDelayPolicy<T> extends DelayPolicyWrapper<T> {

    /**
     * Creates a first-retry-no-delay policy wrapping the specified target policy.
     *
     * @param target the underlying delay policy to wrap
     */
    public FirstRetryNoDelayPolicy(DelayPolicy<? super T> target) {
        super(target);
    }

    /**
     * Returns {@link Duration#ZERO} for the first retry (retry count 0), otherwise
     * delegates to the target policy with the retry count decremented by 1 so that
     * the target policy sees the "effective" retry number.
     *
     * @param context the current retry context
     * @return zero duration for the first retry, or the target policy's delay otherwise
     */
    @Override
    public Duration delay(RetryContext<? extends T> context) {
        if (context.getRetryCount() == 0) {
            return Duration.ZERO;
        } else {
            return target.delay(context.overrideRetryCount(context.getRetryCount() - 1));
        }
    }
}