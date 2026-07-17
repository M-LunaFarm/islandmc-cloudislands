package kr.lunaf.cloudislands.coreservice.job.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.job.IslandJobQueue;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.job.JobClaimLease;
import org.junit.jupiter.api.Test;

class RedisIslandJobQueueTest {
    @Test
    void payloadCodecRoundTripsDelimiterPercentNewlineAndUnicodeExactly() {
        Map<String, String> payload = Map.ofEntries(
            Map.entry("literal-percent", "%3B/%3D/%25"),
            Map.entry("delimiter;=key", "value;with=delimiters"),
            Map.entry("multiline", "first\nsecond\r\nthird"),
            Map.entry("unicode", "섬-☁-🏝️"),
            Map.entry("empty", "")
        );

        String encoded = RedisIslandJobQueue.encodePayload(payload);

        assertFalse(encoded.contains("\n"), "wire payload must not contain literal newlines that corrupt RESP array flattening");
        assertEquals(payload, RedisIslandJobQueue.decodePayload(encoded));
    }

    @Test
    void payloadCodecStillReadsLegacyDelimiterFormat() {
        assertEquals(
            Map.of("fencingToken", "7", "path", "a;b=c"),
            RedisIslandJobQueue.decodePayload("fencingToken=7;path=a%3Bb%3Dc")
        );
    }

