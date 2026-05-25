package com.sqware.parcel;

import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;

final class ConnectConfig {
    private static final URI SQWARE_API_URI = URI.create("https://sqware.gg");
    private static final int POLL_INTERVAL_SECONDS = 15;
    private static final int MAX_TOKEN_LENGTH = 2_048;
    private static final int DEFAULT_JOIN_DELIVERY_DELAY_SECONDS = 2;
    private static final int MAX_JOIN_DELIVERY_DELAY_SECONDS = 30;

    private final String apiToken;
    private final boolean debug;
    private final int joinDeliveryDelaySeconds;

    private ConnectConfig(
            String apiToken,
            boolean debug,
            int joinDeliveryDelaySeconds
    ) {
        this.apiToken = apiToken;
        this.debug = debug;
        this.joinDeliveryDelaySeconds = joinDeliveryDelaySeconds;
    }

    static ConnectConfig from(FileConfiguration config) {
        String apiToken = config.getString("api-token", "").trim();
        validateApiToken(apiToken);
        int joinDeliveryDelaySeconds = config.getInt(
                "join-delivery-delay-seconds",
                DEFAULT_JOIN_DELIVERY_DELAY_SECONDS
        );
        validateJoinDeliveryDelaySeconds(joinDeliveryDelaySeconds);

        return new ConnectConfig(
                apiToken,
                config.getBoolean("debug", false),
                joinDeliveryDelaySeconds
        );
    }

    String apiToken() {
        return apiToken;
    }

    URI apiUri() {
        return SQWARE_API_URI;
    }

    int pollIntervalSeconds() {
        return POLL_INTERVAL_SECONDS;
    }

    boolean debug() {
        return debug;
    }

    int joinDeliveryDelaySeconds() {
        return joinDeliveryDelaySeconds;
    }

    boolean hasApiToken() {
        return !isBlank(apiToken);
    }

    private static void validateApiToken(String apiToken) {
        if (isBlank(apiToken)) {
            return;
        }
        if (apiToken.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            throw new IllegalArgumentException("api-token must contain only the token, not the Bearer prefix.");
        }
        if (apiToken.length() > MAX_TOKEN_LENGTH) {
            throw new IllegalArgumentException("api-token is too long.");
        }
        for (int i = 0; i < apiToken.length(); i++) {
            char character = apiToken.charAt(i);
            if (Character.isWhitespace(character) || Character.isISOControl(character)) {
                throw new IllegalArgumentException("api-token must not contain whitespace or control characters.");
            }
        }
    }

    private static void validateJoinDeliveryDelaySeconds(int joinDeliveryDelaySeconds) {
        if (joinDeliveryDelaySeconds < 0 || joinDeliveryDelaySeconds > MAX_JOIN_DELIVERY_DELAY_SECONDS) {
            throw new IllegalArgumentException(
                    "join-delivery-delay-seconds must be between 0 and "
                            + MAX_JOIN_DELIVERY_DELAY_SECONDS + "."
            );
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
