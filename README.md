<div align="center">
 <h1>Freeze Shield</h1>
 <p><b>An improved GrimAC fork with extra checks inspired by NoCheatPlus and a new Meteor Client detection</b></p>
</div>

Freeze Shield is a Minecraft anticheat for Spigot, Paper, Folia and Fabric servers, supporting Minecraft 1.8–26.3.
It is built on top of [GrimAC](https://github.com/GrimAnticheat/Grim) and keeps Grim's movement simulation engine,
latency compensation and world replication. On top of that it adds:

- **Meteor Client detection on join**: detects Meteor Client even when it spoofs its brand as "vanilla", and quietly kicks the player.
- **New checks against popular hacked clients** (Meteor, Wurst and similar), some of them inspired by
  [NoCheatPlus](https://github.com/Updated-NoCheatPlus/NoCheatPlus).

## What's new compared to Grim

### Meteor Client detection
Shortly after a player joins, Freeze Shield asks the client to resolve Meteor's keybinds through an invisible,
client-only sign. A vanilla client can't resolve them; a client with Meteor installed can. Detected players are
reported to staff and kicked after a short random delay with a normal-looking timeout message.

- Clients that block this technique are logged as inconclusive and **not** kicked, so vanilla players are never punished by it.
- The list of clients and keybinds is configurable under `client-detection` in `config.yml`.
- Limits: modified or renamed Meteor builds and anti-detection mods can avoid it. Wurst can't be detected this way,
  because it registers no vanilla keybinds or translations. It is caught by its behaviour instead.

### New checks

| Check | Detects | Typical modules |
|---|---|---|
| `InventoryA` | Moving items between two slots within a single tick | AutoTotem, Offhand, AutoArmor, ChestSwap, AutoReplenish |
| `SignA` | Writing a sign faster than humanly possible (inspired by NCP `blockplace.autosign`) | AutoSign |
| `WallHit` | Attacking an entity through solid blocks (inspired by NCP `fight.visible`) | KillAura / CrystalAura with "walls range" |
| `AimA` *(experimental)* | Combat rotations that don't match the player's mouse sensitivity | KillAura, AimAssist, BowAimbot, CrystalAura |

`AimA` only runs with `experimental-checks: true`, because cinematic camera and some trackpads can trigger it.

Everything Grim already detects stays: movement (flight, speed, step, jesus, NoFall, velocity, timer),
combat (reach, hitboxes, multi-target), block placement and breaking (scaffold, nuker, fast break), packet order and more.

## Requirements & Installation

- Java 17 or higher.
- A Spigot, Paper, Folia, or Fabric server environment.
- Install the jar for your platform and restart the server.

If you use a proxy such as Velocity or BungeeCord:
- If you use Geyser, Floodgate must be installed on the backend server (where Freeze Shield is).
- If you use ViaVersion, it must be installed on the backend server ONLY, not on the proxy.

Geyser (Bedrock) players are exempt from the anticheat to prevent false positives.

## Commands & Permissions

The main command is `/freezeshield` (alias `/fs`), for example `/fs alerts`, `/fs verbose`, `/fs debug <player>`,
`/fs history <player>`, `/fs reload`.

Permissions use the `freezeshield.` prefix, for example `freezeshield.alerts`, `freezeshield.verbose`,
`freezeshield.exempt`, `freezeshield.reload`.

> Migrating from Grim: the plugin folder is now `plugins/FreezeShield`, the command is `/freezeshield` instead of
> `/grim`, and permissions are `freezeshield.*` instead of `grim.*`. Copy your old config over and update
> your permission setup.

## Compiling From Source

1. `git clone https://github.com/LiGLP/Freeze-Shield.git`
2. `cd Freeze-Shield`
3. `./gradlew build`
4. The final jars are in the `<platform>/build/libs` folders.

## Credits & License

- **[GrimAC](https://github.com/GrimAnticheat/Grim)** by the GrimAnticheat team and contributors. Freeze Shield is a
  modified version of Grim, and the vast majority of the code is theirs.
- **[NoCheatPlus](https://github.com/Updated-NoCheatPlus/NoCheatPlus)**: the ideas behind `SignA` and `WallHit` come from its checks.
- **[PacketEvents](https://github.com/retrooper/packetevents)** for packet handling.

Freeze Shield is licensed under the **GNU General Public License v3.0**, the same as GrimAC. See [LICENSE](LICENSE).
This is a modified version of GrimAC; changes are listed in the git history.
