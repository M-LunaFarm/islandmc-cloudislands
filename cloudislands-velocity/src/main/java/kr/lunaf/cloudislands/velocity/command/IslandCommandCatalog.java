package kr.lunaf.cloudislands.velocity.command;

import java.util.List;

public final class IslandCommandCatalog {
    private IslandCommandCatalog() {}

    public static List<String> playerCommands() {
        return List.of(
            "섬 help [page]", "섬 command list [page]", "섬", "섬 메뉴", "섬 생성 [template]", "섬 목록", "섬 내섬",
            "섬 홈 [name]", "섬 홈목록", "섬 셋홈 [name]", "섬 방문 <섬|플레이어|random>", "섬 랜덤방문", "섬 공개섬 [limit]",
            "섬 초대 <player>", "섬 초대목록", "섬 초대수락 <inviteId>", "섬 초대거절 <inviteId>", "섬 멤버", "섬 추방 <player>",
            "섬 승급 <player>", "섬 강등 <player>", "섬 양도 <player>", "섬 신뢰 <player>", "섬 신뢰해제 <player>",
            "섬 밴 <player>", "섬 밴해제 <player>", "섬 밴목록", "섬 방문자추방 <player>", "섬 공개", "섬 비공개",
            "섬 잠금", "섬 잠금해제", "섬 비행 [true|false]", "섬 인벤보존 [true|false]", "섬 피빕 [true|false]", "섬 공개워프 [true|false]",
            "섬 설정", "섬 권한", "섬 권한설정 <role> <permission> <true|false>", "섬 플래그", "섬 워프 <name>", "섬 워프목록", "섬 공개워프목록", "섬 워프설정 <name>",
            "섬 워프삭제 <name>", "섬 워프공개 <name>", "섬 워프비공개 <name>",
            "섬 레벨", "섬 레벨계산", "섬 가치", "섬 랭킹 [limit]", "섬 랭킹 worth [limit]", "섬 가치랭킹 [limit]",
            "섬 업그레이드", "섬 업그레이드목록", "섬 업그레이드구매 <upgradeKey>", "섬 크기", "섬 경계", "섬 바이옴 <biomeKey>",
            "섬 은행", "섬 입금 <amount>", "섬 출금 <amount>", "섬 미션 [missionKey]", "섬 챌린지 [challengeKey]", "섬 채팅 <message>", "섬 팀채팅 <message>",
            "섬 제한 [limitKey value]", "섬 호퍼 <limit>", "섬 스포너 <limit>", "섬 엔티티 <limit>", "섬 레드스톤 <limit>", "섬 스냅샷 [reason]",
            "섬 스냅샷목록", "섬 복원 <snapshotNo>", "섬 로그", "섬 리셋 [reason]", "섬 삭제"
        );
    }

    public static List<String> adminCommands() {
        return List.of(
            "ciadmin status",
            "ciadmin help [page]",
            "ciadmin command list [page]",
            "ciadmin island info <island|player>", "ciadmin island where <island>", "ciadmin island tp <island>",
            "ciadmin island activate <island>", "ciadmin island deactivate <island>", "ciadmin island migrate <island> <node>",
            "ciadmin island save <island>", "ciadmin island snapshot <island> [reason]", "ciadmin island snapshots <island>", "ciadmin island rollback <island> <snapshot>",
            "ciadmin island quarantine <island> [reason]", "ciadmin island repair <island> [reason]", "ciadmin island delete <island>",
            "ciadmin island restore <island> <snapshot>", "ciadmin player info <player>", "ciadmin player setisland <player> <islandUuid>",
            "ciadmin player clearisland <player>", "ciadmin node list", "ciadmin node info <node>", "ciadmin node islands <node> [limit]",
            "ciadmin node drain <node>", "ciadmin node undrain <node>", "ciadmin node kickall <node> [reason]",
            "ciadmin node sweep [node]", "ciadmin node shutdown-safe <node> [reason]", "ciadmin jobs list", "ciadmin jobs retry <jobId>",
            "ciadmin jobs cancel <jobId>", "ciadmin jobs recover [nodeId] [minIdleMillis] [maxJobs]", "ciadmin route debug [all|player]", "ciadmin route ticket <ticket|player>",
            "ciadmin route clear <player> [ticket]", "ciadmin cache clear", "ciadmin events", "ciadmin audit", "ciadmin metrics", "ciadmin storage",
            "ciadmin rankings level [limit]", "ciadmin rankings worth [limit]",
            "ciadmin block-values list", "ciadmin block-values set <materialKey> <worth> <levelPoints> <limit>", "ciadmin upgrade-rules", "ciadmin template list",
            "ciadmin template upsert <id> <name> [enabled] [minNodeVersion]", "ciadmin template enable <id>",
            "ciadmin template disable <id>", "ciadmin templates list",
            "ciadmin templates upsert <id> <name> [enabled] [minNodeVersion]", "ciadmin templates enable <id>",
            "ciadmin templates disable <id>", "ciadmin reload",
            "ciadmin migrate-superiorskyblock2 scan", "ciadmin migrate-superiorskyblock2 dryrun",
            "ciadmin migrate-superiorskyblock2 dry-run",
            "ciadmin migrate-superiorskyblock2 import", "ciadmin migrate-superiorskyblock2 verify",
            "ciadmin migrate-superiorskyblock2 rollback"
        );
    }
}
