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

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Promises;
import net.tascalate.concurrent.Try;

/**
 * Read-only view of a channel.
 * Pass this to consumers to prevent them from accidentally sending.
 */
public interface ReceiveChannel<T> extends ChannelBase {
    /**
     * Receives a value. Returns a completed future with the value if available,
     * a pending future if the channel is empty, or a failed future if closed
     * in {@link CloseMode#FAIL_ALL}.
     * <p>
     * In {@link CloseMode#DRAIN} mode, returns {@code null} once fully drained.
     */
    Promise<T> receive();
    
    default Promise<T> receive(long millis) {
        return receive(Duration.ofMillis(millis));
    }
    
    /**
     * Receives a value, timing out if none arrives within the given duration.
     * If the timeout elapses, the returned Promise fails with a TimeoutException,
     * and the underlying receive operation is automatically cancelled, removing 
     * the waiter from the channel's internal queue.
     */
    default Promise<T> receive(Duration timeout) {
        return receive().orTimeout(timeout, true);
    }

    /**
     * Non-blocking receive. Returns immediately without registering a waiter.
     */
    Try<T> tryReceive();
    
    default SelectCase.Receive<T> receiving() {
        return SelectCase.receive(this);
    }
    
    /**
     * Asynchronously consumes all elements from the channel until it is closed and drained.
     * Mirrors Go's {@code for v := range ch { action(v) }}.
     */
    default Promise<Void> forEach(Consumer<? super T> action) {
        return forEach(action, v -> true);
    }

    /**
     * Asynchronously consumes elements until the channel is closed, drained, 
     * or the condition returns {@code false}.
     * <p>
     * The condition is evaluated <i>before</i> the action. If it returns 
     * {@code false}, the loop terminates immediately and the action is NOT 
     * executed for that specific element (similar to {@code Stream.takeWhile}).
     * <p>
     * Mirrors Go's:
     * <pre>{@code
     * for v := range ch {
     *     if !condition(v) { break }
     *     action(v)
     * }
     * }</pre>
     */
    default Promise<Void> forEach(Consumer<? super T> action, Predicate<? super T> continueCondition) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(continueCondition, "continueCondition");
        
        return Promises.loop(
            Boolean.TRUE,
            continueLoop -> continueLoop,
            continueLoop -> receive().dependent().thenApply(value -> {
                if (value == null && isClosed()) {
                    return Boolean.FALSE; // EOF
                }
                if (!continueCondition.test(value)) {
                    return Boolean.FALSE; // Break condition met
                }
                action.accept(value);
                return Boolean.TRUE; // Continue loop
            }, true)
        ).dependent()
         .thenAccept(b -> {}, true)
         .unwrap();
    }
}