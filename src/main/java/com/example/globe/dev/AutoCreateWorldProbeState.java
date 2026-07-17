package com.example.globe.dev;

final class AutoCreateWorldProbeState {
    enum Phase {
        IDLE,
        WAITING_FOR_CREATE_SCREEN,
        WAITING_FOR_CONFIRM,
        WAITING_FOR_WORLD_OR_BLOCKER,
        WAITING_FOR_POST_ENTRY_CAPTURE,
        TIMED_OUT,
        COMPLETE
    }

    private static Phase phase = Phase.IDLE;
    private static long startMs = 0L;
    private static long timeoutMs = 45_000L;
    private static boolean opened = false;
    private static boolean confirmed = false;
    private static boolean worldEntered = false;
    private static long worldEnteredGameTime = -1L;
    private static boolean timedOut = false;
    private static boolean logged = false;
    private static boolean screenDetectedLogged = false;
    private static boolean creativeApplied = false;
    private static boolean diagnosticsCaptured = false;

    private AutoCreateWorldProbeState() {
    }

    static synchronized void reset(long requestedTimeoutMs) {
        phase = Phase.WAITING_FOR_CREATE_SCREEN;
        startMs = System.currentTimeMillis();
        timeoutMs = requestedTimeoutMs > 0L ? requestedTimeoutMs : 45_000L;
        opened = false;
        confirmed = false;
        worldEntered = false;
        worldEnteredGameTime = -1L;
        timedOut = false;
        logged = false;
        screenDetectedLogged = false;
        creativeApplied = false;
        diagnosticsCaptured = false;
    }

    static synchronized Phase getPhase() {
        return phase;
    }

    static synchronized long getStartMs() {
        return startMs;
    }

    static synchronized long getTimeoutMs() {
        return timeoutMs;
    }

    static synchronized boolean isOpened() {
        return opened;
    }

    static synchronized void markOpened() {
        opened = true;
        phase = Phase.WAITING_FOR_CREATE_SCREEN;
    }

    static synchronized boolean isConfirmed() {
        return confirmed;
    }

    static synchronized void markConfirmed() {
        confirmed = true;
        phase = Phase.WAITING_FOR_WORLD_OR_BLOCKER;
    }

    static synchronized boolean isWorldEntered() {
        return worldEntered;
    }

    static synchronized void markWorldEntered(long gameTime) {
        worldEntered = true;
        worldEnteredGameTime = gameTime;
        phase = Phase.WAITING_FOR_POST_ENTRY_CAPTURE;
    }

    static synchronized boolean isTimedOut() {
        return timedOut;
    }

    static synchronized void markTimedOut() {
        timedOut = true;
        phase = Phase.TIMED_OUT;
    }

    static synchronized boolean isLogged() {
        return logged;
    }

    static synchronized void markLogged() {
        logged = true;
    }

    static synchronized boolean isScreenDetectedLogged() {
        return screenDetectedLogged;
    }

    static synchronized void markScreenDetectedLogged() {
        screenDetectedLogged = true;
        phase = Phase.WAITING_FOR_CONFIRM;
    }

    static synchronized boolean isCreativeApplied() {
        return creativeApplied;
    }

    static synchronized void markCreativeApplied() {
        creativeApplied = true;
    }

    static synchronized long getWorldEnteredGameTime() {
        return worldEnteredGameTime;
    }

    static synchronized boolean isDiagnosticsCaptured() {
        return diagnosticsCaptured;
    }

    static synchronized void markDiagnosticsCaptured() {
        diagnosticsCaptured = true;
        phase = Phase.COMPLETE;
    }
}
