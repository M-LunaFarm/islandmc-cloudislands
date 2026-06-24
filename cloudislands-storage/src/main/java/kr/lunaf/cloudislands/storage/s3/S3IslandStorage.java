package kr.lunaf.cloudislands.storage.s3;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.compression.BundleCompressionPolicy;
import kr.lunaf.cloudislands.storage.manifest.IslandManifestJson;
import kr.lunaf.cloudislands.storage.object.ObjectStorageClient;
import kr.lunaf.cloudislands.storage.object.ObjectStorageMetrics;
import kr.lunaf.cloudislands.storage.object.ObjectStoragePutOptions;
import kr.lunaf.cloudislands.storage.object.ObjectStoragePutResult;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;

public final class S3IslandStorage implements IslandStorage, ObjectStorageClient {
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final URI endpoint;
    private final String bucket;
    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final String bearerToken;
    private final Duration requestTimeout;
    private final int maxAttempts;
    private final long multipartThresholdBytes;
    private final long multipartPartBytes;
    private final ObjectStorageMetrics metrics = new ObjectStorageMetrics();
    private final HttpClient client = HttpClient.newHttpClient();

    public S3IslandStorage(URI endpoint, String bucket, String bearerToken) {
        this(endpoint, bucket, "us-east-1", "", "", bearerToken);
    }

    public S3IslandStorage(URI endpoint, String bucket, String region, String accessKey, String secretKey, String bearerToken) {
        this(endpoint, bucket, region, accessKey, secretKey, bearerToken, Duration.ofSeconds(30), 3, 8L * 1024L * 1024L, 8L * 1024L * 1024L);
    }

    S3IslandStorage(URI endpoint, String bucket, String region, String accessKey, String secretKey, String bearerToken, Duration requestTimeout, int maxAttempts, long multipartThresholdBytes, long multipartPartBytes) {
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.region = region == null || region.isBlank() ? "us-east-1" : region;
        this.accessKey = accessKey == null ? "" : accessKey;
        this.secretKey = secretKey == null ? "" : secretKey;
        this.bearerToken = bearerToken == null ? "" : bearerToken;
        this.requestTimeout = requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero() ? Duration.ofSeconds(30) : requestTimeout;
        this.maxAttempts = Math.max(1, Math.min(maxAttempts, 5));
        this.multipartThresholdBytes = Math.max(1L, multipartThresholdBytes);
        this.multipartPartBytes = Math.max(1L, multipartPartBytes);
    }

