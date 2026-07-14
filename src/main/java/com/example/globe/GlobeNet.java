package com.example.globe;

import com.example.globe.core.PassageAxis;
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
     * re-validates the edge distance for the given {@link PassageAxis}, then teleports); {@code cross=false} =
     * "turn back" (server applies a one-shot nudge back toward the equator/center on that axis). The client is
     * trusted for NOTHING beyond delivering {@code (cross, axis)} -- the server re-derives the player's
     * authoritative distance to that edge and rejects a spoofed {@code cross} from out of band.
     *
     * <p><b>B-7 axis extension.</b> {@code axis} (0 = EW, 1 = POLE) selects the edge; one registered payload,
     * one server receiver, one guard chain with an axis branch (design §5.2). Field order: {@code (cross, axis)}.
     * Client+server ship in the same jar, so widening the codec is lockstep-safe.
     */
    public record PassageAnswerPayload(boolean cross, PassageAxis axis) implements CustomPacketPayload {
        public static final Type<PassageAnswerPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("globe", "c2s_passage_answer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PassageAnswerPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL,
                PassageAnswerPayload::cross,
                ByteBufCodecs.VAR_INT,
                p -> p.axis().id(),
                (cross, axisId) -> new PassageAnswerPayload(cross, PassageAxis.fromId(axisId))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * S2C: sent ONLY to the crossing player after a successful teleport (never broadcast). Carries the
     * {@link PassageAxis} and the arrival {@code (X, Z)} so P2 can (a) derive the arrived hemisphere for the
     * arrival title -- EW reads {@code arrivalX >= centerX} = East, POLE flips the E/W hemisphere too via the
     * mirrored X -- and (b) seed the RIGHT client passage arm DISARMED-in-band (the mirror lands at the
     * identical edge distance on that axis, so without this the client would self-reprompt forever). P1's client
     * receiver routes EW as B-5 shipped and treats POLE as a documented stub (P2 builds the pole arm/title).
     *
     * <p><b>B-7 axis extension.</b> Field order: {@code (axis, arrivalX, arrivalZ)} (design §5.2). {@code arrivalZ}
     * is new -- the pole arrival changes latitude (lands at the 89.5 deg S5 arrival line) where the EW arrival barely does; both are
     * VAR_INT and round-trip signed coordinates exactly as the original {@code arrivalX} did.
     */
    public record PassageArrivalPayload(PassageAxis axis, int arrivalX, int arrivalZ) implements CustomPacketPayload {
        public static final Type<PassageArrivalPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("globe", "s2c_passage_arrival"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PassageArrivalPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                p -> p.axis().id(),
                ByteBufCodecs.VAR_INT,
                PassageArrivalPayload::arrivalX,
                ByteBufCodecs.VAR_INT,
                PassageArrivalPayload::arrivalZ,
                (axisId, x, z) -> new PassageArrivalPayload(PassageAxis.fromId(axisId), x, z)
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
