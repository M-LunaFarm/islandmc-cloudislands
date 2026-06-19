plugins { `java-library` }

dependencies {
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes(
            "CloudIslands-API-Read-Policy" to "queries-available-from-every-server-through-core-client-or-cache",
            "CloudIslands-API-Write-Policy" to "writes-go-through-core-api-no-paper-event-direct-db-writes",
            "CloudIslands-API-Event-Coverage" to "pre-create,create,pre-activate,activate,deactivate,migrate,delete,restore,reset,recovery,repair,runtime,pre-visit,visit,invite,member,access,visitor,role,ownership,permission,flag,level,worth,warp,home,biome,upgrade,limit,bank,block-value,mission,snapshot,node,route-ticket,template,addon-state,core-cache,core-reload",
            "CloudIslands-API-Event-Delivery" to "CloudEventMapper-to-CloudIslandsAddon-onCloudEvent-callbacks",
            "CloudIslands-API-Addon-Lifecycle-Callbacks" to "onAddonRegistered,onAddonReloaded,onAddonUnregistered,onCloudEvent",
            "CloudIslands-API-Addon-Feature-Dependency-Syntax" to "feature:required+required",
            "CloudIslands-API-Permission-Keys" to "BUILD,BREAK,INTERACT,OPEN_CONTAINER,USE_DOOR,USE_BUTTON,USE_PRESSURE_PLATE,USE_REDSTONE,PLACE_LIQUID,BREAK_LIQUID,ATTACK_PLAYER,ATTACK_MOB,PICKUP_ITEM,DROP_ITEM,USE_SPAWNER,USE_ANVIL,USE_ENCHANT_TABLE,USE_BREWING_STAND,MANAGE_MEMBERS,MANAGE_ROLES,MANAGE_FLAGS,MANAGE_WARPS,MANAGE_UPGRADES,START_LEVEL_CALC,BAN_VISITOR,KICK_VISITOR,SET_HOME,SET_BIOME,WITHDRAW_BANK,DEPOSIT_BANK",
            "CloudIslands-API-Flag-Keys" to "PVP,MOB_SPAWN,ANIMAL_SPAWN,MONSTER_SPAWN,FIRE_SPREAD,EXPLOSION,CREEPER_DAMAGE,TNT_DAMAGE,WITHER_DAMAGE,ENDERMAN_GRIEF,WATER_FLOW,LAVA_FLOW,ICE_MELT,LEAF_DECAY,VISITOR_INTERACT,VISITOR_CONTAINER,VISITOR_PICKUP,VISITOR_DROP,VISITOR_PVP,FLY,KEEP_INVENTORY,PUBLIC_WARPS",
            "CloudIslands-API-Permission-Decision-Order" to "admin-bypass,owner,explicit-role,trusted,visitor-flags,default-deny",
            "CloudIslands-API-Upgrade-Types" to "ISLAND_SIZE,MAX_MEMBERS,MAX_WARPS,HOPPER_LIMIT,SPAWNER_LIMIT,GENERATOR_LEVEL,MOB_LIMIT,CROP_GROWTH,FLY_ACCESS,REDSTONE_LIMIT,BANK_LIMIT",
            "CloudIslands-API-Economy-Bridge" to "withdraw,deposit,balance-with-uuid-amount-reason-contract",
            "CloudIslands-API-Addon-State-Bulk" to "table-and-table-key-value-bulk-save-contracts",
            "CloudIslands-API-Satis-Integration" to "external-addon-or-built-in-compatible-cloudislands-state-authority"
        )
    }
}
