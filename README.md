# SodiumForge

**Radium (Sodium 0.8.15) ported to Minecraft 1.8.9 / Forge.**

SodiumForge brings the [Sodium](https://github.com/CaffeineMC/sodium-fabric) rendering
engine — via its [Radium](https://github.com/Adubbz/Radium) fork for the legacy
Yarn/1.8.9 ecosystem — to Forge 1.8.9. It replaces the vanilla chunk rendering
pipeline with a modern, heavily optimized one: faster chunk building on multiple
threads, smarter visibility culling, and dramatically reduced memory usage.

**Measured on a stock install (vanilla → SodiumForge): ~50 FPS → ~105 FPS at
render distance 7.**

## Requirements

| Item      | Version                                  |
|-----------|------------------------------------------|
| Minecraft | 1.8.9                                    |
| Forge     | 1.8.9 (11.15.1.2318, or any 1.8.9 build) |
| Java      | 8                                       |

## Installation

1. Install Forge 1.8.9 and run the game once.
2. Copy **both** files into your `.minecraft/mods/` folder:
   - `radium-0.8.15.jar`
   - `mixin-0.7.11-launchwrapper-bridge.jar` (required – Forge 1.8.9 has no bundled Mixin)
3. Launch the game. There is nothing else to configure.

> The mod installs as a Forge *core mod* (FML coremod): it does not appear in
> the mods list, and its mixins are applied at launch. If the game fails to
> start after installing it, remove the jar and check `latest.log`.

## Options

`Options → Video Settings` keeps the vanilla screen. Three extra rows at the
bottom of the settings list open the Sodium screens:

- **Performance…** — quad splitting ("Perfect Translucency"), chunk update
  threads, chunk update deferral, async culling, fog occlusion, block-face
  culling, smart culling, entity culling.
- **Quality…** — clouds, vignette, biome blend radius, cloud height, chunk fade.
- **Advanced…** — CPU render-ahead, staging buffers, memory tracing.

Labels are embedded in the controls ("Use Async Culling: ON"), two columns per
row, and hovering a control for a moment shows a tooltip explaining it.

## Configuration file

Settings are persisted to `<game directory>/config/radium-options.json` on
every change; safe to edit by hand while the game is closed.

## Building from source

Requirements: JDK 8 (the build enforces Java 8 class files), git, and network
access to the Forge/Maven repositories (the Gradle wrapper downloads everything
else, including the Forge 1.8.9 MDK with `stable_22` MCP mappings).

```bash
git clone https://github.com/RtxOP/SodiumForge.git
cd SodiumForge
JAVA_HOME=/path/to/jdk8 ./gradlew build
```

The output jar is `build/libs/radium-0.8.15.jar` — a fat jar with Mixin
shaded in and a runtime refmap. Copy it (plus the mixin bridge jar) into
`mods/` as described above.

## How it works

- **Coremod bootstrap** — `MixinLoader` (in `com.github.sonagg.radium`) injects
  the Mixin 0.7.11 runtime into the Forge 1.8.9 launch and applies the mixins
  from `radium-forge.mixins.json`.
- **Renderer** — the full Radium pipeline was ported: `RenderSectionManager`,
  chunk mesh building (`ChunkBuilderMeshingTask`, region-based uploads),
  translucency sorting, `RayOcclusionSectionTree` + async culling, and the
  1.8.9-compatible GL pipeline with LWJGL2.
- **Memory** — `@Redirect` on `GameSettings.renderDistanceChunks` during
  `loadRenderers` keeps the vanilla `ViewFrustum` from allocating
  (2·d+1)²×16 `RenderChunk` objects; the Sodium region-based storage replaces
  per-chunk buffers.
- **1.8.9 fidelity** — everything is written against MCP `stable_22` SRG names
  and Java 8 APIs; no Java 9+ constructs, no LWJGL3 calls.

## Compatibility notes

- LWJGL2 only (i.e. the stock 1.8.9 client). Not compatible with LWJGL3
  wrapper backports.
- The render-distance slider is extended to 32 chunks (vanilla 1.8.9 stops at 16).
- `.minecraft/mods/` must contain only the two jars above; other Forge mods
  that replace the chunk renderer will conflict.

## Credits

- **Sodium** — CaffeineMC and contributors (LGPL-3.0).
- **Radium** — Adubbz and contributors: the 1.8.9/Fabric fork this project is
  based on.
- **Fabric & Forge communities**, and the Mixin project.
- Port to Forge 1.8.9 by **Rtx**.
