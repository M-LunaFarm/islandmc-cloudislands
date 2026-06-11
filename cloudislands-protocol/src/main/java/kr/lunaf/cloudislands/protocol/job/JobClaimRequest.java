package kr.lunaf.cloudislands.protocol.job;

import java.util.List;

public record JobClaimRequest(String nodeId, List<IslandJobType> supportedTypes, int maxJobs) {}
