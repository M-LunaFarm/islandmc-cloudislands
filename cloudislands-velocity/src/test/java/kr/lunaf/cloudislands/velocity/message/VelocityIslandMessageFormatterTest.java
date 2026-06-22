package kr.lunaf.cloudislands.velocity.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.coreclient.AdminIslandRuntimeView;
import kr.lunaf.cloudislands.coreclient.ChatActionView;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.EnvironmentActionView;
import kr.lunaf.cloudislands.coreclient.HomeWarpActionView;
import kr.lunaf.cloudislands.coreclient.IslandLifecycleActionView;
import kr.lunaf.cloudislands.coreclient.LevelView;
import kr.lunaf.cloudislands.coreclient.MemberActionView;
import kr.lunaf.cloudislands.coreclient.MutationResult;
import kr.lunaf.cloudislands.coreclient.PermissionActionView;
import kr.lunaf.cloudislands.coreclient.PermissionAssignmentView;
import kr.lunaf.cloudislands.coreclient.ProgressionMissionCompletionView;
import kr.lunaf.cloudislands.coreclient.ProgressionRankingEntryView;
import kr.lunaf.cloudislands.coreclient.ProgressionUpgradePurchaseView;
import kr.lunaf.cloudislands.coreclient.SettingsActionView;
import kr.lunaf.cloudislands.coreclient.UpgradeRuleView;
import org.junit.jupiter.api.Test;

class VelocityIslandMessageFormatterTest {
    private final VelocityIslandMessageFormatter formatter = new VelocityIslandMessageFormatter();

    @Test
    void formatsPlayerIslandList() {
        assertEquals(
            "내 섬 목록: Alpha (ID=11111111, 역할=OWNER, 레벨=42) / 이름 없는 섬 (ID=22222222, 역할=MEMBER, 레벨=0)",
            formatter.playerIslands(List.of(
                new CoreGuiViews.PlayerIslandView("11111111-1111-1111-1111-111111111111", "Alpha", "ACTIVE", "OWNER", 42L, "100"),
                new CoreGuiViews.PlayerIslandView("22222222-2222-2222-2222-222222222222", "", "ACTIVE", "", 0L, "0")
            ))
        );
    }

    @Test
    void formatsEmptyPlayerIslandList() {
        assertEquals("속한 섬이 없습니다.", formatter.playerIslands(List.of()));
    }

    @Test
    void formatsPublicIslandList() {
        assertEquals(
            "공개 섬: 1. Market (ID=33333333, 레벨=9, 가치=1200) | 2. 이름 없는 섬 (ID=44444444, 레벨=1, 가치=0)",
            formatter.publicIslands(List.of(
                new CoreGuiViews.PublicIslandView("33333333-3333-3333-3333-333333333333", "99999999-9999-9999-9999-999999999999", "Market", 9L, "1200"),
                new CoreGuiViews.PublicIslandView("44444444-4444-4444-4444-444444444444", "99999999-9999-9999-9999-999999999999", "", 1L, "")
            ))
        );
    }

    @Test
    void formatsEmptyPublicIslandList() {
        assertEquals("공개 섬이 없습니다.", formatter.publicIslands(List.of()));
    }

    @Test
    void formatsInviteList() {
        assertEquals(
            "섬 초대: aaaaaaaa 섬=bbbbbbbb 초대한사람=cccccccc, dddddddd",
            formatter.invites(List.of(
                new CoreGuiViews.InviteView("aaaaaaaa-0000-0000-0000-000000000000", "bbbbbbbb-0000-0000-0000-000000000000", "cccccccc-0000-0000-0000-000000000000", "", ""),
                new CoreGuiViews.InviteView("dddddddd-0000-0000-0000-000000000000", "", "", "", "")
            ))
        );
    }

    @Test
    void formatsEmptyInviteList() {
        assertEquals("대기 중인 섬 초대가 없습니다.", formatter.invites(List.of()));
    }

    @Test
    void formatsActionResultWithAdminDetail() {
        assertEquals(
            "Island activate: rejected target=11111111 code=NO_READY_NODE_POOL detail=no-ready-node 섬=22222222",
            formatter.actionResult(
                "Island activate",
                "11111111-1111-1111-1111-111111111111",
                new IslandLifecycleActionView(false, "NO_READY_NODE_POOL", "22222222-2222-2222-2222-222222222222", 0L, "")
            )
        );
    }

    @Test
    void formatsActionResultSnapshotFromTypedLifecycleView() {
        assertEquals(
            "Island restore: accepted target=11111111 snapshot=3",
            formatter.actionResult(
                "Island restore",
                "11111111-1111-1111-1111-111111111111",
                new IslandLifecycleActionView(true, "", "11111111-1111-1111-1111-111111111111", 3L, "")
            )
        );
    }

