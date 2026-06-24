package kr.lunaf.cloudislands.coreservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.job.InMemoryIslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.job.IslandJobQueue;
import kr.lunaf.cloudislands.coreservice.profile.InMemoryPlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.InMemoryIslandSnapshotRepository;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.job.JobClaimLease;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;
import org.junit.jupiter.api.Test;

class CoreIslandDeleteServiceTest {
    private static final UUID ISLAND_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000102");

    @Test
    void activeIslandDeleteReportsRequestQueuedNotDeleted() {
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryPlayerProfileRepository profiles = new InMemoryPlayerProfileRepository();
        InMemoryIslandJobPublisher jobs = new InMemoryIslandJobPublisher();
        CoreIslandDeleteService service = service(islands, runtimes, profiles, jobs);
        islands.createOwnedIsland(ISLAND_ID, OWNER_UUID, "default", "owner-island");
        islands.setState(ISLAND_ID, IslandState.ACTIVE);
        runtimes.markActive(ISLAND_ID, "node-a", "world", 0, 0, 7L);
        profiles.setPrimaryIsland(OWNER_UUID, ISLAND_ID);

        DeleteIslandResult result = service.requestIslandDelete(ISLAND_ID, OWNER_UUID, OWNER_UUID, "test-delete");

        assertTrue(result.accepted());
        assertEquals("DELETE_REQUESTED", result.code());
        assertEquals(IslandState.DEACTIVATING, islands.findById(ISLAND_ID).orElseThrow().state());
        assertEquals(IslandState.DEACTIVATING, runtimes.find(ISLAND_ID).orElseThrow().state());
        assertEquals(Optional.of(ISLAND_ID), profiles.find(OWNER_UUID).primaryIslandId());
        assertEquals(1, jobs.snapshot().size());
        assertEquals(IslandJobType.DELETE_ISLAND, jobs.snapshot().getFirst().type());
    }

    @Test
    void inactiveIslandDeleteReportsDeletedAfterImmediateMarkDeleted() {
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryPlayerProfileRepository profiles = new InMemoryPlayerProfileRepository();
        CoreIslandDeleteService service = service(islands, runtimes, profiles, new InMemoryIslandJobPublisher());
        islands.createOwnedIsland(ISLAND_ID, OWNER_UUID, "default", "owner-island");
        islands.setState(ISLAND_ID, IslandState.INACTIVE_READY);
        profiles.setPrimaryIsland(OWNER_UUID, ISLAND_ID);

        DeleteIslandResult result = service.requestIslandDelete(ISLAND_ID, OWNER_UUID, OWNER_UUID, "test-delete");

        assertTrue(result.accepted());
        assertEquals("DELETED", result.code());
        assertTrue(islands.findById(ISLAND_ID).isEmpty());
        assertTrue(profiles.find(OWNER_UUID).primaryIslandId().isEmpty());
        assertEquals(IslandState.DELETING, runtimes.find(ISLAND_ID).orElseThrow().state());
    }

    @Test
    void deleteJobPublishFailureReportsFailureAndMarksRecoveryRequired() {
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryPlayerProfileRepository profiles = new InMemoryPlayerProfileRepository();
        CoreIslandDeleteService service = service(islands, runtimes, profiles, new FailingIslandJobQueue());
        islands.createOwnedIsland(ISLAND_ID, OWNER_UUID, "default", "owner-island");
        islands.setState(ISLAND_ID, IslandState.ACTIVE);
        runtimes.markActive(ISLAND_ID, "node-a", "world", 0, 0, 7L);

        DeleteIslandResult result = service.requestIslandDelete(ISLAND_ID, OWNER_UUID, OWNER_UUID, "test-delete");

        assertFalse(result.accepted());
        assertEquals("DELETE_QUEUE_FAILED", result.code());
        assertEquals(IslandState.RECOVERY_REQUIRED, islands.findById(ISLAND_ID).orElseThrow().state());
        assertEquals(IslandState.RECOVERY_REQUIRED, runtimes.find(ISLAND_ID).orElseThrow().state());
    }

