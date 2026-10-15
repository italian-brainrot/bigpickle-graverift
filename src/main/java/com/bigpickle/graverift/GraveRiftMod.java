package com.bigpickle.graverift;

import com.bigpickle.graverift.config.GraveRiftConfig;
import com.bigpickle.graverift.data.GraveRiftSavedData;
import com.bigpickle.graverift.registry.ModCreativeTabs;
import com.bigpickle.graverift.registry.ModEntities;
import com.bigpickle.graverift.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
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

    /**
     * Keeps the chunk a rift lives in entity-ticking for as long as the rift is open.
     *
     * <p>This is not an optimisation, the mechanic depends on it. Entering a rift teleports the
     * player to their death location, which is routinely thousands of blocks away, and the whole
     * point of the rift is to keep running (charging its owner Hunger, warning its members, and
     * finally yanking them back) while they are over there. Without a chunk ticket the rift's chunk
     * unloads a second after the entry teleport, the entity is stripped out of the level and dumped
     * on disk, and the rift never counts down, warns or collapses at all.</p>
     *
     * <p>The validation callback runs while the levels are still being prepared, i.e. before a
     * single rift entity has been loaded back in, so it cannot ask "is this rift in the world yet".
     * It asks the saved data whether the rift is still worth holding a chunk for: a rift whose
     * lifetime has already run out (or that no longer exists at all, e.g. after a crash) must not
     * keep a chunk force-loaded forever.</p>
     */
    private static final TicketController RIFT_TICKETS = new TicketController(
            id("rift_chunks"),
            (level, ticketHelper) -> {
                GraveRiftSavedData rifts = GraveRiftSavedData.get(level.getServer());
                ticketHelper.getEntityTickets().forEach((owner, tickets) -> {
                    if (!rifts.isRiftStillOpen(owner, level.getGameTime())) {
                        ticketHelper.removeAllTickets(owner);
                    }
                });
            });

    public GraveRiftMod(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(this::registerTicketControllers);

        modContainer.registerConfig(ModConfig.Type.COMMON, GraveRiftConfig.SPEC);
    }

    private void registerTicketControllers(RegisterTicketControllersEvent event) {
        event.register(RIFT_TICKETS);
    }

    /** Convenience helper for creating resource locations inside this mod's namespace. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /** Exposed so rifts can force-load their own chunk for the length of their lifetime. */
    public static TicketController riftTickets() {
        return RIFT_TICKETS;
    }
}
