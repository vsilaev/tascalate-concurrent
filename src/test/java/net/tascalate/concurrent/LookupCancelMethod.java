package net.tascalate.concurrent;

import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;

import net.tascalate.concurrent.core.CancelMethodsCache;

public class LookupCancelMethod {

    @Before
    public void setUp() {
        
    }
    
    @Test
    public void testRegularCancleMethod() {
        CompletableFuture<String> o = CompletableFuture.completedFuture("ABC");
        CancelMethodsCache.cancellationOf(o.getClass()).apply(o, false);
    }
}
