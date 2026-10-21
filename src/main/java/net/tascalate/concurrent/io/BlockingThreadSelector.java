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
package net.tascalate.concurrent.io;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

/**
 * Internal hack leveraging {@link AbstractSelector#begin()} and 
 * {@link AbstractSelector#end()} mechanics to make legacy blocking I/O 
 * responsive to {@link Thread#interrupt()}.
 *
 * <h2>How it works</h2>
 * <p>
 * When {@link #enter()} is called, it invokes {@code begin()}, which registers 
 * the current thread with the JVM's internal interruption mechanism. If the 
 * thread is subsequently interrupted, the JVM will invoke {@link #wakeup()} on 
 * all selectors registered to that thread.
 * </p>
 * <p>
 * This class overrides {@link #wakeup()} to execute a custom 
 * {@code interruptHandler} (provided by {@link BlockingIO}), which forcefully 
 * closes all registered {@link AutoCloseable} resources, thereby unblocking 
 * the I/O operation and allowing the thread to proceed.
 * </p>
 *
 * <h2>Limitations</h2>
 * <p>
 * This is <b>not</b> a real {@link Selector}. All standard selector operations 
 * ({@link #select()}, {@link #keys()}, etc.) throw 
 * {@link UnsupportedOperationException}. It exists solely as a registration 
 * mechanism for thread interruption.
 * </p>
 *
 * @author vsilaev
 * @see BlockingIO
 */
final class BlockingThreadSelector extends AbstractSelector {
    private final Runnable interruptHandler;

    /**
     * Creates a new blocking thread selector.
     *
     * @param interruptHandler the action to execute when the thread is interrupted
     */
    BlockingThreadSelector(Runnable interruptHandler) {
        // Pass the default provider to avoid potential NPEs in some JDK implementations
        super(SelectorProvider.provider());
        this.interruptHandler = interruptHandler;
    }

    /**
     * Registers the current thread for interruption by calling 
     * {@link AbstractSelector#begin()}.
     */
    void enter() { 
        begin(); 
    }

    /**
     * Unregisters the current thread from interruption by calling 
     * {@link AbstractSelector#end()}.
     */
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

    /**
     * Overrides the standard wakeup mechanism to trigger the custom 
     * interruption handler instead of waking up a selection operation.
     *
     * @return this selector
     */
    @Override
    public Selector wakeup() {
        interruptHandler.run();
        return this;
    }

    private static <T> T unimplemented() {
        throw new UnsupportedOperationException("This selector is only used for thread interruption mechanics, not actual selection.");
    }
}