package com.example.globe.client.create;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.jetbrains.annotations.Nullable;

/**
 * One-shot transfer of Latitude's basic text inputs to the next vanilla create-world screen.
 *
 * <p>The claim key is the {@code Runnable} the outgoing screen will be constructed with, so only
 * the screen Latitude actually opened can take the payload; an unrelated create-world screen
 * opened by any other path finds nothing waiting. The TTL is the second guard: if the handoff is
 * armed and the screen never opens (an exception on the way, or the player backing out through a
 * path that skips the cancel), the payload expires instead of leaking into whatever create-world
 * screen happens to open next.</p>
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

    public static void armNext(Object claimKey, String worldName, String seed, Runnable exitCreateFlow) {
        NEXT_SCREEN.arm(claimKey,
                Objects.requireNonNull(worldName, "worldName"),
                Objects.requireNonNull(seed, "seed"),
                Objects.requireNonNull(exitCreateFlow, "exitCreateFlow"));
    }

    /**
     * Arms a claim that carries no name, seed, or return callback -- for a path that opens vanilla
     * directly rather than by way of Latitude's own create screen (the world-list door). The claimed
     * session is still marked vanilla-only (Globe withheld, the {@code init} redirect does not
     * bounce back to Latitude), but nothing is written into vanilla's own fields, and the
     * escape-hatch footer relabel never fires: it already refuses to act without a return callback.
     *
     * <p>The empty-string form this replaces is unsafe, not just unnecessary: the redirect calls
     * {@code uiState.setName(payload.worldName())} unconditionally, and vanilla's create screen
     * already has its own default name filled in by the time that runs. An empty string does not
     * leave that default alone -- it BLANKS it, and the door would open on an unnamed world.</p>
     */
    public static void armNextWithoutReturn(Object claimKey) {
        NEXT_SCREEN.arm(claimKey, null, null, null);
    }

    public static Optional<Payload> claimNext(Object claimKey) {
        return NEXT_SCREEN.claim(claimKey);
    }

    public static void cancelNext() {
        NEXT_SCREEN.cancel();
    }

    synchronized void arm(Object claimKey, @Nullable String worldName, @Nullable String seed,
                          @Nullable Runnable exitCreateFlow) {
        this.pending = new Pending(
                Objects.requireNonNull(claimKey, "claimKey"),
                new Payload(worldName, seed, exitCreateFlow),
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

    /**
     * All three fields are individually nullable -- {@link #armNextWithoutReturn} arms with none of
     * them, for a claim that exists only to mark the session vanilla-only. Every reader must check
     * before using any one of them.
     */
    public record Payload(@Nullable String worldName, @Nullable String seed,
                          @Nullable Runnable exitCreateFlow) {}

    private record Pending(Object claimKey, Payload payload, long armedAtNanos) {}
}
