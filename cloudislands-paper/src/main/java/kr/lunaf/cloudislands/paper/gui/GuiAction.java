package kr.lunaf.cloudislands.paper.gui;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.RoleId;

public sealed interface GuiAction permits GuiAction.Close, GuiAction.AdminNodeAction, GuiAction.AdminIslandPrompt, GuiAction.MainOpen, GuiAction.InfoOpen, GuiAction.IslandListOpen, GuiAction.ChatOpen, GuiAction.LogsOpen, GuiAction.LogsList, GuiAction.NoPayload, GuiAction.IslandCreate, GuiAction.BankAmount, GuiAction.SnapshotCreate, GuiAction.SnapshotRestore, GuiAction.BiomeSet, GuiAction.FlagSet, GuiAction.LimitSet, GuiAction.VisitTarget, GuiAction.HomeTeleport, GuiAction.HomeSet, GuiAction.WarpTeleport, GuiAction.WarpDelete, GuiAction.WarpAccess, GuiAction.InviteAction, GuiAction.MemberPage, GuiAction.MemberDetail, GuiAction.MemberRoleChange, GuiAction.BanPardon, GuiAction.LogDetail, GuiAction.RoleWeightAdjust, GuiAction.RankingList, GuiAction.MissionsOpen, GuiAction.MissionComplete, GuiAction.UpgradePurchase, GuiAction.DangerResetConfirm, GuiAction.DangerDeleteConfirm, GuiAction.PermissionPage, GuiAction.ChangePermission, GuiAction.MemberRemoval {
    String actionId();

    Map<String, String> data();

    default String confirmationToken() {
        return data().getOrDefault(ConfirmationTokenPolicy.TOKEN_KEY, "");
    }

    default String stableFingerprint(GuiClick click) {
        StringBuilder builder = new StringBuilder(actionId())
            .append('|')
            .append(click == null ? GuiClick.UNSUPPORTED.name() : click.name());
        data().entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .forEach(entry -> builder.append('|').append(entry.getKey()).append('=').append(entry.getValue()));
        return builder.toString();
    }

    record Close() implements GuiAction {
        @Override
        public String actionId() {
            return "gui.close";
        }

        @Override
        public Map<String, String> data() {
            return Map.of();
        }
    }

    record AdminNodeAction(AdminNodeActionType type, String nodeId, String reason, String confirmationToken) implements GuiAction {
        public AdminNodeAction {
            if (type == null) {
                throw new IllegalArgumentException("type is required");
            }
            nodeId = nodeId == null ? "" : nodeId.trim();
            reason = reason == null || reason.isBlank() ? "admin-gui" : reason.trim();
            confirmationToken = confirmationToken == null ? "" : confirmationToken.trim();
        }

        @Override
        public String actionId() {
            return type.actionId();
        }

        @Override
        public Map<String, String> data() {
            java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
            if (!nodeId.isBlank()) {
                values.put("nodeId", nodeId);
            }
            if (type.confirmation()) {
                values.put("reason", reason);
                if (!confirmationToken.isBlank()) {
                    values.put(ConfirmationTokenPolicy.TOKEN_KEY, confirmationToken);
                }
            }
            return Map.copyOf(values);
        }

        public boolean kickAll() {
            return type == AdminNodeActionType.KICKALL_PREPARE || type == AdminNodeActionType.KICKALL_CONFIRM;
        }

        public boolean shutdownSafe() {
            return type == AdminNodeActionType.SHUTDOWN_SAFE_PREPARE || type == AdminNodeActionType.SHUTDOWN_SAFE_CONFIRM;
        }

        public boolean confirmation() {
            return type.confirmation();
        }

        @Override
        public String confirmationToken() {
            return confirmationToken;
        }
    }

    enum AdminNodeActionType {
        OPEN("admin.node.open", false),
        LIST("admin.node.list", false),
        INFO("admin.node.info", false),
        ISLANDS("admin.node.islands", false),
        DRAIN("admin.node.drain", false),
        UNDRAIN("admin.node.undrain", false),
        SWEEP("admin.node.sweep", false),
        KICKALL_PREPARE("admin.node.kickall.prepare", false),
        SHUTDOWN_SAFE_PREPARE("admin.node.shutdown-safe.prepare", false),
        KICKALL_CONFIRM("admin.node.kickall.confirm", true),
        SHUTDOWN_SAFE_CONFIRM("admin.node.shutdown-safe.confirm", true);

