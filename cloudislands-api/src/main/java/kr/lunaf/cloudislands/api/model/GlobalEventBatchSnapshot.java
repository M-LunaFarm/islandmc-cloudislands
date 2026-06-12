package kr.lunaf.cloudislands.api.model;

import java.util.List;

public record GlobalEventBatchSnapshot(long oldestSequence, long latestSequence, List<GlobalEventSnapshot> events) {}
