package com.maximde.hologramlib.hook;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import studio.o7.megalodon.Megalodon;
import studio.o7.megalodon.localization.Translation;
import studio.o7.megalodon.player.GamePlayer;


public final class MegalodonIntegration {
    private static Megalodon instance;

    public static void setMegalodon(Megalodon meg) {
        instance = meg;
    }

    public static boolean isPresent() {
        return instance != null;
    }

    public static GamePlayer getGamePlayer(Player player) {
        if (instance == null) return null;
        return instance.getPlayer(player);
    }

    public static Component translate(String key, Player player) {
        GamePlayer gp = getGamePlayer(player);
        if (gp == null) return Component.text("<lang:" + key + ">");
        Translation translation = instance.translations().translation(key);
        return translation.component(gp);
    }
}