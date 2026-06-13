package kr.seungmin.satisskyfactory.hook;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudIslandsSkyblockProviderApiTest {
    @Test
    void exposesGoalIslandUuidAccessors() {
        CloudIslandsSkyblockProvider provider = new CloudIslandsSkyblockProvider(null);
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000006001");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000006002");
        IslandRef island = new IslandRef(new Object(), islandUuid, ownerUuid);

        assertEquals(islandUuid, provider.getIslandUuid(island));
        assertEquals(ownerUuid, provider.getIslandOwnerUuid(island));
    }
}
