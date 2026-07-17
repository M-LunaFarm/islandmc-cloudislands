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
import org.junit.jupiter.api.Test;

class PaperRedisClientTest {
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
}
