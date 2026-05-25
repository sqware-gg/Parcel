package com.sqware.parcel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class PendingConfirmationStore {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private final Map<String, DeliveryConfirmation> confirmations = new ConcurrentHashMap<>();

    PendingConfirmationStore(Path path) {
        this.path = path;
    }

    synchronized void load() throws IOException {
        confirmations.clear();
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonArray()) {
                throw new IOException("pending confirmation store must contain a JSON array");
            }

            for (JsonElement element : root.getAsJsonArray()) {
                if (element != null && element.isJsonObject()) {
                    DeliveryConfirmation confirmation = DeliveryConfirmation.fromJson(element.getAsJsonObject());
                    if (!isBlank(confirmation.orderId())) {
                        confirmations.put(confirmation.orderId(), confirmation);
                    }
                }
            }
        } catch (JsonParseException e) {
            throw new IOException("pending confirmation store contains invalid JSON", e);
        }
    }

    boolean contains(String orderId) {
        return confirmations.containsKey(orderId);
    }

    int size() {
        return confirmations.size();
    }

    List<DeliveryConfirmation> snapshot() {
        List<DeliveryConfirmation> sorted = new ArrayList<>(confirmations.values());
        sorted.sort(Comparator.comparing(DeliveryConfirmation::orderId));
        return sorted;
    }

    synchronized void put(DeliveryConfirmation confirmation) throws IOException {
        if (isBlank(confirmation.orderId())) {
            return;
        }
        confirmations.put(confirmation.orderId(), confirmation);
        save();
    }

    synchronized void remove(String orderId) throws IOException {
        if (confirmations.remove(orderId) != null) {
            save();
        }
    }

    private void save() throws IOException {
        JsonArray root = new JsonArray();
        List<DeliveryConfirmation> sorted = new ArrayList<>(confirmations.values());
        sorted.sort(Comparator.comparing(DeliveryConfirmation::orderId));
        for (DeliveryConfirmation confirmation : sorted) {
            root.add(confirmation.toJson());
        }

        Files.createDirectories(path.getParent());
        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
            gson.toJson(root, writer);
        }

        try {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
