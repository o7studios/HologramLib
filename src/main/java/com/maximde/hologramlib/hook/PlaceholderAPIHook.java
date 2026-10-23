package com.maximde.hologramlib.hook;

import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.maximde.hologramlib.utils.MiniMessage;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PlaceholderAPIHook implements PacketListener {

    private static final int TEXT_DISPLAY_TEXT_INDEX = 23;

    public PlaceholderAPIHook(PacketEventsAPI<?> packetEventsAPI) {
        packetEventsAPI.getEventManager().registerListener(this, PacketListenerPriority.NORMAL);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) return;

        WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
        Player player = event.getPlayer();
        if (player == null) return;

        List<EntityData<?>> newMetadata = new ArrayList<>();
        boolean modified = false;

        for (EntityData<?> data : packet.getEntityMetadata()) {
            if (data.getIndex() == TEXT_DISPLAY_TEXT_INDEX && data.getType() == EntityDataTypes.ADV_COMPONENT) {
                Component component = (Component) data.getValue();
                String serialized = MiniMessage.serialize(component);
                if (serialized.contains("%")) {
                    String parsed = PlaceholderAPI.setPlaceholders(player, serialized);
                    if (!serialized.equals(parsed)) {
                        component = MiniMessage.get(parsed);
                        newMetadata.add(new EntityData<>(TEXT_DISPLAY_TEXT_INDEX, EntityDataTypes.ADV_COMPONENT, component));
                        modified = true;
                        continue;
                    }
                }
                newMetadata.add(data);
            } else {
                newMetadata.add(data);
            }
        }

        if (modified) {
            packet.setEntityMetadata(newMetadata);
        }
    }
}