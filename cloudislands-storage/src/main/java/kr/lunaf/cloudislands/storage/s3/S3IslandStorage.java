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
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.checksum.Sha256Checksums;
import kr.lunaf.cloudislands.storage.manifest.IslandManifestJson;

public final class S3IslandStorage implements IslandStorage {
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final URI endpoint;
    private final String bucket;
    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final String bearerToken;
    private final HttpClient client = HttpClient.newHttpClient();

    public S3IslandStorage(URI endpoint, String bucket, String bearerToken) {
        this(endpoint, bucket, "us-east-1", "", "", bearerToken);
    }

    public S3IslandStorage(URI endpoint, String bucket, String region, String accessKey, String secretKey, String bearerToken) {
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.region = region == null || region.isBlank() ? "us-east-1" : region;
        this.accessKey = accessKey == null ? "" : accessKey;
        this.secretKey = secretKey == null ? "" : secretKey;
        this.bearerToken = bearerToken == null ? "" : bearerToken;
    }

    @Override
    public boolean available() throws IOException {
        try {
            HttpRequest request = requestBuilder("HEAD", "", null)
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
    public InputStream openBundle(String storagePath) throws IOException {
        return new ByteArrayInputStream(requestBytes("GET", storagePath, null));
    }

    @Override
    public void writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        String snapshot = String.format("%06d", snapshotNo);
        writeBundle(islandId, "snapshots/" + snapshot, bundle, manifest, true, snapshot);
    }

    @Override
    public StoredBundle writeDeleteBackup(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        return writeBundle(islandId, "backups/delete-" + String.format("%06d", snapshotNo), bundle, manifest, false, "");
    }

    @Override
    public StoredBundle writeDeleteBackupFromLatest(UUID islandId, long snapshotNo) throws IOException {
        IslandBundleManifest manifest = readManifest(islandId);
        try (InputStream input = openLatestBundle(islandId)) {
            return writeDeleteBackup(islandId, snapshotNo, input, manifest);
        }
    }

    @Override
    public void promoteSnapshot(UUID islandId, long snapshotNo) throws IOException {
        String snapshot = String.format("%06d", snapshotNo);
        byte[] manifest = requestBytes("GET", key(islandId, "snapshots/" + snapshot + "/manifest.json"), null);
        requestBytes("PUT", key(islandId, "manifest.json"), manifest);
        requestBytes("PUT", key(islandId, "latest"), snapshot.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void promoteBundle(UUID islandId, long snapshotNo, String storagePath) throws IOException {
        String snapshot = String.format("%06d", snapshotNo);
        String prefix = storagePath.substring(0, storagePath.lastIndexOf('/') + 1);
        byte[] bundle = requestBytes("GET", storagePath, null);
        byte[] manifest = requestBytes("GET", prefix + "manifest.json", null);
        requestBytes("PUT", key(islandId, "snapshots/" + snapshot + "/bundle.tar.zst"), bundle);
        requestBytes("PUT", key(islandId, "snapshots/" + snapshot + "/manifest.json"), manifest);
        try {
            byte[] checksums = requestBytes("GET", prefix + "checksums.sha256", null);
            requestBytes("PUT", key(islandId, "snapshots/" + snapshot + "/checksums.sha256"), checksums);
        } catch (IOException ignored) {
            // Older bundles may not have a checksum sidecar.
        }
        requestBytes("PUT", key(islandId, "manifest.json"), manifest);
        requestBytes("PUT", key(islandId, "latest"), snapshot.getBytes(StandardCharsets.UTF_8));
    }

    private StoredBundle writeBundle(UUID islandId, String prefix, InputStream bundle, IslandBundleManifest manifest, boolean updateLatest, String latestValue) throws IOException {
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
        return new StoredBundle(checksum, bundleBytes.length);
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
                deleteKey(key);
                deletedKeys++;
            }
        }
        return deletedKeys == 0 ? 0 : removedSnapshots.size();
    }

    @Override
    public void deleteIsland(UUID islandId) throws IOException {
        for (String key : listKeys("islands/" + islandId + "/")) {
            deleteKey(key);
        }
    }

    @Override
    public void deleteLiveState(UUID islandId) throws IOException {
        deleteKey(key(islandId, "manifest.json"));
        deleteKey(key(islandId, "latest"));
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

    private void deleteKey(String key) throws IOException {
        try {
            HttpRequest request = requestBuilder("DELETE", key, null)
                .method("DELETE", HttpRequest.BodyPublishers.noBody())
                .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if ((response.statusCode() < 200 || response.statusCode() >= 300) && response.statusCode() != 404) {
                throw new IOException("storage delete failed with status " + response.statusCode());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("storage delete interrupted", exception);
        }
    }

    private byte[] requestBytes(String method, String key, byte[] body) throws IOException {
        try {
            HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body);
            HttpRequest request = requestBuilder(method, key, body).method(method, publisher).build();
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

    private HttpRequest.Builder requestBuilder(String method, String key, byte[] body) throws IOException {
        URI uri = endpoint.resolve("/" + bucket + "/" + (key == null ? "" : key));
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri);
        if (accessKey.isBlank() || secretKey.isBlank()) {
            if (!bearerToken.isBlank()) {
                builder.header("Authorization", "Bearer " + bearerToken);
            }
            return builder;
        }
        byte[] payload = body == null ? new byte[0] : body;
        String payloadHash = sha256Hex(payload);
        Instant now = Instant.now();
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = DATE_STAMP.format(now);
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalHeaders = "host:" + hostHeader(uri) + "\n"
            + "x-amz-content-sha256:" + payloadHash + "\n"
            + "x-amz-date:" + amzDate + "\n";
        String canonicalRequest = method + "\n"
            + uri.getRawPath() + "\n"
            + canonicalQuery(uri) + "\n"
            + canonicalHeaders + "\n"
            + signedHeaders + "\n"
            + payloadHash;
        String credentialScope = dateStamp + "/" + region + "/s3/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n"
            + amzDate + "\n"
            + credentialScope + "\n"
            + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        String signature = hex(hmac(signingKey(dateStamp), stringToSign));
        return builder
            .header("x-amz-date", amzDate)
            .header("x-amz-content-sha256", payloadHash)
            .header("Authorization", "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature);
    }

    private String hostHeader(URI uri) {
        int port = uri.getPort();
        if (port < 0 || (uri.getScheme().equalsIgnoreCase("http") && port == 80) || (uri.getScheme().equalsIgnoreCase("https") && port == 443)) {
            return uri.getHost();
        }
        return uri.getHost() + ":" + port;
    }

    private String canonicalQuery(URI uri) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return "";
        }
        return Arrays.stream(query.split("&"))
            .sorted()
            .reduce((left, right) -> left + "&" + right)
            .orElse("");
    }

    private byte[] signingKey(String dateStamp) throws IOException {
        byte[] dateKey = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] regionKey = hmac(dateKey, region);
        byte[] serviceKey = hmac(regionKey, "s3");
        return hmac(serviceKey, "aws4_request");
    }

    private static byte[] hmac(byte[] key, String value) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IOException("failed to sign storage request", exception);
        }
    }

    private static String sha256Hex(byte[] bytes) throws IOException {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IOException("failed to hash storage request", exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }
}
