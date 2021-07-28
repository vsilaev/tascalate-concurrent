/**
 * Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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

class Contextualization {
    private final List<ContextVar<?>> contextVars;
    private final ContextTrampoline.Propagation propagation;
    private final List<Object> capturedContext;
    
    Contextualization(List<ContextVar<?>> contextVars, 
                      ContextTrampoline.Propagation propagation, 
                      List<Object> capturedContext) {
        
        this.contextVars = null == contextVars ? 
            Collections.emptyList() : 
            Collections.unmodifiableList(contextVars);
            
        this.propagation = propagation == null ? ContextTrampoline.Propagation.OPTIMIZED : propagation;
        
        this.capturedContext = null == capturedContext ?
            Collections.emptyList() : 
            Collections.unmodifiableList(capturedContext);        
    }
    
    List<Object> enter() {
        List<Object> previousContextState = ContextTrampoline.Propagation.STRICT.equals(propagation) ? 
            ContextTrampoline.captureContext(contextVars) : Collections.nCopies(contextVars.size(), null);
        restoreContext(capturedContext);
        return previousContextState;
    }
    
    void exit(List<Object> previousContextState) {
        restoreContext(previousContextState);
    }
    
    private void restoreContext(List<Object> contextState) {
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
