/**
 * Implementations of the {@link net.tascalate.concurrent.DelayPolicy} interface, used by 
 * {@link net.tascalate.concurrent.RetryPolicy} to determine the backoff delay between 
 * asynchronous retry attempts as well as timeouts for each iteration.
 *
 * <h2>Overview</h2>
 * <p>
 * This package provides a comprehensive set of delay strategies ranging from simple fixed 
 * intervals to complex, jittered exponential backoff. All policies in this package are 
 * designed to be highly composable via the fluent decorator methods provided in the 
 * {@link net.tascalate.concurrent.DelayPolicy} interface.
 * </p>
 *
 * <h2>Core Strategies</h2>
 * <ul>
 *   <li>{@link net.tascalate.concurrent.delays.FixedIntervalDelayPolicy}: Returns a constant 
 *       delay regardless of the retry count.</li>
 *   <li>{@link net.tascalate.concurrent.delays.ExponentialDelayPolicy}: Multiplies the delay 
 *       by a configurable factor after each retry, providing classic exponential backoff.</li>
 * </ul>
 *
 * <h2>Random Jitter</h2>
 * <p>
 * To prevent synchronized "thundering herd" retries when multiple clients fail simultaneously, 
 * random jitter can be applied to any base policy:
 * </p>
 * <ul>
 *   <li>{@link net.tascalate.concurrent.delays.UniformRandomDelayPolicy}: Adds a random 
 *       variation within a fixed absolute range (e.g., &plusmn;100ms).</li>
 *   <li>{@link net.tascalate.concurrent.delays.ProportionalRandomDelayPolicy}: Adds a random 
 *       variation proportional to the base delay (e.g., &plusmn;10% of the current backoff).</li>
 * </ul>
 *
 * <h2>Bounding and Conditional Policies</h2>
 * <p>
 * Policies can be wrapped to enforce limits or conditionally skip delays:
 * </p>
 * <ul>
 *   <li>{@link net.tascalate.concurrent.delays.BoundedMinDelayPolicy}: Ensures the delay 
 *       never falls below a specified minimum threshold.</li>
 *   <li>{@link net.tascalate.concurrent.delays.BoundedMaxDelayPolicy}: Caps the delay at a 
 *       specified maximum, essential for taming exponential backoff.</li>
 *   <li>{@link net.tascalate.concurrent.delays.FirstRetryNoDelayPolicy}: Skips the delay 
 *       entirely for the very first retry attempt.</li>
 *   <li>{@link net.tascalate.concurrent.delays.OnFailureNoDelayPolicy}: Skips the delay if 
 *       the previous attempt failed with an exception (fail-fast retries).</li>
 *   <li>{@link net.tascalate.concurrent.delays.OnSuccessNoDelayPolicy}: Skips the delay if 
 *       the previous attempt succeeded but returned an invalid result (polling scenarios).</li>
 * </ul>
 *
 * <h2>Composability Example</h2>
 * <p>
 * Policies are typically composed using the fluent API on {@link net.tascalate.concurrent.DelayPolicy}:
 * </p>
 * <pre>{@code
 * DelayPolicy<?> policy = DelayPolicy
 *     .exponential(Duration.ofMillis(100), 2.0) // Start at 100ms, double each time
 *     .withMaxDelay(Duration.ofSeconds(10))     // Cap at 10 seconds
 *     .withUniformJitter(Duration.ofMillis(50)) // Add +/- 50ms of jitter
 *     .withFirstRetryNoDelay();                 // Skip delay on the very first retry
 * }</pre>
 *
 * @author vsilaev
 * @see net.tascalate.concurrent.DelayPolicy
 * @see net.tascalate.concurrent.RetryPolicy
 */
package net.tascalate.concurrent.delays;