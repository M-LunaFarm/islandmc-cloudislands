package kr.lunaf.cloudislands.coreservice.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicIslandPaginationTest {
    @Test
    void publicIslandPagesAreStableOrderedAndExcludeLockedIslands() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID locked = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        metadata.setPublicAccess(third, true);
        metadata.setPublicAccess(first, true);
        metadata.setPublicAccess(locked, true);
        metadata.setLocked(locked, true);

        assertEquals(List.of(first), metadata.publicIslandIdsPage(0, 1));
        assertEquals(List.of(third), metadata.publicIslandIdsPage(1, 1));
        assertEquals(List.of(first, third), metadata.publicIslandIdsPage(0, 10));
    }

    @Test
    void jdbcAppliesDisplayOrderingBeforePageBoundaries() throws Exception {
        String jdbc = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/repository/JdbcIslandMetadataRepository.java"));
        assertEquals(1, jdbc.split(java.util.regex.Pattern.quote("ORDER BY level DESC, name ASC, id ASC LIMIT ? OFFSET ?"), -1).length - 1);
    }
}
