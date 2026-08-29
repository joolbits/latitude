package com.example.globe.client.create;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.jetbrains.annotations.Nullable;

/**
 * One-shot transfer of Latitude's basic text inputs -- and its way back out -- to the next vanilla
 * create-world screen.
 *
 * <p>The exit callback travels with the payload because the vanilla screen cannot work out on its
 * own where Latitude's screen would have gone on cancel: it holds only the return-to-Latitude
 * callback, and vanilla's own {@code parent} field is not a dependable stand-in. Carrying it here
 * lets the escape-hatch footer offer a genuine one-click exit instead of making the player cancel
 * twice (maintainer report, 2026-08-26).</p>
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
        NEXT_SCREEN.arm(claimKey, worldName, seed, exitCreateFlow);
    }

    /**
     * Arms a vanilla-only session that carries nothing back.
     *
     * <p>For the Select World screen's direct route to vanilla, where there is no Latitude screen
     * behind the vanilla one. Two consequences follow, and both are the ABSENCE of behaviour the
     * ordinary arm() gives you:</p>
     *
     * <ul>
     *   <li><b>No return callback</b>, so the escape-hatch footer leaves vanilla's own buttons
     *       exactly as vanilla built them. Relabelling Cancel to "Back to Latitude" here would point
     *       at a screen that was never open.</li>
     *   <li><b>No payload</b>, so vanilla keeps its own default world name and seed. Arming with
     *       empty strings would blank them instead, which is not the same thing as leaving them
     *       alone.</li>
     * </ul>
     *
     * <p>What it DOES still do is mark the session vanilla-only, which is what stops the redirect
     * bouncing the player straight back into Latitude's screen.</p>
     */
    public static void armNextWithoutReturn(Object claimKey) {
        NEXT_SCREEN.armBare(claimKey);
    }

    public static Optional<Claim> claimNext(Object claimKey) {
        return NEXT_SCREEN.claim(claimKey);
    }

    public static void cancelNext() {
        NEXT_SCREEN.cancel();
    }

    synchronized void armBare(Object claimKey) {
        this.pending = new Pending(
                Objects.requireNonNull(claimKey, "claimKey"), null, null, this.ticker.getAsLong());
    }

    synchronized void arm(Object claimKey, String worldName, String seed, Runnable exitCreateFlow) {
        this.pending = new Pending(
                Objects.requireNonNull(claimKey, "claimKey"),
                new Payload(Objects.requireNonNull(worldName, "worldName"), Objects.requireNonNull(seed, "seed")),
                Objects.requireNonNull(exitCreateFlow, "exitCreateFlow"),
                this.ticker.getAsLong());
    }

    synchronized Optional<Claim> claim(Object claimKey) {
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
        return Optional.of(new Claim(claimed.payload(), claimed.exitCreateFlow()));
    }

    synchronized void cancel() {
        this.pending = null;
    }

    /**
     * The text inputs carried across. Kept a pure value record -- its equality is meaningful and is
     * asserted directly by the regression, which a {@code Runnable} field would silently reduce to
     * identity comparison. The exit callback therefore rides alongside it in {@link Claim} rather
     * than inside it.
     */
    public record Payload(String worldName, String seed) {}

    /**
     * Everything one screen claims: the text inputs, plus the way back out of the whole flow.
     *
     * <p>Both are nullable, and null is meaningful rather than a defect: a session armed from the
     * Select World screen carries neither, because it has no typed inputs to forward and no Latitude
     * screen to return to. A claimant must treat null as "leave vanilla's own behaviour alone".</p>
     */
    public record Claim(@Nullable Payload payload, @Nullable Runnable exitCreateFlow) {}

    private record Pending(Object claimKey, @Nullable Payload payload,
                           @Nullable Runnable exitCreateFlow, long armedAtNanos) {}
}
