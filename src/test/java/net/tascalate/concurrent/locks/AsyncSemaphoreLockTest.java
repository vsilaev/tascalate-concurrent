package net.tascalate.concurrent.locks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Promises;

/**
 * Tests for {@link AsyncSemaphoreLock} / {@link DefaultAsyncSemaphoreLock}:
 * token-based permit acquisition, fairness, draining, cancellation, and validation.
 */
public class AsyncSemaphoreLockTest {

    @Test
    public void tryAcquireSinglePermitTokens() {
        AsyncSemaphoreLock sem = AsyncSemaphoreLock.create(2, false);
        assertEquals(2, sem.availablePermits());

        Optional<AsyncSemaphoreLock.Token> t1 = sem.tryAcquire();
        assertTrue(t1.isPresent());
        assertEquals(1, t1.get().permits());
        assertEquals(1, sem.availablePermits());

        Optional<AsyncSemaphoreLock.Token> t2 = sem.tryAcquire();
        assertTrue(t2.isPresent());
        assertEquals(0, sem.availablePermits());

        assertFalse("No permits left", sem.tryAcquire().isPresent());

        t1.get().release();
        assertEquals(1, sem.availablePermits());
        t2.get().release();
        assertEquals(2, sem.availablePermits());
    }

    @Test
    public void tryAcquireMultiplePermits() {
        AsyncSemaphoreLock sem = AsyncSemaphoreLock.create(5, false);

        Optional<AsyncSemaphoreLock.Token> t = sem.tryAcquire(3);
        assertTrue(t.isPresent());
        assertEquals(3, t.get().permits());
        assertEquals(2, sem.availablePermits());

        assertFalse("Only 2 permits left — 3 must not be granted", sem.tryAcquire(3).isPresent());

        t.get().release();
        assertEquals(5, sem.availablePermits());
    }

    @Test
    public void acquireWaitsForPermitsAndFulfilsWaitersInOrder() {
        AsyncSemaphoreLock sem = AsyncSemaphoreLock.create(2, false);

        AsyncSemaphoreLock.Token first = sem.acquire(2).join(); // consumes everything
        assertEquals(0, sem.availablePermits());

        Promise<AsyncSemaphoreLock.Token> second = sem.acquire(1);
        Promise<AsyncSemaphoreLock.Token> third  = sem.acquire(2);
        assertFalse(second.isDone());
        assertFalse(third.isDone());
        assertEquals(2, sem.getQueueLength());

        first.release(); // 2 available → fulfils 'second' (needs 1), not enough for 'third'
        assertTrue(second.isDone());
        assertFalse(third.isDone());
        assertEquals(1, sem.availablePermits());
        assertEquals(1, sem.getQueueLength());

        second.join().release(); // 2 available → fulfils 'third' (needs 2)
        assertTrue(third.isDone());
        assertEquals(0, sem.availablePermits());

        third.join().release();
        assertEquals(2, sem.availablePermits());
        assertEquals(0, sem.getQueueLength());
    }

    @Test
    public void tokenReleaseIsIdempotent() {
        AsyncSemaphoreLock sem = AsyncSemaphoreLock.create(3, false);
        AsyncSemaphoreLock.Token t = sem.tryAcquire(2).get();

        t.release();
        assertEquals(3, sem.availablePermits());
        t.release(); // double release must be a no-op
        assertEquals(3, sem.availablePermits());
    }

    @Test
    public void drainPermitsReturnsAllAvailablePermitsAsToken() {
        AsyncSemaphoreLock sem = AsyncSemaphoreLock.create(5, false);
        AsyncSemaphoreLock.Token held = sem.tryAcquire(2).get();

        Optional<AsyncSemaphoreLock.Token> drained = sem.drainPermits();
        assertTrue(drained.isPresent());
        assertEquals(3, drained.get().permits());
        assertEquals(0, sem.availablePermits());

        assertFalse("Nothing left to drain", sem.drainPermits().isPresent());

        drained.get().release();
        held.release();
        assertEquals(5, sem.availablePermits());
    }

    @Test
    public void fairModePreventsBarging() {
        AsyncSemaphoreLock fair = AsyncSemaphoreLock.create(2, true);

        AsyncSemaphoreLock.Token first = fair.tryAcquire().get();     // 1 left
        Promise<AsyncSemaphoreLock.Token> waiter = fair.acquire(2);   // needs 2 → queues
        assertFalse(waiter.isDone());
        assertEquals(1, fair.availablePermits());

        // The spare permit must NOT be granted ahead of the queued waiter
        assertFalse(fair.tryAcquire().isPresent());

        first.release(); // 2 available → fulfils the waiter
        assertTrue(waiter.isDone());
        assertEquals(2, waiter.join().permits());
        assertEquals(0, fair.availablePermits());

        waiter.join().release();
        assertEquals(2, fair.availablePermits());
    }

    @Test
    public void unfairModeAllowsBarging() {
        AsyncSemaphoreLock unfair = AsyncSemaphoreLock.create(2, false);

        AsyncSemaphoreLock.Token first = unfair.tryAcquire().get();    // 1 left
        Promise<AsyncSemaphoreLock.Token> waiter = unfair.acquire(2);  // needs 2 → queues
        assertFalse(waiter.isDone());

        // Unfair: barging ahead of the queued waiter is allowed
        Optional<AsyncSemaphoreLock.Token> barged = unfair.tryAcquire();
        assertTrue(barged.isPresent());
        assertEquals(0, unfair.availablePermits());

        barged.get().release(); // 1
        first.release();        // 2 → fulfils the waiter
        assertTrue(waiter.isDone());

        waiter.join().release();
        assertEquals(2, unfair.availablePermits());
    }

    @Test
    public void cancelledWaiterLeavesPermitsForOthers() {
        AsyncSemaphoreLock sem = AsyncSemaphoreLock.create(2, false);

        AsyncSemaphoreLock.Token all = sem.acquire(2).join();

        Promise<AsyncSemaphoreLock.Token> cancelled = sem.acquire(2);
        Promise<AsyncSemaphoreLock.Token> survivor  = sem.acquire(1);
        assertEquals(2, sem.getQueueLength());

        cancelled.cancel(true);
        assertTrue(cancelled.isCancelled());
        assertEquals(1, sem.getQueueLength());

        all.release(); // 2 available → fulfils the survivor
        assertTrue(survivor.isDone());
        assertEquals(1, sem.availablePermits());

        survivor.join().release();
        assertEquals(2, sem.availablePermits());
    }

    @Test
    public void guardHoldsOnePermitDuringAction() throws Exception {
        AsyncSemaphoreLock sem = AsyncSemaphoreLock.create(2, false);

        AtomicInteger permitsDuringAction = new AtomicInteger(-1);
        Promise<String> result = sem.guard(() -> {
            permitsDuringAction.set((int) sem.availablePermits());
            return Promises.success("ok");
        });

        assertEquals("ok", result.get());
        assertEquals(1, permitsDuringAction.get());
        assertEquals(2, sem.availablePermits()); // permit returned afterwards
    }

    @Test(expected = IllegalArgumentException.class)
    public void acquireRejectsMorePermitsThanTotal() {
        AsyncSemaphoreLock.create(2, false).acquire(3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void acquireRejectsNonPositivePermits() {
        AsyncSemaphoreLock.create(2, false).acquire(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createRejectsNonPositiveTotalPermits() {
        AsyncSemaphoreLock.create(0, false);
    }
}