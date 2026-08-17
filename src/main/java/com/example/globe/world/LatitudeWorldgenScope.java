package com.example.globe.world;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-local authority for globally registered worldgen hooks that cannot see their owning
 * generator or dimension. Generator mixins enter this scope around exact synchronous paths.
 */
public final class LatitudeWorldgenScope {
    private static final ThreadLocal<Deque<Frame>> FRAMES = new ThreadLocal<>();

    private LatitudeWorldgenScope() {
    }

    public static Scope enter(boolean active) {
        return enter(active, false);
    }

    /** Marks the biome-decoration phase, where vegetation block-write guards are authoritative. */
    public static Scope enterFeatures(boolean active) {
        return enter(active, true);
    }

    private static Scope enter(boolean active, boolean featureRoot) {
        Deque<Frame> frames = FRAMES.get();
        if (frames == null) {
            frames = new ArrayDeque<>();
            FRAMES.set(frames);
        }
        boolean inheritedFeature = !frames.isEmpty() && frames.peek().features;
        Frame frame = new Frame(active, active && (featureRoot || inheritedFeature));
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

    private record Frame(boolean active, boolean features) {
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
