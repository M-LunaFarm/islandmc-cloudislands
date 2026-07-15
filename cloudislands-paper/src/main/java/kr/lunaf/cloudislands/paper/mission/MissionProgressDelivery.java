package kr.lunaf.cloudislands.paper.mission;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreMutationContext;
import kr.lunaf.cloudislands.coreclient.CoreMutationMetadata;
import kr.lunaf.cloudislands.coreclient.ProgressionCommandClient;
import kr.lunaf.cloudislands.coreclient.ProgressionMissionCompletionView;

/** Sends mission mutations with replay-safe transport metadata. */
public final class MissionProgressDelivery {
    private MissionProgressDelivery() {
    }

    public static CompletableFuture<ProgressionMissionCompletionView> increment(
        ProgressionCommandClient commands,
        UUID islandId,
        UUID actorUuid,
        String missionKey,
        String kind,
        long amount
    ) {
        return deliver(commands, "island.mission.progress", () ->
            commands.progressMission(islandId, actorUuid, missionKey, kind, amount));
    }

    public static CompletableFuture<ProgressionMissionCompletionView> advanceTo(
        ProgressionCommandClient commands,
        UUID islandId,
        UUID actorUuid,
        String missionKey,
        String kind,
        long progress
    ) {
        return deliver(commands, "island.mission.progress-to", () ->
            commands.progressMissionTo(islandId, actorUuid, missionKey, kind, progress));
    }

    private static CompletableFuture<ProgressionMissionCompletionView> deliver(
        ProgressionCommandClient commands,
        String auditAction,
        java.util.function.Supplier<CompletableFuture<ProgressionMissionCompletionView>> operation
    ) {
        if (commands == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("progression commands are required"));
        }
        String deliveryKey = "mission-" + UUID.randomUUID();
        return CoreMutationContext.with(CoreMutationMetadata.idempotent(auditAction, deliveryKey), operation);
    }
}
