# Parcel

[![Build](https://github.com/sqware-gg/Parcel/actions/workflows/build.yml/badge.svg)](https://github.com/sqware-gg/Parcel/actions/workflows/build.yml)

**Join the SQWARE Discord: [discord.sqware.gg](https://discord.sqware.gg).**

Parcel is the Minecraft webstore delivery plugin for SQWARE stores. It polls the SQWARE delivery API, runs paid package commands from the server console, queues online-only deliveries, and confirms successful delivery back to your store.

Use it for Minecraft store delivery when your server uses SQWARE for ranks, keys, currency, cosmetics, or other paid rewards.

![SQWARE dashboard store page showing packages, revenue, delivery status, Stripe Connect payouts, support links, and Parcel setup.](dashboard-store.png)

## Features

- Outbound-only connection to `https://api.sqware.gg`.
- Bearer token authentication from `plugins/Parcel/config.yml`.
- Automatic heartbeat with server software, version, player count, and delivery state.
- Delivery polling every 15 seconds.
- Console command execution through `Bukkit.dispatchCommand`.
- Durable queueing for deliveries that require the player to be online.
- `{player}` and `{uuid}` placeholders in delivery commands.
- Idempotency checks to avoid duplicate command execution.
- Local confirmation retry storage for temporary API or network failures.

## Requirements

- Bukkit, Spigot, or Paper
- API target: Spigot `1.8.8`
- Runtime: Java `8+`
- Build runtime: Java `9+`
- Maven

Parcel is designed for broad server compatibility, including older production networks.

## SQWARE Store Setup

1. List your Minecraft server at https://sqware.gg.
2. Open https://sqware.gg/dashboard-store.
3. Configure store details, packages, prices, and delivery commands.
4. Connect Stripe Connect for payouts.
5. Copy the Parcel API token for your server.
6. Paste the token into `plugins/Parcel/config.yml`.

Delivery commands are configured in the SQWARE dashboard and executed by the Minecraft server console.

## Configuration

```yaml
api-token: ""
debug: false
join-delivery-delay-seconds: 2
```

Keep the API token private. Anyone with that token may be able to access delivery functions for your store.

## Commands

```text
/parcel status
/parcel reload
/parcel poll
```

## Permissions

```text
parcel.admin  - status, reload, and poll commands, default op
```

## Delivery Safety

Parcel treats the order ID as an idempotency key. If the same order appears again, already processed commands are not re-run.

If commands execute but the API cannot be reached for confirmation, Parcel saves the confirmation locally and retries. Do not delete these files unless you understand the delivery state they contain:

```text
plugins/Parcel/queued-deliveries.json
plugins/Parcel/pending-confirmations.json
```

## Privacy

Parcel sends only the data needed to poll and confirm deliveries, such as plugin version, server software/version, player count, and delivery state. It does not send server logs, files, MOTD, bind address, or player chat.

## Build

```powershell
mvn package
```

The shaded jar is written to `target/parcel.jar`.

## License

Parcel is licensed under the Apache License, Version 2.0. SQWARE hosted services, dashboard, APIs, trademarks, and commercial terms are separate from this repository.
