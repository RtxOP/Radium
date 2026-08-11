# SodiumForge — Living Project Documentation

**Status: living document.** Append new findings as they emerge (see §9 for the append log).

---

## 1. What this project is

`SodiumForge` ports **Radium** (SonaGG's fork of Sodium) to **Minecraft 1.8.9 on Forge**
(`1.8.9-11.15.1.2318-1.8.9`), from its Fabric/Ornithe home. The upstream reference source
is vendored locally in [`Radium-Reference/`](./Radium-Reference) (a clone of
`https://github.com/SonaGG/Radium`, commit `71c47e96`).

- Goal 1: **compile** on Java 8.
- Goal 2: **boot** a baseline client to the main menu with a minimal, verified mixin set.
- Goal 3 (later): port the real renderer — this is where the LWJGL3 problem (§6) bites.

### Status board

| Milestone | State |
|---|---|
| M1 structural blockers (FML imports, renamed classes, dead scaffolding) | ✅ done |
| M1 compile: Java 8 + MCP names | ✅ done — `./gradlew build` **green** |
| M2 bootable baseline (shrunk mixin config → main menu) | ✅ done — `[Radium] Injecting with IFMLLoadingPlugin.` + main menu + mod list (2026-08-08) |
| M3 real renderer — Phase A: GL device layer (`client/gl/**`) → LWJGL2 | ✅ done — 55 files, native LWJGL2 rewrite (no `org.lwjgl.system` shim), `javac` + `gradlew build` green; deps fixed after convention audit: joml shaded, `@MCVersion`, `clientSideOnly` (2026-08-08) |
| M3 real renderer — Phases B–F (vertex encoders, chunk meshing/data, culling, world clones, integration, config) | ✅ **COMPLETE 2026-08-10** — full chunk pipeline renders real worlds (C6a–C6d: world slice → meshing → RenderSectionManager → RenderGlobal wiring, user-validated boot) |

### §1.1 Quickstart — READ THIS FIRST (new-agent handoff)

Do not re-derive what's below; it cost hours to learn.

**Build** (on the **Codespace**, `/workspaces/SodiumForge`):

```
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 ./gradlew build
```

- The local machine `/home/e/dev/SodiumForge` **cannot build** — ~1 GiB free RAM, empty Gradle
  module cache, and `gradle.properties` forks a 4 GiB Fernflower decompile. **Edit + audit locally,
  build + boot on the Codespace.**
- Green build = `BUILD SUCCESSFUL` after `:compileJava` / `:reobfShadowJar`. The Mixin AP's
  `Writing refmap to …/compileJava-refmap.json` lines are harmless noise.

**Boot test (M2 — DONE 2026-08-08):**

```
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 ./gradlew runClient
```

- Success = the console prints `[Radium] Injecting with IFMLLoadingPlugin.` (proves the
  `FMLCorePlugin` ran and MixinBootstrap initialized) **and** the client reaches the **main menu**
  with no mixin-apply failure.
- Headless machine (e.g. a Codespace without a desktop): wrap in a virtual framebuffer —
  `JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 xvfb-run -a ./gradlew runClient` (install `xvfb`
  first). The game still needs a GL context; mesa/llvmpipe provides one.
- Failure signatures (console or `run/logs/latest.log`):
  - `Mixin apply for mixin … failed` + a `ReferenceResolutionException` → a bootable-set mixin
    targets a member that doesn't exist under `stable_22`.
  - `Invalid mixin` / `was not found in the target class` → wrong `@Mixin` class name.
  - `Critical injection failure` → wrong `@Inject`/`@Redirect` host or target.
  - `required mixins were not applied` → a config entry failed to load at all.
- Rule: the config has `"required": true`, so **any** bootable-set mistake hard-crashes launch.
  Before adding a mixin back into the config, verify every `@Shadow`/`@Invoker`/`@Overwrite`/target
  against MCP-919 (below). Non-bootable mixins may be wrong (warning-only) — they aren't loaded.
- **Jar hygiene:** the installed jar is only trustworthy when built from HEAD. Rebuild on
  the Codespace and reinstall: `JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 ./gradlew build`
  → copy BOTH `build/libs/radium-0.8.15.jar` **and**
  `build/libs/mixin-0.7.11-launchwrapper-bridge.jar` into the mods folder.
  **Both jars are required** (since 2026-08-08 late): FML 1.8.9 puts the mixin classes on the
  system classloader via the TweakClass cascade, and the cascade must live in a SEPARATE jar
  because a TweakClass-bearing jar is permanently invisible to @Mod scanning (see §7).
  Expected after the fix: jar ≈ 2 MB (mod) + ~0.9 MB (bridge), `identified 4 mods`,
  `FMLFileResource:Radium`, `[mcp, FML, Forge, radium]` handshake, and no mixin errors.

**Ground truth for any 1.8.9 name** (never trust WebFetch summaries of decompiled files — they
contradicted themselves):

```
curl https://raw.githubusercontent.com/Marcelektro/MCP-919/master/src/minecraft/net/minecraft/client/Minecraft.java | grep -nE "isAmbientOcclusionEnabled|getRenderViewEntity"
```

All `net.minecraft.*` imports currently in the tree are batch-verified against MCP-919 (43/43).

**Key files** (all under `src/main/`):

| Path | Purpose |
|---|---|
| `java/com/github/sonagg/radium/MixinLoader.java` | Forge coremod (`IFMLLoadingPlugin`) that bootstraps Mixin 0.7 + registers the config; wired via manifest `FMLCorePlugin`. |
| `java/com/github/sonagg/radium/RadiumForgeMod.java` | `@Mod(modid="radium")`; calls `SodiumClientMod.onInitialization(VERSION)` on FML init. |
| `resources/radium-forge.mixins.json` | **The boot gate.** Only the bootable set is listed (`minVersion 0.7`, `JAVA_8`, `required: true`). |
| `java/gg/sona/radium/mixin/**` | All 24 mixins (inventory in §3). |
| `java/net/caffeinemc/mods/sodium/client/SodiumClientMod.java` | Stub: `options()`, `onInitialization(String)`, `getVersion()`. |
| `java/net/caffeinemc/mods/sodium/client/gui/SodiumOptions.java` | Stub options tree the mixins read (`quality.*`, `advanced.*`). |
| `java/net/caffeinemc/mods/sodium/client/render/SodiumWorldRenderer.java` | No-op renderer stub for M2; the real-port target for M3. |

**Git:** working tree clean on `main`; `DOCS.md` is tracked and committed (separate doc
commits preferred). Error: a jar built from anything but HEAD (/uncommitted tree) is
non-reproducible — see the Jar hygiene bullet above.

**Milestone checklist / definitions of done:**
- M1 ✅ **compile** — `BUILD SUCCESSFUL` on Java 8.
- M2 ⏳ **boot baseline** — `runClient` reaches the main menu, no mixin-apply failure. **Active task.**
- M3 ⛔ **real renderer** — port `common/src/desktop` + `GLRenderDevice`; LWJGL decision per §6.4
  (recommended: Option 1 `org.lwjgl` compat shim).

---

## 2. Toolchain & build environment

- **Gradle**: wrapper 3.1 (Shadow 2.0.4 rejects Gradle <3.0).
- **Plugins**: `net.minecraftforge.gradle:forge:2.1-SNAPSHOT`, `org.spongepowered:mixin:0.6-SNAPSHOT`
  (MixinGradle → emits the refmap), `com.github.jengelman.gradle.plugins:shadow:2.0.4`.
- **Mappings**: `stable_22`. MCP `stable_20` was generated against **1.8.8** — using it on
  1.8.9 produces member-name drift that surfaces as mixins referencing non-existent members at
  runtime. `stable_22` matches 1.8.9 (commit `feddbfb`).
- **Java 8 required** — ForgeGradle 2.x + Forge 1.8.9 do not run on newer JDKs:
  `JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 ./gradlew build`.
- **Runtime deps**:
  - `org.spongepowered:mixin:0.7.11-SNAPSHOT` — **shaded** (Forge 1.8.9 bundles no Mixin).
    Config must use `"minVersion": "0.7"`, `"compatibilityLevel": "JAVA_8"`.
  - `it.unimi.dsi:fastutil:8.5.13` — **removed 2026-08-08** (was shaded; not on the 1.8.9
    classpath). The 4 files that used it now use JDK collections (`ArrayDeque`,
    `LinkedHashMap`, `IdentityHashMap`). **Re-add the shade when porting the chunk-renderer
    phase** — upstream uses fastutil in ~40 files (meshing, translucent BSP sorts,
    config/gui); the Fabric build gets it free from its loader, Forge 1.8.9 does not.
  - `org.joml:joml:1.10.8` — **shaded** (real runtime dep: `ChunkRenderMatrices`
    `Matrix4f` fields, `GlUniformMatrix4f` `Matrix4fc` signatures — mirroring upstream Radium's
    Fabric build, which shades joml too). Without shading, the first shader/chunk
    code path dies with `NoClassDefFoundError`. Boot-safe today only because `MixinBaseFrustum`'s
    `FrustumIntersection.OUTSIDE/INTERSECT/INSIDE` are javac-inlined int constants (verified in
    the class-file constant pool — no `org/joml` refs). **Gotcha:** joml 1.10.8 ships
    `module-info.class` at class-file major 53 (Java 9) — `shadowJar` excludes it, else
    `:reobfShadowJar` dies in SpecialSource's ASM 5.x `ClassReader` (bare
    `java.lang.IllegalArgumentException`, no message). joml's regular classes are major 46/52
    (Java ≤8 — verified on the actual Maven artifact).
  - jna, slf4j-api, jetbrains-annotations, mixinextras — **compile-only**. jna/slf4j are
    declared-but-unimported (parity with upstream, which never imports them either);
    annotations are CLASS-retention (no runtime effect).
  - **LWJGL 2.9.4-nightly-20150209 substitution** (all platforms): the stock
    `2.9.2-nightly-201408222` that ForgeGradle 2.1 defaults to for MC 1.8.9 is the **1.8.8-era**
    artifact. Mojang's canonical 1.8.9 `version.json`
    (`piston-meta.mojang.com/v1/packages/d546f1…/1.8.9.json`) pins
    `org.lwjgl.lwjgl:2.9.4-nightly-20150209` on Linux/Windows and `2.9.2-nightly-20140822` on
    macOS. The build substitutes the FG default → `2.9.4-nightly-20150209` and forces
    `lwjgl-platform:2.9.4-nightly-20150209`, mirroring the canonical template's **macOS** branch
    (which is the only upstream-proven one). **Do NOT use the template's Linux branch's
    `2.9.4-babric.1`** — that artifact exists in no public repository (verified 404/000 against
    forge.net, Maven Central, sponge, legacyfabric, babric.dev); babric as a whole is offline.
    `2.9.4-nightly-20150209` resolves from `libraries.minecraft.net`, which ForgeGradle already has
    on its repo list. **Natives verified GLIBC-2.2.5→2.4** (`objdump -T liblwjgl64.so`) — the
    "2.9.4 natives break on modern glibc" worry was about the *older* 2.9.2-nightly line, not this
    one; boot on a modern Linux should be fine. **Gotcha:** the template's Linux branch also uses
    `dependencySubstitution…using(…)`, which does **not exist on Gradle 3.1** — both projects pin
    Gradle 3.1. Use `with(…)`.
