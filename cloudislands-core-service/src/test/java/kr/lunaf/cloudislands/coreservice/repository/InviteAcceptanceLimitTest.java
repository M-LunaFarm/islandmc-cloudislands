package kr.lunaf.cloudislands.coreservice.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InviteAcceptanceLimitTest {
    @Test
    void jdbcAcceptanceLocksIslandAndChecksLimitInsideTransaction() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/repository/JdbcIslandMetadataRepository.java"));
        int start = source.indexOf("public boolean acceptInvite(UUID inviteId, UUID playerUuid, long maxMembers)");
        int end = source.indexOf("public boolean declineInvite", start);
        String acceptance = source.substring(start, end);

        org.junit.jupiter.api.Assertions.assertTrue(acceptance.contains("SELECT id FROM islands WHERE id = ? AND deleted_at IS NULL FOR UPDATE"));
        org.junit.jupiter.api.Assertions.assertTrue(acceptance.contains("memberCount(connection, invite.islandId()) >= Math.max(0L, maxMembers)"));
        org.junit.jupiter.api.Assertions.assertTrue(acceptance.indexOf("lockInvite(connection, inviteId)") < acceptance.indexOf("memberCount(connection, invite.islandId())"));
        org.junit.jupiter.api.Assertions.assertTrue(acceptance.indexOf("memberCount(connection, invite.islandId())") < acceptance.indexOf("connection.commit();"));
    }

    @Test
    void concurrentInviteAcceptanceCannotExceedMemberLimit() throws Exception {
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        UUID islandId = UUID.randomUUID();
        UUID inviterUuid = UUID.randomUUID();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        UUID firstInvite = metadata.createInvite(islandId, inviterUuid, firstPlayer).inviteId();
        UUID secondInvite = metadata.createInvite(islandId, inviterUuid, secondPlayer).inviteId();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> accept(metadata, firstInvite, firstPlayer, ready, start));
            Future<Boolean> second = executor.submit(() -> accept(metadata, secondInvite, secondPlayer, ready, start));
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            int accepted = (first.get(5, TimeUnit.SECONDS) ? 1 : 0) + (second.get(5, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, accepted);
            assertEquals(1, metadata.members(islandId).size());
        }
    }

    private static boolean accept(InMemoryIslandMetadataRepository metadata, UUID inviteId, UUID playerUuid, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        return metadata.acceptInvite(inviteId, playerUuid, 1L);
    }
}
