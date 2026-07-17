package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import kr.lunaf.cloudislands.coreclient.TemplateView;
import org.junit.jupiter.api.Test;

class AdminTemplateMenuTest {
    @Test
    void templatesSortByOperatorOrderThenDisplayName() {
        TemplateView later = template("later", "Alpha", true, 20, "templates/later.tar");
        TemplateView second = template("second", "Zulu", false, 10, "");
        TemplateView first = template("first", "Alpha", true, 10, "templates/first.tar");

        assertEquals(List.of(first, second, later), AdminTemplateMenu.sortedTemplates(List.of(later, second, first)));
        assertEquals(List.of(), AdminTemplateMenu.sortedTemplates(null));
    }

    @Test
    void templateLoreShowsOperationalMetadataAndSanitizesCatalogText() {
        TemplateView template = template("starter\nunsafe", "Starter", true, 7, "templates/starter.tar");

        List<String> lore = AdminTemplateMenu.templateLore(template, null);

        assertTrue(lore.stream().noneMatch(line -> line.contains("\n") || line.contains("\r")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("ID: starter unsafe")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("상태: 활성")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("스키마/크기: 3/256")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("필요 권한: cloudislands.template.premium")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("생성 비용: 25")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("번들: 준비됨")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("정렬 순서: 7")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("비활성화 확인")));
    }

    private static TemplateView template(String id, String displayName, boolean enabled, int sortOrder, String bundlePath) {
        return new TemplateView(
            id,
            displayName,
            "description",
            "premium",
            enabled,
            "1.21.11",
            "cloudislands.template.premium",
            "GRASS_BLOCK",
            0,
            "preview/starter.png",
            bundlePath,
            "sha256:abc",
            4096L,
            3,
            256,
            0.5D,
            100.0D,
            0.5D,
            180.0F,
            0.0F,
            "default",
            "normal",
            "minecraft:plains",
            "BLUE",
            "0",
            "25",
            sortOrder,
            List.of("starter")
        );
    }
}
