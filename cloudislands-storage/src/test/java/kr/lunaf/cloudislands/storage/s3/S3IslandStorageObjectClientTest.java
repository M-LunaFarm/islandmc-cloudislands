package kr.lunaf.cloudislands.storage.s3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.manifest.IslandManifestJson;
import kr.lunaf.cloudislands.storage.object.ObjectStoragePutOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class S3IslandStorageObjectClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void objectClientStreamsMultipartUploadAndTracksMetrics() throws Exception {
        FakeS3 fake = new FakeS3();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", fake::handle);
        server.start();
        S3IslandStorage storage = new S3IslandStorage(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
            "bucket",
            "us-east-1",
            "",
            "",
            "",
            Duration.ofSeconds(5),
            2,
            4L,
            4L
        );
        byte[] payload = "large-bundle-stream".getBytes(StandardCharsets.UTF_8);

        var result = storage.putObject("islands/test/snapshots/000001/bundle.tar.zst", new ByteArrayInputStream(payload), ObjectStoragePutOptions.defaults().withMultipart(4L, 4L));

        assertEquals(payload.length, result.sizeBytes());
        assertEquals(sha256(payload), result.checksum());
        assertTrue(result.multipart());
        assertArrayEquals(payload, storage.openObject("islands/test/snapshots/000001/bundle.tar.zst").readAllBytes());
        assertEquals(payload.length, storage.objectMetrics().uploadedBytes());
        assertEquals(1L, storage.objectMetrics().multipartUploads());
    }

    @Test
    void immutableMultipartUploadDoesNotOverwriteExistingObject() throws Exception {
        FakeS3 fake = new FakeS3();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", fake::handle);
        server.start();
        S3IslandStorage storage = new S3IslandStorage(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
            "bucket",
            "us-east-1",
            "",
            "",
            "",
            Duration.ofSeconds(5),
            2,
            4L,
            4L
        );
        String key = "islands/test/snapshots/000001/bundle.tar.zst";
        byte[] first = "first-bundle-stream".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second-bundle-stream".getBytes(StandardCharsets.UTF_8);
        ObjectStoragePutOptions options = ObjectStoragePutOptions.defaults().asImmutable().withMultipart(4L, 4L);

        storage.putObject(key, new ByteArrayInputStream(first), options);

        assertThrows(IOException.class, () -> storage.putObject(key, new ByteArrayInputStream(second), options));
        assertArrayEquals(first, storage.openObject(key).readAllBytes());
        assertEquals(1L, storage.objectMetrics().putFailures());
        assertTrue(storage.objectMetrics().orphanCleanups() >= 1L);
    }

    @Test
    void writeSnapshotUpdatesLatestWithCreateCas() throws Exception {
        FakeS3 fake = new FakeS3();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", fake::handle);
        server.start();
        S3IslandStorage storage = new S3IslandStorage(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
            "bucket",
            "us-east-1",
            "",
            "",
            "",
            Duration.ofSeconds(5),
            2,
            1024L,
            1024L
        );
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000601");
        byte[] payload = "snapshot-bundle".getBytes(StandardCharsets.UTF_8);

        IslandStorage.StoredBundle stored = storage.writeSnapshot(islandId, 1L, new ByteArrayInputStream(payload), manifest(islandId));

        assertEquals(sha256(payload), stored.checksum());
        assertArrayEquals(payload, storage.openLatestBundle(islandId).readAllBytes());
        assertEquals(stored.checksum(), storage.readManifest(islandId).checksum());
        assertEquals("*", fake.latestIfNoneMatch);
    }

    @Test
    void promoteSnapshotRestoresCompatibilityManifestWhenLatestCasConflicts() throws Exception {
        FakeS3 fake = new FakeS3();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", fake::handle);
        server.start();
        S3IslandStorage storage = new S3IslandStorage(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
            "bucket",
            "us-east-1",
            "",
            "",
            "",
            Duration.ofSeconds(5),
            2,
            1024L,
            1024L
        );
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000604");
        String rootManifestKey = "islands/" + islandId + "/manifest.json";
        byte[] oldManifest = IslandManifestJson.write(manifest(islandId).withStoredBundle("old-checksum", "SHA-256", "zstd", rootManifestKey, 12L)).getBytes(StandardCharsets.UTF_8);
        byte[] promotedManifest = IslandManifestJson.write(manifest(islandId).withStoredBundle("new-checksum", "SHA-256", "zstd", "snapshots/000002/bundle.tar.zst", 13L)).getBytes(StandardCharsets.UTF_8);
        fake.objects.put(rootManifestKey, oldManifest);
        fake.objects.put("islands/" + islandId + "/latest", "000001".getBytes(StandardCharsets.UTF_8));
        fake.objects.put("islands/" + islandId + "/snapshots/000002/manifest.json", promotedManifest);
        fake.latestCasConflict = true;

        IOException exception = assertThrows(IOException.class, () -> storage.promoteSnapshot(islandId, 2L));

        assertTrue(exception.getMessage().contains("CAS conflict"));
        assertEquals("\"6\"", fake.latestIfMatch);
        assertArrayEquals("000001".getBytes(StandardCharsets.UTF_8), fake.objects.get("islands/" + islandId + "/latest"));
        assertArrayEquals(oldManifest, fake.objects.get(rootManifestKey));
        assertTrue(storage.objectMetrics().orphanCleanups() >= 1L);
    }

    @Test
    void promoteBundleStreamsSourceObjectAndRewritesManifestForNewSnapshot() throws Exception {
        FakeS3 fake = new FakeS3();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", fake::handle);
        server.start();
        S3IslandStorage storage = new S3IslandStorage(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
            "bucket",
            "us-east-1",
            "",
            "",
            "",
            Duration.ofSeconds(5),
            2,
            4L,
            4L
        );
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000603");
        String sourcePrefix = "islands/" + islandId + "/backups/delete-000001/";
        String sourceBundleKey = sourcePrefix + "bundle.tar.zst";
        byte[] sourceBundle = "promoted-bundle-stream".getBytes(StandardCharsets.UTF_8);
        IslandBundleManifest sourceManifest = manifest(islandId).withStoredBundle("old-checksum", "SHA-256", "zstd", sourceBundleKey, sourceBundle.length);
        fake.objects.put(sourceBundleKey, sourceBundle);
        fake.objects.put(sourcePrefix + "manifest.json", IslandManifestJson.write(sourceManifest).getBytes(StandardCharsets.UTF_8));

        storage.promoteBundle(islandId, 2L, sourceBundleKey);

        String promotedBundleKey = "islands/" + islandId + "/snapshots/000002/bundle.tar.zst";
        assertArrayEquals(sourceBundle, fake.objects.get(promotedBundleKey));
        IslandBundleManifest promoted = storage.readManifest(islandId);
        assertEquals(promotedBundleKey, promoted.storagePath());
        assertEquals(sha256(sourceBundle), promoted.checksum());
        assertEquals(sourceBundle.length, promoted.sizeBytes());
        assertEquals(sourceBundle.length, storage.objectMetrics().uploadedBytes());
        assertEquals(1L, storage.objectMetrics().multipartUploads());
        assertEquals("*", fake.latestIfNoneMatch);
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    private static IslandBundleManifest manifest(UUID islandId) {
        return new IslandBundleManifest(
            islandId,
            UUID.fromString("00000000-0000-0000-0000-000000000602"),
            3,
            "1.21.11",
            12,
            300,
            new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F),
            List.of("default"),
            List.of(),
            List.of("minecraft:plains"),
            Instant.EPOCH,
            Instant.EPOCH,
            "",
            "SHA-256",
            "zstd",
            "",
            0L,
            "CREATED"
        );
    }

    private static final class FakeS3 {
        private final Map<String, byte[]> objects = new LinkedHashMap<>();
        private final Map<Integer, byte[]> parts = new LinkedHashMap<>();
        private String latestIfNoneMatch = "";
        private String latestIfMatch = "";
        private boolean latestCasConflict = false;

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String key = path.substring("/bucket/".length());
            String query = exchange.getRequestURI().getRawQuery();
            if ("HEAD".equals(exchange.getRequestMethod())) {
                byte[] body = objects.get(key);
                if (body == null) {
                    write(exchange, 404, new byte[0]);
                    return;
                }
                exchange.getResponseHeaders().add("ETag", etag(body));
                write(exchange, 200, new byte[0]);
                return;
            }
            if ("POST".equals(exchange.getRequestMethod()) && "uploads".equals(query)) {
                write(exchange, 200, "<InitiateMultipartUploadResult><UploadId>upload-1</UploadId></InitiateMultipartUploadResult>".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if ("PUT".equals(exchange.getRequestMethod()) && query != null && query.contains("partNumber=")) {
                int partNumber = Integer.parseInt(query.replaceFirst(".*partNumber=([0-9]+).*", "$1"));
                parts.put(partNumber, exchange.getRequestBody().readAllBytes());
                exchange.getResponseHeaders().add("ETag", "\"part-" + partNumber + "\"");
                write(exchange, 200, new byte[0]);
                return;
            }
            if ("POST".equals(exchange.getRequestMethod()) && query != null && query.contains("uploadId=")) {
                int size = parts.values().stream().mapToInt(part -> part.length).sum();
                byte[] combined = new byte[size];
                int offset = 0;
                for (byte[] part : parts.values()) {
                    System.arraycopy(part, 0, combined, offset, part.length);
                    offset += part.length;
                }
                String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
                if ("*".equals(ifNoneMatch) && objects.containsKey(key)) {
                    write(exchange, 412, new byte[0]);
                    return;
                }
                objects.put(key, combined);
                write(exchange, 200, "<CompleteMultipartUploadResult/>".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if ("PUT".equals(exchange.getRequestMethod()) && query == null) {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
                String ifMatch = exchange.getRequestHeaders().getFirst("If-Match");
                if (key.endsWith("/latest")) {
                    latestIfNoneMatch = ifNoneMatch == null ? "" : ifNoneMatch;
                    latestIfMatch = ifMatch == null ? "" : ifMatch;
                    if (latestCasConflict) {
                        write(exchange, 412, new byte[0]);
                        return;
                    }
                }
                if ("*".equals(ifNoneMatch) && objects.containsKey(key)) {
                    write(exchange, 412, new byte[0]);
                    return;
                }
                if (ifMatch != null && !ifMatch.equals(etag(objects.get(key)))) {
                    write(exchange, 412, new byte[0]);
                    return;
                }
                objects.put(key, body);
                exchange.getResponseHeaders().add("ETag", etag(body));
                write(exchange, 200, new byte[0]);
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())) {
                byte[] body = objects.get(key);
                write(exchange, body == null ? 404 : 200, body == null ? new byte[0] : body);
                return;
            }
            write(exchange, 501, new byte[0]);
        }

        private String etag(byte[] body) {
            return "\"" + (body == null ? 0 : body.length) + "\"";
        }

        private void write(HttpExchange exchange, int status, byte[] body) throws IOException {
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }
}