        private final String actionId;
        private final boolean confirmation;

        AdminNodeActionType(String actionId, boolean confirmation) {
            this.actionId = actionId;
            this.confirmation = confirmation;
        }

        public String actionId() {
            return actionId;
        }

        public boolean confirmation() {
            return confirmation;
        }
    }

    record AdminIslandPrompt(AdminIslandPromptType type, String nodeId) implements GuiAction {
        public AdminIslandPrompt {
            if (type == null) {
                throw new IllegalArgumentException("type is required");
            }
            nodeId = nodeId == null ? "" : nodeId.trim();
        }

        @Override
        public String actionId() {
            return type.actionId();
        }

        @Override
        public Map<String, String> data() {
            return nodeId.isBlank() ? Map.of() : Map.of("nodeId", nodeId);
        }
    }

    enum AdminIslandPromptType {
        WHERE("admin.island.where.prompt"),
        MIGRATE("admin.island.migrate.prompt");

        private final String actionId;

        AdminIslandPromptType(String actionId) {
            this.actionId = actionId;
        }

        public String actionId() {
            return actionId;
        }
    }

    record MainOpen() implements GuiAction {
        @Override
        public String actionId() {
            return "island.main.open";
        }

        @Override
        public Map<String, String> data() {
            return Map.of();
        }
    }

    record InfoOpen() implements GuiAction {
        @Override
        public String actionId() {
            return "island.info.open";
        }

        @Override
        public Map<String, String> data() {
            return Map.of();
        }
    }

    record IslandListOpen() implements GuiAction {
        @Override
        public String actionId() {
            return "island.list.open";
        }

        @Override
        public Map<String, String> data() {
            return Map.of();
        }
    }

    record ChatOpen() implements GuiAction {
        @Override
        public String actionId() {
            return "island.chat.open";
        }

        @Override
        public Map<String, String> data() {
            return Map.of();
        }
    }

    record LogsOpen() implements GuiAction {
        @Override
        public String actionId() {
            return "island.logs.open";
        }

        @Override
        public Map<String, String> data() {
            return Map.of();
        }
    }

    record LogsList() implements GuiAction {
        @Override
        public String actionId() {
            return "island.logs.list";
        }

        @Override
        public Map<String, String> data() {
            return Map.of();
        }
    }

    record NoPayload(NoPayloadType type) implements GuiAction {
        public NoPayload {
            if (type == null) {
                throw new IllegalArgumentException("type is required");
            }
        }

        @Override
        public String actionId() {
            return type.actionId();
        }

        @Override
        public Map<String, String> data() {
            return Map.of();
        }
    }

    enum NoPayloadType {
        BANK_OPEN("island.bank.open"),
        SNAPSHOTS_OPEN("island.snapshots.open"),
        SNAPSHOTS_LIST("island.snapshots.list"),
        RANKING_OPEN("island.ranking.open"),
        REVIEWS_OPEN("island.reviews.open"),
        LEVEL_RECALCULATE("island.level.recalculate"),
        LEVEL_SHOW("island.level.show"),
        WORTH_SHOW("island.worth.show"),
        UPGRADES_OPEN("island.upgrades.open"),
        UPGRADES_LIST("island.upgrades.list"),
        HOMES_OPEN("island.homes.open"),
        WARPS_OPEN("island.warps.open"),
        WAREHOUSE_OPEN("island.warehouse.open"),
        VISIT_OPEN("island.visit.open"),
        VISIT_RANDOM("island.visit.random"),
        VISIT_PUBLIC_OPEN("island.visit.public.open"),
        CREATE_OPEN("island.create.open"),
        DANGER_OPEN("island.danger.open"),
        DANGER_RESET_PREPARE("island.danger.reset.prepare"),
        DANGER_DELETE_PREPARE("island.danger.delete.prepare"),
        MEMBERS_OPEN("island.members.open"),
        MEMBER_ROLE("island.member.role"),
        MEMBER_INVITE("island.member.invite"),
        MEMBER_INVITE_HELP("island.member.invite.help"),
        MEMBER_LIST("island.member.list"),
        INVITES_OPEN("island.invites.open"),
        BANS_OPEN("island.bans.open"),
        BANS_LIST("island.bans.list"),
        PERMISSIONS_OPEN("island.permissions.open"),
        PERMISSIONS_LIST("island.permissions.list"),
        PERMISSIONS_SAVE("island.permissions.save"),
        PERMISSIONS_RESET("island.permissions.reset"),
        ROLES_OPEN("island.roles.open"),
        ROLES_LIST("island.roles.list"),
        BIOME_OPEN("island.biome.open"),
        BIOME_SHOW("island.biome.show"),
        LIMITS_OPEN("island.limits.open"),
        LIMITS_LIST("island.limits.list"),
        BORDER_OPEN("island.border.open"),
        SETTINGS_OPEN("island.settings.open"),
        PUBLIC_TOGGLE("island.public.toggle"),
        LOCK_TOGGLE("island.lock.toggle"),
        FLAGS_OPEN("island.flags.open"),
        FLAGS_LIST("island.flags.list");

