package kr.lunaf.cloudislands.coreservice.job;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;

public final class InMemoryIslandJobPublisher implements IslandJobQueue {
    private static final int MAX_ATTEMPTS = 3;
    private final List<JobRecord> jobs = new ArrayList<>();

    @Override
    public synchronized void publish(IslandJob job) {
        jobs.add(new JobRecord(job, JobState.PENDING, null, null, 0, null, Instant.now()));
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
            .map(JobRecord::job)
            .findFirst();
    }

    
    public synchronized void complete(String nodeId, UUID jobId) {
        replace(jobId, record -> record.lockedBy() != null && record.lockedBy().equals(nodeId) ? record.completed() : record);
    }

    @Override
    public synchronized void fail(String nodeId, UUID jobId, String errorMessage) {
        replace(jobId, record -> record.lockedBy() != null && record.lockedBy().equals(nodeId) ? record.failed(errorMessage) : record);
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

    private enum JobState {
        PENDING,
        CLAIMED,
        COMPLETED,
        FAILED
    }

    private record JobRecord(IslandJob job, JobState state, String lockedBy, Instant lockedAt, int attempts, String errorMessage, Instant updatedAt) {
        private JobRecord locked(String nodeId) {
            return new JobRecord(job, JobState.CLAIMED, nodeId, Instant.now(), attempts + 1, null, Instant.now());
        }

        private JobRecord completed() {
            return new JobRecord(job, JobState.COMPLETED, lockedBy, lockedAt, attempts, null, Instant.now());
        }

        private JobRecord failed(String error) {
            Map<String, String> payload = new HashMap<>(job.payload());
            payload.put("lastError", error == null ? "unknown" : error);
            payload.put("attempts", Integer.toString(attempts));
            IslandJob failedJob = new IslandJob(job.jobId(), job.type(), job.islandId(), job.targetNode(), job.priority(), Map.copyOf(payload), job.createdAt());
            JobState nextState = attempts < MAX_ATTEMPTS ? JobState.PENDING : JobState.FAILED;
            String nextLockedBy = nextState == JobState.PENDING ? null : lockedBy;
            return new JobRecord(failedJob, nextState, nextLockedBy, null, attempts, error, Instant.now());
        }
    }
}
