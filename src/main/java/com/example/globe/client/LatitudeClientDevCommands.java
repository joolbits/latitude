package com.example.globe.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

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
 * package {@code net.fabricmc.fabric.api.client.command.v2}).
 *
 * <p><b>Client root SHADOWS the server tree -- so we must forward.</b> Fabric checks client commands FIRST:
 * {@code ClientPacketListenerMixin} injects at the HEAD of {@code ClientPacketListener.sendCommand(String)}
 * and hands every typed command to {@code ClientCommandInternals.executeCommand}, which runs the client
 * dispatcher. That method only lets a command "fall through" to the server when the client parse throws
 * {@code dispatcherUnknownCommand} (root literal totally unknown to the client) or
 * {@code dispatcherParseException} (see {@code isIgnoredException}); ANY OTHER parse failure is caught,
 * {@code source.sendError(...)}'d, and the command is treated as consumed -- it is NEVER forwarded. So once a
 * client-side {@code latdev} root exists, typing {@code /latdev here} matches the root literal but fails on
 * the missing {@code here} child, throwing a {@code literalIncorrect}-type exception that is NOT ignored:
 * the whole server {@code /latdev} tree is shadowed and the player sees "Incorrect argument for command".
 * To keep BOTH the client-only {@code title} preview AND every server subcommand, this root therefore adds an
 * explicit passthrough: a bare-{@code latdev} {@code .executes(...)} and a greedy {@code subcommand} argument
 * child whose {@code .executes(...)} forwards the command verbatim to the server (Brigadier tries the
 * {@code title} LITERAL before the greedy argument, so {@code title} still runs client-side while everything
 * else is forwarded). See {@link #forwardToServer} for why forwarding sends the packet directly instead of
 * re-calling {@code sendCommand} (which would re-enter the same mixin and recurse).
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
                // The one genuinely client-only subcommand. Brigadier evaluates LITERAL children before the
                // greedy argument below, so "/latdev title" always resolves here (never the passthrough).
                .then(ClientCommands.literal("title")
                        .executes(LatitudeClientDevCommands::fireTitle))
                // Bare "/latdev" -> forward verbatim so the server can print its own root usage/help.
                .executes(ctx -> forwardToServer(ctx.getSource(), "latdev"))
                // Everything that isn't "title" (e.g. "/latdev here", "/latdev tpband temperate low") lands
                // on this greedy passthrough and is forwarded to the server dispatcher verbatim, restoring
                // every pre-existing server subcommand. Empty suggestions keep this node from injecting a
                // noisy "<subcommand>" placeholder into tab-completion -- the server's own children (here,
                // tpband, ...) still appear because Fabric merges client command children into the known
                // server command tree under the same-name root.
                .then(ClientCommands.argument("subcommand", StringArgumentType.greedyString())
                        .suggests((c, b) -> b.buildFuture())
                        .executes(ctx -> forwardToServer(ctx.getSource(),
                                "latdev " + StringArgumentType.getString(ctx, "subcommand")))));
    }

    /**
     * Forwards {@code command} (no leading slash) to the SERVER dispatcher by sending a serverbound command
     * packet directly, exactly as vanilla {@code ClientPacketListener.sendCommand(String)} does for commands
     * with no signable arguments (its {@code SignableCommand.arguments().isEmpty()} branch sends a plain
     * {@link ServerboundChatCommandPacket}).
     *
     * <p><b>Why not just call {@code connection.sendCommand(command)}:</b> fabric-command-api-v2's
     * {@code ClientPacketListenerMixin} injects at the HEAD of {@code sendCommand(String)} and re-routes the
     * string through the client dispatcher. Calling {@code sendCommand} from inside this very passthrough
     * would re-enter that mixin, re-match this greedy child, and recurse forever -- and because the mixin
     * cancels the inner {@code sendCommand}, the command would never actually reach the server anyway.
     * {@code ClientCommonPacketListenerImpl.send(Packet)} is NOT intercepted, so sending the packet directly
     * is a single, safe, non-recursive hop. (Dev commands carry no signed/message arguments, so the unsigned
     * packet is exactly what vanilla would have sent.)
     */
    private static int forwardToServer(FabricClientCommandSource src, String command) {
        LocalPlayer player = src.getPlayer();
        if (player == null || player.connection == null) {
            src.sendError(Component.literal("[latdev] not connected to a server; cannot run: " + command));
            return 0;
        }
        player.connection.send(new ServerboundChatCommandPacket(command));
        return 1;
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
