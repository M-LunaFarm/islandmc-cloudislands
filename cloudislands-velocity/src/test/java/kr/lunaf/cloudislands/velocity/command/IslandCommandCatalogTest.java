package kr.lunaf.cloudislands.velocity.command;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandCommandCatalogTest {
    @Test
    void playerCommandCatalogIncludesGoalCommandsOnePerLine() {
        List<String> commands = IslandCommandCatalog.playerCommands();

        for (String command : List.of(
                "섬",
                "섬 생성",
                "섬 생성 <템플릿>",
                "섬 홈",
                "섬 홈 <이름>",
                "섬 셋홈",
                "섬 셋홈 <이름>",
                "섬 방문 <플레이어>",
                "섬 방문 <섬이름>",
                "섬 랜덤방문",
                "섬 초대 <player>",
                "섬 초대수락 <플레이어|섬|inviteId>",
                "섬 초대거절 <플레이어|섬|inviteId>",
                "섬 멤버",
                "섬 추방 <player>",
                "섬 승급 <player>",
                "섬 강등 <player>",
                "섬 양도 <player>",
                "섬 신뢰 <player>",
                "섬 신뢰해제 <player>",
                "섬 밴 <player>",
                "섬 밴해제 <player>",
                "섬 밴목록",
                "섬 공개",
                "섬 비공개",
                "섬 잠금",
                "섬 잠금해제",
                "섬 설정",
                "섬 권한",
                "섬 플래그",
                "섬 워프",
                "섬 워프 <이름>",
                "섬 워프설정 <name>",
                "섬 워프삭제 <name>",
                "섬 워프공개 <name>",
                "섬 워프비공개 <name>",
                "섬 레벨",
                "섬 가치",
                "섬 랭킹 [limit]",
                "섬 레벨계산",
                "섬 업그레이드",
                "섬 크기",
                "섬 바이옴",
                "섬 바이옴 <바이옴>",
                "섬 경계",
                "섬 미션 [missionKey]",
                "섬 챌린지 [challengeKey]",
                "섬 채팅 <message>",
                "섬 팀채팅 <message>",
                "섬 로그",
                "섬 리셋 [reason] confirm",
                "섬 삭제 confirm"
        )) {
            assertTrue(commands.contains(command), command);
        }
    }

    @Test
    void adminCommandCatalogIncludesGoalCommandsAndKoreanAlias() {
        List<String> commands = IslandCommandCatalog.adminCommands(true);

        for (String command : List.of(
                "ciadmin",
                "섬관리",
                "ciadmin island info <island|player>",
                "ciadmin island where <island>",
                "ciadmin island tp <island>",
                "ciadmin island activate <island>",
                "ciadmin island deactivate <island>",
                "ciadmin island migrate <island> <node>",
                "ciadmin island save <island>",
                "ciadmin island snapshot <island> [reason]",
                "ciadmin island rollback <island> <snapshot>",
                "ciadmin island quarantine <island> [reason]",
                "ciadmin island repair <island> [reason]",
                "ciadmin island delete <island> confirm",
                "ciadmin island restore <island> <snapshot>",
                "ciadmin player info <player>",
                "ciadmin player setisland <player> <islandUuid>",
                "ciadmin player clearisland <player>",
                "ciadmin node list",
                "ciadmin node info <node>",
                "ciadmin node drain <node>",
                "ciadmin node undrain <node>",
                "ciadmin node kickall <node> [reason]",
                "ciadmin node shutdown-safe <node> [reason]",
                "ciadmin route debug [all|player]",
                "ciadmin route ticket <ticket|player>",
                "ciadmin route clear <player> [ticket]",
                "ciadmin jobs list",
                "ciadmin jobs retry <jobId>",
                "ciadmin jobs cancel <jobId>",
                "ciadmin cache clear",
                "ciadmin reload",
                "ciadmin migrate-superiorskyblock2 scan [path]"
        )) {
            assertTrue(commands.contains(command), command);
        }
    }

    @Test
    void destructiveVelocityCommandsRequireConfirmationBeforeCoreMutation() throws Exception {
        String support = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityCommandSupport.java"));
        String player = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerCommandDispatcher.java"));
        String admin = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityAdminCommandDispatcher.java"));
        String suggestions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityCommandSuggestions.java"));

        assertTrue(support.contains("destructiveConfirmed(String[] args)"), "Velocity command support must define a shared destructive confirmation boundary");
        assertTrue(support.contains("confirm") && support.contains("확인"), "Velocity destructive confirmation must support English and Korean confirmation tokens");
        assertTrue(indexOf(player, "sendDestructiveConfirmationRequired(player, \"섬 리셋 [reason] confirm\")") < indexOf(player, "playerRouting.resetIsland("), "player reset must require confirmation before Core mutation");
        assertTrue(indexOf(player, "sendDestructiveConfirmationRequired(player, \"섬 삭제 confirm\")") < indexOf(player, "playerRouting.deleteIsland("), "player delete must require confirmation before Core mutation");
        assertTrue(indexOf(admin, "sendDestructiveConfirmationRequired(player, \"ciadmin island delete <island> confirm\")") < indexOf(admin, "adminActions.adminDeleteIslandTarget("), "admin delete must require confirmation before Core mutation");
        assertTrue(suggestions.contains("List.of(\"confirm\", \"확인\")"), "Velocity suggestions must expose destructive confirmation tokens");
    }

    private int indexOf(String source, String needle) {
        int index = source.indexOf(needle);
        assertTrue(index >= 0, needle);
        return index;
    }
}
