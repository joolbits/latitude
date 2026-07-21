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
        Deque<Frame> frames = FRAMES.get();
        if (frames == null) {
            frames = new ArrayDeque<>();
            FRAMES.set(frames);
        }
        Frame frame = new Frame(active);
        frames.push(frame);
        return new Scope(frame);
    }

    public static boolean isActive() {
        Deque<Frame> frames = FRAMES.get();
        return frames != null && !frames.isEmpty() && frames.peek().active;
    }

    private record Frame(boolean active) {
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
