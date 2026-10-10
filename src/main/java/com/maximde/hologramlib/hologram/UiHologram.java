package com.maximde.hologramlib.hologram;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.maximde.hologramlib.HologramLib;
import com.maximde.hologramlib.utils.BukkitTasks;
import com.maximde.hologramlib.utils.TaskHandle;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.tofaa.entitylib.meta.EntityMeta;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import studio.o7.heldenUi.ui.UiScreen;
import studio.o7.heldenUi.ui.UiService;
import studio.o7.heldenUi.ui.UiView;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * A HeldenUI screen on a text display. Every viewer gets their own copy of it, with its own state, texts in their
 * language, animations, presses and refreshes, like the dialog they would see otherwise. Needs the HeldenUI pack.
 *
 * <p>With the default {@link Display.Billboard#FIXED} billboard it is clickable: interaction entities cover the
 * text, and a right or left click runs the action on the pixel the player looks at. Other billboards turn with the
 * camera, so they only show the screen. Spawn it without {@code ignorePitchYaw} to face the location's yaw.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class UiHologram extends Hologram<UiHologram> {

    public static final int TEXT_INDEX = 23;
    /** Rows are laid out to the pixel, the display must never wrap them. */
    private static final int NO_WRAP = 1 << 14;
    private static final float CLICK_COLUMN_WIDTH = 0.25f;
    /** Lets neighbouring click boxes overlap a little, and reach past the text's top and bottom edge. */
    private static final float CLICK_BOX_MARGIN = 0.05f;
    private static final int FULL_BRIGHT = 15;

    private final UiService ui;
    private final Function<Player, UiScreen> home;
    private final Map<UUID, Viewer> viewers = new ConcurrentHashMap<>();
    private final Set<UUID> firstFramesQueued = ConcurrentHashMap.newKeySet();
    private final Set<UUID> refreshQueued = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean refreshAllQueued = new AtomicBoolean();
    private final List<InteractionBox> clickBoxes = new CopyOnWriteArrayList<>();
    private int coveredWidth;
    private int coveredHeight;
    private volatile @Nullable TaskHandle ticker;

    @Setter @Getter @Accessors(chain = true)
    private boolean seeThroughBlocks = false;

    /** A viewer's own copy of the screen and the frame they were sent last. */
    private static final class Viewer {
        private final UiView view;
        private volatile UiView.@Nullable Frame frame;

        private Viewer(UiView view) {
            this.view = view;
        }
    }

    /** The same screen for everyone, each viewer still gets their own copy of its state. */
    public UiHologram(String id, UiService ui, UiScreen home) {
        this(id, RenderMode.ALL, ui, player -> home);
    }

    /** {@code home} builds the screen a viewer starts on, like a menu with their own data. Called on the main thread. */
    public UiHologram(String id, UiService ui, Function<Player, UiScreen> home) {
        this(id, RenderMode.ALL, ui, home);
    }

    public UiHologram(String id, RenderMode renderMode, UiService ui, Function<Player, UiScreen> home) {
        super(id, renderMode, EntityTypes.TEXT_DISPLAY);
        this.ui = ui;
        this.home = home;
        this.billboard = Display.Billboard.FIXED;
        // The screen's colors are meant as drawn, not darkened by the light around the display.
        setBrightness(FULL_BRIGHT, FULL_BRIGHT);
    }

    /** Redraws the screen for every viewer in the next tick, for changes that did not come from a click. */
    public UiHologram refresh() {
        refreshAllQueued.set(true);
        return this;
    }

    public UiHologram refresh(Player player) {
        refreshQueued.add(player.getUniqueId());
        return this;
    }

    @Override
    public UiHologram update() {
        super.update();
        if (ticker == null && !dead) ticker = BukkitTasks.runTaskTimer(this::tick, 1, 1);
        return this;
    }

    @Override
    public UiHologram teleport(Location newLocation) {
        super.teleport(newLocation);
        placeClickBoxes();
        return this;
    }

    @Deprecated
    @Override
    public void kill() {
        super.kill();
        var running = ticker;
        if (running != null) running.cancel();
        ticker = null;
        removeClickBoxes();
        viewers.clear();
        coveredWidth = 0;
        coveredHeight = 0;
    }

    @Override
    public UiHologram addViewer(Player player) {
        super.addViewer(player);
        if (renderMode == RenderMode.VIEWER_LIST) clickBoxes.forEach(box -> box.addViewer(player));
        return this;
    }

    @Override
    public UiHologram removeViewer(Player player) {
        super.removeViewer(player);
        if (renderMode == RenderMode.VIEWER_LIST) clickBoxes.forEach(box -> box.removeViewer(player));
        return this;
    }

    @Override
    public void show(Player player) {
        super.show(player);
        clickBoxes.forEach(box -> box.show(player));
    }

    @Override
    public void hide(Player player) {
        super.hide(player);
        clickBoxes.forEach(box -> box.hide(player));
    }

    /**
     * The text this player should see, for the packet listener. A viewer without a frame yet gets an empty one
     * and their first frame right after, rendering has to happen on the main thread.
     */
    public Component textFor(Player player) {
        var viewer = viewers.get(player.getUniqueId());
        var frame = viewer == null ? null : viewer.frame;
        if (frame != null) return frame.text();
        if (firstFramesQueued.add(player.getUniqueId())) BukkitTasks.runTask(() -> showFirstFrame(player));
        return Component.empty();
    }

    private void showFirstFrame(Player player) {
        firstFramesQueued.remove(player.getUniqueId());
        if (dead || !player.isOnline()) return;
        show(player, viewers.computeIfAbsent(player.getUniqueId(), id -> new Viewer(ui.view(home.apply(player)))));
    }

    private void tick() {
        if (dead) return;
        boolean refreshAll = refreshAllQueued.getAndSet(false);
        viewers.forEach((id, viewer) -> {
            var player = Bukkit.getPlayer(id);
            if (player == null) {
                viewers.remove(id);
                return;
            }
            boolean refresh = refreshQueued.remove(id) || refreshAll;
            if (!hasViewer(id)) return;
            if (refresh || viewer.view.frameDue()) show(player, viewer);
        });
    }

    private void show(Player player, Viewer viewer) {
        var frame = viewer.view.render(player);
        var previous = viewer.frame;
        viewer.frame = frame;
        cover(frame);
        if (previous != null && previous.text().equals(frame.text())) return;
        // The listener fills in the viewer's frame, like it does for every other metadata packet of this display.
        var packet = new WrapperPlayServerEntityMetadata(entityID,
                List.of(new EntityData<>(TEXT_INDEX, EntityDataTypes.ADV_COMPONENT, Component.empty())));
        HologramLib.getPlayerManager().sendPacket(player, packet);
    }

    private void click(Player player) {
        var viewer = viewers.get(player.getUniqueId());
        var frame = viewer == null ? null : viewer.frame;
        if (frame == null || location == null) return;
        var eye = player.getEyeLocation();
        var origin = new Vector3f((float) (eye.getX() - location.getX()), (float) (eye.getY() - location.getY()),
                (float) (eye.getZ() - location.getZ()));
        var look = eye.getDirection();
        var direction = new Vector3f((float) look.getX(), (float) look.getY(), (float) look.getZ());
        plane(frame.width(), frame.height()).hit(origin, direction).ifPresent(pixel -> {
            viewer.view.click(player, (int) Math.floor(pixel.x), (int) Math.floor(pixel.y));
            show(player, viewer);
        });
    }

    /** Frames all stand centered on the same spot, so boxes around the largest one so far cover every viewer's. */
    private void cover(UiView.Frame frame) {
        if (frame.width() <= coveredWidth && frame.height() <= coveredHeight) return;
        coveredWidth = Math.max(coveredWidth, frame.width());
        coveredHeight = Math.max(coveredHeight, frame.height());
        placeClickBoxes();
    }

    /**
     * Interaction boxes are upright and square, so the text is covered by a row of narrow columns. One large box
     * would stick out far in front of and behind the text and catch clicks meant for the world around it.
     */
    private void placeClickBoxes() {
        removeClickBoxes();
        if (dead || location == null || coveredWidth == 0 || billboard != Display.Billboard.FIXED) return;
        var manager = HologramLib.getManager().orElse(null);
        if (manager == null) return;

        var plane = plane(coveredWidth, coveredHeight);
        float length = plane.point(0, coveredHeight / 2f).distance(plane.point(coveredWidth, coveredHeight / 2f));
        int columns = Math.max(1, (int) Math.ceil(length / CLICK_COLUMN_WIDTH));
        float width = length / columns + CLICK_BOX_MARGIN;
        for (int i = 0; i < columns; i++) {
            float x = (i + 0.5f) * coveredWidth / columns;
            var top = plane.point(x, 0);
            var bottom = plane.point(x, coveredHeight);
            float low = Math.min(top.y, bottom.y) - CLICK_BOX_MARGIN;
            float high = Math.max(top.y, bottom.y) + CLICK_BOX_MARGIN;
            var at = location.clone().add((top.x + bottom.x) / 2, low, (top.z + bottom.z) / 2);
            var box = new InteractionBox(id + "_ui_click_" + i, renderMode, this::click).setSize(width, high - low);
            clickBoxes.add(manager.spawn(box, at));
        }
        // Other render modes find their viewers by themselves.
        if (renderMode == RenderMode.VIEWER_LIST) {
            BukkitTasks.runTask(() -> getViewers().forEach(player -> clickBoxes.forEach(box -> box.addViewer(player))));
        }
    }

    private void removeClickBoxes() {
        HologramLib.getManager().ifPresent(manager -> clickBoxes.forEach(manager::removeInteractionBox));
        clickBoxes.clear();
    }

    private TextPlane plane(int width, int height) {
        return TextPlane.of(location.getYaw(), location.getPitch(), translation, quaternion(leftRotation), scale,
                quaternion(rightRotation), width, height);
    }

    private static Quaternionf quaternion(Quaternion4f rotation) {
        return new Quaternionf(rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW());
    }

    @Override
    protected EntityMeta applyMeta() {
        var meta = (TextDisplayMeta) entity.getEntityMeta();
        // Every viewer has their own text, UiHologramListener puts it into the packets.
        meta.setText(Component.empty());
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
        // The screen draws its own background and shadows.
        meta.setBackgroundColor(0);
        meta.setShadow(false);
        meta.setInvisible(isInvisible);
        if (brightness > -1) meta.setBrightnessOverride(brightness);
        meta.setSeeThrough(seeThroughBlocks);
        return meta;
    }

    @Override
    int getHeight() {
        return Math.max(1, (int) Math.ceil(coveredHeight * 0.025f * scale.y));
    }

    @Override
    int getWidth() {
        return Math.max(1, (int) Math.ceil(coveredWidth * 0.025f * Math.max(scale.x, scale.z)));
    }

    @Override
    protected UiHologram copy() {
        return copy(id + "_copy_" + ThreadLocalRandom.current().nextInt(100000));
    }

    @Override
    public UiHologram copy(String id) {
        var copy = new UiHologram(id, renderMode, ui, home);
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
        copy.seeThroughBlocks = seeThroughBlocks;
        copy.updateTaskPeriod = updateTaskPeriod;
        copy.maxPlayerRenderDistanceSquared = maxPlayerRenderDistanceSquared;
        return copy;
    }
}
