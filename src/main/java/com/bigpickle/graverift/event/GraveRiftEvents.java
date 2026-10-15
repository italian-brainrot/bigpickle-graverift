package com.bigpickle.graverift.event;

import java.util.UUID;

import com.bigpickle.graverift.GraveRiftMod;
import com.bigpickle.graverift.data.DimensionalPos;
import com.bigpickle.graverift.data.GraveRiftSavedData;
import com.bigpickle.graverift.entity.GraveRiftEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Gameplay hooks. Everything is a plain event listener – no mixins, no patches – so this class is
 * safe to coexist with other mods.
 */
@EventBusSubscriber(modid = GraveRiftMod.MODID)
public final class GraveRiftEvents {

    private GraveRiftEvents() {
    }

    /**
     * Records where a player died and forgets every rift they were inside.
     *
     * <p>Forgetting is important: respawning creates a brand new {@link ServerPlayer} with the same
     * UUID, and the spec explicitly says a respawned player must not be dragged back by a rift they
     * "no longer" belong to. Every rift is un-bound, not just the most recent one, because a player
     * can walk through several rifts in a row and the last one to be entered is not the only one
     * holding a claim on them.</p>
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        GraveRiftSavedData data = GraveRiftSavedData.get(server);
        UUID playerId = player.getUUID();
        data.setLastDeath(playerId, DimensionalPos.of(player));
        data.clearPendingReturn(playerId);

        for (UUID riftId : data.getActiveRifts(playerId)) {
            GraveRiftEntity rift = findRift(server, riftId);
            if (rift != null) {
                // Clears the claim in the saved data as well.
                rift.removeMember(playerId);
            }
        }
        // A rift whose chunk is unloaded is not in the world right now and could not be told. Wipe
        // the claims here as well, otherwise that rift would still pull the respawned player back
        // the moment its chunk comes back.
        data.clearActiveRifts(playerId);
    }

    /**
     * Applies a pending return. This is how a player that logged out inside a rift that later closed
     * still gets sent back (and punished) once they return to the server.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        DimensionalPos pending = GraveRiftSavedData.get(server).takePendingReturn(player.getUUID());
        if (pending != null) {
            GraveRiftEntity.returnPlayer(player, pending);
        }
    }

    @Nullable
    private static GraveRiftEntity findRift(MinecraftServer server, UUID riftId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(riftId);
            if (entity instanceof GraveRiftEntity rift) {
                return rift;
            }
        }
        return null;
    }
}
