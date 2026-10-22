package com.maximde.hologramlib.hologram;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A hologram text and its MiniMessage form. The packet listeners serialized every text display on every
 * metadata packet to every viewer just to look for placeholders, while the text almost never changes.
 */
public final class SerializedText {

    private record Entry(Component component, String serialized) {}

    private volatile @Nullable Entry entry;

    public String of(Component component) {
        Entry current = entry;
        if (current != null && current.component().equals(component)) return current.serialized();
        String serialized = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(component);
        entry = new Entry(component, serialized);
        return serialized;
    }
}
