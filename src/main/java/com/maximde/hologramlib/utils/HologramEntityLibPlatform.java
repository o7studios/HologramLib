package com.maximde.hologramlib.utils;

import me.tofaa.entitylib.APIConfig;
import me.tofaa.entitylib.spigot.SpigotEntityLibPlatform;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * EntityLib's platform, finishing its setup with {@link ServerEntityIdProvider} where EntityLib's own id provider
 * no longer links. It fails after the API is created, so only the steps after it have to be repeated here.
 */
public final class HologramEntityLibPlatform extends SpigotEntityLibPlatform {

    public HologramEntityLibPlatform(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public void setupApi(APIConfig settings) {
        try {
            super.setupApi(settings);
            return;
        } catch (NoSuchMethodError e) {
            // Paper 26.2 removed UnsafeValues#nextEntityId(), which EntityLib's provider calls.
        }
        try {
            setEntityIdProvider(new ServerEntityIdProvider());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("The server has no entity id counter HologramLib knows", e);
        }
        getAPI().onLoad();
        getAPI().onEnable();
    }
}
