package kr.lunaf.cloudislands.coreservice.role;

import kr.lunaf.cloudislands.api.model.RoleId;
import kr.lunaf.cloudislands.api.model.SystemRole;

public final class CoreRoleKeys {
    public static final String OWNER = SystemRole.OWNER.roleId().value();
    public static final String CO_OWNER = RoleId.of("CO_OWNER").value();
    public static final String MEMBER = RoleId.of("MEMBER").value();
    public static final String TRUSTED = RoleId.of("TRUSTED").value();
    public static final String VISITOR = SystemRole.VISITOR.roleId().value();
    public static final String BANNED = SystemRole.BANNED.roleId().value();

    private CoreRoleKeys() {
    }

    public static String normalize(String roleKey) {
        return IslandRoleRepository.normalizeRoleKey(roleKey);
    }

    public static boolean memberRole(String roleKey) {
        String normalized = normalize(roleKey);
        return !normalized.isBlank() && !normalized.equals(VISITOR) && !normalized.equals(BANNED);
    }
}
