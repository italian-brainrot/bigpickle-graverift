package com.bigpickle.graverift;

import com.bigpickle.graverift.config.GraveRiftConfig;
import com.bigpickle.graverift.registry.ModCreativeTabs;
import com.bigpickle.graverift.registry.ModEntities;
import com.bigpickle.graverift.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Entry point for the Grave Rift mod.
 *
 * <p>The mod is intentionally small and non-invasive: it only uses public NeoForge APIs, data
 * driven recipes, and a self contained entity + client renderer. It does not use mixins, access
 * transformers, or patch any vanilla behaviour, so it is safe to drop into large mod packs.</p>
 */
@Mod(GraveRiftMod.MODID)
public class GraveRiftMod {
    public static final String MODID = "bigpickle_graverift";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GraveRiftMod(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, GraveRiftConfig.SPEC);
    }

    /** Convenience helper for creating resource locations inside this mod's namespace. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
