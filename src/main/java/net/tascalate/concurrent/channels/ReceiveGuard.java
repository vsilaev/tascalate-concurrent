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
package net.tascalate.concurrent.channels;

import java.util.function.Predicate;

/**
 * A two-phase receive condition for channel consumption loops.
 * <p>
 * Standard {@link Predicate} conditions passed to
 * {@link ReceiveChannel#forEach(java.util.function.Consumer, Predicate)} are
 * evaluated only <i>after</i> an element has been pulled from the channel. This
 * is correct for value-based conditions (e.g., "stop when the value equals a
 * poison pill"), but causes a <b>read-ahead bug</b> for count-based limits:
 * to discover that N items have already been received, the loop must consume
 * the (N+1)th item, evaluate the predicate against it, and then discard it.
 * That extra item is permanently removed from the channel buffer even though
 * the caller never intended to consume it.
 * <p>
 * {@code ReceiveGuard} solves this by introducing a <b>pre-fetch phase</b>.
 * The abstract method {@link #mayReceive()} is evaluated <i>before</i> calling
 * {@code receive()}. If it returns {@code false}, the consumption loop
 * terminates immediately without pulling any element from the channel,
 * leaving the buffer untouched.
 * <p>
 * Because this class implements {@link Predicate}, it is directly compatible
 * with the {@code forEach} signature. The two phases work together:
 * <ol>
 *   <li><b>Pre-check ({@link #mayReceive()}):</b> evaluated before each
 *       {@code receive()} call. Controls whether the loop is allowed to
 *       fetch the next element at all.</li>
 *   <li><b>Post-check ({@link #test(Object)}):</b> evaluated after an element
 *       has been received. By default unconditionally returns {@code true},
 *       but may be overridden to add value-based filtering on top of the
 *       pre-fetch guard.</li>
 * </ol>
 *
 * <h2>Usage examples</h2>
 *
 * Pre-check only (bounded consumption):
 * <pre>{@code
 * List<Integer> result = new ArrayList<>();
 * int maxItems = 10;
 *
 * ReceiveGuard<Integer> guard = new ReceiveGuard<Integer>() {
 *     @Override
 *     boolean mayReceive() {
 *         return result.size() < maxItems;
 *     }
 * };
 *
 * ch.forEach(result::add, guard).join();
 * // result contains exactly maxItems elements.
 * // The (maxItems+1)th element remains in the channel.
 * }</pre>
 *
 * Pre-check combined with post-check:
 * <pre>{@code
 * List<Integer> result = new ArrayList<>();
 * int maxItems = 10;
 *
 * ReceiveGuard<Integer> guard = new ReceiveGuard<Integer>() {
 *     @Override
 *     boolean mayReceive() {
 *         return result.size() < maxItems;
 *     }
 *
 *     @Override
 *     public boolean test(Integer value) {
 *         return value != -1; // also stop on poison pill
 *     }
 * };
 *
 * ch.forEach(result::add, guard).join();
 * }</pre>
 *
 * @param <T> the type of elements being received
 * @see ReceiveChannel#forEach(java.util.function.Consumer, Predicate)
 */
public abstract class ReceiveGuard<T> implements Predicate<T> {

    /**
     * Pre-fetch guard evaluated <i>before</i> each call to {@code receive()}.
     * <p>
     * The consumption loop calls this method at the top of every iteration,
     * before any element is pulled from the channel. If this method returns
     * {@code false}, the loop terminates immediately and no element is
     * consumed. If it returns {@code true}, the loop proceeds to call
     * {@code receive()} and process the next element.
     * <p>
     * Implementations should make this method lightweight and side-effect-free,
     * as it may be called many times in a tight synchronous drain loop.
     *
     * @return {@code true} if the loop is allowed to fetch the next element;
     *         {@code false} to terminate the loop without consuming from the
     *         channel
     */
    abstract boolean mayReceive();

    /**
     * Post-fetch evaluation, called after an element has been received.
     * <p>
     * By default this method unconditionally returns {@code true}, meaning
     * every element that passes the {@link #mayReceive()} pre-check is
     * accepted. Override this method to add value-based termination on top
     * of the pre-fetch guard (e.g., stopping on a poison-pill value).
     * <p>
     * If this method returns {@code false}, the consumption loop terminates
     * and the received element is <b>not</b> delivered to the consumer action.
     * Note that unlike the pre-check, the element has already been consumed
     * from the channel at this point and cannot be "put back".
     *
     * @param t the received element
     * @return {@code true} to accept the element and continue the loop;
     *         {@code false} to terminate the loop
     */
    @Override
    public boolean test(T t) {
        return true;
    }
}