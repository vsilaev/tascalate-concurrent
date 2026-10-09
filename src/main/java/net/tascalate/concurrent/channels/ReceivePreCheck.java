package net.tascalate.concurrent.channels;

import java.util.function.Predicate;

/**
 * A specialized {@link Predicate} that acts as a pre-fetch guard for channel consumption loops.
 * <p>
 * Standard predicates passed to {@link ReceiveChannel#forEach} are evaluated <i>after</i> 
 * an element is pulled from the channel. This is correct for value-based conditions 
 * (e.g., "stop when value == STOP_WORD"), but causes a "read-ahead" bug for count-based 
 * limits (e.g., "stop after N items"), because the N+1th element would be consumed and 
 * discarded just to evaluate the condition.
 * <p>
 * If a predicate implements this interface, the {@link #mayReceive()} method is evaluated 
 * <i>before</i> calling {@code receive()}. If it returns {@code false}, the loop terminates 
 * immediately without pulling an element from the channel buffer.
 *
 * @param <T> the type of elements being received
 */
public abstract class ReceivePreCheck<T> implements Predicate<T> {

    /**
     * Evaluated before pulling an element from the channel.
     * 
     * @return {@code true} if the loop is allowed to call {@code receive()} and 
     *         fetch the next element; {@code false} to terminate the loop immediately 
     *         without consuming from the channel.
     */
    abstract boolean mayReceive();
    
    /**
     * Always returns {@code true}. 
     * <p>
     * When used as a {@link ReceivePreCheck}, the termination decision is made entirely 
     * by {@link #mayReceive()} before the element is fetched. Therefore, the post-fetch 
     * evaluation of the element itself unconditionally accepts it.
     *
     * @param t the received element
     * @return always {@code true}
     */
    @Override
    public final boolean test(T t) {
        return true;
    }
}
