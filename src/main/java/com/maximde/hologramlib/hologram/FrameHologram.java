package com.maximde.hologramlib.hologram;

import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Quaternion4f;
import me.tofaa.entitylib.meta.EntityMeta;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Display;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A text display whose whole text is replaced many times a second, like the frames of a video. Unlike
 * {@link TextHologram} a frame is neither parsed nor serialized here: {@link #showFrame} hands the component
 * straight to the entity, which sends viewers one metadata packet holding only the text. Safe from any thread.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class FrameHologram extends Hologram<FrameHologram> {

    /** Frames are laid out to the pixel, the display must never wrap them. */
    private static final int NO_WRAP = 1 << 14;
    private static final int FULL_BRIGHT = 15;
    private static final float PIXEL = 0.025f;

    private volatile Component frame = Component.empty();
    private int frameWidth;
    private int frameHeight;

    public FrameHologram(String id) {
        this(id, RenderMode.ALL);
    }

    public FrameHologram(String id, RenderMode renderMode) {
        super(id, renderMode, EntityTypes.TEXT_DISPLAY);
        this.billboard = Display.Billboard.FIXED;
        // Frames carry their own colors, which the light around the display should not darken.
        setBrightness(FULL_BRIGHT, FULL_BRIGHT);
    }

    public FrameHologram showFrame(Component text) {
        frame = text;
        ((TextDisplayMeta) entity.getEntityMeta()).setText(text);
        return this;
    }

    /** The size of the frames in text pixels, for the hologram's bounds. */
    public FrameHologram setFrameSize(int width, int height) {
        frameWidth = width;
        frameHeight = height;
        return this;
    }

    @Override
    protected EntityMeta applyMeta() {
        var meta = (TextDisplayMeta) entity.getEntityMeta();
        meta.setText(frame);
        meta.setInterpolationDelay(-1);
        meta.setTransformationInterpolationDuration(interpolationDurationTransformation);
        meta.setPositionRotationInterpolationDuration(teleportDuration);
        meta.setTranslation(toVector3f(translation));
        meta.setLeftRotation(leftRotation);
        meta.setRightRotation(rightRotation);
        meta.setScale(toVector3f(scale));
        meta.setBillboardConstraints(AbstractDisplayMeta.BillboardConstraints.valueOf(billboard.name()));
        meta.setLineWidth(NO_WRAP);
        meta.setViewRange((float) viewRange);
        meta.setGlowing(glowing);
        meta.setGlowColorOverride(glowColor);
        meta.setBackgroundColor(0);
        meta.setShadow(false);
        meta.setInvisible(isInvisible);
        if (brightness > -1) meta.setBrightnessOverride(brightness);
        return meta;
    }

    @Override
    int getHeight() {
        return Math.max(1, (int) Math.ceil(frameHeight * PIXEL * scale.y));
    }

    @Override
    int getWidth() {
        return Math.max(1, (int) Math.ceil(frameWidth * PIXEL * Math.max(scale.x, scale.z)));
    }

    @Override
    protected FrameHologram copy() {
        return copy(id + "_copy_" + ThreadLocalRandom.current().nextInt(100000));
    }

    @Override
    public FrameHologram copy(String id) {
        var copy = new FrameHologram(id, renderMode);
        copy.frame = frame;
        copy.frameWidth = frameWidth;
        copy.frameHeight = frameHeight;
        copy.scale = new Vector3f(scale);
        copy.translation = new Vector3f(translation);
        copy.leftRotation = new Quaternion4f(leftRotation.getX(), leftRotation.getY(), leftRotation.getZ(), leftRotation.getW());
        copy.rightRotation = new Quaternion4f(rightRotation.getX(), rightRotation.getY(), rightRotation.getZ(), rightRotation.getW());
        copy.billboard = billboard;
        copy.teleportDuration = teleportDuration;
        copy.interpolationDurationTransformation = interpolationDurationTransformation;
        copy.viewRange = viewRange;
        copy.glowing = glowing;
        copy.glowColor = glowColor;
        copy.brightness = brightness;
        copy.updateTaskPeriod = updateTaskPeriod;
        copy.maxPlayerRenderDistanceSquared = maxPlayerRenderDistanceSquared;
        return copy;
    }
}
