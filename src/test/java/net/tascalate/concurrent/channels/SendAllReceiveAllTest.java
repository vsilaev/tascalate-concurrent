package net.tascalate.concurrent.channels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import net.tascalate.concurrent.channels.ChannelBase.CloseMode;

public class SendAllReceiveAllTest {

    // ─────────────────────────────────────────────────────────────────
    // sendAll
    // ─────────────────────────────────────────────────────────────────

    @Test
    public void sendAllToBufferedChannelReturnsCount() {
        Channel<Integer> ch = Channel.buffered(10);
        List<Integer> items = Arrays.asList(1, 2, 3, 4, 5);

        int sent = ch.sendAll(items).join();

        assertEquals(5, sent);
        assertEquals(5, ch.size());
    }

    @Test
    public void sendAllEmptyIterableReturnsZero() {
        Channel<Integer> ch = Channel.buffered(4);

        int sent = ch.sendAll(Collections.<Integer>emptyList()).join();

        assertEquals(0, sent);
        assertEquals(0, ch.size());
    }

    @Test
    public void sendAllPreservesOrder() {
        Channel<Integer> ch = Channel.buffered(20);
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add(i);
        }

        ch.sendAll(items).join();
        ch.close(CloseMode.DRAIN);

        List<Integer> received = ch.receiveAll().join();
        assertEquals(items, received);
    }

    @Test
    public void sendAllHandlesNullValues() {
        Channel<String> ch = Channel.buffered(4);
        List<String> items = Arrays.asList("a", null, "b");

        int sent = ch.sendAll(items).join();

        assertEquals(3, sent);
        ch.close(CloseMode.DRAIN);
        List<String> received = ch.receiveAll().join();
        assertEquals(3, received.size());
        assertEquals("a", received.get(0));
        assertEquals(null, received.get(1));
        assertEquals("b", received.get(2));
    }

    @Test
    public void sendAllFailsOnClosedChannel() {
        Channel<Integer> ch = Channel.buffered(4);
        ch.close(CloseMode.FAIL_ALL);

        try {
            ch.sendAll(Arrays.asList(1, 2, 3)).join();
            fail("Expected exception when sending to closed channel");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException
                    || e.getCause() instanceof IllegalStateException);
        }
    }

    @Test
    public void sendAllToRendezvousWithConcurrentConsumer() throws Exception {
        Channel<Integer> ch = Channel.rendezvous();
        int totalItems = 20;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Integer> received = new CopyOnWriteArrayList<>();
            CountDownLatch consumerDone = new CountDownLatch(1);

            pool.submit(() -> {
                for (int i = 0; i < totalItems; i++) {
                    received.add(ch.receive().join());
                }
                consumerDone.countDown();
            });

            List<Integer> items = new ArrayList<>();
            for (int i = 0; i < totalItems; i++) {
                items.add(i);
            }
            int sent = ch.sendAll(items).join();

            assertEquals(totalItems, sent);
            assertTrue(consumerDone.await(5, TimeUnit.SECONDS));
            assertEquals(totalItems, received.size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void sendAllLargeCollectionStress() {
        int totalItems = 10_000;
        Channel<Integer> ch = Channel.buffered(totalItems);
        List<Integer> items = new ArrayList<>(totalItems);
        for (int i = 0; i < totalItems; i++) {
            items.add(i);
        }

        long start = System.currentTimeMillis();
        int sent = ch.sendAll(items).join();
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(totalItems, sent);
        assertTrue("Greedy sendAll took too long: " + elapsed + "ms", elapsed < 1000);
    }

    @Test(expected = NullPointerException.class)
    public void sendAllRejectsNullItems() {
        Channel<Integer> ch = Channel.buffered(1);
        ch.sendAll(null);
    }

    // ─────────────────────────────────────────────────────────────────
    // receiveAll
    // ─────────────────────────────────────────────────────────────────

    @Test
    public void receiveAllDrainsClosedChannel() {
        Channel<Integer> ch = Channel.buffered(10);
        for (int i = 0; i < 5; i++) {
            ch.send(i).join();
        }
        ch.close(CloseMode.DRAIN);

        List<Integer> received = ch.receiveAll().join();

        assertEquals(5, received.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(Integer.valueOf(i), received.get(i));
        }
    }

    @Test
    public void receiveAllOnEmptyClosedChannelReturnsEmptyList() {
        Channel<Integer> ch = Channel.buffered(4);
        ch.close(CloseMode.DRAIN);

        List<Integer> received = ch.receiveAll().join();

        assertTrue(received.isEmpty());
    }

    @Test
    public void receiveAllWithMaxItemsStopsAtLimit() {
        Channel<Integer> ch = Channel.buffered(10);
        for (int i = 0; i < 10; i++) {
            ch.send(i).join();
        }
        // Note: channel NOT closed; maxItems bounds the receive
        List<Integer> received = ch.receiveAll(4).join();

        assertEquals(4, received.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(Integer.valueOf(i), received.get(i));
        }
        // Remaining items still in the channel
        assertEquals(6, ch.size());
    }

    @Test
    public void receiveAllWithZeroMaxItemsMeansUnlimited() {
        Channel<Integer> ch = Channel.buffered(10);
        for (int i = 0; i < 6; i++) {
            ch.send(i).join();
        }
        ch.close(CloseMode.DRAIN);

        // maxItems = 0 -> unlimited
        List<Integer> received = ch.receiveAll(0).join();

        assertEquals(6, received.size());
    }

    @Test
    public void receiveAllWithNegativeMaxItemsMeansUnlimited() {
        Channel<Integer> ch = Channel.buffered(10);
        for (int i = 0; i < 4; i++) {
            ch.send(i).join();
        }
        ch.close(CloseMode.DRAIN);

        List<Integer> received = ch.receiveAll(-1).join();

        assertEquals(4, received.size());
    }

    @Test
    public void receiveAllWithMaxItemsGreaterThanAvailable() {
        Channel<Integer> ch = Channel.buffered(10);
        for (int i = 0; i < 3; i++) {
            ch.send(i).join();
        }
        ch.close(CloseMode.DRAIN);

        // maxItems (100) > available (3) -> receive all available
        List<Integer> received = ch.receiveAll(100).join();

        assertEquals(3, received.size());
    }

    @Test
    public void receiveAllHandlesNullValues() {
        Channel<String> ch = Channel.buffered(4);
        ch.send("a").join();
        ch.send(null).join();
        ch.send("b").join();
        ch.close(CloseMode.DRAIN);

        List<String> received = ch.receiveAll().join();

        assertEquals(3, received.size());
        assertEquals("a", received.get(0));
        assertEquals(null, received.get(1));
        assertEquals("b", received.get(2));
    }

    @Test
    public void receiveAllFailsOnFailAllClose() {
        Channel<Integer> ch = Channel.buffered(10);
        ch.send(1).join();
        ch.close(CloseMode.FAIL_ALL);

        try {
            ch.receiveAll().join();
            fail("Expected exception from FAIL_ALL close");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException
                    || e.getCause() instanceof IllegalStateException);
        }
    }

    @Test
    public void receiveAllWithConcurrentProducer() throws Exception {
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

            List<Integer> received = ch.receiveAll().join();

            assertEquals(totalItems, received.size());
            // Single producer, FIFO channel -> order preserved
            for (int i = 0; i < totalItems; i++) {
                assertEquals(Integer.valueOf(i), received.get(i));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void receiveAllOnRendezvousWithConcurrentProducer() throws Exception {
        Channel<String> ch = Channel.rendezvous();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> {
                for (int i = 0; i < 3; i++) {
                    ch.send("item-" + i).join();
                }
                ch.close(CloseMode.DRAIN);
            });

            List<String> received = ch.receiveAll().join();

            assertEquals(3, received.size());
            assertEquals("item-0", received.get(0));
            assertEquals("item-1", received.get(1));
            assertEquals("item-2", received.get(2));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void receiveAllLargeBufferStress() {
        int totalItems = 10_000;
        Channel<Integer> ch = Channel.buffered(totalItems);
        for (int i = 0; i < totalItems; i++) {
            ch.send(i).join();
        }
        ch.close(CloseMode.DRAIN);

        long start = System.currentTimeMillis();
        List<Integer> received = ch.receiveAll().join();
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(totalItems, received.size());
        assertTrue("Greedy receiveAll took too long: " + elapsed + "ms", elapsed < 1000);
    }

    // ─────────────────────────────────────────────────────────────────
    // sendAll + receiveAll round trip
    // ─────────────────────────────────────────────────────────────────

    @Test
    public void sendAllThenReceiveAllRoundTrip() {
        Channel<Integer> ch = Channel.buffered(100);
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 42; i++) {
            items.add(i);
        }

        int sent = ch.sendAll(items).join();
        assertEquals(42, sent);

        ch.close(CloseMode.DRAIN);
        List<Integer> received = ch.receiveAll().join();

        assertEquals(items, received);
    }

    @Test
    public void sendAllThenBoundedReceiveAll() {
        Channel<Integer> ch = Channel.buffered(100);
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add(i);
        }

        ch.sendAll(items).join();

        // Receive only first 4, leave the rest
        List<Integer> first = ch.receiveAll(4).join();
        assertEquals(4, first.size());
        assertEquals(Arrays.asList(0, 1, 2, 3), first);

        // Receive the rest
        ch.close(CloseMode.DRAIN);
        List<Integer> rest = ch.receiveAll().join();
        assertEquals(6, rest.size());
        assertEquals(Arrays.asList(4, 5, 6, 7, 8, 9), rest);
    }
}