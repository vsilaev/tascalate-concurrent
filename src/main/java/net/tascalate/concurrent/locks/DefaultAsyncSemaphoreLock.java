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
package net.tascalate.concurrent.locks;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * The default token-based implementation of {@link AsyncSemaphoreLock}, built on
 * {@link AsyncSemaphoreBase}.
 * <p>
 * Unlike {@link DefaultAsyncSemaphore} (which delivers the granted permit count and
 * requires an explicit {@code release(...)}), this implementation hands out
 * {@link AsyncSemaphoreLock.Token tokens}: each token knows how many permits it holds
 * ({@link Token#permits()}) and releases exactly that many permits when
 * {@link Token#release()} is called. Tokens are therefore self-contained and safe to
 * use with the asynchronous try-with-resources combinators
 * ({@link net.tascalate.concurrent.Promises#tryCompose},
 * {@link AbstractAsyncLock#guard}).
 * </p>
 * <p>
 * Both token release and waiter release are guarded by idempotent release-once flags
 * ({@code int} flags managed through {@link AtomicIntegerFieldUpdater}s), so releasing
 * a token more than once has no effect.
 * </p>
 *
 * <p>Create instances with {@link AsyncSemaphoreLock#create(long, boolean)}.</p>
 *
 * @see AsyncSemaphoreLock
 * @see AsyncSemaphoreBase
 * @see DefaultAsyncSemaphore
 */
public class DefaultAsyncSemaphoreLock extends AsyncSemaphoreBase<AsyncSemaphoreLock.Token> 
                                       implements AsyncSemaphoreLock {
    
    /** CAS-based updater for the per-promise release flag {@link LockPromise#released}. */
    private static final AtomicIntegerFieldUpdater<LockPromise> RELEASED =
        AtomicIntegerFieldUpdater.newUpdater(LockPromise.class, "released");
    
    /**
     * Creates a semaphore lock with the given total permits and fairness policy.
     *
     * @param totalPermits the total number of permits; must be positive
     * @param fair         {@code true} to grant permits strictly in arrival order
     */
    public DefaultAsyncSemaphoreLock(long totalPermits, boolean fair) {
         super(totalPermits, fair);
     }

     /**
      * {@inheritDoc}
      * <p>
      * If any permits are available they are drained and wrapped into a token;
      * otherwise an empty {@link Optional} is returned.
      * </p>
      */
     @Override
     public Optional<Token> drainPermits() {
         long acquired = drainPermitsInternal();
         return acquired > 0 ? Optional.of(createPromisePayload(acquired)) : Optional.empty(); 
     }

     /**
      * {@inheritDoc}
      */
     @Override
     public Optional<Token> tryAcquire(long permitsCount) {
         if (tryAcquireInternal(permitsCount)) {
             return Optional.of(createPromisePayload(permitsCount));
         } else {
             return Optional.empty();
         }
     }

     /**
      * {@inheritDoc}
      * <p>
      * Creates a release-once token holding the given number of permits; releasing
      * the token returns exactly that many permits to this semaphore. The
      * release-once guard is inherited from {@link AbstractToken#tryMarkReleased()}.
      * </p>
      */
     @Override
     protected Token createPromisePayload(long permits) {
         return new AbstractToken(permits) {
             @Override
             public void release() {
                 if (tryMarkReleased()) {
                     DefaultAsyncSemaphoreLock.this.release(permits());
                 }
             }
         };  
     }

     /**
      * {@inheritDoc}
      */
     @Override
     protected LockPromise createPromise(long permitsCount) {
         return new LockPromise(permitsCount);
     }


     /**
      * Base {@link Token} implementation carrying the held permit count and the
      * release-once flag shared by all token variants.
      */
     abstract static class AbstractToken implements Token {

         /** CAS-based updater for the release-once flag {@link #released}. */
         private static final AtomicIntegerFieldUpdater<AbstractToken> RELEASED =
             AtomicIntegerFieldUpdater.newUpdater(AbstractToken.class, "released");

         /** The number of permits held by this token. */
         private final long permitsCount;

         /**
          * Release-once guard: {@code 0} = not released, {@code 1} = released.
          * Accessed exclusively via {@link #RELEASED}.
          */
         @SuppressWarnings("unused")
         private volatile int released = 0;

         /**
          * Creates a token holding the given permit count.
          *
          * @param permitsCount the number of permits held
          */
         public AbstractToken(long permitsCount) {
             this.permitsCount = permitsCount;
         }

         /** {@inheritDoc} */
         @Override
         public long permits() {
             return permitsCount;
         }

         /**
          * Atomically marks this token as released.
          *
          * @return {@code true} if this invocation performed the transition, i.e. the
          *         token had not been released before; {@code false} on repeat calls
          */
         final boolean tryMarkReleased() {
             return RELEASED.compareAndSet(this, 0, 1);
         }
     }

     /**
      * A waiter promise for a token acquisition; completing it delivers a token whose
      * {@link Token#release()} returns the granted permits to the semaphore.
      */
     private class LockPromise extends AbstractSemaphorePromise {

         /**
          * Release-once guard: {@code 0} = not released, {@code 1} = released.
          * Accessed exclusively via {@link DefaultAsyncSemaphoreLock#RELEASED}.
          */
         @SuppressWarnings("unused")
         volatile int released = 0;

         /** The token delivered upon successful acquisition. */
         private final Token token;

         /**
          * Creates a waiter for the given permit count.
          *
          * @param permitsCount the number of permits requested
          */
         LockPromise(long permitsCount) {
             this.token = new AbstractToken(permitsCount) {
                 @Override
                 public void release() {
                     LockPromise.this.release();
                 }
             };
         }

         /** {@inheritDoc} */
         long permits() {
             return token.permits();
         }

         /**
          * {@inheritDoc}
          * <p>Completes this waiter with the token.</p>
          */
         boolean acquire() {
             return success(token);
         }

         /**
          * Releases the permits held by this waiter's token back to the semaphore.
          * Idempotent — only the first invocation takes effect.
          */
         void release() {
             if (RELEASED.compareAndSet(this, 0, 1)) {
                 DefaultAsyncSemaphoreLock.this.release(permits());
             }            
         }        
     }
}