package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the version-string gate that auto-enables {@code /latdev} on shipped jars.
 * A drift here silently turns dev commands on (or off) for players — the exact class of
 * quiet regression the 2026-08-07 campaign audit flagged as untested.
 */
class VersionChannelTest {

    @Test
    void prereleaseChannelsEnableTheGate() {
        assertTrue(VersionChannel.isPrerelease("2.0-beta.1+26.2"));
        assertTrue(VersionChannel.isPrerelease("2.0-alpha.3"));
        assertTrue(VersionChannel.isPrerelease("2.0-rc.1"));
        assertTrue(VersionChannel.isPrerelease("2.0-pre.2"));
        assertTrue(VersionChannel.isPrerelease("26.2-SNAPSHOT"));
        assertTrue(VersionChannel.isPrerelease("2.0-BETA.1"), "case-insensitive");
    }

    @Test
    void stableChannelsKeepTheGateOff() {
        assertFalse(VersionChannel.isPrerelease("2.0.0+26.2"));
        assertFalse(VersionChannel.isPrerelease("1.5.0+1.21.11"));
        assertFalse(VersionChannel.isPrerelease("2.0.1"));
        assertFalse(VersionChannel.isPrerelease(null), "unresolvable version must fail CLOSED (off)");
    }

    @Test
    void buildMetadataAloneDoesNotFakeAChannel() {
        // '+26.2' style build metadata must not trip the gate; only real qualifiers do.
        assertFalse(VersionChannel.isPrerelease("2.0.0+build.7"));
    }
}
