package kr.lunaf.cloudislands.coreservice.job;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.job.JobClaimLease;

public final class InMemoryIslandJobPublisher implements IslandJobQueue {
    private static final int MAX_ATTEMPTS = 3;
    private final List<JobRecord> jobs = new ArrayList<>();

    @Override
    public synchronized void publish(IslandJob job) {
        jobs.add(new JobRecord(job, JobState.PENDING, null, null, null, 0L, 0, null, Instant.now()));
    }

    @Override
    public synchronized List<IslandJob> claim(String nodeId, List<IslandJobType> supportedTypes, int maxJobs) {
        List<IslandJob> claimed = new ArrayList<>();
        jobs.stream()
            .filter(record -> record.state() == JobState.PENDING)
            .filter(record -> record.job().targetNode() == null || record.job().targetNode().isBlank() || record.job().targetNode().equals(nodeId))
            .filter(record -> supportedTypes.contains(record.job().type()))
            .sorted(Comparator.comparingInt((JobRecord record) -> record.job().priority()).reversed().thenComparing(record -> record.job().createdAt()))
            .limit(maxJobs)
            .forEach(record -> {
                JobRecord locked = record.locked(nodeId);
                jobs.set(jobs.indexOf(record), locked);
                claimed.add(locked.job());
            });
        return claimed;
    }

    @Override
    public synchronized java.util.Optional<kr.lunaf.cloudislands.protocol.job.IslandJob> findClaimed(java.util.UUID jobId) {
        return jobs.stream()
            .filter(record -> record.job().jobId().equals(jobId))
            .filter(record -> record.state() == JobState.CLAIMED)
            .map(JobRecord::claimedJob)
            .findFirst();
    }

    @Override
    public synchronized java.util.Optional<IslandJob> findClaimed(UUID jobId, JobClaimLease claimLease) {
        return jobs.stream()
            .filter(record -> leaseMatches(record, jobId, claimLease))
            .map(JobRecord::claimedJob)
            .findFirst();
    }

    @Override
    public synchronized boolean complete(String nodeId, UUID jobId) {
        final boolean[] changed = {false};
        replace(jobId, record -> {
            if (record.state() == JobState.CLAIMED && record.lockedBy() != null && record.lockedBy().equals(nodeId)) {
                changed[0] = true;
                return record.completed();
            }
            return record;
        });
        return changed[0];
    }

    @Override
    public synchronized boolean complete(String nodeId, UUID jobId, JobClaimLease claimLease) {
        final boolean[] changed = {false};
        replace(jobId, record -> {
            if (leaseMatches(record, jobId, claimLease) && record.lockedBy().equals(nodeId)) {
                changed[0] = true;
                return record.completed();
            }
            return record;
        });
        return changed[0];
    }

    @Override
    public synchronized boolean fail(String nodeId, UUID jobId, String errorMessage) {
        final boolean[] changed = {false};
        replace(jobId, record -> {
            if (record.state() == JobState.CLAIMED && record.lockedBy() != null && record.lockedBy().equals(nodeId)) {
                changed[0] = true;
                return record.failed(errorMessage);
            }
            return record;
        });
        return changed[0];
    }

    @Override
    public synchronized boolean fail(String nodeId, UUID jobId, JobClaimLease claimLease, String errorMessage) {
        final boolean[] changed = {false};
        replace(jobId, record -> {
            if (leaseMatches(record, jobId, claimLease) && record.lockedBy().equals(nodeId)) {
                changed[0] = true;
                return record.failed(errorMessage);
            }
            return record;
        });
        return changed[0];
    }

    @Override
    public synchronized boolean retry(UUID jobId) {
        final boolean[] changed = {false};
        replace(jobId, record -> {
            if (record.state() == JobState.FAILED || record.state() == JobState.CLAIMED) {
                changed[0] = true;
                return record.pending();
            }
            return record;
        });
        return changed[0];
    }

    @Override
    public synchronized boolean cancel(UUID jobId) {
        final boolean[] changed = {false};
        replace(jobId, record -> {
            if (record.state() == JobState.PENDING || record.state() == JobState.CLAIMED || record.state() == JobState.FAILED) {
                changed[0] = true;
                return record.canceled();
            }
            return record;
        });
        return changed[0];
    }

    public synchronized List<IslandJob> snapshot() {
        return jobs.stream().map(JobRecord::job).toList();
    }

    public synchronized Map<String, Long> countsByState() {
        Map<String, Long> counts = new HashMap<>();
        for (JobRecord record : jobs) {
            counts.merge(record.state().name(), 1L, Long::sum);
        }
        for (JobState state : JobState.values()) {
            counts.putIfAbsent(state.name(), 0L);
        }
        return Map.copyOf(counts);
    }

