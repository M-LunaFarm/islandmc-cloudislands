package kr.lunaf.cloudislands.storage.s3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.manifest.IslandManifestJson;

public final class S3IslandStorage implements IslandStorage {
    private final URI endpoint;
    private final String bucket;
    private final String bearerToken;
    private final HttpClient client = HttpClient.newHttpClient();

    public S3IslandStorage(URI endpoint, String bucket, String bearerToken) {
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.bearerToken = bearerToken;
    }

    @Override
    public IslandBundleManifest readManifest(UUID islandId) throws IOException {
        String manifest = request("GET", key(islandId, "manifest.json"), null);
        if (manifest.isBlank()) {
            throw new IOException("missing island manifest: " + islandId);
        }
        return IslandManifestJson.minimal(islandId, new UUID(0L, 0L), "remote");
    }

    @Override
    public InputStream openLatestBundle(UUID islandId) throws IOException {
        String latest = request("GET", key(islandId, "latest"), null).trim();
        byte[] bytes = requestBytes("GET", key(islandId, "snapshots/" + latest + "/bundle.tar.zst"), null);
        return new java.io.ByteArrayInputStream(bytes);
    }

    @Override
    public void writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        String snapshot = String.format("%06d", snapshotNo);
        requestBytes("PUT", key(islandId, "snapshots/" + snapshot + "/bundle.tar.zst"), bundle.readAllBytes());
        requestBytes("PUT", key(islandId, "snapshots/" + snapshot + "/manifest.json"), IslandManifestJson.write(manifest).getBytes(StandardCharsets.UTF_8));
        requestBytes("PUT", key(islandId, "manifest.json"), IslandManifestJson.write(manifest).getBytes(StandardCharsets.UTF_8));
        requestBytes("PUT", key(islandId, "latest"), snapshot.getBytes(StandardCharsets.UTF_8));
    }

    private String key(UUID islandId, String suffix) {
        return "islands/" + islandId + "/" + suffix;
    }

    private String request(String method, String key, byte[] body) throws IOException {
        return new String(requestBytes(method, key, body), StandardCharsets.UTF_8);
    }

    private byte[] requestBytes(String method, String key, byte[] body) throws IOException {
        try {
            HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body);
            HttpRequest request = HttpRequest.newBuilder(endpoint.resolve("/" + bucket + "/" + key))
                .header("Authorization", "Bearer " + bearerToken)
                .method(method, publisher)
                .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("storage request failed with status " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("storage request interrupted", exception);
        }
    }
}
