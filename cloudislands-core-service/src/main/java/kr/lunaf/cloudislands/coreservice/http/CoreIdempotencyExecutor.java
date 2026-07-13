package kr.lunaf.cloudislands.coreservice.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class CoreIdempotencyExecutor {
    static final String HEADER = "Idempotency-Key";
    static final String REPLAY_HEADER = "X-CloudIslands-Idempotent-Replay";
    private static final long IN_PROGRESS_WAIT_MILLIS = 2_000L;
    private static final long IN_PROGRESS_POLL_MILLIS = 25L;

    private CoreIdempotencyExecutor() {
    }

    static boolean requested(HttpExchange exchange, String method) {
        return mutationMethod(method) && !key(exchange).isBlank();
    }

    static void execute(
            HttpExchange exchange,
            String requestPath,
            HttpHandler handler,
            CoreIdempotencyStore store,
            RouteInvoker routeInvoker) throws IOException {
        String key = key(exchange);
        if (!validKey(key)) {
            CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 1-200 URL-safe characters"));
            return;
        }
        byte[] requestBody;
        try {
            requestBody = CoreHttpResponses.readBody(exchange).getBytes(StandardCharsets.UTF_8);
        } catch (CoreHttpException exception) {
            CoreHttpResponses.write(exchange, exception.status(), ApiResponses.error(exception.code(), exception.getMessage()));
            return;
        }
        String requestTarget = exchange.getRequestURI() == null ? requestPath : exchange.getRequestURI().toString();
        String fingerprint = fingerprint(exchange.getRequestMethod(), requestTarget, requestBody);
        CoreIdempotencyStore.BeginResult begin;
        try {
            begin = store.begin(key, fingerprint);
        } catch (IllegalStateException exception) {
            CoreHttpResponses.write(exchange, 503, ApiResponses.error("IDEMPOTENCY_UNAVAILABLE", "Core mutation deduplication is temporarily unavailable"));
            return;
        }
        switch (begin.status()) {
            case CONFLICT -> CoreHttpResponses.write(exchange, 409, ApiResponses.error("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was already used for a different request"));
            case IN_PROGRESS -> awaitOwner(exchange, store, key, fingerprint);
            case REPLAY -> replay(exchange, begin.response());
            case OWNER -> executeOwner(exchange, requestPath, handler, store, routeInvoker, key, fingerprint, requestBody);
        }
    }

    private static void awaitOwner(HttpExchange exchange, CoreIdempotencyStore store, String key, String fingerprint) throws IOException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(IN_PROGRESS_WAIT_MILLIS);
        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(IN_PROGRESS_POLL_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            CoreIdempotencyStore.BeginResult current;
            try {
                current = store.begin(key, fingerprint);
            } catch (IllegalStateException exception) {
                CoreHttpResponses.write(exchange, 503, ApiResponses.error("IDEMPOTENCY_UNAVAILABLE", "Core mutation deduplication is temporarily unavailable"));
                return;
            }
            if (current.status() == CoreIdempotencyStore.BeginStatus.REPLAY) {
                replay(exchange, current.response());
                return;
            }
            if (current.status() == CoreIdempotencyStore.BeginStatus.CONFLICT) {
                CoreHttpResponses.write(exchange, 409, ApiResponses.error("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was already used for a different request"));
                return;
            }
        }
        exchange.getResponseHeaders().set("Retry-After", "1");
        CoreHttpResponses.write(exchange, 409, ApiResponses.error("IDEMPOTENCY_IN_PROGRESS", "A request with this Idempotency-Key is still in progress"));
    }

    private static void executeOwner(
            HttpExchange exchange,
            String requestPath,
            HttpHandler handler,
            CoreIdempotencyStore store,
            RouteInvoker routeInvoker,
            String key,
            String fingerprint,
            byte[] requestBody) throws IOException {
        BufferedHttpExchange buffered = new BufferedHttpExchange(exchange, requestBody);
        routeInvoker.invoke(buffered, requestPath, handler);
        CoreIdempotencyStore.StoredResponse response = buffered.storedResponse();
        try {
            store.complete(key, fingerprint, response);
        } catch (IllegalStateException exception) {
            CoreHttpResponses.write(exchange, 503, ApiResponses.error("IDEMPOTENCY_COMMIT_FAILED", "Mutation completed but its deduplication receipt could not be persisted; do not retry automatically"));
            return;
        }
        exchange.getResponseHeaders().set(REPLAY_HEADER, "false");
        CoreHttpResponses.write(exchange, response.status(), response.body(), response.contentType());
    }

    private static void replay(HttpExchange exchange, CoreIdempotencyStore.StoredResponse response) throws IOException {
        if (response == null) {
            CoreHttpResponses.write(exchange, 503, ApiResponses.error("IDEMPOTENCY_RECEIPT_INVALID", "Stored mutation receipt is incomplete"));
            return;
        }
        exchange.getResponseHeaders().set(REPLAY_HEADER, "true");
        CoreHttpResponses.write(exchange, response.status(), response.body(), response.contentType());
    }

    private static String key(HttpExchange exchange) {
        String value = exchange.getRequestHeaders().getFirst(HEADER);
        return value == null ? "" : value.trim();
    }

    private static boolean validKey(String key) {
        if (key.isEmpty() || key.length() > 200) {
            return false;
        }
        for (int index = 0; index < key.length(); index++) {
            char character = key.charAt(index);
            boolean alphaNumeric = character >= 'a' && character <= 'z'
                || character >= 'A' && character <= 'Z'
                || character >= '0' && character <= '9';
            if (!alphaNumeric && character != '.' && character != '_' && character != ':' && character != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean mutationMethod(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private static String fingerprint(String method, String path, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((method == null ? "" : method).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update((path == null ? "" : path).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(body == null ? new byte[0] : body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @FunctionalInterface
    interface RouteInvoker {
        void invoke(HttpExchange exchange, String requestPath, HttpHandler handler) throws IOException;
    }
}
