package kr.lunaf.cloudislands.coreservice.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InMemoryPlayerProfileRepositoryTest {
    @Test
    void partialUpdatesPreserveAtomicSaturatingDisbandQuota() throws Exception {
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        InMemoryPlayerProfileRepository repository = new InMemoryPlayerProfileRepository();

        try (var workers = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < 1_000; index++) {
                int sequence = index;
                workers.submit(() -> repository.addDisbandsRemaining(playerUuid, 1));
                workers.submit(() -> repository.touch(playerUuid, "Player" + sequence, sequence % 2 == 0 ? "ko_kr" : "en_us"));
            }
            workers.shutdown();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertEquals(1_000, repository.find(playerUuid).disbandsRemaining());
        assertEquals(Integer.MAX_VALUE, repository.addDisbandsRemaining(playerUuid, Integer.MAX_VALUE).disbandsRemaining());
        assertEquals(Integer.MAX_VALUE, repository.addDisbandsRemaining(playerUuid, 1).disbandsRemaining());
        assertEquals(0, repository.addDisbandsRemaining(playerUuid, Integer.MIN_VALUE).disbandsRemaining());
        assertEquals(0, repository.addDisbandsRemaining(playerUuid, -1).disbandsRemaining());
    }
}
