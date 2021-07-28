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
package net.tascalate.concurrent.var;

import java.util.List;
import java.util.concurrent.Executor;

class ContextualExecutor<D extends Executor> implements Executor {
    protected final D delegate;
    protected final Contextualization ctxz;
    
    ContextualExecutor(D delegate, Contextualization ctxz) {
        this.delegate = delegate;
        this.ctxz = ctxz;
    }
    
    @Override
    public void execute(Runnable command) {
        delegate.execute(contextualRunnable(command));
    }

    Runnable contextualRunnable(Runnable original) {
        return () -> {
            List<Object> originalContext = ctxz.enter(); 
            try {
                original.run();
            } finally {
                ctxz.exit(originalContext);
            }            
        };
    }
}
