package com.example.globe.world;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.resources.Identifier;

/**
 * Thread-local authority for globally registered worldgen hooks that cannot see their owning
 * generator or dimension. Generator mixins enter this scope around exact synchronous paths.
 */
public final class LatitudeWorldgenScope {
    private static final ThreadLocal<Deque<Frame>> FRAMES = new ThreadLocal<>();

    private LatitudeWorldgenScope() {
    }

    public static Scope enter(boolean active) {
        return enter(active, false, null);
    }

    /** Marks the biome-decoration phase, where vegetation block-write guards are authoritative. */
    public static Scope enterFeatures(boolean active) {
        return enterFeatures(active, null);
    }

    /**
     * Marks one top-level placed-feature invocation and carries its registry id
     * through nested configured features on the same worldgen thread.
     */
    public static Scope enterFeatures(boolean active, Identifier placedFeatureId) {
        return enter(active, true, placedFeatureId);
    }

    private static Scope enter(boolean active,
                               boolean featureRoot,
                               Identifier placedFeatureId) {
        Deque<Frame> frames = FRAMES.get();
        if (frames == null) {
            frames = new ArrayDeque<>();
            FRAMES.set(frames);
        }
        Frame parent = frames.peek();
        boolean inheritedFeature = parent != null && parent.features;
        Identifier inheritedPlacedFeatureId = parent != null ? parent.placedFeatureId : null;
        Identifier activePlacedFeatureId = active
                ? (featureRoot ? placedFeatureId : inheritedPlacedFeatureId)
                : null;
        Frame frame = new Frame(
                active,
                active && (featureRoot || inheritedFeature),
                activePlacedFeatureId);
        frames.push(frame);
        return new Scope(frame);
    }

    public static boolean isActive() {
        Deque<Frame> frames = FRAMES.get();
        return frames != null && !frames.isEmpty() && frames.peek().active;
    }

    public static boolean isFeatureActive() {
        Deque<Frame> frames = FRAMES.get();
        return frames != null && !frames.isEmpty() && frames.peek().features;
    }

    public static Identifier currentPlacedFeatureId() {
        Deque<Frame> frames = FRAMES.get();
        return frames != null && !frames.isEmpty()
                ? frames.peek().placedFeatureId
                : null;
    }

    private record Frame(boolean active, boolean features, Identifier placedFeatureId) {
    }

    public static final class Scope implements AutoCloseable {
        private final Frame frame;
        private boolean closed;

        private Scope(Frame frame) {
            this.frame = frame;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            Deque<Frame> frames = FRAMES.get();
            if (frames == null || frames.isEmpty() || frames.peek() != frame) {
                throw new IllegalStateException("Latitude worldgen scope closed out of order");
            }
            frames.pop();
            closed = true;
            if (frames.isEmpty()) {
                FRAMES.remove();
            }
        }
    }
}
