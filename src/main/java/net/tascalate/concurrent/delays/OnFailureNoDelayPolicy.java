/**
 * Copyright 2015-2020 Valery Silaev (http://vsilaev.com)
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

public class OnFailureNoDelayPolicy<T> extends DelayPolicyWrapper<T> {
    
    public OnFailureNoDelayPolicy(DelayPolicy<? super T> target) {
        super(target);
    }

    @Override
    public Duration delay(RetryContext<? extends T> context) {
        return context.getLastError() != null ? Duration.ZERO : target.delay(context);
    }
}
