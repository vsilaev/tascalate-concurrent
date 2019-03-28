package net.tascalate.concurrent;

import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class OrTimeoutExceptionTests {
    
    private TaskExecutorService executor;

    @Before
    public void setUp() {
        executor = TaskExecutors.newFixedThreadPool(4);
    }
    
    @After
    public void tearDown() {
        executor.shutdown();
    }
    
    @Test
    public void testExceptionalOrTimeout() {
        Promise<?> p = 
        CompletableTask
            .submit(this::doTask, executor)
            .orTimeout(Duration.ofSeconds(5L))
            .whenComplete((res, err) -> {
                if (res != null)
                    System.out.println("RESULT: " + res);
                else
                    System.out.println("ERROR: " + err.getMessage());
             });
        try {
            p.join();
        } catch (CompletionException ex) {
            assertTrue(ex.getCause() instanceof IllegalStateException);
        }
    }
    
    private String doTask () throws Exception {
        TimeUnit.SECONDS.sleep(3);
        if (null != System.out)
            throw new IllegalStateException("my error");
        return "executed ok";
    }

}
