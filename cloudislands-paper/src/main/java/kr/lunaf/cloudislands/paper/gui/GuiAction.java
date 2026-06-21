package kr.lunaf.cloudislands.paper.gui;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.RoleId;

public sealed interface GuiAction permits GuiAction.Raw, GuiAction.BankAmount, GuiAction.SnapshotCreate, GuiAction.SnapshotRestore, GuiAction.BiomeSet, GuiAction.FlagSet, GuiAction.LimitSet, GuiAction.PermissionPage, GuiAction.ChangePermission, GuiAction.MemberRemoval {
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
}
