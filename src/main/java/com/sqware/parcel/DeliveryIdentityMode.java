package com.sqware.parcel;

import java.util.Locale;

enum DeliveryIdentityMode {
    UUID("uuid"),
    NAME("name");

    private final String configValue;

    DeliveryIdentityMode(String configValue) {
        this.configValue = configValue;
    }

    String configValue() {
        return configValue;
    }

    static DeliveryIdentityMode fromConfig(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || "uuid".equals(normalized) || "player_uuid".equals(normalized)
                || "player-uuid".equals(normalized)) {
            return UUID;
        }
        if ("name".equals(normalized) || "username".equals(normalized) || "player_name".equals(normalized)
                || "player-name".equals(normalized)) {
            return NAME;
        }
        throw new IllegalArgumentException("delivery-identity must be either uuid or name.");
    }
}
