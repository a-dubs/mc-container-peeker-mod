# AGENTS.md — Contributing & Development Guide

Guidance for humans and AI agents working on **Container Peeker**. Read this before
making changes. For user-facing docs see [README.md](README.md).

---

## What this mod does

A client-side-focused Fabric mod that renders an overlay of a container's contents
when the player looks at it and holds/toggles a hotkey. On dedicated servers it uses a
small request/response packet pair to fetch live contents (the mod is also installable
server-side).

---

## Tech stack

| Thing | Value |
| --- | --- |
| Minecraft | 1.21.11 |
| Mappings | **Mojang official** (`loom.officialMojangMappings()`) — Yarn is EOL after 1.21.11 |
| Loom | `net.fabricmc.fabric-loom-remap` 1.16 |
| Gradle | 9.4.1 (wrapper committed) |
| Java | 21 (`options.release = 21`) |
| Fabric Loader | dev 0.19.3, runtime requirement `>=0.18.1` |
| Fabric API | `0.141.4+1.21.11` |
| Cloth Config | `21.11.153` (optional) |
| Mod Menu | `17.0.0` (optional) |

Dependency versions live in `gradle.properties`. Maven repos for Cloth/Mod Menu are in
`build.gradle` (`maven.shedaniel.me`, `maven.terraformersmc.com`).

---

## Prerequisites

- **JDK 21 is required.** This machine's default `JAVA_HOME` may point at a broken JDK.
  Prefer:
  ```bash
  export JAVA_HOME="$(/usr/libexec/java_home -v 21)"   # macOS
  ```
  The release script already does this automatically.

---

## Project structure

```
src/
  main/java/com/adubs/containerpeeker/
    ContainerPeeker.java            # ModInitializer (common). MOD_ID, LOGGER,
                                    # payload registration, server-side request handler.
    net/PeekPayloads.java           # C2S Request(BlockPos) + S2C Response(BlockPos, items)
                                    # custom payloads, their Types and StreamCodecs.
  main/resources/
    fabric.mod.json                 # id=containerpeeker, environment="*", entrypoints
    assets/containerpeeker/lang/en_us.json
  client/java/com/adubs/containerpeeker/client/
    ContainerPeekerClient.java      # ClientModInitializer: keybind, client tick loop,
                                    # response receiver, per-tick resolve, HUD hook.
    ContainerReader.java            # Resolves the looked-at container + snapshots contents
                                    # (integrated-server read or remote cache). Holds PeekResult.
    PeekHud.java                    # Draws the overlay grid via GuiGraphics.
    PeekConfig.java                 # JSON config (Gson) at config/containerpeeker.json.
    PeekConfigScreen.java           # Cloth Config screen.
    ModMenuIntegration.java         # ModMenuApi entrypoint -> PeekConfigScreen.
scripts/release.sh                  # build + tag + push + GitHub release
```

> Naming note: the **main** class is `ContainerPeeker`; the **resolver** is
> `ContainerReader` (deliberately not also called `ContainerPeeker` to avoid a clash).

---

## Build & run

```bash
./gradlew build         # compile + produce build/libs/containerpeeker-<version>.jar
./gradlew runClient     # launch a dev client
./gradlew runServer     # launch a dev dedicated server (accept EULA in run/eula.txt)
```

Smoke test: a successful client launch logs
`Container Peeker initialized (mode=..., corner=...)`; a successful server launch
reaches `Done (...)!` with `containerpeeker <version>` in the mod list.

---

## How it works

1. **Resolve (client, once per tick):** `ContainerPeekerClient#onClientTick` raycasts
   the block under the crosshair (`Minecraft#hitResult`) and calls
   `ContainerReader#resolveLookedAtContainer`. Resolving every *tick* (not every frame)
   avoids redundant lookups/item copies at high FPS; the HUD callback just draws the
   cached `currentResult`.
