package kr.lunaf.cloudislands.velocity.message;

import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.arrayValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.boolValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.doubleValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.jsonValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.longValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.matchingObjectEnd;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.objectValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import kr.lunaf.cloudislands.coreclient.ChatActionView;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.EnvironmentActionView;
import kr.lunaf.cloudislands.coreclient.LevelView;
import kr.lunaf.cloudislands.coreclient.ProgressionMissionCompletionView;
import kr.lunaf.cloudislands.coreclient.ProgressionUpgradePurchaseView;
import kr.lunaf.cloudislands.coreclient.UpgradeRuleView;

public final class VelocityIslandMessageFormatter {
    private final VelocityRoutePrivacyFormatter routePrivacy;

    public VelocityIslandMessageFormatter() {
        this(new VelocityRoutePrivacyFormatter(true));
    }

    public VelocityIslandMessageFormatter(VelocityRoutePrivacyFormatter routePrivacy) {
        this.routePrivacy = routePrivacy == null ? new VelocityRoutePrivacyFormatter(true) : routePrivacy;
    }

    public String playerIslands(String body) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(body, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String islandId = jsonValue(object, "islandId");
            if (!islandId.isBlank()) {
                String name = jsonValue(object, "name");
                String role = jsonValue(object, "role");
                long level = longValue(object, "level");
                entries.add((name.isBlank() ? "이름 없는 섬" : name)
                    + " (ID=" + shortId(islandId)
                    + ", 역할=" + (role.isBlank() ? "MEMBER" : role)
                    + ", 레벨=" + level + ")");
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "속한 섬이 없습니다." : "내 섬 목록: " + String.join(" / ", entries);
    }

    public String publicIslands(String body) {
        if (body == null || body.isBlank()) {
            return "공개 섬이 없습니다.";
        }
        String islands = arrayValue(body, "islands");
        if (islands.isBlank()) {
            return "공개 섬이 없습니다.";
        }
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (index < islands.length() && entries.size() < 20) {
            int objectStart = islands.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(islands, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = islands.substring(objectStart, objectEnd + 1);
            String islandId = jsonValue(object, "islandId");
            if (!islandId.isBlank()) {
                String name = jsonValue(object, "name");
                long level = longValue(object, "level");
                String worth = jsonValue(object, "worth");
                entries.add((entries.size() + 1) + ". "
                    + (name.isBlank() ? "이름 없는 섬" : name)
                    + " (ID=" + shortId(islandId)
                    + ", 레벨=" + level
                    + ", 가치=" + (worth.isBlank() ? "0" : worth) + ")");
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "공개 섬이 없습니다." : "공개 섬: " + String.join(" | ", entries);
    }

    public String invites(String body) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(body, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String inviteId = jsonValue(object, "inviteId");
            if (!inviteId.isBlank()) {
                String islandId = jsonValue(object, "islandId");
                String inviterUuid = jsonValue(object, "inviterUuid");
                entries.add(shortId(inviteId)
                    + (islandId.isBlank() ? "" : " 섬=" + shortId(islandId))
                    + (inviterUuid.isBlank() ? "" : " 초대한사람=" + shortId(inviterUuid)));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "대기 중인 섬 초대가 없습니다." : "섬 초대: " + String.join(", ", entries);
    }

    public String actionResult(String label, String targetId, String body) {
        if (body == null || body.isBlank()) {
            return label + ": accepted target=" + compactTarget(targetId);
        }
        String code = jsonValue(body, "code");
        boolean accepted = body.contains("\"accepted\"") ? boolValue(body, "accepted") : !body.contains("\"accepted\":false");
        StringBuilder builder = new StringBuilder(label)
            .append(": ")
            .append(accepted ? "accepted" : "rejected")
            .append(" target=")
            .append(compactTarget(targetId));
        if (!code.isBlank()) {
            builder.append(" code=").append(code);
            String detail = adminCodeDetail(code);
            if (!detail.isBlank()) {
                builder.append(" detail=").append(detail);
            }
        }
        String islandId = jsonValue(body, "islandId");
        if (!islandId.isBlank() && !islandId.equals(targetId)) {
            builder.append(" 섬=").append(shortId(islandId));
        }
        String materialKey = jsonValue(body, "materialKey");
        if (!materialKey.isBlank()) {
            builder.append(" material=").append(materialKey);
        }
        String worth = jsonValue(body, "worth");
        if (!worth.isBlank()) {
            builder.append(" worth=").append(worth);
        }
        if (body.contains("\"snapshotNo\"")) {
            builder.append(" snapshot=").append(longValue(body, "snapshotNo"));
        }
        String storagePath = jsonValue(body, "storagePath");
        if (!storagePath.isBlank()) {
            builder.append(" storagePath=").append(storagePath);
        }
        if (body.contains("\"restoreManifestRequired\"")) {
            builder.append(" restoreManifest=").append(boolValue(body, "restoreManifestRequired"));
        }
        String restoreChecksumPolicy = jsonValue(body, "restoreChecksumPolicy");
        if (!restoreChecksumPolicy.isBlank()) {
            builder.append(" restoreChecksum=").append(restoreChecksumPolicy);
        }
        if (body.contains("\"restorePortableRequired\"")) {
            builder.append(" restorePortable=").append(boolValue(body, "restorePortableRequired"));
        }
        String restoreSupportedFormats = jsonValue(body, "restoreSupportedFormats");
        if (!restoreSupportedFormats.isBlank()) {
            builder.append(" restoreFormats=").append(restoreSupportedFormats);
        }
        return builder.toString();
    }

    public String inviteCreate(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "초대: 실패 사유=" + code;
        }
        return "초대: 생성됨 invite=" + shortId(jsonValue(body, "inviteId"))
            + " 섬=" + shortId(jsonValue(body, "islandId"))
            + " target=" + shortId(jsonValue(body, "targetUuid"))
            + " state=" + jsonValue(body, "state");
    }

    public String chatResult(String label, String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return label + ": 실패 사유=" + code;
        }
        return label + ": 전송 완료 채널=" + jsonValue(body, "channel");
    }

    public String chatResult(String label, ChatActionView view) {
        if (!view.accepted()) {
            return label + ": 실패 사유=" + (view.code().isBlank() ? "FAILED" : view.code());
        }
        return label + ": 전송 완료 채널=" + view.channel();
    }

    public String islandInfo(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "섬 정보: 실패 사유=" + code;
        }
        String islandId = jsonValue(body, "islandId");
        String ownerUuid = jsonValue(body, "ownerUuid");
        String name = jsonValue(body, "name");
        String state = jsonValue(body, "state");
        return "섬 정보: ID=" + shortId(islandId)
            + " 소유자=" + shortId(ownerUuid)
            + (name.isBlank() ? "" : " 이름=" + name)
            + " 상태=" + (state.isBlank() ? "UNKNOWN" : state)
            + " 크기=" + longValue(body, "size")
            + " 레벨=" + longValue(body, "level")
            + " 가치=" + jsonValue(body, "worth")
            + " 공개=" + boolValue(body, "publicAccess");
    }

    public String islandStat(String label, String field, String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return label + ": 실패 사유=" + code;
        }
        String islandId = jsonValue(body, "islandId");
        String value = field.equals("worth") ? jsonValue(body, field) : Long.toString(longValue(body, field));
        return label + ": 섬=" + shortId(islandId) + " 값=" + value;
    }

    public String biomeInfo(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "섬 바이옴: 실패 사유=" + code;
        }
        return "섬 바이옴: 섬=" + shortId(jsonValue(body, "islandId"))
            + " 바이옴=" + jsonValue(body, "biomeKey")
            + " 변경자=" + shortId(jsonValue(body, "updatedBy"));
    }

