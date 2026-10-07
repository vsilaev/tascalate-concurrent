package net.tascalate.concurrent.channel;

import static net.tascalate.concurrent.channel.TestsShared.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import net.tascalate.concurrent.Promise;

public class SelectTest {

    // Basic Receive Select 

    @Test
    public void selectSingleReadyReceive() {
        Channel<String> ch = Channel.buffered(4);
        ch.send("ready").join();

        SelectResult<String> result = Channel.select(
            SelectCase.receive(ch)
        ).join();

        assertEquals(0, result.index());
        assertEquals("ready", result.value());
        assertFalse(result.match() instanceof SelectCase.Send);
    }

    @Test
    public void selectPicksReadyChannelAmongMultiple() {
        Channel<String> ch1 = Channel.buffered(4);
        Channel<String> ch2 = Channel.buffered(4);
        ch2.send("from-ch2").join();

        SelectResult<String> result = Channel.select(
            SelectCase.receive(ch1),
            SelectCase.receive(ch2)
        ).join();

        assertEquals("from-ch2", result.value());
        assertEquals(1, result.index());
    }

    @Test
    public void selectWaitsForAsyncValue() {
        Channel<String> ch = Channel.buffered(4);

        CompletableFuture<SelectResult<String>> future =
            Channel.select(SelectCase.receive(ch)).toCompletableFuture();

        // JUnit 4 requires the message string as the FIRST parameter
        assertFalse("Should be waiting", future.isDone());

        ch.send("arrived").join();

        SelectResult<String> result = future.join();
        assertEquals("arrived", result.value());
    }

    // Basic Send Select

    @Test
    public void selectSendToReadyChannel() {
        Channel<String> ch = Channel.buffered(4);

        SelectResult<String> result = Channel.select(
            SelectCase.send(ch, "sent-value")
        ).join();

        assertEquals(0, result.index());
        assertTrue(result.match() instanceof SelectCase.Send);
        assertEquals("sent-value", ch.receive().join());
    }

    @Test
    public void selectSendToFullChannelWaits() {
        Channel<String> ch = Channel.buffered(1);
        ch.send("full").join();

        Promise<SelectResult<String>> future =
            Channel.select(SelectCase.send(ch, "waiting"));

        assertFalse(future.isDone());

        ch.receive().join(); // make space
        SelectResult<String> result = future.join();
        assertTrue(result.match() instanceof SelectCase.Send);
    }

    // Default Case

    @Test
    public void selectDefaultWhenNothingReady() {
        Channel<String> ch = Channel.buffered(4);

        SelectResult<String> result = Channel.select(
            SelectCase.receive(ch),
            SelectCase.defaultCase()
        ).join();

        assertNull(result.value());
        assertEquals(1, result.index());
    }

    @Test
    public void selectSkipsDefaultWhenCaseReady() {
        Channel<String> ch = Channel.buffered(4);
        ch.send("data").join();

        SelectResult<String> result = Channel.select(
            SelectCase.receive(ch),
            SelectCase.defaultCase()
        ).join();

        assertEquals("data", result.value());
        assertEquals(0, result.index());
    }

    // Disabled Case

    @Test
    public void selectSkipsDisabledCases() {
        Channel<String> ch1 = Channel.buffered(4);
        Channel<String> ch2 = Channel.buffered(4);
        ch2.send("active").join();

        SelectResult<String> result = Channel.select(
            SelectCase.disabled(),
            SelectCase.receive(ch1),
            SelectCase.receive(ch2)
        ).join();

        assertEquals("active", result.value());
        assertEquals(2, result.index());
    }

    @Test
    public void selectAllDisabledWithDefaultUsesDefault() {
        SelectResult<Object> result = Channel.select(
            SelectCase.disabled(),
            SelectCase.disabled(),
            SelectCase.defaultCase()
        ).join();

        assertEquals(2, result.index());
    }

    // Mixed Send/Receive

    @Test
    public void selectMixedSendAndReceive() {
        Channel<String> sendCh = Channel.buffered(4);
        Channel<String> recvCh = Channel.buffered(4);
        recvCh.send("incoming").join();

        SelectResult<String> result = Channel.select(
            recvCh.receiving(),
            SelectCase.send(sendCh, "outgoing")
        ).join();

        // recvCh is ready, so receive should win (or send to buffered sendCh)
        // Both are ready, so either could win. Just verify no exception.
        assertNotNull(result);
    }

    // Error Propagation

