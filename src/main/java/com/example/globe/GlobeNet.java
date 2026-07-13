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

        // Phase 5 B-5 Hemisphere Passage. Built fresh on the LIVE GlobeStatePayload idiom (record + Type +
        // StreamCodec) -- deliberately NOT reusing the orphaned OpenSpawnPicker/SetSpawnPicker plumbing (the
        // SpawnZoneScreen those served was deleted). C2S = the player's answer to the crossing prompt; S2C =
        // the per-crossing-player arrival signal (P2 consumes it for the arrival title + to seed the client
        // arm state disarmed-in-band). Both inert unless latitude.passageV2.enabled.
        PayloadTypeRegistry.serverboundPlay().register(PassageAnswerPayload.ID, PassageAnswerPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PassageArrivalPayload.ID, PassageArrivalPayload.CODEC);
    }

    public record GlobeStatePayload(boolean isGlobe, int latitudeZRadius, int intendedXRadius) implements CustomPacketPayload {
        public static final Type<GlobeStatePayload> ID = new Type<>(Identifier.fromNamespaceAndPath("globe", "s2c_globe_state"));
        // latitudeZRadius is the Z (latitude) radius in blocks. For Mercator worlds this differs from the
        // (X-sized) WorldBorder half, so the client needs it to render correct latitude/zone/pole HUD.
        // intendedXRadius is the mod's OWN E/W radius (zRadius in Classic, zRadius*ASPECT in Wide): the client
        // anchors ALL E/W-edge feature geometry (fog/prompt/re-arm/banner) on it instead of the live
        // border half, so a lerping/vandalized border can't slide those lines (TEST 86 finding). Both 0 mean
        // "use the border half" (Classic byte-identical / not-a-globe).
        public static final StreamCodec<RegistryFriendlyByteBuf, GlobeStatePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL,
                GlobeStatePayload::isGlobe,
                ByteBufCodecs.VAR_INT,
                GlobeStatePayload::latitudeZRadius,
                ByteBufCodecs.VAR_INT,
                GlobeStatePayload::intendedXRadius,
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

    /**
     * C2S: the player's answer to the hemisphere-passage prompt. {@code cross=true} = "pass through" (server
     * re-validates the edge distance, then teleports); {@code cross=false} = "turn back" (server applies a
     * one-shot inland nudge). The client is trusted for NOTHING beyond delivering the boolean -- the server
     * re-derives the player's authoritative edge distance and rejects a spoofed {@code cross} from inland.
     */
    public record PassageAnswerPayload(boolean cross) implements CustomPacketPayload {
        public static final Type<PassageAnswerPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("globe", "c2s_passage_answer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PassageAnswerPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL,
                PassageAnswerPayload::cross,
                PassageAnswerPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * S2C: sent ONLY to the crossing player after a successful teleport (never broadcast). Carries the arrival
     * X so P2 can derive the arrived hemisphere ({@code arrivalX >= centerX} = East) for the arrival title and
     * seed the client passage arm state DISARMED-in-band (the mirror lands at the identical border distance, so
     * without this the client would self-reprompt forever). P1's client receiver is a minimal stub.
     */
    public record PassageArrivalPayload(int arrivalX) implements CustomPacketPayload {
        public static final Type<PassageArrivalPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("globe", "s2c_passage_arrival"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PassageArrivalPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                PassageArrivalPayload::arrivalX,
                PassageArrivalPayload::new
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
