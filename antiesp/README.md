# Bundled anti-ESP (RaycastedAntiESP)

This folder contains [RaycastedAntiESP](https://github.com/Cubicake/RaycastedAntiESP) by Cubicake and Contributors,
copyright © 2025-2026, licensed under the GNU Affero General Public License v3.0 only (see [LICENSE](LICENSE)),
together with its libraries:

| Folder | Upstream | License |
|---|---|---|
| `core`, `packetevents`, `paper` | [RaycastedAntiESP](https://github.com/Cubicake/RaycastedAntiESP) | AGPL-3.0-only |
| `logging` | [CubiLogging](https://github.com/Cubicake/CubiLogging) | AGPL-3.0-only |
| `locatables` | [Locatables](https://github.com/Cubicake/Locatables) | AGPL-3.0-only |
| `leafpile` | [ParallelepipedLeafPile](https://github.com/Cubicake/ParallelepipedLeafPile) (Spottedleaf's concurrency utilities) | GPL-3.0 |
| `libs/yaml-4.3.0-SNAPSHOT-cubi.jar` | [Cubicake/Configurate](https://github.com/Cubicake/Configurate), see `libs/README.md` | Apache-2.0 |

The exact upstream commits are in [UPSTREAM-VERSION.txt](UPSTREAM-VERSION.txt), and the original README is
[UPSTREAM-README.md](UPSTREAM-README.md). The generated sources in `leafpile/src/main/java/ca/spottedleaf/concurrentutil/map/concurrent`
come from `leafpile/gen_sources.sh` and the templates next to it.

## Changes made for Freeze Shield

- `paper/.../RaycastedAntiESP.java` runs as a component started by Freeze Shield (`AntiEspLoader` in the Bukkit
  module) instead of as its own plugin; its standalone update check and bStats are not started.
- `packetevents/.../GrimSync.java` is new: every packet the anti-ESP writes silently, cancels after the anticheat saw it,
  or edits in place (hidden chests in chunks and multi-block changes) is reported to Freeze Shield, so the anticheat
  simulates against exactly what the player received.
- The attribution notice (`/raycastedantiespCredits`) uses the fork notice provided by upstream, linking to
  https://github.com/LiGLP/Freeze-Shield.
- Unit tests and the standalone Gradle setup (paperweight, run-paper) are not included; the modules build as part of
  Freeze Shield's Gradle build.
