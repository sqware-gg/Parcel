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

final class QueuedDeliveryStore {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private final Map<String, QueuedDelivery> deliveries = new ConcurrentHashMap<>();

    QueuedDeliveryStore(Path path) {
        this.path = path;
    }

    synchronized void load() throws IOException {
        deliveries.clear();
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonArray()) {
                throw new IOException("queued delivery store must contain a JSON array");
            }

            for (JsonElement element : root.getAsJsonArray()) {
                if (element != null && element.isJsonObject()) {
                    QueuedDelivery delivery = QueuedDelivery.fromJson(element.getAsJsonObject());
                    if (!isBlank(delivery.orderId())) {
                        deliveries.put(delivery.orderId(), delivery);
                    }
                }
            }
        } catch (JsonParseException e) {
            throw new IOException("queued delivery store contains invalid JSON", e);
        }
    }

    boolean contains(String orderId) {
        return deliveries.containsKey(orderId);
    }

    int size() {
        return deliveries.size();
    }

    List<QueuedDelivery> snapshot() {
        List<QueuedDelivery> sorted = new ArrayList<>(deliveries.values());
        sorted.sort(Comparator
                .comparingLong(QueuedDelivery::queuedAtUnixMillis)
                .thenComparing(QueuedDelivery::orderId));
        return sorted;
    }

    synchronized void put(QueuedDelivery delivery) throws IOException {
        if (delivery == null || isBlank(delivery.orderId())) {
            return;
        }
        deliveries.put(delivery.orderId(), delivery);
        save();
    }

    synchronized void remove(String orderId) throws IOException {
        if (deliveries.remove(orderId) != null) {
            save();
        }
    }

    private void save() throws IOException {
        JsonArray root = new JsonArray();
        List<QueuedDelivery> sorted = snapshot();
        for (QueuedDelivery delivery : sorted) {
            root.add(delivery.toJson());
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
