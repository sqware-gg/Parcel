package com.sqware.parcel.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class ParcelDeliveryExecutedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String orderId;
    private final String playerName;
    private final String playerUuid;
    private final boolean queued;
    private final boolean success;
    private final String error;

    public ParcelDeliveryExecutedEvent(String orderId, String playerName, String playerUuid,
                                       boolean queued, boolean success, String error) {
        this.orderId = orderId == null ? "" : orderId;
        this.playerName = playerName == null ? "" : playerName;
        this.playerUuid = playerUuid == null ? "" : playerUuid;
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
