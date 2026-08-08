package com.example.globe.core;

import java.util.Locale;

/**
 * Pure predicate over the mod version string: is this a pre-release build?
 *
 * <p>Extracted from {@code LatitudeDevCommands.isPrereleaseBuild} (2026-08-07) so the gate that
 * decides whether {@code /latdev} auto-registers on a SHIPPED jar is unit-pinned per version
 * string — the version string is a security-relevant switch (dev commands on players' servers),
 * and before this extraction nothing tested it. Behavior identical to the inlined original.
 */
public final class VersionChannel {

    private VersionChannel() {
    }

    /** True for beta/alpha/rc/pre/snapshot version strings; false for stable (and null). */
    public static boolean isPrerelease(String friendlyVersion) {
        if (friendlyVersion == null) {
            return false;
        }
        String v = friendlyVersion.toLowerCase(Locale.ROOT);
        return v.contains("beta") || v.contains("alpha") || v.contains("-rc")
                || v.contains("-pre") || v.contains("snapshot");
    }
}