    @Test
    void hidesRuntimeTopologyWhenConfigured() {
        VelocityIslandMessageFormatter hidden = new VelocityIslandMessageFormatter(new VelocityRoutePrivacyFormatter(true));

        assertEquals(
            "Island runtime: 섬=33333333 state=READY fence=7",
            hidden.runtimeInfo(runtimeView())
        );
    }

    @Test
    void showsRuntimeTopologyWhenConfigured() {
        VelocityIslandMessageFormatter visible = new VelocityIslandMessageFormatter(new VelocityRoutePrivacyFormatter(false));

        assertEquals(
            "Island runtime: 섬=33333333 state=READY node=island-1 world=ci_shard_001 cell=3,4 fence=7",
            visible.runtimeInfo(runtimeView())
        );
    }

    @Test
    void formatsTypedProgressionCommandResults() {
        assertEquals(
            "레벨 계산: 섬=55555555 level=12 worth=9876",
            formatter.levelRecalculation(new LevelView("55555555-5555-5555-5555-555555555555", 12L, "9876", ""))
        );
        assertEquals(
            "업그레이드 구매: 접수됨 비용=250 업그레이드=generator 레벨=3",
            formatter.upgradePurchase(new ProgressionUpgradePurchaseView(true, "", "55555555-5555-5555-5555-555555555555", "generator", "GENERATOR", 3L, "250", ""))
        );
        assertEquals(
            "섬 미션: 완료 키=builder 보상=coins:100",
            formatter.missionResult("섬 미션", new ProgressionMissionCompletionView(true, "", "55555555-5555-5555-5555-555555555555", "builder", "MISSION", "Builder", 10L, 10L, true, "coins:100", ""))
        );
    }

    @Test
    void formatsTypedProgressionListsAndActions() {
        assertEquals(
            "업그레이드 규칙: 전체 1개 / generator 유형=GENERATOR 최대=5 기본비용=100",
            formatter.upgradeRules(List.of(new UpgradeRuleView("generator", "GENERATOR", 5L, "100", "2.0")))
        );
        assertEquals(
            "섬 업그레이드: generator:stone 레벨=4 유형=GENERATOR",
            formatter.upgradeList(List.of(new CoreGuiViews.UpgradeView("generator:stone", "GENERATOR", 4)))
        );
        assertEquals(
            "섬 생성기: key=stone level=4 / 업그레이드: /섬 업그레이드구매 generator",
            formatter.generatorInfo(List.of(new CoreGuiViews.UpgradeView("generator:stone", "GENERATOR", 4)))
        );
        assertEquals(
            "섬 미션: builder 7/10 완료=false",
            formatter.missionList("섬 미션", List.of(new CoreGuiViews.MissionView("builder", "Builder", 7L, 10L, false, "coins:100")))
        );
        assertEquals(
            "섬 제한: members 값=8",
            formatter.limitList(List.of(new CoreGuiViews.LimitView("members", 8L, "")))
        );
        assertEquals(
            "섬 제한 변경: members=8 섬=55555555",
            formatter.limitResult(new EnvironmentActionView(true, "", "members", 8L, "55555555-5555-5555-5555-555555555555", "", ""))
        );
        assertEquals(
            "섬 채팅: 전송 완료 채널=ISLAND",
            formatter.chatResult("섬 채팅", new ChatActionView(true, "CHAT_SENT", "ISLAND", "hello"))
        );
    }

    @Test
    void formatsTypedRoutingViewsAndActions() {
        assertEquals(
            "공개 섬: 1. Market (ID=33333333, 레벨=9, 가치=1200)",
            formatter.publicIslands(List.of(new CoreGuiViews.PublicIslandView("33333333-3333-3333-3333-333333333333", "99999999-9999-9999-9999-999999999999", "Market", 9L, "1200")))
        );
        assertEquals(
            "섬 정보: ID=33333333 소유자=99999999 이름=Market 상태=READY 크기=100 레벨=9 가치=1200 공개=true",
            formatter.islandInfo(new CoreGuiViews.IslandInfoView("Market", "READY", "33333333-3333-3333-3333-333333333333", 9L, "1200", true, false, 100L, 50L, "99999999-9999-9999-9999-999999999999"))
        );
        assertEquals(
            "섬 가치: 섬=33333333 값=1200",
            formatter.islandStat("섬 가치", "worth", new CoreGuiViews.IslandInfoView("Market", "READY", "33333333-3333-3333-3333-333333333333", 9L, "1200", true, false, 100L, 50L, "99999999-9999-9999-9999-999999999999"))
        );
        assertEquals(
            "섬 바이옴: 섬=33333333 바이옴=PLAINS 변경자=99999999",
            formatter.biomeInfo(java.util.UUID.fromString("33333333-3333-3333-3333-333333333333"), new CoreGuiViews.BiomeView("PLAINS", "99999999-9999-9999-9999-999999999999", ""))
        );
        assertEquals(
            "섬 랭킹: 전체 1개 / #1 Market (ID=33333333, 레벨=9, 가치=1200)",
            formatter.rankingList("섬 랭킹", List.of(new ProgressionRankingEntryView("33333333-3333-3333-3333-333333333333", "Market", 9L, "1200", "level")))
        );
        assertEquals(
            "섬 워프: 전체 1개 / spawn(공개) 섬=33333333 위치=0.500,100.000,0.500",
            formatter.warpList("섬 워프", List.of(new CoreGuiViews.WarpView("33333333-3333-3333-3333-333333333333", "spawn", 0.5D, 100.0D, 0.5D, true)))
        );
        assertEquals(
            "섬 워프 설정: 접수됨 code=WARP_SET",
            formatter.homeWarpAction("섬 워프 설정", new HomeWarpActionView(true, "WARP_SET"))
        );
    }

