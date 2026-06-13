package kr.lunaf.cloudislands.paper.redis;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class PaperRedisClient implements Closeable {
    private final URI redisUri;
    private final int timeoutMillis;
    private final boolean enabled;
    private final AtomicLong pingsTotal = new AtomicLong();
    private final AtomicLong failuresTotal = new AtomicLong();
    private volatile PingResult lastResult;

    private PaperRedisClient(URI redisUri, Duration timeout, boolean enabled, PingResult initialResult) {
        this.redisUri = redisUri;
        this.timeoutMillis = (int) Math.max(1L, timeout == null ? 1000L : timeout.toMillis());
        this.enabled = enabled;
        this.lastResult = initialResult;
    }

    public static PaperRedisClient create(String redisUri, Duration timeout) {
        if (redisUri == null || redisUri.isBlank()) {
            return new PaperRedisClient(null, timeout, false, PingResult.disabled());
        }
        try {
            return new PaperRedisClient(URI.create(redisUri), timeout, true, PingResult.disabled());
        } catch (IllegalArgumentException exception) {
            return new PaperRedisClient(null, timeout, false, PingResult.unavailable(0.0D, 0L, 0L, "invalid redis uri"));
        }
    }

    public PingResult ping() {
        if (!enabled || redisUri == null) {
            return lastResult;
        }
        long started = System.nanoTime();
        try (Socket socket = new Socket()) {
            String host = redisUri.getHost() == null ? "localhost" : redisUri.getHost();
            int port = redisUri.getPort() < 0 ? 6379 : redisUri.getPort();
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            authenticateIfNeeded(out, in);
            writeCommand(out, "PING");
            String response = readResponse(in);
            if (!response.equalsIgnoreCase("PONG")) {
                throw new IOException("unexpected redis PING response: " + response);
            }
            long pingCount = pingsTotal.incrementAndGet();
            double latency = (System.nanoTime() - started) / 1_000_000_000.0D;
            PingResult result = new PingResult(true, latency, pingCount, failuresTotal.get(), "");
            lastResult = result;
            return result;
        } catch (IOException exception) {
            long failureCount = failuresTotal.incrementAndGet();
            double latency = (System.nanoTime() - started) / 1_000_000_000.0D;
            PingResult result = PingResult.unavailable(latency, pingsTotal.get(), failureCount, exception.getMessage());
            lastResult = result;
            return result;
        }
    }

    @Override
    public void close() {
        lastResult = PingResult.disabled();
    }

    private void authenticateIfNeeded(BufferedOutputStream out, BufferedInputStream in) throws IOException {
        String userInfo = redisUri.getUserInfo();
        if (userInfo == null || userInfo.isBlank()) {
            return;
        }
        int separator = userInfo.indexOf(':');
        if (separator < 0) {
            writeCommand(out, "AUTH", userInfo);
        } else {
            writeCommand(out, "AUTH", userInfo.substring(0, separator), userInfo.substring(separator + 1));
        }
        String response = readResponse(in);
        if (!response.equalsIgnoreCase("OK")) {
            throw new IOException("redis auth failed");
        }
    }

    private static void writeCommand(BufferedOutputStream out, String... values) throws IOException {
        out.write(("*" + values.length + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (String value : values) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            out.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.flush();
    }

    private static String readResponse(BufferedInputStream in) throws IOException {
        int type = in.read();
        if (type < 0) {
            throw new IOException("redis closed connection");
        }
        return switch ((char) type) {
            case '+' -> readLine(in);
            case '-' -> throw new IOException("redis error: " + readLine(in));
            case ':' -> readLine(in);
            case '$' -> readBulkString(in);
            case '*' -> String.join(",", readArray(in));
            default -> throw new IOException("unknown redis response type: " + (char) type);
        };
    }

    private static List<String> readArray(BufferedInputStream in) throws IOException {
        int count = Integer.parseInt(readLine(in));
        List<String> values = new ArrayList<>(Math.max(0, count));
        for (int index = 0; index < count; index++) {
            values.add(readResponse(in));
        }
        return values;
    }

    private static String readBulkString(BufferedInputStream in) throws IOException {
        int length = Integer.parseInt(readLine(in));
        if (length < 0) {
            return "";
        }
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("redis closed bulk string");
        }
        if (in.read() != '\r' || in.read() != '\n') {
            throw new IOException("invalid redis bulk terminator");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String readLine(BufferedInputStream in) throws IOException {
        StringBuilder builder = new StringBuilder();
        while (true) {
            int current = in.read();
            if (current < 0) {
                throw new IOException("redis closed connection");
            }
            if (current == '\r') {
                int next = in.read();
                if (next != '\n') {
                    throw new IOException("invalid redis line terminator");
                }
                return builder.toString();
            }
            builder.append((char) current);
        }
    }

    public record PingResult(boolean available, double latencySeconds, long pingsTotal, long failuresTotal, String error) {
        public static PingResult disabled() {
            return new PingResult(false, 0.0D, 0L, 0L, "disabled");
        }

        private static PingResult unavailable(double latencySeconds, long pingsTotal, long failuresTotal, String error) {
            return new PingResult(false, latencySeconds, pingsTotal, failuresTotal, error == null ? "" : error);
        }
    }
}
