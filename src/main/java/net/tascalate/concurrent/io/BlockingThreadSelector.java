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
package net.tascalate.concurrent.io;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.util.Set;

final class BlockingThreadSelector extends AbstractSelector {
    
    private final Runnable interruptHandler;

    BlockingThreadSelector(Runnable interruptHandler) {
        super(null);
        this.interruptHandler = interruptHandler;
    }

    void enter() { 
        begin(); 
    }
    
    void exit() { 
        end();
    }

    @Override
    protected void implCloseSelector() {
        
    }

    @Override
    protected SelectionKey register(AbstractSelectableChannel ch, int ops, Object att) {
        return unimplemented();
    }

    @Override
    public Set<SelectionKey> keys() {
        return unimplemented();
    }

    @Override
    public int select() {
        return unimplemented();
    }

    @Override
    public int select(long timeout) {
        return unimplemented();
    }

    @Override
    public int selectNow() {
        return unimplemented();
    }

    @Override
    public Set<SelectionKey> selectedKeys() {
        return unimplemented();
    }

    @Override
    public Selector wakeup() {
        interruptHandler.run();
        return this;
    }
    
    private static <T> T unimplemented() {
        throw new UnsupportedOperationException();
    }
}
