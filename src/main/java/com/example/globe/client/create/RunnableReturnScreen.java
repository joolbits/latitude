package com.example.globe.client.create;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Adapts a return callback to the {@link Screen} that 1.21.1's create-world screen takes as its
 * "go back here" target.
 *
 * <p>The 1.21.9+ lines construct {@code CreateWorldScreen} with a {@code Runnable onClose}, so
 * Latitude can hand vanilla a callback that first absorbs the player's edits and only then swaps
 * back. 1.21.1 takes a {@code Screen lastScreen} and does a bare {@code setScreen(lastScreen)}
 * instead, which would drop that absorb step. This screen restores it: vanilla returns to it, its
 * {@code init} runs the callback, and the callback puts the real Latitude screen up. It is never
 * rendered — the swap happens during initialisation, before the first frame.
 *
 * <p>It is also the handoff claim key on this target, exactly as the {@code Runnable} is on the
 * newer lines: the screen vanilla is constructed with is the only one that can claim the payload.
 */
public final class RunnableReturnScreen extends Screen {
    private final Runnable action;

    public RunnableReturnScreen(Runnable action) {
        super(Component.empty());
        this.action = action;
    }

    @Override
    protected void init() {
        this.action.run();
    }

    @Override
    public void onClose() {
        this.action.run();
    }
}
