package net.tascalate.concurrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import org.junit.Test;

public class PromisesTryResourcesTest {

    static class SyncCloseable implements AutoCloseable {
        boolean closed = false;
        @Override
        public void close() { closed = true; }
    }

    static class AsyncCloseableImpl implements AsyncCloseable {
        boolean closed = false;
        @Override
        public CompletionStage<Void> close() {
            closed = true;
            return Promises.success(null);
        }
    }

    @Test
    public void testTryApplySuccess() throws Exception {
        SyncCloseable res = new SyncCloseable();
        Promise<String> p = Promises.tryApply(Promises.success(res), r -> "Result");
        
        assertEquals("Result", p.get());
        assertTrue("Resource must be closed on success", res.closed);
    }

    @Test
    public void testTryApplyFailure() throws Exception {
        SyncCloseable res = new SyncCloseable();
        Promise<String> p = Promises.tryApply(Promises.success(res), r -> {
            throw new RuntimeException("Action fail");
        });
        
        try {
            p.get();
            fail("Should throw ExecutionException");
        } catch (ExecutionException e) {
            assertEquals("Action fail", e.getCause().getMessage());
        }
        assertTrue("Resource must be closed even on action failure", res.closed);
    }

    @Test
    public void testTryComposeExSuccess() throws Exception {
        AsyncCloseableImpl res = new AsyncCloseableImpl();
        Promise<String> p = Promises.tryComposeEx(
            Promises.success(res), 
            r -> Promises.success("Async Result")
        );
        
        assertEquals("Async Result", p.get());
        assertTrue("Async resource must be closed on success", res.closed);
    }
    
    @Test
    public void testTryComposeExFailure() throws Exception {
        AsyncCloseableImpl res = new AsyncCloseableImpl();
        Promise<String> p = Promises.tryComposeEx(
            Promises.success(res), 
            r -> Promises.failure(new RuntimeException("Async Fail"))
        );
        
        try {
            p.get();
            fail("Should throw ExecutionException");
        } catch (ExecutionException e) {
            assertEquals("Async Fail", e.getCause().getMessage());
        }
        assertTrue("Async resource must be closed even on async failure", res.closed);
    }
}