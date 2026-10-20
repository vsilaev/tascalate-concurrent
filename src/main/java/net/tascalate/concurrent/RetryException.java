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
package net.tascalate.concurrent;

import java.time.Duration;

/**
 * Signals that an asynchronous retry loop has given up: either the retry budget
 * ({@code maxRetries}) of the governing {@link RetryPolicy} was exhausted, or one
 * of its abort rules matched.
 * <p>
 * This exception is the terminal failure of the {@code Promises.retry(...)},
 * {@code Promises.retryOptional(...)} and {@code Promises.retryFuture(...)} family:
 * the promise they return completes exceptionally with a {@code RetryException}
 * when the policy decides that no further attempts should be made.
 * </p>
 * <p>
 * It carries diagnostic information about the failed loop:
 * </p>
 * <ul>
 *   <li>{@link #getRetryCount()} &mdash; the number of attempts made;</li>
 *   <li>{@link #getLastCallDuration()} &mdash; the duration of the last attempt;</li>
 *   <li>{@link #getCause()} &mdash; the last error thrown by an attempt, or {@code null}
 *       when the loop stopped because of a rejected result rather than an exception.</li>
 * </ul>
 *
 * @see RetryPolicy
 * @see RetryContext
 * @see Promises#retry(java.util.concurrent.Callable, java.util.concurrent.Executor, RetryPolicy)
 */
public class RetryException extends Exception {

    private static final long serialVersionUID = 1L;

    /** The number of attempts made before the loop gave up. */
    private final int retry;

    /** The duration of the last attempt. */
    private final Duration lastCallDuration;

    /**
     * Creates an exception describing an empty loop: zero attempts, zero duration,
     * and no cause.
     */
    public RetryException() {
        retry = 0;
        lastCallDuration = Duration.ZERO;
    }

    /**
     * Creates an exception carrying the final state of a failed retry loop.
     *
     * @param retry            the number of attempts made
     * @param lastCallDuration the duration of the last attempt
     * @param lastThrowable    the last error thrown by an attempt; may be {@code null}
     *                         when the loop stopped because a result was rejected by the
     *                         policy rather than because an attempt threw
     */
    public RetryException(int retry, Duration lastCallDuration, Throwable lastThrowable) {
        super(lastThrowable);
        this.retry = retry;
        this.lastCallDuration = lastCallDuration;
    }

    /**
     * Returns the number of attempts made before the loop gave up.
     *
     * @return the attempt count
     */
    public int getRetryCount() {
        return retry;
    }

    /**
     * Returns the duration of the last attempt.
     *
     * @return the last attempt duration
     */
    public Duration getLastCallDuration() {
        return lastCallDuration;
    }
}