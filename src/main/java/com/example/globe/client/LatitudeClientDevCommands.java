package com.example.globe.client;

import com.mojang.brigadier.CommandDispatcher;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client-side {@code /latdev title} test command: fires the real big zone-enter title overlay for the zone
 * the player is CURRENTLY standing in, on demand, so a tester can preview it without walking across a zone
 * boundary.
 *
 * <p><b>Why client-side (and not a child of the existing server {@code /latdev} tree in
 * {@link com.example.globe.LatitudeDevCommands}):</b> the title overlay is a client-only rendering path
 * ({@link ZoneEnterTitleOverlay} / {@link GlobeWarningOverlay#debugFireZoneTitleNow}) that can't safely be
 * reached from the server dispatcher (that command class is loaded on dedicated servers too, where client
 * classes aren't reachable). So {@code title} is registered here via Fabric's
 * {@link ClientCommandRegistrationCallback} ({@link ClientCommands} / {@link FabricClientCommandSource},
 * package {@code net.fabricmc.fabric.api.client.command.v2}). Fabric checks client commands FIRST and only
 * intercepts what they explicitly register, so registering ONLY a {@code title} child under a client-side
 * {@code latdev} root does not collide with or shadow the server's existing {@code /latdev} subcommands --
 * anything unmatched (e.g. {@code /latdev here}) falls through to the server dispatcher completely unaffected.
 *
 * <p>Registration is only ever wired from {@code GlobeModClient} (a {@code ClientModInitializer}), so this is
 * unreachable on a dedicated server. The gating policy mirrors
 * {@link com.example.globe.LatitudeDevCommands#registerIfEnabled} exactly: skip in a dev environment (the full
 * dev command tree owns {@code /latdev} there), otherwise on for pre-release builds / when
 * {@code -Dlatitude.devCommands=true}, off for stable / when {@code -Dlatitude.devCommands=false}.
 */
public final class LatitudeClientDevCommands {
    private LatitudeClientDevCommands() {}

    public static void registerIfEnabled() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return; // in dev the full dev.LatitudeDevCommand owns /latdev; avoid any client-vs-full collision
        }
        if (!devCommandsEnabled()) {
            return;
        }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> register(dispatcher));
    }

    // Mirrors LatitudeDevCommands.devCommandsEnabled(): explicit -Dlatitude.devCommands=true/false always
    // wins; otherwise auto-on for pre-release builds (beta/alpha/rc/pre/snapshot), auto-off for stable.
    private static boolean devCommandsEnabled() {
        String explicit = System.getProperty("latitude.devCommands");
        if (explicit != null) {
            return Boolean.parseBoolean(explicit);
        }
        return isPrereleaseBuild();
    }

    private static boolean isPrereleaseBuild() {
        return FabricLoader.getInstance().getModContainer("globe")
                .map(c -> c.getMetadata().getVersion().getFriendlyString().toLowerCase(Locale.ROOT))
                .map(v -> v.contains("beta") || v.contains("alpha") || v.contains("-rc")
                        || v.contains("-pre") || v.contains("snapshot"))
                .orElse(false);
    }

    private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("latdev")
                .then(ClientCommands.literal("title")
                        .executes(LatitudeClientDevCommands::fireTitle)));
    }

    private static int fireTitle(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource src = ctx.getSource();
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) {
            src.sendError(Component.literal("[latdev] title: not in a world."));
            return 0;
        }
        // Only meaningful in a Latitude globe world -- otherwise there are no latitude zones to title.
        // Mirror the render()/GlobeClientState.evaluate() "active" gate used by the real crossing trigger.
        if (!GlobeClientState.evaluate(client).active()) {
            src.sendError(Component.literal(
                    "[latdev] title: this isn't a Latitude globe world (no latitude zones to show)."));
            return 0;
        }
        String titleText = GlobeWarningOverlay.debugFireZoneTitleNow(client);
        if (titleText == null) {
            src.sendError(Component.literal("[latdev] title: could not resolve the current zone."));
            return 0;
        }
        src.sendFeedback(Component.literal("[latdev] fired zone title: " + titleText));
        return 1;
    }
}
