package com.sqware.parcel;

import com.google.gson.JsonObject;

final class DeliveryConfirmation {
    private static final int MAX_ERROR_LENGTH = 500;

    private final String orderId;
    private final boolean ok;
    private final String error;

    DeliveryConfirmation(String orderId, boolean ok, String error) {
        this.orderId = orderId;
        this.ok = ok;
        this.error = truncate(error);
    }

    String orderId() {
        return orderId;
    }

    boolean ok() {
        return ok;
    }

    String error() {
        return error;
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("orderId", orderId);
        json.addProperty("ok", ok);
        if (error != null && !error.trim().isEmpty()) {
            json.addProperty("error", error);
        }
        return json;
    }

    static DeliveryConfirmation fromJson(JsonObject json) {
        String orderId = json.has("orderId") && json.get("orderId").isJsonPrimitive()
                && json.get("orderId").getAsJsonPrimitive().isString()
                ? json.get("orderId").getAsString().trim()
                : "";
        boolean ok = json.has("ok") && json.get("ok").isJsonPrimitive()
                && json.get("ok").getAsJsonPrimitive().isBoolean()
                && json.get("ok").getAsBoolean();
        String error = json.has("error") && json.get("error").isJsonPrimitive()
                && json.get("error").getAsJsonPrimitive().isString()
                ? json.get("error").getAsString()
                : null;
        return new DeliveryConfirmation(orderId, ok, error);
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }
}