    @Override
    public boolean available() throws IOException {
        try {
            HttpRequest request = requestBuilder("HEAD", "", (byte[]) null)
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
        String manifest;
        try {
            String latest = request("GET", key(islandId, "latest"), null).trim();
            manifest = latest.isBlank()
                ? request("GET", key(islandId, "manifest.json"), null)
                : request("GET", key(islandId, "snapshots/" + latest + "/manifest.json"), null);
        } catch (IOException exception) {
            manifest = request("GET", key(islandId, "manifest.json"), null);
        }
        if (manifest.isBlank()) {
            throw new IOException("missing island manifest: " + islandId);
        }
        return IslandManifestJson.read(manifest);
    }

    @Override
    public Optional<IslandBundleManifest> readSnapshotManifest(UUID islandId, long snapshotNo) throws IOException {
        try {
            String manifest = request("GET", key(islandId, "snapshots/" + String.format("%06d", snapshotNo) + "/manifest.json"), null);
            return manifest.isBlank() ? Optional.empty() : Optional.of(IslandManifestJson.read(manifest));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<IslandBundleManifest> readBundleManifest(String storagePath) throws IOException {
        try {
            String bundleKey = storedBundleKey(storagePath);
            String prefix = bundleKey.substring(0, bundleKey.lastIndexOf('/') + 1);
            String manifest = request("GET", prefix + "manifest.json", null);
            return manifest.isBlank() ? Optional.empty() : Optional.of(IslandManifestJson.read(manifest));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    @Override
    public InputStream openLatestBundle(UUID islandId) throws IOException {
        String latest = request("GET", key(islandId, "latest"), null).trim();
        return openObject(key(islandId, "snapshots/" + latest + "/bundle.tar.zst"));
    }

    @Override
    public InputStream openSnapshotBundle(UUID islandId, long snapshotNo) throws IOException {
        return openObject(key(islandId, "snapshots/" + String.format("%06d", snapshotNo) + "/bundle.tar.zst"));
    }

    @Override
    public InputStream openBundle(String storagePath) throws IOException {
        return openObject(storedBundleKey(storagePath));
    }

    @Override
    public StoredBundle writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        String snapshot = String.format("%06d", snapshotNo);
        return writeBundle(islandId, "snapshots/" + snapshot, bundle, manifest, true, snapshot);
    }

    @Override
    public StoredBundle writeDeleteBackup(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        return writeBundle(islandId, "backups/delete-" + String.format("%06d", snapshotNo), bundle, manifest, false, "");
    }

    @Override
    public StoredBundle writeDeleteBackupFromLatest(UUID islandId, long snapshotNo) throws IOException {
        return writeDeleteBackupFromLatest(islandId, snapshotNo, "BEFORE_DELETE");
    }

    @Override
    public StoredBundle writeDeleteBackupFromLatest(UUID islandId, long snapshotNo, String reason) throws IOException {
        IslandBundleManifest manifest = readManifest(islandId);
        try (InputStream input = openLatestBundle(islandId)) {
            return writeDeleteBackup(islandId, snapshotNo, input, manifest.withSnapshotReason(reason));
        }
    }

    @Override
    public void promoteSnapshot(UUID islandId, long snapshotNo) throws IOException {
        String snapshot = String.format("%06d", snapshotNo);
        String manifestKey = key(islandId, "manifest.json");
        String latestKey = key(islandId, "latest");
        byte[] manifest = requestBytes("GET", key(islandId, "snapshots/" + snapshot + "/manifest.json"), null);
        Optional<String> expectedLatestEtag = objectEtag(latestKey);
        Optional<byte[]> previousManifest = optionalObject(manifestKey);
        boolean compatibilityManifestWritten = false;
        try {
            requestBytes("PUT", manifestKey, manifest);
            compatibilityManifestWritten = true;
            putLatestCas(latestKey, snapshot.getBytes(StandardCharsets.UTF_8), expectedLatestEtag);
        } catch (IOException exception) {
            if (compatibilityManifestWritten) {
                try {
                    if (previousManifest.isPresent()) {
                        requestBytes("PUT", manifestKey, previousManifest.orElseThrow());
                    } else {
                        deleteKey(manifestKey);
                    }
                } catch (IOException restoreFailure) {
                    exception.addSuppressed(restoreFailure);
                }
                metrics.recordOrphanCleanup();
            }
            throw exception;
        }
    }

    @Override
    public void promoteBundle(UUID islandId, long snapshotNo, String storagePath) throws IOException {
        String snapshot = String.format("%06d", snapshotNo);
        String bundleKey = storedBundleKey(storagePath);
        String prefix = bundleKey.substring(0, bundleKey.lastIndexOf('/') + 1);
        IslandBundleManifest sourceManifest = IslandManifestJson.read(request("GET", prefix + "manifest.json", null));
        String compression = BundleCompressionPolicy.normalize(sourceManifest.compression());
        String bundleFileName = BundleCompressionPolicy.bundleFileName(compression);
        String snapshotPrefix = "snapshots/" + snapshot;
        String snapshotBundleKey = key(islandId, snapshotPrefix + "/" + bundleFileName);
        String snapshotManifestKey = key(islandId, snapshotPrefix + "/manifest.json");
        String snapshotChecksumsKey = key(islandId, snapshotPrefix + "/checksums.sha256");
        String compatibilityManifestKey = key(islandId, "manifest.json");
        String latestKey = key(islandId, "latest");
        Optional<String> expectedLatestEtag = objectEtag(latestKey);
        Optional<byte[]> previousCompatibilityManifest = optionalObject(compatibilityManifestKey);
        ObjectStoragePutOptions options = new ObjectStoragePutOptions(true, requestTimeout, maxAttempts, multipartThresholdBytes, multipartPartBytes);
        ObjectStoragePutResult uploaded;
        boolean bundleWritten = false;
        boolean manifestWritten = false;
        boolean checksumsWritten = false;
        boolean compatibilityManifestWritten = false;
        try {
            try (InputStream sourceBundle = openObject(bundleKey)) {
                uploaded = putObject(snapshotBundleKey, sourceBundle, options);
            }
            bundleWritten = true;
            IslandBundleManifest promoted = sourceManifest.withStoredBundle(uploaded.checksum(), uploaded.checksumAlgorithm(), compression, snapshotBundleKey, uploaded.sizeBytes());
            byte[] promotedManifest = IslandManifestJson.write(promoted).getBytes(StandardCharsets.UTF_8);
            requestBytes("PUT", snapshotManifestKey, promotedManifest);
            manifestWritten = true;
            requestBytes("PUT", snapshotChecksumsKey, (uploaded.checksum() + "  " + bundleFileName + "\n").getBytes(StandardCharsets.UTF_8));
            checksumsWritten = true;
            requestBytes("PUT", compatibilityManifestKey, promotedManifest);
            compatibilityManifestWritten = true;
            putLatestCas(latestKey, snapshot.getBytes(StandardCharsets.UTF_8), expectedLatestEtag);
        } catch (IOException exception) {
            if (checksumsWritten) {
                deleteKeyQuietly(snapshotChecksumsKey);
            }
            if (manifestWritten) {
                deleteKeyQuietly(snapshotManifestKey);
            }
            if (compatibilityManifestWritten) {
                restoreOptionalObject(compatibilityManifestKey, previousCompatibilityManifest, exception);
            }
            if (bundleWritten) {
                deleteKeyQuietly(snapshotBundleKey);
            }
            if (bundleWritten || manifestWritten || checksumsWritten || compatibilityManifestWritten) {
                metrics.recordOrphanCleanup();
            }
            throw exception;
        }
    }

    private StoredBundle writeBundle(UUID islandId, String prefix, InputStream bundle, IslandBundleManifest manifest, boolean updateLatest, String latestValue) throws IOException {
        String compression = BundleCompressionPolicy.normalize(manifest.compression());
        String bundleFileName = BundleCompressionPolicy.bundleFileName(compression);
        String storagePath = key(islandId, prefix + "/" + bundleFileName);
        String manifestKey = key(islandId, prefix + "/manifest.json");
        String checksumsKey = key(islandId, prefix + "/checksums.sha256");
        String compatibilityManifestKey = key(islandId, "manifest.json");
        String latestKey = key(islandId, "latest");
        Optional<String> expectedLatestEtag = updateLatest ? objectEtag(latestKey) : Optional.empty();
        Optional<byte[]> previousCompatibilityManifest = updateLatest ? optionalObject(compatibilityManifestKey) : Optional.empty();
        ObjectStoragePutOptions options = new ObjectStoragePutOptions(true, requestTimeout, maxAttempts, multipartThresholdBytes, multipartPartBytes);
        boolean bundleWritten = false;
        boolean manifestWritten = false;
        boolean checksumsWritten = false;
        boolean compatibilityManifestWritten = false;
        try {
            ObjectStoragePutResult uploaded = putObject(storagePath, bundle, options);
            bundleWritten = true;
            IslandBundleManifest savedManifest = manifest.withStoredBundle(uploaded.checksum(), uploaded.checksumAlgorithm(), compression, storagePath, uploaded.sizeBytes());
            requestBytes("PUT", manifestKey, IslandManifestJson.write(savedManifest).getBytes(StandardCharsets.UTF_8));
            manifestWritten = true;
            requestBytes("PUT", checksumsKey, (uploaded.checksum() + "  " + bundleFileName + "\n").getBytes(StandardCharsets.UTF_8));
            checksumsWritten = true;
            if (updateLatest) {
                requestBytes("PUT", compatibilityManifestKey, IslandManifestJson.write(savedManifest).getBytes(StandardCharsets.UTF_8));
                compatibilityManifestWritten = true;
                putLatestCas(latestKey, latestValue.getBytes(StandardCharsets.UTF_8), expectedLatestEtag);
            }
            return new StoredBundle(uploaded.checksum(), uploaded.sizeBytes(), storagePath, uploaded.checksumAlgorithm(), compression);
        } catch (IOException exception) {
            boolean cleaned = false;
            if (checksumsWritten) {
                deleteKeyQuietly(checksumsKey);
                cleaned = true;
            }
            if (manifestWritten) {
                deleteKeyQuietly(manifestKey);
                cleaned = true;
            }
            if (compatibilityManifestWritten) {
                restoreOptionalObject(compatibilityManifestKey, previousCompatibilityManifest, exception);
                cleaned = true;
            }
            if (bundleWritten) {
                deleteKeyQuietly(storagePath);
                cleaned = true;
            }
            if (cleaned) {
                metrics.recordOrphanCleanup();
            }
            throw exception;
        }
    }

    private void restoreOptionalObject(String key, Optional<byte[]> previousValue, IOException exception) {
        try {
            if (previousValue.isPresent()) {
                requestBytes("PUT", key, previousValue.orElseThrow());
            } else {
                deleteKey(key);
            }
        } catch (IOException restoreFailure) {
            exception.addSuppressed(restoreFailure);
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
        removedSnapshots.remove(latestSnapshotName(islandId));
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
    public int pruneSnapshots(UUID islandId, SnapshotRetentionPolicy policy) throws IOException {
        SnapshotRetentionPolicy effectivePolicy = policy == null ? SnapshotRetentionPolicy.defaultPolicy() : policy.normalized();
        String prefix = "islands/" + islandId + "/snapshots/";
        List<String> keys = listKeys(prefix);
        List<String> snapshots = keys.stream()
            .map(key -> snapshotName(prefix, key))
            .filter(name -> !name.isBlank())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
        Set<String> retained = new HashSet<>();
        String latest = latestSnapshotName(islandId);
        if (!latest.isBlank()) {
            retained.add(latest);
        }
        int manualKept = 0;
        for (String snapshot : snapshots) {
            if (manualSnapshot(islandId, snapshot) && manualKept < effectivePolicy.keepManual()) {
                retained.add(snapshot);
                manualKept++;
            }
        }
        retainAutomaticSnapshots(islandId, snapshots, retained, effectivePolicy);
        int deletedKeys = 0;
        Set<String> removedSnapshots = new HashSet<>();
        for (String key : keys) {
            String snapshot = snapshotName(prefix, key);
            if (!snapshot.isBlank() && !retained.contains(snapshot)) {
                deleteKey(key);
                removedSnapshots.add(snapshot);
                deletedKeys++;
            }
        }
        return deletedKeys == 0 ? 0 : removedSnapshots.size();
    }

    private void retainAutomaticSnapshots(UUID islandId, List<String> snapshots, Set<String> retained, SnapshotRetentionPolicy policy) {
        retainAutomaticBucket(islandId, snapshots, retained, policy.keepHourly(), "hour");
        retainAutomaticBucket(islandId, snapshots, retained, policy.keepDaily(), "day");
        retainAutomaticBucket(islandId, snapshots, retained, policy.keepWeekly(), "week");
    }

    private void retainAutomaticBucket(UUID islandId, List<String> snapshots, Set<String> retained, int limit, String bucketType) {
        if (limit <= 0) {
            return;
        }
        Set<String> retainedBuckets = new HashSet<>();
        for (String snapshot : snapshots) {
            if (retained.contains(snapshot) || manualSnapshot(islandId, snapshot)) {
                continue;
            }
            String bucket = snapshotBucket(islandId, snapshot, bucketType);
            if (retainedBuckets.add(bucket)) {
                retained.add(snapshot);
                if (retainedBuckets.size() >= limit) {
                    return;
                }
            }
        }
    }

    private String latestSnapshotName(UUID islandId) {
        try {
            return request("GET", key(islandId, "latest"), null).trim();
        } catch (IOException exception) {
            return "";
        }
    }

    private boolean manualSnapshot(UUID islandId, String snapshot) {
        try {
            String manifest = request("GET", key(islandId, "snapshots/" + snapshot + "/manifest.json"), null);
            String reason = IslandManifestJson.read(manifest).snapshotReason();
            return reason != null && reason.toUpperCase(Locale.ROOT).contains("MANUAL");
        } catch (IOException exception) {
            return false;
        }
    }

    private String snapshotBucket(UUID islandId, String snapshot, String bucketType) {
        ZonedDateTime time = ZonedDateTime.ofInstant(snapshotTime(islandId, snapshot), ZoneOffset.UTC);
        return switch (bucketType) {
            case "hour" -> time.getYear() + "-" + time.getMonthValue() + "-" + time.getDayOfMonth() + "-" + time.getHour();
            case "day" -> time.toLocalDate().toString();
            case "week" -> time.getYear() + "-W" + time.get(WeekFields.ISO.weekOfWeekBasedYear());
            default -> snapshot;
        };
    }

    private Instant snapshotTime(UUID islandId, String snapshot) {
        try {
            String manifest = request("GET", key(islandId, "snapshots/" + snapshot + "/manifest.json"), null);
            Instant savedAt = IslandManifestJson.read(manifest).savedAt();
            if (savedAt != null) {
                return savedAt;
            }
        } catch (RuntimeException | IOException ignored) {
            // Fall back to the snapshot id below.
        }
        try {
            long numeric = Long.parseLong(snapshot);
            if (numeric > 100_000_000_000L) {
                return Instant.ofEpochMilli(numeric);
            }
            return Instant.EPOCH.plusSeconds(Math.max(0L, numeric));
        } catch (NumberFormatException ignored) {
            return Instant.EPOCH;
        }
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

    @Override
    public InputStream openObject(String key) throws IOException {
        IOException failure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest request = requestBuilder("GET", key, (String) null)
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    response.headers().firstValueAsLong("content-length").ifPresent(metrics::recordDownload);
                    return response.body();
                }
                closeQuietly(response.body());
                failure = new IOException("storage object open failed with status " + response.statusCode());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("storage object open interrupted", exception);
            } catch (IOException exception) {
                failure = exception;
            }
            sleepBeforeRetry(attempt);
        }
        throw failure == null ? new IOException("storage object open failed") : failure;
    }

    @Override
    public ObjectStoragePutResult putObject(String key, InputStream body, ObjectStoragePutOptions options) throws IOException {
        ObjectStoragePutOptions effective = options == null ? ObjectStoragePutOptions.defaults() : options;
        Path temp = Files.createTempFile("cloudislands-object-", ".upload");
        String checksum;
        long sizeBytes;
        boolean multipart = false;
        try {
            ChecksumCopy copy = copyToTemp(body, temp);
            checksum = copy.checksum();
            sizeBytes = copy.sizeBytes();
            multipart = sizeBytes >= effective.multipartThresholdBytes();
            if (multipart) {
                uploadMultipart(key, temp, sizeBytes, effective);
            } else {
                sendFileWithRetry("PUT", key, temp, checksum, effective.timeout(), effective.maxAttempts(), effective.immutable());
            }
            metrics.recordUpload(sizeBytes, multipart);
            return new ObjectStoragePutResult(key, checksum, sizeBytes, "SHA-256", multipart);
        } catch (IOException exception) {
            metrics.recordPutFailure();
            throw exception;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public void deleteObject(String key) throws IOException {
        deleteKey(key);
    }

    @Override
    public List<String> listObjects(String prefix) throws IOException {
        return listKeys(prefix);
    }

    @Override
    public ObjectStorageMetrics objectMetrics() {
        return metrics;
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

    private String storedBundleKey(String storagePath) throws IOException {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IOException("missing storage path");
        }
        String key = storagePath.trim();
        if (key.startsWith("/") || key.contains("\\") || key.contains("..") || key.endsWith("/") || !key.endsWith("/bundle.tar.zst")) {
            throw new IOException("invalid stored bundle path: " + storagePath);
        }
        if (key.lastIndexOf('/') <= 0) {
            throw new IOException("stored bundle path has no parent prefix: " + storagePath);
        }
        return key;
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

    private Optional<byte[]> optionalObject(String key) throws IOException {
        try {
            return Optional.of(requestBytes("GET", key, null));
        } catch (IOException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("status 404")) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private void deleteKey(String key) throws IOException {
        try {
            HttpRequest request = requestBuilder("DELETE", key, (byte[]) null)
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

    private void deleteKeyQuietly(String key) {
        try {
            deleteKey(key);
        } catch (IOException ignored) {
            // Best-effort cleanup for abandoned immutable objects.
        }
    }

    private Optional<String> objectEtag(String key) throws IOException {
        try {
            HttpRequest request = requestBuilder("HEAD", key, (byte[]) null)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("storage object metadata failed with status " + response.statusCode());
            }
            return response.headers().firstValue("ETag");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("storage object metadata interrupted", exception);
        }
    }

    private void putLatestCas(String key, byte[] body, Optional<String> expectedEtag) throws IOException {
        IOException failure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofByteArray(body);
                HttpRequest.Builder builder = requestBuilder("PUT", key, body).method("PUT", publisher);
                if (expectedEtag.isPresent()) {
                    builder.header("If-Match", expectedEtag.orElseThrow());
                } else {
                    builder.header("If-None-Match", "*");
                }
                HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return;
                }
                if (response.statusCode() == 412) {
                    throw new IOException("storage latest CAS conflict");
                }
                failure = new IOException("storage latest update failed with status " + response.statusCode());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("storage latest update interrupted", exception);
            } catch (IOException exception) {
                if (exception.getMessage() != null && exception.getMessage().contains("CAS conflict")) {
                    throw exception;
                }
                failure = exception;
            }
            sleepBeforeRetry(attempt);
        }
        throw failure == null ? new IOException("storage latest update failed") : failure;
    }

    private ChecksumCopy copyToTemp(InputStream body, Path temp) throws IOException {
        if (body == null) {
            throw new IOException("missing object body");
        }
        MessageDigest digest = sha256Digest();
        long size = 0L;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = body; OutputStream output = Files.newOutputStream(temp)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("object upload cancelled");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
                size += read;
            }
        }
        return new ChecksumCopy(hex(digest.digest()), size);
    }

    private void uploadMultipart(String key, Path source, long sizeBytes, ObjectStoragePutOptions options) throws IOException {
        String uploadId = initiateMultipartUpload(key);
        Map<Integer, String> etags = new LinkedHashMap<>();
        try (InputStream input = Files.newInputStream(source)) {
            byte[] buffer = new byte[64 * 1024];
            int partNumber = 1;
            long remaining = sizeBytes;
            while (remaining > 0L) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("object multipart upload cancelled");
                }
                Path part = Files.createTempFile("cloudislands-object-part-", ".upload");
                long partBytes = 0L;
                try (OutputStream output = Files.newOutputStream(part)) {
                    while (partBytes < options.multipartPartBytes() && remaining > 0L) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new IOException("object multipart upload cancelled");
                        }
                        int read = input.read(buffer, 0, (int) Math.min(buffer.length, Math.min(options.multipartPartBytes() - partBytes, remaining)));
                        if (read < 0) {
                            break;
                        }
                        output.write(buffer, 0, read);
                        partBytes += read;
                        remaining -= read;
                    }
                }
                try {
                    String partHash = sha256Hex(part);
                    HttpResponse<byte[]> response = sendFileWithRetry("PUT", key + "?partNumber=" + partNumber + "&uploadId=" + encode(uploadId), part, partHash, options.timeout(), options.maxAttempts(), false);
                    etags.put(partNumber, response.headers().firstValue("ETag").orElse("\"part-" + partNumber + "\""));
                    partNumber++;
                } finally {
                    Files.deleteIfExists(part);
                }
            }
            completeMultipartUpload(key, uploadId, etags, options);
        } catch (IOException | RuntimeException exception) {
            abortMultipartUpload(key, uploadId);
            metrics.recordOrphanCleanup();
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    private String initiateMultipartUpload(String key) throws IOException {
        String xml = request("POST", key + "?uploads", null);
        return xmlValues(xml, "UploadId").stream()
            .findFirst()
            .map(S3IslandStorage::xmlUnescape)
            .orElseThrow(() -> new IOException("storage multipart upload did not return an upload id"));
    }

    private void completeMultipartUpload(String key, String uploadId, Map<Integer, String> etags, ObjectStoragePutOptions options) throws IOException {
        StringBuilder xml = new StringBuilder("<CompleteMultipartUpload>");
        for (Map.Entry<Integer, String> entry : etags.entrySet()) {
            xml.append("<Part><PartNumber>")
                .append(entry.getKey())
                .append("</PartNumber><ETag>")
                .append(xmlEscape(entry.getValue()))
                .append("</ETag></Part>");
        }
        xml.append("</CompleteMultipartUpload>");
        byte[] body = xml.toString().getBytes(StandardCharsets.UTF_8);
        IOException failure = null;
        for (int attempt = 1; attempt <= options.maxAttempts(); attempt++) {
            try {
                HttpRequest.Builder builder = requestBuilder("POST", key + "?uploadId=" + encode(uploadId), body)
                    .timeout(options.timeout())
                    .method("POST", HttpRequest.BodyPublishers.ofByteArray(body));
                if (options.immutable()) {
                    builder.header("If-None-Match", "*");
                }
                HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return;
                }
                failure = new IOException("storage multipart complete failed with status " + response.statusCode());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("storage multipart complete interrupted", exception);
            } catch (IOException exception) {
                failure = exception;
            }
            sleepBeforeRetry(attempt);
        }
        throw failure == null ? new IOException("storage multipart complete failed") : failure;
    }

    private void abortMultipartUpload(String key, String uploadId) {
        try {
            requestBytes("DELETE", key + "?uploadId=" + encode(uploadId), null);
        } catch (IOException ignored) {
            // Best-effort orphan cleanup.
        }
    }

    private HttpResponse<byte[]> sendFileWithRetry(String method, String key, Path file, String payloadHash, Duration timeout, int attempts, boolean immutable) throws IOException {
        IOException failure = null;
        int safeAttempts = Math.max(1, Math.min(attempts, 5));
        for (int attempt = 1; attempt <= safeAttempts; attempt++) {
            metrics.recordPutAttempt();
            try {
                HttpRequest.Builder builder = requestBuilder(method, key, payloadHash)
                    .timeout(timeout)
                    .method(method, HttpRequest.BodyPublishers.ofFile(file));
                if (immutable) {
                    builder.header("If-None-Match", "*");
                }
                HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response;
                }
                failure = new IOException("storage object write failed with status " + response.statusCode());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("storage object write interrupted", exception);
            } catch (IOException exception) {
                failure = exception;
            }
            sleepBeforeRetry(attempt);
        }
        throw failure == null ? new IOException("storage object write failed") : failure;
    }

