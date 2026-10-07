package net.tascalate.concurrent.channel;

import static net.tascalate.concurrent.channel.TestsShared.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Try;

public class BufferedChannelTest {

    // Factory & Construction

    @Test
    public void rendezvousChannelHasZeroCapacity() {
        Channel<String> ch = Channel.rendezvous();
        assertEquals(0, ch.capacity());
    }

    @Test
    public void bufferedChannelHasGivenCapacity() {
        Channel<String> ch = Channel.buffered(5);
        assertEquals(5, ch.capacity());
    }

    @Test
    public void bufferedChannelRejectsZeroCapacity() {
        assertThrows(IllegalArgumentException.class, () -> Channel.buffered(0));
    }

    @Test
    public void bufferedChannelRejectsNegativeCapacity() {
        assertThrows(IllegalArgumentException.class, () -> Channel.buffered(-1));
    }

    @Test
    public void nilChannelIsSingleton() {
        Channel<String> a = Channel.nil();
        Channel<String> b = Channel.nil();
        assertSame(a, b);
    }

    @Test
    public void nilChannelNeverCompletes() {
        Channel<String> nil = Channel.nil();
        Promise<String> r = nil.receive();
        Promise<String> s = nil.send("x");
        assertFalse(r.isDone());
        assertFalse(s.isDone());
        assertEquals(0, nil.size());
        assertEquals(0, nil.capacity());
        assertFalse(nil.isClosed());
    }

    // Basic Send / Receive

    @Test
    public void bufferedSendAndReceive() {
        Channel<String> ch = Channel.buffered(4);
        ch.send("hello").join();
        assertEquals("hello", ch.receive().join());
    }

    @Test
    public void bufferedSendMultipleValues() {
        Channel<Integer> ch = Channel.buffered(10);
        for (int i = 0; i < 10; i++) {
            ch.send(i).join();
        }
        assertEquals(10, ch.size());
        for (int i = 0; i < 10; i++) {
            assertEquals(i, ch.receive().join().intValue());
        }
    }

    @Test
    public void rendezvousHandoff() {
        Channel<String> ch = Channel.rendezvous();

        // Start receiver in background
        CompletableFuture<String> received = ch.receive().toCompletableFuture();

        // Send must complete the pending receive
        ch.send("handoff").join();
        assertEquals("handoff", received.join());
    }

    @Test
    public void rendezvousSenderBlocksUntilReceiver() {
        Channel<String> ch = Channel.rendezvous();

        Promise<String> sendPromise = ch.send("waiting");
        // JUnit 4 requires the message string as the FIRST parameter
        assertFalse("Sender should block without receiver", sendPromise.isDone());

        String result = ch.receive().join();
        assertEquals("waiting", result);
        assertTrue(sendPromise.isDone());
    }

    @Test
    public void nullValueSentinel() {
        Channel<String> ch = Channel.buffered(4);
        ch.send(null).join();
        assertNull(ch.receive().join());
        // Channel should still work after null
        ch.send("after-null").join();
        assertEquals("after-null", ch.receive().join());
    }

    @Test
    public void sendReturnsSentValue() {
        Channel<String> ch = Channel.buffered(4);
        String sent = ch.send("payload").join();
        assertEquals("payload", sent);
    }

    // trySend / tryReceive

    @Test
    public void tryReceiveReturnsNullWhenEmpty() {
        Channel<String> ch = Channel.buffered(4);
        assertNull(ch.tryReceive());
    }

    @Test
    public void tryReceiveReturnsValueWhenAvailable() {
        Channel<String> ch = Channel.buffered(4);
        ch.send("ready").join();
        Try<String> r = ch.tryReceive();
        assertNotNull(r);
        assertTrue(r.isSuccess());
        assertEquals("ready", r.get());
    }

    @Test
    public void trySendReturnsNullWhenFull() {
        Channel<String> ch = Channel.buffered(1);
        ch.send("fill").join();
        assertNull(ch.trySend("overflow"));
    }

    @Test
    public void trySendReturnsValueWhenSpaceAvailable() {
        Channel<String> ch = Channel.buffered(4);
        Try<String> r = ch.trySend("ok");
        assertNotNull(r);
        assertTrue(r.isSuccess());
        assertEquals("ok", r.get());
    }

    @Test
    public void trySendRendezvousWithWaitingReceiver() {
        Channel<String> ch = Channel.rendezvous();

        // Register a receiver first
        CompletableFuture<String> received = ch.receive().toCompletableFuture();

        Try<String> r = ch.trySend("handoff");
        assertNotNull(r);
        assertTrue(r.isSuccess());
        assertEquals("handoff", received.join());
    }

    //  Close Modes

    @Test
    public void closeDrainAllowsRemainingReceives() {
        Channel<String> ch = Channel.buffered(4);
        ch.send("a").join();
        ch.send("b").join();
        ch.close(ChannelBase.CloseMode.DRAIN);

        assertTrue(ch.isClosed());
        assertEquals(ChannelBase.CloseMode.DRAIN, ch.closedMode());
        assertEquals("a", ch.receive().join());
        assertEquals("b", ch.receive().join());
        assertNull(ch.receive().join()); // EOF
    }

