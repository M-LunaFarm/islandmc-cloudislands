package kr.lunaf.cloudislands.common.permission;

import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.common.protection.ProtectionDecisionPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandPermissionSystemPolicyTest {
    @Test
    void pinsGoalRoleSurface() {
        for (String roleKey : List.of(
                "OWNER",
                "CO_OWNER",
                "MODERATOR",
                "MEMBER",
                "TRUSTED",
                "VISITOR",
                "BANNED"
        )) {
            assertTrue(IslandPermissionSystemPolicy.isBaseRoleKey(roleKey), roleKey);
            assertTrue(IslandPermissionSystemPolicy.isBaseRoleKey(roleKey.toLowerCase(java.util.Locale.ROOT)), roleKey);
        }
        assertEquals(7, IslandPermissionSystemPolicy.baseRoleKeys().size());
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyBaseRoleAdaptersRemainCompatible() {
        assertTrue(IslandPermissionSystemPolicy.isBaseRole(IslandRole.OWNER));
        assertTrue(IslandPermissionSystemPolicy.baseRoles().contains(IslandRole.BANNED));
    }

    @Test
    void pinsGoalPermissionKeys() {
        for (IslandPermission permission : IslandPermission.values()) {
            assertTrue(IslandPermissionSystemPolicy.isBasePermission(permission), permission.name());
        }
    }

    @Test
    void pinsGoalFlagKeys() {
        for (IslandFlag flag : IslandFlag.values()) {
            assertTrue(IslandPermissionSystemPolicy.isBaseFlag(flag), flag.name());
        }
    }

    @Test
    void pinsDecisionOrderToProtectionHotPathContract() {
        assertEquals(
                List.of("admin-bypass", "island-owner", "explicit-member-role", "trusted-override", "visitor-flags", "default-deny"),
                IslandPermissionSystemPolicy.decisionSteps()
        );
        assertEquals(ProtectionDecisionPolicy.DECISION_ORDER, IslandPermissionSystemPolicy.DECISION_ORDER);
        assertEquals("region-index-and-local-permission-cache-only", ProtectionDecisionPolicy.HOT_PATH_POLICY);
        assertEquals("DENY_SYNC_IO", ProtectionDecisionPolicy.syncSourceDecision("database"));
        assertEquals("DENY_SYNC_IO", ProtectionDecisionPolicy.syncSourceDecision("core-api-http"));
        assertEquals("DENY_SYNC_IO", ProtectionDecisionPolicy.syncSourceDecision("redis"));
        assertEquals("ALLOW_LOCAL_CACHE", ProtectionDecisionPolicy.syncSourceDecision("local-permission-cache"));
    }
}
