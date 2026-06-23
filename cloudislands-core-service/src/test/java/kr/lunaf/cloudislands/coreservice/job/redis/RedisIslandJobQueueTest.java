package kr.lunaf.cloudislands.coreservice.job.redis;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import org.junit.jupiter.api.Test;

class RedisIslandJobQueueTest {
    @Test
    void retryCanUseRedisClaimHashWithoutLocalClaimState() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        String streamId = "1700000000000-0";
        try (FakeRedis redis = FakeRedis.withClaim(jobId, islandId, streamId)) {
            RedisIslandJobQueue queue = new RedisIslandJobQueue(redis.uri(), Duration.ofSeconds(30));

            assertTrue(queue.retry(jobId));

            assertTrue(redis.commands().contains(List.of("HGETALL", RedisKeys.jobClaim(jobId))));
            assertTrue(redis.commands().contains(List.of("XACK", RedisKeys.jobsStream(), "cloudislands-agents", streamId)));
            assertTrue(redis.commands().contains(List.of("DEL", RedisKeys.jobClaim(jobId))));
            assertTrue(redis.commands().stream().anyMatch(command ->
                command.size() > 2
                    && command.get(0).equals("XADD")
                    && command.get(1).equals(RedisKeys.jobsStream())
                    && command.contains(jobId.toString())
            ));
        }
    }

    @Test
    void cancelCanUseRedisClaimHashWithoutLocalClaimState() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        String streamId = "1700000000001-0";
        try (FakeRedis redis = FakeRedis.withClaim(jobId, islandId, streamId)) {
            RedisIslandJobQueue queue = new RedisIslandJobQueue(redis.uri(), Duration.ofSeconds(30));

            assertTrue(queue.cancel(jobId));

            assertTrue(redis.commands().contains(List.of("HGETALL", RedisKeys.jobClaim(jobId))));
            assertTrue(redis.commands().contains(List.of("XACK", RedisKeys.jobsStream(), "cloudislands-agents", streamId)));
            assertTrue(redis.commands().contains(List.of("DEL", RedisKeys.jobClaim(jobId))));
            assertTrue(redis.commands().stream().anyMatch(command ->
                command.size() > 2
                    && command.get(0).equals("XADD")
                    && command.get(1).equals(RedisKeys.auditStream())
                    && command.contains("JOB_CANCELED")
            ));
        }
    }

    private static final class FakeRedis implements Closeable {
        private final ServerSocket server;
        private final Thread thread;
        private final Map<String, String> claim;
        private final List<List<String>> commands = Collections.synchronizedList(new ArrayList<>());

        private FakeRedis(ServerSocket server, Map<String, String> claim) {
            this.server = server;
            this.claim = claim;
            this.thread = new Thread(this::serve, "fake-redis-job-queue-test");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        static FakeRedis withClaim(UUID jobId, UUID islandId, String streamId) throws IOException {
            ServerSocket server = new ServerSocket(0);
            return new FakeRedis(server, Map.ofEntries(
                Map.entry("jobId", jobId.toString()),
                Map.entry("streamId", streamId),
                Map.entry("claimedByNode", "node-a"),
                Map.entry("claimToken", "claim-token"),
                Map.entry("claimEpoch", "7"),
                Map.entry("leaseExpiresAt", Instant.now().plusSeconds(60).toString()),
                Map.entry("attempt", "7"),
                Map.entry("type", IslandJobType.SAVE_ISLAND.name()),
                Map.entry("islandId", islandId.toString()),
                Map.entry("targetNode", "node-a"),
                Map.entry("priority", "3"),
                Map.entry("createdAt", Instant.EPOCH.toString()),
                Map.entry("payload", "fencingToken=11")
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
                writeArray(output, claim);
            } else if (name.equals("XACK") || name.equals("DEL")) {
                write(output, ":1\r\n");
            } else {
                write(output, "+OK\r\n");
            }
            output.flush();
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
