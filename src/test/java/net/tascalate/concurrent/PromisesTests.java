package net.tascalate.concurrent;

import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PromisesTests {

    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }
    
    @Test
    public void testGenericArgs() {
        Promise<List<Number>> p = ttt(BigDecimal.valueOf(10), Long.valueOf(11));
        List<Number> v = p.join();
        assertTrue("Fisrt is BigDecimal ", v.get(0) instanceof BigDecimal);
        assertTrue("Second is Long ", v.get(1) instanceof Long);
    }
    
    <T, U extends T, V extends T> Promise<List<T>> ttt(U a, V b) {
        List<Promise<T>> promises = new ArrayList<>();
        promises.add(Promises.success(a));
        promises.add(Promises.success(b));
        Promise<List<T>> all = Promises.all(promises);
        return all;
    }
}
