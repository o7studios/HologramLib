package com.maximde.hologramlib.listener;

import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.utils.BukkitTasks;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

@RequiredArgsConstructor
public class PlayerSyncListener implements Listener {

    private final HologramManager hologramManager;

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        BukkitTasks.runTaskLater(() -> resync(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        BukkitTasks.runTaskLater(() -> resync(event.getPlayer()), 2L);
    }

    private void resync(Player player) {
        if (!player.isOnline()) return;

        hologramManager.getHolograms().forEach(hologram -> {
            if (!hologram.hasViewer(player.getUniqueId())) return;
            if (hologram.getBlacklistedViewers().contains(player)) return;
            hologram.removeViewer(player);
            hologram.addViewer(player);
        });

        hologramManager.getInteractionBoxes().forEach(box -> {
            if (!box.hasViewer(player.getUniqueId())) return;
            box.removeViewer(player);
            box.addViewer(player);
        });
    }
}
