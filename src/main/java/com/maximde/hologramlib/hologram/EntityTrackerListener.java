package com.maximde.hologramlib.hologram;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.maximde.hologramlib.HologramLib;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps attached holograms in sync with the vanilla entity tracker per viewer.
 * <p>
 * The server destroys entities that leave a player's tracking range and re-spawns
 * them when they come back. Attached holograms are packet-only passengers, so they
 * must only ever exist client-side while their vehicle does: a hologram spawned for
 * a viewer whose client does not track the vehicle cannot be mounted (the client
 * drops the passengers packet) and would float detached at its spawn position.
 * <p>
 * This listener therefore maintains a per-viewer set of entity ids the client
 * currently knows (from SPAWN/DESTROY/RESPAWN/JOIN packets). {@link Hologram#addViewer}
 * consults it via {@link #isTracking} and defers the client-side spawn when the
 * vehicle is unknown; once the vehicle's spawn packet goes out, the branch below
 * (re-)spawns and mounts every attached hologram within the same channel flush, so
 * the client never renders an unmounted frame. When the vehicle is destroyed for a
 * viewer, its holograms are destroyed client-side too (the plugin-side viewer list
 * is left untouched so the next spawn re-syncs them).
 */
public class EntityTrackerListener implements PacketListener {

    /** Entity ids each viewer's client currently has loaded. */
    private static final Map<UUID, Set<Integer>> trackedEntities = new ConcurrentHashMap<>();

    public static boolean isTracking(UUID viewer, int entityId) {
        Set<Integer> tracked = trackedEntities.get(viewer);
        if (tracked != null && tracked.contains(entityId)) return true;
        // A client never receives a spawn packet for its own entity — it learns that id from
        // JOIN_GAME, and the branch below drops the whole set on JOIN_GAME/RESPAWN — so a
        // viewer's own id is never in the map even though their client always knows it.
        // Without this, every hologram attached to a player is silently invisible to that
        // same player, no matter what the caller's viewer rules say.
        Player self = Bukkit.getPlayer(viewer);
        return self != null && self.getEntityId() == entityId;
    }

    public static void clear(UUID viewer) {
        trackedEntities.remove(viewer);
    }

    @Override
    public void onPacketSend(@NotNull PacketSendEvent event) {
        PacketTypeCommon type = event.getPacketType();

        if (type == PacketType.Play.Server.SPAWN_ENTITY || type == PacketType.Play.Server.SPAWN_PLAYER) {
            int entityId = type == PacketType.Play.Server.SPAWN_ENTITY
                    ? new WrapperPlayServerSpawnEntity(event).getEntityId()
                    : new WrapperPlayServerSpawnPlayer(event).getEntityId();
            UUID viewerId = event.getUser().getUUID();
            if (viewerId == null) return;
            trackedEntities.computeIfAbsent(viewerId, k -> ConcurrentHashMap.newKeySet()).add(entityId);

            Set<Hologram<?>> attached = PassengerManager.getAttachments(entityId);
            if (attached.isEmpty()) return;
            if (!(event.getPlayer() instanceof Player player)) return;
            event.getTasksAfterSend().add(() -> {
                boolean resynced = false;
                for (Hologram<?> hologram : attached) {
                    if (hologram.isDead() || !hologram.hasViewer(viewerId)) continue;
                    // Destroy + spawn instead of a bare spawn so this also heals the
                    // case where the client silently dropped the display entity.
                    hologram.entity.removeViewer(viewerId);
                    hologram.entity.addViewer(viewerId);
                    resynced = true;
                }
                if (resynced) {
                    HologramLib.getPlayerManager().sendPacket(player, new WrapperPlayServerSetPassengers(
                            entityId, PassengerManager.getMergedPassengers(entityId)));
                }
            });
            return;
        }

        if (type == PacketType.Play.Server.DESTROY_ENTITIES) {
            WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(event);
            UUID viewerId = event.getUser().getUUID();
            if (viewerId == null) return;
            Set<Integer> tracked = trackedEntities.get(viewerId);
            if (tracked != null) {
                for (int destroyedId : packet.getEntityIds()) tracked.remove(destroyedId);
            }

            if (!PassengerManager.hasAttachments()) return;
            if (!(event.getPlayer() instanceof Player player)) return;
            List<Integer> hologramIds = new ArrayList<>(0);
            for (int destroyedId : packet.getEntityIds()) {
                for (Hologram<?> hologram : PassengerManager.getAttachments(destroyedId)) {
                    if (!hologram.isDead() && hologram.entity.hasViewer(viewerId)) {
                        hologramIds.add(hologram.getEntityID());
                    }
                }
            }
            if (hologramIds.isEmpty()) return;
            int[] ids = new int[hologramIds.size()];
            for (int i = 0; i < ids.length; i++) ids[i] = hologramIds.get(i);
            // Only a client-side destroy: the EntityLib viewer set keeps the player so
            // the spawn branch above restores everything on re-track.
            event.getTasksAfterSend().add(() ->
                    HologramLib.getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(ids)));
            return;
        }

        // The client discards all entities when switching worlds / respawning and on
        // (re-)login; the tracker re-spawns what is relevant afterwards.
        if (type == PacketType.Play.Server.RESPAWN || type == PacketType.Play.Server.JOIN_GAME) {
            UUID viewerId = event.getUser().getUUID();
            if (viewerId != null) trackedEntities.remove(viewerId);
        }
    }
}
