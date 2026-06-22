package kr.lunaf.cloudislands.velocity.message;

import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.boolValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.doubleValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.hasField;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.jsonValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.longValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.objectValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.objects;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.objectsFromArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.coreclient.AdminAddonStateSummaryView;
import kr.lunaf.cloudislands.coreclient.AdminIslandRuntimeView;
import kr.lunaf.cloudislands.coreclient.BlockValueActionView;
import kr.lunaf.cloudislands.coreclient.BlockValueView;
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
import kr.lunaf.cloudislands.coreclient.PlayerProfileView;
import kr.lunaf.cloudislands.coreclient.ProgressionMissionCompletionView;
import kr.lunaf.cloudislands.coreclient.ProgressionRankingEntryView;
import kr.lunaf.cloudislands.coreclient.ProgressionUpgradePurchaseView;
import kr.lunaf.cloudislands.coreclient.SettingsActionView;
import kr.lunaf.cloudislands.coreclient.TemplateView;
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
        for (String object : objectsFromArray(body)) {
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
        }
        return entries.isEmpty() ? "속한 섬이 없습니다." : "내 섬 목록: " + String.join(" / ", entries);
    }

    public String playerIslands(List<CoreGuiViews.PlayerIslandView> islands) {
        if (islands == null || islands.isEmpty()) {
            return "속한 섬이 없습니다.";
        }
        List<String> entries = new ArrayList<>();
        for (CoreGuiViews.PlayerIslandView island : islands) {
            if (!island.islandId().isBlank()) {
                entries.add((island.name().isBlank() ? "이름 없는 섬" : island.name())
                    + " (ID=" + shortId(island.islandId())
                    + ", 역할=" + (island.role().isBlank() ? "MEMBER" : island.role())
                    + ", 레벨=" + island.level() + ")");
            }
        }
        return entries.isEmpty() ? "속한 섬이 없습니다." : "내 섬 목록: " + String.join(" / ", entries);
    }

    public String publicIslands(String body) {
        if (body == null || body.isBlank()) {
            return "공개 섬이 없습니다.";
        }
        List<String> islands = objects(body, "islands");
        if (islands.isEmpty()) {
            return "공개 섬이 없습니다.";
        }
        List<String> entries = new ArrayList<>();
        for (String object : islands) {
            if (entries.size() >= 20) {
                break;
            }
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
        }
        return entries.isEmpty() ? "공개 섬이 없습니다." : "공개 섬: " + String.join(" | ", entries);
    }

    public String publicIslands(List<CoreGuiViews.PublicIslandView> islands) {
        if (islands == null || islands.isEmpty()) {
            return "공개 섬이 없습니다.";
        }
        List<String> entries = new ArrayList<>();
        for (CoreGuiViews.PublicIslandView island : islands) {
            if (entries.size() >= 20) {
                break;
            }
            if (!island.islandId().isBlank()) {
                entries.add((entries.size() + 1) + ". "
                    + (island.name().isBlank() ? "이름 없는 섬" : island.name())
                    + " (ID=" + shortId(island.islandId())
                    + ", 레벨=" + island.level()
                    + ", 가치=" + fallback(island.worth(), "0") + ")");
            }
        }
        return entries.isEmpty() ? "공개 섬이 없습니다." : "공개 섬: " + String.join(" | ", entries);
    }

    public String invites(String body) {
        List<String> entries = new ArrayList<>();
        for (String object : objectsFromArray(body)) {
            String inviteId = jsonValue(object, "inviteId");
            if (!inviteId.isBlank()) {
                String islandId = jsonValue(object, "islandId");
                String inviterUuid = jsonValue(object, "inviterUuid");
                entries.add(shortId(inviteId)
                    + (islandId.isBlank() ? "" : " 섬=" + shortId(islandId))
                    + (inviterUuid.isBlank() ? "" : " 초대한사람=" + shortId(inviterUuid)));
            }
        }
        return entries.isEmpty() ? "대기 중인 섬 초대가 없습니다." : "섬 초대: " + String.join(", ", entries);
    }

    public String invites(List<CoreGuiViews.InviteView> invites) {
        if (invites == null || invites.isEmpty()) {
            return "대기 중인 섬 초대가 없습니다.";
        }
        List<String> entries = new ArrayList<>();
        for (CoreGuiViews.InviteView invite : invites) {
            if (!invite.inviteId().isBlank()) {
                entries.add(shortId(invite.inviteId())
                    + (invite.islandId().isBlank() ? "" : " 섬=" + shortId(invite.islandId()))
                    + (invite.inviterUuid().isBlank() ? "" : " 초대한사람=" + shortId(invite.inviterUuid())));
            }
        }
        return entries.isEmpty() ? "대기 중인 섬 초대가 없습니다." : "섬 초대: " + String.join(", ", entries);
    }

    public String actionResult(String label, String targetId, String body) {
        if (body == null || body.isBlank()) {
            return label + ": accepted target=" + compactTarget(targetId);
        }
        String code = jsonValue(body, "code");
        boolean accepted = !hasField(body, "accepted") || boolValue(body, "accepted");
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
        if (hasField(body, "snapshotNo")) {
            builder.append(" snapshot=").append(longValue(body, "snapshotNo"));
        }
        String storagePath = jsonValue(body, "storagePath");
        if (!storagePath.isBlank()) {
            builder.append(" storagePath=").append(storagePath);
        }
        if (hasField(body, "restoreManifestRequired")) {
            builder.append(" restoreManifest=").append(boolValue(body, "restoreManifestRequired"));
        }
        String restoreChecksumPolicy = jsonValue(body, "restoreChecksumPolicy");
        if (!restoreChecksumPolicy.isBlank()) {
            builder.append(" restoreChecksum=").append(restoreChecksumPolicy);
        }
        if (hasField(body, "restorePortableRequired")) {
            builder.append(" restorePortable=").append(boolValue(body, "restorePortableRequired"));
        }
        String restoreSupportedFormats = jsonValue(body, "restoreSupportedFormats");
        if (!restoreSupportedFormats.isBlank()) {
            builder.append(" restoreFormats=").append(restoreSupportedFormats);
        }
        return builder.toString();
    }

    public String actionResult(String label, String targetId, IslandLifecycleActionView view) {
        if (view == null) {
            return label + ": accepted target=" + compactTarget(targetId);
        }
        StringBuilder builder = new StringBuilder(label)
            .append(": ")
            .append(view.accepted() ? "accepted" : "rejected")
            .append(" target=")
            .append(compactTarget(targetId));
        if (!view.code().isBlank()) {
            builder.append(" code=").append(view.code());
            String detail = adminCodeDetail(view.code());
            if (!detail.isBlank()) {
                builder.append(" detail=").append(detail);
            }
        }
        if (!view.islandId().isBlank() && !view.islandId().equals(targetId)) {
            builder.append(" 섬=").append(shortId(view.islandId()));
        }
        if (view.snapshotNo() > 0L) {
            builder.append(" snapshot=").append(view.snapshotNo());
        }
        if (!view.storagePath().isBlank()) {
            builder.append(" storagePath=").append(view.storagePath());
        }
        return builder.toString();
    }

    public String actionResult(String label, String targetId, BlockValueActionView view) {
        if (view == null) {
            return label + ": accepted target=" + compactTarget(targetId);
        }
        String material = view.materialKey().isBlank() ? targetId : view.materialKey();
        return label + ": " + (view.accepted() ? "accepted" : "rejected")
            + " target=" + compactTarget(targetId)
            + (!view.accepted() && !view.code().isBlank() ? " code=" + view.code() : "")
            + (material == null || material.isBlank() ? "" : " material=" + material);
    }

    public String playerAction(String label, String playerUuid, PlayerProfileView view) {
        if (view == null) {
            return label + ": accepted target=" + compactTarget(playerUuid);
        }
        return label + ": accepted target=" + compactTarget(view.playerUuid().isBlank() ? playerUuid : view.playerUuid())
            + (view.primaryIslandId().isBlank() ? " 섬=없음" : " 섬=" + shortId(view.primaryIslandId()))
            + (view.lastName().isBlank() ? "" : " 이름=" + view.lastName());
    }

    public String templateAction(String label, String templateId, TemplateView view) {
        if (view == null) {
            return label + ": accepted target=" + compactTarget(templateId);
        }
        return label + ": accepted target=" + compactTarget(view.id().isBlank() ? templateId : view.id())
            + " 상태=" + (view.enabled() ? "사용 가능" : "비활성")
            + (view.minNodeVersion().isBlank() ? "" : " 최소버전=" + view.minNodeVersion());
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

    public String inviteCreate(CoreGuiViews.InviteView invite) {
        return "초대: 생성됨 invite=" + shortId(invite.inviteId())
            + " 섬=" + shortId(invite.islandId())
            + " target=" + shortId(invite.targetUuid())
            + " state=" + invite.state();
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

    public String islandInfo(CoreGuiViews.IslandInfoView view) {
        return "섬 정보: ID=" + shortId(view.islandId())
            + " 소유자=" + shortId(view.ownerUuid())
            + (view.name().isBlank() ? "" : " 이름=" + view.name())
            + " 상태=" + (view.state().isBlank() ? "UNKNOWN" : view.state())
            + " 크기=" + view.size()
            + " 레벨=" + view.level()
            + " 가치=" + view.worth()
            + " 공개=" + view.publicAccess();
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

    public String islandStat(String label, String field, CoreGuiViews.IslandInfoView view) {
        String value = switch (field) {
            case "worth" -> view.worth();
            case "size" -> Long.toString(view.size());
            case "border" -> Long.toString(view.border());
            default -> Long.toString(view.level());
        };
        return label + ": 섬=" + shortId(view.islandId()) + " 값=" + value;
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

    public String biomeInfo(java.util.UUID islandId, CoreGuiViews.BiomeView view) {
        return "섬 바이옴: 섬=" + shortId(islandId == null ? "" : islandId.toString())
            + " 바이옴=" + view.key()
            + " 변경자=" + shortId(view.updatedBy());
    }

    public String environmentAction(String label, EnvironmentActionView view) {
        return label + ": " + (view.accepted() ? "접수됨" : "거부됨")
            + (view.code().isBlank() ? "" : " code=" + view.code());
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

    public String runtimeInfo(AdminIslandRuntimeView view) {
        if (view == null) {
            return "Island runtime: failed code=NOT_FOUND";
        }
        if (!view.code().isBlank()) {
            return "Island runtime: failed code=" + view.code();
        }
        return "Island runtime: 섬=" + shortId(view.islandId())
            + " state=" + (view.state().isBlank() ? "UNKNOWN" : view.state())
            + routePrivacy.routeNodeSuffix(view.activeNode())
            + routePrivacy.runtimeWorldSuffix(view.activeWorld())
            + (view.hasCell() ? routePrivacy.runtimeCellSuffix("{\"cellX\":" + view.cellX() + ",\"cellZ\":" + view.cellZ() + "}") : "")
            + " fence=" + view.fencingToken();
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

    public String playerInfo(PlayerProfileView view) {
        if (view == null) {
            return "플레이어 정보: 실패 사유=NOT_FOUND";
        }
        return "플레이어 정보: ID=" + shortId(view.playerUuid())
            + (view.lastName().isBlank() ? "" : " 이름=" + view.lastName())
            + (view.primaryIslandId().isBlank() ? " 섬=없음" : " 섬=" + shortId(view.primaryIslandId()));
    }

    public String rankingList(String label, String body) {
        List<String> rankings = objects(body, "rankings");
        if (rankings.isEmpty()) {
            return label + ": 기록이 없습니다.";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        for (String object : rankings) {
            total++;
            if (entries.size() < 10) {
                String islandId = jsonValue(object, "islandId");
                String name = jsonValue(object, "name");
                entries.add("#" + total
                    + " " + (name.isBlank() ? "이름 없는 섬" : name)
                    + " (ID=" + shortId(islandId)
                    + ", 레벨=" + longValue(object, "level")
                    + ", 가치=" + jsonValue(object, "worth") + ")");
            }
        }
        return label + ": 전체 " + total + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String rankingList(String label, List<ProgressionRankingEntryView> rankings) {
        if (rankings == null || rankings.isEmpty()) {
            return label + ": 기록이 없습니다.";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        for (ProgressionRankingEntryView ranking : rankings) {
            total++;
            if (entries.size() < 10) {
                entries.add("#" + total
                    + " " + ranking.name()
                    + " (ID=" + shortId(ranking.islandId())
                    + ", 레벨=" + ranking.level()
                    + ", 가치=" + ranking.worth() + ")");
            }
        }
        return label + ": 전체 " + total + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String blockValueList(String body) {
        List<String> values = objects(body, "values");
        if (values.isEmpty()) {
            return "Block values: empty";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        for (String object : values) {
            total++;
            if (entries.size() < 10) {
                entries.add(jsonValue(object, "materialKey")
                    + " worth=" + jsonValue(object, "worth")
                    + " level=" + longValue(object, "levelPoints")
                    + " limit=" + longValue(object, "limit"));
            }
        }
        return "Block values: total=" + total + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String blockValueList(List<BlockValueView> values) {
        if (values == null || values.isEmpty()) {
            return "Block values: empty";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        for (BlockValueView value : values) {
            total++;
            if (entries.size() < 10) {
                entries.add(value.materialKey()
                    + " worth=" + value.worth()
                    + " level=" + value.levelPoints()
                    + " limit=" + value.limit());
            }
        }
        return "Block values: total=" + total + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String addonStateSummary(String body) {
        List<String> addons = objects(body, "addons");
        if (addons.isEmpty()) {
            return "Addon state: empty";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        for (String object : addons) {
            total++;
            if (entries.size() < 10) {
                entries.add(jsonValue(object, "addonId")
                    + " global=" + longValue(object, "globalKeys")
                    + " island=" + longValue(object, "islandKeys")
                    + " totalKeys=" + longValue(object, "totalKeys"));
            }
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

    public String addonStateSummary(AdminAddonStateSummaryView view) {
        if (view == null || view.addons().isEmpty()) {
            return "Addon state: empty";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        for (AdminAddonStateSummaryView.AddonView addon : view.addons()) {
            total++;
            if (entries.size() < 10) {
                entries.add(addon.addonId()
                    + " global=" + addon.globalKeys()
                    + " island=" + addon.islandKeys()
                    + " totalKeys=" + addon.totalKeys());
            }
        }
        return "Addon state: total=" + total
            + " owner=" + view.stateOwnership()
            + " registeredRequired=" + view.registeredAddonRequired()
            + " orphanPolicy=" + view.orphanStatePolicy()
            + " missingPolicy=" + view.missingAddonStatePolicy()
            + " tableKeyPrefix=" + view.tableKeyPrefix()
            + " maxKeysPerAddon=" + view.maxKeysPerAddon()
            + " maxValueLength=" + view.maxValueLength()
            + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String templateList(String body) {
        List<String> templates = objects(body, "templates");
        if (templates.isEmpty()) {
            return "섬 템플릿: 없음";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        int enabled = 0;
        for (String object : templates) {
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
        }
        return "섬 템플릿: 전체 " + total + "개, 사용 가능 " + enabled + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String templateList(List<TemplateView> templates) {
        if (templates == null || templates.isEmpty()) {
            return "섬 템플릿: 없음";
        }
        List<String> entries = new ArrayList<>();
        int enabled = 0;
        for (TemplateView template : templates) {
            if (template.enabled()) {
                enabled++;
            }
            if (entries.size() < 10) {
                entries.add(template.id()
                    + " " + (template.enabled() ? "사용 가능" : "비활성")
                    + (template.minNodeVersion().isBlank() ? "" : " 최소버전=" + template.minNodeVersion()));
            }
        }
        return "섬 템플릿: 전체 " + templates.size() + "개, 사용 가능 " + enabled + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String warpList(String label, String body) {
        return namedObjectList(label, body, "warps", object -> jsonValue(object, "name")
            + (boolValue(object, "publicAccess") ? "(공개)" : "")
            + " 섬=" + shortId(jsonValue(object, "islandId"))
            + " 위치=" + seconds(doubleValue(object, "localX")) + "," + seconds(doubleValue(object, "localY")) + "," + seconds(doubleValue(object, "localZ")));
    }

    public String warpList(String label, List<CoreGuiViews.WarpView> warps) {
        if (warps == null || warps.isEmpty()) {
            return label + ": empty";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        for (CoreGuiViews.WarpView warp : warps) {
            total++;
            if (entries.size() < 10) {
                entries.add(warp.name()
                    + (warp.publicAccess() ? "(공개)" : "")
                    + " 섬=" + shortId(warp.islandId())
                    + " 위치=" + seconds(warp.x()) + "," + seconds(warp.y()) + "," + seconds(warp.z()));
            }
        }
        return label + ": 전체 " + total + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String homeWarpAction(String label, HomeWarpActionView view) {
        return label + ": " + (view.accepted() ? "접수됨" : "거부됨")
            + (view.code().isBlank() ? "" : " code=" + view.code());
    }

    public String homeList(String body) {
        return namedObjectList("섬 홈", body, "homes", object -> jsonValue(object, "name")
            + " 위치=" + seconds(doubleValue(object, "localX")) + "," + seconds(doubleValue(object, "localY")) + "," + seconds(doubleValue(object, "localZ")));
    }

    public String homeList(List<CoreGuiViews.HomeView> homes) {
        if (homes == null || homes.isEmpty()) {
            return "섬 홈: empty";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        for (CoreGuiViews.HomeView home : homes) {
            total++;
            if (entries.size() < 10) {
                entries.add(home.name() + " 위치=" + seconds(home.x()) + "," + seconds(home.y()) + "," + seconds(home.z()));
            }
        }
        return "섬 홈: 전체 " + total + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String memberList(String body) {
        return namedObjectList("섬 멤버", body, "members", object -> shortId(jsonValue(object, "playerUuid"))
            + " 역할=" + jsonValue(object, "role"));
    }

    public String memberList(List<CoreGuiViews.MemberView> members) {
        if (members == null || members.isEmpty()) {
            return "섬 멤버: empty";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        for (CoreGuiViews.MemberView member : members) {
            total++;
            if (entries.size() < 10) {
                entries.add(shortId(member.playerUuid()) + " 역할=" + member.role());
            }
        }
        return "섬 멤버: 전체 " + total + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String banList(String body) {
        return namedObjectList("섬 밴", body, "bans", object -> shortId(jsonValue(object, "bannedUuid"))
            + " 사유=" + fallback(jsonValue(object, "reason"), "-"));
    }

    public String banList(List<CoreGuiViews.BanView> bans) {
        if (bans == null || bans.isEmpty()) {
            return "섬 밴: empty";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        for (CoreGuiViews.BanView ban : bans) {
            total++;
            if (entries.size() < 10) {
                entries.add(shortId(ban.bannedUuid()) + " 사유=" + fallback(ban.reason(), "-"));
            }
        }
        return "섬 밴: 전체 " + total + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String permissionList(String body) {
        return namedObjectList("섬 권한", body, "rules", object -> jsonValue(object, "role")
            + ":" + jsonValue(object, "permission")
            + "=" + (boolValue(object, "allowed") ? "허용" : "거부"));
    }

    public String permissionList(List<PermissionAssignmentView> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return "섬 권한: empty";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        for (PermissionAssignmentView permission : permissions) {
            total++;
            if (entries.size() < 10) {
                String subject = permission.role().isBlank() ? shortId(permission.playerUuid()) : permission.role();
                entries.add(subject + ":" + permission.permission() + "=" + (permission.allowed() ? "허용" : "거부"));
            }
        }
        return "섬 권한: 전체 " + total + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String roleList(String body) {
        return namedObjectList("섬 역할", body, "roles", object -> jsonValue(object, "role")
            + " weight=" + longValue(object, "weight")
            + " name=" + fallback(jsonValue(object, "displayName"), "-"));
    }

    public String roleList(List<CoreGuiViews.RoleView> roles) {
        if (roles == null || roles.isEmpty()) {
            return "섬 역할: empty";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        for (CoreGuiViews.RoleView role : roles) {
            total++;
            if (entries.size() < 10) {
                entries.add(role.role() + " weight=" + role.weight() + " name=" + fallback(role.displayName(), "-"));
            }
        }
        return "섬 역할: 전체 " + total + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String islandLogList(String body) {
        return namedObjectList("섬 로그", body, "logs", object -> fallback(jsonValue(object, "action"), "UNKNOWN")
            + " 처리자=" + shortId(jsonValue(object, "actorUuid"))
            + " 시각=" + jsonValue(object, "createdAt"));
    }

    public String islandLogList(List<CoreGuiViews.LogEntryView> logs) {
        if (logs == null || logs.isEmpty()) {
            return "섬 로그: empty";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        for (CoreGuiViews.LogEntryView log : logs) {
            total++;
            if (entries.size() < 10) {
                entries.add(fallback(log.action(), "UNKNOWN")
                    + " 처리자=" + shortId(log.actorUuid())
                    + " 시각=" + log.createdAt());
            }
        }
        return "섬 로그: 전체 " + total + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String bankInfo(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "섬 은행: 실패 사유=" + code;
        }
        return "섬 은행: 섬=" + shortId(jsonValue(body, "islandId"))
            + " balance=" + jsonValue(body, "balance");
    }

    public String bankInfo(java.util.UUID islandId, CoreGuiViews.BankView view) {
        return "섬 은행: 섬=" + shortId(islandId == null ? "" : islandId.toString())
            + " balance=" + view.balance();
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
        for (String object : objectsFromArray(body)) {
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

    public String flagList(Map<IslandFlag, String> flags) {
        if (flags == null || flags.isEmpty()) {
            return "섬 플래그: 없음";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        for (Map.Entry<IslandFlag, String> entry : flags.entrySet()) {
            total++;
            if (entries.size() < 12) {
                entries.add(entry.getKey().name() + "=" + entry.getValue());
            }
        }
        return "섬 플래그: 전체 " + total + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String memberAction(String label, MemberActionView view) {
        return label + ": " + (view.accepted() ? "접수됨" : "거부됨")
            + (view.code().isBlank() ? "" : " code=" + view.code());
    }

    public String settingsAction(String label, SettingsActionView view) {
        return label + ": " + (view.accepted() ? "접수됨" : "거부됨")
            + (view.code().isBlank() ? "" : " code=" + view.code());
    }

    public String permissionAction(String label, PermissionActionView view) {
        return label + ": " + (view.accepted() ? "접수됨" : "거부됨")
            + (view.code().isBlank() ? "" : " code=" + view.code());
    }

    public String roleMutation(String label, MutationResult<CoreGuiViews.RoleView> result) {
        CoreGuiViews.RoleView role = result.value();
        String roleKey = role == null ? "" : role.role();
        long weight = role == null ? 0L : role.weight();
        String displayName = role == null ? "" : role.displayName();
        return label + ": " + fallback(roleKey, "-")
            + " weight=" + weight
            + " name=" + fallback(displayName, "-")
            + (result.version().isBlank() ? "" : " version=" + result.version());
    }

    public String upgradeRules(String body) {
        List<String> rules = objects(body, "rules");
        if (rules.isEmpty()) {
            return "업그레이드 규칙: 없음";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        for (String object : rules) {
            total++;
            if (entries.size() < 12) {
                entries.add(jsonValue(object, "upgradeKey")
                    + " 유형=" + jsonValue(object, "type")
                    + " 최대=" + longValue(object, "maxLevel")
                    + " 기본비용=" + jsonValue(object, "baseCost"));
            }
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
        List<String> objects = objects(body, arrayField);
        if (objects.isEmpty()) {
            return label + ": empty";
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        for (String object : objects) {
            total++;
            if (entries.size() < 10) {
                entries.add(formatter.apply(object));
            }
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
