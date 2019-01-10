/**
 * ﻿Copyright 2015-2018 Valery Silaev (http://vsilaev.com)
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

import java.math.BigInteger;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.tascalate.concurrent.decorators.ExtendedPromiseDecorator;

public class J8Examples {
    
    public static void main(final String[] argv) throws InterruptedException, ExecutionException {
        final TaskExecutorService executorService = TaskExecutors.newFixedThreadPool(6);
        
        @SuppressWarnings("unused")
        Promise<Void>   t1 = Promises.retry(() -> System.out.println("Hello!"), executorService, RetryPolicy.DEFAULT);
        
        @SuppressWarnings("unused")
        Promise<String> t2 = Promises.retry(() -> "Hello!", executorService, RetryPolicy.DEFAULT);
        
        // Must be a block of code in next sample -- otherwise ambiguity
        @SuppressWarnings("unused")
        Promise<Void>   t3 = Promises.retry(ctx -> {System.out.println("Hello!");}, executorService, RetryPolicy.DEFAULT);
        
        @SuppressWarnings("unused")
        Promise<String> t4 = Promises.retry(ctx -> "Hello!", executorService, RetryPolicy.DEFAULT);
        
        @SuppressWarnings("unused")
        Promise<Void> t5 = Promises.retry(J8Examples::nop, executorService, RetryPolicy.DEFAULT);
        
        @SuppressWarnings("unused")
        Promise<Void> t6 = Promises.retry(J8Examples::nopCtx, executorService, RetryPolicy.DEFAULT);

        
        Promise<BigInteger> tryTyping = Promises.retry(
            J8Examples::tryCalc, executorService, 
            RetryPolicy.<Number>create().withResultValidator(v -> v.intValue() > 0).withMaxRetries(2)
        );
        System.out.println( tryTyping.get() );

        Promise<Object> k = CompletableTask.supplyAsync(() -> produceStringSlow("-ABC"), executorService);
        //Promise<Object> k = CompletableTask.complete("ABC", executorService);
        //Promise<Object> k = CompletableTask.supplyAsync(() -> {throw new RuntimeException();}, executorService);
        //Promise<Object> k = Promises.success("ABC");
        //Promise<Object> k = Promises.failure(new RuntimeException());
        k.dependent().delay(Duration.ofMillis(1), true).whenComplete((r, e) -> System.out.println(Thread.currentThread() + " ==> " + r + ", " + e));
        
        /*
        if (System.out != null) {
            Thread.sleep(1000);
            executorService.shutdown();
            return;
        }
        */
        
        Promise<Object> k1 = CompletableTask.supplyAsync(() -> produceStringSlow("-onTimeout1"), executorService);
        k1.onTimeout("ALTERNATE1", Duration.ofMillis(50))
            .whenComplete((r, e) -> System.out.println(Thread.currentThread() + " - onTimeout(value) -> " + r));
        
        Promise<Object> k2 = CompletableTask.supplyAsync(() -> produceStringSlow("-onTimeout2"), executorService);
        k2.onTimeout(() -> "ALTERNATE2", Duration.ofMillis(50))
            .whenComplete((r, e) -> System.out.println(Thread.currentThread() + " - onTimeout(supplier) -> " + r));
        
        Promise<Object> k3 = CompletableTask.supplyAsync(() -> produceStringSlow("-orTimeout"), executorService);
        k3.orTimeout(Duration.ofMillis(30))
            .whenComplete((r, e) -> System.out.println(Thread.currentThread() + " - orTimeout -> " + e));

        
        Thread.sleep(150);

        final Promise<Number> p = Promises.any(
            CompletableTask.supplyAsync(() -> awaitAndProduce1(20, 100), executorService),
            CompletableTask.supplyAsync(() -> awaitAndProduce1(-10, 50), executorService)
        );
        p.whenComplete((r,e) -> {
           System.out.println("Result = " + r + ", Error = " + e); 
        });
        //p.cancel(true);
        p.get();
        
        Promise<String> pollerFuture = Promises.retryFuture( 
            ctx -> pollingFutureMethod(ctx, executorService),
            RetryPolicy.DEFAULT
                       .withMaxRetries(10)
                       .withBackoff(DelayPolicy.fixedInterval(200))
        );
        System.out.println("Poller (future): " + pollerFuture.get());
        
        Promise<String> pollerPlain = Promises.retry(
            J8Examples::pollingMethod, executorService, 
            RetryPolicy.DEFAULT
                       .rejectNullResult()
                       .withMaxRetries(10)
                       .withTimeout(DelayPolicy.fixedInterval(3200))
                       .withBackoff(DelayPolicy.fixedInterval(200).withMinDelay(100).withFirstRetryNoDelay())
        );
        
        System.out.println("Poller (plain): " + pollerPlain.get());

        CompletableTask
            .delay( Duration.ofMillis(100), executorService )
            .thenRun(() -> System.out.println("After initial delay"));

        
        CompletableTask
            .supplyAsync(() -> awaitAndProduceN(73), executorService)
            .as(ExtendedPromiseDecorator<Integer, Promise<Integer>>::new)
            .dependent()
            .thenApply(Function.identity(), true)
            .delay( Duration.ofMillis(100), true, true )
            .thenApply(v -> {
                System.out.println("After delay: " + v);
                return v;
            }, true)
            .onTimeout(123456789, Duration.ofMillis(2000))
            .thenAcceptAsync(J8Examples::onComplete)
            .get(); 
        
        for (int i : Arrays.asList(5, -5, 10, 4)) {
            final Promise<Integer> task1 = executorService.submit(() -> awaitAndProduce1(i, 1500));
            final Promise<Integer> task2 = executorService.submit(() -> awaitAndProduce2(i + 1));
            task1.thenCombineAsync(
                     task2, 
                     (a,b) -> a + b
                 )
                .thenAcceptAsync(J8Examples::onComplete)
                .exceptionally(J8Examples::onError)
                ;
            if (i == 10) {
                Thread.sleep(200);
                task1.cancel(true);
            }
        }
        
        
        Promise<Integer> intermidiate;
        Promises.atLeast(
                4, //Change to 5 or 6 to see the difference -- will end up exceptionally
                executorService.submit(() -> awaitAndProduceN(2)),
                intermidiate = 
                executorService.submit(() -> awaitAndProduceN(3)).thenAcceptAsync(J8Examples::multByX).thenApply((v) -> 1234),
                executorService.submit(() -> awaitAndProduceN(5)),
                executorService.submit(() -> awaitAndProduceN(6)),
                executorService.submit(() -> awaitAndProduceN(7)),                
                executorService.submit(() -> awaitAndProduceN(8)),
                executorService.submit(() -> awaitAndProduceN(11))
        )
        .defaultAsyncOn(executorService)
        .thenApplyAsync(
                l -> l.stream().filter(v -> v != null).collect(Collectors.summingInt((Integer i) -> i.intValue()))
        )
        .thenAcceptAsync(J8Examples::onComplete)
        .exceptionally(J8Examples::onError);
        
        
        executorService.submit(() -> awaitAndProduceN(789)).whenComplete((r, e) -> {
        	if (null == e) {
        		System.out.println("On complete result: " + r);
        	} else {
        		System.out.println("On complete error: " + e);
        	}
        }).thenAccept(System.out::println);
        
        System.out.println("Intermediate result: " + intermidiate.toCompletableFuture().get());
		

        // Suicidal task to close gracefully
        executorService.submit(() -> {
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
            executorService.shutdown();
        });
        
    }
    
    
    private static int multByX(int v) {
    	return v * 100;
    }
    
    private static int awaitAndProduce1(int i, long delay) {
        try {
            System.out.println("Delay I in " + Thread.currentThread());
            Thread.sleep(delay);
            if (i < 0) {
                throw new RuntimeException("Negative value: " + i);
            }
            return i * 10;
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }
    
    private static int awaitAndProduce2(int i) {
        try {
            System.out.println("Delay II in " + Thread.currentThread());
            Thread.sleep(150);
            return i * 10;
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }
    
    private static int awaitAndProduceN(int i) {
        try {
            System.out.println("Delay N + " + i + " in " + Thread.currentThread());
            Thread.sleep(1500);
            if (i % 2 == 0) {
                throw new RuntimeException("Even value: " + i);
            }
            return i * 1000;
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            System.out.println("awaitAndProduceN interrupted, requested value " + i);
            return -1;
        }
    }
    
    
    static String produceStringSlow(String suffix) {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            System.out.println("Interrupted" + suffix + "!!!");
            return "INTERRUPTED" + suffix;
        }
        return "PRODUCED" + suffix;
    }
    
    private static String pollingMethod(RetryContext<String> ctx) throws InterruptedException {
        System.out.println("Polling method, #" + ctx.getRetryCount());
        try {
            if (ctx.getRetryCount() < 5) {
                Thread.sleep((5 - ctx.getRetryCount()) * 1000);
            }
            if (ctx.getRetryCount() < 7) {
                throw new IllegalStateException();
            }
            return "Result " + ctx.getRetryCount();
        } catch (final InterruptedException ex) {
            System.out.println("Polling method, #" + ctx.getRetryCount() + ", interrupted!");
            Thread.currentThread().interrupt();
            throw ex;
        }
    }
    
    private static CompletionStage<String> pollingFutureMethod(RetryContext<String> ctx, Executor executor) throws InterruptedException {
        System.out.println("Polling future, #" + ctx.getRetryCount());
        if (ctx.getRetryCount() < 3) {
            throw new RuntimeException("Fail to start future");
        }
        if (ctx.getRetryCount() < 5) {
            return CompletableTask.supplyAsync(() -> {
                throw new RuntimeException("Fail to complete future");
            }, executor);
        }
        return CompletableTask.supplyAsync(() -> "42", executor);
    }
    
    static BigInteger tryCalc(RetryContext<Number> ctx) {
        return BigInteger.ONE;
    }
    
    static void nop() {
        
    }
    
    static void nopCtx(RetryContext<?> ctx) {
        
    }
    
    private static void onComplete(int i) {
        System.out.println(">>> Result " + i + ", " + Thread.currentThread());
    }
    
    private static Void onError(Throwable i) {
        System.out.println(">>> Error " + i + ", " + Thread.currentThread());
        return null;
    }
}
