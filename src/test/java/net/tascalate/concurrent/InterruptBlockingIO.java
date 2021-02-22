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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.tascalate.concurrent.io.BlockingIO;

public class InterruptBlockingIO {
    
    public static void main(String[] argv) throws Exception {
        ExecutorService executors = Executors.newFixedThreadPool(10);

        try {
            long startTime = System.currentTimeMillis();
            List<Promise<String>> promises = getPromises(executors);
            System.out.println("Futures size:" + promises.size());
            Promises.all(false, promises).join();
            System.out.println("All futures are completed in " + (System.currentTimeMillis() - startTime) + "ms");
        } finally {
            executors.shutdownNow();
        }
   }    
    
    private static List<Promise<String>> getPromises(ExecutorService executors) {
        List<Promise<String>> futures = new ArrayList<>();
        futures.add(get(null, "https://deelay.me/500/https://www.github.com", executors));    // returns within 500ms
        futures.add(get(null, "https://deelay.me/40000/https://www.oracle.com", executors));  // returns within 40_000ms (40 seconds)
        futures.add(get(null, "https://deelay.me/1100/https://www.google.com", executors));   // returns within 1100ms
        return futures;
    }
    
    private static Promise<String> get(Object proxy, String url, ExecutorService executors) {
        return CompletableTask.supplyAsync(BlockingIO.interruptible(() -> callHttp(proxy, url)), executors)
                              .onTimeout(() -> {
                                  System.out.println("Timeout with url " + url);
                                  return defaultResponse();
                              }, Duration.ofSeconds(3), true);
    }

    private static String callHttp(Object proxy, String url) {
        System.out.println("Calling " + url + "...");
        Object content;
        try {
            HttpURLConnection conn = (HttpURLConnection)(new URL(url).openConnection());
            BlockingIO.register(() -> conn.disconnect());
            content = conn.getContent();
            System.out.println("Called " + url);
        } catch (IOException ex) {
            System.out.println("ERROR reading " + url + " ==> " + ex.getLocalizedMessage());
            content = null;
        }
        
        return content == null ? null : content.toString();
    }

    private static String defaultResponse() {
        return "Timeout!!!";
    }    
}