- **Manifest** (`jar { manifest.attributes(...) }`): `FMLCorePlugin=com.github.sonagg.radium.MixinLoader`,
  `FMLCorePluginContainsFMLMod=true`, `MixinConfigs=radium-forge.mixins.json`, `ModSide=CLIENT`
  (+ `TweakClass`/`TweakOrder` for the cascaded MixinTweaker). The loader class also carries
  `@IFMLLoadingPlugin.Name` + `@MCVersion("1.8.9")` (see §7). `ForceLoadAsMod` is NOT used —
  it is a no-op on 1.8.9.
- **Mixin config** `src/main/resources/radium-forge.mixins.json`:
  `"required": true` → any wrong target hard-crashes at launch; the bootable set must stay shrinkable.

### Two-machine split (important)

- **`/workspaces/SodiumForge` (Codespace)** — has the decompiled MC + warm Gradle cache; this is
  where builds actually run. Java8 via `/usr/lib/jvm/temurin-8-jdk-amd64`.
- **`/home/e/dev/SodiumForge` (local)** — editing + static verification only. Only ~1 GiB free RAM
  and an empty Gradle module cache, so ForgeGradle's 4 GiB Fernflower decompile OOMs locally.
  → Local: edit + audit (curl MCP-919). Remote: build + boot.

### Ground-truth verification method

`WebFetch` summarization of 1000–3000-line decompiled MC files gave contradictory answers. The
reliable path is direct `curl` against the **MCP-919** re-export of 1.8.9 (MCP stable mappings
source):

```
curl https://raw.githubusercontent.com/Marcelektro/MCP-919/master/src/minecraft/net/minecraft/client/Minecraft.java | grep -nE "isAmbientOcclusionEnabled|getRenderViewEntity|..."
```

Always verify a mixin target/import against MCP-919 before trusting it.

---

## 3. Port architecture decisions (log)

- **Bulk port strategy**: standard Forge hand-port to MCP stable_22 (NOT Ploceus build-time remap).
- **Option gating**: deleted the upstream config machinery
  (`AbstractCaffeineConfigMixinPlugin`, `CaffeineConfig*`, `MixinOption`, the access-widener,
  `MixinConfig`) rather than porting it. Config values live in a stub `SodiumOptions`.
- **FML package**: 1.8.9 is `net.minecraftforge.fml.*`, not `cpw.mods.fml.*`.
- **Stub renderer**: `SodiumWorldRenderer` is a no-op shell satisfying the API surface the mixins
  expect. No chunk meshing until M3.
- **Frustum bridge**: vanilla 1.8.9 `Frustum`/`ClippingHelper` implement the Sodium
  `ExtendedFrustum` contract (`radium$intersect` / `radium$getPlanes`) via two mixins
  (`core.MixinBaseFrustum` on `ClippingHelper`, `core.MixinCullingCameraView` on `Frustum`).

### The bootable mixin set (M2)

`"client"`: `core.MixinBaseFrustum`, `core.MixinCullingCameraView`, `sodium.core.access.AChunk`,
`sodium.features.options.world.DimensionMixin`, `sodium.features.textures.tracking.SpriteMixin`.
`"mixins"`: `core.MixinBiome`.

All their `@Shadow`/`@Invoker`/`@Overwrite` targets are MCP-919-verified:
`ClippingHelper.frustum`/`dot`, `Frustum.clippingHelper`, `Chunk.getBlock(int,int,int)`,
`WorldProvider.getCloudHeight`, `BiomeGenBase.getBiomeFromBiomeList`/`biomeList`/`ocean`/`logger`.

### Full mixin inventory (24 files under `gg/sona/radium/mixin/`)

All compile. Everything outside the bootable set is unloaded at boot (config lists only the set
above), so an occasional wrong non-bootable target is a warning, not a crash.

| Area | Mixins |
|---|---|
| core | `MixinBaseFrustum`, `MixinCullingCameraView`, `MixinBiome`, `MixinBufferBuilder` (WorldRenderer `sortVertexData`), `MixinFramebuffer` (GL_RGBA8→GL_RGBA16) |
| sodium.core | `MinecraftMixin` (startGame/runtick + GPU fences), `VertexFormatMixin`, `LevelRendererMixin` (RenderGlobal overwrites), `AGameRenderer`, `AChunk`, biome/map `ClientLevelMixin` ×2, `ClientChunkCacheMixin` |
| features | `GameOptionsMixin`, `MinecraftClientMixin`, `DirectionMixin`, `GuiMixin`, `LeavesBlockMixin`, weather `LevelRendererMixin`, `DimensionMixin`, `SpriteMixin`, console `GameRendererMixin`, `DebugScreenOverlayMixin`, `OptionsScreenMixin` |

---

## 4. Compile pitfalls hit (so they don't recur)

- **LWJGL 2 vs 3 fence types**: 1.8.9's LWJGL 2.9.4 models GL sync as `org.lwjgl.opengl.GLSync`
  objects; modern Sodium/LWJGL3 use `long` handles. `MinecraftMixin` stores fences in
  `ArrayDeque<GLSync>` (fastutil `LongArrayFIFOQueue` is the LWJGL3 version). Gated behind
  `advanced.cpuRenderAhead` (stub default `false`).
- **Wrong packages (1.8.9 MCP)**:
  - `DestroyBlockProgress` → `net.minecraft.client.renderer` (NOT `net.minecraft.util`).
  - `NetHandlerPlayClient` → `net.minecraft.client.network` (NOT `net.minecraft.client.multiplayer`).
- **Mixin AP semantics**: `@Overwrite` of a missing method = hard error; `@Mixin` of an unresolvable
  class = error; `@Invoker` wrong method = error; wrong `@Inject`/`@Redirect` host = warning.
  Shadow visibility may differ from the target member.
- **MCP `stable_20` ≠ `stable_22`** (see §2).
- All `net.minecraft.*` imports currently in the tree are batch-verified against MCP-919 (43/43).

---

## 5. Reference sources

- **`Radium-Reference/`** — vendored upstream Radium clone. Layout:
  - `common/src/main` — renderer core (`net/caffeinemc/mods/sodium/client/...`).
  - `common/src/api` — modloader-neutral API interfaces.
  - `common/src/desktop` — `net/caffeinemc/mods/sodium/desktop/...`, the LWJGL-backed implementation
    (the part that needs the LWJGL3 layer).
  - `fabric/` — Fabric/Ornithe entrypoint + Gradle wiring.
  - `thirdparty/` — NOTICE + licenses: **LGPL-2.1, LGPL-3.0, MPL2, Apache-2.0, MIT** — attribution
    obligations apply to any code copied into this port.
- **MCP-919** (`Marcelektro/MCP-919`) — decompiled 1.8.9 with MCP stable mappings; the naming
  ground truth for `stable_22`.
- **`forge-1.8.9-MDK/`** — stock Forge MDK folder inside the repo; `stable_20` is its default and is
  **wrong for 1.8.9** (use `stable_22`).
- **`REPORT.md`** — earlier planning report; §4.3/§11.5 describe the bootable set rationale.

---

## 6. THE LWJGL PROBLEM — dependency on `legacy-lwjgl3`

### 6.1 Why it's needed

MC 1.8.9 ships **LWJGL 2.9.4**. The Sodium/Radium renderer (the code we must port in M3) is written
against **LWJGL 3** APIs. Grep of `Radium-Reference/common` shows the LWJGL3 surface actually used:

| API | uses | notes |
|---|---|---|
| `org.lwjgl.system.MemoryUtil` | 16 | `memPutInt`, `memGetInt`, `memPutFloat`, `memAddress`, `memAlignedAlloc/Free`, `memCopy`, `memSet`, `memUTF8`, `memSlice`, `memRealloc` … |
| `org.lwjgl.system.MemoryStack` | 3 | `stackPush` … |
| `org.lwjgl.opengl.GL11/15/20/30/31/32/40/42/44`, `GL20C/30C`, `GL`, `GLCapabilities` | many | LWJGL3-style GL classes |
| `org.lwjgl.opengl.ARBBufferStorage` | 1 | persistent buffers |
| `org.lwjgl.PointerBuffer` | 1 | LWJGL3 `PointerBuffer` |

…but the reference **also** uses LWJGL2-name classes that must keep resolving:
`org.lwjgl.opengl.Display`, `org.lwjgl.input.Keyboard`/`Mouse`, `org.lwjgl.Sys`,
`org.lwjgl.BufferUtils`, `org.lwjgl.util.vector.Vector3f`, `org.lwjgl.opengl.ContextCapabilities`.

That exact mix — real LWJGL3 runtime **plus** LWJGL2-name shims — is what **legacy-lwjgl3** provides.
The Fabric 1.8.9 Radium port lists it as a hard dependency for precisely this reason.

### 6.2 What legacy-lwjgl3 is

