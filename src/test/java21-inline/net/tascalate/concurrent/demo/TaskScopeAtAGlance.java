package net.tascalate.concurrent.demo;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.TaskScope;
import net.tascalate.concurrent.var.ContextRelay;
import net.tascalate.concurrent.var.ContextVar;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * All-features demo:
 *
 * 1. ContextVar propagation via ContextRelay into forked tasks.
 * 2. Nested TaskScope invoked as a separate fork from the main scope.
 * 3. N-out-of-M quorum scenario in the main path.
 * 4. Scope-level timeout in the main scope.
 * 5. Fork-level timeouts in the main scope.
 */
public class TaskScopeAtAGlance {

    private static final ContextVar<String> TRACE_ID = ContextVar.newInstance("trace-id");

    public static void main(String[] args) {
        System.out.println("=== Tascalate Concurrent: All Features Demo ===\n");

        ContextRelay<?> relay = TRACE_ID.relay();
        String traceId = "REQ-" + System.currentTimeMillis();

        TRACE_ID.bind(traceId).run(() -> {
            System.out.println("[Main] Bound TRACE_ID: " + TRACE_ID.get());

            try {
                executeMainScenario(relay);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        System.out.println("\n=== Demo Finished ===");
    }

    private static void executeMainScenario(ContextRelay<?> relay) throws Exception {
        int totalServers = 10;
        int quorumSize = 5;

        System.out.println("[Main] Starting main scope with 5s scope-level timeout...\n");

        try (TaskScope scope = TaskScope.builder()
                                        .name("MainScope")
                                        .timeout(Duration.ofSeconds(5))
                                        .contextualizer(relay)
                                        .build()) {

            // ------------------------------------------------------------
            // 1. Nested scope invocation as a separate fork from main scope
            // ------------------------------------------------------------
            Promise<String> nestedJob = scope.fork(() -> runNestedScope(scope.toBuilder()));

            // ------------------------------------------------------------
            // 2. Main M tasks with fork-level timeouts
            // ------------------------------------------------------------
            for (int i = 1; i <= totalServers; i++) {
                final int serverId = i;

                scope.fork(() -> fetchFromServer(serverId))
                     // Fork-level timeout: fail fast if this server takes too long.
                     .orTimeout(Duration.ofMillis(1800), true);
            }

            System.out.println("[Main] Waiting for " + quorumSize + " successful main results...\n");

            // ------------------------------------------------------------
            // 3. N-out-of-M quorum from the main path (incl. nested)
            //
            // ------------------------------------------------------------
            List<String> quorum = 
            scope.completions()
                 .filter(Promise.State.SUCCESS)
                 .map(Promise::join)
                 .map(Object::toString)
                 .limit(quorumSize)
                 .toList();

            if (quorum.size() < quorumSize) {
                System.out.println("\n[Main] Quorum NOT reached: only " + quorum.size() + " successful results.");
            } else {
                System.out.println("\n[Main] Quorum reached:");
                quorum.forEach(result -> System.out.println("  -> " + result));
            }

            // ------------------------------------------------------------
            // 4. Wait for the separately forked nested scope job.
            //
            // The nested scope itself was created and consumed inside
            // runNestedScope(...), on the thread executing nestedJob.
            // ------------------------------------------------------------
            System.out.println("\n[Main] Waiting for separately forked nested scope job...");

            try {
                String nestedResults = nestedJob.orTimeout(Duration.ofSeconds(3), true)
                                                .join();

                System.out.println("[Main] Nested scope job completed: " + nestedResults);
            } catch (CompletionException ex) {
                Throwable cause = ex.getCause();
                System.out.println("[Main] Nested scope job did not complete normally: " + cause);
            }

        } finally {
            System.out.println("[Main] Main scope closed.");
        }
    }

    /**
     * This method runs inside a forked task of the main scope.
     *
     * It creates a nested TaskScope, forks tasks inside that nested scope,
     * and consumes their completions. Because it is invoked through
     * scope.fork(...), the nested scope is owned by the worker thread
     * executing this method.
     */
    private static String runNestedScope(TaskScope.Builder parentScopeBuilder) throws Exception {
        System.out.println("  [NestedJob] Started as separate fork. TRACE_ID = " + TRACE_ID.get());

        // Child scope inherits executor and contextualizer from parent scope,
        // but does not inherit the parent's timeout.
        try (TaskScope nestedScope = parentScopeBuilder.name("NestedScope").build()) {

            for (int i = 1; i <= 3; i++) {
                final int detailId = i;

                nestedScope.fork(() -> deepInspect(detailId));
            }

            // Consume nested completions.
            // This runs on the nested scope's owner thread, i.e. the forked task thread.
            return nestedScope.completions()
                              .filter(Promise.State.SUCCESS)
                              .map(Promise::join)
                              .map(String.class::cast)
                              .limit(3)
                              .collect(Collectors.joining(" | "));
        }
    }

    private static String fetchFromServer(int serverId) throws InterruptedException {
        String traceId = TRACE_ID.get();
        int latencyMs = simulatedServerLatency(serverId);

        System.out.printf(
                "  [Server %02d] TRACE_ID=%s fetching... latency=%dms%n",
                serverId,
                traceId,
                latencyMs
        );

        Thread.sleep(latencyMs);

        return "Server-" + serverId + "-OK";
    }

    private static String deepInspect(int detailId) throws InterruptedException {
        String traceId = TRACE_ID.get();

        System.out.printf(
                "    [NestedTask %d] TRACE_ID=%s inspecting detail...%n",
                detailId,
                traceId
        );

        Thread.sleep(300);

        return "NestedDetail-" + detailId + "-OK";
    }

    private static int simulatedServerLatency(int serverId) {
        // Fast servers: successfully join the quorum.
        if (serverId <= 3) {
            return 400;
        }

        // Still fast enough for the fork-level timeout.
        if (serverId <= 6) {
            return 1200;
        }

        // Too slow: hit the fork-level timeout of 1800ms.
        if (serverId <= 8) {
            return 2500;
        }

        // Very slow: would be cancelled by fork timeout, and eventually by
        // the scope timeout if the scope stayed open long enough.
        return 7000;
    }
}