package kr.lunaf.cloudislands.migration.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class Ss2IslandDocumentParserTest {
    @Test
    void jsonParserFlattensNestedScalarsAndScalarLists() {
        ParsedIslandDocument document = new Ss2JsonIslandParser().parse("""
            {
              "owner": {
                "uuid": "00000000-0000-0000-0000-000000002002"
              },
              "members": [
                "00000000-0000-0000-0000-000000002003"
              ],
              "homeWorld": "sky\\"world"
            }
            """);

        assertEquals("00000000-0000-0000-0000-000000002002", document.value("owner.uuid"));
        assertEquals(List.of("00000000-0000-0000-0000-000000002003"), document.list("members"));
        assertEquals("sky\"world", document.value("homeWorld"));
    }

    @Test
    void yamlParserFlattensNestedScalarsListsAndComments() {
        ParsedIslandDocument document = new Ss2YamlIslandParser().parse("""
            owner:
              uuid: "00000000-0000-0000-0000-000000002002" # retained without comment
            members:
              - "00000000-0000-0000-0000-000000002003"
            completedMissions: [farm_master, miner_one]
            quoted: "value # not a comment"
            """);

        assertEquals("00000000-0000-0000-0000-000000002002", document.value("owner.uuid"));
        assertEquals(List.of("00000000-0000-0000-0000-000000002003"), document.list("members"));
        assertEquals(List.of("farm_master", "miner_one"), document.list("completedMissions"));
        assertEquals("value # not a comment", document.value("quoted"));
        assertTrue(document.hasKey("owner.uuid"));
    }
}
