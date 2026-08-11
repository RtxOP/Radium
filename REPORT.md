# SodiumForge — Port Status & Developer Guide

**Project:** A port of **Radium** (the [SonaGG/Radium](https://github.com/SonaGG/Radium) fork of Sodium for Minecraft **1.8.9**) from **Fabric to Forge 1.8.9**.
**Assessment date:** 2026-08-05
**Overall status:** ~5% complete. **The project does not currently compile.** The platform scaffolding (build.gradle, Mixin bootstrap, manifest, mixin config, a stub API surface) is largely the *correct* canonical Forge1.8.9 recipe, but the actual port work — renaming the reference's modern-Yarn source to MCP 1.8.9 names — is barely started.

> This document is intended to be the single source of context for starting development. Everything here was verified against the repo, the decompiled MCP 1.8.9 sources (`Marcelektro/MCP-919`), and the canonical Forge1.8.9 Mixin template (`manuthebyte/template-forge-mixin-1.8.9`). Read this first; then read code only where this points you.

---

## Table of contents

1. [Repo layout](#1-repo-layout)
2. [The reference (Radium-Reference) and its real strategy](#2-the-reference-and-its-real-strategy) — **read this first**
3. [Build & toolchain](#3-build--toolchain)
4. [Blocking issues](#4-blocking-issues)
5. [MCP 1.8.9 name reference (Yarn → MCP)](#5-mcp-189-name-reference-yarn--mcp)
6. [Per-mixin audit](#6-per-mixin-audit)
7. [Ported `net/` classes (per-file status)](#7-ported-net-classes-per-file-status)
8. [What is missing](#8-what-is-missing)
9. [Mixin config inventory](#9-mixin-config-inventory)
10. [Reference bootstrap (Fabric) — the seams a Forge port must replace](#10-reference-bootstrap-fabric--the-seams-a-forge-port-must-replace)
11. [Recommended next steps (ordered)](#11-recommended-next-steps-ordered)
12. [Sources](#12-sources)

---

## 1. Repo layout

```
/home/e/dev/SodiumForge/
├── build.gradle                     # ForgeGradle 2.1 + MixinGradle 0.6 + Shadow 2.0.4
├── gradle.properties                # -Xmx4G, daemon off
├── settings.gradle                  # rootProject.name = 'radium-forge'
├── gradle/wrapper/gradle-wrapper.properties  # Gradle 3.1
├── forge-1.8.9-MDK/                 # Stock 1.8.9 MDK (ExampleMod shows correct net.minecraftforge.fml imports)
├── Radium-Reference/                # THE Fabric 1.8.9 reference implementation (a git clone)
│   ├── common/                      # ~391 net.caffeinemc.mods.sodium files + 31 gg.sona.radium.mixin files + an `api` source set
│   ├── fabric/                      # Fabric bootstrap (SodiumFabricMod, Services impls, fabric.mod.json)
│   └── buildSrc/                    # Ploceus/Loom build config (see §2)
└── src/main/
    ├── java/com/github/sonagg/radium/     # MixinLoader.java, RadiumForgeMod.java (BROKEN — see §4.1)
    ├── java/gg/sona/radium/mixin/        # 26 mixin files (20 broken, 4 stub, 3 faithful)
    ├── java/net/caffeinemc/mods/sodium/  # 23 of the ~391 reference classes (17 are stubs)
    ├── java/net/minecraft/client/renderer/ # FluidRendererStub, BlockRendererDispatcherStub (bad idea — see §4.4)
    └── resources/                        # radium-forge.mixins.json, mcmod.info, sodium-common.accesswidener, shaders, lang
```

**Git state:** branch `main`, 26 mixin files modified-but-uncommitted, `src/main/java/net/` entirely untracked. No `build/` artifacts exist (the `build/` dir only contains a `reports/problems/problems-report.html` from a failed Gradle 9.4 run).

---

## 2. The reference and its real strategy — **read this first**

`Radium-Reference/` is a **Fabric 1.8.9** mod. The single most important fact for this port:

> **The reference does NOT hand-rename its source to 1.8.9 names.** It keeps modern-Yarn Sodium source and remaps it **at build time** using **Ploceus** (Ornithe's Loom extension):

```kotlin
// Radium-Reference/common/build.gradle.kts + fabric/build.gradle.kts
ploceus { setIntermediaryGeneration(2) }
mappings "net.legacyfabric:legacy-yarn:1.8.9+build.4:v2"
```

With *intermediary generation 2* (modern intermediary), code written against modern Yarn names is remapped down to 1.8.9 during `remapJar`. Ploceus **rewrites Mixin annotation string references in bytecode too**, which is why the reference mixins carry **no refmap** and why its configs legally say `"minVersion": "0.8"` (Fabric Loader supplies Mixin 0.8.7) and `"compatibilityLevel": "JAVA_17"` (the 1.8.9 client is launched on Java 17 + [legacy-lwjgl3](https://modrinth.com/mod/legacy-lwjgl3)).

**Why the reference mixins look "modern":** e.g. `ClientWorld`, `GameRenderer`, `GameOptions`, `sortQuads`, `Camera`, `RenderLayer`, `BlockEntityRenderDispatcher`, `textRenderer`, `getLoadedEntities`, `method_6915` — those are *Yarn* names. At Ploceus build time they become `WorldClient`, `EntityRenderer`, `GameSettings`, `sortVertexData`, `ActiveRenderInfo`, `EnumWorldBlockLayer`, `TileEntityRendererDispatcher`, `fontRenderer`, `loadedEntityList`, `func_...` respectively. **The Forge port cannot use that pipeline** (Forge 1.8.9 consumes MCP names natively) — so every Yarn name must be hand-translated to MCP `stable_22`, *or* an equivalent build-time mapping-translation step must be constructed (see §11, step 7).

### Reference facts you'll need

- **fabric.mod.json** (`Radium-Reference/fabric/src/main/resources/fabric.mod.json`):
  - `minecraft: ["1.8.9"]`, `fabricloader: ">=0.16.0"`, `legacy-lwj3: ">=1.2.11"`, `suggests osl >= 0.17.0`
  - entrypoints: `client → net.caffeinemc.mods.sodium.fabric.SodiumFabricMod`, `preLaunch → net.caffeinemc.mods.sodium.fabric.SodiumPreLaunch`
  - `accessWidener: sodium-fabric.accesswidener`, `mixins: ["radium.mixins.json", "sodium-fabric.mixins.json"]`
- **Bootstrap flow:** `SodiumFabricMod.onInitializeClient()` → `ConfigLoaderFabric.collectConfigEntryPoints()` (registers config entrypoints, registers `SodiumConfigBuilder` as the `radium` entrypoint) → `SodiumClientMod.onInitialization(version)` → `ConfigManager.registerConfigsEarly()`. `SodiumPreLaunch.onPreLaunch()` is an empty no-op in the reference.
- **Service seams** (via `META-INF/services/`, loaded by `net.caffeinemc.mods.sodium.client.services.Services`):
  - `CaffeineConfigPlatform` → `gg.sona.radium.mixin.config.CaffeineConfigFabric`
  - `PlatformMixinOverrides` → `net.caffeinemc.mods.sodium.fabric.FabricMixinOverrides` (reads JSON key `radium:options`)
  - `PlatformRuntimeInformation` → `net.caffeinemc.mods.sodium.fabric.FabricRuntimeInformation` (the only seam that is actually live in the reference)
- **The mixin-option machinery is DEAD CODE in the reference.** Neither `radium.mixins.json` nor `sodium-fabric.mixins.json` declares a `"plugin"` key, and no concrete `AbstractCaffeineConfigMixinPlugin` subclass exists anywhere. `shouldApplyMixin()` checks prefixes `dev.vexor.radium.mixin.sodium` / `dev.vexor.radium.mixin.extra` which never match the real `gg.sona.radium.mixin` package — a **pre-existing leftover from the original VexorMC fork** (the `api` source set's `dev/vexor/radium/compat/mojang/**` shims are the same leftover). So in practice **every mixin always applies**. The port inherited all of this unchanged.
- The reference's 1.8.9-specific adaptation lives in two places: (a) the `gg.sona.radium.mixin.*` mixins, and (b) `Radium-Reference/common/src/api/java/dev/vexor/radium/compat/mojang/**` shims (e.g. `FluidSprites`, `Mth`, `SectionPos`, `QuartPos`, `FogHelper`, `LightTexture`, `LinearCongruentialGenerator`). The shims were **not** copied into the Forge port.
- Upstream: `github.com/SonaGG/Radium` (archived Jul 27 2026, branch `dev`, 1543 commits, build requires OpenJDK 17 + Gradle 8.10.x; distributed on [Modrinth](https://modrinth.com/mod/radium-mod)).

---

## 3. Build & toolchain

### 3.1 What is already correct ✅ (verified against the canonical template)

The `build.gradle` is a faithful copy of the working `manuthebyte/template-forge-mixin-1.8.9` recipe. Do **not** change these:

| Item | Location | Why it's right |
|---|---|---|
| `mappings = 'stable_22'` | `build.gradle:38` | `stable_22` is the correct stable MCP mapping for **1.8.9**; `stable_20` is for **1.8.8**. Verified from [MCP-Archive `versions.json`](https://github.com/Aizistral-Studios/MCP-Archive). |
| Gradle **3.1** wrapper | `gradle/wrapper/gradle-wrapper.properties` | The manuthebyte template uses exactly Gradle 3.1. ForgeGradle 2.1 works on Gradle 2.7–4.x; **Gradle 7+ hard-breaks** (the `compile` configuration ForgeGradle 2.1 uses was removed in Gradle 7), Gradle 9.x is impossible. |
| ForgeGradle `2.1-SNAPSHOT` | `build.gradle:8` | The 1.8.9 line (2.0.2 was for 1.8.8). |
| MixinGradle `0.6-SNAPSHOT` | `build.gradle:10` | 0.6 targets ForgeGradle 2.x / Gradle 2–4; **it does run on Gradle 3.1** (the Gradle 5+ requirement belongs to MixinGradle 0.7 for Mixin 0.8). 0.6's refmap generation integrates with ForgeGradle 2.1 reobf. |
| Shadow `2.0.4` | `build.gradle:15` | Needs Gradle ≥3.0. |
| Shaded `org.spongepowered:mixin:0.7.11-SNAPSHOT` | `build.gradle:59` | Mixin **0.7.x is the canonical runtime for Forge 1.8.9** (Mixin must be shaded — Forge 1.8.9 doesn't bundle it). Mixin 0.8 does not bootstrap cleanly on legacy Forge (use [MixinBooter](https://www.curseforge.com/minecraft/mc-mods/mixin-booter) if you ever need 0.8 features); 0.7 and 0.8 cannot coexist ([Mixin#469](https://github.com/SpongePowered/Mixin/issues/469)). |
| Shadow exclusions (launchwrapper, guava, gson, commons-io, log4j-core) | `build.gradle:60-64` | Those libs are already on Forge 1.8.9's classpath. |
| FMLCorePlugin + TweakClass + MixinBootstrap pattern | `build.gradle:106-115`, `MixinLoader.java` | Verified to work on 1.8.9: a jar whose manifest has `TweakClass` goes down the cascading-tweak path; Mixin 0.7's `MixinPlatformAgentFML` re-injects the FMLCorePlugin. `getASMTransformerClass()` returning `[]` is correct. |
| `compatibilityLevel: JAVA_8` | `radium-forge.mixins.json:5` | Correct for Forge1.8.9 (the reference's `JAVA_17` would be wrong here). |
| Manifest attrs (`FMLCorePluginContainsFMLMod`, `ForceLoadAsMod`, `ModSide: CLIENT`, `TweakOrder`, `MixinConfigs`) | `build.gradle:106-114` | Canonical. |
| refmap wiring (`sourceSets.main.ext.refMap`) | `build.gradle:120-124` | Correct; `radium-forge.mixins.refmap.json` must be emitted by the Mixin AP and bundled in the jar (see §11 step 4). |

### 3.2 How to build (Java 8 — required)

The machine's default `java` is **Java 21**, which Gradle 3.1 and ForgeGradle 2.1 cannot run on. A **JDK 8** is installed at `/usr/lib/jvm/temurin-8-jdk-amd64`.

```bash
cd /home/e/dev/SodiumForge
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 ./gradlew build
# or: ./gradlew setupDecompWorkspace genEclipseRuns / runClient / etc.
```

First run decompiles the 1.8.9 jar (Fernflower, ~4 GB heap — already configured in `gradle.properties`) and downloads deps from `maven.minecraftforge.net` and `repo.spongepowered.org` (network is available).

### 3.3 Toolchain traps

- **`.gradle/9.4.0/`** in the project root is a failed Gradle 9.4 run (the `build/reports/problems/problems-report.html` is Gradle 7.4+ "configuration-cache-report" output). Gradle 9 cannot run ForgeGradle 2.1. Ignore/delete it.
- Gradle 6.9.4 is cached under `~/.gradle/wrapper/dists/gradle-6.9.4-all` but is **not** supported by ForgeGradle 2.1 (ceiling is ~Gradle 4.x). Stick with the 3.1 wrapper.
- Annotation processors on Gradle <5 are auto-discovered from the compile classpath, so the Mixin AP runs and emits the refmap with the current setup; on Gradle 5+ you'd need an `annotationProcessor` configuration.
- If reobf throws `Could not determine mapping type for obf task` or genSrgs complains about `packaged.srg`/`packaged.exc`, wipe `~/.gradle/caches/minecraft` and the project `.gradle/` and re-run — the classic 1.8.9 cache fix.

---

## 4. Blocking issues

### 4.1 Compile blockers (3 independent causes — fix these first)

1. **Wrong FML package imports.** Forge renamed the whole FML package from `cpw.mods.fml` to `net.minecraftforge.fml` in MC 1.8; the 1.8.9 branch has **no `cpw` package at all** (verified against [the MinecraftForge 1.8.9 tree](https://github.com/MinecraftForge/MinecraftForge/tree/1.8.9/src/main/java) and the MDK's `ExampleMod.java`).
   - `src/main/java/com/github/sonagg/radium/MixinLoader.java:3`
     `import cpw.mods.fml.relauncher.IFMLLoadingPlugin;` → `import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;`
   - `src/main/java/com/github/sonagg/radium/RadiumForgeMod.java:3-4`
     `import cpw.mods.fml.common.Mod;` → `import net.minecraftforge.fml.common.Mod;`
     `import cpw.mods.fml.common.event.FMLInitializationEvent;` → `import net.minecraftforge.fml.common.event.FMLInitializationEvent;`
2. **Stub `SodiumClientMod` is missing `onInitialization(String)`.** `RadiumForgeMod.java:15` calls it; the stub only has `options()` and `getVersion()`. Either add the method to `src/main/java/net/caffeinemc/mods/sodium/client/SodiumClientMod.java` or change the call site.
3. **Stub `MixinConfig` is missing `load(File)` / `getEffectiveOptionForMixin(String)` / `getOptionCount()` / `getOptionOverrideCount()`.** `src/main/java/net/caffeinemc/mods/sodium/client/data/config/MixinConfig.java` is an empty 4-line class; `src/main/java/gg/sona/radium/mixin/config/AbstractCaffeineConfigMixinPlugin.java:26,51,80` call those methods → `cannot find symbol`. Options: port the reference `MixinConfig` (rules `core`/`features.*`/`workarounds.*`, reads `./config/radium-mixins.properties`), or delete the option-gating entirely (see §4.4 — it's dead code anyway).

Plus ~15 mixins with unresolvable symbols / non-Java-8 syntax — see the [per-mixin table](#6-per-mixin-audit). The biggest repeat offenders: `var` (Java 10), `String.formatted` (Java 15), and `net.fabricmc.api.EnvType/Environment` imports (Fabric isn't on the classpath).

### 4.2 Fatal runtime bug: `minVersion` vs shaded Mixin

`src/main/resources/radium-forge.mixins.json:3` declares `"minVersion": "0.8"`, but the shaded runtime Mixin is `0.7.11-SNAPSHOT`. Mixin 0.7.x `MixinConfig.checkVersion()` does strict semver (`0.8.0 > 0.7.11`) and — because the config is `"required": true` — throws **`MixinInitialisationError` during bootstrap → hard crash at startup** (not a warning). Verified from [Mixin 0.7.x source](https://github.com/SpongePowered/Mixin/blob/0.7_dev/src/main/java/org/spongepowered/asm/mixin/transformer/MixinConfig.java).

**Fix:** change `"minVersion": "0.8"` → `"minVersion": "0.7"` (or just remove the key — `VersionNumber.parse(null)` yields `0.0.0`, non-fatal). Keep `"required": true` and `"compatibilityLevel": "JAVA_8"` — both fine on 0.7.11.

### 4.3 Mixin targets mostly don't exist in 1.8.9

With `required: true` + `injectors.defaultRequire: 1` (both set in the config), any `@Inject`/`@Overwrite`/`@Redirect`/`@ModifyConstant`/`@Shadow` whose target is absent from MCP 1.8.9 crashes at apply time — **even after the build compiles**. 20 of 26 mixins have wrong member names. Use the mapping table in [§5](#5-mcp-189-name-reference-yarn--mcp).

**During development**, shrink the config's `client`/`mixins` lists to only mixins that currently apply (SpriteMixin, AChunk, DimensionMixin) so `required:true` doesn't brick startup; re-add mixins as you fix them.

### 4.4 Inert / dead scaffolding (copy-paste residue)

- **Mixin option-gating is never wired**: `radium-forge.mixins.json` has **no `"plugin"` key**, and `AbstractCaffeineConfigMixinPlugin` is abstract with **no subclass** in the port → `onLoad()` never runs. `shouldApplyMixin()` checks `dev.vexor.radium.mixin.*` prefixes that never match (`gg.sona.radium`). Decide: wire it (fix the prefix + register the plugin) or delete it. The reference's effective behavior is "all mixins always apply", so deleting is the pragmatic choice.
- **`CaffeineConfig` latent crash**: `gg/sona/radium/mixin/config/CaffeineConfig.java:27` — `ServiceLoader.load(CaffeineConfigPlatform.class).findFirst().get()` throws `NoSuchElementException` the moment `CaffeineConfig` is loaded, because **no `META-INF/services` file exists** in the port (the reference has one in `Radium-Reference/fabric/src/main/resources/META-INF/services/`). Currently harmless only because nothing loads it.
- **`sodium-common.accesswidener`** (in `src/main/resources/`) is a **Fabric-only artifact** — referenced nowhere, inert in the jar, and its entries reference 1.14+ classes. On Forge, access widening is done via **Access Transformers** (`*_at.cfg` moved to `META-INF/` — `build.gradle:88` renames them, but **zero `_at.cfg` files exist**). Convert needed entries to `@Accessor`/`@Invoker` mixins instead.
- **The two stub classes are a bad idea.** 1.8.9 **does** have real `net.minecraft.client.renderer.BlockRendererDispatcher` and `net.minecraft.client.renderer.BlockFluidRenderer`. The port instead invented `src/main/java/net/minecraft/client/renderer/FluidRendererStub.java` + `BlockRendererDispatcherStub.java` and hung `AFluidRenderer`/`ABlockRenderManager` accessors off them. Those stubs are never instantiated by the game, so the accessors are dead and any future `(AFluidRenderer)(Object)realRenderer` cast would `ClassCastException`. Delete the stubs and target the real classes (or drop the accessors — in 1.8.9 liquids render via `RenderGlobal`/`LiquidBlockRenderer`, so the modern fluid-renderer access pattern doesn't map cleanly anyway).
- **Duplicate config registration**: `TweakClass`+`MixinConfigs` (manifest) *and* `MixinLoader.addConfiguration()` both load `radium-forge.mixins.json`. Mixin de-dups by config name — harmless, but fragile.
- **`DimensionMixin` is orphaned**: `gg/sona/radium/mixin/sodium/features/options/world/DimensionMixin.java` is a faithful mixin (`@Overwrite WorldProvider.getCloudHeight()`, which exists in 1.8.9) but is **not listed** in `radium-forge.mixins.json` → never loads.

### 4.5 Even if it ran, it would be a no-op / crash

- `SodiumWorldRenderer` is a stub; `instance()` **throws `UnsupportedOperationException`** → crash on entity rendering (`LevelRendererMixin` calls it).
- `LevelRendererMixin`'s `@Overwrite`s forward the vanilla terrain pass to the stub renderer → **no terrain renders**.
- Everything else is stubbed no-ops (`RenderDevice`, `ConfigManager`, `ConsoleHooks`, `FPSCounter`, `ChunkTracker`, …).

---

## 5. MCP 1.8.9 name reference (Yarn → MCP)

The reference is modern-Yarn source. These are the 1.8.9 (stable_22) equivalents the mixins need — **all verified against the decompiled [MCP-919](https://github.com/Marcelektro/MCP-919) sources** (the canonical MCP 1.8.9 tree) and/or the mapping docs.

### 5.1 Classes

| Yarn / modern | MCP 1.8.9 |
|---|---|
| `net.minecraft.client.MinecraftClient` | `net.minecraft.client.Minecraft` |
| `net.minecraft.client.world.ClientWorld` | `net.minecraft.client.multiplayer.WorldClient` |
| `net.minecraft.client.render.WorldRenderer` | `net.minecraft.client.renderer.RenderGlobal` |
| `net.minecraft.client.render.GameRenderer` | `net.minecraft.client.renderer.EntityRenderer` |
| `net.minecraft.client.render.BufferBuilder` | `net.minecraft.client.renderer.WorldRenderer` *(the vertex builder — renamed to BufferBuilder only in 1.12)* |
| `net.minecraft.client.gl.Framebuffer` | `net.minecraft.client.shader.Framebuffer` |
| `net.minecraft.client.option.GameOptions` | `net.minecraft.client.settings.GameSettings` |
| `net.minecraft.client.gui.screen.Screen` | `net.minecraft.client.gui.GuiScreen` |
| `net.minecraft.client.gui.screen.SettingsScreen` | `net.minecraft.client.gui.GuiVideoSettings` |
| `net.minecraft.client.gui.hud.InGameHud` | `net.minecraft.client.gui.GuiIngame` |
| `net.minecraft.client.gui.hud.DebugHud` | `net.minecraft.client.gui.GuiOverlayDebug` |
| `net.minecraft.util.math.Direction` | `net.minecraft.util.EnumFacing` |
| `net.minecraft.client.texture.Sprite` | `net.minecraft.client.renderer.texture.TextureAtlasSprite` |
| `net.minecraft.world.chunk.ClientChunkProvider` | `net.minecraft.client.multiplayer.ChunkProviderClient` |
| `net.minecraft.world.dimension.Dimension` | `net.minecraft.world.WorldProvider` |
| `net.minecraft.world.level.LevelProperties` | `net.minecraft.world.storage.WorldInfo` |
| `net.minecraft.client.network.ClientPlayNetworkHandler` | `net.minecraft.client.network.NetHandlerPlayClient` |
| `net.minecraft.world.Difficulty` | `net.minecraft.world.EnumDifficulty` |
| `net.minecraft.entity.LivingEntity` | `net.minecraft.entity.EntityLivingBase` |
| `net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher` | `net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher` |
| `net.minecraft.client.util.math.BlockPos.Mutable` | `net.minecraft.util.BlockPos` (immutable in 1.8.9 — no `.Mutable`) |
| `net.minecraft.client.render.Camera` / `CameraView` | `net.minecraft.client.renderer.ActiveRenderInfo` / `net.minecraft.client.renderer.culling.ICamera` |
| `net.minecraft.util.Identifier` | `net.minecraft.util.ResourceLocation` |
| `net.minecraft.util.Formatting` | `net.minecraft.util.EnumChatFormatting` |
| `net.minecraft.client.render.RenderLayer` | `net.minecraft.util.EnumWorldBlockLayer` |

### 5.2 Members (correct 1.8.9 MCP names)

| Yarn / modern | MCP 1.8.9 |
|---|---|
| `WorldRenderer.sortQuads` / fields `buffer`, `format` | `sortVertexData(float,float,float)` / fields `byteBuffer`, `vertexFormat` (also `rawIntBuffer`, `rawShortBuffer`, `rawFloatBuffer`, `vertexCount`, `drawMode`, `isDrawing`) |
| `VertexFormat.getVertexSizeInteger()` | `VertexFormat.getIntegerSize()` (and `getNextOffset()`, `getTotalSize()`) |
| `Minecraft.profiler` | `mcProfiler` |
| `Minecraft.initializeGame()` | `startGame()` |
| `Minecraft.tick()` | `runTick()` |
| `Minecraft.isAmbientOcclusionEnabled()` | **Exists** in 1.8.9 (static; reads `gameSettings.ambientOcclusion`) |
| `Minecraft.setGlErrorMessage(String)` | **Does not exist** in 1.8.9 |
| `Framebuffer.attachTexture` | `createFramebuffer(int,int)` (hardcodes GL_RGBA8 = 0x8058 = 32856) |
| `GuiIngame.render` | `renderGameOverlay(float)` |
| `EntityRenderer.render` | `updateCameraAndRender(float, long)` |
| `GuiOverlayDebug.getRightText` | `renderDebugInfoRight(ScaledResolution)` |
| `GameSettings.getCloudMode()` | **Does not exist** — 1.8.9 uses `shouldRenderClouds()` / field `renderClouds` |
| `GameSettings.viewDistance` | `renderDistanceChunks` |
| `GameSettings.Option.RENDER_DISTANCE.setMaxValue(32)` | `GameSettings.Options.RENDER_DISTANCE.setValueMax(float)` (name differs) |
| `EntityRenderer.renderWeather` | `renderRainSnow(float)` |
| `EntityRenderer.renderClouds` | `renderCloudsCheck(RenderGlobal, float, int)` |
| `BlockLeaves.setGraphics` | `setGraphicsLevel(boolean)` |
| `ChunkProviderClient.getOrGenerateChunk` | `loadChunk(int,int)` / `provideChunk(int,int)` / `unloadChunk(int,int)` |
| `ChunkProviderClient.world` (shadow) | `worldObj` |
| `Chunk.unloadFromWorld()` / `setChunkLoaded(boolean)` | `onChunkUnload()` / `onChunkLoad()` |
| `BiomeGenBase.BIOMES`, `OCEAN`, `LOGGER` | `biomeList`, `ocean`, `logger` |
| `BiomeGenBase.getBiomeById(int, BiomeGenBase)` | `getBiomeFromBiomeList(int, BiomeGenBase)` |
| `ClippingHelper.homogeneousCoordinates` / `multiply` | `frustum` / `dot` |
| `EntityRenderer.client` (shadow) | `mc` |
| `Entity.getCameraPosVec`, `hasVehicle()`, `rider`, `shouldRender(...)` | `getPositionEyes(float)`, `riddenByEntity`, `isInRangeToRender3d(...)` |
| `world.getLoadedEntities()`, `world.entities` | `world.loadedEntityList` (a `List`) |
| `client.textRenderer` / `client.options` / `client.getCameraEntity()` | `client.fontRenderer` / `client.gameSettings` / `client.renderViewEntity` |
| `entityRenderDispatcher.method_6915(...)` | `entityRenderDispatcher.renderEntity(Entity, float)` |
| `GameOptions.fancyGraphics` field path | `GameSettings.fancyGraphics` |
| `RenderGlobal.reload()`, `setWorld()`, `scheduleTerrainUpdate()`, `renderLayer()`, `setupTerrain()`, `updateBlock()`, `updateChunks()`, `getChunksDebugString()` | **None of these exist** — 1.8.9 `RenderGlobal` has `loadRenderers()`, `setWorldAndLoadRenderers(WorldClient)`, `markBlockRangeForRenderUpdate(int,int,int,int,int,int)`, `renderBlockLayer(EnumWorldBlockLayer, double, int, Entity)`, `setupTerrain(Entity, double, ICamera, int, boolean)`, `renderEntities(Entity, ICamera, float)`, `getDebugInfoRenders()`. Fields: `mc`, `renderEngine`, `renderManager`, `theWorld`, `viewFrustum`, `renderDistanceChunks`, `countEntitiesTotal`, `countEntitiesRendered`, `damagedBlocks` (not `blockBreakingInfos`). |

---

## 6. Per-mixin audit

Config: `src/main/resources/radium-forge.mixins.json` (package `gg.sona.radium.mixin`, `required:true`, `minVersion:"0.8"` → **fix to 0.7**, `compatibilityLevel:JAVA_8`, `refmap:radium-forge.mixins.refmap.json`, `defaultRequire:1`).

Legend: 🟢 faithful · 🟡 stub/inert · 🔴 broken (compile and/or runtime)

| Mixin (file path) | `@Mixin` target | Status | Verdict / what to fix |
|---|---|---|---|
| `config/AbstractCaffeineConfigMixinPlugin` | n/a (IMixinConfigPlugin) | 🔴 | Calls missing `MixinConfig.load()`/`getEffectiveOptionForMixin()`. Never registered (no `"plugin"` key) → dead. Prefix check is `dev.vexor.radium.*`. Delete or fix. |
| `core/MixinBaseFrustum` | `ClippingHelper` | 🔴 | `@Shadow homogeneousCoordinates`→`frustum`; `@Shadow multiply`→`dot`. Logic itself is a faithful AABB-vs-frustum port. |
| `core/MixinBiome` | `BiomeGenBase` | 🔴 | `@Shadow BIOMES`→`biomeList`, `OCEAN`→`ocean`, `LOGGER`→`logger`; `@Overwrite getBiomeById`→`getBiomeFromBiomeList`. |
| `core/MixinBufferBuilder` | `BufferBuilder` | 🔴 | **Wrong on 5 counts** (verified against MCP-919): target `BufferBuilder`→`WorldRenderer`; `sortQuads`→`sortVertexData`; shadows `buffer`/`format`→`byteBuffer`/`vertexFormat`; `getVertexSizeInteger()`→`getIntegerSize()`. This is a self-contained, real port once retargeted — a great first fix. |
| `core/MixinCullingCameraView` | `ICamera` | 🔴 | `@Shadow BaseFrustum clipper` — `BaseFrustum` unresolved; `ICamera` (interface) has no fields in 1.8.9. Rework against `net.minecraft.client.renderer.culling.Frustum` (field `clippingHelper`). |
| `core/MixinFramebuffer` | `Framebuffer` | 🔴 | `@ModifyConstant(method="attachTexture")` — no such method; the 32856 literal is in `createFramebuffer`. |
| `sodium/core/MinecraftMixin` | `Minecraft` | 🔴 | `@Shadow profiler`→`mcProfiler`; `@Inject initializeGame`→`startGame`; `@Inject tick`→`runTick`; `@Overwrite setGlErrorMessage` doesn't exist; `InGameHud`→`GuiIngame` path; `var`→Java8. GL sync fence logic is a real, keepable port. |
| `sodium/core/access/ABlockRenderManager` | `BlockRendererDispatcherStub` | 🟡 | Accessor over a dead stub. Delete or retarget the real `BlockRendererDispatcher` (which has no fluid renderer field in 1.8.9). |
| `sodium/core/access/AChunk` | `Chunk` | 🟢 | `@Invoker invokeGetBlock` targets `Chunk.getBlock(int,int,int)` — exists in 1.8.9. Faithful, registered, applies. |
| `sodium/core/access/AFluidRenderer` | `FluidRendererStub` | 🟡 | Same dead-stub problem as ABlockRenderManager. 1.8.9 liquids render via `RenderGlobal`/`LiquidBlockRenderer`. |
| `sodium/core/access/AGameRenderer` | `EntityRenderer` | 🔴 | `Identifier` unresolved (→ `ResourceLocation`); invoker must match a real 1.8.9 `EntityRenderer` method. |
| `sodium/core/render/VertexFormatMixin` | `VertexFormat` | 🔴 | `@Inject` descriptor `Lnet/minecraft/client/render/VertexFormat;` → must be `Lnet/minecraft/client/renderer/vertex/VertexFormat;`. (Copy-ctor `VertexFormat(VertexFormat)` exists in 1.8.9.) |
| `sodium/core/render/world/LevelRendererMixin` | `RenderGlobal` | 🔴 | Massive Yarn leftovers: `RenderLayer`, `Camera`, `CameraView`, `BlockEntityRenderDispatcher`, `ClientWorld`, `LivingEntity`, `BlockPos.Mutable`, `textRenderer`, `getLoadedEntities`, `method_6915`, `hasVehicle`, `getCameraPosVec`, `entity.prevTickX/x/y/z`, `world.entities`, `entity.rider`, `gameRenderer`, `options`, `targetedEntity` — plus `@Redirect` to `net/minecraft/client/option/GameOptions;viewDistance:I`. Needs a full rewrite against 1.8.9 `RenderGlobal` (see §5.2 table). |
| `sodium/core/world/biome/ClientLevelMixin` | `WorldClient` | 🔴 | `@Inject <init>` handler params are Yarn (`ClientPlayNetworkHandler`, `LevelInfo`, `Difficulty`); 1.8.9 ctor is `WorldClient(NetHandlerPlayClient, WorldSettings, int, EnumDifficulty, Profiler)`. |
| `sodium/core/world/map/ClientChunkCacheMixin` | `ChunkProviderClient` | 🔴 | `(ClientWorld)` cast unresolved; `@Shadow world`→`worldObj`; `getOrGenerateChunk`→`loadChunk`; `Chunk.unloadFromWorld()`→`onChunkUnload()`; `setChunkLoaded(Z)`→`onChunkLoad()`. |
| `sodium/core/world/map/ClientLevelMixin` | `WorldClient` | 🔴 | `extends World` super-call uses Yarn `LevelProperties`/`Dimension` → `WorldInfo`/`WorldProvider`. |
| `sodium/features/gui/hooks/console/GameRendererMixin` | `EntityRenderer` | 🔴 | `@Shadow client`→`mc`; `@Inject method="render"`→`updateCameraAndRender`; INVOKE targets `Screen`/`InGameHud`→`GuiScreen`/`GuiIngame`; `client.profiler`→`mcProfiler`; `client.options.debugEnabled`→`gameSettings.showDebugInfo`. |
| `sodium/features/gui/hooks/debug/DebugScreenOverlayMixin` | `GuiOverlayDebug` | 🔴 | `@Redirect getRightText`→`renderDebugInfoRight`; `Formatting`→`EnumChatFormatting`; `String.formatted`→`String.format`; `var`→Java8. |
| `sodium/features/gui/hooks/settings/OptionsScreenMixin` | `GuiVideoSettings` | 🟡 | Target + `actionPerformed(GuiButton)` are valid 1.8.9; body is an **intentional no-op** until the config UI is ported. |
| `sodium/features/options/GameOptionsMixin` | `GameSettings` | 🔴 | `GameOptions`→`GameSettings`; `GameOptions.Option.RENDER_DISTANCE.setMaxValue`→`GameSettings.Options.RENDER_DISTANCE.setValueMax`; `@Shadow viewDistance`→`renderDistanceChunks`; `@Overwrite getCloudMode()` doesn't exist. |
| `sodium/features/options/MinecraftClientMixin` | `Minecraft` | 🟡 | `@Overwrite isAmbientOcclusionEnabled()` **exists** in 1.8.9 (verified); body is a placeholder. Compiles & applies. |
| `sodium/features/options/overlays/GuiMixin` | `GuiIngame` | 🔴 | `@Redirect method="render"`→`renderGameOverlay`; the `Minecraft.isFancyGraphicsEnabled()Z` target is reachable only after fixing the host method name. |
| `sodium/features/options/render_layers/LeavesBlockMixin` | `BlockLeaves` | 🔴 | `@ModifyVariable method="setGraphics"`→`setGraphicsLevel(boolean)`. |
| `sodium/features/options/weather/LevelRendererMixin` | `EntityRenderer` | 🔴 | `renderWeather`→`renderRainSnow`; `renderClouds`→`renderCloudsCheck`; `GameOptions`→`GameSettings`; `GameOptions.getCloudMode()`→`gameSettings.shouldRenderClouds()`. |
| `sodium/features/options/world/DimensionMixin` | `WorldProvider` | 🟢 | Faithful (`@Overwrite getCloudHeight()` exists, returns 128.0F) — **but NOT registered in the config → never loads.** |
| `sodium/features/render/immediate/DirectionMixin` | `EnumFacing` | 🔴 | `EnumFacing.getFacing(float,float,float)` **does exist** in 1.8.9 (task-confirmed) and the body is faithful — but leftover `net.fabricmc.api.*` imports and `var` break compilation. |
| `sodium/features/textures/tracking/SpriteMixin` | `TextureAtlasSprite` | 🟢 | Faithful; adds `@Unique active` + implements `SpriteExtension`. Registered, applies. |

**Summary: 20 🔴 · 4 🟡 · 3 🟢** (one of the 🟢, DimensionMixin, is unregistered).

---

## 7. Ported `net/` classes (per-file status)

Reference: 391 files under `Radium-Reference/common/src/main/java/net/caffeinemc/mods/sodium/`. Port: **23 present** (~6%), plus 2 stub classes under `net.minecraft.client.renderer`.

**Faithful (4):**
- `api/vertex/format/VertexFormatExtensions.java` — identical to reference (interface `sodium$getGlobalId()`, implemented by `VertexFormatMixin`).
- `client/render/texture/SpriteExtension.java` — identical (implemented by `SpriteMixin`).
- `client/util/frustum/ExtendedFrustum.java` — identical (real implementations in `MixinBaseFrustum`, `MixinCullingCameraView`).
- `client/world/LevelRendererExtension.java` — identical (implemented by `LevelRendererMixin`).

**Modified (2):**
- `client/render/chunk/ChunkRenderMatrices.java` — reference is `record (Matrix4fc, Matrix4fc)`; port is a mutable class. Fine.
- `client/world/BiomeSeedProvider.java` — interface same; static helper dropped. Fine.

**Stub (17)** — all carry `Stub:` markers or empty bodies:
- `client/SodiumClientMod.java` — **also a compile blocker** (missing `onInitialization`), hardcodes version `0.8.15-stub`.
- `client/config/ConfigManager.java` — `registerConfigsLate()` no-op.
- `client/data/config/MixinConfig.java` — **also a compile blocker** (empty class).
- `client/gl/device/RenderDevice.java` — `enterManagedCode()/exitManagedCode()` no-ops. Reference is an interface with `INSTANCE=GLRenderDevice`, `createCommandList()`, capabilities.
- `client/gui/console/ConsoleHooks.java` / `client/gui/console/FPSCounter.java` — render no-ops.
- `client/gui/SodiumOptions.java` — **the one stub with real behavior**: `Quality` (enableClouds, enableVignette, cloudHeight, leaves/weather/cloud `.isFancy`) + `Advanced` (cpuRenderAhead, cpuRenderAheadLimit). Missing persistence and most options (no `fpsOverlay`, no `smoothLighting`, no `betterSkies`…).
- `client/gui/VideoSettingsScreen.java` — empty `GuiScreen`; nothing opens it (OptionsScreenMixin is a no-op). Dead code.
- `client/render/chunk/map/ChunkStatus.java` — only `FLAG_ALL = 0xFFFF` (reference: 1/2/3).
- `client/render/chunk/map/ChunkTrackerHolder.java` / `ChunkTracker.java` — no-op tracker.
- `client/render/SodiumWorldRenderer.java` — **no-op; `instance()` throws.** LevelRendererMixin forwards to it.
- `client/render/viewport/frustum/SimpleFrustum.java` / `client/render/viewport/Viewport.java` — wrappers/data holders.
- `client/util/MathUtil.java` — only `toMib` (reference has the full math util).
- `client/util/NativeBuffer.java` — only `getTotalAllocated()` → 0L.
- `api/vertex/format/VertexFormatRegistry.java` — `allocateGlobalId()` returns 0.

**+ 2 stub classes (bad idea, see §4.4):**
- `net/minecraft/client/renderer/FluidRendererStub.java` (fields `waterSprites`/`lavaSprites`)
- `net/minecraft/client/renderer/BlockRendererDispatcherStub.java` (field `fluidRenderer`)

---

## 8. What is missing

**~368 of the 391 reference classes** are not ported. The mixins import (at class level) only the 23 present files — the real gaps are:

- **The entire chunk renderer**: `client/render/chunk/RenderSectionManager`, `RenderSection`, `region/*`, `compile/**` (~100 classes: meshing, light pipelines, occlusion, tasks, estimators), `lists/**`, `translucent_sorting/**`, `tree/**`, `vertex/format/impl/*`.
- **The GL layer**: `client/gl/device/{CommandList,GLRenderDevice,DrawCommandList}`, `client/gl/arena/**`, `client/gl/buffer/**`, `client/gl/shader/**`, `client/gl/tessellation/**`, `client/gl/state/GlStateTracker`, `client/gl/functions/*`, `client/gl/sync/GlFence`.
- **The console**: `client/console/{Console,ConsoleSink,message/*}` + `client/gui/console/ConsoleRenderer`.
- **The config framework**: `client/config/structure/**`, `client/config/builder/**`, `client/config/value/**`, `client/config/search/**`, `api/config/structure/ConfigBuilder`, `api/config/ConfigEntryPoint`, `client/gui/SodiumConfigBuilder`, `client/gui/screen/*`, `client/gui/widgets/**`, `client/gui/options/**`.
- **Services**: `client/services/{Services,PlatformRuntimeInformation,PlatformMixinOverrides}`.
- **World/biome**: `client/world/biome/*`, `client/world/cloned/*`, `client/world/LevelSlice`, `client/render/viewport/CameraTransform`, `client/render/chunk/map/ClientChunkEventListener`.
- **Util**: `client/util/interval_tree/**`, `client/util/collections/**`, `client/util/sorting/**`, `client/util/iterator/**`, `client/util/color/BoxBlur`, `FileUtil`, `Dim2i`, `DirectionUtil`, `UInt32`, `BitwiseMath`, `ListUtil`, `ModelQuadUtil`, `ScissorUtil`, `CameraUtils`, `BlockRenderType`.
- **Data/fingerprint**: `client/data/fingerprint/*`, `client/data/config/MixinConfig` (real version).
- **The `dev/vexor/radium/compat/mojang/**` shims** (`FluidSprites`, `Mth`, `SectionPos`, `QuartPos`, `FogHelper`, `LightTexture`, …) — not copied into the port at all.

Also absent from the port: the reference `fabric/` source set's equivalents — there is **no Forge implementation** of `PlatformRuntimeInformation`, `PlatformMixinOverrides`, or `CaffeineConfigPlatform`, and **no `META-INF/services`** entries.

---

## 9. Mixin config inventory

Current `radium-forge.mixins.json` (`client` + `mixins` lists):

```
core.MixinBaseFrustum, core.MixinBufferBuilder, core.MixinCullingCameraView,
core.MixinFramebuffer, sodium.core.MinecraftMixin, sodium.core.access.ABlockRenderManager,
sodium.core.access.AChunk, sodium.core.access.AFluidRenderer, sodium.core.access.AGameRenderer,
sodium.core.render.VertexFormatMixin, sodium.core.render.world.LevelRendererMixin,
sodium.core.world.biome.ClientLevelMixin, sodium.core.world.map.ClientChunkCacheMixin,
sodium.core.world.map.ClientLevelMixin, sodium.features.gui.hooks.console.GameRendererMixin,
sodium.features.gui.hooks.debug.DebugScreenOverlayMixin,
sodium.features.gui.hooks.settings.OptionsScreenMixin, sodium.features.options.GameOptionsMixin,
sodium.features.options.MinecraftClientMixin, sodium.features.options.overlays.GuiMixin,
sodium.features.options.render_layers.LeavesBlockMixin,
sodium.features.options.weather.LevelRendererMixin, sodium.features.render.immediate.DirectionMixin,
sodium.features.textures.tracking.SpriteMixin
mixins: [core.MixinBiome]
```

Every listed class **exists** in `src/main/java` (including `sodium.core.access.AChunk`). The one mixin file **not** listed: `sodium.features.options.world.DimensionMixin`.

---

## 10. Reference bootstrap (Fabric) — the seams a Forge port must replace

The Forge port must provide equivalents of what the reference's `fabric/` source set does:

| Fabric (reference) | Forge 1.8.9 equivalent needed |
|---|---|
| `fabric.mod.json` entrypoints (`SodiumFabricMod.onInitializeClient`, `SodiumPreLaunch`) | `RadiumForgeMod` `@Mod` + `FMLInitializationEvent` (exists, currently broken) |
| Mixin via Fabric Loader (bundled Mixin 0.8.7) | Mixin shaded in-jar + `MixinLoader` FMLCorePlugin/TweakClass (exists, pattern correct) |
| `accessWidener` (Fabric-only) | `@Accessor`/`@Invoker` mixins or `*_at.cfg` ATs (neither exists yet) |
| `Services` ServiceLoader (`PlatformRuntimeInformation`, `PlatformMixinOverrides`, `CaffeineConfigPlatform`) | Forge impls + `META-INF/services`, or direct wiring |
| `ConfigLoaderFabric.collectConfigEntryPoints()` | Forge config registration in `ConfigManager` (currently a stub) |
| Ploceus build-time Yarn→1.8.9 remap | Manual Yarn→MCP rename (the actual work) |

---

## 11. Recommended next steps (ordered)

1. **Make it compile.**
   - Fix FML imports in `MixinLoader.java:3` and `RadiumForgeMod.java:3-4` → `net.minecraftforge.fml.*` (see §4.1).
   - Add `SodiumClientMod.onInitialization(String)` or fix the call in `RadiumForgeMod.java:15`.
   - Add `MixinConfig.load(File)`/`getEffectiveOptionForMixin` (port from reference) or delete the option-gating plugin entirely.
   - Strip `net.fabricmc.*` imports (DirectionMixin), replace `var`→typed, `String.formatted`→`String.format`.
2. **Fix the fatal runtime bug:** `"minVersion": "0.8"` → `"0.7"` in `radium-forge.mixins.json`.
3. **Build with Java 8:** `JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 ./gradlew build` (first run decompiles 1.8.9; be patient).
4. **Verify the refmap** `radium-forge.mixins.refmap.json` lands inside the produced jar with MCP→SRG entries keyed by stable_22 names. If missing, the Mixin AP didn't run (on Gradle 3.1 it's on the compile classpath, so it should). Consider adding `mixin { defaultObfuscationEnv = searge }` for determinism.
5. **Shrink the mixin config** to only currently-applying mixins (`core.MixinBiome`, `SpriteMixin`, `AChunk`, `DimensionMixin`) so `required:true` doesn't brick startup while you work.
6. **Fix mixins one at a time** using the §5 mapping table. Start with `MixinBufferBuilder` (self-contained, big win), then the core ones (`MixinBaseFrustum`, `MixinFramebuffer`, `MinecraftMixin`, `VertexFormatMixin`), then the option/feature mixins. Register `DimensionMixin`.
7. **Decide the big strategy for the 368 missing classes** (this is the real project):
   - **(a) Hand-rename** all modern-Yarn classes to MCP 1.8.9 (mechanical but error-prone, and you must handle the API drift between modern Sodium and 1.8.9's rendering, which is large — e.g. 1.8.9 has no `RenderLayer`/BakedQuad model system).
   - **(b) Build a mapping-translation pipeline** for Forge (the analogue of Ploceus: translate Yarn→MCP/SRG so the modern source compiles against 1.8.9). This is what the reference actually does and is far less work per-class, but requires build tooling.
   - Note that even Radium's author shipped the Fabric 1.8.9 version by keeping modern source + Ploceus remap; the *1.8.9-specific* logic lives in the 26 mixins + the compat shims. Keep the mixins and shims as the only hand-written adaptation layer.
8. **Port the Forge service seams** (`PlatformRuntimeInformation` for game dir/config dir/dev env, `PlatformMixinOverrides`, `CaffeineConfigPlatform`) or drop them.
9. **Remove dead/inert scaffolding:** delete `FluidRendererStub`/`BlockRendererDispatcherStub` (and the two accessor mixins, or retarget them), delete/convert `sodium-common.accesswidener`, and either wire or delete `AbstractCaffeineConfigMixinPlugin` + fix `CaffeineConfig`'s ServiceLoader crash.

---

## 12. Sources

### Repo / references
- [github.com/SonaGG/Radium](https://github.com/SonaGG/Radium) — upstream, archived Jul 27 2026, branch `dev`
- [modrinth.com/mod/radium-mod](https://modrinth.com/mod/radium-mod) — distributed 1.8.9 Fabric/Legacy-Fabric/Ornithe
- [github.com/Marcelektro/MCP-919](https://github.com/Marcelektro/MCP-919) — **decompiled MCP 1.8.9 sources (authoritative for names)** — e.g. `net/minecraft/client/renderer/WorldRenderer.java`, `RenderGlobal.java`, `BiomeGenBase.java`, `ClippingHelper.java`
- [github.com/Aizistral-Studios/MCP-Archive](https://github.com/Aizistral-Studios/MCP-Archive) — `versions.json`: 1.8.8→stable_20, **1.8.9→stable_22**
- [mcp.zeith.org/mcp_stable/22-1.8.9/](https://mcp.zeith.org/mcp_stable/22-1.8.9/) — MCPBot stable_22 CSV export

### Forge 1.8.9 + Mixin
- [github.com/manuthebyte/template-forge-mixin-1.8.9](https://github.com/manuthebyte/template-forge-mixin-1.8.9) — **the canonical working 1.8.9 template this project copies** (Gradle 3.1, Mixin 0.7.10-SNAPSHOT, `minVersion:"0.7"`, `net.minecraftforge.fml.relauncher.IFMLLoadingPlugin`)
- [github.com/MinecraftForge/MinecraftForge/tree/1.8.9](https://github.com/MinecraftForge/MinecraftForge/tree/1.8.9/src/main/java) — `net/minecraftforge/fml/relauncher/IFMLLoadingPlugin.java`, `CoreModManager.java` (no `cpw` package)
- [github.com/SpongePowered/Mixin/blob/0.7_dev](https://github.com/SpongePowered/Mixin/blob/0.7_dev/src/main/java/org/spongepowered/asm/mixin/transformer/MixinConfig.java) — `checkVersion()` throws `MixinInitialisationError` when `minVersion > runtime` and `required=true`
- [wiki: Mixins on Minecraft Forge](https://github.com/SpongePowered/Mixin/wiki/Mixins-on-Minecraft-Forge) — shading required on legacy Forge
- [github.com/SpongePowered/MixinGradle](https://github.com/SpongePowered/MixinGradle) — 0.6 ↔ ForgeGradle 2.x; refmap+reobf integration
- [github.com/SpongePowered/Mixin/issues/469](https://github.com/SpongePowered/Mixin/issues/469) — 0.7.11 vs 0.8 cannot coexist
- [CurseForge MixinBooter](https://www.curseforge.com/minecraft/mc-mods/mixin-booter) — the way to run Mixin 0.8.x on 1.8–1.12.2
- [github.com/Debuggingss/ExampleMod](https://github.com/Debuggingss/ExampleMod) — working 1.8.9 mod (Mixin 0.7.11-SNAPSHOT, TweakClass + MixinConfigs, no FMLCorePlugin)
- [moddev.nea.moe/mixins/](https://moddev.nea.moe/mixins/) — nea89 Legacy Modding Wiki: 1.8.9 → `minVersion "0.7"`, `compatibilityLevel JAVA_8`
- [Hypixel thread: Forge 1.8.9 Mixins](https://hypixel.net/threads/forge-1-8-9-mixins.5051317/)

### Fabric 1.8.9 / Ploceus
- [wiki.ornithemc.net/wiki/Ploceus](https://wiki.ornithemc.net/wiki/Ploceus) — Ornithe Loom extension; `setIntermediaryGeneration(2)` remaps modern source down to 1.8.9
- [legacy-yarn on legacyfabric.net](https://meta.legacyfabric.net/v2/versions/yarn/1.8.9) — `1.8.9+build.4:v2` mappings
- [github.com/FabricMC/fabric-loader](https://github.com/FabricMC/fabric-loader) — 0.16/0.18 bundle sponge-mixin 0.8.7

### Toolchain
- [github.com/MinecraftForge/ForgeGradle (FG_2.1)](https://github.com/MinecraftForge/ForgeGradle/tree/FG_2.1) — Java 6 bytecode, wrapper Gradle 2.14; ceiling ~Gradle 4.x
- [mvnrepository: mcp_stable 22-1.8.9](https://mvnrepository.com/artifact/de.oceanlabs.mcp/mcp_stable/22-1.8.9)
- [MCP 1.8.9 mappings discussion](https://forums.minecraftforge.net/topic/36604-whats-the-right-mappings-version-for-mc-189/)

---

*Generated by a multi-agent assessment (3 online-research agents + 3 codebase-review agents), cross-checked against decompiled MCP 1.8.9 sources and the canonical Forge 1.8.9 Mixin template. Key findings also stored in Claude project memory (`memory/radium-port-strategy.md`).*
