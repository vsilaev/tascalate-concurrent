/**
 * Copyright 2015-2019 Valery Silaev (http://vsilaev.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    public void testGenericArgsWithList() {
        Promise<List<Number>> p = combineWithList(BigDecimal.valueOf(10), Long.valueOf(11));
        List<Number> v = p.join();
        assertTrue("Fisrt is BigDecimal ", v.get(0) instanceof BigDecimal);
        assertTrue("Second is Long ", v.get(1) instanceof Long);
    }
    
    @Test
    public void testGenericArgsWithArray() {
        Promise<List<Number>> p = combineWithArray1(BigDecimal.valueOf(10), Long.valueOf(11));
        List<Number> v = p.join();
        assertTrue("Fisrt is BigDecimal ", v.get(0) instanceof BigDecimal);
        assertTrue("Second is Long ", v.get(1) instanceof Long);
    }

    
    <T, U extends T, V extends T> Promise<List<T>> combineWithList(U a, V b) {
        List<Promise<T>> promises = new ArrayList<>();
        promises.add(Promises.success(a));
        promises.add(Promises.success(b));
        Promise<List<T>> all = Promises.all(promises);
        return all;
    }
    
    <T, U extends T, V extends T> Promise<List<T>> combineWithArray1(U a, V b) {
        @SuppressWarnings("unchecked")
        Promise<T>[] promises = new Promise[2];
        promises[0] = Promises.success(a);
        promises[1] = Promises.success(b);
        Promise<List<T>> all = Promises.all(promises);
        return all;        
    }
    
    <T, U extends T, V extends T> Promise<List<T>> combineWithArray2(U a, V b) {
        Promise<List<T>> all = Promises.all(Promises.success(a), Promises.success(b));
        return all;        
    }
}
