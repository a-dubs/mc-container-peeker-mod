# Container Peeker

A lightweight **Fabric** mod for **Minecraft 1.21.11** that lets you peek inside a
container without opening it. Hold (or toggle) a hotkey while looking at a chest,
barrel, hopper, dropper, dispenser, shulker box, and more, and a small overlay pops
up in the corner of your screen showing exactly what's inside.

- 🔍 Look at a container + press a key → see its contents, no clicking required
- 🧱 The grid auto-sizes to the container (hopper, dropper, single/double chest, …)
- 🎛️ Fully configurable: corner, scale, margins, opacity, hold-vs-toggle
- 🌐 Works in singleplayer, on LAN, and on dedicated servers (with the mod installed server-side)

---

## Requirements

| Requirement | Version |
| --- | --- |
| Minecraft | 1.21.11 |
| [Fabric Loader](https://fabricmc.net/use/installer/) | ≥ 0.18.1 |
| [Fabric API](https://modrinth.com/mod/fabric-api) | for 1.21.11 (required) |
| Java | 21+ |

**Optional** (for the in-game settings screen):

- [Mod Menu](https://modrinth.com/mod/modmenu)
- [Cloth Config API](https://modrinth.com/mod/cloth-config)

Without those two, the mod still works fully — you just configure it by editing a
JSON file instead of using a GUI.

---

## Installation

1. Install **Fabric Loader** for Minecraft 1.21.11.
2. Drop **Fabric API** into your `mods/` folder.
3. Drop **`containerpeeker-<version>.jar`** (from the
   [Releases page](https://github.com/a-dubs/mc-container-peeker-mod/releases))
   into your `mods/` folder.
4. *(Optional)* add Mod Menu + Cloth Config for the in-game config screen.

### Multiplayer

To use it on a dedicated server, install **the same jar + Fabric API on the server too**.
Mod Menu and Cloth Config are **not** needed server-side. See
[Multiplayer & limitations](#multiplayer--limitations) for details.

---

## Usage

- Default key: **`V`** (rebindable in **Options → Controls → Key Binds → Container Peeker**).
- Look at a container and **hold `V`** (default) to show the overlay.
- Prefer a toggle? Switch the activation mode to `TOGGLE` (config), then a single
  press flips the overlay on/off.

The overlay's grid matches the container type, e.g.:

| Container | Layout |
| --- | --- |
| Hopper | 1 × 5 |
| Dropper / Dispenser | 3 × 3 |
| Chest / Barrel / Shulker | 9 × 3 (27) |
| Double chest | 9 × 6 (54) |

---

## Configuration

Configure in-game via **Mod Menu → Container Peeker → Config** (requires Cloth Config),
or edit `config/containerpeeker.json` directly. The file is created with defaults on
first launch.

| Option | Default | Description |
| --- | --- | --- |
| `enabled` | `true` | Master switch for the overlay. |
| `mode` | `HOLD` | `HOLD` (show while held) or `TOGGLE` (press to flip). |
| `corner` | `BOTTOM_RIGHT` | `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, or `BOTTOM_RIGHT`. |
| `scale` | `1.0` | Overall size multiplier (0.25–4.0). |
| `marginX` | `8` | Horizontal gap from the screen edge (GUI pixels). |
| `marginY` | `8` | Vertical gap from the screen edge (GUI pixels). |
| `backgroundOpacity` | `75` | Panel background opacity, 0–100. |
| `showTitle` | `true` | Draw the container name above the grid. |
| `hideWhenEmpty` | `false` | Hide the overlay when the container is empty. |

---

## Multiplayer & limitations

How contents are read depends on where you're playing:

- **Singleplayer / LAN host** — contents are read directly from the integrated
  server, so they're always live and accurate (no server-side setup needed).
- **Dedicated server with the mod installed** — the client asks the server for the
  contents of the container you're aiming at; the server replies with a live
  snapshot. Requires the mod on **both** client and server.
- **Server without the mod** — the client sends nothing and the overlay simply shows
  empty. Nothing breaks.

Notes:

- The server validates **distance (~8 blocks)** but not line-of-sight. In normal play
  the client only requests the block your crosshair is on, but this isn't an
  anti-grief boundary.
- Reading is **read-only** — the mod never modifies containers.

---

## Building from source

```bash
git clone https://github.com/a-dubs/mc-container-peeker-mod.git
cd mc-container-peeker-mod
./gradlew build
# artifact: build/libs/containerpeeker-<version>.jar
```

Requires JDK 21. For development details and conventions, see [AGENTS.md](AGENTS.md).

---

## License

[MIT](LICENSE) © a-dubs
