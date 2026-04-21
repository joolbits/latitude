package com.example.globe.client;

import com.example.globe.GlobeMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class ClipboardImageWriter {
    public enum ClipboardCopyResult {
        SUCCESS,
        HEADLESS,
        UNAVAILABLE
    }

    private ClipboardImageWriter() {
    }

    public static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    public static ClipboardCopyResult copyToClipboard(NativeImage image) {
        if (isHeadless()) {
            GlobeMod.LOGGER.info("[latdev] Clipboard unavailable (headless)");
            return ClipboardCopyResult.HEADLESS;
        }

        try {
            BufferedImage bufferedImage = toBufferedImage(image);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransferable(bufferedImage), null);
            return ClipboardCopyResult.SUCCESS;
        } catch (HeadlessException e) {
            return ClipboardCopyResult.HEADLESS;
        } catch (IllegalStateException | UnsupportedOperationException e) {
            return ClipboardCopyResult.UNAVAILABLE;
        } catch (RuntimeException e) {
            GlobeMod.LOGGER.debug("[latdev] Clipboard copy failed", e);
            return ClipboardCopyResult.UNAVAILABLE;
        }
    }

    public static File saveToDisk(Minecraft client, NativeImage image) throws IOException {
        File capturesDir = ensureDirectory(new File(client.gameDirectory, "Latitude/captures"));
        File output = new File(capturesDir, nextCaptureName());
        image.writeToFile(output);
        return output;
    }

    public static File saveTempClipboardImage(Minecraft client, NativeImage image) throws IOException {
        File capturesDir = ensureDirectory(new File(client.gameDirectory, "Latitude/captures"));
        File tempDir = ensureDirectory(new File(capturesDir, ".tmp"));
        File output = new File(tempDir, nextCaptureName());
        image.writeToFile(output);
        return output;
    }

    public static File moveTempCaptureToCaptures(Minecraft client, File tempFile) throws IOException {
        File capturesDir = ensureDirectory(new File(client.gameDirectory, "Latitude/captures"));
        File destination = new File(capturesDir, tempFile.getName());
        if (destination.equals(tempFile)) {
            return destination;
        }
        Files.move(tempFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return destination;
    }

    public static void deleteQuietly(File file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException ignored) {
            GlobeMod.LOGGER.debug("[latdev] Failed to delete temp capture {}", file.getAbsolutePath());
        }
    }

    public static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(Locale.ROOT).contains("win");
    }

    public static boolean copyPngFileToClipboardWindowsPowerShell(Path pngPath) {
        if (pngPath == null || !Files.isRegularFile(pngPath) || !isWindows()) {
            return false;
        }

        String escapedPath = pngPath.toAbsolutePath().toString().replace("'", "''");
        String script = "$ErrorActionPreference='Stop';"
                + "Add-Type -AssemblyName System.Windows.Forms;"
                + "Add-Type -AssemblyName System.Drawing;"
                + "$img=[System.Drawing.Image]::FromFile('" + escapedPath + "');"
                + "try{[System.Windows.Forms.Clipboard]::SetImage($img)}finally{$img.Dispose()}";

        ProcessBuilder builder = new ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-Command",
                script
        );
        builder.redirectErrorStream(true);

        try {
            Process process = builder.start();
            boolean finished = process.waitFor(8, TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();

            if (!finished) {
                process.destroyForcibly();
                GlobeMod.LOGGER.warn("[latdev] PowerShell clipboard copy timed out");
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String outputSuffix = output.isEmpty() ? "" : " output=" + truncate(output, 240);
                GlobeMod.LOGGER.warn("[latdev] PowerShell clipboard copy failed (exitCode={}){}", exitCode, outputSuffix);
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            GlobeMod.LOGGER.warn("[latdev] PowerShell clipboard copy interrupted");
            return false;
        } catch (IOException e) {
            GlobeMod.LOGGER.warn("[latdev] Failed to run PowerShell clipboard backend: {}", e.getMessage());
            return false;
        }
    }

    public static File moveTempCaptureToCaptures(Minecraft client, Path tempPath) throws IOException {
        return moveTempCaptureToCaptures(client, tempPath.toFile());
    }

    private static BufferedImage toBufferedImage(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = image.getPixels();
        bufferedImage.setRGB(0, 0, width, height, pixels, 0, width);
        return bufferedImage;
    }

    private static String nextCaptureName() {
        return "capture-" + Util.getFilenameFormattedDateTime() + ".png";
    }

    private static String truncate(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "...";
    }

    private static File ensureDirectory(File directory) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create directory: " + directory.getAbsolutePath());
        }
        return directory;
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
