package kr.lunaf.cloudislands.paper.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfig;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.message.TranslationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;

class PaperInviteNotificationListenerTest {
    private static final UUID INVITE_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Test
    void actionableInviteMessageRunsCanonicalAcceptDeclineAndListCommands() {
        Component notification = PaperInviteNotificationListener.notification(renderer(), "en_us", invite());
        List<ClickEvent> clicks = clickEvents(notification);

        assertEquals(3, clicks.size());
        assertEquals(ClickEvent.Action.RUN_COMMAND, clicks.get(0).action());
        assertEquals("/is accept " + INVITE_ID, text(clicks.get(0)));
        assertEquals("/is decline " + INVITE_ID, text(clicks.get(1)));
        assertEquals("/is invites", text(clicks.get(2)));
    }

    @Test
    void overflowReminderOpensThePendingInviteMenu() {
        List<ClickEvent> clicks = clickEvents(PaperInviteNotificationListener.moreNotification(renderer(), "en_us", 3));

        assertEquals(1, clicks.size());
        assertEquals("/is invites", text(clicks.get(0)));
    }

    @Test
    void bootstrapRegistersNotificationDeliveryAndAsyncResultsReturnToPaperScheduler() throws Exception {
        String bootstrap = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/PaperPluginBootstrap.java"));
        String listener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/session/PaperInviteNotificationListener.java"));

        assertTrue(bootstrap.contains("new PaperInviteNotificationListener(plugin, client, plugin.messages, plugin.playerLocales)"));
        assertTrue(listener.contains("thenAccept(invites -> PaperSchedulers.run(plugin"));
        assertTrue(listener.contains("plugin.getServer().getPlayer(playerUuid)"));
        assertTrue(listener.contains("\"PENDING\".equalsIgnoreCase(event.state())"));
    }

    private static IslandInviteSnapshot invite() {
        return new IslandInviteSnapshot(
            INVITE_ID,
            UUID.fromString("00000000-0000-0000-0000-000000000012"),
            UUID.fromString("00000000-0000-0000-0000-000000000013"),
            UUID.fromString("00000000-0000-0000-0000-000000000014"),
            "PENDING",
            Instant.parse("2026-07-16T00:00:00Z"),
            Instant.parse("2026-07-17T00:00:00Z")
        );
    }

    private static MessageRenderer renderer() {
        Map<String, String> translations = Map.of(
            "invite-notification", "Invite for {island}",
            "invite-notification-accept", "[Accept]",
            "invite-notification-decline", "[Decline]",
            "invite-notification-view", "[Invites]",
            "invite-notification-more", "{count} more"
        );
        return new MessageRenderer(TranslationManager.fromSnapshot(new PaperRuntimeConfig.Messages("en_us", translations, List.of()), "CloudIslands"));
    }

    private static List<ClickEvent> clickEvents(Component component) {
        ArrayList<ClickEvent> result = new ArrayList<>();
        collect(component, result);
        return result;
    }

    private static void collect(Component component, List<ClickEvent> result) {
        if (component.clickEvent() != null) {
            result.add(component.clickEvent());
        }
        component.children().forEach(child -> collect(child, result));
    }

    private static String text(ClickEvent click) {
        ClickEvent.Payload payload = click.payload();
        return payload instanceof ClickEvent.Payload.Text text ? text.value() : "";
    }
}