    @Test
    void deleteBackupFailurePublishesRecoveryRequiredForCrossNodeInvalidation() {
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryPlayerProfileRepository profiles = new InMemoryPlayerProfileRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        CoreIslandDeleteService service = service(islands, runtimes, profiles, new InMemoryIslandJobPublisher(), events, new FailingDeleteBackupStorage());
        islands.createOwnedIsland(ISLAND_ID, OWNER_UUID, "default", "owner-island");
        islands.setState(ISLAND_ID, IslandState.INACTIVE_READY);

        DeleteIslandResult result = service.requestIslandDelete(ISLAND_ID, OWNER_UUID, OWNER_UUID, "test-delete");

        assertFalse(result.accepted());
        assertEquals("DELETE_BACKUP_FAILED", result.code());
        assertEquals(IslandState.RECOVERY_REQUIRED, islands.findById(ISLAND_ID).orElseThrow().state());
        assertEquals(IslandState.RECOVERY_REQUIRED, runtimes.find(ISLAND_ID).orElseThrow().state());
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_DELETE_BACKUP_FAILED.name()));
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_RECOVERY_REQUIRED.name()));
        assertTrue(events.countsByField(CloudIslandEventType.ISLAND_RECOVERY_REQUIRED.name(), "reason").containsKey("delete-backup-failed"));
    }

    private static CoreIslandDeleteService service(
            InMemoryIslandRepository islands,
            InMemoryIslandRuntimeRepository runtimes,
            InMemoryPlayerProfileRepository profiles,
            IslandJobQueue jobs) {
        return new CoreIslandDeleteService(
            null,
            islands,
            profiles,
            runtimes,
            jobs,
            new InMemoryGlobalEventPublisher(),
            new InMemoryIslandSnapshotRepository(),
            SnapshotRetentionPolicy.defaultPolicy()
        );
    }

    private static CoreIslandDeleteService service(
            InMemoryIslandRepository islands,
            InMemoryIslandRuntimeRepository runtimes,
            InMemoryPlayerProfileRepository profiles,
            IslandJobQueue jobs,
            InMemoryGlobalEventPublisher events,
            IslandStorage storage) {
        return new CoreIslandDeleteService(
            storage,
            islands,
            profiles,
            runtimes,
            jobs,
            events,
            new InMemoryIslandSnapshotRepository(),
            SnapshotRetentionPolicy.defaultPolicy()
        );
    }

    private static final class FailingIslandJobQueue implements IslandJobQueue {
        @Override
        public void publish(IslandJob job) {
            throw new IllegalStateException("queue unavailable");
        }

        @Override
        public List<IslandJob> claim(String nodeId, List<IslandJobType> supportedTypes, int maxJobs) {
            return List.of();
        }

        @Override
        public Optional<IslandJob> findClaimed(UUID jobId) {
            return Optional.empty();
        }

        @Override
        public boolean complete(String nodeId, UUID jobId) {
            return false;
        }

        @Override
        public boolean complete(String nodeId, UUID jobId, JobClaimLease claimLease) {
            return false;
        }

        @Override
        public boolean fail(String nodeId, UUID jobId, String errorMessage) {
            return false;
        }

        @Override
        public boolean fail(String nodeId, UUID jobId, JobClaimLease claimLease, String errorMessage) {
            return false;
        }

        @Override
        public boolean retry(UUID jobId) {
            return false;
        }

        @Override
        public boolean cancel(UUID jobId) {
            return false;
        }
    }

    private static final class FailingDeleteBackupStorage implements IslandStorage {
        @Override
        public boolean available() {
            return true;
        }

        @Override
        public IslandBundleManifest readManifest(UUID islandId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream openLatestBundle(UUID islandId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream openSnapshotBundle(UUID islandId, long snapshotNo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream openBundle(String storagePath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredBundle writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredBundle writeDeleteBackup(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredBundle writeDeleteBackupFromLatest(UUID islandId, long snapshotNo) throws IOException {
            throw new IOException("backup unavailable");
        }

        @Override
        public StoredBundle writeDeleteBackupFromLatest(UUID islandId, long snapshotNo, String reason) throws IOException {
            throw new IOException("backup unavailable");
        }

        @Override
        public void promoteSnapshot(UUID islandId, long snapshotNo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void promoteBundle(UUID islandId, long snapshotNo, String storagePath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int pruneSnapshots(UUID islandId, int keepLatest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteLiveState(UUID islandId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteIsland(UUID islandId) {
            throw new UnsupportedOperationException();
        }
    }
}