        private final String actionId;

        NoPayloadType(String actionId) {
            this.actionId = actionId;
        }

        public String actionId() {
            return actionId;
        }
    }

    record IslandCreate(String templateId) implements GuiAction {
        public IslandCreate {
            templateId = templateId == null || templateId.isBlank() ? "default" : templateId.trim();
        }

        @Override
        public String actionId() {
            return "island.create";
        }

        @Override
        public Map<String, String> data() {
            return Map.of("templateId", templateId);
        }
    }

    record BankAmount(String actionId, BigDecimal amount) implements GuiAction {
        public BankAmount {
            actionId = actionId == null ? "" : actionId.trim();
            if (!actionId.equals("island.bank.deposit") && !actionId.equals("island.bank.withdraw")) {
                throw new IllegalArgumentException("unsupported bank action");
            }
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("positive amount is required");
            }
            amount = amount.stripTrailingZeros();
        }

        @Override
        public Map<String, String> data() {
            return Map.of("amount", amount.toPlainString());
        }

        public boolean deposit() {
            return actionId.equals("island.bank.deposit");
        }
    }

    record SnapshotCreate(String reason) implements GuiAction {
        public SnapshotCreate {
            reason = reason == null || reason.isBlank() ? "manual" : reason.trim();
        }

        @Override
        public String actionId() {
            return "island.snapshot.create";
        }

        @Override
        public Map<String, String> data() {
            return Map.of("reason", reason);
        }
    }

    record SnapshotRestore(SnapshotRestoreType type, long snapshotNo, String confirmationToken) implements GuiAction {
        public SnapshotRestore {
            if (type == null) {
                throw new IllegalArgumentException("type is required");
            }
            if (snapshotNo <= 0L) {
                throw new IllegalArgumentException("positive snapshotNo is required");
            }
            confirmationToken = confirmationToken == null ? "" : confirmationToken.trim();
        }

        @Override
        public String actionId() {
            return type.actionId();
        }

        @Override
        public Map<String, String> data() {
            if (type.confirmation()) {
                return Map.of(
                    "snapshotNo", Long.toString(snapshotNo),
                    ConfirmationTokenPolicy.TOKEN_KEY, confirmationToken
                );
            }
            return Map.of("snapshotNo", Long.toString(snapshotNo));
        }

        public boolean confirmation() {
            return type.confirmation();
        }

        @Override
        public String confirmationToken() {
            return confirmationToken;
        }
    }

    enum SnapshotRestoreType {
        PREPARE("island.snapshot.restore.prepare", false),
        CONFIRM(ConfirmationTokenPolicy.SNAPSHOT_RESTORE_CONFIRM_ACTION, true);

        private final String actionId;
        private final boolean confirmation;

        SnapshotRestoreType(String actionId, boolean confirmation) {
            this.actionId = actionId;
            this.confirmation = confirmation;
        }

        public String actionId() {
            return actionId;
        }

        public boolean confirmation() {
            return confirmation;
        }
    }

    record BiomeSet(String biomeKey) implements GuiAction {
        public BiomeSet {
            biomeKey = biomeKey == null ? "" : biomeKey.trim();
            if (biomeKey.isBlank()) {
                throw new IllegalArgumentException("biomeKey is required");
            }
        }

        @Override
        public String actionId() {
            return "island.biome.set";
        }

        @Override
        public Map<String, String> data() {
            return Map.of("biomeKey", biomeKey);
        }
    }

    record FlagSet(IslandFlag flag) implements GuiAction {
        public FlagSet {
            if (flag == null) {
                throw new IllegalArgumentException("flag is required");
            }
        }

        @Override
        public String actionId() {
            return "island.flag.set";
        }

        @Override
        public Map<String, String> data() {
            return Map.of("flag", flag.name());
        }
    }

    record LimitSet(String limitKey, long value) implements GuiAction {
        public LimitSet {
            limitKey = normalizeKey(limitKey);
            if (limitKey.isBlank()) {
                throw new IllegalArgumentException("limitKey is required");
            }
            if (value < 0L) {
                throw new IllegalArgumentException("limit value must be non-negative");
            }
        }

        @Override
        public String actionId() {
            return "island.limit.set";
        }

        @Override
        public Map<String, String> data() {
            return Map.of("limitKey", limitKey, "value", Long.toString(value));
        }

        private static String normalizeKey(String value) {
            return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        }
    }

    record VisitTarget(String target) implements GuiAction {
        public VisitTarget {
            target = target == null ? "" : target.trim();
            if (target.isBlank()) {
                throw new IllegalArgumentException("target is required");
            }
        }

        @Override
        public String actionId() {
            return "island.visit.target";
        }

        @Override
        public Map<String, String> data() {
            return Map.of("target", target);
        }
    }

    record HomeTeleport(String homeName) implements GuiAction {
        public HomeTeleport {
            homeName = requiredName(homeName, "homeName");
        }

        @Override
        public String actionId() {
            return "island.home";
        }

        @Override
        public Map<String, String> data() {
            return Map.of("homeName", homeName);
        }
    }

    record HomeSet(String homeName) implements GuiAction {
        public HomeSet {
            homeName = requiredName(homeName, "homeName");
        }

        @Override
        public String actionId() {
            return "island.home.set";
        }

        @Override
        public Map<String, String> data() {
            return Map.of("homeName", homeName);
        }
    }

    record WarpTeleport(String warpName, UUID islandId) implements GuiAction {
        public WarpTeleport {
            warpName = requiredName(warpName, "warpName");
        }

        @Override
        public String actionId() {
            return "island.warp.teleport";
        }

        @Override
        public Map<String, String> data() {
            if (islandId == null) {
                return Map.of("warpName", warpName);
            }
            return Map.of("warpName", warpName, "islandId", islandId.toString());
        }
    }

    record WarpDelete(WarpDeleteType type, String warpName, String confirmationToken) implements GuiAction {
        public WarpDelete {
            if (type == null) {
                throw new IllegalArgumentException("type is required");
            }
            warpName = requiredName(warpName, "warpName");
            confirmationToken = confirmationToken == null ? "" : confirmationToken.trim();
        }

        @Override
        public String actionId() {
            return type.actionId();
        }

        @Override
        public Map<String, String> data() {
            if (type.confirmation()) {
                return Map.of(
                    "warpName", warpName,
                    ConfirmationTokenPolicy.TOKEN_KEY, confirmationToken
                );
            }
            return Map.of("warpName", warpName);
        }

        public boolean confirmation() {
            return type.confirmation();
        }

        @Override
        public String confirmationToken() {
            return confirmationToken;
        }
    }

    enum WarpDeleteType {
        PREPARE("island.warp.delete.prepare", false),
        CONFIRM(ConfirmationTokenPolicy.WARP_DELETE_CONFIRM_ACTION, true);

        private final String actionId;
        private final boolean confirmation;

        WarpDeleteType(String actionId, boolean confirmation) {
            this.actionId = actionId;
            this.confirmation = confirmation;
        }

        public String actionId() {
            return actionId;
        }

        public boolean confirmation() {
            return confirmation;
        }
    }

    record WarpAccess(String actionId, String warpName, boolean publicAccess) implements GuiAction {
        public WarpAccess {
            actionId = actionId == null ? "" : actionId.trim();
            warpName = requiredName(warpName, "warpName");
            if (!actionId.equals("island.warp.public") && !actionId.equals("island.warp.private") && !actionId.equals("island.warp.public.toggle")) {
                throw new IllegalArgumentException("unsupported warp access action");
            }
        }

        @Override
        public Map<String, String> data() {
            if (actionId.equals("island.warp.public.toggle")) {
                return Map.of("warpName", warpName, "publicAccess", Boolean.toString(publicAccess));
            }
            return Map.of("warpName", warpName);
        }

        public boolean targetPublicAccess() {
            if (actionId.equals("island.warp.public")) {
                return true;
            }
            if (actionId.equals("island.warp.private")) {
                return false;
            }
            return !publicAccess;
        }
    }

    record InviteAction(String actionId, UUID inviteId) implements GuiAction {
        public InviteAction {
            actionId = actionId == null ? "" : actionId.trim();
            if (!actionId.equals("island.invite.accept") && !actionId.equals("island.invite.decline")) {
                throw new IllegalArgumentException("unsupported invite action");
            }
            if (inviteId == null) {
                throw new IllegalArgumentException("inviteId is required");
            }
        }

        @Override
        public Map<String, String> data() {
            return Map.of("inviteId", inviteId.toString());
        }

        public boolean accept() {
            return actionId.equals("island.invite.accept");
        }
    }

    record MemberPage(int page) implements GuiAction {
        @Override
        public String actionId() {
            return "island.members.page";
        }

        @Override
        public Map<String, String> data() {
            return Map.of("page", Integer.toString(Math.max(0, page)));
        }
    }

    record MemberDetail(UUID playerUuid, String playerName, String role, String presenceState, String lastSeenAt) implements GuiAction {
        public MemberDetail {
            if (playerUuid == null) {
                throw new IllegalArgumentException("playerUuid is required");
            }
            playerName = playerName == null ? "" : playerName.trim();
            role = role == null || role.isBlank() ? "unknown" : role.trim();
            presenceState = presenceState == null || presenceState.isBlank() ? "UNKNOWN" : presenceState.trim();
            lastSeenAt = lastSeenAt == null ? "" : lastSeenAt.trim();
        }

        @Override
        public String actionId() {
            return "island.member.detail";
        }

        @Override
        public Map<String, String> data() {
            return Map.of(
                "playerUuid", playerUuid.toString(),
                "playerName", playerName,
                "role", role,
                "presenceState", presenceState,
                "lastSeenAt", lastSeenAt
            );
        }

        public String displayName() {
            return playerName.isBlank() ? playerUuid.toString() : playerName;
        }
    }

    record MemberRoleChange(MemberRoleChangeType type, UUID playerUuid, String confirmationToken) implements GuiAction {
        public MemberRoleChange {
            if (type == null) {
                throw new IllegalArgumentException("type is required");
            }
            if (playerUuid == null) {
                throw new IllegalArgumentException("playerUuid is required");
            }
            confirmationToken = confirmationToken == null ? "" : confirmationToken.trim();
        }

        @Override
        public String actionId() {
            return type.actionId();
        }

        @Override
        public Map<String, String> data() {
            if (type.confirmation()) {
                return Map.of(
                    "playerUuid", playerUuid.toString(),
                    ConfirmationTokenPolicy.TOKEN_KEY, confirmationToken
                );
            }
            return Map.of("playerUuid", playerUuid.toString());
        }

        public boolean promote() {
            return type.promote();
        }

        public boolean confirmation() {
            return type.confirmation();
        }

        @Override
        public String confirmationToken() {
            return confirmationToken;
        }
    }

    enum MemberRoleChangeType {
        PROMOTE_PREPARE("island.member.promote.prepare", true, false),
        DEMOTE_PREPARE("island.member.demote.prepare", false, false),
        PROMOTE_CONFIRM("island.member.promote", true, true),
        DEMOTE_CONFIRM("island.member.demote", false, true);

        private final String actionId;
        private final boolean promote;
        private final boolean confirmation;

        MemberRoleChangeType(String actionId, boolean promote, boolean confirmation) {
            this.actionId = actionId;
            this.promote = promote;
            this.confirmation = confirmation;
        }

        public String actionId() {
            return actionId;
        }

        public boolean promote() {
            return promote;
        }

        public boolean confirmation() {
            return confirmation;
        }
    }

    record BanPardon(BanPardonType type, UUID playerUuid, String confirmationToken) implements GuiAction {
        public BanPardon {
            if (type == null) {
                throw new IllegalArgumentException("type is required");
            }
            if (playerUuid == null) {
                throw new IllegalArgumentException("playerUuid is required");
            }
            confirmationToken = confirmationToken == null ? "" : confirmationToken.trim();
        }

        @Override
        public String actionId() {
            return type.actionId();
        }

        @Override
        public Map<String, String> data() {
            if (type.confirmation()) {
                return Map.of(
                    "playerUuid", playerUuid.toString(),
                    ConfirmationTokenPolicy.TOKEN_KEY, confirmationToken
                );
            }
            return Map.of("playerUuid", playerUuid.toString());
        }

        public boolean confirmation() {
            return type.confirmation();
        }

        @Override
        public String confirmationToken() {
            return confirmationToken;
        }
    }

    enum BanPardonType {
        PREPARE("island.ban.pardon.prepare", false),
        CONFIRM(ConfirmationTokenPolicy.BAN_PARDON_CONFIRM_ACTION, true);

        private final String actionId;
        private final boolean confirmation;

        BanPardonType(String actionId, boolean confirmation) {
            this.actionId = actionId;
            this.confirmation = confirmation;
        }

        public String actionId() {
            return actionId;
        }

        public boolean confirmation() {
            return confirmation;
        }
    }

    record RoleWeightAdjust(RoleId roleId, int weight, String displayName) implements GuiAction {
        public RoleWeightAdjust {
            if (roleId == null) {
                throw new IllegalArgumentException("roleId is required");
            }
            if (weight < 0) {
                throw new IllegalArgumentException("weight must be non-negative");
            }
            displayName = displayName == null ? "" : displayName.trim();
        }

        @Override
        public String actionId() {
            return "island.role.weight.adjust";
        }

        @Override
        public Map<String, String> data() {
            return Map.of("role", roleId.value(), "weight", Integer.toString(weight), "displayName", displayName);
        }
    }

    record LogDetail(String logAction, String actorUuid, String createdAt, String payload) implements GuiAction {
        public LogDetail {
            logAction = requiredName(logAction, "logAction");
            actorUuid = actorUuid == null ? "" : actorUuid.trim();
            createdAt = createdAt == null ? "" : createdAt.trim();
            payload = payload == null ? "" : payload.trim();
        }

        @Override
        public String actionId() {
            return "island.log.detail";
        }

        @Override
        public Map<String, String> data() {
            return Map.of(
                "action", logAction,
                "actorUuid", actorUuid,
                "createdAt", createdAt,
                "payload", payload
            );
        }
    }

    record RankingList(String kind) implements GuiAction {
        public RankingList {
            kind = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
        }

        @Override
        public String actionId() {
            return "island.ranking.list";
        }

        @Override
        public Map<String, String> data() {
            return kind.isBlank() ? Map.of() : Map.of("kind", kind);
        }

        public boolean worth() {
            return kind.equals("WORTH");
        }
    }

    record MissionsOpen(String kind) implements GuiAction {
        public MissionsOpen {
            kind = kind == null || kind.isBlank() ? "MISSION" : kind.trim().toUpperCase(Locale.ROOT);
        }

        @Override
        public String actionId() {
            return "island.missions.open";
        }

        @Override
        public Map<String, String> data() {
            return Map.of("kind", kind);
        }
    }

    record MissionComplete(String missionKey, String kind, String label) implements GuiAction {
        public MissionComplete {
            missionKey = requiredName(missionKey, "missionKey");
            kind = kind == null || kind.isBlank() ? "MISSION" : kind.trim().toUpperCase(Locale.ROOT);
            label = label == null || label.isBlank() ? "섬 미션" : label.trim();
        }

        @Override
        public String actionId() {
            return "island.mission.complete";
        }

        @Override
        public Map<String, String> data() {
            return Map.of("missionKey", missionKey, "kind", kind, "label", label);
        }
    }

    record UpgradePurchase(String upgradeKey) implements GuiAction {
        public UpgradePurchase {
            upgradeKey = requiredName(upgradeKey, "upgradeKey");
        }

        @Override
        public String actionId() {
            return "island.upgrade.purchase";
        }

        @Override
        public Map<String, String> data() {
            return Map.of("upgradeKey", upgradeKey);
        }
    }

    record DangerResetConfirm(String operation, String token, String reason) implements GuiAction {
        public DangerResetConfirm {
            operation = operation == null ? "" : operation.trim();
            token = token == null ? "" : token.trim();
            reason = reason == null || reason.isBlank() ? "player-reset" : reason.trim();
            if (!DangerousGuiActionPolicy.RESET_OPERATION.equals(operation) || !DangerousGuiActionPolicy.RESET_TOKEN.equals(token)) {
                throw new IllegalArgumentException("invalid reset confirmation");
            }
        }

        @Override
        public String actionId() {
            return DangerousGuiActionPolicy.RESET_CONFIRM_ACTION;
        }

        @Override
        public Map<String, String> data() {
            return Map.of(
                DangerousGuiActionPolicy.OPERATION_KEY, operation,
                DangerousGuiActionPolicy.TOKEN_KEY, token,
                "reason", reason
            );
        }
    }

    record DangerDeleteConfirm(String operation, String token) implements GuiAction {
        public DangerDeleteConfirm {
            operation = operation == null ? "" : operation.trim();
            token = token == null ? "" : token.trim();
            if (!DangerousGuiActionPolicy.DELETE_OPERATION.equals(operation) || !DangerousGuiActionPolicy.DELETE_TOKEN.equals(token)) {
                throw new IllegalArgumentException("invalid delete confirmation");
            }
        }

        @Override
        public String actionId() {
            return DangerousGuiActionPolicy.DELETE_CONFIRM_ACTION;
        }

        @Override
        public Map<String, String> data() {
            return Map.of(DangerousGuiActionPolicy.OPERATION_KEY, operation, DangerousGuiActionPolicy.TOKEN_KEY, token);
        }
    }

    record PermissionPage(int page, int rolePage) implements GuiAction {
        @Override
        public String actionId() {
            return "island.permissions.page";
        }

        @Override
        public Map<String, String> data() {
            return Map.of("page", Integer.toString(Math.max(0, page)), "rolePage", Integer.toString(Math.max(0, rolePage)));
        }
    }

    record ChangePermission(RoleId roleId, IslandPermission permission, String expectedVersion) implements GuiAction {
        public ChangePermission {
            if (roleId == null) {
                throw new IllegalArgumentException("roleId is required");
            }
            if (permission == null) {
                throw new IllegalArgumentException("permission is required");
            }
            expectedVersion = expectedVersion == null ? "" : expectedVersion.trim();
        }

        @Override
        public String actionId() {
            return "island.permissions.set";
        }

        @Override
        public Map<String, String> data() {
            if (expectedVersion.isBlank()) {
                return Map.of("role", roleId.value(), "permission", permission.name());
            }
            return Map.of("role", roleId.value(), "permission", permission.name(), "expectedVersion", expectedVersion);
        }
    }

    record MemberRemoval(MemberRemovalType type, UUID playerUuid, String confirmationToken) implements GuiAction {
        public MemberRemoval {
            if (type == null) {
                throw new IllegalArgumentException("type is required");
            }
            if (playerUuid == null) {
                throw new IllegalArgumentException("playerUuid is required");
            }
            confirmationToken = confirmationToken == null ? "" : confirmationToken.trim();
        }

        @Override
        public String actionId() {
            return type.actionId();
        }

        @Override
        public Map<String, String> data() {
            if (type.confirmation()) {
                return Map.of(
                    "playerUuid", playerUuid.toString(),
                    ConfirmationTokenPolicy.TOKEN_KEY, confirmationToken
                );
            }
            return Map.of("playerUuid", playerUuid.toString());
        }

        public boolean confirmation() {
            return type.confirmation();
        }

        @Override
        public String confirmationToken() {
            return confirmationToken;
        }
    }

    enum MemberRemovalType {
        PREPARE("island.member.remove.prepare", false),
        CONFIRM(ConfirmationTokenPolicy.MEMBER_REMOVE_CONFIRM_ACTION, true);

        private final String actionId;
        private final boolean confirmation;

        MemberRemovalType(String actionId, boolean confirmation) {
            this.actionId = actionId;
            this.confirmation = confirmation;
        }

        public String actionId() {
            return actionId;
        }

        public boolean confirmation() {
            return confirmation;
        }
    }

    private static String requiredName(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }
}
