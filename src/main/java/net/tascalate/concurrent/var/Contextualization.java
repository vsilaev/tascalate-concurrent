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

import java.util.concurrent.Callable;
import java.util.function.Supplier;

class Contextualization<T> {
    private final ContextVar<T> contextVar;
    private final T capturedContext;
    
    Contextualization(ContextVar<T> contextVar, 
                      T capturedContext) {
        
        this.contextVar = null == contextVar ? ContextVar.empty() : contextVar; 
        this.capturedContext = capturedContext;        
    }
    
    void runWith(Runnable code) {
        contextVar.runWith(capturedContext, code);
    }
    
    <V> V supplyWith(Supplier<V> code) {
        return contextVar.supplyWith(capturedContext, code);
    }
    
    <V> V callWith(Callable<V> code) throws Exception {
        return contextVar.callWith(capturedContext, code);        
    }
}
