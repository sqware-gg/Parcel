package com.sqware.parcel;

import java.util.Locale;

enum SubscriptionEvent {
    NONE(""),
    INITIAL("initial"),
    RENEWAL("renewal"),
    CANCEL("cancel");

    private final String apiValue;

    SubscriptionEvent(String apiValue) {
        this.apiValue = apiValue;
    }

    String apiValue() {
        return apiValue;
    }

    static SubscriptionEvent fromString(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return NONE;
        }
        if ("initial".equals(normalized) || "start".equals(normalized) || "started".equals(normalized)
                || "create".equals(normalized) || "created".equals(normalized)
                || "activate".equals(normalized) || "activated".equals(normalized)
                || "subscriptioninitial".equals(normalized) || "subscriptionstarted".equals(normalized)) {
            return INITIAL;
        }
        if ("renewal".equals(normalized) || "renew".equals(normalized) || "renewed".equals(normalized)
                || "recurring".equals(normalized) || "payment".equals(normalized)
                || "subscriptionrenewal".equals(normalized) || "subscriptionrenewed".equals(normalized)) {
            return RENEWAL;
        }
        if ("cancel".equals(normalized) || "cancelled".equals(normalized) || "canceled".equals(normalized)
                || "cancellation".equals(normalized) || "expire".equals(normalized) || "expired".equals(normalized)
                || "end".equals(normalized) || "ended".equals(normalized)
                || "subscriptioncancel".equals(normalized) || "subscriptioncancelled".equals(normalized)
                || "subscriptioncanceled".equals(normalized) || "subscriptionexpired".equals(normalized)) {
            return CANCEL;
        }
        return NONE;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "")
                .replace(".", "")
                .replace(" ", "");
    }
}
