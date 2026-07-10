package kr.lunaf.cloudislands.coreservice.role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoreRoleKeysTest {
    @Test
    void normalizesBuiltInAndCustomRoleKeys() {
        assertEquals("CO_OWNER", CoreRoleKeys.normalize(" co-owner "));
        assertEquals("BUILDER", CoreRoleKeys.normalize("builder"));
    }

    @Test
    void treatsEveryNonVisitorRoleKeyAsAnIslandMemberRole() {
        assertTrue(CoreRoleKeys.memberRole(CoreRoleKeys.OWNER));
        assertTrue(CoreRoleKeys.memberRole(CoreRoleKeys.MEMBER));
        assertTrue(CoreRoleKeys.memberRole("BUILDER"));
        assertFalse(CoreRoleKeys.memberRole(CoreRoleKeys.VISITOR));
        assertFalse(CoreRoleKeys.memberRole(CoreRoleKeys.BANNED));
        assertFalse(CoreRoleKeys.memberRole("  "));
        assertFalse(CoreRoleKeys.memberRole(null));
    }
}
