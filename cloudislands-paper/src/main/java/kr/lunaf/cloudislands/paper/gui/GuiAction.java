package kr.lunaf.cloudislands.paper.gui;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.RoleId;

public sealed interface GuiAction permits GuiAction.Raw, GuiAction.BankAmount, GuiAction.SnapshotCreate, GuiAction.SnapshotRestore, GuiAction.BiomeSet, GuiAction.FlagSet, GuiAction.LimitSet, GuiAction.VisitTarget, GuiAction.HomeTeleport, GuiAction.HomeSet, GuiAction.WarpTeleport, GuiAction.WarpDelete, GuiAction.WarpAccess, GuiAction.InviteAction, GuiAction.MemberRoleChange, GuiAction.BanPardon, GuiAction.PermissionPage, GuiAction.ChangePermission, GuiAction.MemberRemoval {
    String actionId();

    Map<String, String> data();

    record Raw(String actionId, Map<String, String> data) implements GuiAction {
        public Raw {
            actionId = actionId == null ? "" : actionId.trim();
            data = data == null ? Map.of() : Map.copyOf(data);
            if (actionId.isBlank()) {
                throw new IllegalArgumentException("actionId is required");
            }
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

    record SnapshotRestore(String actionId, long snapshotNo, Map<String, String> data) implements GuiAction {
        public SnapshotRestore {
            actionId = actionId == null ? "" : actionId.trim();
            data = data == null ? Map.of() : Map.copyOf(data);
            if (!actionId.equals("island.snapshot.restore.prepare") && !snapshotRestoreConfirmation(actionId)) {
                throw new IllegalArgumentException("unsupported snapshot restore action");
            }
            if (snapshotNo <= 0L) {
                throw new IllegalArgumentException("positive snapshotNo is required");
            }
        }

        @Override
        public Map<String, String> data() {
            java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>(data);
            values.put("snapshotNo", Long.toString(snapshotNo));
            return Map.copyOf(values);
        }

        public boolean confirmation() {
            return snapshotRestoreConfirmation(actionId);
        }

        private static boolean snapshotRestoreConfirmation(String actionId) {
            return ConfirmationTokenPolicy.requiresToken(actionId) && actionId.endsWith(".snapshot.restore.confirm");
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

    record WarpDelete(String actionId, String warpName, Map<String, String> data) implements GuiAction {
        public WarpDelete {
            actionId = actionId == null ? "" : actionId.trim();
            warpName = requiredName(warpName, "warpName");
            data = data == null ? Map.of() : Map.copyOf(data);
            if (!actionId.equals("island.warp.delete.prepare") && !warpDeleteConfirmation(actionId)) {
                throw new IllegalArgumentException("unsupported warp delete action");
            }
        }

        @Override
        public Map<String, String> data() {
            java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>(data);
            values.put("warpName", warpName);
            return Map.copyOf(values);
        }

        public boolean confirmation() {
            return warpDeleteConfirmation(actionId);
        }

        private static boolean warpDeleteConfirmation(String actionId) {
            return ConfirmationTokenPolicy.requiresToken(actionId) && actionId.endsWith(".warp.delete.confirm");
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

    record MemberRoleChange(String actionId, UUID playerUuid, Map<String, String> data) implements GuiAction {
        public MemberRoleChange {
            actionId = actionId == null ? "" : actionId.trim();
            data = data == null ? Map.of() : Map.copyOf(data);
            if (!memberRolePrepare(actionId) && !memberRoleConfirmation(actionId)) {
                throw new IllegalArgumentException("unsupported member role action");
            }
            if (playerUuid == null) {
                throw new IllegalArgumentException("playerUuid is required");
            }
        }

        @Override
        public Map<String, String> data() {
            java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>(data);
            values.put("playerUuid", playerUuid.toString());
            return Map.copyOf(values);
        }

        public boolean promote() {
            return actionId.equals("island.member.promote.prepare") || actionId.equals("island.member.promote");
        }

        public boolean confirmation() {
            return memberRoleConfirmation(actionId);
        }

        private static boolean memberRolePrepare(String actionId) {
            return actionId.equals("island.member.promote.prepare") || actionId.equals("island.member.demote.prepare");
        }

        private static boolean memberRoleConfirmation(String actionId) {
            return ConfirmationTokenPolicy.requiresToken(actionId)
                && (actionId.equals("island.member.promote") || actionId.equals("island.member.demote"));
        }
    }

    record BanPardon(String actionId, UUID playerUuid, Map<String, String> data) implements GuiAction {
        public BanPardon {
            actionId = actionId == null ? "" : actionId.trim();
            data = data == null ? Map.of() : Map.copyOf(data);
            if (!actionId.equals("island.ban.pardon.prepare") && !banPardonConfirmation(actionId)) {
                throw new IllegalArgumentException("unsupported ban pardon action");
            }
            if (playerUuid == null) {
                throw new IllegalArgumentException("playerUuid is required");
            }
        }

        @Override
        public Map<String, String> data() {
            java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>(data);
            values.put("playerUuid", playerUuid.toString());
            return Map.copyOf(values);
        }

        public boolean confirmation() {
            return banPardonConfirmation(actionId);
        }

        private static boolean banPardonConfirmation(String actionId) {
            return ConfirmationTokenPolicy.requiresToken(actionId) && actionId.endsWith(".ban.pardon.confirm");
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

    record MemberRemoval(String actionId, UUID playerUuid, Map<String, String> data) implements GuiAction {
        public MemberRemoval {
            actionId = actionId == null ? "" : actionId.trim();
            data = data == null ? Map.of() : Map.copyOf(data);
            if (!actionId.equals("island.member.remove.prepare") && !memberRemovalConfirmation(actionId)) {
                throw new IllegalArgumentException("unsupported member removal action");
            }
            if (playerUuid == null) {
                throw new IllegalArgumentException("playerUuid is required");
            }
        }

        @Override
        public Map<String, String> data() {
            java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>(data);
            values.put("playerUuid", playerUuid.toString());
            return Map.copyOf(values);
        }

        private static boolean memberRemovalConfirmation(String actionId) {
            return ConfirmationTokenPolicy.requiresToken(actionId) && actionId.endsWith(".member.remove.confirm");
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