2. **Reading contents:**
   - Singleplayer / LAN host → read the authoritative block entity from the integrated
     server level (`Minecraft#getSingleplayerServer`).
   - Remote server → use a short-lived cache populated by `PeekPayloads.Response`.
   - Both use `HopperBlockEntity.getContainerAt(level, pos)`, which transparently
     handles double chests and any `Container` block entity.
3. **Networking (multiplayer):** while peeking on a remote server, the client throttles
   `PeekPayloads.Request` packets (~10/sec on the same target; instantly on target
   change), guarded by `ClientPlayNetworking.canSend(...)`. The server
   (`ContainerPeeker#respond`) validates distance (~8 blocks), reads the live container,
   and replies. Payload types are registered in the common initializer so they exist on
   both sides.

---

## 1.21.11 / Mojang-mapping gotchas

These tripped up development; keep them in mind when editing:

- `ResourceLocation` is named **`Identifier`** (`net.minecraft.resources.Identifier`) in
  this mapping set. Use `Identifier.fromNamespaceAndPath(...)`.
- Keybinds use **`KeyMapping.Category`** objects, not category strings. Register via
  `KeyMapping.Category.register(Identifier...)` and the
  `KeyMapping(name, InputConstants.Type.KEYSYM, key, category)` constructor.
- Category lang key format: `key.category.<namespace>.<path>`.
- `CustomPacketPayload.createType(String)` forces the `minecraft` namespace and rejects
  colons — build types with `new CustomPacketPayload.Type<>(Identifier...)` instead.
- Get the server from a player via `player.level().getServer()` (no `getServer()` on
  `ServerPlayer`/`Entity` here).
- `GuiGraphics#pose()` is a 2D matrix stack: use `pushMatrix()` / `popMatrix()` /
  `translate(x, y)` / `scale(x, y)`.
- Useful codecs: `BlockPos.STREAM_CODEC`, `ItemStack.OPTIONAL_LIST_STREAM_CODEC`.

When unsure of a Mojang-mapped name, inspect the merged jar under
`~/.gradle/caches/fabric-loom/.../minecraft-merged-*.jar` with `javap -p`.

---

## Conventions

- **Mappings:** Mojang official. Avoid Yarn names.
- **No mixins** currently — prefer Fabric API events/hooks to keep conflict risk low.
- **Client vs common:** anything touching client-only classes (rendering, keybinds,
  Mod Menu, Cloth) lives in the `client` source set. The common initializer must stay
  server-safe (it runs on dedicated servers).
- **Optional deps:** Cloth Config / Mod Menu are `suggests`, never `depends`. Don't
  reference them from common code.
- **Comments:** explain *why*, not *what*. No narration comments.

### Commit messages (Conventional Commits)

```
<type>(<scope>): <summary>      # present tense, ≤72 chars, lowercase
```

Body explains *what changed for the user* and *why* — not implementation mechanics.
Types used here: `feat`, `fix`, `perf`, `refactor`, `chore`.

---

## Versioning & releasing

Version is a single source of truth: `mod_version` in `gradle.properties` (it also
drives the jar name).

```bash
scripts/release.sh            # release current version as-is
scripts/release.sh patch      # x.y.Z+1, then release
scripts/release.sh minor      # x.Y+1.0, then release
scripts/release.sh major      # X+1.0.0, then release
scripts/release.sh --set 2.3.1
scripts/release.sh patch -y   # skip the confirmation prompt
```

The script: refuses to run with **uncommitted tracked changes** (untracked is fine),
selects JDK 21, optionally bumps + commits `gradle.properties`, runs `./gradlew clean
build`, creates an annotated `vX.Y.Z` tag, pushes branch + tag, and publishes a GitHub
release with the jar attached (`gh release create ... --generate-notes`). Requires `gh`
authenticated and an `origin` remote.

---

## Repository

<https://github.com/a-dubs/mc-container-peeker-mod>
