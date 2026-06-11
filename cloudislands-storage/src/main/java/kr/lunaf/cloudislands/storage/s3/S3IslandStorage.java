package kr.lunaf.cloudislands.storage.s3;

import java.io.ByteArrayInputStream;
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
import kr.lunaf.cloudislands.storage.checksum.Sha256Checksums;
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
    public boolean available() throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint.resolve("/" + bucket + "/"))
                .header("Authorization", "Bearer " + bearerToken)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 400;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("storage health check interrupted", exception);
        }
    }

    @Override
    public IslandBundleManifest readManifest(UUID islandId) throws IOException {
        String manifest = request("GET", key(islandId, "manifest.json"), null);
        if (manifest.isBlank()) {
            throw new IOException("missing island manifest: " + islandId);
        }
        return IslandManifestJson.read(manifest);
    }

    @Override
    public InputStream openLatestBundle(UUID islandId) throws IOException {
        String latest = request("GET", key(islandId, "latest"), null).trim();
        byte[] bytes = requestBytes("GET", key(islandId, "snapshots/" + latest + "/bundle.tar.zst"), null);
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public InputStream openSnapshotBundle(UUID islandId, long snapshotNo) throws IOException {
        byte[] bytes = requestBytes("GET", key(islandId, "snapshots/" + String.format("%06d", snapshotNo) + "/bundle.tar.zst"), null);
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        String snapshot = String.format("%06d", snapshotNo);
        byte[] bundleBytes = bundle.readAllBytes();
        String checksum = Sha256Checksums.of(new ByteArrayInputStream(bundleBytes));
        IslandBundleManifest savedManifest = new IslandBundleManifest(manifest.islandId(), manifest.ownerUuid(), manifest.formatVersion(), manifest.minecraftVersion(), manifest.schemaVersion(), manifest.size(), manifest.spawn(), manifest.createdAt(), manifest.savedAt(), checksum);
        requestBytes("PUT", key(islandId, "snapshots/" + snapshot + "/bundle.tar.zst"), bundleBytes);
        requestBytes("PUT", key(islandId, "snapshots/" + snapshot + "/manifest.json"), IslandManifestJson.write(savedManifest).getBytes(StandardCharsets.UTF_8));
        requestBytes("PUT", key(islandId, "snapshots/" + snapshot + "/checksums.sha256"), (checksum + "  bundle.tar.zst\n").getBytes(StandardCharsets.UTF_8));
        requestBytes("PUT", key(islandId, "manifest.json"), IslandManifestJson.write(savedManifest).getBytes(StandardCharsets.UTF_8));
        requestBytes("PUT", key(islandId, "latest"), snapshot.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int pruneSnapshots(UUID islandId, int keepLatest) {
        return 0;
    }

    @Override
    public void deleteIsland(UUID islandId) throws IOException {
        requestBytes("DELETE", "islands/" + islandId + "/", null);
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
