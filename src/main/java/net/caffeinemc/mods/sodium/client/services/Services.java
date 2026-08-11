package net.caffeinemc.mods.sodium.client.services;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;

import java.util.ServiceLoader;

public class Services {
    // This code is used to load a service for the current environment. Your implementation of the service must be defined
    // manually by including a text file in META-INF/services named with the fully qualified class name of the service.
    // Inside the file you should write the fully qualified class name of the implementation to load for the platform.
    public static <T> T load(Class<T> clazz) {
        // ServiceLoader.findFirst() is Java 9+; iterate the loader for the first implementation on Java 8.
        ServiceLoader<T> loader = ServiceLoader.load(clazz);

        for (T loadedService : loader) {
            SodiumClientMod.logger().debug("Loaded {} for service {}", loadedService, clazz);
            return loadedService;
        }

        throw new NullPointerException("Failed to load service for " + clazz.getName());
    }
}
