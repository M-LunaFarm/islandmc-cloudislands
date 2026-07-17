package kr.lunaf.cloudislands.paper.redis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfig;
import org.junit.jupiter.api.Test;

class PaperRedisClientTest {
    @Test
    void blankUriKeepsRedisDisabledWithoutSyntheticFailures() {
        PaperRuntimeConfig.Redis config = new PaperRuntimeConfig.Redis("", "", Duration.ofSeconds(1));
        assertTrue(config.uri().isBlank());
        try (PaperRedisClient client = PaperRedisClient.create(config.uri(), config.password(), config.timeout())) {
            PaperRedisClient.PingResult result = client.ping();
            assertFalse(result.available());
            assertTrue(result.failuresTotal() == 0L);
        }
    }

    @Test
    void pingReturnsCachedObservationWithoutBlockingCaller() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch requestReceived = new CountDownLatch(1);
            Thread responder = new Thread(() -> delayedPong(server, requestReceived), "test-redis-responder");
            responder.setDaemon(true);
            responder.start();

            try (PaperRedisClient client = PaperRedisClient.create(
                "redis://127.0.0.1:" + server.getLocalPort(),
                Duration.ofSeconds(2)
            )) {
                long started = System.nanoTime();
                PaperRedisClient.PingResult initial = client.ping();
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

                assertFalse(initial.available());
                assertTrue(elapsedMillis < 250L, "observability must not wait for Redis network I/O");
                assertTrue(requestReceived.await(1, TimeUnit.SECONDS));

                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                PaperRedisClient.PingResult observed = initial;
                while (!observed.available() && System.nanoTime() < deadline) {
                    Thread.sleep(10L);
                    observed = client.ping();
                }
                assertTrue(observed.available());
                assertTrue(observed.pingsTotal() >= 1L);
            }
        }
    }

    @Test
    void passwordIsAuthenticatedBeforePing() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch requestReceived = new CountDownLatch(1);
            Thread responder = new Thread(() -> authenticatedPong(server, requestReceived), "test-authenticated-redis-responder");
            responder.setDaemon(true);
            responder.start();

            try (PaperRedisClient client = PaperRedisClient.create(
                "redis://127.0.0.1:" + server.getLocalPort(),
                "secret",
                Duration.ofSeconds(2)
            )) {
                client.ping();
                assertTrue(requestReceived.await(1, TimeUnit.SECONDS));

                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                PaperRedisClient.PingResult observed = client.ping();
                while (!observed.available() && System.nanoTime() < deadline) {
                    Thread.sleep(10L);
                    observed = client.ping();
                }
                assertTrue(observed.available());
                assertTrue(observed.pingsTotal() >= 1L);
            }
        }
    }

    private static void delayedPong(ServerSocket server, CountDownLatch requestReceived) {
        try (Socket socket = server.accept()) {
            InputStream input = socket.getInputStream();
            byte[] request = input.readNBytes(14);
            if (new String(request, StandardCharsets.UTF_8).contains("PING")) {
                requestReceived.countDown();
            }
            Thread.sleep(500L);
            socket.getOutputStream().write("+PONG\r\n".getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
        } catch (Exception ignored) {
            // Closing the test socket is sufficient cleanup if an assertion fails.
        }
    }

    private static void authenticatedPong(ServerSocket server, CountDownLatch requestReceived) {
        try (Socket socket = server.accept()) {
            InputStream input = socket.getInputStream();
            byte[] auth = input.readNBytes(26);
            if (!new String(auth, StandardCharsets.UTF_8).contains("secret")) {
                return;
            }
            socket.getOutputStream().write("+OK\r\n".getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();

            byte[] ping = input.readNBytes(14);
            if (new String(ping, StandardCharsets.UTF_8).contains("PING")) {
                requestReceived.countDown();
            }
            socket.getOutputStream().write("+PONG\r\n".getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
        } catch (Exception ignored) {
            // Closing the test socket is sufficient cleanup if an assertion fails.
        }
    }
}
