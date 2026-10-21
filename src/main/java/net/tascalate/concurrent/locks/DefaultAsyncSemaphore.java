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

/**
 * The default permit-based implementation of {@link AsyncSemaphore}, built on
 * {@link AsyncSemaphoreBase}.
 * <p>
 * The token payload type of the underlying base is {@link Long} — a successfully
 * completed acquisition promise delivers the <em>number of permits</em> granted,
 * matching the permit-count contract of {@link AsyncSemaphore}. Releasing permits
 * is the caller's responsibility via {@link #release(long)} (or {@link #release()});
 * the granted count is not tied to any token object.
 * </p>
 *
 * <p>Create instances with {@link AsyncSemaphore#create(long, boolean)}.</p>
 *
 * @see AsyncSemaphore
 * @see AsyncSemaphoreBase
 * @see DefaultAsyncSemaphoreLock
 */
public class DefaultAsyncSemaphore extends AsyncSemaphoreBase<Long> 
                                   implements AsyncSemaphore {

    /**
     * Creates a semaphore with the given total permits and fairness policy.
     *
     * @param totalPermits the total number of permits; must be positive
     * @param fair         {@code true} to grant permits strictly in arrival order
     */
    public DefaultAsyncSemaphore(long totalPermits, boolean fair) {
         super(totalPermits, fair);
     }

     /**
      * {@inheritDoc}
      * <p>Delegates to {@link #drainPermitsInternal()}.</p>
      */
     @Override
     public long drainPermits() {
         return drainPermitsInternal();
     }

     /**
      * {@inheritDoc}
      * <p>Delegates to {@link #tryAcquireInternal(long)}.</p>
      */
     @Override
     public boolean tryAcquire(long permits) {
         return tryAcquireInternal(permits);
     }

     /**
      * {@inheritDoc}
      * <p>
      * Makes the package-private base implementation public for this flavour;
      * released permits are handed to queued waiters by the base machinery.
      * </p>
      */
     @Override
     public void release(long permits) {
         super.release(permits);
     }

     /**
      * {@inheritDoc}
      * <p>For this implementation the payload is the granted permit count itself.</p>
      */
     @Override
     protected Long createPromisePayload(long permits) {
         return Long.valueOf(permits);
     }

     /**
      * {@inheritDoc}
      */
     @Override
     protected SemaphorePromise createPromise(long permits) {
         return new SemaphorePromise(permits);
     }

     /**
      * A waiter promise for a permit-count acquisition; completes with the boxed
      * number of permits once they are reserved by the base machinery.
      */
     private class SemaphorePromise extends AbstractSemaphorePromise {

         /** The requested (and, on completion, granted) permit count. */
         private final Long permits;

         /**
          * Creates a waiter for the given permit count.
          *
          * @param permits the number of permits requested
          */
         SemaphorePromise(long permits) {
             this.permits = Long.valueOf(permits);
         }

         /** {@inheritDoc} */
         long permits() {
             return permits.longValue();
         }

         /**
          * {@inheritDoc}
          * <p>Completes this waiter with the granted permit count.</p>
          */
         boolean acquire() {
             return success(permits);
         }
     }
}