package kr.lunaf.cloudislands.paper.message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class TranslationManager {
    private final String locale;
    private final String serviceName;
    private final Map<String, String> translations;
    private final Map<String, List<String>> lineTranslations;

    private TranslationManager(String locale, String serviceName, Map<String, String> translations, Map<String, List<String>> lineTranslations) {
        this.locale = locale == null || locale.isBlank() ? "ko_kr" : locale.toLowerCase(Locale.ROOT);
        this.serviceName = serviceName == null || serviceName.isBlank() ? "CloudIslands" : serviceName;
        this.translations = Map.copyOf(translations);
        this.lineTranslations = copyLines(lineTranslations);
    }

    public static TranslationManager fromConfig(FileConfiguration config, String serviceName) {
        Map<String, String> values = defaults(serviceName);
        ConfigurationSection section = config.getConfigurationSection("messages.translations");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null) {
                    values.put(normalize(key), value);
                }
            }
        }
        Map<String, List<String>> lines = new HashMap<>();
        lines.put("scoreboard-lines", List.of("플레이어: {player}", "접속: {online}명", "섬 이동: /섬", "방문: /섬 방문", "관리: /섬 설정"));
        List<String> configuredScoreboard = config.getStringList("messages.scoreboard-lines");
        if (!configuredScoreboard.isEmpty()) {
            lines.put("scoreboard-lines", configuredScoreboard);
        }
        return new TranslationManager(config.getString("plugin.language", "ko_kr"), serviceName, values, lines);
    }

    public String text(String key, String... variables) {
        String template = translations.getOrDefault(normalize(key), "");
        return render(template, variables);
    }

    public List<String> lines(String key, String... variables) {
        List<String> templates = lineTranslations.getOrDefault(normalize(key), List.of());
        List<String> rendered = new ArrayList<>(templates.size());
        for (String template : templates) {
            rendered.add(render(template, variables));
        }
        return rendered;
    }

    public String locale() {
        return locale;
    }

    private String render(String template, String... variables) {
        String rendered = template == null ? "" : template;
        rendered = rendered.replace("{service}", serviceName).replace("{locale}", locale);
        for (int index = 0; index + 1 < variables.length; index += 2) {
            rendered = rendered.replace("{" + variables[index] + "}", variables[index + 1] == null ? "" : variables[index + 1]);
        }
        return rendered;
    }

    private static Map<String, String> defaults(String serviceName) {
        Map<String, String> values = new HashMap<>();
        String name = serviceName == null || serviceName.isBlank() ? "CloudIslands" : serviceName;
        values.put("tab-header", "{service}");
        values.put("tab-footer", "섬을 준비하고 있습니다. /섬 으로 이동과 관리를 시작하세요.");
        values.put("tab-player-name", "{player}");
        values.put("join-message", "{player}님이 섬 서비스에 접속했습니다.");
        values.put("quit-message", "{player}님이 섬 서비스에서 나갔습니다.");
        values.put("server-brand", "{service}");
        values.put("chat-prefix", "[" + name + "] ");
        values.put("chat-format", "{prefix}{player}: {message}");
        values.put("island-chat-format", "[섬] {actor}: {message}");
        values.put("team-chat-format", "[팀] {actor}: {message}");
        values.put("scoreboard-title", "{service}");
        values.put("route-loading-complete", "{target} 로딩 완료");
        values.put("route-loading-progress", "{target} 로딩 중 {progress}%");
        values.put("route-preparing-progress", "{target}을 준비하는 중입니다... {progress}%");
        values.put("route-ready", "잠시 후 {target}으로 이동합니다.");
        values.put("migration-notice-primary", "섬 서버를 최적화하는 중입니다...");
        values.put("migration-notice-secondary", "잠시 후 자동으로 이동됩니다.");
        values.put("migration-return-register-failed", "섬 이동 준비를 등록하지 못했습니다.");
        values.put("migration-return-start", "최적화된 섬 서버로 이동합니다.");
        values.put("migration-return-not-ready", "섬 서버 이동 준비가 완료되지 않았습니다. 잠시 후 /섬 홈을 사용해주세요.");
        values.put("island-restore-evacuate", "섬 복원을 위해 로비로 이동합니다.");
        values.put("island-reset-evacuate", "섬 리셋을 위해 로비로 이동합니다.");
        values.put("island-delete-evacuate", "섬 삭제를 위해 로비로 이동합니다.");
        values.put("island-operation-evacuate", "섬 작업을 위해 로비로 이동합니다.");
        values.put("boundary-member-return", "섬 경계 밖으로 이동할 수 없어 섬 스폰으로 돌려보냈습니다.");
        values.put("boundary-visitor-return", "섬 경계 밖으로 이동할 수 없어 방문자 위치로 돌려보냈습니다.");
        values.put("flag-fly-denied", "이 섬에서는 비행할 수 없습니다.");
        values.put("flag-pvp-denied", "이 섬에서는 PVP가 비활성화되어 있습니다.");
        values.put("route-visit-cancelled", "섬 방문이 취소되었습니다.");
        values.put("route-arrived-visit", "방문한 섬에 도착했습니다.");
        values.put("route-arrived-warp", "섬 워프에 도착했습니다.");
        values.put("route-arrived-admin", "관리자 이동이 완료되었습니다.");
        values.put("route-arrived-home", "내 섬에 도착했습니다.");
        values.put("route-consume-loading", "섬 로딩 중");
        values.put("route-consume-preparing", "섬을 준비하는 중입니다...");
        values.put("route-consume-failed", "섬 이동 준비가 완료되지 않았습니다. 다시 시도해주세요.");
        values.put("route-login-proxy-required", "정상적인 프록시 경로로 접속해주세요.");
        values.put("route-login-forwarding-not-ready", "섬 서버 보안 설정이 완료되지 않았습니다. 관리자에게 문의해주세요.");
        values.put("route-login-session-required", "정상적인 섬 입장 요청이 없습니다. /섬 홈으로 다시 이동해주세요.");
        values.put("route-session-check-failed", "섬 입장 준비를 확인하지 못했습니다.");
        values.put("route-session-preparing", "섬 입장을 준비하는 중입니다...");
        values.put("route-session-missing-fallback", "섬 입장 요청이 없어 로비로 이동합니다.");
        values.put("limit-reached", "섬 {limit} 제한에 도달했습니다. 현재 {current}/{max}");
        values.put("level-recalculate-denied", "섬 레벨을 계산할 권한이 없습니다.");
        values.put("level-recalculate-started", "섬 블록을 다시 확인하는 중입니다.");
        values.put("bank-deposit-denied", "섬 은행에 입금할 권한이 없습니다.");
        values.put("bank-withdraw-denied", "섬 은행에서 출금할 권한이 없습니다.");
        values.put("economy-unavailable", "경제 플러그인을 찾을 수 없습니다.");
        values.put("upgrade-purchase-denied", "섬 업그레이드를 구매할 권한이 없습니다.");
        values.put("biome-set-denied", "섬 바이옴을 변경할 권한이 없습니다.");
        values.put("limit-set-denied", "섬 제한을 변경할 권한이 없습니다.");
        values.put("snapshot-create-denied", "섬 스냅샷을 생성할 관리자 권한이 없습니다.");
        values.put("snapshot-restore-denied", "섬 스냅샷을 복원할 관리자 권한이 없습니다.");
        values.put("home-set-denied", "섬 홈을 설정할 권한이 없습니다.");
        values.put("home-teleport-denied", "섬 홈으로 이동할 권한이 없습니다.");
        values.put("warp-set-denied", "섬 워프를 설정할 권한이 없습니다.");
        values.put("warp-delete-denied", "섬 워프를 삭제할 권한이 없습니다.");
        values.put("warp-access-denied", "섬 워프 공개 상태를 변경할 권한이 없습니다.");
        values.put("access-change-denied", "섬 공개 상태를 변경할 권한이 없습니다.");
        values.put("lock-change-denied", "섬 잠금 상태를 변경할 권한이 없습니다.");
        values.put("member-invite-denied", "섬 멤버를 초대할 권한이 없습니다.");
        values.put("member-remove-denied", "섬 멤버를 추방할 권한이 없습니다.");
        values.put("member-role-denied", "섬 멤버 역할을 변경할 권한이 없습니다.");
        values.put("visitor-ban-denied", "섬 방문자를 밴할 권한이 없습니다.");
        values.put("visitor-pardon-denied", "섬 방문자 밴을 해제할 권한이 없습니다.");
        values.put("visitor-kick-denied", "섬 방문자를 추방할 권한이 없습니다.");
        values.put("warp-teleport-denied", "섬 워프로 이동할 권한이 없습니다.");
        values.put("flag-set-denied", "섬 플래그를 변경할 권한이 없습니다.");
        values.put("role-edit-denied", "섬 역할을 편집할 권한이 없습니다.");
        values.put("role-reset-denied", "섬 역할을 초기화할 권한이 없습니다.");
        values.put("permission-set-denied", "섬 권한을 변경할 권한이 없습니다.");
        values.put("name-change-denied", "섬 이름을 변경할 권한이 없습니다.");
        values.put("input-warp-name-required", "워프 이름을 입력해주세요.");
        values.put("input-deposit-amount-required", "입금할 금액을 입력해주세요.");
        values.put("input-withdraw-amount-required", "출금할 금액을 입력해주세요.");
        values.put("input-upgrade-key-required", "구매할 업그레이드 키를 입력해주세요.");
        values.put("input-limit-key-value-required", "제한 키와 값을 입력해주세요.");
        values.put("input-limit-value-required", "제한 값을 입력해주세요.");
        values.put("input-snapshot-number-required", "복원할 스냅샷 번호를 입력해주세요.");
        values.put("input-snapshot-number-invalid", "올바른 스냅샷 번호를 입력해주세요.");
        values.put("input-invite-player-required", "초대할 플레이어를 입력해주세요.");
        values.put("input-invite-accept-target-required", "수락할 초대 ID, 섬 ID/이름, 또는 초대한 플레이어를 입력해주세요.");
        values.put("input-invite-decline-target-required", "거절할 초대 ID, 섬 ID/이름, 또는 초대한 플레이어를 입력해주세요.");
        values.put("input-invite-id-invalid", "올바른 초대 ID를 입력해주세요.");
        values.put("input-remove-player-required", "추방할 플레이어를 입력해주세요.");
        values.put("input-trust-player-required", "신뢰할 플레이어를 입력해주세요.");
        values.put("input-untrust-player-required", "신뢰 해제할 플레이어를 입력해주세요.");
        values.put("input-promote-player-required", "승급할 플레이어를 입력해주세요.");
        values.put("input-demote-player-required", "강등할 플레이어를 입력해주세요.");
        values.put("input-member-role-required", "역할을 바꿀 플레이어와 역할을 입력해주세요.");
        values.put("input-member-role-invalid", "올바른 멤버 역할을 입력해주세요. 예: MEMBER, MODERATOR, CUSTOM_1");
        values.put("input-role-edit-required", "역할, 가중치, 표시 이름을 입력해주세요.");
        values.put("input-role-edit-invalid", "편집 가능한 멤버 역할을 입력해주세요. 예: CUSTOM_1");
        values.put("input-role-reset-required", "초기화할 역할을 입력해주세요.");
        values.put("input-role-reset-invalid", "초기화 가능한 멤버 역할을 입력해주세요. 예: CUSTOM_1");
        values.put("input-transfer-player-required", "양도할 플레이어를 입력해주세요.");
        values.put("input-ban-player-required", "밴할 플레이어를 입력해주세요.");
        values.put("input-pardon-player-required", "밴 해제할 플레이어를 입력해주세요.");
        values.put("input-kick-visitor-required", "추방할 방문자를 입력해주세요.");
        values.put("input-island-name-required", "새 섬 이름을 입력해주세요.");
        values.put("input-flag-value-required", "플래그와 값을 입력해주세요.");
        values.put("input-flag-invalid", "올바른 섬 플래그를 입력해주세요.");
        values.put("input-permission-set-required", "역할, 권한, 허용 여부를 입력해주세요.");
        values.put("input-permission-set-invalid", "올바른 역할과 권한을 입력해주세요.");
        values.put("player-only-command", "플레이어만 사용할 수 있습니다.");
        values.put("input-island-uuid-invalid", "섬 UUID가 올바르지 않습니다.");
        values.put("input-amount-invalid", "올바른 금액을 입력해주세요.");
        values.put("route-command-failed", "섬으로 이동하지 못했습니다.");
        values.put("route-command-publish-failed", "섬 이동 경로를 준비하지 못했습니다.");
        values.put("route-command-started", "섬으로 이동합니다.");
        values.put("route-target-world-missing", "대상 월드를 찾을 수 없습니다.");
        values.put("visitor-kick-target-offline", "방문자 추방을 기록했습니다. 대상 플레이어는 현재 온라인이 아닙니다.");
        values.put("visitor-kick-target-not-on-island", "방문자 추방을 기록했습니다. 대상 플레이어는 현재 이 섬에 없습니다.");
        values.put("chat-menu-island-usage", "사용법: /섬 채팅 <메시지>");
        values.put("chat-menu-team-usage", "사용법: /섬 팀채팅 <메시지>");
        values.put("bank-menu-load-failed", "섬 은행을 불러오지 못했습니다.");
        values.put("bank-menu-deposit-usage", "사용법: /섬 입금 <금액>");
        values.put("bank-menu-withdraw-usage", "사용법: /섬 출금 <금액>");
        values.put("bank-menu-current-balance", "현재 잔액: ");
        values.put("bank-menu-no-update", "업데이트 정보 없음");
        values.put("bank-menu-updated-at", "갱신 시각: ");
        values.put("home-menu-load-failed", "섬 홈을 불러오지 못했습니다.");
        values.put("home-menu-set-usage", "사용법: /섬 셋홈 <이름>");
        values.put("home-menu-empty", "현재 등록된 섬 홈이 없습니다.");
        values.put("home-menu-no-created-info", "생성 정보 없음");
        values.put("home-menu-created-at", "생성 시각: ");
        values.put("home-menu-left-click", "좌클릭: 이 홈으로 이동");
        values.put("home-menu-right-click", "우클릭: 현재 위치로 갱신");
        values.put("warp-menu-load-failed", "섬 워프를 불러오지 못했습니다.");
        values.put("warp-menu-public-load-failed", "공개 섬 워프를 불러오지 못했습니다.");
        values.put("warp-menu-set-usage", "사용법: /섬 워프설정 <이름>");
        values.put("warp-menu-location", "위치: ");
        values.put("warp-menu-public-left-click", "좌클릭: 공개 워프로 이동");
        values.put("warp-menu-public-label", "공개 워프");
        values.put("warp-menu-private-label", "비공개 워프");
        values.put("warp-menu-left-click", "좌클릭: 이동");
        values.put("warp-menu-toggle-click", "우클릭: 공개/비공개 전환");
        values.put("warp-menu-delete-click", "Shift+우클릭: 삭제");
        values.put("info-menu-load-failed", "섬 정보를 불러오지 못했습니다.");
        values.put("info-menu-island-name", "섬 이름: ");
        values.put("info-menu-no-name", "이름 없음");
        values.put("info-menu-state", "상태: ");
        values.put("info-menu-unknown", "알 수 없음");
        values.put("info-menu-yes", "예");
        values.put("info-menu-no", "아니오");
        values.put("info-menu-close", "메뉴를 닫습니다.");
        values.put("biome-menu-load-failed", "섬 바이옴을 불러오지 못했습니다.");
        values.put("biome-menu-not-set", "설정 없음");
        values.put("biome-menu-selected", "현재 적용됨");
        values.put("biome-menu-click-to-change", "클릭하면 이 바이옴으로 변경합니다.");
        values.put("ranking-menu-load-failed", "섬 랭킹을 불러오지 못했습니다.");
        values.put("ranking-menu-click-to-visit", "클릭하면 방문을 시도합니다.");
        values.put("my-islands-menu-load-failed", "내 섬 목록을 불러오지 못했습니다.");
        values.put("my-islands-menu-empty", "속한 섬이 없습니다.");
        values.put("my-islands-menu-click-to-visit", "클릭하면 이 섬으로 이동합니다.");
        values.put("visit-menu-load-failed", "공개 섬 목록을 불러오지 못했습니다.");
        values.put("visit-menu-empty", "방문 가능한 공개 섬이 없습니다.");
        values.put("visit-menu-random-description", "공개된 섬 중 하나로 이동합니다.");
        values.put("visit-menu-click-to-visit", "클릭하면 방문합니다.");
        values.put("limit-menu-load-failed", "섬 제한을 불러오지 못했습니다.");
        values.put("limit-menu-empty", "현재 설정된 섬 제한이 없습니다.");
        values.put("limit-menu-no-update", "업데이트 정보 없음");
        values.put("limit-menu-left-click", "좌클릭: +1");
        values.put("limit-menu-right-click", "우클릭: -1");
        values.put("limit-menu-shift-click", "Shift+클릭: 10 단위로 조정");
        values.put("mission-menu-load-failed", "섬 과제를 불러오지 못했습니다.");
        values.put("mission-menu-empty", "현재 표시할 섬 과제가 없습니다.");
        values.put("mission-menu-no-reward", "없음");
        values.put("mission-menu-completed", "완료됨");
        values.put("mission-menu-click-to-complete", "클릭하면 완료를 요청합니다.");
        values.put("upgrade-menu-load-failed", "섬 업그레이드를 불러오지 못했습니다.");
        values.put("upgrade-menu-empty", "Core API에 등록된 섬 업그레이드가 없습니다.");
        values.put("upgrade-menu-click-to-buy", "클릭하면 다음 레벨 구매를 요청합니다.");
        values.put("settings-menu-load-failed", "섬 설정을 불러오지 못했습니다.");
        values.put("settings-menu-current", "현재: ");
        values.put("settings-menu-public", "공개");
        values.put("settings-menu-private", "비공개");
        values.put("settings-menu-locked", "잠김");
        values.put("settings-menu-open", "열림");
        values.put("settings-menu-public-left-click", "좌클릭: /섬 공개");
        values.put("settings-menu-public-right-click", "우클릭: /섬 비공개");
        values.put("settings-menu-lock-left-click", "좌클릭: /섬 잠금해제");
        values.put("settings-menu-lock-right-click", "우클릭: /섬 잠금");
        values.put("flag-menu-load-failed", "섬 플래그를 불러오지 못했습니다.");
        values.put("flag-menu-default", "기본값");
        values.put("flag-menu-allow", "허용");
        values.put("flag-menu-deny", "거부");
        values.put("flag-menu-current-value", "현재 값: ");
        values.put("flag-menu-click-actions", "좌클릭: 허용, 우클릭: 거부");
        values.put("permission-menu-load-failed", "섬 권한을 불러오지 못했습니다.");
        values.put("permission-menu-default", "기본값");
        values.put("permission-menu-allow", "허용");
        values.put("permission-menu-deny", "차단");
        values.put("permission-menu-current-state", "현재 상태: ");
        values.put("permission-menu-click-actions", "좌클릭: 허용, 우클릭: 차단");
        values.put("member-menu-load-failed", "섬 멤버를 불러오지 못했습니다.");
        values.put("member-menu-invite-usage", "사용법: /섬 초대 <플레이어>");
        values.put("member-menu-transfer-direct", "소유권 이전은 명령어로 직접 확인해주세요: /섬 양도 ");
        values.put("member-menu-no-join-info", "가입 정보 없음");
        values.put("member-menu-joined-at", "가입 시각: ");
        values.put("member-menu-left-click", "좌클릭: 승급");
        values.put("member-menu-right-click", "우클릭: 강등");
        values.put("member-menu-shift-right-click", "Shift+우클릭: 추방");
        values.put("member-menu-transfer-line", "양도: /섬 양도 ");
        values.put("invite-menu-load-failed", "섬 초대 목록을 불러오지 못했습니다.");
        values.put("invite-menu-empty", "받은 섬 초대가 없습니다.");
        values.put("invite-menu-no-created-info", "생성 정보 없음");
        values.put("invite-menu-created-at", "생성 시각: ");
        values.put("invite-menu-no-expire-info", "만료 정보 없음");
        values.put("invite-menu-expires-at", "만료 시각: ");
        values.put("invite-menu-left-click", "좌클릭: 초대 수락");
        values.put("invite-menu-right-click", "우클릭: 초대 거절");
        values.put("ban-menu-load-failed", "섬 밴 목록을 불러오지 못했습니다.");
        values.put("ban-menu-empty", "현재 밴된 방문자가 없습니다.");
        values.put("ban-menu-detail-title", "방문자 밴 상세");
        values.put("ban-menu-reason", "사유: ");
        values.put("ban-menu-none", "없음");
        values.put("ban-menu-no-created-info", "생성 정보 없음");
        values.put("ban-menu-created-at", "생성 시각: ");
        values.put("ban-menu-no-expire", "만료 없음");
        values.put("ban-menu-expires-at", "만료 시각: ");
        values.put("ban-menu-left-click", "좌클릭: 상세 보기");
        values.put("ban-menu-right-click", "우클릭: 밴 해제");
        values.put("log-menu-load-failed", "섬 로그를 불러오지 못했습니다.");
        values.put("log-menu-detail-title", "섬 로그 상세");
        values.put("log-menu-empty", "아직 기록된 섬 로그가 없습니다.");
        values.put("log-menu-time", "시간: ");
        values.put("log-menu-actor", "처리자: ");
        values.put("log-menu-unknown", "unknown");
        values.put("log-menu-payload-empty", "payload: 없음");
        values.put("role-menu-load-failed", "섬 역할을 불러오지 못했습니다.");
        values.put("role-menu-edit-prefix", "역할 편집: /섬 역할편집 ");
        values.put("role-menu-edit-suffix", " <weight> <displayName>");
        values.put("role-menu-empty-example", "/섬 역할편집 CUSTOM_1 5 부관리자");
        values.put("role-menu-click-edit", "클릭: 편집 명령어 안내");
        values.put("danger-confirm-required", "Shift+우클릭해야 실행됩니다.");
        values.put("danger-confirm-click-required", "위험 작업은 Shift+우클릭해야 실행됩니다.");
        values.put("snapshot-menu-load-failed", "섬 스냅샷을 불러오지 못했습니다.");
        values.put("snapshot-restore-confirm-required", "스냅샷 복원은 Shift+우클릭해야 실행됩니다.");
        values.put("snapshot-menu-detail-title", "스냅샷 상세");
        values.put("snapshot-menu-empty", "아직 생성된 섬 스냅샷이 없습니다.");
        values.put("snapshot-menu-reason", "사유: ");
        values.put("snapshot-menu-none", "없음");
        values.put("snapshot-menu-size", "크기: ");
        values.put("snapshot-menu-no-created-info", "생성 정보 없음");
        values.put("snapshot-menu-created-at", "생성 시각: ");
        values.put("snapshot-menu-left-click", "좌클릭: 상세 보기");
        values.put("snapshot-menu-shift-right-click", "Shift+우클릭: 이 스냅샷 복원 요청");
        return values;
    }

    private static String normalize(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static Map<String, List<String>> copyLines(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }
}
