package com.maximde.hologramlib.utils;

import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import me.tofaa.entitylib.EntityIdProvider;
import org.bukkit.Bukkit;
import org.bukkit.UnsafeValues;
import org.bukkit.World;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;

/**
 * Takes entity ids from the server's own counter, so packet entities never share an id with a real one. Paper
 * 26.2 wants a world for it, EntityLib only knows the older {@code nextEntityId()} without one.
 */
public final class ServerEntityIdProvider implements EntityIdProvider {

    private final MethodHandle nextEntityId;

    public ServerEntityIdProvider() throws ReflectiveOperationException {
        this.nextEntityId = MethodHandles.publicLookup()
                .findVirtual(UnsafeValues.class, "nextEntityId", MethodType.methodType(int.class, World.class))
                .bindTo(Bukkit.getUnsafe());
    }

    @Override
    public int provide(UUID uuid, EntityType entityType) {
        try {
            return (int) nextEntityId.invoke(Bukkit.getWorlds().getFirst());
        } catch (Throwable e) {
            throw new IllegalStateException("Could not take an entity id from the server", e);
        }
    }
}
