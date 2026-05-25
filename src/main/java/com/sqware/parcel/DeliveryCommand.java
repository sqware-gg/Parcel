package com.sqware.parcel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DeliveryCommand {
    private final String orderId;
    private final String playerName;
    private final String playerUuid;
    private final List<String> commands;
    private final boolean requiresOnline;

    private DeliveryCommand(
            String orderId,
            String playerName,
            String playerUuid,
            List<String> commands,
            boolean requiresOnline
    ) {
        this.orderId = orderId;
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.commands = Collections.unmodifiableList(new ArrayList<>(commands));
        this.requiresOnline = requiresOnline;
    }

    static DeliveryCommand fromJson(JsonObject json) {
        List<String> commands = new ArrayList<>();
        JsonArray commandArray = json.has("commands") && json.get("commands").isJsonArray()
                ? json.getAsJsonArray("commands")
                : new JsonArray();

        for (JsonElement element : commandArray) {
            if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String command = element.getAsString().trim();
                if (!command.isEmpty()) {
                    commands.add(command);
                }
            }
        }

        String orderId = stringValue(json, "orderId");
        String playerName = stringValue(json, "playerName");
        String playerUuid = stringValue(json, "playerUuid");
        return new DeliveryCommand(
                orderId,
                playerName,
                playerUuid,
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

        String deliveryMode = stringValue(json, "deliveryMode").toLowerCase(java.util.Locale.ROOT);
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
            if (command.contains("{player}") || command.contains("{uuid}")) {
                return true;
            }
        }
        return false;
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
