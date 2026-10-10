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
     * Records where a player died and forgets any rift they were inside.
     *
     * <p>Forgetting is important: respawning creates a brand new {@link ServerPlayer} with the same
     * UUID, and the spec explicitly says a respawned player must not be dragged back by a rift they
     * "no longer" belong to.</p>
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
        data.setLastDeath(player.getUUID(), DimensionalPos.of(player));
        data.clearPendingReturn(player.getUUID());

        UUID riftId = data.getActiveRift(player.getUUID());
        if (riftId != null) {
            GraveRiftEntity rift = findRift(server, riftId);
            if (rift != null) {
                rift.removeMember(player.getUUID());
            }
            data.clearActiveRift(player.getUUID());
        }
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
