package net.tascalate.concurrent.locks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Promises;

/**
 * Tests for {@link AsyncExclusiveLock} / {@link DefaultAsyncExclusiveLock}: mutual exclusion,
 * FIFO waiter ordering, idempotent release, cancellation, and {@code guard(...)}.
 */
public class AsyncLockTest {

    @Test
    public void tryAcquireSucceedsWhenFreeAndFailsWhenHeld() {
        AsyncExclusiveLock lock = AsyncExclusiveLock.create();

        AsyncExclusiveLock.Token first = lock.tryAcquire();
        assertNotNull(first);
        assertEquals("Lock is held — tryAcquire must fail", lock.tryAcquire(), null);

        first.release();
        AsyncExclusiveLock.Token again = lock.tryAcquire();
        assertNotNull(again);
        again.release();
    }

    @Test
    public void acquireCompletesImmediatelyWhenFree() {
        AsyncExclusiveLock lock = AsyncExclusiveLock.create();
        Promise<AsyncExclusiveLock.Token> p = lock.acquire();
        assertTrue(p.isDone());
        p.join().release();
    }

    @Test
    public void acquireWaitsUntilReleaseAndServesWaitersInFifoOrder() {
        AsyncExclusiveLock lock = AsyncExclusiveLock.create();
        AsyncExclusiveLock.Token first = lock.tryAcquire();

        List<String> order = new ArrayList<>();
        Promise<AsyncExclusiveLock.Token> second = lock.acquire().thenApply(t -> { order.add("second"); return t; });
        Promise<AsyncExclusiveLock.Token> third  = lock.acquire().thenApply(t -> { order.add("third");  return t; });

        assertFalse(second.isDone());
        assertFalse(third.isDone());

        first.release();
        assertTrue(second.isDone());
        assertFalse(third.isDone());
        assertEquals(Collections.singletonList("second"), order);

        second.join().release();
        assertTrue(third.isDone());
        assertEquals(Arrays.asList("second", "third"), order);

        third.join().release();
        assertNotNull(lock.tryAcquire());
    }

    @Test
    public void doubleReleaseIsANoOp() {
        AsyncExclusiveLock lock = AsyncExclusiveLock.create();
        AsyncExclusiveLock.Token token = lock.tryAcquire();

        token.release();
        token.release(); // second release must not corrupt state or free an extra slot

        assertNotNull(lock.tryAcquire());
    }

    @Test
    public void tokenCloseReleasesViaAutoCloseable() {
        AsyncExclusiveLock lock = AsyncExclusiveLock.create();
        AsyncExclusiveLock.Token token = lock.tryAcquire();

        token.close();

        assertNotNull(lock.tryAcquire());
    }

    @Test
    public void cancelledAcquisitionIsRemovedFromQueue() {
        AsyncExclusiveLock lock = AsyncExclusiveLock.create();
        AsyncExclusiveLock.Token first = lock.tryAcquire();

        Promise<AsyncExclusiveLock.Token> waiter = lock.acquire();
        assertFalse(waiter.isDone());

        waiter.cancel(true);
        assertTrue(waiter.isCancelled());

        first.release();
        // The cancelled waiter must not consume the lock — it is free again
        AsyncExclusiveLock.Token next = lock.tryAcquire();
        assertNotNull(next);
        next.release();
    }

    @Test
    public void explicitTokenHandlingWithTryCompose() throws Exception {
        AsyncExclusiveLock lock = AsyncExclusiveLock.create();

        Promise<String> result = Promises.tryCompose(
            lock.acquire(),
            token -> Promises.success("worked")
        );

        assertEquals("worked", result.get());
        assertNotNull("Token must be released after the action", lock.tryAcquire());
    }

    @Test
    public void guardHoldsTheLockDuringActionAndReleasesAfterSuccess() throws Exception {
        AsyncExclusiveLock lock = AsyncExclusiveLock.create();
        AtomicBoolean lockHeldDuringAction = new AtomicBoolean(false);

        Promise<String> result = lock.guard(() -> {
            lockHeldDuringAction.set(lock.tryAcquire() == null);
            return Promises.success("done");
        });

        assertEquals("done", result.get());
        assertTrue("Lock must be held while the guarded action runs", lockHeldDuringAction.get());
        assertNotNull("Lock must be released after the guarded action", lock.tryAcquire());
    }

    @Test
    public void guardReleasesTheLockAfterFailure() {
        AsyncExclusiveLock lock = AsyncExclusiveLock.create();
        RuntimeException boom = new RuntimeException("boom");

        Promise<String> result = lock.guard(() -> Promises.failure(boom));

        assertTrue(result.isCompletedExceptionally());
        try {
            result.join();
            fail("Expected the guarded promise to fail");
        } catch (CompletionException expected) {
            assertSame(boom, Promises.unwrapCompletionException(expected));
        }
        assertNotNull("Lock must be released even when the action fails", lock.tryAcquire());
    }
}