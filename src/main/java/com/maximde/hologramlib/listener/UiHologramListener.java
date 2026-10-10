package com.maximde.hologramlib.listener;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.hologram.UiHologram;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Puts each viewer's own frame into the text of {@link UiHologram}s, whose entity only carries an empty one.
 * Runs last, so the text other listeners see stays small and free of placeholders.
 */
@RequiredArgsConstructor
public class UiHologramListener implements PacketListener {

    private final HologramManager hologramManager;

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) return;
        Player player = event.getPlayer();
        if (player == null) return;

        var packet = new WrapperPlayServerEntityMetadata(event);
        if (!(hologramManager.getHologramByEntityId(packet.getEntityId()).orElse(null) instanceof UiHologram hologram)) return;
        if (packet.getEntityMetadata().stream().noneMatch(data -> data.getIndex() == UiHologram.TEXT_INDEX)) return;

        List<EntityData<?>> metadata = packet.getEntityMetadata().stream()
                .<EntityData<?>>map(data -> data.getIndex() == UiHologram.TEXT_INDEX
                        ? new EntityData<>(UiHologram.TEXT_INDEX, EntityDataTypes.ADV_COMPONENT, hologram.textFor(player))
                        : data)
                .toList();
        packet.setEntityMetadata(metadata);
        event.markForReEncode(true);
    }
}
