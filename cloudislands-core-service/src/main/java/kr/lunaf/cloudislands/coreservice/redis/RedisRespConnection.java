package kr.lunaf.cloudislands.coreservice.redis;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLSocketFactory;

public final class RedisRespConnection implements Closeable {
    private static final int CONNECT_TIMEOUT_MILLIS = 3_000;
    private static final int READ_TIMEOUT_MILLIS = 3_000;
    private static final int MAX_BULK_BYTES = 16 * 1024 * 1024;
    private final Socket socket;
    private final BufferedInputStream input;
    private final BufferedOutputStream output;

    public RedisRespConnection(URI redisUri) throws IOException {
        String host = redisUri.getHost() == null ? "localhost" : redisUri.getHost();
        int port = redisUri.getPort() < 0 ? 6379 : redisUri.getPort();
        this.socket = createSocket(redisUri);
        this.socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        this.socket.setSoTimeout(READ_TIMEOUT_MILLIS);
        this.input = new BufferedInputStream(socket.getInputStream());
        this.output = new BufferedOutputStream(socket.getOutputStream());
        String userInfo = redisUri.getUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            String[] parts = userInfo.split(":", 2);
            if (parts.length == 2) {
                command("AUTH", parts[0], parts[1]);
            } else {
                command("AUTH", parts[0]);
            }
        }
    }

    private static Socket createSocket(URI redisUri) throws IOException {
        if ("rediss".equalsIgnoreCase(redisUri.getScheme())) {
            return SSLSocketFactory.getDefault().createSocket();
        }
        return new Socket();
    }

    public synchronized String command(String... args) throws IOException {
        output.write(('*' + Integer.toString(args.length) + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (String arg : args) {
            byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
            output.write(('$' + Integer.toString(bytes.length) + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(bytes);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        output.flush();
        return readReply();
    }

    private String readReply() throws IOException {
        int prefix = input.read();
        if (prefix < 0) {
            throw new IOException("redis closed connection");
        }
        String line = readLine();
        if (prefix == '-') {
            throw new IOException("redis error: " + line);
        }
        if (prefix == '$') {
            int length = parseRedisInteger(line, "bulk length");
            if (length < 0) {
                return "";
            }
            if (length > MAX_BULK_BYTES) {
                throw new IOException("redis bulk string exceeds maximum size: " + length);
            }
            byte[] data = input.readNBytes(length);
            if (data.length != length) {
                throw new IOException("redis closed bulk string");
            }
            if (input.read() != '\r' || input.read() != '\n') {
                throw new IOException("invalid redis bulk terminator");
            }
            return new String(data, StandardCharsets.UTF_8);
        }
        if (prefix == '*') {
            int count = parseRedisInteger(line, "array length");
            if (count < 0) {
                return "";
            }
            List<String> values = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                values.add(readReply());
            }
            return String.join("\n", values);
        }
        return line;
    }

    private int parseRedisInteger(String value, String field) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IOException("invalid redis " + field + ": " + value, exception);
        }
    }

    private String readLine() throws IOException {
        StringBuilder builder = new StringBuilder();
        while (true) {
            int c = input.read();
            if (c < 0) {
                throw new IOException("redis closed connection");
            }
            if (c == '\r') {
                if (input.read() != '\n') {
                    throw new IOException("invalid redis line terminator");
                }
                return builder.toString();
            }
            builder.append((char) c);
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
