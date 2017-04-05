package net.tascalate.concurrent;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class J8Examples {

    public static void main(final String[] argv) throws InterruptedException, ExecutionException {
        final TaskExecutorService executorService = TaskExecutors.newFixedThreadPool(3);
        
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
        ).thenApply(
                l -> l.stream().filter(v -> v != null).collect(Collectors.summingInt((Integer i) -> i.intValue()))
        )
        .thenAccept(J8Examples::onComplete)
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
            Thread.sleep(500);
            if (i % 2 == 0) {
                throw new RuntimeException("Even value: " + i);
            }
            return i * 1000;
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }
    
    private static void onComplete(int i) {
        System.out.println(">>> Result " + i + ", " + Thread.currentThread());
    }
    
    private static Void onError(Throwable i) {
        System.out.println(">>> Error " + i + ", " + Thread.currentThread());
        return null;
    }
}
