package com.sqware.parcel;

final class HeartbeatPayload {
    private final String pluginVersion;
    private final long observedAtUnixMillis;
    private final long pluginUptimeSeconds;
    private final String serverSoftware;
    private final String serverVersion;
    private final String bukkitVersion;
    private final int serverPort;
    private final boolean onlineMode;
    private final int onlinePlayerCount;
    private final int maxPlayers;
    private final int pendingConfirmations;
    private final int inFlightDeliveries;

    HeartbeatPayload(
            String pluginVersion,
            long observedAtUnixMillis,
            long pluginUptimeSeconds,
            String serverSoftware,
            String serverVersion,
            String bukkitVersion,
            int serverPort,
            boolean onlineMode,
            int onlinePlayerCount,
            int maxPlayers,
            int pendingConfirmations,
            int inFlightDeliveries
    ) {
        this.pluginVersion = pluginVersion;
        this.observedAtUnixMillis = observedAtUnixMillis;
        this.pluginUptimeSeconds = pluginUptimeSeconds;
        this.serverSoftware = serverSoftware;
        this.serverVersion = serverVersion;
        this.bukkitVersion = bukkitVersion;
        this.serverPort = serverPort;
        this.onlineMode = onlineMode;
        this.onlinePlayerCount = onlinePlayerCount;
        this.maxPlayers = maxPlayers;
        this.pendingConfirmations = pendingConfirmations;
        this.inFlightDeliveries = inFlightDeliveries;
    }

    String pluginVersion() {
        return pluginVersion;
    }

    long observedAtUnixMillis() {
        return observedAtUnixMillis;
    }

    long pluginUptimeSeconds() {
        return pluginUptimeSeconds;
    }

    String serverSoftware() {
        return serverSoftware;
    }

    String serverVersion() {
        return serverVersion;
    }

    String bukkitVersion() {
        return bukkitVersion;
    }

    int serverPort() {
        return serverPort;
    }

    boolean onlineMode() {
        return onlineMode;
    }

    int onlinePlayerCount() {
        return onlinePlayerCount;
    }

    int maxPlayers() {
        return maxPlayers;
    }

    int pendingConfirmations() {
        return pendingConfirmations;
    }

    int inFlightDeliveries() {
        return inFlightDeliveries;
    }
}
