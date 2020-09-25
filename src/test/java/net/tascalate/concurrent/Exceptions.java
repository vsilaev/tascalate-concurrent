package net.tascalate.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadFactory;

import net.tascalate.concurrent.core.CompletionStageAPI;

public class Exceptions {
    public static void main(final String[] argv) throws InterruptedException, ExecutionException {
        CompletionStageAPI.current();

        CompletableFuture<String> stage1 = CompletableFuture.supplyAsync(() -> {throw new IllegalStateException();});
        stage1.whenComplete((r, e) -> {
            System.out.println(e);
        });
        
        CompletableFuture<String> stage2 = CompletableFuture.completedFuture("ABC");
        stage2.thenApply(s -> {throw new RuntimeException("Z");})
              .handle((r, e) -> {
            System.out.println(e);
            return null;
        });
        
        final ThreadFactory tf = TaskExecutors.newThreadFactory()
            .withNameFormat("CTX-MY-THREAD-%d-OF-%s")
            .withThreadGroup(
                TaskExecutors.newThreadGroup()
                    .withName("Tascalate-Tasks")
                    .withMaxPriority(Thread.NORM_PRIORITY)
                .build()
            )
            .withContextClassLoader(J8Examples.class.getClassLoader())
        .build();
        
        TaskExecutorService executorService = TaskExecutors.newFixedThreadPool(6, tf);
        Promise<String> task1 = CompletableTask.completed("ABC", executorService);
        task1.thenApply(s -> {throw new RuntimeException("Z");})
             .handle((r, e) -> {
                 System.out.println(e);
                 return null;
             });
        executorService.shutdownNow();
    }
}
