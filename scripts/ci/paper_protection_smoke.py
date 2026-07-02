#!/usr/bin/env python3
import json
import sys
from pathlib import Path


REQUIRED_LISTENER_MARKERS = {
    "block_break": ["BlockBreakEvent", "IslandPermission.BREAK"],
    "block_place": ["BlockPlaceEvent", "IslandPermission.BUILD"],
    "interact": ["PlayerInteractEvent", "interactionPermission"],
    "container_open": ["InventoryOpenEvent", "IslandPermission.OPEN_CONTAINER"],
    "hopper_transfer": ["InventoryMoveItemEvent", "sameIsland"],
    "bucket_empty_fill": ["PlayerBucketEmptyEvent", "PlayerBucketFillEvent", "PLACE_LIQUID", "BREAK_LIQUID"],
    "lava_water_spread": ["BlockFromToEvent", "LAVA_FLOW", "WATER_FLOW"],
    "fire_spread": ["BlockIgniteEvent", "BlockBurnEvent", "FIRE_SPREAD"],
    "explosion": ["EntityExplodeEvent", "BlockExplodeEvent", "explosionAllowed"],
    "entity_damage": ["EntityDamageByEntityEvent", "ATTACK_PLAYER", "ATTACK_MOB"],
    "item_frame_armor_stand": ["HangingPlaceEvent", "PlayerArmorStandManipulateEvent"],
    "redstone_piston": ["BlockPistonExtendEvent", "BlockPistonRetractEvent", "USE_REDSTONE"],
    "audit_event": ["IslandPermissionCheckEvent", "PaperEvents.call"],
    "deny_message": ["sendDenyMessage", "sendActionBar"],
}

REQUIRED_ROLE_MARKERS = [
    "protectionSmokeMatrixCoversOwnerMemberTrustedVisitorBannedAndAdminBypass",
    "IslandRole.OWNER",
    "IslandRole.MEMBER",
    "IslandRole.TRUSTED",
    "IslandRole.BANNED",
    "admin bypass",
]

REQUIRED_BOOT_MARKERS = [
    "papermc_smoke.py",
    "ciBootSmoke",
]


def contains_all(source: str, markers: list[str]) -> bool:
    return all(marker in source for marker in markers)


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    listener = (root / "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/IslandProtectionListener.java").read_text(encoding="utf-8")
    test = (root / "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/ProtectionControllerTest.java").read_text(encoding="utf-8")
    build = (root / "build.gradle.kts").read_text(encoding="utf-8")

    failures = []
    passed = {}
    for scenario, markers in REQUIRED_LISTENER_MARKERS.items():
        ok = contains_all(listener, markers)
        passed[scenario] = ok
        if not ok:
            missing = [marker for marker in markers if marker not in listener]
            failures.append(f"{scenario}: missing listener markers {', '.join(missing)}")

    missing_roles = [marker for marker in REQUIRED_ROLE_MARKERS if marker not in test]
    if missing_roles:
        failures.append(f"role_matrix: missing test markers {', '.join(missing_roles)}")

    missing_boot = [marker for marker in REQUIRED_BOOT_MARKERS if marker not in build]
    if missing_boot:
        failures.append(f"boot_smoke_link: missing Gradle markers {', '.join(missing_boot)}")

    report = {
        "scenario": "paper-protection-smoke",
        "passed": not failures,
        "listenerScenarios": passed,
        "roleMatrixVerified": not missing_roles,
        "bootSmokeLinked": not missing_boot,
        "failures": failures,
    }
    print(json.dumps(report, indent=2, sort_keys=True))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
