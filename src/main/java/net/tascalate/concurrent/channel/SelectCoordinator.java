/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.concurrent.channel;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public abstract class SelectCoordinator {
    
    SelectCoordinator() {
        
    }
    
    abstract boolean tryWin();
    
    static SelectCoordinator createFirstWins() {
        return new FirstWins();
    }
    
    static SelectCoordinator anyWins() {
        return ANY_WINS;
    }
    
    private static SelectCoordinator ANY_WINS = new SelectCoordinator() {
        @Override
        boolean tryWin() {
            return true;
        }
    }; 
    
    static final class FirstWins extends SelectCoordinator {
        
        private static final AtomicIntegerFieldUpdater<FirstWins> UPDATER = 
                AtomicIntegerFieldUpdater.newUpdater(FirstWins.class, "victory");
        
        // 0 = false, 1 = true
        @SuppressWarnings("unused")
        private volatile int victory = 0;
        
        @Override
        boolean tryWin() {
            return UPDATER.compareAndSet(this, 0, 1);
        }
    }
}
