/**
 * Original work: copyright 2013 Tomasz Nurkiewicz
 * https://github.com/nurkiewicz/async-retry
 * 
 * This class is based on the work create by Tomasz Nurkiewicz 
 * under the Apache License, Version 2.0. Please see 
 * https://github.com/nurkiewicz/async-retry/blob/master/src/main/java/com/nurkiewicz/asyncretry/backoff/BackoffWrapper.java 
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
package net.tascalate.concurrent.delays;

import java.time.Duration;
import java.util.Objects;

import net.tascalate.concurrent.DelayPolicy;

public abstract class DelayPolicyWrapper implements DelayPolicy {

    protected final DelayPolicy target;

    public DelayPolicyWrapper(DelayPolicy target) {
        this.target = Objects.requireNonNull(target);
    }
    
    
    protected static Duration max(Duration a, Duration b) {
        return a.compareTo(b) > 0 ? a : b;
    }
    
    protected static Duration min(Duration a, Duration b) {
        return a.compareTo(b) < 0 ? a : b;
    }
}