- Repo: **moehreag/legacy-lwjgl3** (default branch `ornithe-multi`), **LGPL-2.1**.
- An **Ornithe/(legacy-fabric) mod** that makes MC <1.13 run on real **LWJGL3** instead of LWJGL2:
  swaps `Display`/`Keyboard`/`Mouse`/sound to GLFW/OpenAL via LWJGL3 natives, and provides
  LWJGL2-named shim classes (largely adapted from the original LWJGL 2 source) so vanilla and
  legacy mods keep compiling against `org.lwjgl.*`.
- Lineage: forked from **Zarzelcow/legacy-lwjgl3** (legacy-fabric; "tested 1.8.9"; JitPack
  `com.github.Zarzelcow:legacy-lwjgl3`), which is based on **gudenau/MC-LWJGL3** (a **Forge**
  coremod) plus the LWJGL 2 source.
- Published: `io.github.moehreag:legacy-lwjgl3:<VER>` on **AxolotlClient maven**
  (`https://maven.axolotlclient.com/releases`). Modrinth release `1.2.9+1.8.9` (7.67 MiB jar,
  covers MC 1.8–1.12.2) at
  `https://cdn.modrinth.com/data/lpiIRiAZ/versions/wCvmJAx1/legacy-lwjgl3-1.2.9+1.8.9.jar`.
- Bundles LWJGL3 BOM natives: `lwjgl` + `lwjgl-glfw`, `lwjgl-openal`, `lwjgl-opengl`, `lwjgl-stb`,
  `lwjgl-assimp`.
- Config knob: system property `legacy_lwjgl3.use_sdl` / env `LEGACY_LWJGL3_USE_SDL` → use SDL3
  instead of GLFW for window/input.

### 6.3 Forge-lineage candidates (the "find a Forge version" answer)

| Repo | What it is | Target | License / status |
|---|---|---|---|
| **gudenau/MC-LWJGL3** | The Forge coremod that started it all ("hacks Minecraft to use LWJGL3") | ~1.12 era (2017) | LGPL-2.1, **archived, "unfinished"** |
| **Verschwiegener/MCLWJGL3** | Forge-flavored LWJGL3 integration | old MC | source-of-inspiration |
| **GTNewHorizons/lwjgl3ify** | The heavyweight 1.7.10 coremod doing the same trick | 1.7.10 | reference patterns only |
| **yapeteam/minecraft-1.8.9-lwjgl3** | **MCP 1.8.9 built on LWJGL3** (`mvn compile exec:exec`), based on lwjgl3ify + MCLWJGL3 | **exactly 1.8.9** | Apache-2.0, active |

`yapeteam/minecraft-1.8.9-lwjgl3` is the closest match to our toolchain (MCP 1.8.9), but it is a
full MCP reimplementation of the game, not a drop-in mod — adopting it means replacing the launch
path, not just adding a dependency.

### 6.4 The three strategies for M3 — **DECISION: Option 1, executed as a pure native rewrite**

1. **LWJGL2-native rewrite** — keep compiling against stock LWJGL2 (as today). Rewrite
   `MemoryUtil`/`MemoryStack`/fence/GL-call sites into LWJGL2 idioms. No new runtime deps, no
   windowing surgery; but ~40+ MemoryUtil call sites in `common/src/desktop` + the GL device layer
   would need manual backporting, and the reference stays LWJGL3-shaped upstream.
2. **Adopt a Forge/MCP LWJGL3 runtime** — bring real LWJGL3 natives + LWJGL2 shims onto the
   classpath (via `yapeteam/minecraft-1.8.9-lwjgl3`, or by porting gudenau's coremod approach).
   Then `common/src/desktop` ports nearly verbatim. Cost: coremod-style patching of
   `Display`/context creation and bundling natives; more moving parts at boot.
3. **Port the legacy-lwjgl3 shim layer to Forge** — reuse our existing Forge coremod
   (`MixinLoader`) to inject the `org.lwjgl.*` shims + LWJGL3 natives. Most faithful, most work.

**DECISION (2026-08-08, executed in M3 Phase A):** **Option 1 — LWJGL2-native**, in its *purest*
form: a **direct hand-rewrite with no `org.lwjgl.system` shim at all**. The early "smart" estimate
(shim + ~20 files / ~60 call sites) proved conservative once the actual `gl/**` tree was javap'd
against `2.9.4-nightly-20150209`: only **~10 of 55 files** carried LWJGL3-only code, and every GL
constant/method the reference uses exists in LWJGL2 (some in different classes — see the ground
truth below). A compat shim would have been net-new surface with zero payoff, so none was shipped.

**Verified LWJGL2 ground truth that shaped Phase A** (all from `javap` on the real jar):
- Fences: `glFenceSync(int,int)→GLSync` (reference treats as `long`); `glGetSynci(GLSync,int)→int`
  (reference uses 3-arg IntBuffer); `glClientWaitSync(GLSync,int,long)`; `glDeleteSync(GLSync)`.
- `glMultiDrawElementsBaseVertex` **absent** → per-draw `glDrawElementsBaseVertex` loop over the
  batch; `MultiDrawBatch` raw pointers → direct `IntBuffer`/`LongBuffer` (byte offsets).
- `GL44.glMemoryBarrier` **absent** → `ARBShaderImageLoadStore.glMemoryBarrier`.
- `GL_COPY_READ/WRITE_BUFFER_BINDING` live in **GL31**, not GL42.
- `GL_MAX_TEXTURE_LOD_BIAS` lives in **GL14**; the query is `GL11.glGetInteger` — the GLxx classes
  do **not** subclass each other in LWJGL2, so inherited statics don't resolve across them.
- The FloatBuffer matrix-uniform wrapper is **`glUniformMatrix4`** (`glUniformMatrix4fv` is
  native-only); `glShaderSource(int, CharSequence)` exists; `glGetShaderi/glGetProgrami` exist, so
  `GLX.gl20Get*` maps straight to `GL20`.
- Java-16 surface in the reference (records, `var`, switch-exprs, `String.formatted`,
  `ByteBuffer.slice(int,int)`, `ServiceLoader.findFirst`) all downgraded to Java 8.

Rationale (unchanged): self-contained single-jar distribution on stock LWJGL2; Radium is archived
(no live-upstream churn to preserve); the LWJGL3 path's real cost is coremod windowing/context-init
surgery. Flipping condition for Option 3: tracking live upstream Sodium, or needing the LWJGL3
allocator ceiling.

---

## 7. Key facts / gotchas index

- MCP 1.8.9 → `stable_22`; verify names via MCP-919 curl (never WebFetch-summarize).
- `net.minecraftforge.fml.*` (no `cpw.mods.fml`).
- Mixin 0.7.11 shaded; config `minVersion 0.7`, `JAVA_8`, `required: true`.
- NEVER name `org.spongepowered.asm.mixin.transformer.MixinTransformer` in
  `getASMTransformerClass()`: its ctor is package-private and FML's `ASMTransformerWrapper`
  subclass fails to instantiate it (noisy ERROR every boot). The template returns `new String[0]`;
  mixin registers its own public `Proxy` via the tweak cascade.
- The LaunchClassLoader must delegate `org.spongepowered.asm.launch.` to the system classloader
  (`Launch.classLoader.addClassLoaderExclusion(...)` in the MixinLoader ctor), otherwise a second
  `MixinBootstrap` static state boots and logs "Multiple Mixin containers present…".
- Build on the Codespace; edit/audit locally.
- `@IFMLLoadingPlugin.MCVersion("1.8.9")` must **exactly** equal `FMLInjectionData.mccversion`
  ("1.8.9"): a missing annotation = WARN every boot; a mismatch = FML **ignores the coremod**
  (verified in CoreModManager 1.8.9 lines 543-558). Keep both in sync when bumping MC.
- `ForceLoadAsMod` is a **no-op on 1.8.9** — the string appears in zero classes of the Forge
  1.8.9 universal jar and LaunchWrapper 1.12 (template dead weight; removed). Unknown manifest
  attributes are silently ignored. The real 1.8.9 keys are `FMLCorePlugin`,
  `FMLCorePluginContainsFMLMod`, `ModSide=CLIENT`, `MixinConfigs`.
- **`TweakClass` in the manifest BANS the jar from the mod list (1.8.9).**
  `CoreModManager.discoverCoreMods()` (lines 342-350) treats any mods-folder jar whose manifest
  declares a cascaded tweaker as a *pure tweaker*: it registers the tweak, adds the jar to
  `ignoredModFiles`, and `continue`s — so `ModDiscoverer.findModDirMods()` never scans it for
  @Mod annotations. Symptom: coremod lines all present in the log, but the mod is missing from
  the mods list, from `FMLFileResourcePack:*`, and from the server mod handshake
  (“identified 3 mods to load” instead of 4). **Fix:** no `TweakClass`/`TweakOrder` in the
  manifest; instead register the mixin transformer directly:
  `getASMTransformerClass() → {"org.spongepowered.asm.mixin.transformer.MixinTransformer"}`
  (classic Forge-1.8.x mixin integration). Evidence: 2026-08-08 18:34 session listed radium
  (pre-fix jar), 20:51 session did not (post-fix jar); both jars' manifests differ only in the
  Tweak keys — the 1.8.9 source explains the regression.
- **Jar-size budget (2026-08-08):** `radium-0.8.15.jar` was 25.7 MB =
  fastutil 8.5.13 (12,809 classes ≈ 20.5 MB compressed + ~3.3 MB zip-entry overhead) + mixin
  0.7.11 (0.84 MB) + joml 1.10.8 (0.76 MB) + our code (~0.5 MB). The Fabric Radium jar (2 MB)
  works because FabricLoader provides loader-level mixin AND fastutil at runtime; Forge 1.8.9
  ships neither, so we shaded them. With fastutil replaced by JDK collections the jar is
  expected to be **≈ 2 MB**. When Phase B+ (chunk meshing, translucent sorting — ~40 upstream
  files that use fastutil) is ported, re-add the fastutil shade (or move it to a separate
  non-mod library jar in mods/, which FML 1.8.9 injects into the classpath).
- **Mixin 0.7.11-SNAPSHOT must live on the SYSTEM classloader — never move it back into a
  single-mod jar without a `TweakClass` cascade.** `MixinServiceLaunchWrapperBootstrap.bootstrap()`
  adds `org.spongepowered.asm.service.`/`.lib.`/`.mixin.`/`.util.` to the `LaunchClassLoader`'s
  exclusion list; then every `org.spongepowered` class is looked up through the system
  classloader. FML 1.8.9's only mechanism that puts a mods-dir jar there is `TweakClass`
  (`CoreModManager.handleCascadingTweak()` reflection-injects the jar into the AppClassLoader).
  That is exactly why the 22:20 boot died with `NoClassDefFoundError:
  org/spongepowered/asm/service/IMixinService` after `TweakClass` was removed from the mod
  jar, and why the fix is the dedicated `mixin-0.7.11-launchwrapper-bridge.jar` (task
  `mixinBridgeJar`): it carries the mixin classes + `TweakClass: MixinTweaker`, while the mod
  jar stays TweakClass-free and therefore listed. Empirically verified (local probe with the
  real launchwrapper-1.12 + JRE 1.8.0_202): mixin boots only when its classes are on the
  system classpath. REMEMBER for any future re-packaging: bridge jar + mod jar are a pair.
- `org.joml:joml` is shaded (real runtime dep since Phase A). `MixinBaseFrustum` runs without
  it only because `FrustumIntersection` int constants are javac-inlined; don't rely on that for
  new code.
- `:reobfShadowJar` (SpecialSource/ASM 5.x) can only parse class-file majors ≤ 52 (Java 8).
  Any future shaded dependency must be Java-8 bytecode and ship **no `module-info.class`**
  (joml 1.10.8's module-info is major 53 — excluded via `shadowJar { exclude 'module-info.class' }`;
  the failure signature is a message-less `IllegalArgumentException` in `org.objectweb.asm.ClassReader`).
- LWJGL2 fences are `GLSync` objects; LWJGL3 fences are `long`.
- `DestroyBlockProgress` → `client.renderer`; `NetHandlerPlayClient` → `client.network`.

---

## 8. Open questions

- [x] Does any 1.8.9 Forge mod ship a ready LWJGL3 layer as a *library* (not a coremod)?
  (gudenau is archived/1.12; yapeteam is MCP-reimplementation.) — **Moot:** §6.4 chose the native
  LWJGL2 rewrite, no LWJGL3 runtime needed.
- [x] For M2: does the shrunk bootable config actually reach the main menu on `runClient`? — **Yes**
  (M2 done 2026-08-08, see append log).
- [x] Is Radium's `common/src/desktop` truly LWJGL3-only, or does it already tolerate LWJGL2
  static classes? — **Quantified in Phase A:** only ~10 of 55 `client/gl/**` files carry
  LWJGL3-only code; every constant/method has an LWJGL2 equivalent (often in a different class).
- [ ] Licensing: Radium pulls in LGPL/MPL/Apache/MIT third-party code (see `thirdparty/NOTICE.txt`)
  — confirm obligations before copying into the shaded Forge jar.

---

## 9. Append log

- **2026-08-08** — Created. Documented the M1/M2 state, the compile fixes, and the newly-learned
  `legacy-lwjgl3` dependency with its full Forge lineage (§6).
- **2026-08-08** — Added §1.1 new-agent quickstart (build/boot commands, mixin-failure signatures,
  key-file map, git conventions, DoD checklist) so a fresh agent can start working without
  re-deriving the operational setup. Recorded the M1-compile-green confirmation and M2-boot as the
  active task.
- **2026-08-08** — Pre-boot hardening, grounded in manuthebyte/template-forge-mixin-1.8.9 (the
  canonical 1.8.9+Forge+Mixin template): (1) added `[Radium] Injecting with IFMLLoadingPlugin.`
  diagnostic to `MixinLoader` so a boot log has an unambiguous coremod-loaded signal (the template
  prints the same line); (2) added the template's LWJGL 2.9.4 substitution (Linux babric / macOS
  nightly) to `build.gradle` so `runClient` loads natives on modern systems instead of crashing on
  the stock 2.9.2-nightly. M2 boot test (runClient → main menu) is the next action.
