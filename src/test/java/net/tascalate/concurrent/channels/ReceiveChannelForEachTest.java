package net.tascalate.concurrent.channels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import net.tascalate.concurrent.channels.ChannelBase.CloseMode;

public class ReceiveChannelForEachTest {

    // ── Basic consumption ─────────────────────────────────────────────

    @Test
    public void forEachConsumesAllElementsUntilDrainClose() throws Exception {
        Channel<Integer> ch = Channel.buffered(10);
        for (int i = 0; i < 5; i++) {
            ch.send(i).join();
        }
        ch.close(CloseMode.DRAIN);

        List<Integer> received = new CopyOnWriteArrayList<>();
        ch.forEach(received::add).join();

        assertEquals(5, received.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(Integer.valueOf(i), received.get(i));
        }
    }

    @Test
    public void forEachOnRendezvousChannel() throws Exception {
        Channel<String> ch = Channel.rendezvous();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch producerDone = new CountDownLatch(1);
            pool.submit(() -> {
                for (int i = 0; i < 3; i++) {
                    ch.send("item-" + i).join();
                }
                ch.close(CloseMode.DRAIN);
                producerDone.countDown();
            });

            List<String> received = new CopyOnWriteArrayList<>();
            ch.forEach(received::add).join();

            assertTrue(producerDone.await(5, TimeUnit.SECONDS));
            assertEquals(3, received.size());
            assertEquals("item-0", received.get(0));
            assertEquals("item-1", received.get(1));
            assertEquals("item-2", received.get(2));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void forEachOnEmptyClosedChannel() throws Exception {
        Channel<Integer> ch = Channel.buffered(4);
        ch.close(CloseMode.DRAIN);

        AtomicInteger count = new AtomicInteger(0);
        ch.forEach(v -> count.incrementAndGet()).join();

        assertEquals(0, count.get());
    }

    // ── Close modes ───────────────────────────────────────────────────

    @Test
    public void forEachFailsOnFailAllClose() throws Exception {
        Channel<Integer> ch = Channel.buffered(10);
        ch.send(1).join();
        ch.send(2).join();
        ch.close(CloseMode.FAIL_ALL);

        List<Integer> received = new CopyOnWriteArrayList<>();
        try {
            ch.forEach(received::add).join();
            fail("Expected exception from FAIL_ALL close");
        } catch (Exception e) {
            // Expected: the loop should terminate exceptionally
            assertTrue(e instanceof IllegalStateException
                    || e.getCause() instanceof IllegalStateException);
        }
    }

    @Test
    public void forEachDrainsBufferBeforeEof() throws Exception {
        Channel<Integer> ch = Channel.buffered(10);
        for (int i = 0; i < 4; i++) {
            ch.send(i).join();
        }
        ch.close(CloseMode.DRAIN);

        List<Integer> received = new CopyOnWriteArrayList<>();
        ch.forEach(received::add).join();

        // All 4 buffered elements must be received before EOF
        assertEquals(4, received.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(Integer.valueOf(i), received.get(i));
        }
    }

    // ── continueCondition (takeWhile semantics) ───────────────────────

    @Test
    public void forEachStopsWhenConditionReturnsFalse() throws Exception {
        Channel<Integer> ch = Channel.buffered(10);
        for (int i = 0; i < 10; i++) {
            ch.send(i).join();
        }
        ch.close(CloseMode.DRAIN);

        List<Integer> received = new CopyOnWriteArrayList<>();
        // Stop when value >= 5 (takeWhile semantics)
        ch.forEach(received::add, v -> v < 5).join();

        assertEquals(5, received.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(Integer.valueOf(i), received.get(i));
        }
    }

    @Test
    public void forEachConditionFalseOnFirstElement() throws Exception {
        Channel<Integer> ch = Channel.buffered(4);
        ch.send(100).join();
        ch.close(CloseMode.DRAIN);

        AtomicInteger count = new AtomicInteger(0);
        ch.forEach(v -> count.incrementAndGet(), v -> false).join();

        assertEquals(0, count.get());
    }

    @Test
    public void forEachConditionAlwaysTrue() throws Exception {
        Channel<Integer> ch = Channel.buffered(4);
        for (int i = 0; i < 3; i++) {
            ch.send(i).join();
        }
        ch.close(CloseMode.DRAIN);

        List<Integer> received = new CopyOnWriteArrayList<>();
        ch.forEach(received::add, v -> true).join();

        assertEquals(3, received.size());
    }

    // ── batchSize variants ────────────────────────────────────────────

    @Test
    public void forEachWithBatchSizeConsumesAll() throws Exception {
        Channel<Integer> ch = Channel.buffered(20);
        for (int i = 0; i < 15; i++) {
            ch.send(i).join();
        }
        ch.close(CloseMode.DRAIN);

        List<Integer> received = new CopyOnWriteArrayList<>();
        // Batch size of 3: should still consume all 15 elements
        ch.forEach(received::add, 3).join();

        assertEquals(15, received.size());
        for (int i = 0; i < 15; i++) {
            assertEquals(Integer.valueOf(i), received.get(i));
        }
    }

    @Test
    public void forEachWithBatchSizeOne() throws Exception {
        Channel<Integer> ch = Channel.buffered(10);
        for (int i = 0; i < 5; i++) {
            ch.send(i).join();
        }
        ch.close(CloseMode.DRAIN);

        List<Integer> received = new CopyOnWriteArrayList<>();
        ch.forEach(received::add, 1).join();

        assertEquals(5, received.size());
    }

    @Test
    public void forEachWithBatchSizeAndCondition() throws Exception {
        Channel<Integer> ch = Channel.buffered(20);
        for (int i = 0; i < 10; i++) {
            ch.send(i).join();
        }
        ch.close(CloseMode.DRAIN);

        List<Integer> received = new CopyOnWriteArrayList<>();
        // Batch size 2, stop at value >= 6
        ch.forEach(received::add, v -> v < 6, 2).join();

        assertEquals(6, received.size());
        for (int i = 0; i < 6; i++) {
            assertEquals(Integer.valueOf(i), received.get(i));
        }
    }

    @Test
    public void forEachWithZeroBatchSizeDrainsAll() throws Exception {
        Channel<Integer> ch = Channel.buffered(10);
        for (int i = 0; i < 8; i++) {
            ch.send(i).join();
        }
        ch.close(CloseMode.DRAIN);

        List<Integer> received = new CopyOnWriteArrayList<>();
        // batchSize = 0 means greedy synchronous drain
        ch.forEach(received::add, 0).join();

        assertEquals(8, received.size());
    }

    @Test
    public void forEachWithNegativeBatchSizeDrainsAll() throws Exception {
        Channel<Integer> ch = Channel.buffered(10);
        for (int i = 0; i < 6; i++) {
            ch.send(i).join();
        }
        ch.close(CloseMode.DRAIN);

        List<Integer> received = new CopyOnWriteArrayList<>();
        ch.forEach(received::add, -1).join();

        assertEquals(6, received.size());
    }

    // ── Null values ───────────────────────────────────────────────────

    @Test
    public void forEachHandlesNullValues() throws Exception {
        Channel<String> ch = Channel.buffered(4);
        ch.send("a").join();
        ch.send(null).join();
        ch.send("b").join();
        ch.close(CloseMode.DRAIN);

        List<String> received = new CopyOnWriteArrayList<>();
        ch.forEach(received::add).join();

        assertEquals(3, received.size());
        assertEquals("a", received.get(0));
        assertEquals(null, received.get(1));
        assertEquals("b", received.get(2));
    }

    // ── Concurrent producer ───────────────────────────────────────────

    @Test
    public void forEachWithConcurrentProducer() throws Exception {
        Channel<Integer> ch = Channel.buffered(4);
        int totalItems = 50;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> {
                for (int i = 0; i < totalItems; i++) {
                    ch.send(i).join();
                }
                ch.close(CloseMode.DRAIN);
            });

            List<Integer> received = new CopyOnWriteArrayList<>();
            ch.forEach(received::add).join();

            assertEquals(totalItems, received.size());
            // Verify ordering is preserved (single producer, FIFO channel)
            for (int i = 0; i < totalItems; i++) {
                assertEquals(Integer.valueOf(i), received.get(i));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void forEachWithConcurrentProducerAndBatchSize() throws Exception {
        Channel<Integer> ch = Channel.buffered(8);
        int totalItems = 30;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> {
                for (int i = 0; i < totalItems; i++) {
                    ch.send(i).join();
                }
                ch.close(CloseMode.DRAIN);
            });

            List<Integer> received = new CopyOnWriteArrayList<>();
            ch.forEach(received::add, 5).join();

            assertEquals(totalItems, received.size());
        } finally {
            pool.shutdownNow();
        }
    }

