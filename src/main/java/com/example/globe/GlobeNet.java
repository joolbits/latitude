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

        PayloadTypeRegistry.clientboundPlay().register(GlobeStatePayload.ID, GlobeStatePayload.CODEC);

        PayloadTypeRegistry.clientboundPlay().register(OpenSpawnPickerPayload.ID, OpenSpawnPickerPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetSpawnPickerPayload.ID, SetSpawnPickerPayload.CODEC);
    }

    public record GlobeStatePayload(boolean isGlobe, int latitudeZRadius) implements CustomPacketPayload {
        public static final Type<GlobeStatePayload> ID = new Type<>(Identifier.fromNamespaceAndPath("globe", "s2c_globe_state"));
        // latitudeZRadius is the Z (latitude) radius in blocks. For Mercator worlds this differs from the
        // (X-sized) WorldBorder half, so the client needs it to render correct latitude/zone/pole HUD.
        // 0 means "use the border half" (Classic / unknown).
        public static final StreamCodec<RegistryFriendlyByteBuf, GlobeStatePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL,
                GlobeStatePayload::isGlobe,
                ByteBufCodecs.VAR_INT,
                GlobeStatePayload::latitudeZRadius,
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
