package com.example.globe;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class GlobeNet {
    private static boolean registered;

    private GlobeNet() {
    }

    // Channel identifiers (1.20.1 networking uses raw Identifiers, not CustomPayload)
    public static final Identifier S2C_GLOBE_STATE = new Identifier("globe", "s2c_globe_state");
    public static final Identifier S2C_OPEN_SPAWN_PICKER = new Identifier("globe", "s2c_open_spawn_picker");
    public static final Identifier C2S_SET_SPAWN_PICKER = new Identifier("globe", "c2s_set_spawn_picker");

    public static void registerPayloads() {
        if (registered) {
            return;
        }
        registered = true;
        // In 1.20.1 Fabric Networking v1, channels are registered on use (no PayloadTypeRegistry).
        // Server-side receivers are registered in GlobeMod; client-side in GlobeModClient.
    }

    // --- Payload helper records (data-only, no CustomPayload interface) ---

    public record GlobeStatePayload(boolean isGlobe) {
        public PacketByteBuf write() {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBoolean(isGlobe);
            return buf;
        }

        public static GlobeStatePayload read(PacketByteBuf buf) {
            return new GlobeStatePayload(buf.readBoolean());
        }
    }

    public record OpenSpawnPickerPayload(boolean open) {
        public PacketByteBuf write() {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBoolean(open);
            return buf;
        }

        public static OpenSpawnPickerPayload read(PacketByteBuf buf) {
            return new OpenSpawnPickerPayload(buf.readBoolean());
        }
    }

    public record SetSpawnPickerPayload(String zoneId) {
        public PacketByteBuf write() {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(zoneId);
            return buf;
        }

        public static SetSpawnPickerPayload read(PacketByteBuf buf) {
            return new SetSpawnPickerPayload(buf.readString());
        }
    }
}
