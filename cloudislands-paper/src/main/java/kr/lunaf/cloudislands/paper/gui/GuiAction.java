package kr.lunaf.cloudislands.paper.gui;

import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.RoleId;

public sealed interface GuiAction permits GuiAction.Raw, GuiAction.PermissionPage, GuiAction.ChangePermission, GuiAction.MemberRemoval {
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