    public synchronized long retryAttemptsTotal() {
        long total = 0L;
        for (JobRecord record : jobs) {
            if (record.errorMessage() != null) {
                total += record.attempts();
            } else {
                total += Math.max(0, record.attempts() - 1);
            }
        }
        return total;
    }

    public synchronized String toJson() {
        StringBuilder builder = new StringBuilder("{\"jobs\":[");
        boolean first = true;
        for (JobRecord record : jobs) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            IslandJob job = record.job();
            builder.append('{')
                .append("\"id\":\"").append(job.jobId()).append("\",")
                .append("\"type\":\"").append(job.type()).append("\",")
                .append("\"islandId\":\"").append(job.islandId()).append("\",")
                .append("\"targetNode\":\"").append(job.targetNode()).append("\",")
                .append("\"state\":\"").append(record.state()).append("\",")
                .append("\"attempts\":").append(record.attempts()).append(',')
                .append("\"lockedBy\":\"").append(record.lockedBy() == null ? "" : record.lockedBy()).append("\",")
                .append("\"claimToken\":\"").append(record.claimToken() == null ? "" : record.claimToken()).append("\",")
                .append("\"claimEpoch\":").append(record.claimEpoch()).append(',')
                .append("\"leaseExpiresAt\":\"").append(record.lockedUntil() == null ? "" : record.lockedUntil()).append("\",")
                .append("\"error\":\"").append(record.errorMessage() == null ? "" : record.errorMessage()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    private void replace(UUID jobId, java.util.function.UnaryOperator<JobRecord> update) {
        for (int i = 0; i < jobs.size(); i++) {
            JobRecord record = jobs.get(i);
            if (record.job().jobId().equals(jobId)) {
                jobs.set(i, update.apply(record));
                return;
            }
        }
    }

    private boolean leaseMatches(JobRecord record, UUID jobId, JobClaimLease claimLease) {
        return record.state() == JobState.CLAIMED
            && record.job().jobId().equals(jobId)
            && claimLease != null
            && claimLease.matches(jobId, record.lockedBy())
            && claimLease.claimToken().equals(record.claimToken())
            && claimLease.claimEpoch() == record.claimEpoch()
            && record.lockedUntil() != null
            && record.lockedUntil().isAfter(Instant.now());
    }

    private enum JobState {
        PENDING,
        CLAIMED,
        COMPLETED,
        FAILED,
        CANCELED
    }

    private record JobRecord(IslandJob job, JobState state, String lockedBy, Instant lockedUntil, String claimToken, long claimEpoch, int attempts, String errorMessage, Instant updatedAt) {
        private JobRecord pending() {
            return new JobRecord(job.withClaimLease(JobClaimLease.unclaimed(job.jobId())), JobState.PENDING, null, null, null, claimEpoch, attempts, null, Instant.now());
        }

        private JobRecord canceled() {
            return new JobRecord(job.withClaimLease(JobClaimLease.unclaimed(job.jobId())), JobState.CANCELED, lockedBy, null, null, claimEpoch, attempts, errorMessage, Instant.now());
        }

        private JobRecord locked(String nodeId) {
            Instant expiresAt = Instant.now().plusSeconds(30L);
            long nextEpoch = claimEpoch + 1L;
            String token = UUID.randomUUID() + "-" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36);
            JobClaimLease lease = new JobClaimLease(job.jobId(), "", nodeId, token, nextEpoch, expiresAt, attempts + 1);
            return new JobRecord(job.withClaimLease(lease), JobState.CLAIMED, nodeId, expiresAt, token, nextEpoch, attempts + 1, null, Instant.now());
        }

        private JobRecord completed() {
            return new JobRecord(job.withClaimLease(JobClaimLease.unclaimed(job.jobId())), JobState.COMPLETED, lockedBy, null, null, claimEpoch, attempts, null, Instant.now());
        }

        private JobRecord failed(String error) {
            Map<String, String> payload = new HashMap<>(job.payload());
            payload.put("lastError", error == null ? "unknown" : error);
            payload.put("attempts", Integer.toString(attempts));
            IslandJob failedJob = new IslandJob(job.jobId(), job.type(), job.islandId(), job.targetNode(), job.priority(), Map.copyOf(payload), job.createdAt());
            JobState nextState = attempts < MAX_ATTEMPTS ? JobState.PENDING : JobState.FAILED;
            String nextLockedBy = nextState == JobState.PENDING ? null : lockedBy;
            return new JobRecord(failedJob, nextState, nextLockedBy, null, null, claimEpoch, attempts, error, Instant.now());
        }

        private IslandJob claimedJob() {
            if (state != JobState.CLAIMED || claimToken == null || lockedBy == null || lockedUntil == null) {
                return job;
            }
            return job.withClaimLease(new JobClaimLease(job.jobId(), "", lockedBy, claimToken, claimEpoch, lockedUntil, attempts));
        }
    }
}
