package com.sqware.parcel;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class SQWAREApiClient {
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 10_000;

    private final Gson gson = new Gson();
    private final ConnectConfig config;
    private final String pluginVersion;
    private final String userAgent;

    SQWAREApiClient(ConnectConfig config, String pluginVersion) {
        this.config = config;
        this.pluginVersion = pluginVersion;
        this.userAgent = "Parcel/" + pluginVersion + " (Minecraft Webstore Delivery)";
    }

    void sendHeartbeat(HeartbeatPayload payload) throws ApiException {
        JsonObject json = new JsonObject();
        json.addProperty("pluginVersion", payload.pluginVersion());
        json.addProperty("observedAtUnixMillis", payload.observedAtUnixMillis());
        json.addProperty("pluginUptimeSeconds", payload.pluginUptimeSeconds());
        json.addProperty("serverSoftware", payload.serverSoftware());
        json.addProperty("serverVersion", payload.serverVersion());
        json.addProperty("bukkitVersion", payload.bukkitVersion());
        json.addProperty("serverPort", payload.serverPort());
        json.addProperty("onlineMode", payload.onlineMode());
        json.addProperty("onlinePlayerCount", payload.onlinePlayerCount());
        json.addProperty("maxPlayers", payload.maxPlayers());
        json.addProperty("pendingConfirmations", payload.pendingConfirmations());
        json.addProperty("inFlightDeliveries", payload.inFlightDeliveries());
        json.addProperty("metricSource", "plugin");
        json.addProperty("publicMetricsAuthoritative", false);

        HttpResponse response = send("POST", "/api/plugin/heartbeat", gson.toJson(json), "heartbeat");
        ensureSuccess(response, "heartbeat", "/api/plugin/heartbeat");
    }

    List<DeliveryCommand> fetchQueue() throws ApiException {
        HttpResponse response = send("GET", "/api/plugin/queue", null, "queue poll");
        ensureSuccess(response, "queue poll", "/api/plugin/queue");
        if (isBlank(response.body())) {
            return Collections.emptyList();
        }

        JsonElement root = parseJson(response.body(), "queue poll");
        if (!root.isJsonObject()) {
            throw new ApiException("queue poll returned a non-object response");
        }

        JsonObject json = root.getAsJsonObject();
        JsonArray commands = json.has("commands") && json.get("commands").isJsonArray()
                ? json.getAsJsonArray("commands")
                : new JsonArray();

        List<DeliveryCommand> deliveries = new ArrayList<>();
        for (JsonElement element : commands) {
            if (element != null && element.isJsonObject()) {
                deliveries.add(DeliveryCommand.fromJson(element.getAsJsonObject()));
            }
        }
        return Collections.unmodifiableList(deliveries);
    }

    void sendConfirmation(DeliveryConfirmation confirmation) throws ApiException {
        HttpResponse response = send(
                "POST",
                "/api/plugin/confirm",
                gson.toJson(confirmation.toJson()),
                "delivery confirmation"
        );
        ensureSuccess(response, "delivery confirmation", "/api/plugin/confirm");
    }

    private HttpResponse send(String method, String path, String body, String operation) throws ApiException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(config.apiUri().toString() + path).openConnection();
            connection.setRequestMethod(method);
            connection.setUseCaches(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("Authorization", "Bearer " + config.apiToken());
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("User-Agent", userAgent);
            connection.setRequestProperty("X-SQWARE-Plugin", "Parcel");
            connection.setRequestProperty("X-SQWARE-Plugin-Version", pluginVersion);

            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                writeBody(connection, body);
            }

            int statusCode = connection.getResponseCode();
            String statusMessage = connection.getResponseMessage();
            String retryAfter = connection.getHeaderField("Retry-After");
            String responseBody = statusCode == HttpURLConnection.HTTP_NO_CONTENT
                    ? ""
                    : readBody(connection, statusCode);
            return new HttpResponse(statusCode, responseBody, statusMessage, retryAfter);
        } catch (IOException e) {
            throw new ApiException("could not reach the delivery API: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void writeBody(HttpURLConnection connection, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(bytes);
        }
    }

    private String readBody(HttpURLConnection connection, int statusCode) throws IOException {
        InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            return body.toString();
        }
    }

    private void ensureSuccess(HttpResponse response, String operation, String path) throws ApiException {
        int status = response.statusCode();
        if (status == 200 || status == 204) {
            return;
        }

        String body = response.body() == null ? "" : response.body().trim();
        String apiError = extractApiError(body);
        if (status == 401) {
            throw new ApiException(operation + " was rejected by the delivery API: invalid or revoked API token"
                    + suffix(apiError));
        }
        if (status == 403) {
            throw new ApiException(operation + " was rejected by the delivery API: API token is not allowed to use Parcel"
                    + suffix(apiError));
        }
        if (status == 404 && looksLikeHtml(body)) {
            throw new ApiException(operation + " returned HTTP 404 from " + config.apiUri() + path
                    + " (Parcel API route not deployed or routed to the website)");
        }
        if (status >= 500) {
            throw new ApiException(formatServerSideFailure(response, apiError));
        }

        if (!isBlank(apiError)) {
            throw new ApiException(operation + " returned HTTP " + status + ": " + apiError);
        }

        if (body.length() > 300) {
            body = body.substring(0, 300) + "...";
        }
        String suffix = body.isEmpty() ? "" : ": " + body;
        throw new ApiException(operation + " returned HTTP " + status + suffix);
    }

    private boolean looksLikeHtml(String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("<!doctype html") || lower.contains("<html");
    }

    private String extractApiError(String body) {
        if (isBlank(body) || looksLikeHtml(body)) {
            return "";
        }
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) {
                return "";
            }
            JsonObject json = root.getAsJsonObject();
            String error = stringField(json, "error");
            if (!isBlank(error)) {
                return error;
            }
            return stringField(json, "message");
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private String stringField(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonPrimitive()
                || !json.get(field).getAsJsonPrimitive().isString()) {
            return "";
        }
        return json.get(field).getAsString().trim();
    }

    private String suffix(String value) {
        return isBlank(value) ? "" : " (" + value + ")";
    }

    private String formatServerSideFailure(HttpResponse response, String apiError) {
        int status = response.statusCode();
        StringBuilder message = new StringBuilder()
                .append("Delivery API returned ")
                .append(formatHttpStatus(status, response.statusMessage()))
                .append(".");

        if (!isBlank(apiError)) {
            message.append(" API said: ").append(apiError).append(".");
        } else if (status == 503) {
            message.append(" The delivery backend is temporarily unavailable or under maintenance.");
        } else if (status == 502 || status == 504) {
            message.append(" The delivery backend did not get a healthy response from its upstream service.");
        } else {
            message.append(" The delivery backend encountered a temporary server-side problem.");
        }

        if (!isBlank(response.retryAfter())) {
            message.append(" Retry-After: ").append(response.retryAfter().trim()).append(".");
        }

        message.append(" This is likely outside the Minecraft server's control; Parcel will retry automatically.");
        return message.toString();
    }

    private String formatHttpStatus(int status, String statusMessage) {
        String normalized = normalizeStatusMessage(status, statusMessage);
        return isBlank(normalized) ? "HTTP " + status : "HTTP " + status + " " + normalized;
    }

    private String normalizeStatusMessage(int status, String statusMessage) {
        if (!isBlank(statusMessage)) {
            return statusMessage.trim();
        }
        switch (status) {
            case 500:
                return "Internal Server Error";
            case 502:
                return "Bad Gateway";
            case 503:
                return "Service Unavailable";
            case 504:
                return "Gateway Timeout";
            default:
                return "";
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private JsonElement parseJson(String body, String operation) throws ApiException {
        try {
            return JsonParser.parseString(body);
        } catch (RuntimeException e) {
            throw new ApiException(operation + " returned invalid JSON", e);
        }
    }

    private static final class HttpResponse {
        private final int statusCode;
        private final String body;
        private final String statusMessage;
        private final String retryAfter;

        private HttpResponse(int statusCode, String body, String statusMessage, String retryAfter) {
            this.statusCode = statusCode;
            this.body = body;
            this.statusMessage = statusMessage;
            this.retryAfter = retryAfter;
        }

        private int statusCode() {
            return statusCode;
        }

        private String body() {
            return body;
        }

        private String statusMessage() {
            return statusMessage;
        }

        private String retryAfter() {
            return retryAfter;
        }
    }
}
