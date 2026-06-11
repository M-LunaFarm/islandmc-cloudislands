package kr.lunaf.cloudislands.storage.s3;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        writeBundle(islandId, "snapshots/" + snapshot, bundle, manifest, true, snapshot);
    }

    @Override
    public void writeDeleteBackup(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        writeBundle(islandId, "backups/delete-" + String.format("%06d", snapshotNo), bundle, manifest, false, "");
    }

    @Override
    public void promoteSnapshot(UUID islandId, long snapshotNo) throws IOException {
        String snapshot = String.format("%06d", snapshotNo);
        byte[] manifest = requestBytes("GET", key(islandId, "snapshots/" + snapshot + "/manifest.json"), null);
        requestBytes("PUT", key(islandId, "manifest.json"), manifest);
        requestBytes("PUT", key(islandId, "latest"), snapshot.getBytes(StandardCharsets.UTF_8));
    }

    private void writeBundle(UUID islandId, String prefix, InputStream bundle, IslandBundleManifest manifest, boolean updateLatest, String latestValue) throws IOException {
        byte[] bundleBytes = bundle.readAllBytes();
        String checksum = Sha256Checksums.of(new ByteArrayInputStream(bundleBytes));
        IslandBundleManifest savedManifest = new IslandBundleManifest(manifest.islandId(), manifest.ownerUuid(), manifest.formatVersion(), manifest.minecraftVersion(), manifest.schemaVersion(), manifest.size(), manifest.spawn(), manifest.createdAt(), manifest.savedAt(), checksum);
        requestBytes("PUT", key(islandId, prefix + "/bundle.tar.zst"), bundleBytes);
        requestBytes("PUT", key(islandId, prefix + "/manifest.json"), IslandManifestJson.write(savedManifest).getBytes(StandardCharsets.UTF_8));
        requestBytes("PUT", key(islandId, prefix + "/checksums.sha256"), (checksum + "  bundle.tar.zst\n").getBytes(StandardCharsets.UTF_8));
        if (updateLatest) {
            requestBytes("PUT", key(islandId, "manifest.json"), IslandManifestJson.write(savedManifest).getBytes(StandardCharsets.UTF_8));
            requestBytes("PUT", key(islandId, "latest"), latestValue.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public int pruneSnapshots(UUID islandId, int keepLatest) throws IOException {
        if (keepLatest < 1) {
            throw new IllegalArgumentException("keepLatest must be positive");
        }
        String prefix = "islands/" + islandId + "/snapshots/";
        List<String> keys = listKeys(prefix);
        List<String> snapshots = keys.stream()
            .map(key -> snapshotName(prefix, key))
            .filter(name -> !name.isBlank())
            .distinct()
            .sorted(Comparator.reverseOrder())
            .toList();
        if (snapshots.size() <= keepLatest) {
            return 0;
        }
        Set<String> removedSnapshots = new HashSet<>(snapshots.subList(keepLatest, snapshots.size()));
        int deletedKeys = 0;
        for (String key : keys) {
            if (removedSnapshots.contains(snapshotName(prefix, key))) {
                requestBytes("DELETE", key, null);
                deletedKeys++;
            }
        }
        return deletedKeys == 0 ? 0 : removedSnapshots.size();
    }

    @Override
    public void deleteIsland(UUID islandId) throws IOException {
        for (String key : listKeys("islands/" + islandId + "/")) {
            requestBytes("DELETE", key, null);
        }
    }

    private String key(UUID islandId, String suffix) {
        return "islands/" + islandId + "/" + suffix;
    }

    private List<String> listKeys(String prefix) throws IOException {
        List<String> keys = new ArrayList<>();
        String continuationToken = "";
        do {
            String query = "?list-type=2&prefix=" + encode(prefix);
            if (!continuationToken.isBlank()) {
                query += "&continuation-token=" + encode(continuationToken);
            }
            String xml = request("GET", query, null);
            for (String key : xmlValues(xml, "Key")) {
                keys.add(xmlUnescape(key));
            }
            continuationToken = xmlValues(xml, "NextContinuationToken").stream()
                .findFirst()
                .map(S3IslandStorage::xmlUnescape)
                .orElse("");
        } while (!continuationToken.isBlank());
        return keys;
    }

    private static String snapshotName(String prefix, String key) {
        if (!key.startsWith(prefix)) {
            return "";
        }
        String rest = key.substring(prefix.length());
        int slash = rest.indexOf('/');
        return slash <= 0 ? "" : rest.substring(0, slash);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static List<String> xmlValues(String xml, String tag) {
        List<String> values = new ArrayList<>();
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int index = 0;
        while (index < xml.length()) {
            int start = xml.indexOf(open, index);
            if (start < 0) {
                break;
            }
            int valueStart = start + open.length();
            int end = xml.indexOf(close, valueStart);
            if (end < 0) {
                break;
            }
            values.add(xml.substring(valueStart, end));
            index = end + close.length();
        }
        return values;
    }

    private static String xmlUnescape(String value) {
        return value.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&");
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
