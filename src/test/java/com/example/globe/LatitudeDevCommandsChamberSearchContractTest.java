package com.example.globe;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Raw-source law for {@code /latdev locateChamber}'s per-tick budget.
 *
 * <h2>What this class exists to stop happening again</h2>
 * The tick hook checks its wall-clock budget between RING CHUNKS only. One ring chunk, however, is not one
 * chunk of work: {@code visitNextChunk} force-generates the chunk itself, and when that chunk holds any
 * collapse cell {@code classifyAround} then force-generates its four cardinal neighbours AND hands the
 * reconstruction a generating {@code ChamberCellReader}, whose chamber box reaches far enough to pull in a
 * further ring. A single tick could therefore synchronously generate a 7x7 block of chunks with no budget
 * check anywhere inside it -- seconds of server freeze from a command whose whole design is "a couple of
 * chunks per tick so the server stays playable".
 *
 * <p>The fix keeps the search's shape and makes the generation inside {@code classifyAround} budget-aware:
 * after each chunk it brings to FULL it asks the job whether the tick's allowance is spent, and if it is the
 * pending patch is REQUEUED rather than dropped, so the next tick re-enters {@code classifyAround} for that
 * chunk instead of losing the mouth. The reconstruction read that follows generation is allowed to finish.
 *
 * <p>{@code LatitudeDevCommands} cannot be loaded in a unit test (it is a Brigadier command class over live
 * Minecraft types), so this is a source-structure tripwire -- the same idiom the chamber feature's own
 * contract tests use.
 */
class LatitudeDevCommandsChamberSearchContractTest {

    private static String commandsSource() throws IOException {
        return Files.readString(Path.of("src/main/java/com/example/globe/LatitudeDevCommands.java"));
    }

    @Test
    void classifyAroundChecksTheTickBudgetAroundEveryChunkItForceGenerates() throws IOException {
        String source = commandsSource();

        int classifyAt = source.indexOf("private void classifyAround(");
        assertTrue(classifyAt > 0, "the wide search still classifies the neighbourhood of a mouth");
        int classifyEnd = source.indexOf("\n        }\n    }", classifyAt);
        assertTrue(classifyEnd > classifyAt, "could not find the end of classifyAround");
        String body = source.substring(classifyAt, classifyEnd);

        assertTrue(body.contains("getChunk(neighbourX, neighbourZ, ChunkStatus.FULL, true)"),
                "classifyAround still brings the four cardinal neighbours to FULL");
        assertTrue(body.contains("tickBudgetSpent()"),
                "every chunk classifyAround force-generates must be followed by a tick-budget check: "
                        + "without one, a single ring chunk can generate a whole neighbourhood in one tick");
        int generateAt = body.indexOf("getChunk(neighbourX, neighbourZ, ChunkStatus.FULL, true)");
        int budgetAt = body.indexOf("tickBudgetSpent()", generateAt);
        assertTrue(budgetAt > generateAt,
                "the budget check must sit INSIDE the neighbour-generation loop, after the generating call");
        assertTrue(body.contains("requeue"),
                "a tick that runs out of budget mid-neighbourhood must REQUEUE the pending patch, never "
                        + "drop it: the mouth would otherwise be silently skipped by the search");

        assertTrue(source.contains("boolean tickBudgetSpent()"),
                "the job owns the budget predicate, so the tick hook and classifyAround measure the same "
                        + "allowance against the same tick start");
        assertTrue(source.contains("tickStartNanos"),
                "the budget is measured from the tick's own start, not from a fresh timer per chunk");
    }
}
