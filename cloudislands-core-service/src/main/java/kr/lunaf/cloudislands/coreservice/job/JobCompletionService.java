package kr.lunaf.cloudislands.coreservice.job;

import java.time.Duration;
import kr.lunaf.cloudislands.coreservice.RedisActivationLock;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.IslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.ticket.RouteTicketStore;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;

public final class JobCompletionService {
    static final String ACTIVATION_LOCK_TOKEN_KEY = JobCompletionBackend.ACTIVATION_LOCK_TOKEN_KEY;

    private final JobCompletionBackend backend;

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets) {
        this.backend = new JobCompletionBackend(runtimes, events, snapshots, tickets);
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs) {
        this.backend = new JobCompletionBackend(runtimes, events, snapshots, tickets, jobs);
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles) {
        this.backend = new JobCompletionBackend(runtimes, events, snapshots, tickets, jobs, islands, playerProfiles);
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles, Duration routeTicketTtl) {
        this.backend = new JobCompletionBackend(runtimes, events, snapshots, tickets, jobs, islands, playerProfiles, routeTicketTtl);
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles, Duration routeTicketTtl, int snapshotKeepLatest) {
        this.backend = new JobCompletionBackend(runtimes, events, snapshots, tickets, jobs, islands, playerProfiles, routeTicketTtl, snapshotKeepLatest);
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles, Duration routeTicketTtl, int snapshotKeepLatest, RedisActivationLock activationLock) {
        this.backend = new JobCompletionBackend(runtimes, events, snapshots, tickets, jobs, islands, playerProfiles, routeTicketTtl, snapshotKeepLatest, activationLock);
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles, Duration routeTicketTtl, SnapshotRetentionPolicy snapshotRetentionPolicy, RedisActivationLock activationLock) {
        this.backend = new JobCompletionBackend(runtimes, events, snapshots, tickets, jobs, islands, playerProfiles, routeTicketTtl, snapshotRetentionPolicy, activationLock);
    }

    public void completed(IslandJob job) {
        backend.completed(job);
    }

    public void failed(IslandJob job, String errorMessage) {
        backend.failed(job, errorMessage);
    }
}
