export const meta = {
  name: 'sodiumforge-port-assessment',
  description: 'Assess SodiumForge (Sodium/Radium Fabric->Forge 1.8.9 port) progress: online research on Forge/Fabric 1.8.9 modding + deep codebase review',
  phases: [
    { title: 'Research', detail: 'Forge 1.8.9 Mixin setup, Radium/Fabric 1.8.9 reference, MCP mappings + Gradle compat' },
    { title: 'Codebase', detail: 'per-mixin target validation, build-system correctness, ported-class stub audit' },
  ],
}

const REPO = '/home/e/dev/SodiumForge'

const RESEARCH_SCHEMA = {
  type: 'object',
  properties: {
    findings: { type: 'array', items: { type: 'string' } },
    recommendations: { type: 'array', items: { type: 'string' } },
    sources: { type: 'array', items: { type: 'string' } },
  },
  required: ['findings', 'recommendations', 'sources'],
}

const MIXIN_SCHEMA = {
  type: 'object',
  properties: {
    mixins: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          mixin: { type: 'string' },
          targetClass: { type: 'string' },
          targetExistsInForge189: { type: 'boolean' },
          compileErrors: { type: 'string' },
          injectedMethodOrFieldExists: { type: 'string', enum: ['yes', 'no', 'unverified'] },
          portStatus: { type: 'string', enum: ['faithful', 'modified', 'stub', 'broken'] },
          verdict: { type: 'string' },
        },
        required: ['mixin', 'targetClass', 'targetExistsInForge189', 'portStatus', 'verdict'],
      },
    },
  },
  required: ['mixins'],
}

const BUILD_SCHEMA = {
  type: 'object',
  properties: {
    buildBlockers: { type: 'array', items: { type: 'string' } },
    runtimeBlockers: { type: 'array', items: { type: 'string' } },
    risks: { type: 'array', items: { type: 'string' } },
    deadOrInertCode: { type: 'array', items: { type: 'string' } },
    mappingCorrectness: { type: 'string' },
    overallBuildStatus: { type: 'string', enum: ['likely-builds', 'likely-compile-errors', 'cannot-tell', 'build-broken-at-plugin-resolution'] },
  },
  required: ['buildBlockers', 'runtimeBlockers', 'overallBuildStatus'],
}

const PORTED_SCHEMA = {
  type: 'object',
  properties: {
    files: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          path: { type: 'string' },
          status: { type: 'string', enum: ['faithful', 'modified', 'stub', 'broken'] },
          notes: { type: 'string' },
        },
        required: ['path', 'status'],
      },
    },
    overallProgress: { type: 'string' },
  },
  required: ['files', 'overallProgress'],
}

phase('Research')