    private byte[] requestBytes(String method, String key, byte[] body) throws IOException {
        IOException failure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body);
                HttpRequest request = requestBuilder(method, key, body)
                    .timeout(requestTimeout)
                    .method(method, publisher)
                    .build();
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body();
                }
                failure = new IOException("storage request failed with status " + response.statusCode());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("storage request interrupted", exception);
            } catch (IOException exception) {
                failure = exception;
            }
            sleepBeforeRetry(attempt);
        }
        throw failure == null ? new IOException("storage request failed") : failure;
    }

    private HttpRequest.Builder requestBuilder(String method, String key, byte[] body) throws IOException {
        byte[] payload = body == null ? new byte[0] : body;
        return requestBuilder(method, key, sha256Hex(payload));
    }

    private HttpRequest.Builder requestBuilder(String method, String key, String payloadHash) throws IOException {
        URI uri = endpoint.resolve("/" + bucket + "/" + (key == null ? "" : key));
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(requestTimeout);
        if (accessKey.isBlank() || secretKey.isBlank()) {
            if (!bearerToken.isBlank()) {
                builder.header("Authorization", "Bearer " + bearerToken);
            }
            return builder;
        }
        payloadHash = payloadHash == null || payloadHash.isBlank() ? sha256Hex(new byte[0]) : payloadHash;
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

    private static String sha256Hex(Path path) throws IOException {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = Files.newInputStream(path)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    private static MessageDigest sha256Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception exception) {
            throw new IOException("failed to create SHA-256 digest", exception);
        }
    }

    private static void sleepBeforeRetry(int attempt) throws IOException {
        if (attempt <= 0) {
            return;
        }
        try {
            Thread.sleep(Math.min(1000L, 100L * attempt));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("storage retry interrupted", exception);
        }
    }

    private static void closeQuietly(InputStream input) {
        if (input == null) {
            return;
        }
        try {
            input.close();
        } catch (IOException ignored) {
            // Best-effort close for failed streaming response bodies.
        }
    }

    private static String xmlEscape(String value) {
        return (value == null ? "" : value)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private record ChecksumCopy(String checksum, long sizeBytes) {
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }
}
