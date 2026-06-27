# EchoWave

EchoWave is a Minecraft server plugin (Kotlin) that provides chat and player features for Java Edition and integrates Bedrock players via Geyser + Floodgate. It also has companion web and Android apps to manage per-player custom prefixes and view player info remotely.

> NOTE: Replace placeholder links below (web app, Android app) and any example values with your actual URLs and values.

## Requirements

- Java 17+ (or the version your server requires)
- Paper, Purpur, or any Spigot-compatible server
- (Optional, for Bedrock support) Geyser and Floodgate installed on the proxy or server-side

## Download & Install

1. Download the EchoWave jar from your releases page or build from source (instructions below).
2. Stop your Minecraft server.
3. Copy `EchoWave-<version>.jar` to the server's `plugins/` directory.
4. Start the server. EchoWave will generate `plugins/EchoWave/` and its configuration files.

## Build from source

- Clone the repo:
  git clone https://github.com/jinu2004/EchoWave-Java.git

- Build with Gradle (Linux/macOS):
  ./gradlew clean build

  On Windows:
  gradlew.bat clean build

- The plugin jar will be in `build/libs/` (for example `build/libs/EchoWave-1.0.0.jar`).

## Configuration

After first run, `plugins/EchoWave/config.yml` will be created. Example relevant Floodgate settings and prefix options:

```yaml
# plugins/EchoWave/config.yml

floodgate:
  enabled: true                # Enable Floodgate/Geyser handling
  enableNamePrefix: true       # Add a prefix to Floodgate (Bedrock) players' display names
  defaultPrefix: "&7[Bedrock] &r"  # Default prefix applied to Bedrock players (supports color codes)
  useCustomPrefixFromApp: false # If true, use prefixes set via web/android app

general:
  allowCustomPrefixes: true  # Allow custom prefixes (via commands or app)

api:
  enabled: true
  token: "change-this-token" # Token for web/android app auth (if API enabled)
```

Notes:
- Floodgate must be installed and correctly configured so the server receives Bedrock authentication.
- EchoWave detects Bedrock players by Floodgate UUID and applies prefixes only to those players when enabled.
- Color codes like `&a`, `&c` are supported if your server or chat plugin converts them to Minecraft color characters.

## Floodgate / Geyser Integration

- When `floodgate.enabled` and `floodgate.enableNamePrefix` are true, the plugin detects Floodgate-authenticated players and applies the configured prefix to their display name and chat name.
- If `floodgate.useCustomPrefixFromApp` is enabled, EchoWave will query the web/Android app (or the plugin-side store used by the apps) to apply per-player custom prefixes instead of `defaultPrefix`.
- Detection is based on Floodgate identifiers; Java players are left unchanged unless given custom prefixes.

Example behavior:
- Bedrock player `bedrock_user` joins → display name becomes `§7[Bedrock] §rbedrock_user` (color codes converted at runtime) so the prefix appears in chat and player lists.

## Commands (examples)

- /echowave reload — Reload plugin config
- /echowave setprefix <player> <prefix> — Set a custom prefix for a player
- /echowave removeprefix <player> — Remove a player’s custom prefix

Permissions (example):
- echowave.reload — reload config
- echowave.setprefix — set custom prefix
- echowave.manage — full management

(Adjust command names and permissions to match your plugin implementation.)

## Web App & Android App

EchoWave’s companion apps allow remote management of custom prefixes and player info.

- Web App: [Replace with web app URL] — Manage connected players, set per-player prefixes, and toggle Floodgate prefix behavior. The web app authenticates using the API token configured in the plugin.
- Android App: [Replace with Android app URL / Play Store link] — Manage prefixes and receive notifications for Bedrock joins. Uses the same API token.

How integration works:
- Enable the plugin API and set an API token in the plugin config.
- The web/Android apps call the plugin API to query or set per-player custom prefixes.
- If `useCustomPrefixFromApp` is true, app-set prefixes override the server default.

Security:
- Keep API tokens secret.
- If exposing the API externally, use HTTPS, reverse proxies, IP restrictions, or other protections.

## Examples

- Default Bedrock prefix:
```yaml
floodgate:
  enabled: true
  enableNamePrefix: true
  defaultPrefix: "&b[Bedrock] &r"
  useCustomPrefixFromApp: false
```

- Use per-player custom prefixes from apps:
```yaml
floodgate:
  enabled: true
  enableNamePrefix: true
  defaultPrefix: ""
  useCustomPrefixFromApp: true
```

## Troubleshooting

- Prefixes not appearing? Confirm Floodgate is installed and your server receives Floodgate UUIDs. Check plugin logs for detection messages.
- Colors not rendering? Ensure server or chat plugin supports color codes or adjust to the correct formatting.
- App API calls failing? Verify API token, network connectivity, and firewall settings.

## Contributing

Contributions welcome. Open issues for bugs or feature requests, and submit pull requests for improvements.

## License

Specify your preferred license (e.g., MIT) here.
