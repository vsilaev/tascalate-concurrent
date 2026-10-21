package net.tascalate.concurrent.locks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Promises;

/**
 * Tests for {@link AsyncLock} / {@link DefaultAsyncLock}: mutual exclusion,
 * FIFO waiter ordering, idempotent release, cancellation, and {@code guard(...)}.
 */
public class AsyncLockTest {

    @Test
    public void tryAcquireSucceedsWhenFreeAndFailsWhenHeld() {
        AsyncLock lock = AsyncLock.create();

        Optional<AsyncLock.Token> first = lock.tryAcquire();
        assertTrue(first.isPresent());
        assertFalse("Lock is held — tryAcquire must fail", lock.tryAcquire().isPresent());

        first.get().release();
        Optional<AsyncLock.Token> again = lock.tryAcquire();
        assertTrue(again.isPresent());
        again.get().release();
    }

    @Test
    public void acquireCompletesImmediatelyWhenFree() {
        AsyncLock lock = AsyncLock.create();
        Promise<AsyncLock.Token> p = lock.acquire();
        assertTrue(p.isDone());
        p.join().release();
    }

    @Test
    public void acquireWaitsUntilReleaseAndServesWaitersInFifoOrder() {
        AsyncLock lock = AsyncLock.create();
        AsyncLock.Token first = lock.tryAcquire().get();

        List<String> order = new ArrayList<>();
        Promise<AsyncLock.Token> second = lock.acquire().thenApply(t -> { order.add("second"); return t; });
        Promise<AsyncLock.Token> third  = lock.acquire().thenApply(t -> { order.add("third");  return t; });

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
        assertTrue(lock.tryAcquire().isPresent());
    }

    @Test
    public void doubleReleaseIsANoOp() {
        AsyncLock lock = AsyncLock.create();
        AsyncLock.Token token = lock.tryAcquire().get();

        token.release();
        token.release(); // second release must not corrupt state or free an extra slot

        assertTrue(lock.tryAcquire().isPresent());
    }

    @Test
    public void tokenCloseReleasesViaAutoCloseable() {
        AsyncLock lock = AsyncLock.create();
        AsyncLock.Token token = lock.tryAcquire().get();

        token.close();

        assertTrue(lock.tryAcquire().isPresent());
    }

    @Test
    public void cancelledAcquisitionIsRemovedFromQueue() {
        AsyncLock lock = AsyncLock.create();
        AsyncLock.Token first = lock.tryAcquire().get();

        Promise<AsyncLock.Token> waiter = lock.acquire();
        assertFalse(waiter.isDone());

        waiter.cancel(true);
        assertTrue(waiter.isCancelled());

        first.release();
        // The cancelled waiter must not consume the lock — it is free again
        Optional<AsyncLock.Token> next = lock.tryAcquire();
        assertTrue(next.isPresent());
        next.get().release();
    }

    @Test
    public void explicitTokenHandlingWithTryCompose() throws Exception {
        AsyncLock lock = AsyncLock.create();

        Promise<String> result = Promises.tryCompose(
            lock.acquire(),
            token -> Promises.success("worked")
        );

        assertEquals("worked", result.get());
        assertTrue("Token must be released after the action", lock.tryAcquire().isPresent());
    }

    @Test
    public void guardHoldsTheLockDuringActionAndReleasesAfterSuccess() throws Exception {
        AsyncLock lock = AsyncLock.create();
        AtomicBoolean lockHeldDuringAction = new AtomicBoolean(false);

        Promise<String> result = lock.guard(() -> {
            lockHeldDuringAction.set(!lock.tryAcquire().isPresent());
            return Promises.success("done");
        });

        assertEquals("done", result.get());
        assertTrue("Lock must be held while the guarded action runs", lockHeldDuringAction.get());
        assertTrue("Lock must be released after the guarded action", lock.tryAcquire().isPresent());
    }

    @Test
    public void guardReleasesTheLockAfterFailure() {
        AsyncLock lock = AsyncLock.create();
        RuntimeException boom = new RuntimeException("boom");

        Promise<String> result = lock.guard(() -> Promises.failure(boom));

        assertTrue(result.isCompletedExceptionally());
        try {
            result.join();
            fail("Expected the guarded promise to fail");
        } catch (CompletionException expected) {
            assertSame(boom, Promises.unwrapCompletionException(expected));
        }
        assertTrue("Lock must be released even when the action fails", lock.tryAcquire().isPresent());
    }
}