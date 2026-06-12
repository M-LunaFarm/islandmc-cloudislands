package kr.lunaf.cloudislands.velocity.command;

import java.util.List;

public final class IslandCommandCatalog {
    private IslandCommandCatalog() {}

    public static List<String> playerCommands() {
        return List.of(
            "섬", "섬 생성", "섬 목록", "섬 내섬", "섬 홈", "섬 홈목록", "섬 셋홈", "섬 방문", "섬 랜덤방문",
            "섬 초대", "섬 초대목록", "섬 초대수락", "섬 초대거절", "섬 멤버", "섬 추방",
            "섬 승급", "섬 강등", "섬 양도", "섬 신뢰", "섬 신뢰해제",
            "섬 밴", "섬 밴해제", "섬 밴목록", "섬 방문자추방", "섬 공개", "섬 비공개",
            "섬 잠금", "섬 잠금해제", "섬 비행", "섬 인벤보존", "섬 피빕", "섬 공개워프",
            "섬 설정", "섬 권한", "섬 권한설정", "섬 플래그", "섬 워프", "섬 워프목록", "섬 공개워프목록", "섬 워프설정",
            "섬 워프삭제", "섬 워프공개", "섬 워프비공개",
            "섬 레벨", "섬 레벨계산", "섬 가치", "섬 랭킹", "섬 가치랭킹",
            "섬 업그레이드", "섬 업그레이드목록", "섬 업그레이드구매", "섬 크기", "섬 경계", "섬 바이옴",
            "섬 은행", "섬 입금", "섬 출금", "섬 미션", "섬 챌린지", "섬 채팅", "섬 팀채팅",
            "섬 제한", "섬 호퍼", "섬 스포너", "섬 엔티티", "섬 레드스톤", "섬 스냅샷",
            "섬 스냅샷목록", "섬 복원", "섬 로그", "섬 리셋", "섬 삭제"
        );
    }

    public static List<String> adminCommands() {
        return List.of(
            "ciadmin island info", "ciadmin island where", "ciadmin island tp",
            "ciadmin island activate", "ciadmin island deactivate", "ciadmin island migrate",
            "ciadmin island save", "ciadmin island snapshot", "ciadmin island snapshots", "ciadmin island rollback",
            "ciadmin island quarantine", "ciadmin island repair", "ciadmin island delete",
            "ciadmin island restore", "ciadmin player info", "ciadmin player setisland",
            "ciadmin player clearisland", "ciadmin node list", "ciadmin node info", "ciadmin node islands <node> [limit]",
            "ciadmin node drain", "ciadmin node undrain", "ciadmin node kickall",
            "ciadmin node sweep", "ciadmin node shutdown-safe", "ciadmin jobs list", "ciadmin jobs retry",
            "ciadmin jobs cancel", "ciadmin jobs recover", "ciadmin route debug [all|player]", "ciadmin route ticket <ticket|player>",
            "ciadmin route clear <player> [ticket]", "ciadmin cache clear", "ciadmin events", "ciadmin audit",
            "ciadmin block-values list", "ciadmin block-values set", "ciadmin template list",
            "ciadmin template upsert <id> <name> [enabled] [minNodeVersion]", "ciadmin template enable <id>",
            "ciadmin template disable <id>", "ciadmin reload",
            "ciadmin migrate-superiorskyblock2 scan", "ciadmin migrate-superiorskyblock2 dryrun",
            "ciadmin migrate-superiorskyblock2 import", "ciadmin migrate-superiorskyblock2 verify",
            "ciadmin migrate-superiorskyblock2 rollback"
        );
    }
}
