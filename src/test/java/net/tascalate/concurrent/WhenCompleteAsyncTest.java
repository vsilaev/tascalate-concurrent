/**
 * Copyright 2015-2020 Valery Silaev (http://vsilaev.com) - original author Adam Jurčík
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