const research = await parallel([
  () => agent(`You are researching how to correctly run the Mixin library on **Minecraft Forge 1.8.9** (the ONLY version under discussion - do not drift to modern MC/Forge).
Consult authoritative sources online (search + fetch): the classic "forge mixin 1.8.9" templates (e.g. manuthebyte/template-forge-mixin-1.8.9, SpongePowered Mixin wiki, MixinBootstrap docs, Forge 1.8.9 FMLCorePlugin/TweakClass documentation, old MinecraftForge forum posts).
This project shades Mixin 0.7.11-SNAPSHOT into its jar and declares its mixin config (radium-forge.mixins.json) with "minVersion": "0.8" and "compatibilityLevel": "JAVA_8". The manifest uses FMLCorePlugin=com.github.sonagg.radium.MixinLoader, TweakClass=org.spongepowered.asm.launch.MixinTweaker, TweakOrder=0.
Answer precisely:
1) Which Mixin version is correct/canonical for Forge 1.8.9 (0.7.11? 0.8.x? does 0.8 run on a Java 8 runtime)? Would Mixin 0.7.11 reject a config whose minVersion is "0.8" (i.e. is minVersion greater than runtime version fatal when required:true)? Is "minVersion":"0.8" + shaded 0.7.11 a real runtime bug?
2) Does the FMLCorePlugin + TweakClass + MixinBootstrap approach in the repo's MixinLoader.java work on 1.8.9, or is a different pattern (MixinBootstrap.init + Mixins.addConfiguration vs TweakClass only) required?
3) Any known issues running Mixin on Forge 1.8.9 specifically (e.g. it must be shaded, launchwrapper interplay, getASMTransformerClass requirements, side setting).
Return concrete, version-specific findings with URLs. Do not speculate where you cannot verify; say 'unverified'.`, { label: 'research:forge-mixin', phase: 'Research', schema: RESEARCH_SCHEMA }),

  () => agent(`You are researching the **Radium mod** (a port of Sodium to Minecraft 1.8.9) as the reference implementation for a Fabric->Forge port. The local copy is at ${REPO}/Radium-Reference (a Fabric 1.8.9 layout: common/ + fabric/ source sets).
Read these files to ground yourself:
- ${REPO}/Radium-Reference/fabric/src/main/resources/fabric.mod.json
- ${REPO}/Radium-Reference/common/src/main/resources/radium.mixins.json  (note: "compatibilityLevel": "JAVA_17", minVersion "0.8")
- ${REPO}/Radium-Reference/common/src/main/java/gg/sona/radium/mixin/config/AbstractCaffeineConfigMixinPlugin.java  (note package prefix "dev.vexor.radium.mixin.sodium")
- ${REPO}/Radium-Reference/common/src/main/java/net/caffeinemc/mods/sodium/client/services/ (Services.java, PlatformMixinOverrides.java, PlatformRuntimeInformation.java)
- ${REPO}/Radium-Reference/fabric/src/main/resources/sodium-fabric.mixins.json
Then search online for the real Radium GitHub repos (github.com/sonagg/radium or the original vexor/radium fork) and its docs/README to learn: what Minecraft version(s) does Radium target, is it Fabric 1.8.9, what Fabric Loader/Mixin does it need, how is the Fabric 1.8.9 bootstrap (entrypoint, Services loader, CaffeineConfigPlatform via ServiceLoader, accesswidener usage) wired.
Answer:
1) How does the reference bootstrap on Fabric 1.8.9 (which classes register mixin options, who implements CaffeineConfigPlatform / PlatformMixinOverrides / PlatformRuntimeInformation)?
2) Does the Fabric 1.8.9 reference actually run the modern sodium (JAVA_17) common code, or did it already re-map to 1.8.9 names? What does the reference's net/ code look like - does it use 1.8.9 MCP class names or Fabric/Yarn names?
3) What is the correct Fabric 1.8.9 mixin setup (Mixin version, fabric.mod.json mixin field, accessWidener)?
4) The reference plugin checks mixinClassName.startsWith("dev.vexor.radium.mixin.sodium") but the config package is "gg.sona.radium.mixin". Confirm whether that prefix mismatch is ALSO present in the reference file (i.e. pre-existing bug) or introduced by the port.
Return concrete findings with file paths + URLs. Mark unverified items as 'unverified'.`, { label: 'research:radium-fabric', phase: 'Research', schema: RESEARCH_SCHEMA }),

  () => agent(`You are researching MCP/SRG mappings and ForgeGradle compatibility for **Minecraft Forge 1.8.9 ONLY** (do not drift to newer versions).
Context: this project (repo ${REPO}) uses ForgeGradle 2.1-SNAPSHOT with mappings 'stable_22', Gradle wrapper 3.1, MixinGradle 'org.spongepowered.mixin' 0.6-SNAPSHOT, Shadow plugin 2.0.4, and reobf of a shadowJar with a declared refmap. Recent commits: "Switch MCP mappings stable_20 -> stable_22 (correct fix for MC 1.8.9)".
Search online (e.g. MCP/ForgeGradle docs, the ForgeGradle changelogs, GitHub issues, wiki.vg mappings pages, the MCP mapping list on botrepo/mcpl, old Forge 1.8.9 tutorials) to verify:
1) Is stable_22 the correct MCP mapping for 1.8.9 (vs stable_20 for 1.8.8)? What are the notable class/field name differences between stable_20 and stable_22 relevant to client rendering classes (RenderGlobal, EntityRenderer, WorldClient, Chunk, etc.)?
2) ForgeGradle 2.1 compatibility with Gradle versions: does it work on Gradle 3.1? Gradle 4.x? Gradle 6.9.4? Gradle 9.x? What Java does ForgeGradle 2.1 require (Java 8)? (Note: this project's wrapper is 3.1 but .gradle/9.4.0 dir exists.)
3) MixinGradle 0.6-SNAPSHOT requirements: which Gradle version does it require (5.0+?)? Does it run on Gradle 3.1? Does MixinGradle 0.6's refmap generation integrate with ForgeGradle 2.1 reobf on 1.8.9, or is that broken (classic problem)?
4) On Forge 1.8.9, when a mixin's @Overwrite/@Inject targets a method by name, do the names need to be MCP (stable_22) names or SRG names, and what role does the refmap play (with TweakClass vs MixinBootstrap)?
Return concrete, version-specific answers with URLs. Mark unverified items as 'unverified'.`, { label: 'research:mappings-gradle', phase: 'Research', schema: RESEARCH_SCHEMA }),
])

