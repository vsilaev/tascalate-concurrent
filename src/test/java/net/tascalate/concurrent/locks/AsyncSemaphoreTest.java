package net.tascalate.concurrent.locks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import net.tascalate.concurrent.Promise;

/**
 * Tests for {@link AsyncSemaphore} / {@link DefaultAsyncSemaphore}: the JDK-style,
 * permit-count-based semaphore with explicit {@code release(n)}.
 */
public class AsyncSemaphoreTest {

    @Test
    public void acquireReturnsGrantedPermitCount() throws Exception {
        AsyncSemaphore sem = AsyncSemaphore.create(5, false);

        assertEquals(Long.valueOf(3), sem.acquire(3).get());
        assertEquals(2, sem.availablePermits());

        assertEquals(Long.valueOf(1), sem.acquire().get()); // default: 1 permit
        assertEquals(1, sem.availablePermits());

        sem.release(3);
        assertEquals(4, sem.availablePermits());
        sem.release();
        assertEquals(5, sem.availablePermits());
    }

    @Test
    public void waitersAreFulfilledOnRelease() throws Exception {
        AsyncSemaphore sem = AsyncSemaphore.create(1, false);

        assertEquals(Long.valueOf(1), sem.acquire().get());

        Promise<Long> second = sem.acquire();
        Promise<Long> third  = sem.acquire();
        assertFalse(second.isDone());
        assertFalse(third.isDone());
        assertEquals(2, sem.getQueueLength());

        sem.release(); // fulfils 'second'
        assertEquals(Long.valueOf(1), second.get());
        assertFalse(third.isDone());

        sem.release(); // fulfils 'third'
        assertEquals(Long.valueOf(1), third.get());
        assertEquals(0, sem.getQueueLength());
    }

    @Test
    public void tryAcquireSucceedsOnlyWhenPermitsAvailable() {
        AsyncSemaphore sem = AsyncSemaphore.create(1, false);

        assertTrue(sem.tryAcquire());
        assertFalse(sem.tryAcquire());

        sem.release();
        assertTrue(sem.tryAcquire());
    }

    @Test
    public void drainPermitsReturnsAvailableCount() {
        AsyncSemaphore sem = AsyncSemaphore.create(5, false);

        assertTrue(sem.tryAcquire(2));
        assertEquals(3, sem.drainPermits());
        assertEquals(0, sem.availablePermits());

        assertEquals(0, sem.drainPermits());
        assertFalse(sem.tryAcquire());
    }

    @Test(expected = IllegalStateException.class)
    public void overReleaseIsRejected() {
        AsyncSemaphore.create(2, false).release(100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void acquireRejectsMorePermitsThanTotal() {
        AsyncSemaphore.create(2, false).acquire(3);
    }
}