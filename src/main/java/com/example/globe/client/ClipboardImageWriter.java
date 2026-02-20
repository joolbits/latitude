package com.example.globe.client;

import com.example.globe.GlobeMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Util;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public final class ClipboardImageWriter {
    private ClipboardImageWriter() {
    }

    public static boolean copyToClipboard(NativeImage image) {
        try {
            BufferedImage bufferedImage = toBufferedImage(image);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransferable(bufferedImage), null);
            return true;
        } catch (Throwable t) {
            GlobeMod.LOGGER.warn("[latdev] Clipboard copy failed", t);
            return false;
        }
    }

    public static File saveToDisk(MinecraftClient client, NativeImage image) throws IOException {
        File capturesDir = new File(client.runDirectory, "Latitude/captures");
        if (!capturesDir.exists() && !capturesDir.mkdirs()) {
            throw new IOException("Failed to create captures directory: " + capturesDir.getAbsolutePath());
        }

        File output = new File(capturesDir, "capture-" + Util.getFormattedCurrentTime() + ".png");
        image.writeTo(output);
        return output;
    }

    private static BufferedImage toBufferedImage(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = image.copyPixelsArgb();
        bufferedImage.setRGB(0, 0, width, height, pixels, 0, width);
        return bufferedImage;
    }

    private static final class ImageTransferable implements Transferable {
        private final Image image;

        private ImageTransferable(Image image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) {
            if (!isDataFlavorSupported(flavor)) {
                return null;
            }
            return image;
        }
    }
}
