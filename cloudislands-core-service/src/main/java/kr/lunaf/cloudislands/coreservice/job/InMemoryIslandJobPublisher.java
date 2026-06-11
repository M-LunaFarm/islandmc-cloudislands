package kr.lunaf.cloudislands.coreservice.job;

import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.protocol.job.IslandJob;

public final class InMemoryIslandJobPublisher implements IslandJobPublisher {
    private final List<IslandJob> jobs = new ArrayList<>();

    @Override
    public synchronized void publish(IslandJob job) {
        jobs.add(job);
    }

    public synchronized List<IslandJob> snapshot() {
        return List.copyOf(jobs);
    }

    public synchronized String toJson() {
        StringBuilder builder = new StringBuilder("{\"jobs\":[");
        boolean first = true;
        for (IslandJob job : jobs) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"id\":\"").append(job.jobId()).append("\",")
                .append("\"type\":\"").append(job.type()).append("\",")
                .append("\"islandId\":\"").append(job.islandId()).append("\",")
                .append("\"targetNode\":\"").append(job.targetNode()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }
}
