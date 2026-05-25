package com.sqware.parcel;

import com.google.gson.JsonObject;

final class QueuedDelivery {
    private final DeliveryCommand delivery;
    private final long queuedAtUnixMillis;

    private QueuedDelivery(DeliveryCommand delivery, long queuedAtUnixMillis) {
        this.delivery = delivery;
        this.queuedAtUnixMillis = queuedAtUnixMillis;
    }

    static QueuedDelivery create(DeliveryCommand delivery) {
        return new QueuedDelivery(delivery, System.currentTimeMillis());
    }

    static QueuedDelivery fromJson(JsonObject json) {
        DeliveryCommand delivery = json.has("delivery") && json.get("delivery").isJsonObject()
                ? DeliveryCommand.fromJson(json.getAsJsonObject("delivery"))
                : DeliveryCommand.fromJson(json);
        long queuedAtUnixMillis = json.has("queuedAtUnixMillis") && json.get("queuedAtUnixMillis").isJsonPrimitive()
                && json.get("queuedAtUnixMillis").getAsJsonPrimitive().isNumber()
                ? json.get("queuedAtUnixMillis").getAsLong()
                : System.currentTimeMillis();
        return new QueuedDelivery(delivery, queuedAtUnixMillis);
    }

    String orderId() {
        return delivery.orderId();
    }

    DeliveryCommand delivery() {
        return delivery;
    }

    long queuedAtUnixMillis() {
        return queuedAtUnixMillis;
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.add("delivery", delivery.toJson());
        json.addProperty("queuedAtUnixMillis", queuedAtUnixMillis);
        return json;
    }
}
