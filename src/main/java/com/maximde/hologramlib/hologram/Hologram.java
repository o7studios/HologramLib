package com.maximde.hologramlib.hologram;

import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.maximde.hologramlib.HologramLib;
import com.maximde.hologramlib.utils.BukkitTasks;
import com.maximde.hologramlib.utils.TaskHandle;
import com.maximde.hologramlib.utils.Vector3F;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import me.tofaa.entitylib.meta.EntityMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@SuppressWarnings({"unused", "UnusedReturnValue", "deprecation", "DeprecatedIsStillUsed"})
public abstract class Hologram<T extends Hologram<T>> {

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }
    @Getter
    protected Location location;

    /**
     * Players which will not be automatically added as viewers no matter which render mode
     */
    @Getter
    private final Set<Player> blacklistedViewers = ConcurrentHashMap.newKeySet();

    @Getter
    protected boolean dead = true;

    @Getter
    @Accessors(chain = true)
    protected boolean glowing = false;

    @Getter @Accessors(chain = true)
    protected long updateTaskPeriod = 20L;

    @Getter @Accessors(chain = true)
    protected double maxPlayerRenderDistanceSquared = 62500;

    @Getter @Accessors(chain = true)
    protected Display.Billboard billboard = Display.Billboard.CENTER;

    @Getter @Accessors(chain = true)
    protected int teleportDuration = 10;

    @Getter @Accessors(chain = true)
    protected int interpolationDurationTransformation = 10;

    @Getter @Accessors(chain = true)
    protected double viewRange = 1.0;

    @Getter @Accessors(chain = true)
    protected int brightness = -1;

    @Getter @Accessors(chain = true)
    protected boolean isInvisible = true;

    @Accessors(chain = true) @Getter
    protected int glowColor = 0;

    @Getter
    protected final String id;

    @Getter @Accessors(chain = true)
    protected int entityID;

    protected Vector3f scale = new Vector3f(1, 1, 1);
    protected Vector3f translation = new Vector3f(0, 0F, 0);

    protected Quaternion4f rightRotation = new Quaternion4f(0, 0, 0, 1);
    protected Quaternion4f leftRotation = new Quaternion4f(0, 0, 0, 1);

    /**
     * The render mode determines which players can see the hologram:
     * - NEARBY: Only players within viewing distance
     * - ALL: All players on the server
     * - VIEWER_LIST: Only specific players added as viewers
     * - NONE: Hologram is not visible to any players
     */
    @Getter
    protected RenderMode renderMode;

    @Getter
    protected final EntityType entityType;

    @Getter
    protected TaskHandle task;

    /**
     * Do not use this if you don't know what you are doing!
     * This interface for accessing specific setters is only for internal methods.
     */
    @Getter
    private final Internal internalAccess;

    protected WrapperEntity entity;

    protected @Nullable Integer attachedEntityId;

    /**
     * Cached resolution of {@link #attachedEntityId} to a player so the periodic
     * update task doesn't have to scan all online players every run. Weak so a
     * stale cache entry can never pin a player (and its world) in the heap.
     */
    private volatile @Nullable java.lang.ref.WeakReference<Player> attachedPlayerCache;

    @Getter
    protected boolean interactive;

    @Getter
    protected InteractionBox interactionBox;

    @Getter
    protected InteractionBox.OnInteract onInteract;

    public interface Internal {
        Hologram<?> spawn(Location location, boolean ignorePitchYaw);
        void kill();
        void setLocation(Location location);
    }

    protected Hologram(String id, EntityType entityType) {
        this(id, RenderMode.ALL, entityType);
    }


    protected Hologram(String id, RenderMode renderMode, EntityType entityType) {
        this.entityType = entityType;
        validateId(id);
        this.entity = new WrapperEntity(entityType);
        this.id = id;
        this.entityID = entity.getEntityId();
        this.renderMode = renderMode;
        this.internalAccess = new InternalSetters();
        startRunnable();
    }

    private void startRunnable() {
        if (task != null) return;
        task = BukkitTasks.runTaskTimerAsync(this::updateAffectedPlayers, 20L, updateTaskPeriod);
    }


    private class InternalSetters implements Internal {

        @Override
        public Hologram<?> spawn(Location location, boolean ignoreYawPitch) {
            Hologram.this.spawn(location, ignoreYawPitch);
            return Hologram.this;
        }

        @Override
        public void kill() {
            Hologram.this.kill();
        }

        @Override
        public void setLocation(Location location) {
            Hologram.this.setLocation(location);
        }
    }

    private void setLocation(Location location) {
        this.location = location;
    }

    /**
     * Updates the set properties for the entity (shows them to the players).
     * Should be called after making any changes to the hologram object.
     */
    public T update() {
        this.updateAffectedPlayers();
        EntityMeta meta = this.entity.getEntityMeta();
        // Batch the metadata setters: with notifications on, EntityLib sends one
        // ENTITY_METADATA packet per setter call (~20 packets per update). Turning
        // them off and back on flushes all changes as a single packet, or none at
        // all when nothing actually changed.
        meta.setNotifyAboutChanges(false);
        this.applyMeta();
        meta.setNotifyAboutChanges(true);
        return self();
    }

    public T setRenderMode(RenderMode newRenderMode) {
        if (newRenderMode == null) throw new IllegalArgumentException("RenderMode cannot be null");
        if (this.renderMode == newRenderMode) return self();
        BukkitTasks.runTask(() -> {
            if (this.renderMode == RenderMode.NOT_ATTACHED_PLAYER && attachedEntityId != null) {
                Player attachedPlayer = getPlayerByEntityId(attachedEntityId);
                if (attachedPlayer != null) {
                    addViewer(attachedPlayer);
                }
            }
            this.renderMode = newRenderMode;
            updateAffectedPlayers();
        });
        return self();
    }

    protected void validateId(String id) {
        if (id.contains(" ")) {
            throw new IllegalArgumentException("The hologram ID cannot contain spaces! (" + id + ")");
        }
    }

    protected com.github.retrooper.packetevents.util.Vector3f toVector3f(Vector3f vector) {
        return new com.github.retrooper.packetevents.util.Vector3f(vector.x, vector.y, vector.z);
    }

    /**
     * THIS METHOD WILL BE MADE 'private' SOON!
     * Use HologramManager#remove(Hologram) instead!
     */
    @Deprecated
    public void kill() {
        if (attachedEntityId != null) {
            // Deregister but keep attachedEntityId so a later re-spawn can re-attach.
            PassengerManager.unregisterAttachment(attachedEntityId, this);
            PassengerManager.setPassengers(attachedEntityId, removeElement(PassengerManager.getPassengers(attachedEntityId), this.entityID));
        }
        this.entity.remove();
        this.task.cancel();
        this.dead = true;
        if (this.interactionBox != null) this.interactionBox.kill();
        this.interactive = false;
    }

    public T teleport(Location newLocation) {
        this.location = newLocation;
        this.entity.teleport(SpigotConversionUtil.fromBukkitLocation(newLocation));
        if (this.interactionBox != null) this.interactionBox.teleport(newLocation);
        return self();
    }

    protected abstract EntityMeta applyMeta();

    public Vector3F getTranslation() {
        return new Vector3F(this.translation.x, this.translation.y, this.translation.z);
    }


    public Vector3F getScale() {
        return new Vector3F(this.scale.x, this.scale.y, this.scale.z);
    }

    /**
     * Updates which players should be able to see this hologram based on the render mode.
     * For ALL mode, adds all online players.
     * For VIEWER_LIST mode, only uses manually added viewers.
     * Removes viewers who are too far away or in different worlds.
     */
    private void updateAffectedPlayers() {
        if(this.dead) return;

        if(renderMode == RenderMode.VIEWER_LIST) {
            if (attachedEntityId != null) {
                Player attachedPlayer = resolveAttachedPlayer();
                if (attachedPlayer != null && attachedPlayer.isOnline()) {
                    Location playerLocation = attachedPlayer.getLocation();
                    if (this.location != null) {
                        playerLocation.setYaw(this.location.getYaw());
                        playerLocation.setPitch(this.location.getPitch());
                    }
                    if (playerLocation.getWorld() != null) {
                        // While mounted, the client derives this entity's position from
                        // its vehicle, so teleport packets are ignored anyway. Only keep
                        // the server-side location fresh — it is used as the spawn
                        // position when a viewer (re-)tracks the hologram. Passenger
                        // packets are handled by attach/addViewer/EntityTrackerListener,
                        // not re-broadcast here.
                        this.location = playerLocation;
                        this.entity.setLocation(SpigotConversionUtil.fromBukkitLocation(playerLocation));
                        if (this.interactionBox != null) this.interactionBox.teleport(playerLocation);
                    }
                }
            }
            return;
        }

        if(this.location == null) {
            Bukkit.getLogger().log(Level.WARNING, "Tried to update hologram with ID " + this.id + " entity type " + this.entityType.getName().getKey() + ". But the location is not set!");
            return;
        }

        World world = this.location.getWorld();

        if (world != null &&  (this.renderMode == RenderMode.ALL || this.renderMode == RenderMode.NEARBY || this.renderMode == RenderMode.NOT_ATTACHED_PLAYER)) {
            List<Player> viewersToKeep = List.copyOf(world.getPlayers());
            viewersToKeep = viewersToKeep.stream()
                    .filter(Objects::nonNull)
                    .filter(player ->
                            player.isOnline() &&
                                    !this.blacklistedViewers.contains(player) &&
                                    Objects.equals(player.getLocation().getWorld(), world) &&
                                    player.getLocation().distanceSquared(this.location) <= this.maxPlayerRenderDistanceSquared
                    )
                    .toList();

            if (this.renderMode == RenderMode.NOT_ATTACHED_PLAYER && attachedEntityId != null) {
                Player attachedPlayer = resolveAttachedPlayer();
                if (attachedPlayer != null) {
                    viewersToKeep = viewersToKeep.stream()
                            .filter(player -> !player.equals(attachedPlayer))
                            .toList();
                }
            }

            Set<UUID> viewersToRemove = new HashSet<>(this.entity.getViewers());
            viewersToKeep.forEach(player -> viewersToRemove.remove(player.getUniqueId()));

            viewersToRemove.forEach(this.entity::removeViewer);
            // Passenger packets for newly added viewers are sent by addViewer itself;
            // existing viewers are kept mounted by EntityTrackerListener, so no
            // periodic re-broadcast is needed here.
            this.addAllViewers(viewersToKeep);
        }
    }

    /**
     * Resolves the attached entity id to a player, caching the result so the periodic
     * update task doesn't scan every online player on each run.
     */
    protected @Nullable Player resolveAttachedPlayer() {
        Integer attachedId = this.attachedEntityId;
        if (attachedId == null) return null;
        java.lang.ref.WeakReference<Player> ref = this.attachedPlayerCache;
        Player cached = ref != null ? ref.get() : null;
        if (cached != null && cached.isOnline() && cached.getEntityId() == attachedId) return cached;
        Player resolved = getPlayerByEntityId(attachedId);
        this.attachedPlayerCache = resolved != null ? new java.lang.ref.WeakReference<>(resolved) : null;
        return resolved;
    }

    protected void sendPacket(PacketWrapper<?> packet, List<Player> players) {
        if (this.renderMode == RenderMode.NONE) return;

        players.forEach(player ->
                HologramLib.getPlayerManager().sendPacket(player, packet));
    }

    private void spawn(Location location, boolean ignorePitchYaw) {
        this.location = location;
        if(ignorePitchYaw) {
            this.location.setPitch(0);
            this.location.setYaw(0);
        }
        this.entity.spawn(SpigotConversionUtil.fromBukkitLocation(this.location));
        this.dead = false;
        // Re-register a still-configured attachment when a killed hologram is revived.
        if (attachedEntityId != null) PassengerManager.registerAttachment(attachedEntityId, this);
        if (this.interactionBox != null) this.interactionBox.teleport(location);
    }

    private void spawn(Location location) {
        this.spawn(location, false);
    }

    /**
     * Attaches this hologram to a player.
     * @param player The player to attach the hologram to
     */
    public void attachToPlayer(Player player) {
        attach(player.getEntityId());
    }
    /**
     *
     * Gets the player associated with the given entity ID.
     * @param entityId The entity ID to search for
     * @return The player if found, null otherwise
     */
    private Player getPlayerByEntityId(int entityId) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getEntityId() == entityId) {
                return player;
            }
        }
        return null;
    }

    /**
     * Attaches this hologram to another entity, making it ride the target entity.
     * <b>Warning:</b> Keep in mind that the hologram's location is not automatically
     * updated when it is attached to another entity, so if the entity moves too far
     * away from the hologram's location, the hologram may be unloaded.
     * To work around this, you will need to teleport the hologram to the entity's
     * location when the entity is moved far away from the hologram's original location.
     *
     * @param entityId The entity id to attach the hologram to
     */
    public void attach(int entityId) {
        Integer previous = attachedEntityId;
        if (previous != null && previous != entityId) {
            PassengerManager.unregisterAttachment(previous, this);
            PassengerManager.setPassengers(previous, removeElement(PassengerManager.getPassengers(previous), this.entityID));
        }
        attachedEntityId = entityId;
        attachedPlayerCache = null;

        if (renderMode == RenderMode.NOT_ATTACHED_PLAYER) {
            Player attachedPlayer = resolveAttachedPlayer();
            if (attachedPlayer != null) {
                removeViewer(attachedPlayer);
            }
        }

        // Update PM synchronously before the async send so sequential attach() calls
        // from the same thread each see the accumulated passenger list rather than racing
        // on an empty/stale PM (PM is otherwise only updated via onPacketSend, which
        // never fires when there are no viewers yet).
        PassengerManager.registerAttachment(entityId, this);
        int[] newPassengers = PassengerManager.getMergedPassengers(entityId);
        PassengerManager.setPassengers(entityId, newPassengers);
        WrapperPlayServerSetPassengers attachPacket = new WrapperPlayServerSetPassengers(entityId, newPassengers);
        BukkitTasks.runTaskAsync(() -> this.entity.sendPacketsToViewers(attachPacket));
    }

    /**
     * Detaches the hologram from its attached entity, if any.
     */
    public void detach() {
        if (attachedEntityId != null) {
            int vehicleId = attachedEntityId;
            if (renderMode == RenderMode.NOT_ATTACHED_PLAYER) {
                Player previouslyAttachedPlayer = resolveAttachedPlayer();
                if (previouslyAttachedPlayer != null) {
                    addViewer(previouslyAttachedPlayer);
                }
            }

            PassengerManager.unregisterAttachment(vehicleId, this);
            int[] remaining = removeElement(PassengerManager.getMergedPassengers(vehicleId), this.entityID);
            PassengerManager.setPassengers(vehicleId, remaining);
            WrapperPlayServerSetPassengers detachPacket = new WrapperPlayServerSetPassengers(vehicleId, remaining);
            BukkitTasks.runTaskAsync(() -> this.entity.sendPacketsToViewers(detachPacket));
            attachedEntityId = null;
            attachedPlayerCache = null;
        }
    }

    /**
     * Adds an element to the array if it is not already present.
     *
     * @param array The input array to which the element may be added.
     * @param element The element to be added to the array.
     * @return A new array containing the original elements plus the new element if it was not already included.
     */
    public static int[] addElement(int[] array, int element) {
        for (int value : array) if (value == element) return array;

        int[] result = Arrays.copyOf(array, array.length + 1);
        result[array.length] = element;
        return result;
    }

    /**
     * Ignores render mode
     * @param player
     */
    public void show(Player player) {
        this.removeFromViewerBlacklist(player);
        this.addViewer(player);
        if (this.interactionBox != null) this.interactionBox.show(player);
    }

    /**
     * Ignores render mode
     * @param player
     */
    public void hide(Player player) {
        this.addToViewerBlacklist(player);
        this.removeViewer(player);
        if (this.interactionBox != null) this.interactionBox.hide(player);
    }

    public void addToViewerBlacklist(Player player) {
        this.blacklistedViewers.add(player);
    }

    public void removeFromViewerBlacklist(Player player) {
        this.blacklistedViewers.remove(player);
    }

    /**
     * Removes all occurrences of the specified element from the given array.
     *
     * @param array The input array from which the element will be removed.
     * @param element The element to be removed from the array.
     * @return A new array with all occurrences of the specified element removed.
     */
    public static int[] removeElement(int[] array, int element) {
        int count = 0;
        for (int value : array) if (value == element) count++;

        if (count == 0) return array;
        int[] result = new int[array.length - count];
        int i = 0;
        for (int value : array) if (value != element) result[i++] = value;
        return result;
    }

    /**
     * Attaches entities to this hologram.
     *
     * @param entityIDs The passengers
     */
    public void addPassenger(int... entityIDs) {
        this.entity.addPassengers(entityIDs);
    }

    public Set<Integer> getPassengers() {
        return this.entity.getPassengers();
    }


    /**
     * Period in ticks between updates of the hologram's viewer list.
     * Lower values mean more frequent updates but higher server load.
     * Default is 60 ticks (3 seconds).
     */
    public T setUpdateTaskPeriod(long updateTaskPeriod) {
        this.updateTaskPeriod = updateTaskPeriod;
        return self();
    }

    public T setMaxPlayerRenderDistanceSquared(double maxPlayerRenderDistanceSquared) {
        this.maxPlayerRenderDistanceSquared = maxPlayerRenderDistanceSquared;
        return self();
    }

    public T setBillboard(Display.Billboard billboard) {
        this.billboard = billboard;
        return self();
    }

    @Deprecated(forRemoval = true)
    public int getInterpolationDurationRotation() {
        return this.teleportDuration;
    }

    @Deprecated(forRemoval = true)
    public T setInterpolationDurationRotation(int teleportDuration) {
        this.teleportDuration = teleportDuration;
        return self();
    }

    public T setTeleportDuration(int teleportDuration) {
        this.teleportDuration = teleportDuration;
        return self();
    }

    public T setInterpolationDurationTransformation(int interpolationDurationTransformation) {
        this.interpolationDurationTransformation = interpolationDurationTransformation;
        return self();
    }

    public T setViewRange(double viewRange) {
        this.viewRange = viewRange;
        return self();
    }

    /**
     * @return yaw and pitch
     */
    public Vector2f getRotation() {
        Quaternionf combined = new Quaternionf(
                leftRotation.getX(), leftRotation.getY(), leftRotation.getZ(), leftRotation.getW()
        ).mul(new Quaternionf(
                rightRotation.getX(), rightRotation.getY(), rightRotation.getZ(), rightRotation.getW()
        ));

        Vector3f eulerRadians = new Vector3f();
        combined.getEulerAnglesXYZ(eulerRadians);

        eulerRadians.x = (float) Math.toDegrees(eulerRadians.x);
        eulerRadians.y = (float) Math.toDegrees(eulerRadians.y);
        eulerRadians.z = (float) Math.toDegrees(eulerRadians.z);

        return new Vector2f(eulerRadians.y, eulerRadians.x);
    }

    public T setRotation(float yaw, float pitch) {
        float yawRadians = (float) Math.toRadians(yaw);
        float pitchRadians = (float) Math.toRadians(pitch);
        Quaternionf rotation = new Quaternionf()
                .rotateY(yawRadians)
                .rotateX(pitchRadians);

        this.leftRotation = new Quaternion4f(rotation.x, rotation.y, rotation.z, rotation.w);
        this.rightRotation = new Quaternion4f(0, 0, 0, 1);
        return self();
    }

    public T setLeftRotation(float x, float y, float z, float w) {
        this.leftRotation = new Quaternion4f(x, y, z, w);
        return self();
    }

    public T setRightRotation(float x, float y, float z, float w) {
        this.rightRotation = new Quaternion4f(x, y, z, w);
        return self();
    }

    public T setTranslation(float x, float y, float z) {
        this.translation = new Vector3f(x, y, z);
        return self();
    }

    public T setTranslation(Vector3F translation) {
        this.translation = new Vector3f(translation.x, translation.y, translation.z);
        return self();
    }

    @ApiStatus.Experimental
    public T onInteract(@NonNull InteractionBox.OnInteract onInteract) {
        this.onInteract = onInteract;
        this.interactive = true;
        this.interactionBox = new InteractionBox(this.id + "_interaction", this.onInteract)
                .setHeight(this.getHeight())
                .setWidth(this.getWidth())
                .setResponsive(true);
        return self();
    }

    public T disableInteraction() {
        this.interactive = false;
        if(this.interactionBox != null) this.interactionBox.setResponsive(false);
        return self();
    }

    public T enableInteraction() {
        this.interactive = true;
        if(this.interactionBox != null) this.interactionBox.setResponsive(true);
        return self();
    }

    abstract int getHeight();

    abstract int getWidth();

    public T setTransformation(Transformation transformation) {
        this.translation = transformation.getTranslation();
        this.scale = transformation.getScale();
        Quaternionf rightRotation = transformation.getRightRotation();
        this.rightRotation = new Quaternion4f(rightRotation.x(), rightRotation.y(), rightRotation.z(), rightRotation.w());
        Quaternionf leftRotation = transformation.getLeftRotation();
        this.leftRotation = new Quaternion4f(leftRotation.x(), leftRotation.y(), leftRotation.z(), leftRotation.w());
        return self();
    }

    /**
     * Sets the RGB color for the item's glow effect. (The color can be wrong if server version is below 1.20.5)
     * Only applies when glowing is set to true.
     */
    public T setGlowColor(Color color) {
        int rgb = color.getRGB();
        this.glowColor = ((rgb & 0xFF0000) >> 16) |
                (rgb & 0x00FF00) |
                ((rgb & 0x0000FF) << 16);
        return self();
    }

    public T setGlowing(boolean glowing) {
        this.glowing = glowing;
        return self();
    }

    public T setTransformationMatrix(Matrix4f matrix4f) {
        Vector3f translation = new Vector3f();
        matrix4f.getTranslation(translation);
        this.translation = translation;
        Vector3f scale = new Vector3f();
        matrix4f.getScale(scale);
        this.scale = scale;
        this.rightRotation = new Quaternion4f(0,0,0,1);
        Quaternionf leftRotation = new Quaternionf();
        matrix4f.getNormalizedRotation(leftRotation);
        this.leftRotation = new Quaternion4f(leftRotation.x(), leftRotation.y(), leftRotation.z(), leftRotation.w());
        return self();
    }

    public T setBrightness(int brightness) {
        this.brightness = brightness;
        return self();
    }

    public T setBrightness(Display.Brightness brightness) {
        return self().setBrightness(brightness.getBlockLight(), brightness.getSkyLight());
    }

    public T setBrightness(int blockLight, int skyLight) {
        if (blockLight < 0 || blockLight > 15) {
            throw new IllegalArgumentException("blockLight must be between 0 and 15");
        }
        if (skyLight < 0 || skyLight > 15) {
            throw new IllegalArgumentException("skyLight must be between 0 and 15");
        }
        this.brightness = blockLight << 4 | skyLight << 20;
        return self();
    }

    public T addViewer(Player player) {
        UUID viewerId = player.getUniqueId();
        if(this.entity.hasViewer(viewerId)) return self();
        if (attachedEntityId != null && !EntityTrackerListener.isTracking(viewerId, attachedEntityId)) {
            // The viewer's client doesn't know the vehicle (out of tracking range), so a
            // spawn now could never be mounted and would float detached at this spot.
            // Register the viewer without packets; EntityTrackerListener spawns and
            // mounts the hologram the moment the vehicle spawns for this client.
            this.entity.addViewerSilently(viewerId);
            return self();
        }
        this.entity.addViewer(viewerId);
        if (attachedEntityId != null) {
            // The spawn packet from addViewer and this passenger packet go through the
            // same ordered channel, so mounting happens in the same flush — no delay
            // needed, and no frame where the hologram renders unmounted.
            sendPacket(new WrapperPlayServerSetPassengers(
                            attachedEntityId, PassengerManager.getMergedPassengers(attachedEntityId)),
                    Collections.singletonList(player));
        }
        return self();
    }

    public T removeViewer(Player player) {
        this.entity.removeViewer(player.getUniqueId());
        return self();
    }

    public Set<UUID> getViewerUUIDs() {
        return this.entity.getViewers();
    }

    public List<Player> getViewers() {
        Set<UUID> viewerUUIDs = this.entity.getViewers();
        List<Player> viewers = new ArrayList<>(viewerUUIDs.size());
        viewerUUIDs.forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                viewers.add(player);
            }
        });
        return viewers;
    }

    public boolean hasViewer(UUID viewer) {
        return this.entity.hasViewer(viewer);
    }

    public T addAllViewers(List<Player> viewerList) {
        viewerList.forEach(this::addViewer);
        return self();
    }

    public T removeAllViewers() {
        this.entity.getViewers().forEach(this.entity::removeViewer);
        return self();
    }

    public T setScale(float x, float y, float z) {
        this.scale = new Vector3f(x, y, z);
        return self();
    }

    public T setScale(Vector3F scale) {
        this.scale = new Vector3f(scale.x, scale.y, scale.z);
        return self();
    }

    /**
     * Sets the visibility state of the hologram.
     *
     * @param isInvisible A boolean indicating whether the hologram's blue line when using F3+B is invisible (true) or visible (false).
     * @return The current instance of the hologram for method chaining.
     */
    public T setIsInvisible(boolean isInvisible) {
        this.isInvisible = isInvisible;
        return self();
    }

    protected abstract T copy();
    protected abstract T copy(String id);
}
