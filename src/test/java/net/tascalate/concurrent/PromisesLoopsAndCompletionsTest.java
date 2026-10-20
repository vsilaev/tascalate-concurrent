package net.tascalate.concurrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

public class PromisesLoopsAndCompletionsTest {

    @Test
    public void testLoopBasic() throws Exception {
        Promise<Integer> p = Promises.loop(
            0, 
            val -> val < 5, 
            val -> Promises.success(val + 1)
        );
        assertEquals(Integer.valueOf(5), p.get());
    }

    @Test
    public void testForEachBasic() throws Exception {
        List<String> items = Arrays.asList("A", "B", "C");
        List<String> processed = new ArrayList<>();
        
        Promise<Void> p = Promises.forEach(items, item -> {
            processed.add(item);
            return Promises.TRUE; // true = continue
        });
        
        p.get();
        assertEquals(Arrays.asList("A", "B", "C"), processed);
    }

    @Test
    public void testAsyncCompletionsCompletionOrder() throws Exception {
        List<CompletionStage<String>> stages = new ArrayList<>();
        CompletableFuture<String> f1 = new CompletableFuture<>();
        CompletableFuture<String> f2 = new CompletableFuture<>();
        CompletableFuture<String> f3 = new CompletableFuture<>();
        stages.add(f1);
        stages.add(f2);
        stages.add(f3);

        ForkJoinPool exec = ForkJoinPool.commonPool();
        try (Stream<String> stream = AsyncCompletions.stream(stages, 2)) {
            // Complete in a different order than they were added
            exec.submit(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                f2.complete("B");    
            });
            exec.submit(() -> {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                f1.complete("A");    
            });
            exec.submit(() -> {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                f3.complete("C");   
            });

            List<String> results = stream.collect(Collectors.toList());
            // AsyncCompletions yields results in completion order, not source order
            assertEquals(Arrays.asList("B", "A", "C"), results);
        }
    }
    
    @Test
    public void testAsyncCompletionsCancellation() throws Exception {
        CompletableFuture<String> f1 = new CompletableFuture<>();
        CompletableFuture<String> f2 = new CompletableFuture<>();
        List<CompletionStage<String>> stages = Arrays.asList(f1, f2);

        try (Stream<String> stream = AsyncCompletions.stream(stages, 2, AsyncCompletions.Cancel.ALL)) {
            f1.complete("A");
            Iterator<String> it = stream.iterator();
            assertTrue(it.hasNext());
            
            String first = it.next();
            assertEquals("A", first);
        } // stream closes here, triggering Cancel.ALL
        
        // f2 was enlisted but not consumed; it should be cancelled by the close() policy
        assertTrue("Unconsumed stage must be cancelled on close", f2.isCancelled());
    }
}