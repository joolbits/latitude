package com.example.globe;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

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

    public record GlobeStatePayload(boolean isGlobe) implements CustomPacketPayload {
        public static final Type<GlobeStatePayload> ID = new Type<>(Identifier.fromNamespaceAndPath("globe", "s2c_globe_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GlobeStatePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL,
                GlobeStatePayload::isGlobe,
                GlobeStatePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record OpenSpawnPickerPayload(boolean open) implements CustomPacketPayload {
        public static final Type<OpenSpawnPickerPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("globe", "s2c_open_spawn_picker"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenSpawnPickerPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL,
                OpenSpawnPickerPayload::open,
                OpenSpawnPickerPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record SetSpawnPickerPayload(String zoneId) implements CustomPacketPayload {
        public static final Type<SetSpawnPickerPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("globe", "c2s_set_spawn_picker"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetSpawnPickerPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,
                SetSpawnPickerPayload::zoneId,
                SetSpawnPickerPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
