package kr.lunaf.cloudislands.paper.session;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PaperBrandingListener implements Listener {
    private final Component header;
    private final Component footer;

    public PaperBrandingListener(String serviceName) {
        String name = serviceName == null || serviceName.isBlank() ? "CloudIslands" : serviceName;
        this.header = Component.text(name);
        this.footer = Component.text("섬을 준비하고 있습니다. /섬 으로 이동과 관리를 시작하세요.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().sendPlayerListHeaderAndFooter(header, footer);
        event.joinMessage(Component.text(event.getPlayer().getName() + "님이 섬 서비스에 접속했습니다."));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(Component.text(event.getPlayer().getName() + "님이 섬 서비스에서 나갔습니다."));
    }
}
