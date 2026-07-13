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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import org.junit.jupiter.api.Test;

class RedisPendingJobRecoveryTest {
    @Test
    void staleRecoveryPreservesAttemptAndAtomicallyRequeuesBeforeAck() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        String streamId = "1700000000100-0";
        try (FakeRedis redis = new FakeRedis(jobId, islandId, streamId, 2)) {
            RedisPendingJobRecovery recovery = new RedisPendingJobRecovery(redis.uri(), 30_000L);

            recovery.claimStale("core-recovery", 10);

            List<List<String>> commands = redis.commands();
            int multi = commandIndex(commands, "MULTI", "");
            int requeue = commandIndex(commands, "XADD", RedisKeys.jobsStream());
            int ack = commandIndex(commands, "XACK", RedisKeys.jobsStream());
            int audit = commandIndex(commands, "XADD", RedisKeys.auditStream());
            int exec = commandIndex(commands, "EXEC", "");
            assertTrue(multi >= 0 && multi < requeue, "recovery transaction must begin before requeue");
            assertTrue(requeue < ack, "the replacement entry must be queued before the stale entry is acknowledged");
            assertTrue(ack < audit && audit < exec, "ack and audit evidence must commit in the same transaction");
            List<String> requeueCommand = commands.get(requeue);
            int attemptKey = requeueCommand.indexOf("attempt");
            assertTrue(attemptKey > 0 && attemptKey + 1 < requeueCommand.size());
            assertTrue(requeueCommand.get(attemptKey + 1).equals("2"), "stale recovery must preserve the consumed attempt count");
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
        private final UUID jobId;
        private final UUID islandId;
        private final String streamId;
        private final int attempt;
        private final List<List<String>> commands = Collections.synchronizedList(new ArrayList<>());

        private FakeRedis(UUID jobId, UUID islandId, String streamId, int attempt) throws IOException {
            this.server = new ServerSocket(0);
            this.jobId = jobId;
            this.islandId = islandId;
            this.streamId = streamId;
            this.attempt = attempt;
            this.thread = new Thread(this::serve, "fake-redis-pending-recovery-test");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        private URI uri() {
            return URI.create("redis://127.0.0.1:" + server.getLocalPort());
        }

        private List<List<String>> commands() {
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
                if (command.getFirst().equals("XAUTOCLAIM")) {
                    writeAutoClaim(output);
                } else if (command.getFirst().equals("XACK") || command.getFirst().equals("DEL")) {
                    write(output, ":1\r\n");
                } else {
                    write(output, "+OK\r\n");
                }
                output.flush();
            }
        }

        private void writeAutoClaim(BufferedOutputStream output) throws IOException {
            List<String> fields = List.of(
                "jobId", jobId.toString(),
                "type", IslandJobType.ACTIVATE_ISLAND.name(),
                "islandId", islandId.toString(),
                "targetNode", "island-node-1",
                "priority", "10",
                "createdAt", Instant.EPOCH.toString(),
                "attempt", Integer.toString(attempt),
                "payload", "fencingToken=7"
            );
            write(output, "*3\r\n");
            writeBulk(output, "0-0");
            write(output, "*1\r\n*2\r\n");
            writeBulk(output, streamId);
            write(output, "*" + fields.size() + "\r\n");
            for (String field : fields) {
                writeBulk(output, field);
            }
            write(output, "*0\r\n");
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
                thread.join(1000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
