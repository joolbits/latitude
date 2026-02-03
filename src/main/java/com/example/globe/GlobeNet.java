package com.example.globe;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class GlobeNet {
    private static boolean registered;

    private GlobeNet() {
    }

    public static void registerPayloads() {
        if (registered) {
            return;
        }
        registered = true;

        PayloadTypeRegistry.playS2C().register(GlobeStatePayload.ID, GlobeStatePayload.CODEC);

        PayloadTypeRegistry.playS2C().register(OpenSpawnPickerPayload.ID, OpenSpawnPickerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SetSpawnPickerPayload.ID, SetSpawnPickerPayload.CODEC);
    }

    public record GlobeStatePayload(boolean isGlobe) implements CustomPayload {
        public static final Id<GlobeStatePayload> ID = new Id<>(Identifier.of("globe", "s2c_globe_state"));
        public static final PacketCodec<RegistryByteBuf, GlobeStatePayload> CODEC = PacketCodec.tuple(
                PacketCodecs.BOOL,
                GlobeStatePayload::isGlobe,
                GlobeStatePayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record OpenSpawnPickerPayload(boolean open) implements CustomPayload {
        public static final Id<OpenSpawnPickerPayload> ID = new Id<>(Identifier.of("globe", "s2c_open_spawn_picker"));
        public static final PacketCodec<RegistryByteBuf, OpenSpawnPickerPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.BOOL,
                OpenSpawnPickerPayload::open,
                OpenSpawnPickerPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record SetSpawnPickerPayload(String zoneId) implements CustomPayload {
        public static final Id<SetSpawnPickerPayload> ID = new Id<>(Identifier.of("globe", "c2s_set_spawn_picker"));
        public static final PacketCodec<RegistryByteBuf, SetSpawnPickerPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING,
                SetSpawnPickerPayload::zoneId,
                SetSpawnPickerPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
