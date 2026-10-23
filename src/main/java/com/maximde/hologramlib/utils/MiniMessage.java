package com.maximde.hologramlib.utils;

import net.kyori.adventure.text.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MiniMessage {
    private static final net.kyori.adventure.text.minimessage.MiniMessage minimessage = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
    private static final int MAX_CACHED_TEXTS = 4096;
    // packet listeners see the same hologram text in every metadata packet, so each text is serialized once
    private static final Map<Component, String> SERIALIZED = new ConcurrentHashMap<>();

    public static Component get(String message) {
        return minimessage.deserialize(message);
    }

    public static String stripTags(String message) {
        return minimessage.stripTags(message);
    }

    public static String serialize(Component component) {
        var cached = SERIALIZED.get(component);
        if (cached != null) return cached;

        if (SERIALIZED.size() >= MAX_CACHED_TEXTS) SERIALIZED.clear();
        var serialized = minimessage.serialize(component);
        SERIALIZED.put(component, serialized);
        return serialized;
    }
}
