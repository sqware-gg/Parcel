# Parcel

[![Build](https://github.com/sqware-gg/Parcel/actions/workflows/build.yml/badge.svg)](https://github.com/sqware-gg/Parcel/actions/workflows/build.yml)

Parcel is the Minecraft webstore delivery plugin for SQWARE. It connects a Bukkit, Spigot, or Paper server to SQWARE, polls for paid package deliveries, runs configured console commands, and confirms the result back to SQWARE.

It is intentionally narrow: Parcel delivers purchases. It does not open inbound ports, expose a web server, stream logs, manage files, or control server lifecycle.

## Links

- Website: https://sqware.gg
- Plugin information and support: https://discord.sqware.gg

## Compatibility

- Server software: Bukkit, Spigot, or Paper
- API target: Spigot `1.8.8`
- Runtime: Java `8+`
- Build runtime: Java `9+` for Maven `--release` support
- Build tool: Maven

Parcel is designed for broad server compatibility, including older production networks.

## Features

- Outbound-only connection to `https://sqware.gg`.
- Bearer token authentication from `plugins/Parcel/config.yml`.
- Automatic heartbeat with server software, version, player count, and delivery state.
- Delivery polling every 15 seconds.
- Console command execution through `Bukkit.dispatchCommand`.
- Durable queueing for deliveries that must wait until the player is online.
- `{player}` and `{uuid}` placeholder replacement.
- Idempotency checks to avoid duplicate command execution.
- Local confirmation retry storage when the SQWARE API cannot be reached.

## Installation

1. Download the latest Parcel jar from GitHub Releases.
2. Stop your Minecraft server.
3. Put the jar in your server `plugins` folder.
4. Start the server once to generate `plugins/Parcel/config.yml`.
5. Copy your Parcel API token from the SQWARE dashboard.
6. Paste it into `api-token`.
7. Run `/parcel reload` or restart the server.

Keep the API token private. Anyone with that token may be able to access delivery functions for your store.

## Configuration

```yaml
api-token: ""
debug: false
join-delivery-delay-seconds: 2
```

- `api-token`: token from the SQWARE dashboard.
- `debug`: logs extra delivery details to the console.
- `join-delivery-delay-seconds`: delay before queued online-only deliveries run after a player joins.

The API host is intentionally not configurable by server owners. Parcel always connects to `https://sqware.gg`.

## Commands

```text
/parcel status
/parcel reload
/parcel poll
```

## Permissions

```text
parcel.admin  - use Parcel status, reload, and poll commands, default op
```

## Delivery Safety

Parcel treats the SQWARE order ID as an idempotency key. If the same order is seen more than once, commands are not re-run after the delivery has already been processed.

If commands execute but SQWARE cannot be reached for confirmation, Parcel saves the confirmation locally and retries. This prevents paid deliveries from becoming uncertain during temporary network or API problems.

Local state files:

```text
plugins/Parcel/queued-deliveries.json
plugins/Parcel/pending-confirmations.json
```

Do not delete these files unless you understand the delivery state they contain.

## Privacy

Parcel sends only the data needed to poll and confirm deliveries, such as plugin version, server software/version, player count, and delivery state. It does not send server logs, files, MOTD, bind address, or player chat.

## Updating

Stop the server, replace the jar, and start the server again. Keep `plugins/Parcel/config.yml` and the local delivery state files.

## Build From Source

```powershell
mvn package
```

The shaded server jar is written to:

```text
target/parcel.jar
```

## Troubleshooting

- `API token is not set`: add the token to `plugins/Parcel/config.yml`, then run `/parcel reload`.
- `API token is not allowed to use Parcel`: regenerate or reissue the token in the SQWARE dashboard.
- `Parcel API route not deployed or routed to the website`: the request reached `sqware.gg`, but the API route was not available. Check SQWARE service status or ask support.
- Deliveries wait forever: confirm the player name or UUID in the order and check online-only package settings.
- Commands fail: test the exact command in console and verify placeholders are valid for your package.

## Support

For setup help, compatibility questions, and plugin information, use https://discord.sqware.gg.
