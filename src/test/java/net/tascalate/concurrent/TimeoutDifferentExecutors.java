package net.tascalate.concurrent;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;

public class TimeoutDifferentExecutors {

    public static void main(String[] argv) {
        String v =
        CompletableTask
           .supplyAsync(() -> {
               try {
                   Thread.sleep(10_000); // sleep 10s
               } catch (InterruptedException e) {
                   System.err.println("Interrupted");
                   throw new CompletionException(e);
               }
               System.out.println("Interrupted? " + (Thread.currentThread().isInterrupted() ? "yes" : "no"));
               return "foo";
           }, Executors.newFixedThreadPool(1))
           .orTimeout(Duration.ofMillis(200), true)
           .join();
        System.out.println(v);
   }
}
