package kr.lunaf.cloudislands.velocity.event;

import java.util.List;

public record CoreEventBatch(
    long oldestSequence,
    long latestSequence,
    List<CoreEventEnvelope> events
) {
}
