package kr.lunaf.cloudislands.coreservice.security.permission;

import java.util.EnumSet;
import java.util.Set;

public final class AdminPermissionPolicy {
    private final Set<AdminPermission> allowed;

    public AdminPermissionPolicy(Set<AdminPermission> allowed) {
        this.allowed = EnumSet.copyOf(allowed);
    }

    public static AdminPermissionPolicy all() {
        return new AdminPermissionPolicy(EnumSet.allOf(AdminPermission.class));
    }

    public boolean allows(AdminPermission permission) {
        return allowed.contains(permission);
    }
}
