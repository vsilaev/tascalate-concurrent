package net.tascalate.concurrent;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Adam Jurčík
 */
public class WhenCompleteAsyncTest {
    
    @Test
    public void testSuppressedSelfAddition() throws InterruptedException, ExecutionException {
        Promise<Throwable> failure = CompletableTask
                .asyncOn(ForkJoinPool.commonPool())
                .thenRunAsync(
                        () -> {
                            throw new CompletionException(new Exception());
                        })
                .whenCompleteAsync((r, ex) -> { /* no action */ })
                .handleAsync((r, ex) -> ex);
        
        Throwable t = failure.get();
        
        Assert.assertThat(t, CoreMatchers.instanceOf(CompletionException.class));
    }
    
}
