package net.caffeinemc.mods.sodium.client.services;

import java.util.Collections;
import java.util.List;

/**
 * Forge 1.8.9 implementation of the platform mixin override service.
 *
 * <p>The reference (Fabric) implementation enumerates a config file of overridable mixin rules. Forge 1.8.9 has
 * no equivalent config surface in this port's target scope (GUI/config handling is deferred), so the Forge
 * implementation returns no overrides. Registered via
 * {@code META-INF/services/net.caffeinemc.mods.sodium.client.services.PlatformMixinOverrides}.</p>
 */
public class PlatformMixinOverridesForge implements PlatformMixinOverrides {
    @Override
    public List<MixinOverride> applyModOverrides() {
        return Collections.emptyList();
    }
}