    public String runtimeInfo(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "Island runtime: failed code=" + code;
        }
        String islandId = jsonValue(body, "islandId");
        String state = jsonValue(body, "state");
        String activeNode = jsonValue(body, "activeNode");
        String activeWorld = jsonValue(body, "activeWorld");
        return "Island runtime: 섬=" + shortId(islandId)
            + " state=" + (state.isBlank() ? "UNKNOWN" : state)
            + routePrivacy.routeNodeSuffix(activeNode)
            + routePrivacy.runtimeWorldSuffix(activeWorld)
            + routePrivacy.runtimeCellSuffix(body)
            + " fence=" + longValue(body, "fencingToken");
    }

    public String playerInfo(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "플레이어 정보: 실패 사유=" + code;
        }
        String playerUuid = jsonValue(body, "playerUuid");
        String lastName = jsonValue(body, "lastName");
        String islandId = jsonValue(body, "primaryIslandId");
        return "플레이어 정보: ID=" + shortId(playerUuid)
            + (lastName.isBlank() ? "" : " 이름=" + lastName)
            + (islandId.isBlank() ? " 섬=없음" : " 섬=" + shortId(islandId));
    }

    public String rankingList(String label, String body) {
        String rankings = arrayValue(body, "rankings");
        if (rankings.isBlank()) {
            return label + ": 기록이 없습니다.";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        int index = 0;
        while (index < rankings.length()) {
            int objectStart = rankings.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(rankings, objectStart);
            if (objectEnd < 0) {
                break;
            }
            total++;
            if (entries.size() < 10) {
                String object = rankings.substring(objectStart, objectEnd + 1);
                String islandId = jsonValue(object, "islandId");
                String name = jsonValue(object, "name");
                entries.add("#" + total
                    + " " + (name.isBlank() ? "이름 없는 섬" : name)
                    + " (ID=" + shortId(islandId)
                    + ", 레벨=" + longValue(object, "level")
                    + ", 가치=" + jsonValue(object, "worth") + ")");
            }
            index = objectEnd + 1;
        }
        return label + ": 전체 " + total + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String blockValueList(String body) {
        String values = arrayValue(body, "values");
        if (values.isBlank()) {
            return "Block values: empty";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        int index = 0;
        while (index < values.length()) {
            int objectStart = values.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(values, objectStart);
            if (objectEnd < 0) {
                break;
            }
            total++;
            if (entries.size() < 10) {
                String object = values.substring(objectStart, objectEnd + 1);
                entries.add(jsonValue(object, "materialKey")
                    + " worth=" + jsonValue(object, "worth")
                    + " level=" + longValue(object, "levelPoints")
                    + " limit=" + longValue(object, "limit"));
            }
            index = objectEnd + 1;
        }
        return "Block values: total=" + total + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String addonStateSummary(String body) {
        String addons = arrayValue(body, "addons");
        if (addons.isBlank()) {
            return "Addon state: empty";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        int index = 0;
        while (index < addons.length()) {
            int objectStart = addons.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(addons, objectStart);
            if (objectEnd < 0) {
                break;
            }
            total++;
            if (entries.size() < 10) {
                String object = addons.substring(objectStart, objectEnd + 1);
                entries.add(jsonValue(object, "addonId")
                    + " global=" + longValue(object, "globalKeys")
                    + " island=" + longValue(object, "islandKeys")
                    + " totalKeys=" + longValue(object, "totalKeys"));
            }
            index = objectEnd + 1;
        }
        return "Addon state: total=" + total
            + " owner=" + jsonValue(body, "stateOwnership")
            + " registeredRequired=" + boolValue(body, "registeredAddonRequired")
            + " orphanPolicy=" + jsonValue(body, "orphanStatePolicy")
            + " missingPolicy=" + jsonValue(body, "missingAddonStatePolicy")
            + " tableKeyPrefix=" + jsonValue(body, "tableKeyPrefix")
            + " maxKeysPerAddon=" + longValue(body, "maxKeysPerAddon")
            + " maxValueLength=" + longValue(body, "maxValueLength")
            + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String templateList(String body) {
        String templates = arrayValue(body, "templates");
        if (templates.isBlank()) {
            return "섬 템플릿: 없음";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        int enabled = 0;
        int index = 0;
        while (index < templates.length()) {
            int objectStart = templates.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(templates, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = templates.substring(objectStart, objectEnd + 1);
            total++;
            if (boolValue(object, "enabled")) {
                enabled++;
            }
            if (entries.size() < 10) {
                String minNodeVersion = jsonValue(object, "minNodeVersion");
                entries.add(jsonValue(object, "id")
                    + " " + (boolValue(object, "enabled") ? "사용 가능" : "비활성")
                    + (minNodeVersion.isBlank() ? "" : " 최소버전=" + minNodeVersion));
            }
            index = objectEnd + 1;
        }
        return "섬 템플릿: 전체 " + total + "개, 사용 가능 " + enabled + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String warpList(String label, String body) {
        return namedObjectList(label, body, "warps", object -> jsonValue(object, "name")
            + (boolValue(object, "publicAccess") ? "(공개)" : "")
            + " 섬=" + shortId(jsonValue(object, "islandId"))
            + " 위치=" + seconds(doubleValue(object, "localX")) + "," + seconds(doubleValue(object, "localY")) + "," + seconds(doubleValue(object, "localZ")));
    }

    public String homeList(String body) {
        return namedObjectList("섬 홈", body, "homes", object -> jsonValue(object, "name")
            + " 위치=" + seconds(doubleValue(object, "localX")) + "," + seconds(doubleValue(object, "localY")) + "," + seconds(doubleValue(object, "localZ")));
    }

    public String memberList(String body) {
        return namedObjectList("섬 멤버", body, "members", object -> shortId(jsonValue(object, "playerUuid"))
            + " 역할=" + jsonValue(object, "role"));
    }

    public String banList(String body) {
        return namedObjectList("섬 밴", body, "bans", object -> shortId(jsonValue(object, "bannedUuid"))
            + " 사유=" + fallback(jsonValue(object, "reason"), "-"));
    }

    public String permissionList(String body) {
        return namedObjectList("섬 권한", body, "rules", object -> jsonValue(object, "role")
            + ":" + jsonValue(object, "permission")
            + "=" + (boolValue(object, "allowed") ? "허용" : "거부"));
    }

    public String roleList(String body) {
        return namedObjectList("섬 역할", body, "roles", object -> jsonValue(object, "role")
            + " weight=" + longValue(object, "weight")
            + " name=" + fallback(jsonValue(object, "displayName"), "-"));
    }

    public String islandLogList(String body) {
        return namedObjectList("섬 로그", body, "logs", object -> fallback(jsonValue(object, "action"), "UNKNOWN")
            + " 처리자=" + shortId(jsonValue(object, "actorUuid"))
            + " 시각=" + jsonValue(object, "createdAt"));
    }

    public String bankInfo(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "섬 은행: 실패 사유=" + code;
        }
        return "섬 은행: 섬=" + shortId(jsonValue(body, "islandId"))
            + " balance=" + jsonValue(body, "balance");
    }

    public String levelRecalculation(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "레벨 계산: 실패 사유=" + code;
        }
        return "레벨 계산: 섬=" + shortId(jsonValue(body, "islandId"))
            + " level=" + longValue(body, "level")
            + " worth=" + jsonValue(body, "worth");
    }

    public String levelRecalculation(LevelView view) {
        return "레벨 계산: 섬=" + shortId(view.islandId())
            + " level=" + view.level()
            + " worth=" + view.worth();
    }

    public String upgradeList(String body) {
        return namedObjectList("섬 업그레이드", body, "upgrades", object -> jsonValue(object, "upgradeKey")
            + " 레벨=" + longValue(object, "level")
            + " 유형=" + jsonValue(object, "type"));
    }

    public String upgradeList(List<CoreGuiViews.UpgradeView> upgrades) {
        if (upgrades == null || upgrades.isEmpty()) {
            return "섬 업그레이드가 없습니다.";
        }
        return "섬 업그레이드: " + upgrades.stream()
            .map(upgrade -> upgrade.key() + " 레벨=" + upgrade.level() + " 유형=" + upgrade.type())
            .reduce((left, right) -> left + ", " + right)
            .orElse("섬 업그레이드가 없습니다.");
    }

    public String generatorInfo(String body) {
        String generatorKey = "default";
        long level = 1L;
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(body, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String upgradeKey = jsonValue(object, "upgradeKey");
            String normalized = upgradeKey.toLowerCase(Locale.ROOT);
            if (normalized.equals("generator") || normalized.startsWith("generator:")) {
                long currentLevel = Math.max(1L, longValue(object, "level"));
                String currentKey = jsonValue(object, "generatorKey");
                if (currentKey.isBlank()) {
                    int separator = upgradeKey.indexOf(':');
                    currentKey = separator < 0 ? "default" : upgradeKey.substring(separator + 1);
                }
                if (currentLevel > level || (currentLevel == level && generatorKey.equals("default") && !currentKey.equalsIgnoreCase("default"))) {
                    level = currentLevel;
                    generatorKey = currentKey.isBlank() ? "default" : currentKey;
                }
            }
            index = objectEnd + 1;
        }
        return "섬 생성기: key=" + generatorKey + " level=" + level + " / 업그레이드: /섬 업그레이드구매 generator";
    }

    public String generatorInfo(List<CoreGuiViews.UpgradeView> upgrades) {
        String generatorKey = "default";
        long level = 1L;
        if (upgrades != null) {
            for (CoreGuiViews.UpgradeView upgrade : upgrades) {
                String upgradeKey = upgrade.key();
                String normalized = upgradeKey.toLowerCase(Locale.ROOT);
                if (normalized.equals("generator") || normalized.startsWith("generator:")) {
                    long currentLevel = Math.max(1L, upgrade.level());
                    String currentKey = generatorKey(upgradeKey);
                    if (currentLevel > level || (currentLevel == level && generatorKey.equals("default") && !currentKey.equalsIgnoreCase("default"))) {
                        level = currentLevel;
                        generatorKey = currentKey.isBlank() ? "default" : currentKey;
                    }
                }
            }
        }
        return "섬 생성기: key=" + generatorKey + " level=" + level + " / 업그레이드: /섬 업그레이드구매 generator";
    }

    public String upgradePurchase(String body) {
        String code = jsonValue(body, "code");
        String upgrade = objectValue(body, "upgrade");
        boolean accepted = boolValue(body, "accepted");
        return "업그레이드 구매: " + (accepted ? "접수됨" : "거부됨")
            + (code.isBlank() ? "" : " 사유=" + code)
            + " 비용=" + jsonValue(body, "cost")
            + (upgrade.isBlank() ? "" : " 업그레이드=" + jsonValue(upgrade, "upgradeKey") + " 레벨=" + longValue(upgrade, "level"));
    }

    public String upgradePurchase(ProgressionUpgradePurchaseView view) {
        return "업그레이드 구매: " + (view.accepted() ? "접수됨" : "거부됨")
            + (view.code().isBlank() ? "" : " 사유=" + view.code())
            + " 비용=" + view.cost()
            + (view.upgradeKey().isBlank() ? "" : " 업그레이드=" + view.upgradeKey() + " 레벨=" + view.level());
    }

    public String missionList(String label, String body) {
        return namedObjectList(label, body, "missions", object -> jsonValue(object, "missionKey")
            + " " + longValue(object, "progress") + "/" + longValue(object, "goal")
            + " 완료=" + boolValue(object, "completed"));
    }

    public String missionList(String label, List<CoreGuiViews.MissionView> missions) {
        if (missions == null || missions.isEmpty()) {
            return label + "이 없습니다.";
        }
        return label + ": " + missions.stream()
            .map(mission -> mission.key() + " " + mission.progress() + "/" + mission.goal() + " 완료=" + mission.completed())
            .reduce((left, right) -> left + ", " + right)
            .orElse(label + "이 없습니다.");
    }

    public String missionResult(String label, String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return label + ": 실패 사유=" + code;
        }
        return label + ": 완료 키=" + jsonValue(body, "missionKey")
            + " 보상=" + jsonValue(body, "reward");
    }

    public String missionResult(String label, ProgressionMissionCompletionView view) {
        if (!view.code().isBlank()) {
            return label + ": 실패 사유=" + view.code();
        }
        return label + ": 완료 키=" + view.missionKey()
            + " 보상=" + view.reward();
    }

    public String limitList(String body) {
        return namedObjectList("섬 제한", body, "limits", object -> jsonValue(object, "limitKey")
            + " 값=" + longValue(object, "value"));
    }

    public String limitList(List<CoreGuiViews.LimitView> limits) {
        if (limits == null || limits.isEmpty()) {
            return "섬 제한이 없습니다.";
        }
        return "섬 제한: " + limits.stream()
            .map(limit -> limit.key() + " 값=" + limit.value())
            .reduce((left, right) -> left + ", " + right)
            .orElse("섬 제한이 없습니다.");
    }

    public String limitResult(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "섬 제한 변경: 실패 사유=" + code;
        }
        return "섬 제한 변경: " + jsonValue(body, "limitKey")
            + "=" + longValue(body, "value")
            + " 섬=" + shortId(jsonValue(body, "islandId"));
    }

    public String limitResult(EnvironmentActionView view) {
        if (!view.accepted()) {
            return "섬 제한 변경: 실패 사유=" + (view.code().isBlank() ? "FAILED" : view.code());
        }
        return "섬 제한 변경: " + view.key()
            + "=" + view.value()
            + " 섬=" + shortId(view.islandId());
    }

    public String flagList(String body) {
        String flags = objectValue(body, "flags");
        if (flags.isBlank()) {
            return "섬 플래그: 없음";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        int index = 0;
        while (index < flags.length()) {
            int keyStart = flags.indexOf('"', index);
            if (keyStart < 0) {
                break;
            }
            int keyEnd = flags.indexOf('"', keyStart + 1);
            if (keyEnd < 0) {
                break;
            }
            int valueStart = flags.indexOf('"', keyEnd + 1);
            if (valueStart < 0) {
                break;
            }
            int valueEnd = flags.indexOf('"', valueStart + 1);
            if (valueEnd < 0) {
                break;
            }
            total++;
            if (entries.size() < 12) {
                entries.add(flags.substring(keyStart + 1, keyEnd) + "=" + flags.substring(valueStart + 1, valueEnd));
            }
            index = valueEnd + 1;
        }
        return "섬 플래그: 전체 " + total + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String upgradeRules(String body) {
        String rules = arrayValue(body, "rules");
        if (rules.isBlank()) {
            return "업그레이드 규칙: 없음";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        int index = 0;
        while (index < rules.length()) {
            int objectStart = rules.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(rules, objectStart);
            if (objectEnd < 0) {
                break;
            }
            total++;
            if (entries.size() < 12) {
                String object = rules.substring(objectStart, objectEnd + 1);
                entries.add(jsonValue(object, "upgradeKey")
                    + " 유형=" + jsonValue(object, "type")
                    + " 최대=" + longValue(object, "maxLevel")
                    + " 기본비용=" + jsonValue(object, "baseCost"));
            }
            index = objectEnd + 1;
        }
        return "업그레이드 규칙: 전체 " + total + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String upgradeRules(List<UpgradeRuleView> rules) {
        if (rules == null || rules.isEmpty()) {
            return "업그레이드 규칙: 없음";
        }
        List<String> entries = new ArrayList<>();
        for (UpgradeRuleView rule : rules) {
            if (entries.size() >= 12) {
                break;
            }
            entries.add(rule.key()
                + " 유형=" + rule.type()
                + " 최대=" + rule.maxLevel()
                + " 기본비용=" + rule.baseCost());
        }
        return "업그레이드 규칙: 전체 " + rules.size() + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String namedObjectList(String label, String body, String arrayField, Function<String, String> formatter) {
        String array = arrayValue(body, arrayField);
        if (array.isBlank()) {
            return label + ": empty";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        int index = 0;
        while (index < array.length()) {
            int objectStart = array.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(array, objectStart);
            if (objectEnd < 0) {
                break;
            }
            total++;
            if (entries.size() < 10) {
                entries.add(formatter.apply(array.substring(objectStart, objectEnd + 1)));
            }
            index = objectEnd + 1;
        }
        return label + ": 전체 " + total + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String generatorKey(String upgradeKey) {
        if (upgradeKey == null || upgradeKey.isBlank()) {
            return "default";
        }
        int separator = upgradeKey.indexOf(':');
        return separator < 0 ? "default" : upgradeKey.substring(separator + 1);
    }

    private String adminCodeDetail(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        if (code.startsWith("NO_READY_NODE")) {
            return "no-ready-node";
        }
        if (code.startsWith("TARGET_NODE")) {
            return "target-node-blocked";
        }
        if (code.startsWith("ACTIVE_NODE")) {
            return "active-node-blocked";
        }
        return switch (code) {
            case "ACTIVATION_LOCKED" -> "activation-in-progress";
            case "VISITOR_SOFT_FULL" -> "visitor-denied-soft-full";
            case "CREATE_LOCKED" -> "player-create-lock-held";
            case "NODE_UNAVAILABLE" -> "node-unavailable";
            default -> "";
        };
    }

    private String compactTarget(String targetId) {
        return targetId != null && targetId.length() == 36 && targetId.indexOf('-') > 0 ? shortId(targetId) : targetId;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String seconds(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() > 8 ? value.substring(0, 8) : value;
    }
}
