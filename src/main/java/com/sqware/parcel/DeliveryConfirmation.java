package com.sqware.parcel;

import com.google.gson.JsonObject;

final class DeliveryConfirmation {
    private static final int MAX_ERROR_LENGTH = 500;

    private final String orderId;
    private final boolean ok;
    private final String error;
    private final String playerName;
    private final String playerUuid;
    private final String deliveryType;
    private final String subscriptionId;
    private final String subscriptionEvent;

    DeliveryConfirmation(String orderId, boolean ok, String error) {
        this(orderId, ok, error, "", "");
    }

    DeliveryConfirmation(String orderId, boolean ok, String error, String playerName, String playerUuid) {
        this(orderId, ok, error, playerName, playerUuid, "", "", "");
    }

    DeliveryConfirmation(String orderId, boolean ok, String error, String playerName, String playerUuid,
                         String deliveryType, String subscriptionId, String subscriptionEvent) {
        this.orderId = orderId;
        this.ok = ok;
        this.error = truncate(error);
        this.playerName = safeString(playerName);
        this.playerUuid = safeString(playerUuid);
        this.deliveryType = safeString(deliveryType);
        this.subscriptionId = safeString(subscriptionId);
        this.subscriptionEvent = safeString(subscriptionEvent);
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

    String playerName() {
        return playerName;
    }

    String playerUuid() {
        return playerUuid;
    }

    String deliveryType() {
        return deliveryType;
    }

    String subscriptionId() {
        return subscriptionId;
    }

    String subscriptionEvent() {
        return subscriptionEvent;
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("orderId", orderId);
        json.addProperty("ok", ok);
        if (error != null && !error.trim().isEmpty()) {
            json.addProperty("error", error);
        }
        if (!playerName.trim().isEmpty()) {
            json.addProperty("playerName", playerName);
            json.addProperty("deliveredPlayerName", playerName);
        }
        if (!playerUuid.trim().isEmpty()) {
            json.addProperty("playerUuid", playerUuid);
            json.addProperty("deliveredPlayerUuid", playerUuid);
        }
        if (!deliveryType.trim().isEmpty()) {
            json.addProperty("deliveryType", deliveryType);
        }
        if (!subscriptionId.trim().isEmpty()) {
            json.addProperty("subscriptionId", subscriptionId);
        }
        if (!subscriptionEvent.trim().isEmpty()) {
            json.addProperty("subscriptionEvent", subscriptionEvent);
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
        String playerName = firstStringValue(json, "playerName", "deliveredPlayerName");
        String playerUuid = firstStringValue(json, "playerUuid", "deliveredPlayerUuid");
        String deliveryType = stringValue(json, "deliveryType");
        String subscriptionId = stringValue(json, "subscriptionId");
        String subscriptionEvent = stringValue(json, "subscriptionEvent");
        return new DeliveryConfirmation(
                orderId,
                ok,
                error,
                playerName,
                playerUuid,
                deliveryType,
                subscriptionId,
                subscriptionEvent
        );
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }

    private static String firstStringValue(JsonObject json, String primary, String fallback) {
        String value = stringValue(json, primary);
        return value.isEmpty() ? stringValue(json, fallback) : value;
    }

    private static String stringValue(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonPrimitive()
                || !json.get(field).getAsJsonPrimitive().isString()) {
            return "";
        }
        return json.get(field).getAsString().trim();
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }
}
