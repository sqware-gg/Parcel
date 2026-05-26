package com.sqware.parcel.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class ParcelDeliveryQueuedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String orderId;
    private final String playerName;
    private final String playerUuid;

    public ParcelDeliveryQueuedEvent(String orderId, String playerName, String playerUuid) {
        this.orderId = orderId == null ? "" : orderId;
        this.playerName = playerName == null ? "" : playerName;
        this.playerUuid = playerUuid == null ? "" : playerUuid;
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

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
