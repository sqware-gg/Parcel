package com.sqware.parcel.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class ParcelDeliveryExecutedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String orderId;
    private final String playerName;
    private final String playerUuid;
    private final String deliveryType;
    private final String subscriptionId;
    private final String subscriptionEvent;
    private final boolean queued;
    private final boolean success;
    private final String error;

    public ParcelDeliveryExecutedEvent(String orderId, String playerName, String playerUuid,
                                       boolean queued, boolean success, String error) {
        this(orderId, playerName, playerUuid, "", "", "", queued, success, error);
    }

    public ParcelDeliveryExecutedEvent(String orderId, String playerName, String playerUuid,
                                       String deliveryType, String subscriptionId, String subscriptionEvent,
                                       boolean queued, boolean success, String error) {
        this.orderId = orderId == null ? "" : orderId;
        this.playerName = playerName == null ? "" : playerName;
        this.playerUuid = playerUuid == null ? "" : playerUuid;
        this.deliveryType = deliveryType == null ? "" : deliveryType;
        this.subscriptionId = subscriptionId == null ? "" : subscriptionId;
        this.subscriptionEvent = subscriptionEvent == null ? "" : subscriptionEvent;
        this.queued = queued;
        this.success = success;
        this.error = error == null ? "" : error;
    }

    public String orderId() {
        return orderId;
    }

    public String playerName() {
        return playerName;
    }

    public String playerUuid() {
        return playerUuid;
    }

    public String deliveryType() {
        return deliveryType;
    }

    public String subscriptionId() {
        return subscriptionId;
    }

    public String subscriptionEvent() {
        return subscriptionEvent;
    }

    public boolean queued() {
        return queued;
    }

    public boolean success() {
        return success;
    }

    public String error() {
        return error;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
