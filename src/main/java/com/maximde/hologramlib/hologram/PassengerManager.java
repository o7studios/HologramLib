package com.maximde.hologramlib.hologram;

import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PassengerManager implements PacketListener {

    private static final Map<Integer, int[]> passengers = new ConcurrentHashMap<>();

    /**
     * Vehicle entity id -> holograms currently attached to it. Kept separate from the
     * plain passenger-id snapshot above so the merged passenger list can always be
     * rebuilt even after a vanilla SET_PASSENGERS overwrote the snapshot with only
     * its own (non-hologram) passengers.
     */
    private static final Map<Integer, Set<Hologram<?>>> attachments = new ConcurrentHashMap<>();

    public PassengerManager(PacketEventsAPI<?> packetEventsAPI) {
        packetEventsAPI.getEventManager().registerListener(this, PacketListenerPriority.NORMAL);
    }

    @Override
    public void onPacketSend(@NotNull PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.SET_PASSENGERS) return;
        WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
        passengers.put(packet.getEntityId(), packet.getPassengers());
    }

    public static int[] getPassengers(int entityId) {
        return passengers.getOrDefault(entityId, new int[0]);
    }

    public static void setPassengers(int entityId, int[] passengerIds) {
        passengers.put(entityId, passengerIds);
    }

    public static void remove(int entityId) {
        passengers.remove(entityId);
        attachments.remove(entityId);
    }

    static void registerAttachment(int entityId, Hologram<?> hologram) {
        attachments.computeIfAbsent(entityId, k -> ConcurrentHashMap.newKeySet()).add(hologram);
    }

    static void unregisterAttachment(int entityId, Hologram<?> hologram) {
        attachments.computeIfPresent(entityId, (k, set) -> {
            set.remove(hologram);
            return set.isEmpty() ? null : set;
        });
    }

    static Set<Hologram<?>> getAttachments(int entityId) {
        return attachments.getOrDefault(entityId, Collections.emptySet());
    }

    static boolean hasAttachments() {
        return !attachments.isEmpty();
    }

    /**
     * Last known passenger snapshot for the entity plus the entity ids of every
     * hologram attached to it. This is the list that should be sent to clients so
     * one hologram's packet never dismounts its sibling holograms.
     */
    public static int[] getMergedPassengers(int entityId) {
        int[] base = getPassengers(entityId);
        Set<Hologram<?>> attached = attachments.get(entityId);
        if (attached == null || attached.isEmpty()) return base;
        Set<Integer> merged = new LinkedHashSet<>();
        for (int id : base) merged.add(id);
        for (Hologram<?> hologram : attached) {
            if (!hologram.isDead()) merged.add(hologram.getEntityID());
        }
        int[] result = new int[merged.size()];
        int i = 0;
        for (int id : merged) result[i++] = id;
        return result;
    }
}
