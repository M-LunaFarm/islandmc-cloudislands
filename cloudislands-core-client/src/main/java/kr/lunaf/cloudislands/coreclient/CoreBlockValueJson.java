package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class CoreBlockValueJson {
    private CoreBlockValueJson() {
    }

    static List<BlockValueView> values(String body) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        return SimpleJson.list(root.get("values")).stream()
            .map(SimpleJson::object)
            .filter(object -> !object.isEmpty())
            .map(CoreBlockValueJson::value)
            .filter(value -> !value.materialKey().isBlank())
            .toList();
    }

    static BlockValueActionView action(String body, String materialKey) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("ok"))
            && !Boolean.FALSE.equals(root.get("applied"));
        String code = SimpleJson.text(root.get("code"));
        if (!code.isBlank() && !code.equals("BLOCK_VALUE_SET")) {
            accepted = false;
        }
        return new BlockValueActionView(accepted, code.isBlank() ? (accepted ? "BLOCK_VALUE_SET" : "FAILED") : code, materialKey);
    }

    private static BlockValueView value(Map<?, ?> object) {
        return new BlockValueView(
            SimpleJson.text(object.get("materialKey")),
            SimpleJson.text(object.get("worth")),
            SimpleJson.number(object.get("levelPoints")),
            SimpleJson.number(object.get("limit"))
        );
    }
}
