package com.maximde.hologramlib.listener;

import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.hologram.PassengerManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

@RequiredArgsConstructor
public class PlayerQuitListener implements Listener {

    private final HologramManager hologramManager;

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PassengerManager.remove(event.getPlayer().getEntityId());
        com.maximde.hologramlib.hologram.EntityTrackerListener.clear(event.getPlayer().getUniqueId());
        hologramManager.getEventHandlers().forEach(handler ->
                handler.onQuit(event.getPlayer()));
    }

}