    @Test
    void formatsTypedMembershipViewsAndActions() {
        assertEquals(
            "섬 초대: aaaaaaaa 섬=bbbbbbbb 초대한사람=cccccccc",
            formatter.invites(List.of(new CoreGuiViews.InviteView("aaaaaaaa-0000-0000-0000-000000000000", "bbbbbbbb-0000-0000-0000-000000000000", "cccccccc-0000-0000-0000-000000000000", "dddddddd-0000-0000-0000-000000000000", "PENDING", "", "")))
        );
        assertEquals(
            "초대: 생성됨 invite=aaaaaaaa 섬=bbbbbbbb target=dddddddd state=PENDING",
            formatter.inviteCreate(new CoreGuiViews.InviteView("aaaaaaaa-0000-0000-0000-000000000000", "bbbbbbbb-0000-0000-0000-000000000000", "cccccccc-0000-0000-0000-000000000000", "dddddddd-0000-0000-0000-000000000000", "PENDING", "", ""))
        );
        assertEquals(
            "섬 멤버: 전체 1개 / 11111111 역할=MEMBER",
            formatter.memberList(List.of(new CoreGuiViews.MemberView("11111111-1111-1111-1111-111111111111", "MEMBER", "", "Player", "", "", "")))
        );
        assertEquals(
            "섬 밴: 전체 1개 / 22222222 사유=spam",
            formatter.banList(List.of(new CoreGuiViews.BanView("22222222-2222-2222-2222-222222222222", "11111111-1111-1111-1111-111111111111", "spam", "", "")))
        );
        assertEquals(
            "섬 권한: 전체 1개 / MEMBER:BUILD=허용",
            formatter.permissionList(List.of(new PermissionAssignmentView("MEMBER", "", "BUILD", true, "v1")))
        );
        assertEquals(
            "섬 역할: 전체 1개 / MEMBER weight=10 name=Member",
            formatter.roleList(List.of(new CoreGuiViews.RoleView("MEMBER", 10, "Member")))
        );
        assertEquals(
            "섬 홈: 전체 1개 / home 위치=0.500,100.000,0.500",
            formatter.homeList(List.of(new CoreGuiViews.HomeView("home", 0.5D, 100.0D, 0.5D, "")))
        );
        assertEquals(
            "섬 은행: 섬=33333333 balance=500",
            formatter.bankInfo(java.util.UUID.fromString("33333333-3333-3333-3333-333333333333"), new CoreGuiViews.BankView("500", ""))
        );
        assertEquals(
            "섬 플래그: 전체 1개 / FLY=true",
            formatter.flagList(Map.of(IslandFlag.FLY, "true"))
        );
        assertEquals(
            "섬 멤버 역할 변경: 접수됨 code=MEMBER_ROLE_SET",
            formatter.memberAction("섬 멤버 역할 변경", new MemberActionView(true, "MEMBER_ROLE_SET", ""))
        );
        assertEquals(
            "섬 공개 변경: 접수됨 code=PUBLIC_ACCESS_ENABLED",
            formatter.settingsAction("섬 공개 변경", new SettingsActionView(true, "PUBLIC_ACCESS_ENABLED"))
        );
        assertEquals(
            "섬 권한 변경: 접수됨 code=PERMISSION_SET",
            formatter.permissionAction("섬 권한 변경", new PermissionActionView(true, "PERMISSION_SET"))
        );
        assertEquals(
            "섬 역할 저장 완료: MEMBER weight=10 name=Member version=v2",
            formatter.roleMutation("섬 역할 저장 완료", new MutationResult<>(new CoreGuiViews.RoleView("MEMBER", 10, "Member"), "v2", true))
        );
    }

    private static AdminIslandRuntimeView runtimeView() {
        return new AdminIslandRuntimeView(
            "33333333-3333-3333-3333-333333333333",
            "READY",
            "island-1",
            "ci_shard_001",
            3L,
            4L,
            "",
            7L,
            "",
            "",
            ""
        );
    }
}
