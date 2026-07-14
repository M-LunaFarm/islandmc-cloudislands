package kr.lunaf.cloudislands.coreservice.job;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreservice.RedisActivationLock;
import kr.lunaf.cloudislands.coreservice.addon.AddonStateRepository;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.IslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.ticket.RouteTicketStore;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.JobClaimLease;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;

public final class JobCompletionService {
    static final String ACTIVATION_LOCK_TOKEN_KEY = JobCompletionBackend.ACTIVATION_LOCK_TOKEN_KEY;

    private final JobCompletionCoordinator coordinator;
    private final JobCompletionBackend backend;
    private final JobCompletionBackend failureBackend;
    private final JobCompletionReceiptStore receipts;

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets) {
        this(runtimes, events, snapshots, tickets, null);
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs) {
        this(runtimes, events, snapshots, tickets, jobs, null, null);
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles) {
        this(runtimes, events, snapshots, tickets, jobs, islands, playerProfiles, Duration.ofSeconds(30));
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles, Duration routeTicketTtl) {
        this(runtimes, events, snapshots, tickets, jobs, islands, playerProfiles, routeTicketTtl, 85);
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles, Duration routeTicketTtl, int snapshotKeepLatest) {
        this(runtimes, events, snapshots, tickets, jobs, islands, playerProfiles, routeTicketTtl, snapshotKeepLatest, null);
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles, Duration routeTicketTtl, int snapshotKeepLatest, RedisActivationLock activationLock) {
        this(runtimes, events, snapshots, tickets, jobs, islands, playerProfiles, routeTicketTtl, new SnapshotRetentionPolicy(snapshotKeepLatest, 0, 0, 0, true, "SHA-256"), activationLock);
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles, Duration routeTicketTtl, SnapshotRetentionPolicy snapshotRetentionPolicy, RedisActivationLock activationLock) {
        this(runtimes, events, snapshots, tickets, jobs, islands, playerProfiles, routeTicketTtl, snapshotRetentionPolicy, activationLock, new InMemoryJobCompletionReceiptStore(), new InMemoryJobCompletionOutboxStore());
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles, Duration routeTicketTtl, SnapshotRetentionPolicy snapshotRetentionPolicy, RedisActivationLock activationLock, JobCompletionReceiptStore receipts) {
        this(runtimes, events, snapshots, tickets, jobs, islands, playerProfiles, routeTicketTtl, snapshotRetentionPolicy, activationLock, receipts, new InMemoryJobCompletionOutboxStore());
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles, Duration routeTicketTtl, SnapshotRetentionPolicy snapshotRetentionPolicy, RedisActivationLock activationLock, JobCompletionReceiptStore receipts, JobCompletionOutboxStore outbox) {
        this(runtimes, events, snapshots, tickets, jobs, islands, playerProfiles, routeTicketTtl,
                snapshotRetentionPolicy, activationLock, receipts, outbox, null);
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles, Duration routeTicketTtl, SnapshotRetentionPolicy snapshotRetentionPolicy, RedisActivationLock activationLock, JobCompletionReceiptStore receipts, JobCompletionOutboxStore outbox, AddonStateRepository addonStates) {
        this(runtimes, events, snapshots, tickets, jobs, islands, playerProfiles, routeTicketTtl,
                snapshotRetentionPolicy, activationLock, receipts, outbox, addonStates, events);
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles, Duration routeTicketTtl, SnapshotRetentionPolicy snapshotRetentionPolicy, RedisActivationLock activationLock, JobCompletionReceiptStore receipts, JobCompletionOutboxStore outbox, AddonStateRepository addonStates, GlobalEventPublisher failureEvents) {
        JobCompletionEventBuffer eventBuffer = new JobCompletionEventBuffer();
        JobCompletionOutboxStore safeOutbox = outbox == null ? new InMemoryJobCompletionOutboxStore() : outbox;
        JobCompletionOutboxDispatcher dispatcher = new JobCompletionOutboxDispatcher(safeOutbox, events);
        this.backend = new JobCompletionBackend(runtimes, eventBuffer, snapshots, tickets, jobs, islands, playerProfiles, routeTicketTtl, snapshotRetentionPolicy, activationLock, addonStates);
        this.failureBackend = new JobCompletionBackend(runtimes, failureEvents, snapshots, tickets, jobs, islands, playerProfiles, routeTicketTtl, snapshotRetentionPolicy, activationLock, addonStates, true);
        this.receipts = receipts == null ? new InMemoryJobCompletionReceiptStore() : receipts;
        this.coordinator = new JobCompletionCoordinator(backend, eventBuffer, this.receipts, safeOutbox, dispatcher);
    }

    public void completed(IslandJob job) {
        coordinator.completed(job, Map.of());
    }

    public JobCompletionResult completed(IslandJob job, Map<String, String> completionPayload) {
        return coordinator.completed(job, completionPayload);
    }

    public JobCompletionReceiptStore.ReplayResult verifyCommitted(UUID jobId, String nodeId, JobClaimLease claimLease, Map<String, String> completionPayload) {
        return receipts.verifyCommitted(jobId, nodeId, claimLease, completionPayload);
    }

    public void failed(IslandJob job, String errorMessage) {
        failureBackend.failed(job, errorMessage);
    }
}
