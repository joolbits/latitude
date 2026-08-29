package com.example.globe.client.create;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * One-shot transfer of Latitude's basic text inputs to the next vanilla
 * create-world screen.
 */
public final class VanillaCreateWorldHandoff {
    private static final VanillaCreateWorldHandoff NEXT_SCREEN =
            new VanillaCreateWorldHandoff(System::nanoTime, Duration.ofMinutes(2).toNanos());

    private final LongSupplier ticker;
    private final long ttlNanos;
    private Pending pending;

    VanillaCreateWorldHandoff(LongSupplier ticker, long ttlNanos) {
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        if (ttlNanos <= 0L) {
            throw new IllegalArgumentException("ttlNanos must be positive");
        }
        this.ttlNanos = ttlNanos;
    }

    public static void armNext(Object claimKey, String worldName, String seed) {
        NEXT_SCREEN.arm(claimKey, worldName, seed);
    }

    public static Optional<Payload> claimNext(Object claimKey) {
        return NEXT_SCREEN.claim(claimKey);
    }

    public static void cancelNext() {
        NEXT_SCREEN.cancel();
    }

    synchronized void arm(Object claimKey, String worldName, String seed) {
        this.pending = new Pending(
                Objects.requireNonNull(claimKey, "claimKey"),
                new Payload(Objects.requireNonNull(worldName, "worldName"), Objects.requireNonNull(seed, "seed")),
                this.ticker.getAsLong());
    }

    synchronized Optional<Payload> claim(Object claimKey) {
        Pending claimed = this.pending;
        if (claimed == null) {
            return Optional.empty();
        }
        long elapsed = this.ticker.getAsLong() - claimed.armedAtNanos();
        if (elapsed >= this.ttlNanos) {
            this.pending = null;
            return Optional.empty();
        }
        if (claimed.claimKey() != claimKey) {
            return Optional.empty();
        }
        this.pending = null;
        return Optional.of(claimed.payload());
    }

    synchronized void cancel() {
        this.pending = null;
    }

    public record Payload(String worldName, String seed) {}

    private record Pending(Object claimKey, Payload payload, long armedAtNanos) {}
}
