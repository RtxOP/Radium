package com.github.sonagg.radium;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.util.Map;

/**
 * Bootstrap Mixin for Forge 1.8.9. Forge <1.14 does not bundle Mixin,
 * so this class is registered via the JAR manifest ({@code FMLCorePlugin})
 * to be invoked by FML before any mixin classes are touched.
 *
 * <p>Reference: <a href="https://github.com/manuthebyte/template-forge-mixin-1.8.9">manuthebyte/template-forge-mixin-1.8.9</a>.
 *
 * <p>{@code @MCVersion} is read by FML 1.8.9's CoreModManager: a missing
 * annotation logs a WARN every boot; a value that does not equal
 * {@code FMLInjectionData.mccversion} ("1.8.9") makes FML IGNORE the coremod.
 * Both values must stay in sync when bumping the MC target.
 */
@IFMLLoadingPlugin.MCVersion("1.8.9")
@IFMLLoadingPlugin.Name("Radium-MixinLoader")
public class MixinLoader implements IFMLLoadingPlugin {

    /** Matches the manifest's {@code MixinConfigs} entry. */
    private static final String MIXIN_CONFIG = "radium-forge.mixins.json";

    public MixinLoader() {
        // Diagnostic: mirrors the canonical manuthebyte/template-forge-mixin-1.8.9
        // boot signal so a runClient log has an unambiguous "coremod loaded" line.
        System.out.println("[Radium] Injecting with IFMLLoadingPlugin.");

        // Mixin 0.7.11's LaunchWrapper service only excludes asm.service/,
        // asm.lib/, asm.mixin/ and asm.util/ from the LaunchClassLoader; the
        // asm.launch/ package is NOT excluded. Without this, the launch
        // classes get loaded a SECOND time by the LaunchClassLoader (from the
        // mod jar / bridge jar) while the bridge's TweakClass cascade already
        // booted them on the system classloader, producing a duplicate
        // MixinBootstrap static state and the benign-but-noisy
        // "Multiple Mixin containers present, init suppressed for 0.7.11"
        // warning. Excluding the package keeps exactly one copy (system).
        Launch.classLoader.addClassLoaderExclusion("org.spongepowered.asm.launch.");

        MixinBootstrap.init();
        Mixins.addConfiguration(MIXIN_CONFIG);
        MixinEnvironment.getDefaultEnvironment()
                .setSide(MixinEnvironment.Side.CLIENT);
    }

    @Override
    public String[] getASMTransformerClass() {
        // Do NOT register a transformer here (the canonical template returns
        // an empty array). Mixin 0.7.11 registers its own transformer,
        // org.spongepowered.asm.mixin.transformer.Proxy (a public facade over
        // the package-private MixinTransformer), during the tweak cascade via
        // MixinServiceLaunchWrapper.beginPhase(). Naming MixinTransformer here
        // instead makes FML 1.8.9 wrap it in a generated
        // ASMTransformerWrapper$<name> subclass, which cannot access the
        // package-private constructor and logs
        // "A critical problem occurred registering the ASM transformer class
        // $wrapper.org.spongepowered.asm.mixin.transformer.MixinTransformer"
        // every boot (harmless, but noise, and a second transformer instance).
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }
}
