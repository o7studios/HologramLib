package com.maximde.hologramlib.listener;

import com.maximde.hologramlib.hologram.Hologram;
import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.hologram.TextHologram;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import studio.o7.megalodon.events.GamePlayerLangChangeEvent;
import studio.o7.megalodon.player.GamePlayer;

import java.util.UUID;

public class LanguageChangeListener implements Listener {

    private final HologramManager hologramManager;

    public LanguageChangeListener(HologramManager hologramManager) {
        this.hologramManager = hologramManager;
    }

    @EventHandler
    public void onLanguageChange(GamePlayerLangChangeEvent event) {
        GamePlayer player = event.getPlayer();
        if (player == null || !player.isOnline()) return;

        UUID playerId = player.getUniqueId();

        for (Hologram<?> hologram : hologramManager.getHolograms()) {
            if (hologram instanceof TextHologram textHologram &&
                    textHologram.getViewerUUIDs().contains(playerId)) {
                textHologram.update();
            }
        }
    }
}