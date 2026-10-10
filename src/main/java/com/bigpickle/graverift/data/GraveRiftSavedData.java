package com.bigpickle.graverift.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World level persistent data for the Grave Rift mod.
 *
 * <p>We deliberately keep all persistent state in a single {@link SavedData} attached to the
 * overworld instead of using player capabilities or static maps. That keeps the data tied to the
 * world save, survives server restarts, and does not leak between worlds.</p>
 *
 * <p>Three things are tracked:</p>
 * <ul>
 *     <li>{@code lastDeaths} – where each player last died, used as a rift destination.</li>
 *     <li>{@code pendingReturns} – players who were inside a rift when it closed while offline.
 *     They are sent back, with the return penalty, the next time they log in.</li>
 *     <li>{@code activeRifts} – which rift a player is currently inside. This lets us clean up
 *     membership when a player dies or respawns, which is required so that a respawned player is
 *     never dragged back by a rift they no longer belong to.</li>
 * </ul>
 */
public class GraveRiftSavedData extends SavedData {
    public static final String DATA_NAME = "bigpickle_graverift";

    private final Map<UUID, DimensionalPos> lastDeaths = new HashMap<>();
    private final Map<UUID, DimensionalPos> pendingReturns = new HashMap<>();
    private final Map<UUID, UUID> activeRifts = new HashMap<>();

    public static GraveRiftSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(GraveRiftSavedData::new, GraveRiftSavedData::load),
                DATA_NAME);
    }

    // ------------------------------------------------------------------------------------------
    // Last death
    // ------------------------------------------------------------------------------------------

    public void setLastDeath(UUID player, DimensionalPos pos) {
        this.lastDeaths.put(player, pos);
        this.setDirty();
    }

    public DimensionalPos getLastDeath(UUID player) {
        return this.lastDeaths.get(player);
    }

    // ------------------------------------------------------------------------------------------
    // Pending returns (players who logged out inside a rift that then closed)
    // ------------------------------------------------------------------------------------------

    public void setPendingReturn(UUID player, DimensionalPos pos) {
        this.pendingReturns.put(player, pos);
        this.setDirty();
    }

    /** Returns and removes the pending return for a player, if any. */
    public DimensionalPos takePendingReturn(UUID player) {
        DimensionalPos pos = this.pendingReturns.remove(player);
        if (pos != null) {
            this.setDirty();
        }
        return pos;
    }

    public void clearPendingReturn(UUID player) {
        if (this.pendingReturns.remove(player) != null) {
            this.setDirty();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Active rift membership
    // ------------------------------------------------------------------------------------------

    public void setActiveRift(UUID player, UUID rift) {
        this.activeRifts.put(player, rift);
        this.setDirty();
    }

    public UUID getActiveRift(UUID player) {
        return this.activeRifts.get(player);
    }

    public void clearActiveRift(UUID player) {
        if (this.activeRifts.remove(player) != null) {
            this.setDirty();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Serialization
    // ------------------------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag deaths = new ListTag();
        this.lastDeaths.forEach((uuid, pos) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", uuid);
            entry.put("Pos", pos.toNbt());
            deaths.add(entry);
        });
        tag.put("LastDeaths", deaths);

        ListTag pending = new ListTag();
        this.pendingReturns.forEach((uuid, pos) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", uuid);
            entry.put("Pos", pos.toNbt());
            pending.add(entry);
        });
        tag.put("PendingReturns", pending);

        ListTag active = new ListTag();
        this.activeRifts.forEach((player, rift) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putUUID("Rift", rift);
            active.add(entry);
        });
        tag.put("ActiveRifts", active);

        return tag;
    }

    public static GraveRiftSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        GraveRiftSavedData data = new GraveRiftSavedData();

        ListTag deaths = tag.getList("LastDeaths", Tag.TAG_COMPOUND);
        for (int i = 0; i < deaths.size(); i++) {
            CompoundTag entry = deaths.getCompound(i);
            data.lastDeaths.put(entry.getUUID("Id"), DimensionalPos.fromNbt(entry.getCompound("Pos")));
        }

        ListTag pending = tag.getList("PendingReturns", Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag entry = pending.getCompound(i);
            data.pendingReturns.put(entry.getUUID("Id"), DimensionalPos.fromNbt(entry.getCompound("Pos")));
        }

        ListTag active = tag.getList("ActiveRifts", Tag.TAG_COMPOUND);
        for (int i = 0; i < active.size(); i++) {
            CompoundTag entry = active.getCompound(i);
            data.activeRifts.put(entry.getUUID("Player"), entry.getUUID("Rift"));
        }

        return data;
    }
}
