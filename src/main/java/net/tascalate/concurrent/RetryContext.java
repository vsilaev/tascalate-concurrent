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
 * An immutable snapshot of the retry loop state, passed to contextual code blocks
 * ({@link RetryRunnable} / {@link RetryCallable}) before every attempt and to
 * {@link RetryPolicy} rules when deciding the loop's fate.
 *
 * <p>For the <b>first attempt</b> the retry count is zero, the last call duration is
 * {@link Duration#ZERO}, and both the last result and the last error are {@code null}.
 * For <b>subsequent attempts</b> the context carries the outcome of the <em>previous</em>
 * attempt: its duration, and either its result (when the attempt completed normally but
 * was rejected by the policy's result validator) or its error (when the attempt threw).
 * The result and the error are mutually exclusive — one of them is always {@code null}.
 * </p>
 *
 * <p>Instances are immutable; all {@code override*} methods return modified copies.
 * </p>
 *
 * @param <T> the type of the result tracked by the retry loop
 */
public final class RetryContext<T> {

    /** Number of attempts completed so far (0 before the first attempt). */
    private final int retryCount;

    /** Duration of the previous attempt ({@link Duration#ZERO} before the first attempt). */
    private final Duration lastCallDuration;

    /** Result of the previous attempt if it completed normally but was rejected; otherwise {@code null}. */
    private final T lastResult;

    /** Error of the previous attempt if it threw; otherwise {@code null}. */
    private final Throwable lastError;

    private RetryContext(int retryCount, Duration lastCallDuration, T lastResult, Throwable lastError) {
         this.retryCount = retryCount;
         this.lastCallDuration = lastCallDuration;
         this.lastResult = lastResult;
         this.lastError  = lastError;
     }

     /**
      * Returns the number of attempts completed so far: 0 before the first attempt,
      * 1 before the first retry, and so on.
      *
      * @return the number of completed attempts
      */
     public int getRetryCount() {
         return retryCount;
     }

     /**
      * Returns the duration of the previous attempt.
      *
      * @return the previous attempt duration, or {@link Duration#ZERO} before the first attempt
      */
     public Duration getLastCallDuration() {
         return lastCallDuration;
     }

     /**
      * Returns the result of the previous attempt when it completed normally but was
      * rejected by the policy's result validator.
      *
      * @return the previous result, or {@code null} if the previous attempt threw or
      *         there was no previous attempt
      */
     public T getLastResult() {
         return lastResult;
     }    

     /**
      * Returns the error thrown by the previous attempt.
      *
      * @return the previous error, or {@code null} if the previous attempt completed
      *         normally or there was no previous attempt
      */
     public Throwable getLastError() {
         return lastError;
     }

     /**
      * Returns a copy of this context with the retry count replaced.
      *
      * @param newRetryCount the new retry count
      * @return a modified copy of this context
      */
     public RetryContext<T> overrideRetryCount(int newRetryCount) {
         return new RetryContext<>(newRetryCount, lastCallDuration, lastResult, lastError);
     }

     /**
      * Returns a copy of this context with the last call duration replaced.
      *
      * @param newDuration the new last call duration
      * @return a modified copy of this context
      */
     public RetryContext<T> overrideLastCallDuration(Duration newDuration) {
         return new RetryContext<>(retryCount, newDuration, lastResult, lastError);
     }

     /**
      * Returns a copy of this context with the last result replaced.
      *
      * @param newResult the new last result
      * @return a modified copy of this context
      */
     public RetryContext<T> overrideLastResult(T newResult) {
         return new RetryContext<>(retryCount, lastCallDuration, newResult, lastError);
     }

     /**
      * Returns a copy of this context with the last error replaced.
      *
      * @param newError the new last error
      * @return a modified copy of this context
      */
     public RetryContext<T> overrideLastError(Throwable newError) {
         return new RetryContext<>(retryCount, lastCallDuration, lastResult, newError);
     }

     /**
      * Creates the context seen by the very first attempt.
      *
      * @param <T> the tracked result type
      * @return an initial context with zero attempts and no previous outcome
      */
     static <T> RetryContext<T> initial() {
         return new RetryContext<>(0, Duration.ZERO, null, null);
     }

     /**
      * Advances the loop after an attempt that completed normally but whose result
      * was rejected by the policy.
      *
      * @param callDuration the duration of the completed attempt
      * @param lastResult   the rejected result of the completed attempt
      * @return the context for the next attempt
      */
     RetryContext<T> nextRetry(Duration callDuration, T lastResult) {
         return new RetryContext<>(retryCount + 1, callDuration, lastResult, null);
     }

     /**
      * Advances the loop after an attempt that threw.
      *
      * @param callDuration the duration of the failed attempt
      * @param lastError    the error thrown by the failed attempt
      * @return the context for the next attempt
      */
     RetryContext<T> nextRetry(Duration callDuration, Throwable lastError) {
         return new RetryContext<>(retryCount + 1, callDuration, null, lastError);
     }

     /**
      * Converts the final (exhausted) context into the failure reported by the promise
      * returned from the {@code Promises.retry*} family.
      *
      * @return a {@link RetryException} carrying the attempt count, the last attempt
      *         duration and the last error
      */
     RetryException asFailure() {
         RetryException result = new RetryException(retryCount, lastCallDuration, lastError);
         result.fillInStackTrace();
         return result;
     }
}