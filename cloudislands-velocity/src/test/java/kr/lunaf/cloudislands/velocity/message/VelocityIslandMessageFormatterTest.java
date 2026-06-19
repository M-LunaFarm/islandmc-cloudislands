package kr.lunaf.cloudislands.velocity.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VelocityIslandMessageFormatterTest {
    private final VelocityIslandMessageFormatter formatter = new VelocityIslandMessageFormatter();

    @Test
    void formatsPlayerIslandList() {
        String body = """
            [{"islandId":"11111111-1111-1111-1111-111111111111","name":"Alpha","role":"OWNER","level":42},{"islandId":"22222222-2222-2222-2222-222222222222","name":"","role":"","level":0}]
            """;

        assertEquals(
            "내 섬 목록: Alpha (ID=11111111, 역할=OWNER, 레벨=42) / 이름 없는 섬 (ID=22222222, 역할=MEMBER, 레벨=0)",
            formatter.playerIslands(body)
        );
    }

    @Test
    void formatsEmptyPlayerIslandList() {
        assertEquals("속한 섬이 없습니다.", formatter.playerIslands("[]"));
    }

    @Test
    void formatsPublicIslandList() {
        String body = """
            {"islands":[{"islandId":"33333333-3333-3333-3333-333333333333","name":"Market","level":9,"worth":"1200"},{"islandId":"44444444-4444-4444-4444-444444444444","name":"","level":1}]}
            """;

        assertEquals(
            "공개 섬: 1. Market (ID=33333333, 레벨=9, 가치=1200) | 2. 이름 없는 섬 (ID=44444444, 레벨=1, 가치=0)",
            formatter.publicIslands(body)
        );
    }

    @Test
    void formatsEmptyPublicIslandList() {
        assertEquals("공개 섬이 없습니다.", formatter.publicIslands("{\"islands\":[]}"));
    }

    @Test
    void formatsInviteList() {
        String body = """
            [{"inviteId":"aaaaaaaa-0000-0000-0000-000000000000","islandId":"bbbbbbbb-0000-0000-0000-000000000000","inviterUuid":"cccccccc-0000-0000-0000-000000000000"},{"inviteId":"dddddddd-0000-0000-0000-000000000000"}]
            """;

        assertEquals(
            "섬 초대: aaaaaaaa 섬=bbbbbbbb 초대한사람=cccccccc, dddddddd",
            formatter.invites(body)
        );
    }

    @Test
    void formatsEmptyInviteList() {
        assertEquals("대기 중인 섬 초대가 없습니다.", formatter.invites("[]"));
    }

    @Test
    void formatsActionResultWithAdminDetail() {
        assertEquals(
            "Island activate: rejected target=11111111 code=NO_READY_NODE_POOL detail=no-ready-node 섬=22222222",
            formatter.actionResult(
                "Island activate",
                "11111111-1111-1111-1111-111111111111",
                "{\"accepted\":false,\"code\":\"NO_READY_NODE_POOL\",\"islandId\":\"22222222-2222-2222-2222-222222222222\"}"
            )
        );
    }

    @Test
    void hidesRuntimeTopologyWhenConfigured() {
        VelocityIslandMessageFormatter hidden = new VelocityIslandMessageFormatter(new VelocityRoutePrivacyFormatter(true));

        assertEquals(
            "Island runtime: 섬=33333333 state=READY fence=7",
            hidden.runtimeInfo("{\"islandId\":\"33333333-3333-3333-3333-333333333333\",\"state\":\"READY\",\"activeNode\":\"island-1\",\"activeWorld\":\"ci_shard_001\",\"cellX\":3,\"cellZ\":4,\"fencingToken\":7}")
        );
    }

    @Test
    void showsRuntimeTopologyWhenConfigured() {
        VelocityIslandMessageFormatter visible = new VelocityIslandMessageFormatter(new VelocityRoutePrivacyFormatter(false));

        assertEquals(
            "Island runtime: 섬=33333333 state=READY node=island-1 world=ci_shard_001 cell=3,4 fence=7",
            visible.runtimeInfo("{\"islandId\":\"33333333-3333-3333-3333-333333333333\",\"state\":\"READY\",\"activeNode\":\"island-1\",\"activeWorld\":\"ci_shard_001\",\"cellX\":3,\"cellZ\":4,\"fencingToken\":7}")
        );
    }
}