- **2026-08-08** — Fixed a template bug: its Linux `dependencySubstitution` used `using(…)`, which
  Gradle 3.1 rejects (`No signature of method: …using()`). Replaced with `with(…)` — the form the
  template's macOS branch uses — so the Linux branch now evaluates. Template's Linux path is
  untested upstream; ours is now valid on Gradle 3.1.
- **2026-08-08** — **Killed the phantom babric dependency.** The next build failed to resolve
  `org.lwjgl.lwjgl:2.9.4-babric.1`. Ground-truth investigation (per "never guess"): (1) the
  artifact 404s on **every** reachable repo (forge.net, Maven Central, libraries.minecraft.net,
  sponge, legacyfabric `repo.legacyfabric.net`, and `maven.babric.dev` resolves HTTP000 — domain
  dead); (2) Mojang's canonical 1.8.9 `version.json` shows vanilla 1.8.9 ships
  `2.9.4-nightly-20150209` on Linux/Windows (not babric, not 2.9.2-nightly-201408222); (3) the
  template's Linux branch was never tested (its README: "only tested with IntelliJ" = the macOS
  path). Replaced the Linux-specific babric substitution + compile deps with the **same
  `2.9.4-nightly-20150209` on all platforms** — that is what vanilla 1.8.9 actually runs, it
  resolves from libraries.minecraft.net (200 on POM+jars+natives-linux/natives-osx), and
  `objdump -T` on `liblwjgl64.so` shows only GLIBC_2.2.5→2.4 requirements (safe on modern glibc).
  Removed the now-dead `systemOs` conditional entirely. Build should resolve; M2 `runClient` boot
  test is the next action.
