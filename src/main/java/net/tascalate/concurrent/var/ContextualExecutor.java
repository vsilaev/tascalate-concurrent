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

public class ContextualExecutor<D extends Executor> extends ContextualObject implements Executor {
    protected final D delegate;
    
    protected ContextualExecutor(D delegate, 
                                 List<ContextVar<?>> contextVars, 
                                 ContextVar.Propagation propagation, 
                                 List<Object> capturedContext) {
        
        super(contextVars, propagation, capturedContext);
        this.delegate = delegate;
    }
    
    @Override
    public void execute(Runnable command) {
        delegate.execute(contextualRunnable(command));
    }

    protected Runnable contextualRunnable(Runnable original) {
        return () -> {
            List<Object> originalContext = applyCapturedContext(); 
            try {
                original.run();
            } finally {
                restoreContextVars(originalContext);
            }            
        };
    }
}
