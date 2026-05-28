package com.sqware.parcel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class DeliveryCommand {
    private final String orderId;
    private final String playerName;
    private final String playerUuid;
    private final String deliveryType;
    private final String subscriptionId;
    private final SubscriptionEvent subscriptionEvent;
    private final List<String> commands;
    private final boolean requiresOnline;

    private DeliveryCommand(
            String orderId,
            String playerName,
            String playerUuid,
            String deliveryType,
            String subscriptionId,
            SubscriptionEvent subscriptionEvent,
            List<String> commands,
            boolean requiresOnline
    ) {
        this.orderId = orderId;
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.deliveryType = deliveryType;
        this.subscriptionId = subscriptionId;
        this.subscriptionEvent = subscriptionEvent;
        this.commands = Collections.unmodifiableList(new ArrayList<>(commands));
        this.requiresOnline = requiresOnline;
    }

    static DeliveryCommand fromJson(JsonObject json) {
        SubscriptionEvent subscriptionEvent = parseSubscriptionEvent(json);
        List<String> commands = parseCommands(json, subscriptionEvent);
        String orderId = firstStringValue(json, "orderId", "deliveryId", "eventId");
        String playerName = stringValue(json, "playerName");
        String playerUuid = stringValue(json, "playerUuid");
        String subscriptionId = firstStringValue(json, "subscriptionId", "subscription_id");
        String deliveryType = parseDeliveryType(json, subscriptionEvent, subscriptionId);
        return new DeliveryCommand(
                orderId,
                playerName,
                playerUuid,
                deliveryType,
                subscriptionId,
                subscriptionEvent,
                commands,
                parseRequiresOnline(json, playerName, playerUuid, commands)
        );
    }

    String orderId() {
        return orderId;
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
        return subscriptionEvent.apiValue();
    }

    List<String> commands() {
        return commands;
    }

    boolean requiresOnline() {
        return requiresOnline;
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("orderId", orderId);
        json.addProperty("playerName", playerName);
        json.addProperty("playerUuid", playerUuid);
        json.addProperty("deliveryType", deliveryType);
        json.addProperty("subscriptionId", subscriptionId);
        json.addProperty("subscriptionEvent", subscriptionEvent.apiValue());
        json.addProperty("requiresOnline", requiresOnline);

        JsonArray commandArray = new JsonArray();
        for (String command : commands) {
            commandArray.add(command);
        }
        json.add("commands", commandArray);
        return json;
    }

    private static boolean parseRequiresOnline(
            JsonObject json,
            String playerName,
            String playerUuid,
            List<String> commands
    ) {
        if (json.has("requiresOnline") && json.get("requiresOnline").isJsonPrimitive()
                && json.get("requiresOnline").getAsJsonPrimitive().isBoolean()) {
            return json.get("requiresOnline").getAsBoolean();
        }

        String deliveryMode = stringValue(json, "deliveryMode").toLowerCase(Locale.ROOT);
        if ("online".equals(deliveryMode)
                || "online_only".equals(deliveryMode)
                || "require_online".equals(deliveryMode)
                || "queue_until_online".equals(deliveryMode)) {
            return true;
        }
        if ("immediate".equals(deliveryMode)
                || "offline_ok".equals(deliveryMode)
                || "offline".equals(deliveryMode)) {
            return false;
        }

        if (isBlank(playerName) && isBlank(playerUuid)) {
            return false;
        }

        for (String command : commands) {
            if (command.contains("{player}") || command.contains("{uuid}") || command.contains("{playerUuid}")) {
                return true;
            }
        }
        return false;
    }

    private static List<String> parseCommands(JsonObject json, SubscriptionEvent subscriptionEvent) {
        List<String> commands = commandList(json, "commands", "command");
        if (!commands.isEmpty() || subscriptionEvent == SubscriptionEvent.NONE) {
            return commands;
        }

        commands = nestedCommandList(json, "commands", subscriptionCommandKeys(subscriptionEvent));
        if (!commands.isEmpty()) {
            return commands;
        }

        switch (subscriptionEvent) {
            case INITIAL:
                return commandList(json, "initialCommands", "initialCommand",
                        "subscriptionInitialCommands", "subscriptionInitialCommand");
            case RENEWAL:
                return commandList(json, "renewalCommands", "renewalCommand",
                        "renewCommands", "renewCommand",
                        "subscriptionRenewalCommands", "subscriptionRenewalCommand");
            case CANCEL:
                return commandList(json, "cancelCommands", "cancelCommand",
                        "cancellationCommands", "cancellationCommand",
                        "subscriptionCancelCommands", "subscriptionCancelCommand");
            default:
                return commands;
        }
    }

    private static String[] subscriptionCommandKeys(SubscriptionEvent subscriptionEvent) {
        switch (subscriptionEvent) {
            case INITIAL:
                return new String[] {"initial", "start", "created"};
            case RENEWAL:
                return new String[] {"renewal", "renew", "recurring"};
            case CANCEL:
                return new String[] {"cancel", "canceled", "cancelled", "cancellation", "expired"};
            default:
                return new String[0];
        }
    }

    private static List<String> commandList(JsonObject json, String... fields) {
        List<String> commands = new ArrayList<>();
        for (String field : fields) {
            if (json.has(field)) {
                addCommands(commands, json.get(field));
            }
        }
        return commands;
    }

    private static List<String> nestedCommandList(JsonObject json, String objectField, String... fields) {
        if (!json.has(objectField) || !json.get(objectField).isJsonObject()) {
            return Collections.emptyList();
        }
        return commandList(json.getAsJsonObject(objectField), fields);
    }

    private static void addCommands(List<String> commands, JsonElement value) {
        if (value == null) {
            return;
        }
        if (value.isJsonArray()) {
            JsonArray commandArray = value.getAsJsonArray();
            for (JsonElement element : commandArray) {
                addCommands(commands, element);
            }
            return;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String command = value.getAsString().trim();
            if (!command.isEmpty()) {
                commands.add(command);
            }
        }
    }

    private static SubscriptionEvent parseSubscriptionEvent(JsonObject json) {
        SubscriptionEvent event = SubscriptionEvent.fromString(firstStringValue(
                json,
                "subscriptionEvent",
                "subscriptionAction",
                "billingEvent",
                "deliveryEvent"
        ));
        if (event != SubscriptionEvent.NONE) {
            return event;
        }
        return SubscriptionEvent.fromString(firstStringValue(json, "type", "deliveryType", "event"));
    }

    private static String parseDeliveryType(JsonObject json, SubscriptionEvent subscriptionEvent, String subscriptionId) {
        String value = firstStringValue(json, "deliveryType", "type");
        if (!isBlank(value)) {
            return value;
        }
        if (subscriptionEvent != SubscriptionEvent.NONE || !isBlank(subscriptionId)) {
            return "subscription";
        }
        return "order";
    }

    private static String firstStringValue(JsonObject json, String... fields) {
        for (String field : fields) {
            String value = stringValue(json, field);
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private static String stringValue(JsonObject json, String name) {
        if (!json.has(name) || !json.get(name).isJsonPrimitive()) {
            return "";
        }
        JsonElement value = json.get(name);
        return value.getAsJsonPrimitive().isString() ? value.getAsString().trim() : "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
