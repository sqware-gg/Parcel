package com.sqware.parcel;

import com.sqware.parcel.event.ParcelDeliveryExecutedEvent;
import com.sqware.parcel.event.ParcelDeliveryQueuedEvent;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class ParcelPlugin extends JavaPlugin implements Listener {
    private static final String PRIMARY_COMMAND = "parcel";
    private static final String STORE_COMMAND = "store";
    private static final String WEBSTORE_COMMAND = "webstore";
    private static final String ADMIN_PERMISSION = "parcel.admin";
    private static final String PENDING_CONFIRMATIONS_FILE = "pending-confirmations.json";
    private static final String QUEUED_DELIVERIES_FILE = "queued-deliveries.json";
    private static final int BSTATS_PLUGIN_ID = 31598;
    private static final long INITIAL_POLL_DELAY_TICKS = 20L;
    private static final int CONFIRMATION_RETRIES = 3;
    private static final int MAX_RENDERED_COMMAND_LENGTH = 4_096;
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    private static final String CHAT_PREFIX = "&bParcel &8› &7";

    private final Set<String> inFlightOrders = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Map<String, Boolean> warnedOperations = new ConcurrentHashMap<>();
    private final AtomicBoolean pollRunning = new AtomicBoolean(false);
    private final long startedAtMillis = System.currentTimeMillis();

    private PendingConfirmationStore pendingConfirmations;
    private QueuedDeliveryStore queuedDeliveries;
    private volatile ConnectConfig config;
    private volatile StoreConfig storeConfig;
    private volatile SQWAREApiClient apiClient;
    private StoreGui storeGui;
    private volatile BukkitTask pollTask;
    private volatile Instant lastSuccessfulHeartbeat;
    private volatile Instant lastSuccessfulQueuePoll;
    private volatile Instant lastSuccessfulConfirmation;
    private volatile Instant lastFailureAt;
    private volatile String lastFailureOperation;
    private volatile String lastFailureMessage;
    private volatile int lastOnlinePlayerCount;
    private volatile int lastMaxPlayers;
    private volatile int lastQueueSize;

    @Override
    public void onEnable() {
        new Metrics(this, BSTATS_PLUGIN_ID);
        saveDefaultConfig();
        pendingConfirmations = new PendingConfirmationStore(getDataFolder().toPath().resolve(PENDING_CONFIRMATIONS_FILE));
        queuedDeliveries = new QueuedDeliveryStore(getDataFolder().toPath().resolve(QUEUED_DELIVERIES_FILE));
        try {
            pendingConfirmations.load();
            queuedDeliveries.load();
            reconcileQueuedDeliveries();
        } catch (IOException e) {
            getLogger().severe("Could not load Parcel delivery state; refusing to start to avoid duplicate deliveries: "
                    + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        storeGui = new StoreGui(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(storeGui, this);
        reloadRuntime();
    }

    @Override
    public void onDisable() {
        cancelPoller();
        inFlightOrders.clear();
        pollRunning.set(false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase(STORE_COMMAND)) {
            return handleStoreCommand(sender, label, args);
        }

        if (command.getName().equalsIgnoreCase(WEBSTORE_COMMAND)) {
            return handleWebstoreCommand(sender);
        }

        if (!command.getName().equalsIgnoreCase(PRIMARY_COMMAND)) {
            return false;
        }

        if (!hasAdminPermission(sender)) {
            message(sender, "&cNo permission.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            boolean running = reloadRuntime();
            message(sender, running
                    ? "&aParcel reloaded. &7Polling is active."
                    : "&eParcel reloaded. &7Polling is inactive.");
            return true;
        }

        if (args[0].equalsIgnoreCase("poll")) {
            runManualPoll(sender);
            return true;
        }

        message(sender, "Usage: &b/" + label + " <status|reload|poll>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase(STORE_COMMAND)) {
            return completeStoreCommand(sender, args);
        }

        if (command.getName().equalsIgnoreCase(WEBSTORE_COMMAND)) {
            return Collections.emptyList();
        }

        if (!command.getName().equalsIgnoreCase(PRIMARY_COMMAND) || args.length != 1 || !hasAdminPermission(sender)) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        if ("status".startsWith(prefix)) {
            matches.add("status");
        }
        if ("reload".startsWith(prefix)) {
            matches.add("reload");
        }
        if ("poll".startsWith(prefix)) {
            matches.add("poll");
        }
        return matches;
    }

    private boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission(ADMIN_PERMISSION);
    }

    private boolean handleStoreCommand(CommandSender sender, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!hasAdminPermission(sender)) {
                sendStoreMessage(sender, "&cNo permission.");
                return true;
            }

            reloadConfig();
            boolean running = reloadRuntime();
            message(sender, running
                    ? "&aStore reloaded. &7Polling is active."
                    : "&aStore reloaded. &7Polling is inactive.");
            return true;
        }

        if (!hasStorePermission(sender)) {
            sendStoreMessage(sender, "&cNo permission.");
            return true;
        }

        StoreConfig activeStoreConfig = storeConfig;
        if (activeStoreConfig == null || !activeStoreConfig.enabled()) {
            sendStoreMessage(sender, activeStoreConfig == null
                    ? "&cThe store is currently unavailable."
                    : activeStoreConfig.disabledMessage());
            return true;
        }

        if (args.length > 0
                && (args[0].equalsIgnoreCase("link")
                || args[0].equalsIgnoreCase("url")
                || args[0].equalsIgnoreCase("webstore"))) {
            storeGui.sendWebstoreLink(sender);
            return true;
        }

        if (args.length > 0 && !args[0].equalsIgnoreCase("open")) {
            message(sender, "Usage: &b/" + label + " [open|link|reload]");
            return true;
        }

        if (!(sender instanceof Player)) {
            sendStoreMessage(sender, activeStoreConfig.playersOnlyMessage());
            storeGui.sendWebstoreLink(sender);
            return true;
        }

        if (storeGui.open((Player) sender)) {
            return true;
        }

        storeGui.sendWebstoreLink(sender);
        return true;
    }

    private boolean handleWebstoreCommand(CommandSender sender) {
        if (!hasStorePermission(sender)) {
            sendStoreMessage(sender, "&cNo permission.");
            return true;
        }

        StoreConfig activeStoreConfig = storeConfig;
        if (activeStoreConfig == null || !activeStoreConfig.enabled()) {
            sendStoreMessage(sender, activeStoreConfig == null
                    ? "&cThe store is currently unavailable."
                    : activeStoreConfig.disabledMessage());
            return true;
        }

        storeGui.sendWebstoreLink(sender);
        return true;
    }

    private List<String> completeStoreCommand(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }

        List<String> candidates = new ArrayList<>();
        if (hasStorePermission(sender)) {
            candidates.add("open");
            candidates.add("link");
            candidates.add("webstore");
        }
        if (hasAdminPermission(sender)) {
            candidates.add("reload");
        }
        return matching(args[0], candidates);
    }

    private List<String> matching(String prefix, List<String> candidates) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.startsWith(normalizedPrefix)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    private boolean hasStorePermission(CommandSender sender) {
        StoreConfig activeStoreConfig = storeConfig;
        String permission = activeStoreConfig == null ? StoreConfig.DEFAULT_PERMISSION : activeStoreConfig.permission();
        return isBlank(permission) || sender.hasPermission(permission) || hasAdminPermission(sender);
    }

    private void sendStoreMessage(CommandSender sender, String fallback) {
        StoreConfig activeStoreConfig = storeConfig;
        if (activeStoreConfig != null && fallback.equals("&cNo permission.")) {
            message(sender, activeStoreConfig.noPermissionMessage());
            return;
        }
        message(sender, fallback);
    }

    private synchronized boolean reloadRuntime() {
        StoreConfig nextStoreConfig = StoreConfig.from(this, getConfig());
        storeConfig = nextStoreConfig;
        if (storeGui != null) {
            storeGui.reload(nextStoreConfig);
        }

        ConnectConfig nextConfig;
        try {
            nextConfig = ConnectConfig.from(getConfig());
        } catch (IllegalArgumentException e) {
            cancelPoller();
            config = null;
            apiClient = null;
            getLogger().severe("Invalid Parcel config: " + e.getMessage());
            return false;
        }

        config = nextConfig;
        apiClient = new SQWAREApiClient(nextConfig, getDescription().getVersion());
        warnedOperations.clear();
        scheduleQueuedDeliveryCatchUp(Math.max(INITIAL_POLL_DELAY_TICKS, joinDeliveryDelayTicks(nextConfig)));

        if (!nextConfig.hasApiToken()) {
            cancelPoller();
            getLogger().warning("API token is not set. Add it to plugins/Parcel/config.yml, then run /parcel reload.");
            return false;
        }

        startPoller(nextConfig);
        getLogger().info("Parcel enabled. Polling " + nextConfig.apiUri() + " every "
                + nextConfig.pollIntervalSeconds() + " seconds.");
        return true;
    }

    private synchronized void startPoller(ConnectConfig activeConfig) {
        cancelPoller();
        long intervalTicks = activeConfig.pollIntervalSeconds() * 20L;
        pollTask = getServer().getScheduler().runTaskTimerAsynchronously(
                this,
                this::runPollCycle,
                INITIAL_POLL_DELAY_TICKS,
                intervalTicks
        );
    }

    private synchronized void cancelPoller() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
    }

    private void runManualPoll(CommandSender sender) {
        ConnectConfig activeConfig = config;
        if (activeConfig == null || !activeConfig.hasApiToken() || apiClient == null) {
            message(sender, "&cParcel cannot poll yet. &7Add an API token, then run &b/parcel reload&7.");
            return;
        }

        message(sender, "&aParcel poll queued.");
        getServer().getScheduler().runTaskAsynchronously(this, this::runPollCycle);
    }

    private void runPollCycle() {
        SQWAREApiClient client = apiClient;
        ConnectConfig activeConfig = config;
        if (client == null || activeConfig == null || !activeConfig.hasApiToken()) {
            return;
        }

        if (!pollRunning.compareAndSet(false, true)) {
            logDebug("Skipping poll because the previous poll is still running.");
            return;
        }

        try {
            retryPendingConfirmations(client);
            sendHeartbeat(client);
            fetchAndProcessQueue(client);
            scheduleQueuedDeliveryCatchUp(1L);
        } finally {
            pollRunning.set(false);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        scheduleQueuedDeliveriesForPlayer(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getName(),
                joinDeliveryDelayTicks(config)
        );
    }

    private void retryPendingConfirmations(SQWAREApiClient client) {
        for (DeliveryConfirmation confirmation : pendingConfirmations.snapshot()) {
            try {
                client.sendConfirmation(confirmation);
                if (!cleanupQueuedDeliveryAfterConfirmation(confirmation.orderId())) {
                    continue;
                }
                pendingConfirmations.remove(confirmation.orderId());
                lastSuccessfulConfirmation = Instant.now();
                warnedOperations.remove("pending confirmation");
                logDebug("Confirmed pending delivery result for order " + confirmation.orderId() + ".");
            } catch (ApiException | IOException e) {
                logFailure("pending confirmation", e);
            }
        }
    }

    private void sendHeartbeat(SQWAREApiClient client) {
        try {
            client.sendHeartbeat(createHeartbeatPayload());
            lastSuccessfulHeartbeat = Instant.now();
            warnedOperations.remove("heartbeat");
        } catch (ApiException | InterruptedException | ExecutionException | TimeoutException e) {
            logFailure("heartbeat", e);
        }
    }

    private void fetchAndProcessQueue(SQWAREApiClient client) {
        try {
            List<DeliveryCommand> deliveries = client.fetchQueue();
            lastQueueSize = deliveries.size();
            lastSuccessfulQueuePoll = Instant.now();
            warnedOperations.remove("queue poll");
            for (DeliveryCommand delivery : deliveries) {
                processDelivery(delivery);
            }
        } catch (ApiException e) {
            logFailure("queue poll", e);
        }
    }

    private HeartbeatPayload createHeartbeatPayload()
            throws InterruptedException, ExecutionException, TimeoutException {
        Future<HeartbeatPayload> future = getServer().getScheduler().callSyncMethod(this, () -> {
            int onlinePlayerCount = getServer().getOnlinePlayers().size();
            int maxPlayers = getServer().getMaxPlayers();
            lastOnlinePlayerCount = onlinePlayerCount;
            lastMaxPlayers = maxPlayers;

            return new HeartbeatPayload(
                    getDescription().getVersion(),
                    System.currentTimeMillis(),
                    Math.max(0L, (System.currentTimeMillis() - startedAtMillis) / 1_000L),
                    safeString(getServer().getName()),
                    getServer().getVersion(),
                    safeString(getServer().getBukkitVersion()),
                    getServer().getPort(),
                    getServer().getOnlineMode(),
                    onlinePlayerCount,
                    maxPlayers,
                    pendingConfirmations.size(),
                    inFlightOrders.size()
            );
        });
        return future.get(3, TimeUnit.SECONDS);
    }

    private void processDelivery(DeliveryCommand delivery) {
        if (isBlank(delivery.orderId())) {
            getLogger().warning("Ignoring queued delivery without an orderId.");
            return;
        }

        if (pendingConfirmations.contains(delivery.orderId())) {
            logDebug("Skipping order " + delivery.orderId() + " because its confirmation is still pending.");
            return;
        }

        if (queuedDeliveries.contains(delivery.orderId())) {
            logDebug("Skipping order " + delivery.orderId() + " because it is already queued for a player to join.");
            return;
        }

        if (!inFlightOrders.add(delivery.orderId())) {
            logDebug("Skipping order " + delivery.orderId() + " because it is already in flight.");
            return;
        }

        getServer().getScheduler().runTask(this, () -> dispatchDelivery(delivery));
    }

    private void dispatchDelivery(DeliveryCommand delivery) {
        try {
            if (delivery.requiresOnline() && !isTargetPlayerOnline(delivery)) {
                queueDeliveryUntilPlayerJoins(delivery);
                return;
            }

            executeDelivery(delivery, false);
        } finally {
            inFlightOrders.remove(delivery.orderId());
        }
    }

    private void executeDelivery(DeliveryCommand delivery, boolean fromQueuedStore) {
        DeliveryConfirmation confirmation = runDeliveryCommands(delivery);
        try {
            pendingConfirmations.put(confirmation);
        } catch (IOException e) {
            getLogger().severe("Could not persist confirmation for order " + confirmation.orderId()
                    + ". The plugin will still try to confirm it now: " + e.getMessage());
        }

        if (fromQueuedStore) {
            try {
                queuedDeliveries.remove(delivery.orderId());
            } catch (IOException e) {
                getLogger().warning("Executed queued order " + delivery.orderId()
                        + " but could not update queued delivery storage: " + e.getMessage());
            }
        }

        getServer().getPluginManager().callEvent(new ParcelDeliveryExecutedEvent(
                delivery.orderId(),
                delivery.playerName(),
                delivery.playerUuid(),
                fromQueuedStore,
                confirmation.ok(),
                confirmation.error()
        ));

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            if (confirmWithRetries(confirmation)) {
                try {
                    if (fromQueuedStore && !cleanupQueuedDeliveryAfterConfirmation(confirmation.orderId())) {
                        return;
                    }
                    pendingConfirmations.remove(confirmation.orderId());
                } catch (IOException e) {
                    getLogger().warning("Confirmed order " + confirmation.orderId()
                            + " but could not update pending confirmation storage: " + e.getMessage());
                }
            }
        });
    }

    private void queueDeliveryUntilPlayerJoins(DeliveryCommand delivery) {
        try {
            queuedDeliveries.put(QueuedDelivery.create(delivery));
            getLogger().info("Queued order " + delivery.orderId() + " until " + describePlayer(delivery)
                    + " is online.");
            getServer().getPluginManager().callEvent(new ParcelDeliveryQueuedEvent(
                    delivery.orderId(),
                    delivery.playerName(),
                    delivery.playerUuid()
            ));
        } catch (IOException e) {
            getLogger().severe("Could not persist queued delivery for order " + delivery.orderId()
                    + ". Parcel will wait for the delivery service to send it again: " + e.getMessage());
        }
    }

    private void drainQueuedDeliveriesForOnlinePlayers() {
        for (QueuedDelivery queuedDelivery : queuedDeliveries.snapshot()) {
            if (pendingConfirmations.contains(queuedDelivery.orderId())) {
                removeQueuedDeliveryAfterExecution(queuedDelivery.orderId());
                continue;
            }
            if (queuedDelivery.delivery().requiresOnline() && !isTargetPlayerOnline(queuedDelivery.delivery())) {
                continue;
            }
            attemptQueuedDelivery(queuedDelivery);
        }
    }

    private void drainQueuedDeliveriesForPlayer(UUID playerUuid, String playerName) {
        for (QueuedDelivery queuedDelivery : queuedDeliveries.snapshot()) {
            DeliveryCommand delivery = queuedDelivery.delivery();
            if (!targetsPlayer(delivery, playerUuid, playerName)) {
                continue;
            }
            if (pendingConfirmations.contains(queuedDelivery.orderId())) {
                removeQueuedDeliveryAfterExecution(queuedDelivery.orderId());
                continue;
            }
            if (delivery.requiresOnline() && !isTargetPlayerOnline(delivery)) {
                continue;
            }
            attemptQueuedDelivery(queuedDelivery);
        }
    }

    private void attemptQueuedDelivery(QueuedDelivery queuedDelivery) {
        DeliveryCommand delivery = queuedDelivery.delivery();
        if (!inFlightOrders.add(queuedDelivery.orderId())) {
            return;
        }

        try {
            logDebug("Executing queued order " + queuedDelivery.orderId() + " for " + describePlayer(delivery) + ".");
            executeDelivery(delivery, true);
        } finally {
            inFlightOrders.remove(queuedDelivery.orderId());
        }
    }

    private DeliveryConfirmation runDeliveryCommands(DeliveryCommand delivery) {
        if (delivery.commands().isEmpty()) {
            return new DeliveryConfirmation(delivery.orderId(), false, "No commands were supplied for this delivery.");
        }

        String validationError = validateDelivery(delivery);
        if (validationError != null) {
            return new DeliveryConfirmation(delivery.orderId(), false, validationError);
        }

        List<String> renderedCommands = new ArrayList<>(delivery.commands().size());
        for (int index = 0; index < delivery.commands().size(); index++) {
            String command = delivery.commands().get(index);
            String parsedCommand = renderCommand(command, delivery);
            if (isBlank(parsedCommand)) {
                return new DeliveryConfirmation(delivery.orderId(), false, "Command " + (index + 1) + " was blank after parsing.");
            }
            if (parsedCommand.length() > MAX_RENDERED_COMMAND_LENGTH) {
                return new DeliveryConfirmation(delivery.orderId(), false, "Command " + (index + 1) + " exceeded the maximum allowed length.");
            }
            if (containsControlCharacter(parsedCommand)) {
                return new DeliveryConfirmation(delivery.orderId(), false, "Command " + (index + 1) + " contained an invalid control character.");
            }

            renderedCommands.add(parsedCommand);
        }

        for (int index = 0; index < renderedCommands.size(); index++) {
            if (config != null && config.debug()) {
                getLogger().info("Executing command " + (index + 1) + "/" + renderedCommands.size()
                        + " for order " + delivery.orderId() + ".");
            }

            try {
                boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), renderedCommands.get(index));
                if (!success) {
                    return new DeliveryConfirmation(
                            delivery.orderId(),
                            false,
                            "Command " + (index + 1) + " returned false."
                    );
                }
            } catch (RuntimeException e) {
                return new DeliveryConfirmation(
                        delivery.orderId(),
                        false,
                        "Command " + (index + 1) + " failed: " + safeString(e.getMessage())
                );
            }
        }

        return new DeliveryConfirmation(delivery.orderId(), true, null);
    }

    private String validateDelivery(DeliveryCommand delivery) {
        boolean needsPlayerName = false;
        boolean needsPlayerUuid = false;
        for (String command : delivery.commands()) {
            needsPlayerName = needsPlayerName || command.contains("{player}");
            needsPlayerUuid = needsPlayerUuid || command.contains("{uuid}");
        }

        if (delivery.requiresOnline() && isBlank(delivery.playerName()) && isBlank(delivery.playerUuid())) {
            return "Delivery required the player to be online, but no player identity was supplied.";
        }
        if (needsPlayerName && !PLAYER_NAME_PATTERN.matcher(delivery.playerName()).matches()) {
            return "Delivery required a valid Minecraft player name.";
        }
        if (needsPlayerUuid && !UUID_PATTERN.matcher(delivery.playerUuid()).matches()) {
            return "Delivery required a valid player UUID.";
        }
        if (!isBlank(delivery.playerName()) && !PLAYER_NAME_PATTERN.matcher(delivery.playerName()).matches()) {
            return "Delivery contained an invalid Minecraft player name.";
        }
        if (!isBlank(delivery.playerUuid()) && !UUID_PATTERN.matcher(delivery.playerUuid()).matches()) {
            return "Delivery contained an invalid player UUID.";
        }

        return null;
    }

    private String renderCommand(String command, DeliveryCommand delivery) {
        String parsed = command
                .replace("{player}", delivery.playerName())
                .replace("{uuid}", delivery.playerUuid())
                .trim();
        while (parsed.startsWith("/")) {
            parsed = parsed.substring(1).trim();
        }
        return parsed;
    }

    private boolean confirmWithRetries(DeliveryConfirmation confirmation) {
        SQWAREApiClient client = apiClient;
        if (client == null) {
            return false;
        }

        for (int attempt = 1; attempt <= CONFIRMATION_RETRIES; attempt++) {
            try {
                client.sendConfirmation(confirmation);
                lastSuccessfulConfirmation = Instant.now();
                warnedOperations.remove("delivery confirmation");
                logDebug("Confirmed order " + confirmation.orderId() + " -> ok: " + confirmation.ok());
                return true;
            } catch (ApiException e) {
                if (attempt == CONFIRMATION_RETRIES) {
                    logFailure("delivery confirmation", e);
                    return false;
                }
                sleepBeforeRetry(attempt);
            }
        }
        return false;
    }

    private void scheduleQueuedDeliveryCatchUp(long delayTicks) {
        if (!isEnabled()) {
            return;
        }
        getServer().getScheduler().runTaskLater(
                this,
                this::drainQueuedDeliveriesForOnlinePlayers,
                Math.max(0L, delayTicks)
        );
    }

    private void scheduleQueuedDeliveriesForPlayer(UUID playerUuid, String playerName, long delayTicks) {
        if (!isEnabled() || playerUuid == null) {
            return;
        }
        getServer().getScheduler().runTaskLater(
                this,
                () -> drainQueuedDeliveriesForPlayer(playerUuid, playerName),
                Math.max(0L, delayTicks)
        );
    }

    private long joinDeliveryDelayTicks(ConnectConfig activeConfig) {
        if (activeConfig == null) {
            return 40L;
        }
        return Math.max(0L, activeConfig.joinDeliveryDelaySeconds()) * 20L;
    }

    private void reconcileQueuedDeliveries() throws IOException {
        for (QueuedDelivery queuedDelivery : queuedDeliveries.snapshot()) {
            if (pendingConfirmations.contains(queuedDelivery.orderId())) {
                queuedDeliveries.remove(queuedDelivery.orderId());
            }
        }
    }

    private void removeQueuedDeliveryAfterExecution(String orderId) {
        try {
            queuedDeliveries.remove(orderId);
        } catch (IOException e) {
            getLogger().warning("Could not remove stale queued delivery for order " + orderId + ": " + e.getMessage());
        }
    }

    private boolean cleanupQueuedDeliveryAfterConfirmation(String orderId) {
        if (!queuedDeliveries.contains(orderId)) {
            return true;
        }
        try {
            queuedDeliveries.remove(orderId);
            return true;
        } catch (IOException e) {
            getLogger().warning("Confirmed order " + orderId
                    + " but could not update queued delivery storage. Parcel will keep the confirmation locally until this is fixed: "
                    + e.getMessage());
            return false;
        }
    }

    private boolean isTargetPlayerOnline(DeliveryCommand delivery) {
        return findOnlinePlayer(delivery) != null;
    }

    private Player findOnlinePlayer(DeliveryCommand delivery) {
        UUID playerUuid = parseUuid(delivery.playerUuid());
        if (playerUuid != null) {
            Player player = getServer().getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                return player;
            }
        }

        if (!isBlank(delivery.playerName())) {
            Player player = getServer().getPlayerExact(delivery.playerName());
            if (player == null) {
                player = getServer().getPlayer(delivery.playerName());
            }
            if (player != null && player.isOnline()) {
                return player;
            }
        }

        return null;
    }

    private boolean targetsPlayer(DeliveryCommand delivery, UUID playerUuid, String playerName) {
        UUID deliveryUuid = parseUuid(delivery.playerUuid());
        if (deliveryUuid != null && playerUuid != null && deliveryUuid.equals(playerUuid)) {
            return true;
        }
        return !isBlank(delivery.playerName())
                && !isBlank(playerName)
                && delivery.playerName().equalsIgnoreCase(playerName);
    }

    private UUID parseUuid(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String describePlayer(DeliveryCommand delivery) {
        if (!isBlank(delivery.playerName())) {
            return "player " + delivery.playerName();
        }
        if (!isBlank(delivery.playerUuid())) {
            return "UUID " + delivery.playerUuid();
        }
        return "the target player";
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(Math.min(1_000L, 250L * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendStatus(CommandSender sender) {
        ConnectConfig activeConfig = config;
        message(sender, "&bParcel status");
        message(sender, "API URL: &f" + (activeConfig == null ? "invalid config" : activeConfig.apiUri()));
        message(sender, "Polling: &f" + (pollTask != null ? "active" : "inactive"));
        message(sender, "Players: &f" + lastOnlinePlayerCount + "&8/&f" + lastMaxPlayers);
        message(sender, "Pending confirmations: &f" + pendingConfirmations.size());
        message(sender, "Queued join deliveries: &f" + queuedDeliveries.size());
        message(sender, "In-flight deliveries: &f" + inFlightOrders.size());
        message(sender, "Join delivery delay: &f" + (activeConfig == null
                ? "invalid config"
                : activeConfig.joinDeliveryDelaySeconds() + " seconds"));
        message(sender, "Last queue size: &f" + lastQueueSize);
        message(sender, "Last heartbeat: &f" + formatInstant(lastSuccessfulHeartbeat));
        message(sender, "Last queue poll: &f" + formatInstant(lastSuccessfulQueuePoll));
        message(sender, "Last confirmation: &f" + formatInstant(lastSuccessfulConfirmation));
        if (lastFailureOperation != null) {
            message(sender, "&cLast failure: &f" + lastFailureOperation + " "
                    + formatInstant(lastFailureAt) + " (" + lastFailureMessage + ")");
        }
    }

    void message(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', CHAT_PREFIX + message));
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "never";
        }
        long seconds = Duration.between(instant, Instant.now()).getSeconds();
        if (seconds < 5) {
            return "just now";
        }
        return seconds + " seconds ago";
    }

    private void logFailure(String operation, Exception e) {
        lastFailureAt = Instant.now();
        lastFailureOperation = operation;
        String logMessage = renderFailureLogMessage(operation, e);
        lastFailureMessage = trimForStatus(stripOperationPrefix(operation, logMessage));

        ConnectConfig activeConfig = config;
        boolean debug = activeConfig != null && activeConfig.debug();
        boolean firstFailure = warnedOperations.putIfAbsent(operation, Boolean.TRUE) == null;
        if (debug || firstFailure) {
            getLogger().warning(logMessage);
        }
    }

    private void logDebug(String message) {
        ConnectConfig activeConfig = config;
        if (activeConfig != null && activeConfig.debug()) {
            getLogger().info(message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String trimForStatus(String value) {
        String trimmed = value.trim();
        if (trimmed.length() <= 160) {
            return trimmed;
        }
        return trimmed.substring(0, 160) + "...";
    }

    private String renderFailureLogMessage(String operation, Exception e) {
        String message = safeString(e.getMessage()).trim();
        if (isBlank(message)) {
            return operation + " failed.";
        }
        if (startsWithOperation(operation, message) || startsWithOperation(operation + " failed", message)) {
            return message;
        }
        return operation + " failed: " + message;
    }

    private String stripOperationPrefix(String operation, String message) {
        if (message.regionMatches(true, 0, operation + " failed.", 0, (operation + " failed.").length())) {
            return "unknown error";
        }
        String[] prefixes = new String[] {
                operation + " failed: ",
                operation + " returned ",
                operation + " was ",
                operation + " "
        };
        for (String prefix : prefixes) {
            if (message.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return message.substring(prefix.length()).trim();
            }
        }
        return message;
    }

    private boolean startsWithOperation(String prefix, String message) {
        return message.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (Character.isISOControl(character)) {
                return true;
            }
        }
        return false;
    }
}