    @Test
    void retryCanUseRedisClaimHashWithoutLocalClaimState() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        String streamId = "1700000000000-0";
        try (FakeRedis redis = FakeRedis.withExpiredClaim(jobId, islandId, streamId)) {
            RedisIslandJobQueue queue = new RedisIslandJobQueue(redis.uri(), Duration.ofSeconds(30));

            assertTrue(queue.retry(jobId));

            assertTrue(redis.commands().contains(List.of("HGETALL", RedisKeys.jobClaim(jobId))));
            assertTrue(redis.commands().contains(List.of("XACK", RedisKeys.jobsStream(), "cloudislands-agents", streamId)));
            assertTrue(redis.commands().contains(List.of("DEL", RedisKeys.jobClaim(jobId))));
            assertTrue(redis.commands().stream().anyMatch(command -> command.equals(List.of("MULTI"))));
            assertTrue(redis.commands().stream().anyMatch(command -> command.equals(List.of("EXEC"))));
            assertTrue(redis.commands().stream().anyMatch(command ->
                command.size() > 2
                    && command.get(0).equals("XADD")
                    && command.get(1).equals(RedisKeys.jobsStream())
                    && command.contains(jobId.toString())
                    && command.contains("attempt")
                    && command.contains("0")
            ));
        }
    }

    @Test
    void cancelCanUseRedisClaimHashWithoutLocalClaimState() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        String streamId = "1700000000001-0";
        try (FakeRedis redis = FakeRedis.withExpiredClaim(jobId, islandId, streamId)) {
            RedisIslandJobQueue queue = new RedisIslandJobQueue(redis.uri(), Duration.ofSeconds(30));

            assertTrue(queue.cancel(jobId));

            assertTrue(redis.commands().contains(List.of("HGETALL", RedisKeys.jobClaim(jobId))));
            assertTrue(redis.commands().contains(List.of("XACK", RedisKeys.jobsStream(), "cloudislands-agents", streamId)));
            assertTrue(redis.commands().contains(List.of("DEL", RedisKeys.jobClaim(jobId))));
            assertTrue(redis.commands().contains(List.of("MULTI")));
            assertTrue(redis.commands().contains(List.of("EXEC")));
            assertTrue(redis.commands().stream().anyMatch(command ->
                command.size() > 2
                    && command.get(0).equals("XADD")
                    && command.get(1).equals(RedisKeys.auditStream())
                    && command.contains("JOB_CANCELED")
            ));
        }
    }

    @Test
    void completionAckClaimCleanupAndAuditCommitTogether() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        String streamId = "1700000000007-0";
        try (FakeRedis redis = FakeRedis.withClaim(jobId, islandId, streamId, 2)) {
            RedisIslandJobQueue queue = new RedisIslandJobQueue(redis.uri(), Duration.ofSeconds(30));
            JobClaimLease lease = new JobClaimLease(jobId, streamId, "node-a", "claim-token", 7L, Instant.now().plusSeconds(30), 2);

            assertTrue(queue.complete("node-a", jobId, lease));

            List<List<String>> commands = redis.commands();
            int multi = commandIndex(commands, "MULTI", "");
            int ack = commandIndex(commands, "XACK", RedisKeys.jobsStream());
            int cleanup = commandIndex(commands, "DEL", RedisKeys.jobClaim(jobId));
            int audit = commandIndex(commands, "XADD", RedisKeys.auditStream());
            int exec = commandIndex(commands, "EXEC", "");
            assertTrue(multi >= 0 && multi < ack && ack < cleanup && cleanup < audit && audit < exec);
            assertTrue(commands.get(audit).contains("JOB_COMPLETED"));
        }
    }

    @Test
    void administrativeActionsDoNotInvalidateActiveRedisLease() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        String streamId = "1700000000008-0";
        try (FakeRedis redis = FakeRedis.withClaim(jobId, islandId, streamId)) {
            RedisIslandJobQueue queue = new RedisIslandJobQueue(redis.uri(), Duration.ofSeconds(30));

            assertTrue(queue.hasActiveClaim(jobId));
            assertFalse(queue.retry(jobId));
            assertFalse(queue.cancel(jobId));
            assertFalse(redis.commands().contains(List.of("XACK", RedisKeys.jobsStream(), "cloudislands-agents", streamId)));
            assertFalse(redis.commands().contains(List.of("DEL", RedisKeys.jobClaim(jobId))));
        }
    }

    @Test
    void administrativeLockPreventsDuplicateRedisRetry() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        String streamId = "1700000000010-0";
        try (FakeRedis redis = FakeRedis.withExpiredClaim(jobId, islandId, streamId)) {
            redis.holdAdminLock(jobId);
            RedisIslandJobQueue queue = new RedisIslandJobQueue(redis.uri(), Duration.ofSeconds(30));

            assertFalse(queue.retry(jobId));
            assertFalse(redis.commands().stream().anyMatch(command -> command.size() > 1
                && command.get(0).equals("XADD")
                && command.get(1).equals(RedisKeys.jobsStream())));
        }
    }

    @Test
    void transientFailureAtomicallyRequeuesJobWithinRetryBudget() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        String streamId = "1700000000002-0";
        try (FakeRedis redis = FakeRedis.withClaim(jobId, islandId, streamId, 1)) {
            RedisIslandJobQueue queue = new RedisIslandJobQueue(redis.uri(), Duration.ofSeconds(30));
            JobClaimLease lease = new JobClaimLease(jobId, streamId, "node-a", "claim-token", 7L, Instant.now().plusSeconds(30), 1);

            assertEquals(IslandJobQueue.FailureDisposition.RETRY_SCHEDULED, queue.failureDisposition("node-a", jobId, lease));
            IslandJobQueue.FailureDisposition disposition = queue.failClaimed("node-a", jobId, lease, "storage unavailable");

            assertEquals(IslandJobQueue.FailureDisposition.RETRY_SCHEDULED, disposition);
            assertTrue(redis.commands().stream().anyMatch(command ->
                command.size() > 2
                    && command.get(0).equals("XADD")
                    && command.get(1).equals(RedisKeys.jobsStream())
                    && command.contains("attempt")
                    && command.contains("1")
            ));
            assertTrue(redis.commands().stream().anyMatch(command -> command.contains("JOB_RETRY_SCHEDULED")));
            assertTrue(redis.commands().contains(List.of("XACK", RedisKeys.jobsStream(), "cloudislands-agents", streamId)));
            assertEquals(1L, queue.retryAttemptsTotal());
        }
    }

    @Test
    void exhaustedFailureBudgetTerminatesWithoutRequeue() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        String streamId = "1700000000003-0";
        try (FakeRedis redis = FakeRedis.withClaim(jobId, islandId, streamId, 3)) {
            RedisIslandJobQueue queue = new RedisIslandJobQueue(redis.uri(), Duration.ofSeconds(30));
            JobClaimLease lease = new JobClaimLease(jobId, streamId, "node-a", "claim-token", 7L, Instant.now().plusSeconds(30), 3);

            assertEquals(IslandJobQueue.FailureDisposition.TERMINAL, queue.failureDisposition("node-a", jobId, lease));
            IslandJobQueue.FailureDisposition disposition = queue.failClaimed("node-a", jobId, lease, "storage unavailable");

            assertEquals(IslandJobQueue.FailureDisposition.TERMINAL, disposition);
            assertFalse(redis.commands().stream().anyMatch(command ->
                command.size() > 2
                    && command.get(0).equals("XADD")
                    && command.get(1).equals(RedisKeys.jobsStream())
            ));
            assertTrue(redis.commands().stream().anyMatch(command -> command.contains("JOB_FAILED")));
            List<List<String>> commands = redis.commands();
            int multi = commandIndex(commands, "MULTI", "");
            int persist = commandIndex(commands, "HSET", RedisKeys.jobFailure(jobId));
            int index = commandIndex(commands, "ZADD", RedisKeys.failedJobs());
            int ack = commandIndex(commands, "XACK", RedisKeys.jobsStream());
            int cleanup = commandIndex(commands, "DEL", RedisKeys.jobClaim(jobId));
            int audit = commandIndex(commands, "XADD", RedisKeys.auditStream());
            int exec = commandIndex(commands, "EXEC", "");
            assertTrue(multi >= 0 && multi < persist && persist < index && index < ack && ack < cleanup && cleanup < audit && audit < exec);
            assertEquals(0L, queue.retryAttemptsTotal());
        }
    }

    @Test
    void failedJobCanBeRetriedAfterCoreRestart() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        String streamId = "1700000000009-0";
        try (FakeRedis redis = FakeRedis.withClaim(jobId, islandId, streamId, 3)) {
            RedisIslandJobQueue beforeRestart = new RedisIslandJobQueue(redis.uri(), Duration.ofSeconds(30));
            JobClaimLease lease = new JobClaimLease(jobId, streamId, "node-a", "claim-token", 7L, Instant.now().plusSeconds(30), 3);
            assertEquals(IslandJobQueue.FailureDisposition.TERMINAL, beforeRestart.failClaimed("node-a", jobId, lease, "storage unavailable"));

            RedisIslandJobQueue afterRestart = new RedisIslandJobQueue(redis.uri(), Duration.ofSeconds(30));
            assertEquals(1L, afterRestart.countsByState().get("FAILED"));
            assertEquals(jobId.toString(), afterRestart.failedJobs(10).getFirst().get("jobId"));
            assertEquals(jobId.toString(), afterRestart.failedJobs(10).getFirst().get("id"));
            assertEquals("FAILED", afterRestart.failedJobs(10).getFirst().get("state"));
            assertEquals("3", afterRestart.failedJobs(10).getFirst().get("attempts"));
            assertTrue(!afterRestart.failedJobs(10).getFirst().get("updatedAt").isBlank());
            assertEquals("storage unavailable", afterRestart.failedJobs(10).getFirst().get("error"));
            assertTrue(afterRestart.retry(jobId));
            assertEquals(0L, afterRestart.countsByState().get("FAILED"));
            assertTrue(redis.commands().contains(List.of("DEL", RedisKeys.jobFailure(jobId))));
            assertTrue(redis.commands().contains(List.of("ZREM", RedisKeys.failedJobs(), jobId.toString())));
            assertTrue(redis.commands().stream().anyMatch(command -> command.size() == 6
                && command.get(0).equals("SET")
                && command.get(1).equals(RedisKeys.jobAdminLock(jobId))
                && command.get(3).equals("NX")
                && command.get(4).equals("PX")
                && command.get(5).equals("60000")));
            assertTrue(redis.commands().stream().anyMatch(command -> command.size() == 5
                && command.get(0).equals("EVAL")
                && command.get(3).equals(RedisKeys.jobAdminLock(jobId))));
            assertTrue(redis.commands().stream().anyMatch(command ->
                command.size() > 2
                    && command.get(0).equals("XADD")
                    && command.get(1).equals(RedisKeys.jobsStream())
                    && command.contains(jobId.toString())
                    && command.contains("attempt")
                    && command.contains("0")
            ));
        }
    }

    @Test
    void olderWorkerDefersUnknownJobTypeWithoutAcknowledgingIt() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        String streamId = "1700000000004-0";
        try (FakeRedis redis = FakeRedis.withStreamJob(jobId, islandId, streamId, "FUTURE_RESTORE_ISLAND")) {
            RedisIslandJobQueue queue = new RedisIslandJobQueue(redis.uri(), Duration.ofSeconds(30));

            assertTrue(queue.claim("old-island-node", List.of(IslandJobType.SAVE_ISLAND), 1).isEmpty());

            assertFalse(redis.commands().contains(List.of("XACK", RedisKeys.jobsStream(), "cloudislands-agents", streamId)));
            assertTrue(redis.commands().stream().anyMatch(command ->
                command.size() > 2
                    && command.get(0).equals("XADD")
                    && command.get(1).equals(RedisKeys.auditStream())
                    && command.contains("JOB_DEFERRED_UNSUPPORTED")
                    && command.contains("FUTURE_RESTORE_ISLAND")
            ));
        }
    }

    @Test
    void targetMismatchRequeuesBeforeAckInSingleTransaction() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        String streamId = "1700000000005-0";
        try (FakeRedis redis = FakeRedis.withStreamJob(jobId, islandId, streamId, IslandJobType.SAVE_ISLAND.name(), "island-node-b")) {
            RedisIslandJobQueue queue = new RedisIslandJobQueue(redis.uri(), Duration.ofSeconds(30));

            assertTrue(queue.claim("island-node-a", List.of(IslandJobType.SAVE_ISLAND), 1).isEmpty());

            List<List<String>> commands = redis.commands();
            int multi = commandIndex(commands, "MULTI", "");
            int requeue = commandIndex(commands, "XADD", RedisKeys.jobsStream());
            int ack = commandIndex(commands, "XACK", RedisKeys.jobsStream());
            int audit = commandIndex(commands, "XADD", RedisKeys.auditStream());
            int exec = commandIndex(commands, "EXEC", "");
            assertTrue(multi >= 0 && multi < requeue);
            assertTrue(requeue < ack && ack < audit && audit < exec);
            assertTrue(commands.get(audit).contains("JOB_REQUEUED_TARGET_MISMATCH"));
        }
    }

    @Test
    void malformedJobAckAndAuditCommitTogether() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        String streamId = "1700000000006-0";
        try (FakeRedis redis = FakeRedis.withStreamJob(jobId, islandId, streamId, "", "")) {
            RedisIslandJobQueue queue = new RedisIslandJobQueue(redis.uri(), Duration.ofSeconds(30));

            assertTrue(queue.claim("island-node-a", List.of(IslandJobType.SAVE_ISLAND), 1).isEmpty());

            List<List<String>> commands = redis.commands();
            int multi = commandIndex(commands, "MULTI", "");
            int ack = commandIndex(commands, "XACK", RedisKeys.jobsStream());
            int audit = commandIndex(commands, "XADD", RedisKeys.auditStream());
            int exec = commandIndex(commands, "EXEC", "");
            assertTrue(multi >= 0 && multi < ack && ack < audit && audit < exec);
            assertTrue(commands.get(audit).contains("JOB_SKIPPED_MALFORMED"));
        }
    }

    private static int commandIndex(List<List<String>> commands, String name, String key) {
        for (int i = 0; i < commands.size(); i++) {
            List<String> command = commands.get(i);
            if (!command.isEmpty() && command.getFirst().equals(name) && (key.isBlank() || command.contains(key))) {
                return i;
            }
        }
        return -1;
    }

    private static final class FakeRedis implements Closeable {
        private final ServerSocket server;
        private final Thread thread;
        private final Map<String, String> claim;
        private final Map<String, String> failure = Collections.synchronizedMap(new LinkedHashMap<>());
        private final Set<String> failedJobs = Collections.synchronizedSet(new java.util.LinkedHashSet<>());
        private final Map<String, String> adminLocks = Collections.synchronizedMap(new LinkedHashMap<>());
        private final List<String> streamJob;
        private final String streamId;
        private final List<List<String>> commands = Collections.synchronizedList(new ArrayList<>());

        private FakeRedis(ServerSocket server, Map<String, String> claim, String streamId, List<String> streamJob) {
            this.server = server;
            this.claim = Collections.synchronizedMap(new LinkedHashMap<>(claim));
            this.streamId = streamId;
            this.streamJob = streamJob;
            this.thread = new Thread(this::serve, "fake-redis-job-queue-test");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        static FakeRedis withClaim(UUID jobId, UUID islandId, String streamId) throws IOException {
            return withClaim(jobId, islandId, streamId, 7);
        }

        static FakeRedis withExpiredClaim(UUID jobId, UUID islandId, String streamId) throws IOException {
            return withClaim(jobId, islandId, streamId, 7, Instant.now().minusSeconds(60));
        }

        static FakeRedis withClaim(UUID jobId, UUID islandId, String streamId, int attempt) throws IOException {
            return withClaim(jobId, islandId, streamId, attempt, Instant.now().plusSeconds(60));
        }

        static FakeRedis withClaim(UUID jobId, UUID islandId, String streamId, int attempt, Instant leaseExpiresAt) throws IOException {
            ServerSocket server = new ServerSocket(0);
            return new FakeRedis(server, Map.ofEntries(
                Map.entry("jobId", jobId.toString()),
                Map.entry("streamId", streamId),
                Map.entry("claimedByNode", "node-a"),
                Map.entry("claimToken", "claim-token"),
                Map.entry("claimEpoch", "7"),
                Map.entry("leaseExpiresAt", leaseExpiresAt.toString()),
                Map.entry("attempt", Integer.toString(attempt)),
                Map.entry("type", IslandJobType.SAVE_ISLAND.name()),
                Map.entry("islandId", islandId.toString()),
                Map.entry("targetNode", "node-a"),
                Map.entry("priority", "3"),
                Map.entry("createdAt", Instant.EPOCH.toString()),
                Map.entry("payload", "fencingToken=11")
            ), "", List.of());
        }

        static FakeRedis withStreamJob(UUID jobId, UUID islandId, String streamId, String type) throws IOException {
            return withStreamJob(jobId, islandId, streamId, type, "");
        }

        static FakeRedis withStreamJob(UUID jobId, UUID islandId, String streamId, String type, String targetNode) throws IOException {
            ServerSocket server = new ServerSocket(0);
            return new FakeRedis(server, Map.of(), streamId, List.of(
                "jobId", jobId.toString(),
                "type", type,
                "islandId", islandId.toString(),
                "targetNode", targetNode,
                "priority", "3",
                "createdAt", Instant.EPOCH.toString(),
                "attempt", "0",
                "payload", "fencingToken=11"
            ));
        }

        URI uri() {
            return URI.create("redis://127.0.0.1:" + server.getLocalPort());
        }

        List<List<String>> commands() {
            synchronized (commands) {
                return List.copyOf(commands);
            }
        }

        void holdAdminLock(UUID jobId) {
            adminLocks.put(RedisKeys.jobAdminLock(jobId), "other-core");
        }

        private void serve() {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(3000);
                    handle(socket);
                } catch (IOException ignored) {
                    return;
                }
            }
        }

        private void handle(Socket socket) throws IOException {
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
            while (true) {
                List<String> command = readCommand(input);
                if (command == null) {
                    return;
                }
                commands.add(command);
                writeReply(output, command);
            }
        }

        private List<String> readCommand(BufferedInputStream input) throws IOException {
            int prefix = input.read();
            if (prefix < 0) {
                return null;
            }
            if (prefix != '*') {
                throw new IOException("expected redis array");
            }
            int count = Integer.parseInt(readLine(input));
            List<String> values = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                if (input.read() != '$') {
                    throw new IOException("expected redis bulk string");
                }
                int length = Integer.parseInt(readLine(input));
                byte[] bytes = input.readNBytes(length);
                if (bytes.length != length || input.read() != '\r' || input.read() != '\n') {
                    throw new IOException("invalid redis bulk string");
                }
                values.add(new String(bytes, StandardCharsets.UTF_8));
            }
            return values;
        }

        private String readLine(BufferedInputStream input) throws IOException {
            StringBuilder builder = new StringBuilder();
            while (true) {
                int next = input.read();
                if (next < 0) {
                    throw new IOException("closed");
                }
                if (next == '\r') {
                    if (input.read() != '\n') {
                        throw new IOException("invalid line ending");
                    }
                    return builder.toString();
                }
                builder.append((char) next);
            }
        }

        private void writeReply(BufferedOutputStream output, List<String> command) throws IOException {
            String name = command.getFirst();
            if (name.equals("HGETALL")) {
                writeArray(output, command.get(1).endsWith(":claim") ? claim : failure);
            } else if (name.equals("HSET")) {
                Map<String, String> target = command.get(1).endsWith(":claim") ? claim : failure;
                for (int index = 2; index + 1 < command.size(); index += 2) {
                    target.put(command.get(index), command.get(index + 1));
                }
                write(output, ":1\r\n");
            } else if (name.equals("ZADD")) {
                failedJobs.add(command.get(3));
                write(output, ":1\r\n");
            } else if (name.equals("ZREM")) {
                failedJobs.remove(command.get(2));
                write(output, ":1\r\n");
            } else if (name.equals("ZCARD")) {
                write(output, ":" + failedJobs.size() + "\r\n");
            } else if (name.equals("ZREVRANGE")) {
                write(output, "*" + failedJobs.size() + "\r\n");
                for (String jobId : failedJobs) {
                    writeBulk(output, jobId);
                }
            } else if (name.equals("SET") && command.contains("NX")) {
                if (adminLocks.putIfAbsent(command.get(1), command.get(2)) == null) {
                    write(output, "+OK\r\n");
                } else {
                    write(output, "$-1\r\n");
                }
            } else if (name.equals("EVAL")) {
                String key = command.get(3);
                String token = command.get(4);
                if (token.equals(adminLocks.get(key))) {
                    adminLocks.remove(key);
                    write(output, ":1\r\n");
                } else {
                    write(output, ":0\r\n");
                }
            } else if (name.equals("XREADGROUP")) {
                writeStreamJob(output);
            } else if (name.equals("DEL")) {
                for (int index = 1; index < command.size(); index++) {
                    if (command.get(index).endsWith(":claim")) {
                        claim.clear();
                    } else if (command.get(index).endsWith(":failed")) {
                        failure.clear();
                    }
                }
                write(output, ":1\r\n");
            } else if (name.equals("XACK")) {
                write(output, ":1\r\n");
            } else {
                write(output, "+OK\r\n");
            }
            output.flush();
        }

        private void writeStreamJob(BufferedOutputStream output) throws IOException {
            if (streamJob.isEmpty()) {
                write(output, "*-1\r\n");
                return;
            }
            write(output, "*1\r\n*2\r\n");
            writeBulk(output, RedisKeys.jobsStream());
            write(output, "*1\r\n*2\r\n");
            writeBulk(output, streamId);
            write(output, "*" + streamJob.size() + "\r\n");
            for (String value : streamJob) {
                writeBulk(output, value);
            }
        }

        private void writeArray(BufferedOutputStream output, Map<String, String> values) throws IOException {
            write(output, "*" + (values.size() * 2) + "\r\n");
            for (Map.Entry<String, String> entry : values.entrySet()) {
                writeBulk(output, entry.getKey());
                writeBulk(output, entry.getValue());
            }
        }

        private void writeBulk(BufferedOutputStream output, String value) throws IOException {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            write(output, "$" + bytes.length + "\r\n");
            output.write(bytes);
            write(output, "\r\n");
        }

        private void write(BufferedOutputStream output, String value) throws IOException {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            server.close();
            try {
                thread.join(1000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
