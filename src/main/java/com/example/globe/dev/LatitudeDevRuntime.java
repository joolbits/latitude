package com.example.globe.dev;

import com.example.globe.GlobeMod;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;

/**
 * One immutable runtime identity and activation policy for excluded development tooling.
 */
public final class LatitudeDevRuntime {
    public static final String TEST_MARKER_KEY = "latitude:test_artifact";
    private static final BuildIdentity IDENTITY = resolve();

    private LatitudeDevRuntime() {
    }

    public static BuildIdentity identity() {
        return IDENTITY;
    }

    public static boolean isToolingEnabled() {
        return IDENTITY.toolingAllowed();
    }

    public static boolean isDevelopmentEnvironment() {
        return IDENTITY.developmentEnvironment();
    }

    public static boolean isPackagedTestArtifact() {
        return IDENTITY.packagedTest();
    }

    /** SHA-256 of the exact packaged Latitude JAR, or an honest non-JAR state label. */
    public static String artifactSha256() {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(GlobeMod.MOD_ID);
        if (container.isEmpty()) {
            return "unavailable";
        }
        List<Path> jars = container.get().getOrigin().getPaths().stream()
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".jar"))
                .toList();
        if (jars.size() != 1) {
            return IDENTITY.developmentEnvironment() ? "development-classes" : "unavailable";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(jars.getFirst())) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            return "unavailable";
        }
    }

    private static BuildIdentity resolve() {
        FabricLoader loader = FabricLoader.getInstance();
        boolean developmentEnvironment = loader.isDevelopmentEnvironment();
        Optional<ModContainer> container = loader.getModContainer(GlobeMod.MOD_ID);
        String metadataVersion = container
                .map(value -> value.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        boolean testMarker = container.map(LatitudeDevRuntime::hasTestMarker).orElse(false);
        Attributes manifest = container.map(LatitudeDevRuntime::readManifest).orElseGet(Attributes::new);

        if (developmentEnvironment) {
            return new BuildIdentity(
                    true,
                    true,
                    false,
                    "LOOM_DEV",
                    0,
                    metadataVersion,
                    property("latitude.dev.gitCommit"),
                    property("latitude.dev.gitBranch"),
                    property("latitude.dev.buildDirty"),
                    property("latitude.dev.buildTime"));
        }

        String role = attribute(manifest, "Latitude-Artifact-Role");
        int sequence = parsePositiveInt(attribute(manifest, "Latitude-Test-Sequence"));
        String manifestVersion = attribute(manifest, "Implementation-Version");
        String artifactVersion = attribute(manifest, "Latitude-Artifact-Version");
        String commit = attribute(manifest, "Git-Commit");
        String branch = attribute(manifest, "Git-Branch");
        String dirty = attribute(manifest, "Build-Dirty");
        String time = attribute(manifest, "Build-Time");
        boolean valid = DevToolPolicy.packagedTestIdentityValid(
                testMarker,
                metadataVersion,
                role,
                sequence,
                manifestVersion,
                artifactVersion,
                commit,
                branch,
                dirty,
                time);
        return new BuildIdentity(
                valid,
                false,
                valid,
                role,
                sequence,
                metadataVersion,
                commit,
                branch,
                dirty,
                time);
    }

    private static boolean hasTestMarker(ModContainer container) {
        if (!container.getMetadata().containsCustomValue(TEST_MARKER_KEY)) {
            return false;
        }
        CustomValue value = container.getMetadata().getCustomValue(TEST_MARKER_KEY);
        return value != null
                && value.getType() == CustomValue.CvType.BOOLEAN
                && value.getAsBoolean();
    }

    private static Attributes readManifest(ModContainer container) {
        try (InputStream input = container.findPath("META-INF/MANIFEST.MF")
                .map(path -> {
                    try {
                        return Files.newInputStream(path);
                    } catch (IOException ignored) {
                        return null;
                    }
                })
                .orElse(null)) {
            return input == null
                    ? new Attributes()
                    : new Manifest(input).getMainAttributes();
        } catch (IOException ignored) {
            return new Attributes();
        }
    }

    private static String attribute(Attributes attributes, String key) {
        String value = attributes.getValue(key);
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private static String property(String key) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private static int parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public record BuildIdentity(
            boolean toolingAllowed,
            boolean developmentEnvironment,
            boolean packagedTest,
            String role,
            int sequence,
            String version,
            String commit,
            String branch,
            String dirty,
            String time
    ) {
    }
}