phase('Codebase')

const code = await parallel([
  () => agent(`You are auditing mixin porting correctness for a Sodium/Radium Fabric->Forge 1.8.9 port.
There are exactly these mixin source files under ${REPO}/src/main/java/gg/sona/radium/mixin/. For EACH of the 26 files listed below, read the file AND its reference twin under ${REPO}/Radium-Reference/common/src/main/java/gg/sona/radium/mixin/ (same relative path), then determine:
(a) the mixin target class (@Mixin annotation) and whether that class exists in Minecraft 1.8.9 **Forge** (MCP 1.8.9 class names - e.g. RenderGlobal, EntityRenderer, GuiVideoSettings, EnumFacing, BufferBuilder, WorldClient, ChunkCache, GuiNewChat, TileEntityRendererDispatcher are correct 1.8.9 names; modern names like RenderLayer, GameRenderer, Screen, Direction, BlockEntityRenderDispatcher are NOT).
(b) whether the mixin body contains symbols that DO NOT exist in 1.8.9 (modern-Yarn/Fabric names: Camera, Identifier, BlockPos.Mutable, GameOptions, textRenderer, getLoadedEntities, method_6915, hasVehicle, shouldRender, getCameraPosVec, BlockEntityRenderDispatcher, SheetedDecalTextureGenerator, etc.) - i.e. likely compile errors.
(c) whether @Inject/@Overwrite/@Redirect method/field targets exist in 1.8.9 with that name/signature. You may use web search to confirm MCP 1.8.9 signatures (e.g. BufferBuilder.sortQuads, EnumFacing.getFacing, RenderGlobal.setWorldAndLoadRenderers, EntityRenderer.updateCameraAndRender, GuiVideoSettings.actionPerformed, Minecraft.getMinecraft).
Files to review (relative to src/main/java):
gg/sona/radium/mixin/config/AbstractCaffeineConfigMixinPlugin.java
gg/sona/radium/mixin/core/MixinBaseFrustum.java
gg/sona/radium/mixin/core/MixinBiome.java
gg/sona/radium/mixin/core/MixinBufferBuilder.java
gg/sona/radium/mixin/core/MixinCullingCameraView.java
gg/sona/radium/mixin/core/MixinFramebuffer.java
gg/sona/radium/mixin/sodium/core/MinecraftMixin.java
gg/sona/radium/mixin/sodium/core/access/ABlockRenderManager.java
gg/sona/radium/mixin/sodium/core/access/AChunk.java
gg/sona/radium/mixin/sodium/core/access/AFluidRenderer.java
gg/sona/radium/mixin/sodium/core/access/AGameRenderer.java
gg/sona/radium/mixin/sodium/core/render/VertexFormatMixin.java
gg/sona/radium/mixin/sodium/core/render/world/LevelRendererMixin.java
gg/sona/radium/mixin/sodium/core/world/biome/ClientLevelMixin.java
gg/sona/radium/mixin/sodium/core/world/map/ClientChunkCacheMixin.java
gg/sona/radium/mixin/sodium/core/world/map/ClientLevelMixin.java
gg/sona/radium/mixin/sodium/features/gui/hooks/console/GameRendererMixin.java
gg/sona/radium/mixin/sodium/features/gui/hooks/debug/DebugScreenOverlayMixin.java
gg/sona/radium/mixin/sodium/features/gui/hooks/settings/OptionsScreenMixin.java
gg/sona/radium/mixin/sodium/features/options/GameOptionsMixin.java
gg/sona/radium/mixin/sodium/features/options/MinecraftClientMixin.java
gg/sona/radium/mixin/sodium/features/options/overlays/GuiMixin.java
gg/sona/radium/mixin/sodium/features/options/render_layers/LeavesBlockMixin.java
gg/sona/radium/mixin/sodium/features/options/weather/LevelRendererMixin.java
gg/sona/radium/mixin/sodium/features/options/world/DimensionMixin.java
gg/sona/radium/mixin/sodium/features/render/immediate/DirectionMixin.java
gg/sona/radium/mixin/sodium/features/textures/tracking/SpriteMixin.java
Also read the mixin config ${REPO}/src/main/resources/radium-forge.mixins.json and note whether each listed mixin class actually exists (check all 26 + the config entry 'sodium.core.access.AChunk').
Produce ONE row per mixin. Be precise and evidence-based: quote the offending symbol with its file:line. If you cannot verify a method exists in 1.8.9, mark injectedMethodOrFieldExists 'unverified'. Do NOT speculate broadly.`, { label: 'review:mixins', phase: 'Codebase', schema: MIXIN_SCHEMA }),

  () => agent(`You are auditing the **build system and runtime bootstrapping** of a Sodium->Forge 1.8.9 port at ${REPO}. Read these files completely:
- ${REPO}/build.gradle
- ${REPO}/gradle.properties
- ${REPO}/gradle/wrapper/gradle-wrapper.properties
- ${REPO}/settings.gradle
- ${REPO}/src/main/java/com/github/sonagg/radium/MixinLoader.java
- ${REPO}/src/main/java/com/github/sonagg/radium/RadiumForgeMod.java
- ${REPO}/src/main/resources/radium-forge.mixins.json
- ${REPO}/src/main/resources/mcmod.info
- ${REPO}/src/main/resources/sodium-common.accesswidener
- ${REPO}/src/main/java/net/minecraft/client/renderer/FluidRendererStub.java and BlockRendererDispatcherStub.java
- ${REPO}/src/main/java/gg/sona/radium/mixin/config/AbstractCaffeineConfigMixinPlugin.java and CaffeineConfig.java and CaffeineConfigPlatform.java
- List ${REPO}/src/main/resources/META-INF/ (does a services file for CaffeineConfigPlatform exist? any *_at.cfg access transformers?)
Check for and report as concrete findings (with file:line):
1) Version contradictions: shaded mixin is 'org.spongepowered:mixin:0.7.11-SNAPSHOT' but radium-forge.mixins.json declares "minVersion":"0.8". Is this fatal at runtime? Also compatibilityLevel JAVA_8 vs reference JAVA_17 - fine or not.
2) Gradle/plugin version contradictions: wrapper Gradle 3.1 vs MixinGradle 0.6-SNAPSHOT (which needs Gradle 5+) and Shadow 2.0.4 (needs Gradle >=3.0). Would 'apply plugin: org.spongepowered.mixin' fail on Gradle 3.1 at plugin resolution time? Note .gradle/9.4.0 exists in the project dir.
3) The CaffeineConfig static field PLATFORM = ServiceLoader.load(CaffeineConfigPlatform.class).findFirst().get() - is there a META-INF/services registration anywhere in src? If not, loading CaffeineConfig throws NoSuchElementException. Also is AbstractCaffeineConfigMixinPlugin ever registered (config has a "plugin" key?) - if not, the option gating is inert.
4) shouldApplyMixin() in AbstractCaffeineConfigMixinPlugin checks startsWith("dev.vexor.radium.mixin.sodium") but the real package is gg.sona.radium.mixin.sodium - so it never matches and always falls through to 'return true'. Confirm.
5) sodium-common.accesswidener is a Fabric-only artifact - is it referenced anywhere? (ATs on Forge are _at.cfg + META-INF; processResources renames '(.+_at\\.cfg)' to META-INF/$1 - do any _at.cfg files exist?)
6) The two stub classes net.minecraft.client.renderer.FluidRendererStub/BlockRendererDispatcherStub are bundled in the mod jar with package net.minecraft.client.renderer - is that a valid strategy on Forge 1.8.9 (classes shadowing MC packages in a mod jar)? Will the AFluidRenderer/ABlockRenderManager accessors ever be applied to real game objects? What runtime effect does that have?
7) mcmod.info correctness for Forge 1.8.9 (modid radium, FMLCorePlugin manifest attrs) and the manifest attributes (FMLCorePluginContainsFMLMod, TweakOrder, ModSide CLIENT, ForceLoadAsMod).
8) Anything that would prevent a clean 'gradlew build' from producing a runnable jar on Forge 1.8.9.
Produce a precise, prioritized list.`, { label: 'review:build', phase: 'Codebase', schema: BUILD_SCHEMA }),

  () => agent(`You are auditing which Sodium/Radium runtime classes have been ported and whether they are functional or stubs, for the Fabric->Forge 1.8.9 port at ${REPO}.
The reference has ~391 files under ${REPO}/Radium-Reference/common/src/main/java/net/caffeinemc/mods/sodium/. The port so far only has these files (all under ${REPO}/src/main/java/):
api/vertex/format/VertexFormatExtensions.java
api/vertex/format/VertexFormatRegistry.java
client/config/ConfigManager.java
client/data/config/MixinConfig.java
client/gl/device/RenderDevice.java
client/gui/console/ConsoleHooks.java
client/gui/console/FPSCounter.java
client/gui/SodiumOptions.java
client/gui/VideoSettingsScreen.java
client/render/chunk/ChunkRenderMatrices.java
client/render/chunk/map/ChunkStatus.java
client/render/chunk/map/ChunkTrackerHolder.java
client/render/chunk/map/ChunkTracker.java
client/render/SodiumWorldRenderer.java
client/render/texture/SpriteExtension.java
client/render/viewport/frustum/SimpleFrustum.java
client/render/viewport/Viewport.java
client/SodiumClientMod.java
client/util/frustum/ExtendedFrustum.java
client/util/MathUtil.java
client/util/NativeBuffer.java
client/world/BiomeSeedProvider.java
client/world/LevelRendererExtension.java
For EACH of those files: read it, then read its reference twin (same path under Radium-Reference/common/src/main/java/net/caffeinemc/mods/sodium/) when it exists, and classify status:
- faithful = a working port of the reference logic
- modified = partially ported / behavior changed
- stub = explicitly a placeholder/no-op (e.g. "Stub:" in javadoc, empty methods, throws UnsupportedOperationException)
- broken = has compile errors or references non-existent 1.8.9 classes
Also: identify the minimum set of REFERENCE classes that are NOT yet ported but that the ported mixins actually require at runtime (look at what the mixins in ${REPO}/src/main/java/gg/sona/radium/mixin/ import from net.caffeinemc.mods.sodium.client... and cross-check which of those imports do NOT exist in the port's net/ tree). E.g. SodiumWorldRenderer is referenced by LevelRendererMixin; RenderDevice by LevelRendererMixin; etc. List the specific missing imports.
Note especially: which files are 'stub' per their javadoc (SodiumClientMod, SodiumWorldRenderer, ConfigManager, MixinConfig). And whether VideoSettingsScreen/SodiumOptions/ConsoleHooks/FPSCounter are functional or broken.
Produce per-file rows plus an overall progress estimate.`, { label: 'review:ported', phase: 'Codebase', schema: PORTED_SCHEMA }),
])

return { research, code }
