package net.tascalate.concurrent.locks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/**
 * Tests for {@link AsyncCountDownLatch} — the latch that is itself a promise.
 *
 * NOTE: {@link #completesWhenCountReachesZero()} and
 * {@link #bulkCountDownCompletesWhenReachingZero()} codify the INTENDED semantics
 * and currently FAIL due to the known off-by-one in {@code countDown(long)}
 * (it checks the previous count instead of the updated one). They pass once the
 * fix (use accumulateAndGet and check the updated value) is applied.
 */
public class AsyncCountDownLatchTest {

    @Test
    public void zeroCountCompletesImmediately() throws Exception {
        AsyncCountDownLatch latch = new AsyncCountDownLatch(0);
        assertTrue(latch.isDone());
        assertEquals(Long.valueOf(0), latch.get());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeInitialCountIsRejected() {
        new AsyncCountDownLatch(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonPositiveDeltaIsRejected() {
        new AsyncCountDownLatch(5).countDown(0);
    }

    @Test
    public void countDownReportsCountAfterDecrement() {
        AsyncCountDownLatch latch = new AsyncCountDownLatch(3);
        assertEquals(2, latch.countDown());
        assertEquals(1, latch.countDown());
        assertFalse(latch.isDone());
    }

    @Test
    public void completesWhenCountReachesZero() throws Exception {
        AsyncCountDownLatch latch = new AsyncCountDownLatch(3);
        AtomicInteger fired = new AtomicInteger();
        latch.thenRun(fired::incrementAndGet);
        latch.thenRun(fired::incrementAndGet);

        latch.countDown();
        latch.countDown();
        assertEquals(0, fired.get());

        latch.countDown(); // count reaches zero here
        assertTrue("Latch must complete when the count reaches zero", latch.isDone());
        assertEquals(2, fired.get());
        assertEquals(Long.valueOf(3), latch.get()); // completes with the INITIAL count
    }

    @Test
    public void bulkCountDownCompletesWhenReachingZero() {
        AsyncCountDownLatch latch = new AsyncCountDownLatch(10);

        latch.countDown(4);
        assertFalse(latch.isDone());

        latch.countDown(6); // reaches zero
        assertTrue("Latch must complete when the count reaches zero", latch.isDone());
    }

    @Test
    public void countIsClampedAtZeroAndExtraCountDownsAreHarmless() {
        AsyncCountDownLatch latch = new AsyncCountDownLatch(2);

        latch.countDown(100); // clamps the count at zero
        assertEquals("countDown returns the count before the call", 0, latch.countDown());
    }

    @Test
    public void subscribersAttachedAfterCompletionRunImmediately() {
        AsyncCountDownLatch latch = new AsyncCountDownLatch(0); // already complete
        AtomicBoolean fired = new AtomicBoolean(false);

        latch.thenRun(() -> fired.set(true));

        assertTrue(fired.get());
    }
}