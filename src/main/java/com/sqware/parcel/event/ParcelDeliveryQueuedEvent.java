package com.sqware.parcel.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class ParcelDeliveryQueuedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String orderId;
    private final String playerName;
    private final String playerUuid;
    private final String deliveryType;
    private final String subscriptionId;
    private final String subscriptionEvent;

    public ParcelDeliveryQueuedEvent(String orderId, String playerName, String playerUuid) {
        this(orderId, playerName, playerUuid, "", "", "");
    }

    public ParcelDeliveryQueuedEvent(String orderId, String playerName, String playerUuid,
                                     String deliveryType, String subscriptionId, String subscriptionEvent) {
        this.orderId = orderId == null ? "" : orderId;
        this.playerName = playerName == null ? "" : playerName;
        this.playerUuid = playerUuid == null ? "" : playerUuid;
        this.deliveryType = deliveryType == null ? "" : deliveryType;
        this.subscriptionId = subscriptionId == null ? "" : subscriptionId;
        this.subscriptionEvent = subscriptionEvent == null ? "" : subscriptionEvent;
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

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
