package net.tascalate.concurrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import org.junit.Test;

public class PromisesCombinatorsTest {

    @Test
    public void testAllSuccess() throws Exception {
        Promise<List<String>> p = Promises.all(
            Promises.success("A"), 
            Promises.success("B")
        );
        assertEquals(Arrays.asList("A", "B"), p.get());
    }

    @Test
    public void testAllFailure() throws Exception {
        Promise<List<String>> p = Promises.all(
            Promises.success("A"), 
            Promises.failure(new RuntimeException("Fail"))
        );
        try {
            p.get();
            fail("Should throw ExecutionException");
        } catch (ExecutionException e) {
            assertTrue("Expected MultitargetException", e.getCause() instanceof MultitargetException);
        }
    }

    @Test
    public void testAllMap() throws Exception {
        Map<String, CompletionStage<String>> map = new HashMap<>();
        map.put("k1", Promises.success("v1"));
        map.put("k2", Promises.success("v2"));
        
        Promise<Map<String, String>> p = Promises.all(map);
        Map<String, String> res = p.get();
        
        assertEquals("v1", res.get("k1"));
        assertEquals("v2", res.get("k2"));
    }

    @Test
    public void testAnySuccess() throws Exception {
        CompletableFuture<String> f1 = new CompletableFuture<>();
        CompletableFuture<String> f2 = new CompletableFuture<>();
        
        Promise<String> p = Promises.any(f1, f2);
        f2.complete("B"); // f2 wins the race
        
        assertEquals("B", p.get());
    }

    @Test
    public void testAnyStrictFailure() throws Exception {
        // In strict mode, the first exception immediately fails the combined promise
        Promise<String> p = Promises.anyStrict(
            Promises.failure(new RuntimeException("Strict Fail")),
            Promises.success("B")
        );
        try {
            p.get();
            fail("Should throw ExecutionException");
        } catch (ExecutionException e) {
            assertEquals("Strict Fail", e.getCause().getMessage());
        }
    }

    @Test
    public void testAtLeastSuccess() throws Exception {
        Promise<List<String>> p = Promises.atLeast(2, 
            Promises.success("A"), 
            Promises.failure(new RuntimeException()), 
            Promises.success("C")
        );
        List<String> res = p.get();
        assertEquals("A", res.get(0));
        assertEquals("C", res.get(1));
    }

    @Test
    public void testAtLeastOrderedSuccess() throws Exception {
        Promise<List<Optional<String>>> p = Promises.atLeastOrdered(1, 
            Promises.success("A"), 
            Promises.failure(new RuntimeException())
        );
        List<Optional<String>> res = p.get();
        assertEquals(Optional.of("A"), res.get(0));
        assertNull("Failed stage should yield null", res.get(1));
    }
}