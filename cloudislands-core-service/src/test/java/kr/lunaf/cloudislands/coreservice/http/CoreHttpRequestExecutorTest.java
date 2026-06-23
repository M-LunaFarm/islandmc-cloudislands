package kr.lunaf.cloudislands.coreservice.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.lunaf.cloudislands.coreservice.security.AdminEndpointGuard;
import kr.lunaf.cloudislands.coreservice.security.ApiTokenGuard;
import kr.lunaf.cloudislands.coreservice.security.FixedWindowRateLimiter;
import kr.lunaf.cloudislands.coreservice.security.IpAllowlist;
import kr.lunaf.cloudislands.coreservice.security.MtlsHeaderGuard;
import org.junit.jupiter.api.Test;

class CoreHttpRequestExecutorTest {
    @Test
    void executorUsesBoundedWorkersAndMarksSaturatedRequests() throws Exception {
        CoreHttpRequestExecutor executor = new CoreHttpRequestExecutor(1, 1, Duration.ofSeconds(30));
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        AtomicBoolean thirdSawSaturation = new AtomicBoolean(false);

        executor.execute(() -> {
            firstStarted.countDown();
            await(release);
        });
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        executor.execute(() -> await(release));
        executor.execute(() -> thirdSawSaturation.set(CoreHttpRequestExecutor.saturatedRequest()));

        release.countDown();
        executor.shutdownGracefully(Duration.ofSeconds(5));

        assertEquals(1, executor.workerThreads());
        assertEquals(1, executor.queueCapacity());
        assertTrue(thirdSawSaturation.get());
        assertEquals(1L, executor.rejectedTotal());
        assertFalse(CoreHttpRequestExecutor.saturatedRequest());
    }

    @Test
    void saturatedHttpServerRequestsReturnCoreBusy() throws Exception {
        CoreHttpRequestExecutor executor = new CoreHttpRequestExecutor(1, 1, Duration.ofSeconds(30));
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        executor.execute(() -> {
            firstStarted.countDown();
            await(release);
        });
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        executor.execute(() -> await(release));

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CoreHttpRouteRegistrar registrar = new CoreHttpRouteRegistrar(
            new FixedWindowRateLimiter(Clock.systemUTC(), 100, 60_000L),
            new ApiTokenGuard(""),
            new MtlsHeaderGuard(false, "", ""),
            new IpAllowlist(""),
            new AdminEndpointGuard("", true, "*")
        );
        registrar.attach(server);
        registrar.route("/health", exchange -> CoreHttpResponses.write(exchange, 200, "{\"status\":\"UP\"}"));
        server.setExecutor(executor);
        server.start();

        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/health");
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(503, response.statusCode());
            assertTrue(response.body().contains("CORE_BUSY"));
        } finally {
            release.countDown();
            server.stop(0);
            executor.shutdownGracefully(Duration.ofSeconds(5));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
