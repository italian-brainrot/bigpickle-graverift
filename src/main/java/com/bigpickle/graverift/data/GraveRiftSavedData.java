package com.bigpickle.graverift.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
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
 *     <li>{@code activeRifts} – which rifts a player is currently bound to. This is the authority
 *     on "may this rift still pull this player back", which the rift entity itself cannot be: a
 *     rift whose chunk is unloaded is removed from the level (and restored later, from disk), so it
 *     cannot be told about a death that happened while it was gone. Keeping the answer here means
 *     a respawned player is never dragged back by a rift they no longer belong to, no matter how
 *     many rifts caught them or in which order.</li>
 * </ul>
 */
public class GraveRiftSavedData extends SavedData {
    public static final String DATA_NAME = "bigpickle_graverift";

    private final Map<UUID, DimensionalPos> lastDeaths = new HashMap<>();
    private final Map<UUID, DimensionalPos> pendingReturns = new HashMap<>();
    /** Player UUID -> every rift that currently holds a claim on them. */
    private final Map<UUID, Set<UUID>> activeRifts = new HashMap<>();
    /** Rift UUID -> game time it collapses at. Survives the rift's chunk unloading. */
    private final Map<UUID, Long> riftCloseTimes = new HashMap<>();

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

    /**
     * Binds a player to a rift. A player can be inside several rifts at once (the item does not
     * stop them from opening another one), so this is a set, not a single value.
     */
    public void addActiveRift(UUID player, UUID rift) {
        if (this.activeRifts.computeIfAbsent(player, key -> new HashSet<>()).add(rift)) {
            this.setDirty();
        }
    }

    /** Live copy: callers iterate this while the rift is un-binding members. */
    public Set<UUID> getActiveRifts(UUID player) {
        Set<UUID> rifts = this.activeRifts.get(player);
        return rifts == null ? Set.of() : Set.copyOf(rifts);
    }

    public boolean isActiveRift(UUID player, UUID rift) {
        Set<UUID> rifts = this.activeRifts.get(player);
        return rifts != null && rifts.contains(rift);
    }

    public void removeActiveRift(UUID player, UUID rift) {
        Set<UUID> rifts = this.activeRifts.get(player);
        if (rifts != null && rifts.remove(rift)) {
            if (rifts.isEmpty()) {
                this.activeRifts.remove(player);
            }
            this.setDirty();
        }
    }

    /** Drops every claim on a player. Used when they die, so no rift may pull them back. */
    public void clearActiveRifts(UUID player) {
        if (this.activeRifts.remove(player) != null) {
            this.setDirty();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Open rifts
    // ------------------------------------------------------------------------------------------

    /**
     * Records that a rift is open until the given game time.
     *
     * <p>This is the only trace of a rift that survives outside of its own chunk, which is what
     * lets the chunk ticket that keeps it ticking be validated after a restart (see
     * {@code GraveRiftMod#riftTickets}).</p>
     */
    public void registerRift(UUID rift, long closeTime) {
        this.riftCloseTimes.put(rift, closeTime);
        this.setDirty();
    }

    public void unregisterRift(UUID rift) {
        if (this.riftCloseTimes.remove(rift) != null) {
            this.setDirty();
        }
    }

    /** True when the rift is still worth keeping loaded, i.e. it is known and not yet expired. */
    public boolean isRiftStillOpen(UUID rift, long gameTime) {
        Long closeTime = this.riftCloseTimes.get(rift);
        return closeTime != null && closeTime > gameTime;
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
        this.activeRifts.forEach((player, rifts) -> {
            if (rifts.isEmpty()) {
                return;
            }
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            ListTag list = new ListTag();
            rifts.forEach(rift -> list.add(new IntArrayTag(UUIDUtil.uuidToIntArray(rift))));
            entry.put("Rifts", list);
            active.add(entry);
        });
        tag.put("ActiveRifts", active);

        ListTag rifts = new ListTag();
        this.riftCloseTimes.forEach((rift, closeTime) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", rift);
            entry.putLong("CloseTime", closeTime);
            rifts.add(entry);
        });
        tag.put("OpenRifts", rifts);

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
            UUID player = entry.getUUID("Player");
            Set<UUID> rifts = new HashSet<>();
            ListTag list = entry.getList("Rifts", Tag.TAG_INT_ARRAY);
            for (int j = 0; j < list.size(); j++) {
                rifts.add(UUIDUtil.uuidFromIntArray(list.getIntArray(j)));
            }
            if (entry.hasUUID("Rift")) {
                // Pre-1.1 saves only ever tracked the single most recent rift.
                rifts.add(entry.getUUID("Rift"));
            }
            if (!rifts.isEmpty()) {
                data.activeRifts.put(player, rifts);
            }
        }

        ListTag open = tag.getList("OpenRifts", Tag.TAG_COMPOUND);
        for (int i = 0; i < open.size(); i++) {
            CompoundTag entry = open.getCompound(i);
            data.riftCloseTimes.put(entry.getUUID("Id"), entry.getLong("CloseTime"));
        }

        return data;
    }
}