    // ── Large buffer stress ───────────────────────────────────────────

    @Test
    public void forEachLargeBufferGreedyDrain() throws Exception {
        int totalItems = 10_000;
        Channel<Integer> ch = Channel.buffered(totalItems);
        for (int i = 0; i < totalItems; i++) {
            ch.send(i).join();
        }
        ch.close(CloseMode.DRAIN);

        AtomicInteger count = new AtomicInteger(0);
        long start = System.currentTimeMillis();
        ch.forEach(v -> count.incrementAndGet()).join();
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(totalItems, count.get());
        // Greedy drain of 10k buffered items should be very fast (< 1s)
        assertTrue("Greedy drain took too long: " + elapsed + "ms", elapsed < 1000);
    }

    @Test
    public void forEachLargeBufferWithSmallBatch() throws Exception {
        int totalItems = 1_000;
        Channel<Integer> ch = Channel.buffered(totalItems);
        for (int i = 0; i < totalItems; i++) {
            ch.send(i).join();
        }
        ch.close(CloseMode.DRAIN);

        AtomicInteger count = new AtomicInteger(0);
        ch.forEach(v -> count.incrementAndGet(), 10).join();

        assertEquals(totalItems, count.get());
    }

    // ── Argument validation ───────────────────────────────────────────

    @Test(expected = NullPointerException.class)
    public void forEachRejectsNullAction() {
        Channel<Integer> ch = Channel.buffered(1);
        ch.forEach(null);
    }

    @Test(expected = NullPointerException.class)
    public void forEachRejectsNullCondition() {
        Channel<Integer> ch = Channel.buffered(1);
        ch.forEach(v -> {}, null);
    }

    @Test(expected = NullPointerException.class)
    public void forEachWithBatchRejectsNullAction() {
        Channel<Integer> ch = Channel.buffered(1);
        ch.forEach(null, 5);
    }

    @Test(expected = NullPointerException.class)
    public void forEachFullSignatureRejectsNullAction() {
        Channel<Integer> ch = Channel.buffered(1);
        ch.forEach(null, v -> true, 5);
    }

    @Test(expected = NullPointerException.class)
    public void forEachFullSignatureRejectsNullCondition() {
        Channel<Integer> ch = Channel.buffered(1);
        ch.forEach(v -> {}, null, 5);
    }
}