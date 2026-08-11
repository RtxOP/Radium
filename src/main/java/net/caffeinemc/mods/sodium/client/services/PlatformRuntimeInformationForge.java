package net.caffeinemc.mods.sodium.client.services;

import net.minecraft.launchwrapper.Launch;

import java.io.File;
import java.nio.file.Path;

/**
 * Forge 1.8.9 implementation of the platform runtime information service.
 *
 * <p>Registered via {@code META-INF/services/net.caffeinemc.mods.sodium.client.services.PlatformRuntimeInformation}
 * so that {@link Services#load} picks it up (the ServiceLoader mechanism the reference
 * uses, with the Fabric implementation swapped for a Forge one).</p>
 *
 * <p>The game/configuration directories resolve from the launchwrapper
 * {@code Launch.minecraftHome} (the same source Forge's own config code uses in 1.8.9),
 * and development-environment detection reads FML's {@code fml.deobfuscatedEnvironment}
 * blackboard flag, which is only {@code true} in deobfuscated (Gradle/IDE) launches.</p>
 */
public class PlatformRuntimeInformationForge implements PlatformRuntimeInformation {
    private static File getMinecraftHome() {
        File home = Launch.minecraftHome;
        return home != null ? home : new File(".");
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return Boolean.TRUE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment"));
    }

    @Override
    public Path getGameDirectory() {
        return getMinecraftHome().toPath();
    }

    @Override
    public Path getConfigDirectory() {
        return getMinecraftHome().toPath().resolve("config");
    }
}
