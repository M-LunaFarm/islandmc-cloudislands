package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;

final class CoreBlockValueJson {
    private CoreBlockValueJson() {
    }

    static List<BlockValueView> values(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return CoreJson.objects(root, "values").stream()
            .map(CoreBlockValueJson::value)
            .filter(value -> !value.materialKey().isBlank())
            .toList();
    }

    static List<BlockValueView> values(CoreResponseBody body) {
        return values(body.value());
    }

    static BlockValueActionView action(String body, String materialKey) {
        Map<?, ?> root = CoreJson.object(body);
        boolean accepted = CoreJson.acceptedWithCode(root, "BLOCK_VALUE_SET");
        return new BlockValueActionView(accepted, CoreJson.code(root, "BLOCK_VALUE_SET", accepted), materialKey);
    }

    static BlockValueActionView action(CoreResponseBody body, String materialKey) {
        return action(body.value(), materialKey);
    }

    private static BlockValueView value(Map<?, ?> object) {
        return new BlockValueView(
            CoreJson.text(object, "materialKey"),
            CoreJson.text(object, "worth"),
            CoreJson.number(object, "levelPoints"),
            CoreJson.number(object, "limit")
        );
    }
}
