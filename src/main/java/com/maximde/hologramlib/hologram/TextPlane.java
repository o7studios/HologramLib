package com.maximde.hologramlib.hologram;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Where the text of a text display with a fixed billboard lies, built the same way the client draws it. Maps
 * pixels of the text block, from its top left corner, to blocks relative to the display's position and back.
 */
record TextPlane(Matrix4f toWorld) {

    /** The client draws text 40 pixels to the block. */
    private static final float PIXEL = 0.025f;

    /** {@code width} and {@code height} are the text block's size in pixels. */
    static TextPlane of(float yaw, float pitch, Vector3f translation, Quaternionf leftRotation, Vector3f scale,
                        Quaternionf rightRotation, int width, int height) {
        var toWorld = new Matrix4f()
                .rotationYXZ((float) Math.toRadians(-yaw), (float) Math.toRadians(pitch), 0)
                .translate(translation)
                .rotate(leftRotation)
                .scale(scale)
                .rotate(rightRotation)
                .rotateY((float) Math.PI)
                .scale(-PIXEL)
                .translate(1 - width / 2f, -height, 0);
        return new TextPlane(toWorld);
    }

    Vector3f point(float x, float y) {
        return toWorld.transformPosition(x, y, 0, new Vector3f());
    }

    /** The text pixel a ray hits, its origin relative to the display's position. Empty if it points away. */
    Optional<Vector2f> hit(Vector3f origin, Vector3f direction) {
        var toText = toWorld.invert(new Matrix4f());
        var from = toText.transformPosition(origin, new Vector3f());
        var along = toText.transformDirection(direction, new Vector3f());
        if (Math.abs(along.z) < 1e-6f) return Optional.empty();
        float distance = -from.z / along.z;
        if (distance < 0) return Optional.empty();
        return Optional.of(new Vector2f(from.x + along.x * distance, from.y + along.y * distance));
    }
}