    @Test
    public void closeFailAllRejectsReceives() {
        Channel<String> ch = Channel.buffered(4);
        ch.send("a").join();
        ch.close(ChannelBase.CloseMode.FAIL_ALL);

        assertTrue(ch.isClosed());
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> ch.receive().toCompletableFuture().get());
        assertTrue(ex.getCause() instanceof IllegalStateException);
    }

    @Test
    public void closeFailsWaitingReceivers() {
        Channel<String> ch = Channel.rendezvous();
        CompletableFuture<String> pending = ch.receive().toCompletableFuture();

        ch.close(ChannelBase.CloseMode.FAIL_ALL);

        ExecutionException ex = assertThrows(ExecutionException.class, pending::get);
        assertTrue(ex.getCause() instanceof IllegalStateException);
    }

    @Test
    public void closeDrainResolvesWaitingReceiversWithNull() {
        Channel<String> ch = Channel.rendezvous();
        CompletableFuture<String> pending = ch.receive().toCompletableFuture();

        ch.close(ChannelBase.CloseMode.DRAIN);

        assertNull(pending.join());
    }

    @Test
    public void closeFailsWaitingSenders() {
        Channel<String> ch = Channel.rendezvous();
        CompletableFuture<String> pending = ch.send("blocked").toCompletableFuture();

        ch.close(ChannelBase.CloseMode.FAIL_ALL);

        ExecutionException ex = assertThrows(ExecutionException.class, pending::get);
        assertTrue(ex.getCause() instanceof IllegalStateException);
    }

    @Test
    public void sendToClosedChannelFails() {
        Channel<String> ch = Channel.buffered(4);
        ch.close(ChannelBase.CloseMode.FAIL_ALL);

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> ch.send("late").toCompletableFuture().get());
        assertTrue(ex.getCause() instanceof IllegalStateException);
    }

    @Test
    public void closeIsIdempotent() {
        Channel<String> ch = Channel.buffered(4);
        ch.close(ChannelBase.CloseMode.DRAIN);
        ch.close(ChannelBase.CloseMode.FAIL_ALL); // second close ignored
        assertEquals(ChannelBase.CloseMode.DRAIN, ch.closedMode());
    }

    @Test
    public void defaultCloseUsesFailAll() {
        Channel<String> ch = Channel.buffered(4);
        ch.close(); // AutoCloseable contract
        assertEquals(ChannelBase.CloseMode.FAIL_ALL, ch.closedMode());
    }

    @Test
    public void tryReceiveOnClosedDrainReturnsEOF() {
        Channel<String> ch = Channel.buffered(4);
        ch.close(ChannelBase.CloseMode.DRAIN);
        Try<String> r = ch.tryReceive();
        assertNotNull(r);
        assertTrue(r.isSuccess());
        assertNull(r.get());
    }

    @Test
    public void tryReceiveOnClosedFailAllReturnsFailure() {
        Channel<String> ch = Channel.buffered(4);
        ch.close(ChannelBase.CloseMode.FAIL_ALL);
        Try<String> r = ch.tryReceive();
        assertNotNull(r);
        assertTrue(r.isFailure());
    }

    @Test
    public void trySendOnClosedChannelReturnsFailure() {
        Channel<String> ch = Channel.buffered(4);
        ch.close(ChannelBase.CloseMode.DRAIN);
        Try<String> r = ch.trySend("late");
        assertNotNull(r);
        assertTrue(r.isFailure());
    }

    // Timeout

    @Test
    public void receiveTimeoutWhenNoValue() {
        Channel<String> ch = Channel.buffered(4);
        assertThrows(ExecutionException.class, () ->
            ch.receive(Duration.ofMillis(50)).toCompletableFuture().get()
        );
    }

    @Test
    public void receiveTimeoutNotTriggeredWhenValueArrives() {
        Channel<String> ch = Channel.buffered(4);
        Promise<String> future = ch.receive(Duration.ofSeconds(5));
        ch.send("fast").join();
        assertEquals("fast", future.join());
    }

    // Concurrency

    @Test
    public void multipleProducersAndConsumers() throws Exception {
        Channel<Integer> ch = Channel.buffered(16);
        int producers = 40, consumers = 50, itemsPerProducer = 100000;
        AtomicInteger consumed = new AtomicInteger(0);

        ExecutorService pool = Executors.newCachedThreadPool();
        List<Future<?>> futures = new ArrayList<>();

        for (int p = 0; p < producers; p++) {
            final int offset = p * itemsPerProducer;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < itemsPerProducer; i++) {
                    ch.send(offset + i).join();
                }
            }));
        }

        AtomicInteger totalProduced = new AtomicInteger(producers * itemsPerProducer);
        for (int c = 0; c < consumers; c++) {
            futures.add(pool.submit(() -> {
                while (totalProduced.getAndDecrement() > 0) {
                    Integer v = ch.receive().join();
                    if (v != null) consumed.incrementAndGet();
              }
            }));
        }

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertEquals(producers * itemsPerProducer, consumed.get());
    }

    // forEach

    @Test
    public void forEachConsumesAllValues() {
        Channel<String> ch = Channel.buffered(4);
        ch.send("a").join();
        ch.send("b").join();
        ch.send("c").join();
        ch.close(ChannelBase.CloseMode.DRAIN);

        List<String> collected = new CopyOnWriteArrayList<>();
        ch.forEach(collected::add).join();

        assertEquals(Arrays.asList("a", "b", "c"), collected);
    }

    @Test
    public void forEachWithConditionStopsEarly() {
        Channel<Integer> ch = Channel.buffered(10);
        for (int i = 1; i <= 10; i++) ch.send(i).join();
        ch.close(ChannelBase.CloseMode.DRAIN);

        List<Integer> collected = new CopyOnWriteArrayList<>();
        ch.forEach(collected::add, v -> v <= 5).join();

        assertEquals(Arrays.asList(1, 2, 3, 4, 5), collected);
    }
}