- **2026-08-08** — **M2 boot baseline ✅ DONE.** User launched the built JAR through the SKlauncher
  Forge 1.8.9 install (`/home/e/.minecraft/logs/latest.log`, 13:47–13:53). Confirmed signals:
  `[Radium] Injecting with IFMLLoadingPlugin.` (coremod ran), `SpongePowered MIXIN Subsystem
  Version=0.7.11`, `LWJGL Version: 2.9.4` (the real1.8.9 natives from the substitution load
  fine — the babric worry is fully settled), mod present in Forge's list (`FMLFileResourcePack:
  Radium`, `Attempting connection with missing mods [mcp, FML, Forge, radium]`), main menu + sound
  engine reached, and the user played on a live server for ~5 min. **Zero mixin errors** in the log.
  Two `FATAL` NPEs at 13:49/13:53 are **vanilla 1.8.9 races**, not ours — `NetHandlerPlayClient`
  `handleSpawnPlayer` (`getPlayerInfo(packetIn.getPlayer()).getGameProfile()` NPEs when a server
  spawns a player before info syncs) and `handleConfirmTransaction`
  (`entityplayer.openContainer.windowId` NPE mid-respawn); neither method is touched by any of the
  6 registered mixins (verified against MCP-919). Reproduced on a live BedWars server, so treat as
  environmental. **M2 DoD met: boot to main menu, no mixin failure.** Next: M3, gated on the LWJGL
  decision (§6.4, Option 1 recommended).
- **2026-08-08** — **M3 Phase A ✅ DONE — GL device layer ported to native LWJGL2.** Ported all
  55 `client/gl/**` files as a direct LWJGL2.9.4 rewrite with **no `org.lwjgl.system` shim**
  (§6.4 Option 1, purest form; the earlier shim estimate was conservative). Reference diff is 1:1 —
  zero missing, zero extra files. Landed with it: `RenderDevice` stub → reference interface
  (`INSTANCE`, `enterManagedCode()`/`exitManagedCode()` — `LevelRendererMixin` static callers
  unaffected); `NativeBuffer` ByteBuffer-backed port (keeps `getTotalAllocated()` for
  `DebugScreenOverlayMixin`); full `MathUtil`; `UInt32`; `Services` + Forge
  `PlatformRuntimeInformation` (`Launch.minecraftHome`/`fml.deobfuscatedEnvironment`);
  `ShaderBindingContext`; `SodiumOptions.Advanced.useAdvancedStagingBuffers` +
  `enableMemoryTracing`. **Local `javac -source8` type-check is green** against the real
  `2.9.4-nightly-20150209` jar + fastutil/guava/commons-io/log4j/joml/annotations + a
  `ResourceLocation` stub — it caught two bugs the build would have too: `GL14` does not subclass
  `GL11` (inherited statics don't resolve; `GL11.glGetInteger(GL14.GL_MAX_TEXTURE_LOD_BIAS)`) and
  `ServiceLoader.findFirst()` is Java9+ (iterated the loader instead). Codespace `./gradlew build`
  green. Commits `e607dec` + `ef84fda`. **Next: Phase B** (vertex formats + encoders
  `api/vertex/**` + `render/chunk/vertex/**`). The GL smoke test (temporary `startGame`-RETURN hook
  exercising `createCommandList → createMutableBuffer → allocateStorage → uploadData → mapBuffer/unmap
  → createFence → deleteBuffer`) is staged for the next boot before the user runs the JAR.
- **2026-08-08** — **Forge-convention audit + fixes** (verified against the official 1.8.9 MDK,
  FML 1.8.9 source/javap of the real universal jar, LaunchWrapper 1.12, manuthebyte's template,
  and upstream Radium's Fabric build; see §7). Fixed: (1) **`org.joml:joml:1.10.8` moved to
  `shade`** — it is a real runtime dep (`ChunkRenderMatrices` `Matrix4f` fields,
  `GlUniformMatrix4f` `Matrix4fc` params) and upstream shades it; without shading the first
  shader/chunk code path would `NoClassDefFoundError`. Boot-safe only because `MixinBaseFrustum`
  inlines `FrustumIntersection`'s int constants (constant-pool-verified). (2) Added
  **`@IFMLLoadingPlugin.MCVersion("1.8.9")`** to `MixinLoader` — kills the per-boot
  `does not have a MCVersion annotation` WARN; FML ignores the coremod if the value ≠
  `FMLInjectionData.mccversion` (both "1.8.9"). (3) **Removed `ForceLoadAsMod`** from the
  manifest — a no-op on 1.8.9 (string absent from all FML 1.8.9 + LaunchWrapper 1.12 classes).
  (4) `@Mod(..., clientSideOnly = true)` on `RadiumForgeMod` so FML skips the @Mod load on a
  dedicated server (coremod already skipped via `ModSide=CLIENT`). (5) DOCS §2/§1.1/§7 updated.
  Both edited Java files compile-checked with `javac --release 8` against the real
  `forge-1.8.9-universal.jar` (`-proc:none`; the shaded jar's Mixin AP needs gson). **Toolkit
  caveat: the installed jar at `/home/e/.minecraft/mods/radium-0.8.15.jar` is STILL the
  pre-fix build from an uncommitted tree** (§1.1 Jar hygiene) — rebuild from HEAD on the
  Codespace before the next boot; the 18:34 session predates all of this. Next: GL smoke test,
  then Phase B.
- **2026-08-08** — **`:reobfShadowJar` build failure fixed** (reported by user). Signature:
  `java.lang.IllegalArgumentException` (no message) in `org.objectweb.asm.ClassReader.<init>` ←
  `net.md_5.specialsource.JarRemapper.remapClassFile` ← `TaskSingleReobf.applySpecialSource`.
  Cause: the newly-shaded `org.joml:joml:1.10.8` jar contains **`module-info.class` at class-file
  major 53 (Java 9)**; SpecialSource's bundled ASM 5.x supports only ≤52 and throws on it.
  Verified by inspecting the real Maven artifact: 117 classes major 46, 21 major 52, 1
  (module-info) major 53; and by simulating the reobf input (old jar + joml − module-info →
  zero classes >52). Fix: `shadowJar { exclude 'module-info.class' }` (JPMS metadata is
  dead weight on Java 8 anyway). Commit `7e1de63`.

- **2026-08-08 (late) — “Mod vanished from the mods list + jar is 25 MB” diagnosed and fixed.**
  User rebuilt from HEAD, installed at 20:42, ran at 20:51; reported (1) jar size 25.7 MB and
  (2) radium missing from the loaded-mods list. Diagnosis (both verified against the real
  `forge-1.8.9-universal.jar` + FML source):
  - **Mod-list regression:** `latest.log` 20:51 shows the coremod loading fine (`MCVersion`
    accepted, `[Radium] Injecting…`, MixinTweaker cascade, `identified 3 mods to load`) but
    `FMLFileResourcePack:Radium` is gone and the handshake is `missing mods [mcp, FML, Forge]`
    (was `[…, radium]` at 18:34 with the previous jar). Root cause (source-verified):
    `CoreModManager.discoverCoreMods()` in 1.8.9 treats a mods-dir jar whose manifest declares
    `TweakClass` as a pure tweaker — it registers the cascaded tweak, adds the jar to
    `ignoredModFiles` (lines 342-350 of CoreModManager.java) and never reaches the
    `FMLCorePluginContainsFMLMod` reparse branch; `ModDiscoverer.findModDirMods()` then skips
    the jar for @Mod scanning. The previous jar escaped this only through the uncommitted-tree
    build differences — the fix makes it structurally impossible to regress. **Fix:** remove
    `TweakClass`/`TweakOrder` from the jar manifest and register the transformer through the
    FML plugin instead: `MixinLoader.getASMTransformerClass()` now returns
    `{"org.spongepowered.asm.mixin.transformer.MixinTransformer"}` (classic Forge-1.8.x mixin
    integration; FML registers it as an `IClassTransformer`). Manifest keeps `FMLCorePlugin`,
    `FMLCorePluginContainsFMLMod`, `ModSide`, `MixinConfigs`.
  - **Size:** 25.7 MB = fastutil 8.5.13 (12,809 classes; 20.5 MB compressed + ~3.3 MB zip-entry
    overhead) + mixin 0.7.11 (0.84 MB) + joml (0.76 MB) + our code (~0.5 MB). The Fabric jar
    is 2 MB because FabricLoader provides mixin + fastutil at runtime; Forge 1.8.9 provides
    neither. Replaced the four fastutil usages (`GlVertexFormat`, `ShaderParser`,
    `MappedStagingBuffer` — `ArrayDeque`; `NativeBuffer` — synchronized `IdentityHashMap`)
    with JDK collections, so the jar drops to **≈ 2 MB**. NOTE for future phases: re-add the
    fastutil shade when the chunk renderer lands (upstream uses it in ~40 meshing/sorting files)
    or ship it as a non-mod library jar. All edits `javac --release 8` checked locally (clean;
    `ResourceLocation` stubbed for the check). Expected next boot: `identified 4 mods`,
    `FMLFileResourcePack:Radium`, `[mcp, FML, Forge, radium]` handshake, ≈2 MB jar, mixins
    applied with no new errors.
- **2026-08-08 (late, 2nd) — 22:20 boot crash diagnosed: mixin needs the system classloader; two-jar fix.**
  The 1a070e0 fix (TweakClass removal) restored the mod-list entry but crashed the next boot
  (`NoClassDefFoundError: org/spongepowered/asm/service/IMixinService` in `MixinBootstrap.<clinit>`
  ← `MixinLoader.<init>` ← `CoreModManager.loadCoreMod:577`). Root cause (bytecode-level, on the
  shipped classes): mixin 0.7.11-SNAPSHOT's `MixinServiceLaunchWrapperBootstrap.bootstrap()`
  adds `org.spongepowered.asm.service.`/`.lib.`/`.mixin.`/`.util.` to the LaunchClassLoader's
  classloader-exclusion set, and `MixinService.initService()` then looks up `IMixinService` via
  `ServiceLoader`/`ldc` through the *system* classloader. The only FML 1.8.9 path that places a
  mods-dir jar on the system classloader is the `TweakClass` cascade (CoreModManager
  `handleCascadingTweak` → `URLClassLoader.addURL(AppClassLoader)`) — which permanently bans the
  jar from @Mod scanning. So a SINGLE jar cannot be both mod-listed and mixin-hosted on 1.8.9.
  **Fix (committed):** two-jar deployment — `mixin-0.7.11-launchwrapper-bridge.jar` (new Gradle
  task `mixinBridgeJar`, built from the shaded jar's `org/spongepowered/**`, manifest
  `TweakClass: org.spongepowered.asm.launch.MixinTweaker`, `TweakOrder: 0`, `ModSide: CLIENT`)
  + `radium-0.8.15.jar` unchanged as the listed @Mod (its own org/spongepowered copies are
  harmless dead weight). Verified locally with a probe harness on the real launchwrapper-1.12 +
  JRE 1.8.0_202: bridge-on-system-classpath → mixin boots (needs the real game Launch context
  for the side-detection NPEs, i.e. the probe artifacts are environment, not mixin); mod-jar-only
  → service resolution fails exactly like the 22:20 log. Install BOTH jars into mods/.
  Next boot expect: `Loading tweaker … mixin-0.7.11-launchwrapper-bridge.jar`, `[Radium] Injecting
  with IFMLLoadingPlugin.`, MIXIN subsystem 0.7.11, `identified 4 mods`,
  `FMLFileResourcePack:Radium`, handshake `[mcp, FML, Forge, radium]`, main menu, zero mixin
  errors (this restores the M2-proven layout — tweak cascade + FML plugin — from two jars).


- **2026-08-09** — Two-jar deployment VALIDATED on a real game run (logs `/home/e/.minecraft/logs/
  latest.log`, 23:12–23:19 boot + singleplayer session): `Loading tweaker … from
  mixin-0.7.11-launchwrapper-bridge.jar`, `[Radium] Injecting with IFMLLoadingPlugin.`, MIXIN
  subsystem 0.7.11 `Source=…bridge…`, `Enqueued coremod`/`Loading tweak class name`, FML plugin
  conveyor, `identified 4 mods`, `FMLFileResourcePack:Radium` (2× resource reload), handshake
  `[mcp, FML, Forge, radium]`, `Client attempting to join with 4 mods … radium@0.8.15`, world
  played, clean stop. Install set is BOTH jars (`radium-0.8.15.jar` ≈2.0 MB +
  `mixin-0.7.11-launchwrapper-bridge.jar` ≈0.95 MB) — your single `./gradlew build` makes both.
  Two cosmetic log lines during that boot were root-caused and fixed in this commit:
  1. `A critical problem occurred registering the ASM transformer class
     $wrapper.org.spongepowered.asm.mixin.transformer.MixinTransformer (…can not access a member
     … with modifiers "")` — FML $wrapper subclasses named plugin transformers; `MixinTransformer`'s
     constructor is package-private (javap-verified), so the wrap failed. Root cause: our own
     `getASMTransformerClass()` was naming `MixinTransformer`. Mixin 0.7.11 registers its real
     transformer itself — `org.spongepowered.asm.mixin.transformer.Proxy` — via
     `MixinServiceLaunchWrapper.beginPhase()` during the tweak cascade (verified: no caller of
     `MixinTransformer.<init>` exists outside `Proxy` in the shipped jar; the canonical
     template returns an EMPTY array). **Fix: `getASMTransformerClass()` → `new String[0]`**
     (also removes the duplicate second transformer instance).
  2. `Multiple Mixin containers present, init suppressed for 0.7.11` — the WARN means a second
     `MixinBootstrap` static state: the bridge's tweak-cascade boots `org.spongepowered.asm.*` on
     the SYSTEM loader, while the LaunchClassLoader later loaded the same `asm.launch.` package
     from the mod/bridge jar (mixin's own exclusions cover only service./lib./mixin./util.).
     **Fix: `Launch.classLoader.addClassLoaderExclusion("org.spongepowered.asm.launch.")` first
     thing in the MixinLoader ctor** — the LCL then delegates that package to the system copy
     (which the cascade guaranteed to exist; during the next build, verify both lines disappear
     while all green signals above remain — the exclusion must be registered BEFORE the LCL's
     `Loading tweak class name org.spongepowered…` line in the boot order).
  The mixin chain itself was verified end-to-end with a LaunchWrapper probe on the real JRE 1.8
  (bridge → system cascade → `Proxy` on LCL → `MixinTransformer` → config load):
  `RESULT=MIXED` for an `@Inject(HEAD)` shim; the earlier ORIG results were probe-harness
  artifacts (missing `compatibilityLevel: JAVA_8` on the probe config and putting the probe
  target in the mixin package — real config/targets have neither issue).


### 2026-08-09 — Phase B (vertex pipeline) ported + pushed as `d9094f5`

**Scope**: the Radium vertex-pipeline tree, ported to LWJGL2 / MCP 1.8.9 with the adopted
ByteBuffer / absolute-offset convention (no `org.lwjgl.system.MemoryUtil` anywhere; no mixins
touched — the GL smoke remains the only enabled runtime behavior).

New files:

- `api/util/` — `ColorABGR`, `ColorARGB` (Java 8 switch statements), `ColorMixer`,
  `ColorU8`, `NormI8` (`MathHelper.clamp_float`).
- `api/math/MatrixHelper` (joml `Math.fma`/`invsqrt` — joml is already shaded).
- `api/internal/DependencyInjection` — reference used `String.formatted` → `String.format`
  (Java 8).
- `api/vertex/attributes/common/` (6) — Color / Light / Normal / Overlay / Position /
  Texture attributes; port convention: `memPutInt(long ptr, v)` → `ByteBuffer.putInt(offset, v)`.
- `api/vertex/serializer/` — `VertexSerializer` (`serialize(ByteBuffer src, ByteBuffer dst,
  int count)`) + `VertexSerializerRegistry` (1.8.9 `net.minecraft.client.renderer.vertex.VertexFormat`).
- `api/vertex/format/common/` — **ColorVertex + ParticleVertex only**: 1.8.9
  `VertexFormats.POSITION_COLOR` / `PARTICLE` match the reference layouts (stride 16 / 28).
  `EntityVertex` / `GlyphVertex` / `LineVertex` DELAYED: modern-only `VertexFormats` and
  hardcoded offsets; re-audit against 1.8.9 formats when a consumer lands.
- `client/render/chunk/terrain/` — `TerrainRenderPass`, `DefaultTerrainRenderPasses`
  (`fromLayer(EnumWorldBlockLayer)`), `material/` (`Material`, `MaterialParameters`,
  `AlphaCutoffParameter`, `DefaultMaterials.forBlockState(IBlockState)` via 1.8.9
  `state.getBlock().getBlockLayer()`).
- `client/render/chunk/shader/ChunkShaderBindingPoints` (attribute indices 0–3).
- `client/render/chunk/vertex/format/` — `ChunkVertexEncoder`
  (`int write(ByteBuffer, int offset, int materialBits, Vertex[] quad, int sectionIndex)`),
  `ChunkVertexType`, `ChunkMeshFormats`, `impl/CompactChunkVertex` (STRIDE=20: POSITION hi/lo,
  COLOR, TEXTURE + centering bias, LIGHT_MATERIAL_INDEX), `impl/DefaultChunkMeshAttributes`.
- `client/render/chunk/vertex/builder/ChunkMeshBufferBuilder` — native direct buffers,
  allocate+copy growth (LWJGL2 has no `memRealloc`), `slice()`, `writeExternal`.

**Port findings**:

1. **JDK 8 `ByteBuffer.duplicate()` resets byte order to BIG_ENDIAN** (verified on the real
   game JRE). `slice()` re-applies `buffer.order()`; any future duplicate in this codebase
   must do the same or reads are byte-swapped.
2. Material bits = `{useMip << 0 | alphaCutoff.ordinal() << 1}` (reference-exact, TINY ordinal).

**Local verification** (no Gradle on this machine): 96-file javac closure rc=0 (`--release 8`
+ stubs) + two probes on the real game JVM (JRE 1.8): `ProbeF` 19/19 PASS (20-bit position
quantization, texture centering bias, ao color-mix rounding, light/material/section packing,
grow 4→804 vertices, `writeExternal`) and `AttrProbe` 19/19 PASS (attribute round-trips,
ColorVertex incl. `Matrix4f` transform, ParticleVertex layout). Commit `d9094f5` pushed;
next: Codespace `./gradlew build` + boot (GL smoke lines + no boot regressions).


# Appendix: Phase C5 — translucent sorting + region/executor chain (2026-08-09, second session part)

## What landed in C5
- **RenderSection closure**: `RenderSection.java` (611 lines) now compiles; its `RenderRegion`
  dependency is the **real port** (423 lines) instead of a stub — GL arena/tessellation layers
  from Phase A were sufficient. Ported with it: `SectionRenderDataStorage`, `SectionRenderDataUnsafe`,
  `SharedQuadIndexBuffer`.
- **Compile/executor chain**: `ChunkJob`, `ChunkBuildContext`, `ChunkBuildBuffers`,
  `buffers/{ChunkModelBuilder, BakedChunkModelBuilder}`.
- **Translucent sorting**: full `translucent_sorting/` port (46 files: bsp_tree, data, trigger,
  quad + collector) — all Java 14+ syntax converted (var, pattern instanceof, switch arrows),
  5 pattern-instanceof sites and ~230 `var`s hand-typed to Java 8.
- **Lists/map/occlusion/shader/estimation/util**: `ChunkRenderList(+Iterable)`, `DeferredTaskList`,
  `SectionCollector`, `SortItemsProvider`, `SortedRenderLists`, `TaskCollectingTree` placeholder,
  chunk map cluster, `AsyncCameraTimingControl`, `ChunkFogMode`, `ChunkShaderFogComponent`,
  `ShaderBindingContext`, estimator cluster, `interval_tree` + `util/sorting` (Insertion/Merge/
  RadixSort), `DirectionUtil` (EnumFacing), `Mth`, `FogHelper`.

## Documented port divergences (all marked in-code)
1. **`BlockRenderCache` = minimal stub** (`compile/pipeline/`). Real one needs LevelSlice/
   ArrayLightDataCache/ColorProviderRegistry/LightPipelineProvider/FluidRendererImpl/BlockModelShapes
   (Phase D model/lighting batch). Nothing instantiates `ChunkBuildContext` yet — only `ChunkJob`
   references it by interface, so the chain closes.
2. **`TaskCollectingTree` = constant-only placeholder** (`SECTION_Y_MIN=-128`, reference-exact).
   Real class extends `SectionTree` (occlusion batch, not yet ported); `DeferredTaskList` needs
   only the constant.
3. **`compat/lwjgl/MemoryUtil`** = new LWJGL2 shim backed by `sun.misc.Unsafe` (JRE 8 present),
   replacing LWJGL3 `org.lwjgl.system.MemoryUtil` raw-address ops used by `SectionRenderDataUnsafe`.
   Allocation uses an 8-byte alignment header; `nmemAlignedFree` reads the base pointer from
   `pointer-8`. Divergence documented in the file.
4. **`FogHelper`** reads fog state from GL11 fixed-function state (`GL_FOG_START/END/COLOR`)
   instead of `GlStateManager`/`GameRenderer` fields (both unavailable in MCP 1.8.9; the game
   pushes identical values every frame via `EntityRenderer.setupFog`).
5. **`AsyncCameraTimingControl`** camera source = `Minecraft.getMinecraft().getRenderViewEntity()`
   position (MCP has no `Camera` class); Vec3d → `Vec3` (xCoord/yCoord/zCoord).
6. **`SodiumOptions.performance.quadSplittingMode`** = `QuadSplittingMode.SAFE` (no options UI yet).
7. Java-8 mechanical rewrites: `String.format` replaces `.formatted()`; `Math.fma` expanded;
   `MathHelper.clamp` → `clamp_int`; `new Iterator<>{}` → explicit type arg; nested
   `NodeReuseData` / `QuadIndexConsumerIntoBuffer` / `DynamicTopoSorter` inner classes lose
   `static` members (instance helper where upstream used Java-17 static-in-inner).

## Verification
Local closure javac (JDK8 temurin, 244 sources, jars-only classpath): **rc=0, 0 errors**.
Next: codespace `./gradlew build` (ground truth) + user boot test (GL smoke + world render callbacks).


---

# Appendix: Phase C6a–C6d — the real renderer ships (2026-08-10)

**Milestone complete:** the full Sodium chunk pipeline now renders live worlds on Forge 1.8.9.
User-validated boot at `3833a94` ("everything looked fine") — meshing, async culling,
shader-based chunk drawing, fluids, translucency sorting, entity culling and tile entities
all run through the ported code. Local closure javac and codespace `./gradlew build` both green.

## What landed (commits)
- `c3cd179` C6a — world slice (`LevelSlice`/`WorldSlice`, `SectionPos`, light/color layers).
- `f19d9c5` C6b — renderer core: executor, occlusion tree, lists, regions, shaders, async culling.
- `79d5577` C6c — meshing pipeline: `ChunkBuilderMeshingTask`/`SortingTask`, `BlockRenderer`,
  `BlockRenderCache`, `BlockOcclusionCache`, `fluid/` (renderer+impl+default), `RenderSectionManager`,
  `DefaultChunkRenderer`, `ShaderChunkRenderer`, services, `BakedQuadMixin`.
- `3833a94` C6d — `SodiumWorldRenderer` real hub wired into `RenderGlobal` (`LevelRendererMixin`):
  `renderBlockLayer`/`setupTerrain`/`markBlockRangeForRenderUpdate`/`updateChunks`/`getDebugInfoRenders`
  overwritten, `setWorldAndLoadRenderers`/`onResourceManagerReload` injected.

## Port divergences documented (each also marked in-code)

### Mappings that needed real verification (not guesses)
1. **`hasTransparency()` → `isOpaqueCube()` DIRECT (no negation).** Proven via legacy-yarn 1.8.9
   javadoc (`Lafh;c()Z` ↔ MCP `Block.isOpaqueCube` at index `[42]`) plus vanilla `RenderChunk`
   bytecode, which skips re-rendering when `!isOpaqueCube` — i.e. "is transparent" ⇔ "not opaque cube".
2. **`isSideInvisible()` → `shouldSideBeRendered()`**, TRUE = render side, no negation (javap on
   1.8.9 `Block.a(...)` confirmed same semantics as the reference call site).
3. **`BlockLiquid.getFlowDirection(IBlockAccess, BlockPos, Material)`** (public static, returns
   `atan2(z,x) − π/2` or the `−1000.0` sentinel) replaces the reference's protected
   `getFlowVector`/`Vec3` velocity. `−1000.0` → still sprite; else flowing sprite with
   `dir=(float)flowAngle` and the same sin/cos × 0.25 UV math.
4. **`Block.getBlockType()` → `getRenderType()`** (same int semantics: 3=model, 2=TESR, 1=liquid,
   −1=none), proven by javap + vanilla bytecode.
5. **`LevelGeneratorType.DEBUG` → `WorldType.DEBUG_WORLD`** (javap-verified).

### 1.8.9 structural replacements
6. **No `Camera` class** (legacy-yarn artefact; MCP 1.8.9 has none). `Camera.PROJECTION_MATRIX` /
   `MODEL_MATRIX` / `getRotationX/Z` become **GL readbacks**
   (`SodiumWorldRenderer.captureGlMatrices()`: `glGetFloat(GL_PROJECTION_MATRIX)` +
   `GL_MODELVIEW_MATRIX` into joml `Matrix4f`, column-major). Vanilla populates both in
   `EntityRenderer.setupCameraTransform()` before `setupTerrain`/`renderBlockLayer`, so the readback
   is exactly what vanilla would have drawn with (head-bob translation included, matching upstream).
   `FogHelper` already reads `GL_FOG_END`; pitch/yaw come from `getRenderViewEntity()`.
7. **`RenderLayer` → `EnumWorldBlockLayer`** via `DefaultTerrainRenderPasses.fromLayer`.
   `BlockBreakingInfo` → `DestroyBlockProgress` (`getPosition()`, `getPartialBlockDamage()`).
   `BlockEntity` → `TileEntity` (`TileEntityRendererDispatcher.instance.renderTileEntity(te, partial, stage)`).
   `Entity.shouldRenderName()` → `getAlwaysRenderNameTagForRender()`.
   `GameOptions.viewDistance` → `GameSettings.renderDistanceChunks`.
   `ChunkSection` → `ExtendedBlockStorage`; `world.getChunk` → `getChunkFromChunkCoords`.
8. **`BakedQuad` bridge (`gg.sona.radium.mixin.sodium.core.model.quad.BakedQuadMixin`)** makes
   vanilla `BakedQuad` implement `BakedQuadView`; `getSprite()` returns null (1.8.9 has no
   `TexturedBakedQuad`; sprites resolve via model/atlas). The mixin has **no refmap entry** and that
   is correct: ForgeGradle reobf remaps mixin bytecode MCP→SRG directly (verified:
   `vertexData→field_178215_a`, `tintIndex→field_178213_b`, `face→field_178214_c`,
   `hasTintIndex→func_178212_b`, grep-matched against `mcp-srg.srg`) — same mechanism as `MixinBiome`.
9. **`FluidSprites`** (`dev/vexor/radium/util/`): 1.8.9 `BlockFluidRenderer` has no sprite fields
   (the reference used a mixin accessor), so sprites are lazily fetched from the block atlas
   (`Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("minecraft:blocks/water_still"…`),
   refreshed on first `forFluid` call; `Material.water`/`Material.lava` lowercase.
10. **`DefaultChunkRenderer` NIO batches**: `MultiDrawBatch` uses NIO buffers instead of raw
    pointers (`elementOffsets` LongBuffer, `baseVertices`/`elementCounts` IntBuffer,
    `buffer.put(idx, v)` instead of `MemoryUtil.memPutX`); LWJGL3 imports dropped entirely.
11. **`SpriteUtil` is a no-op**: 1.8.9's `TextureMap` already ticks/animates every sprite each
    frame, so the `SpriteExtension` active-marking mixin has no target.
12. **`rendersOutsideBoundingBox()` doesn't exist in 1.8.9** — meshing passes `true` (entities
    always render; the field is only a culling hint).
13. **Vanilla `ViewFrustum`/`RenderChunk` storage still allocated** (no `@Redirect` nullifying
    `renderDistanceChunks` in `reloadRenderers`). Harmless memory overhead; the vanilla machinery
    is inert because `updateChunks`/`renderBlockLayer`/`setupTerrain` are overwritten.
14. `ChunkRenderMatrices` gained `projection()`/`modelView()` accessors; `SodiumOptions.Performance`
    gained the reference default fields (incl. `useEntityCulling=true`); dev-environment
    terrain-sorting check dropped (no debug options section) → `DYNAMIC_DEFER_NEARBY_ZERO_FRAMES`.
15. `org.lwjgl.util.vector.Vector3f` (lwjgl_util, ships with vanilla 1.8.9) is on the compile
    classpath — no joml detour needed in `BlockRenderer`.
16. Java-8 mechanical rewrites: pattern `instanceof` → cast, records → `private static class`,
    `var` → explicit types, `ChunkJob` interface for `scheduleTask` (returns `ChunkJobTyped<T,O>`),
    `Estimator.toString` returns `String` (reference had an `EstimationParameters` record).

## Verification
- Local closure javac (JDK8, 361 sources, `cp_full` + lwjgl_util): **rc=0**.
- Codespace `./gradlew build`: **SUCCESS** at each C6 commit (mixin AP + searge reobf).
- User boot at `3833a94` (26,259,166 B jar): clean join/play/quit, `[Radium] GL smoke` at first
  frame, zero exceptions, no crash report, world rendered normally.

---

## Phase D2e — settings UI redesign (OptiFine reference) + polish pass (be5c8e2, be5c8e2+)

User feedback on the D2d category screens: (1) sliders looked "weird, not vanilla",
(2) appended buttons on the Video Settings screen were not in the vanilla row/column
layout, (3) setting name and control were spaced too far apart — with explicit
instruction to look at how OptiFine does it. OptiFine 1.8.9 M5 decompiled source
(Oval/OptiFine-1.8.9-M5-SRC) consulted for ground truth.

### What OptiFine 1.8.9 actually does (source-verified)
- Category screens (`GuiQualitySettingsOF` etc.): plain `buttonList` grid,
  `x = width/2 - 155 + i%2*160`, `y = height/6 + 21*(i/2) - 12`, 150-wide controls,
  option name EMBEDDED in the control label ("Name: Value"), Done at
  `(width/2-100, height/6+168+11)`.
- Sliders (`GuiOptionSliderOF` extends `GuiOptionSlider`, zero overrides): plain
  button texture + label text; **no knob ever drawn** — 1.8.9 `GuiScreen` has no
  `mouseDragged`, so the vanilla knob sprites are dead code in 1.8.9.
- Tooltips (`TooltipManager` + `TooltipProviderOptions`): hover-still 700 ms → dark
  rect (`0xE0000000`) at `(width/2-150, height/6-7)` (below the cursor if the cursor
  is in the top area), wrapped grey (`0xDDDDDD`) text, max 8 lines, " ..." suffix.

### Changes (be5c8e2)
1. **`SodiumSlider`** rewritten vanilla-exact: no `drawButton` override (plain button
   bg + label), click-to-set, drag tracking via the screen's `mouseClickMove`
   (1.8.9 has no mouseDragged dispatch), config saved on `mouseReleased`.
2. **`SodiumOptionsScreen`** rewritten OptiFine-style: 2-column grid, labels embedded
   in controls ("Use Async Culling: ON", "Perfect Translucency: Safe"), title at
   y=15, Done at OptiFine's position, Esc returns, tooltips per the OptiFine
   TooltipManager pattern. Tooltip text = reference Radium `en_us.json` strings
   (hardcoded English — the mod jar is a coremod, its assets are not registered as a
   resource pack, so lang-key lookups show raw keys; verified via
   `ChatComponentTranslation.getUnformattedText` → `StatCollector` analysis).
3. **Video Settings stays 100% vanilla**: `OptionsRowListMixin` (replaces
   `OptionsScreenMixin`, deleted) `@Inject`s `GuiOptionsRowList.<init>` RETURN and
   appends two `SodiumOptionsRow`s (Performance|Quality, Advanced|—) into the
   vanilla scroll list (`@Shadow field_148184_k`, already-SRG name, no refmap entry
   needed). Row buttons use the vanilla 2-column x positions; clicking opens the
   category screen with `mc.currentScreen` as the owner (bytecode verified:
   `field_71462_r`, `func_147108_a` after reobf).
4. **Polish pass**: README.md added; mcmod.info authorList → ["Rtx"] + description
   updated; dead `MinecraftClientMixin` (never registered in the mixin json) and
   no-op `ConfigManager` (called every frame from `MinecraftMixin.preRender`)
   deleted, call removed. Kept: `ConsoleHooks`/`FPSCounter` hooks (documented
   no-ops, profiler-guarded, architecture parity with reference).
5. Render distance cap to 32 chunks was already active via `GameOptionsMixin`
   (`GameSettings.Options.RENDER_DISTANCE.setValueMax(32.0F)` — present since the
   original port; README corrected to match).

### Verification
- Local closure javac (JDK8): rc=0.
- Codespace `./gradlew build`: SUCCESS (be5c8e2).
- Jar 26,281,060 B installed; bytecode-verified: row-list mixin registered, old
  OptionsScreenMixin gone, `field_148184_k` shadow intact, `currentScreen` reobf'd
  to `field_71462_r`, `displayGuiScreen` → `func_147108_a`, slider has no
  `drawButton` override.
- Awaiting user boot test: expect vanilla video settings + 2 appended listed rows;
  category screens with tight embedded labels, vanilla-looking sliders with drag,
  and hover tooltips; settings persist.

## Phase D3 — Renderer activation (8383054)

**Critical finding (2026-08-11):** the ported renderer was never active in production. Commit 7c104ad
("sweep") trimmed `radium-forge.mixins.json` from 24 to 8 client mixins while boot crashes were
debugged, and the full list was never restored. All pre-D3 performance measurements (incl. the
105 fps @ RD7 validation) therefore reflect **vanilla rendering** — Sodium's chunk pipeline,
`LevelRendererMixin` overwrites, chunk tracker, vertex formats, options mixins, and the
weather/leaves quality hooks were all dormant.

**Fixed in 8383054:** all 15 previously-trimmed mixins re-registered (23 client + 1 shared =
24, matching the jar's mixin class set).

**Selector hardening** (Forge 1.8.9 runtime classes are srg-named; refmap values are remapped at
runtime but raw annotation strings are not):
- 6 class-bearing `@At` targets converted to literal SRG member names with `remap=false`:
  - `LevelRendererMixin` (core): `GameSettings.renderDistanceChunks` -> `field_151451_c` (ordinal 1 = ViewFrustum alloc read, javap-verified)
  - `ClientChunkCacheMixin`: `Chunk.onChunkUnload` -> `func_76623_d`, `setChunkLoaded` -> `func_177417_c`
  - `GuiMixin`: `Minecraft.isFancyGraphicsEnabled` -> `func_71375_t`
  - weather `LevelRendererMixin`: `fancyGraphics` -> `field_74347_j`, `shouldRenderClouds` -> `func_181147_e`
- console `GameRendererMixin`: Forge replaces the direct `GuiScreen.drawScreen` call in
  `updateCameraAndRender` with `ForgeHooksClient.drawScreen` — retargeted to the Forge hook.
- Every injection point javap-verified against the deobfed 1.8.9 jar (single-occurrence checks;
  `renderGameOverlay(F)V` is `func_175180_a`, NOT the 1.16-style 4-arg version).

**Verified in the built jar:** refmap (13 entries, all correct SRG targets), SRG literals present in
reobf'd class bytecode, 24 registrations.

**Status:** installed (26,279,476 B), AWAITING USER BOOT TEST — first real run of the Sodium pipeline.

**Known follow-up:** jar is 26 MB because `shadowJar` shades ALL of fastutil (~20 MB compressed of
55.6 MB uncompressed, 12.8k classes). `shadowJar { minimize() }` can shrink it to ~5-6 MB once the
renderer activation is validated (fastutil refs are all direct/static — minimization is safe).
---

# HANDOFF — 2026-08-11 run (commit 7839e56 "D4 diagnostics"), user-confirmed: blocks STILL invisible + ~6 FPS

## State / files
- Installed: `~/.minecraft/mods/radium-0.8.15.jar` = 7839e56 build (26,289,213 B, sha256 45fa241de...). Local copies: `/home/e/.cache/sf/radium-7839e56.jar`, b64 chunks `/tmp/sfD4/chunk*.b64`. Backups: `radium-0.8.15.jar.bak-d2752a1` (previous), `.bak-db44842`, `.bak-06b5aa7`.
- Boot log from this run: `~/.minecraft/logs/latest.log` (174 `[RadiumDiag]` lines). User joined MpServer at ~? and walked around; camera ended near (-235..-244, y≈5.6, 347..351) — **ON THE GROUND, not flying** — so the "fog wall at altitude" theory is DEAD.
- Repo: main @ 7839e56. Diagnostics live in `gg.sona.radium.diag.Diag`, hooks: LevelRendererMixin (renderBlockLayer + setupTerrain), DefaultChunkRenderer.drawBatch, EntityRendererDiagMixin (updateCameraAndRender HEAD/RETURN).

## ROOT-CAUSE CANDIDATE #1 — chunk fade factor ≈ 0 (THE invisible-world bug, high confidence)
From the uniform readback (draw-time):
- `u_FadePeriodInv=[0.000]` — printed 0.000 at 3dp but actual = 1/(chunkSectionFadeInTime*1000) = 1/3,500,000 ≈ 2.86e-7 (`DefaultShaderInterface.setFadePeriod`).
- `u_CurrentTime=5152` (ms since region creation).
- Shader: `fade = clamp((u_CurrentTime - chunkFade) * u_FadePeriodInv, 0, 1); fadeFactor = (chunkFade < 0) ? 1.0 : fade;` then `color *= fadeFactor`.
- **If `chunkFades` UBO entry == 0 for a section: fade = 5152 * 2.86e-7 ≈ 0.0015 → color ≈ 0 → fragments = fog color at full fogFactor → screen shows "sky/void" — exactly the user's report.**
- The 1000x scale bug: fade period is interpreted as MILLISECONDS*1000 (inv = 1/(3500*1000)) so a 3.5 s fade takes 3.5e6 ms = **58 minutes**. Even the intended fade-in path (chunkFade = buildTime) is effectively invisible for ~1 hour.
- Escapes that make it visible by design: `chunkFade < 0` → 1.0. RenderRegionManager.uploadResults writes `-1` via `writeMeshTimes` ONLY when `upload.relativeBuiltTime != -1` AND `distanceToPlayer < 768` (always true at RD2, so -1 SHOULD be written on first build). **The UBO content readback FAILED to capture evidence: `ubo=0[]` in the state probe — GL_UNIFORM_BUFFER_BINDING is 0 at probe time (GlUniformBlock unbinds after the draw). The -1 write is unverified on this GPU.**
- Two possibilities to test/fix (either is a complete fix):
  1. The -1 write IS happening → fix the 1000x: `uniformFadePeriod.setFloat(1.0f / (chunkSectionFadeInTime))` (per-ms) OR set `SodiumOptions.quality.chunkSectionFadeInTime = 0`-equivalent (instant) — check `DefaultShaderInterface.setFadePeriod`.
  2. The -1 write is NOT reaching the UBO (writeMeshTimes/streamer/UBO-binding bug) → sections keep initial 0 → **fix the UBO write/bind path** (GlBufferStreamer writeData / GlUniformBlock bind / RenderRegion.DeviceResources.chunkFades).
- Next-step probe if needed: rebind the UBO in the probe (bind GL_UNIFORM_BUFFER to the streamer's buffer id, glGetBufferSubData, unbind) or glGetBufferSubData BEFORE executeDrawBatch while it is still bound.

## VERIFIED CORRECT (do NOT re-chase these)
- `u_ModelViewMatrix` = rotation-only + small eye-height translation (col3 = (0.000,-1.588,-0.420,1.000)) — NO camera-position translate in 1.8.9's GL_MODELVIEW (confirmed from EntityRenderer.setupCameraTransform bytecode: only anaglyph/zoom offsets + rotations). Matrix readback semantics = reference Camera.MODEL_MATRIX. NOTE: the eye-height (-1.588) is VANILLA's intentional view translate for world-coord geometry; our camera-relative vertices get it applied TWICE (vertex already (v - camEye)) → world drawn ~1.6 blocks up. Optional cleanup: zero m03/m13/m23 in SodiumWorldRenderer.captureGlMatrices() (reference parity: rotation-only).
- `u_RegionOffset` = camera-relative in both ours and reference (DefaultChunkRenderer.setModelMatrixUniforms/getCameraTranslation) — fog distance = distance-from-camera, correct.
- `u_ProjectionMatrix` = vanilla terrain gluPerspective (not the sky's). Fog uniforms = vanilla (start 24, end 32, color (0.585,0.687,0.862)).
- Lightmap: enableLightmap() before each layer, disableLightmap() after — identical to vanilla renderBlockLayer.
- Draw state at probe time healthy (fbo=6, tex0=16/tex1=11, depthTest LEQUAL, cull back, colorMask 1111). No GL errors, no exceptions.

## ROOT-CAUSE CANDIDATE #2 — 6 FPS (~150-230 ms/frame) — frame timing from this run
- `frameInterval` 121-229 ms (fps 4.4-8.3); `frameDuration` (inside updateCameraAndRender) 92-105 ms; `worldSegment` (SOLID→TRANSLUCENT passes) 67-169 ms. Sodium phases (prepare/trees/update/upload) ALL 0 ms — the chunk CPU path is free.
- **The 100 ms/frame is inside the world segment but NOT in the sodium setup phases** — i.e., between the first and last block-layer pass: our 3-8 tiny draws (draws=5, idxBytes 3-6 KB), vanilla entities, selection boxes. Candidate sinks: driver sync on the shared index buffer / region VBO draws (glDrawElementsBaseVertex on GL 4.2 compat/Mesa HD4000), the per-layer enableLightmap/disableLightmap matrix pushes, or vanilla entity/selection cost (should be tiny at RD2).
- **One 5736 ms frame** coincided with `sectionBuilt` (-14,0,20) + `buildResults results=6 sections=6 uploads=6 totalUploadSize=737280` — a 737 KB region upload stalled a frame for **5.7 seconds** (previous run had a one-time 468 ms upload spike). The upload path (GlBufferArena.upload + persistent-mapped staging on ARB_buffer_storage) needs a dedicated probe: time arena.upload vs staging flush vs the vertex uploads — likely GL driver sync on orphan/alloc or the mapped staging buffer on Mesa.
- Next probe plan: (1) instrument the world segment interior (time renderLayer pass loop vs renderEntities vs selection in LevelRendererMixin/SodiumWorldRenderer); (2) time RenderRegionManager.uploadResults/arena.upload per frame; (3) check vsync/frame limiter state (GameSettings.limitFramerate) — 1366x768 windowed? (viewport 1366x768, scissor 800x600 from a menu).

## History condensed (why we are here)
- 06b5aa7 UI, db44842 fixes, d2752a1 NPE-fix diag, 7839e56 D4 probes. Prior confirmed fixes: glActiveTexture enum (OpenGlHelper.defaultTexUnit + textureId), weatherEffects entity loop, OptionsRowList ctor param, GL errors gone, head-interior gone. Remaining: invisible blocks + 6 FPS.
- Reference (parity source): `Radium-Reference/common/src/main/java` (radium fork of Sodium 0.8.15 for legacy-yarn 1.8.9). Upstream sodium clone at `/home/e/.cache/sf/sodium-upstream` (mc1.21.11-0.8.12).
- Build/install: codespace `ubiquitous-chainsaw-jxxg7j4rrpphp455`: `git fetch && git reset --hard origin/main && ./gradlew build --no-daemon`; pull via split -b 2M + per-chunk `gh codespace ssh -- cat` base64 with length checks; install = copy to `~/.minecraft/mods/radium-0.8.15.jar` (LOCAL machine — the game runs here, /home/e). Verify sha256 + zip entries + javap before install.
- After root-cause: remove Diag probes + `shadowJar { minimize() }` (target <=2.5 MB).
