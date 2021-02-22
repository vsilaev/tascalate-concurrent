/**
 * Copyright 2015-2020 Valery Silaev (http://vsilaev.com)
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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class InterruptibleAdHoc {

    public static void xmain(String[] argv) {
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
           //.dependent()
           //.thenApply(Function.identity(), true)
           .orTimeout(Duration.ofMillis(200), true)
           .thenApply(Function.identity())
           .join();
        System.out.println(v);
   }
    
    
    public static void main(String[] argv) throws Exception {
        ExecutorService executors = Executors.newFixedThreadPool(10);

        try {
            long startTime = System.currentTimeMillis();
            List<CompletableFuture<String>> futures = getFutures(executors);
            System.out.println("futures size:" + futures.size());
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join();
            System.out.println("all futures completed in " + (System.currentTimeMillis() - startTime) + "ms");
        } finally {
            executors.shutdownNow();
        }
   }    
    
    private static List<CompletableFuture<String>> getFutures(ExecutorService executors) {
        List<CompletableFuture<String>> futures = new ArrayList<>();
        futures.add(get(null, "http://slowwly.robertomurray.co.uk/delay/500/url/https://www.github.com", executors));    // returns within 5s
        futures.add(get(null, "http://slowwly.robertomurray.co.uk/delay/40000/url/https://www.oracle.com", executors));  // returns within 5s
        futures.add(get(null, "http://slowwly.robertomurray.co.uk/delay/700/url/https://www.google.com", executors));    // returns after a min
        return futures;
    }
    
    private static CompletableFuture<String> get(Object proxy, String url, ExecutorService executors) {
        AtomicReference<URLConnection> conn = new AtomicReference<>();
        return CompletableTask
                .supplyAsync(() -> {
                    System.out.println("inside supplyAsync(), calling foo() with url " + url);
                    return foo(proxy, url, conn);
                }, executors)
                .onCancel(() -> {
                    URLConnection x = conn.get();
                    if (null != x) {
                        System.out.println("Disconnecting " + x);
                        ((HttpURLConnection)x).disconnect();
                    }
                })
                .onTimeout(() -> {
                    System.out.println("timeout with url " + url);
                    return defaultResponse();
                }, Duration.ofSeconds(3), true)
                .toCompletableFuture();
    }
    
    /*
    private static String fooz(Object proxy, String url, AtomicReference<URLConnection> ref) {
        System.out.println("inside foo");
        System.out.println("calling " + url);
        //ResponseEntity<String> result = proxy.uri(url).get();
        Object content;
        byte[] buff = new byte[128_000];
        try (InputStream in = Channels.newInputStream(FileChannel.open(Paths.get("E:/Downloads/Music - FLAC/Parov Stelar (FLAC).part3.rar"), StandardOpenOption.READ))) {
            while (in.read(buff) > 0) {
                
            };
            content = "Read done";
        } catch (IOException ex) {
            System.out.println("ERROR reading " + url + " ==> " + ex);
            content = null;
        }
        System.out.println("called " + url );
        
        return content == null ? null : content.toString();
    }
    */

    private static String foo(Object proxy, String url, AtomicReference<URLConnection> ref) {
        System.out.println("inside foo");
        System.out.println("calling " + url);
        //ResponseEntity<String> result = proxy.uri(url).get();
        Object content;
        try {
            ref.compareAndSet(null, new URL(url).openConnection());
            content = ref.get().getContent();
        } catch (IOException ex) {
            System.out.println("ERROR reading " + url + " ==> " + ex.getLocalizedMessage());
            content = null;
        }
        System.out.println("called " + url );
        
        return content == null ? null : content.toString();
    }

    private static String defaultResponse() {
        return "Timeout!!!";
    }    
}
