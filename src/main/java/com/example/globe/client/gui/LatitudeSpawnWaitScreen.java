package com.example.globe.client.gui;

import com.example.globe.client.LatitudeClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkManager;
import net.minecraft.world.chunk.ChunkStatus;

public class LatitudeSpawnWaitScreen extends Screen {
    private static final int CHECK_RADIUS = 1; // 3x3 chunks

    public LatitudeSpawnWaitScreen() {
        super(Text.literal("Preparing spawn area…"));
    }

    @Override
    public void tick() {
        if (isReady()) {
            LatitudeClientState.firstWorldLoad = false;
            LatitudeClientState.firstWorldLoadStartMs = 0L;
            MinecraftClient.getInstance().setScreen(null);
        }
    }

    private boolean isReady() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (!LatitudeClientState.firstWorldLoad) return true;
        if (world == null || client.player == null) return false;

        int cx = client.player.getBlockPos().getX() >> 4;
        int cz = client.player.getBlockPos().getZ() >> 4;
        ChunkManager mgr = world.getChunkManager();
        for (int dx = -CHECK_RADIUS; dx <= CHECK_RADIUS; dx++) {
            for (int dz = -CHECK_RADIUS; dz <= CHECK_RADIUS; dz++) {
                Chunk chunk = mgr.getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Let vanilla draw its background/blur first.
        super.render(context, mouseX, mouseY, delta);

        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();

        String line1 = "Preparing spawn area…";
        String line2 = "First load can take longer.";
        int cx = w / 2;
        int cy = h / 2;
        var tr = this.textRenderer;
        int y = cy - 10;
        int w1 = tr.getWidth(line1);
        context.drawText(tr, line1, cx - w1 / 2, y, 0xFFFFFFFF, false);
        int w2 = tr.getWidth(line2);
        context.drawText(tr, line2, cx - w2 / 2, y + 12, 0xFFCCCCCC, false);
    }
}
