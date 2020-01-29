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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

abstract class ContextualObject {
    private final List<ContextVar<?>> contextVars;
    private final ContextVar.Propagation propagation;
    private final List<Object> capturedContext;
    
    protected ContextualObject(List<ContextVar<?>> contextVars, ContextVar.Propagation propagation, List<Object> capturedContext) {
        this.contextVars = null == contextVars ? 
            Collections.emptyList() : 
            Collections.unmodifiableList(contextVars);
            
        this.propagation = propagation == null ? ContextVar.Propagation.OPTIMIZED : propagation;
        
        this.capturedContext = null == capturedContext ?
            Collections.emptyList() : 
            Collections.unmodifiableList(capturedContext);        
    }
    
    protected final List<Object> applyCapturedContext() {
        List<Object> originalContext = ContextVar.Propagation.STRICT.equals(propagation) ? 
            captureContextVars(contextVars) : Collections.nCopies(contextVars.size(), null);
        restoreContextVars(capturedContext);
        return originalContext;
    }
    
    static List<Object> captureContextVars(List<? extends ContextVar<?>> contextVars) {
        return contextVars.stream().map(v -> v.get()).collect(Collectors.toList());
    }
    
    protected final void restoreContextVars(List<Object> contextState) {
        Iterator<? extends ContextVar<?>> vars = contextVars.iterator();
        Iterator<Object> values = contextState.iterator();
        while (vars.hasNext() && values.hasNext()) {
            @SuppressWarnings("unchecked")
            ContextVar<Object> contextVar = (ContextVar<Object>)vars.next();
            Object contextVal = values.next();
            if (null == contextVal) {
                contextVar.remove();
            } else {
                contextVar.set(contextVal);
            }
        }
    }    

}
