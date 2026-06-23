package kr.lunaf.cloudislands.paper.world;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.integration.IntegrationLifecycleHooks;
import kr.lunaf.cloudislands.paper.world.bundle.BundleRestorePlan;
import kr.lunaf.cloudislands.paper.world.bundle.BundleRestorePlanner;
import kr.lunaf.cloudislands.storage.BundleCompatibilityPolicy;
import kr.lunaf.cloudislands.storage.BundleRestorePolicy;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.checksum.Sha256Checksums;
import kr.lunaf.cloudislands.storage.manifest.IslandManifestJson;

public final class IslandWorldRestorer {
    private final IslandStorage storage;
    private final Path stagingRoot;
    private final BundleRestorePlanner restorePlanner;
    private final IntegrationLifecycleHooks integrationHooks;

    public IslandWorldRestorer(IslandStorage storage, Path stagingRoot, BundleRestorePlanner restorePlanner) {
        this(storage, stagingRoot, restorePlanner, IntegrationLifecycleHooks.noop());
    }

    public IslandWorldRestorer(IslandStorage storage, Path stagingRoot, BundleRestorePlanner restorePlanner, IntegrationLifecycleHooks integrationHooks) {
        this.storage = storage;
        this.stagingRoot = stagingRoot;
        this.restorePlanner = restorePlanner;
        this.integrationHooks = integrationHooks == null ? IntegrationLifecycleHooks.noop() : integrationHooks;
    }

    public BundleRestorePlan stage(UUID islandId, String worldName, int originX, int originZ) throws IOException {
        return stage(islandId, worldName, originX, originZ, 0L);
    }

    public BundleRestorePlan stage(UUID islandId, String worldName, int originX, int originZ, long snapshotNo) throws IOException {
        return stage(islandId, worldName, originX, originZ, snapshotNo, "");
    }

    public BundleRestorePlan stage(UUID islandId, String worldName, int originX, int originZ, long snapshotNo, String storagePath) throws IOException {
        return stage(islandId, worldName, 0, 0, originX, originZ, 0L, snapshotNo, storagePath);
    }

    public BundleRestorePlan stage(UUID islandId, String worldName, int cellX, int cellZ, int originX, int originZ, long fencingToken, long snapshotNo, String storagePath) throws IOException {
        Path islandStage = stagingRoot.resolve(islandId.toString());
        Files.createDirectories(islandStage);
        Path bundle = islandStage.resolve("bundle.tar.zst");
        try (InputStream input = storagePath == null || storagePath.isBlank() ? snapshotNo > 0L ? storage.openSnapshotBundle(islandId, snapshotNo) : storage.openLatestBundle(islandId) : storage.openBundle(storagePath)) {
            Files.copy(input, bundle, StandardCopyOption.REPLACE_EXISTING);
        }
        verifyStagedBundle(islandId, bundle, snapshotNo, storagePath);
        RestorePlan restorePlan = new RestorePlan(islandId, worldName, originX, originZ, bundle);
        BundleRestorePlan plan = restorePlanner.plan(restorePlan);
        IslandBundleManifest manifest = validateExtractedManifest(islandId, plan.extractedRoot().resolve("manifest.json"));
        IntegrationLifecycleHooks.LifecycleBatch integrations = integrationHooks.restoreState(islandId, worldName, cellX, cellZ, originX, originZ, fencingToken, snapshotNo, storagePath, bundle, manifest);
        integrations.throwIfFailed();
        integrations.writeIfPresent(plan.extractedRoot().resolve("integrations/restore.json"));
        return plan;
    }

    private void verifyStagedBundle(UUID islandId, Path bundle, long snapshotNo, String storagePath) throws IOException {
        Optional<IslandBundleManifest> manifest = restoreManifest(islandId, snapshotNo, storagePath);
        if (manifest.isEmpty()) {
            throw new IOException("missing island bundle manifest for restore: " + islandId);
        }
        IslandBundleManifest restoreManifest = manifest.get();
        validateRestoreManifest(islandId, restoreManifest);
        String expectedChecksum = restoreManifest.checksum();
        if (expectedChecksum == null || expectedChecksum.isBlank()) {
            return;
        }
        String actualChecksum;
        try (InputStream input = Files.newInputStream(bundle)) {
            actualChecksum = Sha256Checksums.of(input);
        }
        if (!expectedChecksum.equalsIgnoreCase(actualChecksum)) {
            throw new IOException("island bundle checksum mismatch: " + islandId);
        }
    }

    private IslandBundleManifest validateExtractedManifest(UUID islandId, Path manifestPath) throws IOException {
        if (!Files.isRegularFile(manifestPath)) {
            throw new IOException("extracted island bundle is missing manifest.json: " + islandId);
        }
        IslandBundleManifest embeddedManifest = IslandManifestJson.read(Files.readString(manifestPath, StandardCharsets.UTF_8));
        if (!islandId.equals(embeddedManifest.islandId())) {
            throw new IOException("extracted island bundle manifest island mismatch: " + islandId);
        }
        validateRestoreManifest(islandId, embeddedManifest);
        return embeddedManifest;
    }

    private void validateRestoreManifest(UUID islandId, IslandBundleManifest manifest) throws IOException {
        if (BundleRestorePolicy.PORTABLE_REQUIRED && !manifest.portable()) {
            throw new IOException("island bundle is not portable: " + islandId);
        }
        if (!supportedChecksumAlgorithm(manifest.checksumAlgorithm())) {
            throw new IOException("unsupported island bundle checksum algorithm: " + manifest.checksumAlgorithm());
        }
        if (!supportedCompression(manifest.compression())) {
            throw new IOException("unsupported island bundle compression: " + manifest.compression());
        }
        BundleCompatibilityPolicy.requireCompatible(manifest);
    }

    private boolean supportedChecksumAlgorithm(String algorithm) {
        return algorithm == null || algorithm.isBlank() || algorithm.equalsIgnoreCase(BundleRestorePolicy.CHECKSUM_ALGORITHM);
    }

    private boolean supportedCompression(String compression) {
        return compression == null || compression.isBlank() || compression.equalsIgnoreCase(BundleRestorePolicy.COMPRESSION);
    }

    private Optional<IslandBundleManifest> restoreManifest(UUID islandId, long snapshotNo, String storagePath) throws IOException {
        if (storagePath != null && !storagePath.isBlank()) {
            return storage.readBundleManifest(storagePath);
        }
        if (snapshotNo > 0L) {
            return storage.readSnapshotManifest(islandId, snapshotNo);
        }
        return Optional.of(storage.readManifest(islandId));
    }

    public record RestorePlan(UUID islandId, String worldName, int originX, int originZ, Path stagedBundle) {}
}
