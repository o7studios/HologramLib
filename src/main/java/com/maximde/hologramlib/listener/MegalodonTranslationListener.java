package com.maximde.hologramlib.listener;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.maximde.hologramlib.HologramLib;
import com.maximde.hologramlib.hologram.Hologram;
import com.maximde.hologramlib.hologram.TextHologram;
import com.maximde.hologramlib.hook.MegalodonIntegration;
import com.maximde.hologramlib.utils.MiniMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MegalodonTranslationListener implements PacketListener {

    private static final int TEXT_DISPLAY_TEXT_INDEX = 23;
    private static final Pattern LANG_TAG_PATTERN = Pattern.compile("<lang:([^>]+)>");

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) return;
        if (!MegalodonIntegration.isPresent()) return;

        WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
        Player player = event.getPlayer();
        int entityId = packet.getEntityId();

        Optional<Hologram<?>> opt = HologramLib.getManager().flatMap(m -> m.getHologramByEntityId(entityId));
        if (opt.isEmpty() || !(opt.get() instanceof TextHologram textHologram)) return;

        List<EntityData<?>> newMetadata = new ArrayList<>();
        boolean modified = false;

        for (EntityData<?> data : packet.getEntityMetadata()) {
            if (data.getIndex() == TEXT_DISPLAY_TEXT_INDEX && data.getType() == EntityDataTypes.ADV_COMPONENT) {
                Component finalComponent = null;

                if (textHologram.getPerPlayerTextProvider() != null) {
                    finalComponent = textHologram.getPerPlayerTextProvider().apply(player);
                } else {
                    Component original = (Component) data.getValue();
                    finalComponent = replaceLangTags(original, player);
                }

                if (finalComponent != null) {
                    newMetadata.add(new EntityData<>(TEXT_DISPLAY_TEXT_INDEX, EntityDataTypes.ADV_COMPONENT, finalComponent));
                    modified = true;
                } else {
                    newMetadata.add(data);
                }
            } else {
                newMetadata.add(data);
            }
        }

        if (modified) {
            packet.setEntityMetadata(newMetadata);
        }
    }

    private Component replaceLangTags(Component component, Player player) {
        String raw = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(component);
        if (!raw.contains("<lang:")) return component;

        Matcher matcher = LANG_TAG_PATTERN.matcher(raw);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Component replacement = MegalodonIntegration.translate(key, player);
            String serialized = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(replacement);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(serialized));
        }
        matcher.appendTail(sb);
        return MiniMessage.get(sb.toString());
    }
}