    @Test
    public void selectFailsOnClosedChannel() {
        Channel<String> ch = Channel.buffered(4);
        ch.close(ChannelBase.CloseMode.FAIL_ALL);

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
            Channel.select(SelectCase.receive(ch)).toCompletableFuture().get()
        );
        assertTrue(ex.getCause() instanceof IllegalStateException);
    }

    @Test
    public void selectRequiresAtLeastOneCase() {
        assertThrows(Exception.class, () ->
            Channel.select().join()
        );
    }

    // Concurrency & Race Conditions

    @Test
    public void selectConcurrentRaces() throws Exception {
        int iterations = 100;
        ExecutorService pool = Executors.newCachedThreadPool();

        for (int i = 0; i < iterations; i++) {
            Channel<Integer> ch1 = Channel.buffered(1);
            Channel<Integer> ch2 = Channel.buffered(1);

            CompletableFuture<SelectResult<Integer>> selectFuture =
                Channel.select(
                    SelectCase.receive(ch1),
                    SelectCase.receive(ch2)
                ).toCompletableFuture();

            // Race: both channels get values concurrently
            pool.submit(() -> ch1.send(1).join());
            pool.submit(() -> ch2.send(2).join());

            SelectResult<Integer> result = selectFuture.get(5, TimeUnit.SECONDS);
            assertNotNull(result);
            assertNotNull(result.value());
            assertTrue(result.value().equals(1) || result.value().equals(2));
        }
        pool.shutdown();
    }

    @Test
    public void selectCancelPropagation() {
        Channel<String> ch1 = Channel.buffered(4);
        Channel<String> ch2 = Channel.buffered(4);

        CompletableFuture<SelectResult<String>> selectFuture =
            Channel.select(
                SelectCase.receive(ch1),
                SelectCase.receive(ch2)
            ).toCompletableFuture();

        // ch1 completes first
        ch1.send("winner").join();
        SelectResult<String> result = selectFuture.join();
        assertEquals("winner", result.value());

        // ch2 should still be usable (its waiter was cancelled, not consumed)
        ch2.send("after-select").join();
        assertEquals("after-select", ch2.receive().join());
    }

    @Test
    public void selectDoesNotLoseValuesOnRace() throws Exception {
        int iterations = 50;
        for (int i = 0; i < iterations; i++) {
            Channel<Integer> ch1 = Channel.buffered(1);
            Channel<Integer> ch2 = Channel.buffered(1);

            // Pre-fill both channels
            ch1.send(1).join();
            ch2.send(2).join();

            SelectResult<?> result = Channel.select(
                SelectCase.receive(ch1),
                SelectCase.receive(ch2)
            ).join();

            // One value is consumed, the other must remain
            int consumed = (Integer) result.value();
            int remaining = (consumed == 1) ? 2 : 1;
            Channel<Integer> remainingCh = (consumed == 1) ? ch2 : ch1;

            // JUnit 4 requires the message string as the FIRST parameter
            assertEquals("The losing channel's value must not be lost", remaining, remainingCh.receive().join().intValue());
        }
    }

    // SelectCoordinator Integration

    @Test
    public void selectCoordinatorPreventsDoubleConsumption() throws Exception {
        Channel<String> ch1 = Channel.buffered(4);
        Channel<String> ch2 = Channel.buffered(4);
        ch1.send("val1").join();
        ch2.send("val2").join();

        SelectCoordinator coordinator = SelectCoordinator.createFirstWins();

        Promise<String> r1 = ch1.receive(coordinator);
        Promise<String> r2 = ch2.receive(coordinator);

        // First one should succeed
        assertTrue(r1.isDone() || r2.isDone());

        // At most one should have a real value
        String v1 = r1.isDone() && !r1.isCancelled() ? r1.join() : null;
        String v2 = r2.isDone() && !r2.isCancelled() ? r2.join() : null;

        // JUnit 4 requires the message string as the FIRST parameter
        assertTrue("Exactly one channel should have won the coordination", (v1 != null) != (v2 != null));
    }

    // Typed Select

    @Test
    public void typedSelectReturnsTypedResult() {
        Channel<String> ch = Channel.buffered(4);
        ch.send("typed").join();

        Promise<SelectResult<String>> promise = Channel.select(
            SelectCase.send(ch, "typed")
        );

        SelectResult<String> result = promise.join();
        assertEquals("typed", result.value());
    }

    // NilChannel in Select

    @Test
    public void nilChannelInSelectNeverReady() {
        Channel<String> nil = Channel.nil();
        Channel<String> real = Channel.buffered(4);
        real.send("real").join();

        SelectResult<String> result = Channel.select(
            SelectCase.receive(nil),
            SelectCase.receive(real)
        ).join();

        assertEquals("real", result.value());
        assertEquals(1, result.index());
    }

    @Test
    public void nilChannelWithDefaultUsesDefault() {
        Channel<String> nil = Channel.nil();

        SelectResult<String> result = Channel.select(
            SelectCase.receive(nil),
            SelectCase.defaultCase()
        ).join();

        assertEquals(1, result.index());
    }

    // Select Result Structure

    @Test
    public void selectResultEquality() {
        SelectResult<String> a = new SelectResult<>(0, SelectCase.receive(Channel.nil()), "v");
        SelectResult<String> b = new SelectResult<>(0, SelectCase.receive(Channel.nil()), "v");
        SelectResult<String> c = new SelectResult<>(1, SelectCase.send(Channel.nil(), "v"), "v");

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void selectResultToString() {
        SelectResult<String> r = new SelectResult<>(0, SelectCase.receive(Channel.nil()), "hello");
        String s = r.toString();
        assertTrue(s.contains("index=0"));
        assertTrue(s.contains("hello"));
    }
}