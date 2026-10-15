package com.bigpickle.graverift.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.bigpickle.graverift.GraveRiftMod;
import com.bigpickle.graverift.config.GraveRiftConfig;
import com.bigpickle.graverift.data.DimensionalPos;
import com.bigpickle.graverift.data.GraveRiftSavedData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A temporary tear in reality that links to a player's last death location.
 *
 * <p>Lifecycle:</p>
 * <ol>
 *     <li>Opened by {@link com.bigpickle.graverift.item.GraveRiftItem} at a creature's position.</li>
 *     <li>While open, the player that opened it suffers a strong Hunger effect – the cost of holding
 *     the way open.</li>
 *     <li>Players that step inside are teleported to the destination, lose their experience, and get
 *     a short burst of protective effects so the arrival is survivable.</li>
 *     <li>Ten seconds before it collapses, everyone inside is warned with Nausea.</li>
 *     <li>When it collapses, everyone that entered it is pulled back, starved to zero food, and
 *     weakened for ten minutes.</li>
 * </ol>
 *
 * <p>All of the gameplay logic runs server side. The client only needs a synced lifetime value for
 * the renderer, so the class stays cheap to tick and trivial to render.</p>
 */
public class GraveRiftEntity extends Entity {
    /** Ticks before the rift collapses during which members are warned. */
    public static final int WARNING_TICKS = 200;

    private static final EntityDataAccessor<Integer> DATA_LIFETIME =
            SynchedEntityData.defineId(GraveRiftEntity.class, EntityDataSerializers.INT);

    /**
     * Absolute {@link Level#getGameTime()} at which the rift collapses.
     *
     * <p>This is deliberately a timestamp and not a tick counter. A rift stops ticking as soon as
     * its chunk stops entity-ticking, which happens as soon as the player walks (or is teleported)
     * away from it – Minecraft removes the entity from the level and writes it to disk, then
     * restores it verbatim when the chunk comes back. A tick counter would therefore freeze for as
     * long as nobody was nearby and a rift could be walked into minutes after it was opened. Game
     * time keeps advancing while the rift is unloaded, so the rift always collapses exactly
     * {@code riftLifetimeTicks} after it was torn open.</p>
     */
    private long closeTime;
    /** Player that opened the rift and is paying the Hunger cost. */
    @Nullable
    private UUID riftOwner;
    /** Where the rift sends anyone that enters it. */
    @Nullable
    private DimensionalPos destination;
    /** Whether the "it's about to close" warning has been delivered. */
    private boolean warned;
    /** Guards against re-entering the close routine. */
    private boolean closing;

    /** UUID -> position to return the player to when the rift closes. */
    private final Map<UUID, DimensionalPos> members = new HashMap<>();

    /** Chunk this rift is currently holding a loading ticket for, or null. */
    @Nullable
    private ChunkPos heldChunk;

    public GraveRiftEntity(EntityType<? extends GraveRiftEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
        this.closeTime = level.getGameTime() + GraveRiftConfig.RIFT_LIFETIME_TICKS.get();
    }

    // ------------------------------------------------------------------------------------------
    // Chunk loading
    // ------------------------------------------------------------------------------------------

    /**
     * Claims a loading ticket for this rift's chunk the moment the rift joins the level.
     *
     * <p>A rift has to keep ticking while its owner is on the other side of the world, otherwise
     * the mechanic is impossible: the entry teleport routinely moves the player thousands of blocks
     * away, which unloads the rift's chunk, which removes the rift entity from the level entirely
     * (it is written to disk and only restored if the player ever walks back). A frozen rift never
     * expires, never warns and never pulls anybody back. Holding the chunk keeps the entity ticking
     * for the whole lifetime; {@link #close(MinecraftServer)} and {@link #onRemovedFromLevel()} give
     * the ticket back.</p>
     */
    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        this.acquireChunkTicket();
    }

    private void acquireChunkTicket() {
        if (this.heldChunk != null || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        ChunkPos chunk = new ChunkPos(this.blockPosition());
        GraveRiftSavedData.get(level.getServer()).registerRift(this.getUUID(), this.closeTime);
        GraveRiftMod.riftTickets().forceChunk(level, this.getUUID(), chunk.x, chunk.z, true, true);
        this.heldChunk = chunk;
    }

    private void releaseChunkTicket() {
        ChunkPos chunk = this.heldChunk;
        this.heldChunk = null;
        if (chunk != null && this.level() instanceof ServerLevel level) {
            GraveRiftMod.riftTickets().forceChunk(level, this.getUUID(), chunk.x, chunk.z, false, true);
            GraveRiftSavedData.get(level.getServer()).unregisterRift(this.getUUID());
        }
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        // Whatever brought the rift down, the chunk must not stay pinned for it. Member claims are
        // deliberately left alone: a chunk unload only means "not right now", not "gone", and the
        // saved claims are what stop a rift from yanking a player who died while it was away.
        this.releaseChunkTicket();
    }

    // ------------------------------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            // The client only needs to keep the synced lifetime fresh for interpolation.
            return;
        }

        int remaining = this.getRemainingTicks();
        if (remaining != this.entityData.get(DATA_LIFETIME)) {
            this.entityData.set(DATA_LIFETIME, remaining);
        }

        MinecraftServer server = this.getServer();
        if (server == null) {
            this.discard();
            return;
        }

        if (!this.warned && remaining <= WARNING_TICKS) {
            this.warned = true;
            warnMembers(server);
        }

        // Collapse before anything else: a rift that outlived its lifetime because its chunk was
        // unloaded must never catch anybody on the way back in.
        if (remaining <= 0) {
            close(server);
            return;
        }

        applyOwnerHunger(server);
        spawnAmbientEffects();
        detectEntries();
    }

    /** Ticks left before this rift collapses, based on game time rather than on ticks elapsed. */
    private int getRemainingTicks() {
        long remaining = this.closeTime - this.level().getGameTime();
        if (remaining <= 0L) {
            return 0;
        }
        return (int) Math.min(remaining, Integer.MAX_VALUE);
    }

    private void applyOwnerHunger(MinecraftServer server) {
        if (this.riftOwner == null || this.tickCount % 20 != 0) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(this.riftOwner);
        if (owner != null && owner.isAlive()) {
            owner.addEffect(new MobEffectInstance(
                    MobEffects.HUNGER,
                    45,
                    GraveRiftConfig.HUNGER_AMPLIFIER.get(),
                    false,
                    false,
                    true));
        }
    }

    private void spawnAmbientEffects() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        double centerY = this.getY() + this.getBbHeight() * 0.5D;
        if (this.tickCount % 2 == 0) {
            level.sendParticles(ParticleTypes.SOUL,
                    this.getX(), centerY, this.getZ(),
                    2, 0.55D, 0.8D, 0.55D, 0.004D);
        }
        if (this.tickCount % 5 == 0) {
            level.sendParticles(ParticleTypes.SCULK_SOUL,
                    this.getX(), this.getY() + 0.25D, this.getZ(),
                    1, 0.45D, 0.35D, 0.45D, 0.0D);
        }
        if (this.tickCount % 60 == 0 && this.tickCount > 0) {
            level.playSound(null, this.getX(), centerY, this.getZ(),
                    SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS,
                    0.55F, 0.4F + this.random.nextFloat() * 0.2F);
        }
    }

    private void detectEntries() {
        AABB area = this.getBoundingBox().inflate(0.75D);
        for (ServerPlayer player : this.level().getEntitiesOfClass(ServerPlayer.class, area)) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            if (!player.getBoundingBox().inflate(0.25D).intersects(area)) {
                continue;
            }
            // Members are never re-entered: this makes walking back through your own rift, or
            // spawning inside it after a death elsewhere, a harmless no-op.
            if (!this.members.containsKey(player.getUUID())) {
                enter(player);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Entry / warning / close
    // ------------------------------------------------------------------------------------------

    private void enter(ServerPlayer player) {
        DimensionalPos returnPos = DimensionalPos.of(player);
        this.members.put(player.getUUID(), returnPos);

        MinecraftServer server = this.getServer();
        if (server != null) {
            // The rift only ever *adds* a claim here. Later rifts do not replace it, and dying
            // removes the claim from every rift at once, so no rift can yank this player back.
            GraveRiftSavedData.get(server).addActiveRift(player.getUUID(), this.getUUID());
        }

        if (this.destination != null && server != null) {
            ServerLevel dest = server.getLevel(this.destination.dimension());
            if (dest != null) {
                player.teleportTo(dest,
                        this.destination.x(), this.destination.y(), this.destination.z(),
                        player.getYRot(), player.getXRot());
            }
        }

        if (GraveRiftConfig.REMOVE_EXPERIENCE.get()) {
            player.setExperienceLevels(0);
            player.setExperiencePoints(0);
            player.totalExperience = 0;
        }

        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 200, 13, false, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 10, false, true, false));

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    private void warnMembers(MinecraftServer server) {
        // Cover the whole remaining lifetime plus a one-second safety margin so the warning is
        // still ticking when the rift collapses; returnPlayer clears Nausea on arrival, so the
        // buffer never lingers after the player is pulled back.
        int duration = this.getRemainingTicks() + 20;
        GraveRiftSavedData data = GraveRiftSavedData.get(server);
        for (UUID id : this.members.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && player.isAlive() && data.isActiveRift(id, this.getUUID())) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration, 0, false, true, true));
            }
        }
    }

    private void close(MinecraftServer server) {
        if (this.closing) {
            return;
        }
        this.closing = true;

        GraveRiftSavedData data = GraveRiftSavedData.get(server);
        for (Map.Entry<UUID, DimensionalPos> entry : this.members.entrySet()) {
            UUID playerId = entry.getKey();
            // The saved data, not this map, decides whether the claim is still live. If the player
            // died (or was otherwise detached) while this rift was unloaded it could not have been
            // told, and must not be dragged back to a body they no longer have.
            if (!data.isActiveRift(playerId, this.getUUID())) {
                continue;
            }
            data.removeActiveRift(playerId, this.getUUID());

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                // The player is offline. Remember to send them back the next time they log in.
                data.setPendingReturn(playerId, entry.getValue());
            } else if (player.isAlive()) {
                returnPlayer(player, entry.getValue());
            }
            // Online but dead: their claim was already dropped when they died, so there is nothing
            // to do for them (and nothing to remember for their next login).
        }
        this.members.clear();

        if (this.level() instanceof ServerLevel level) {
            level.playSound(null, this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ(),
                    SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, 0.8F, 0.45F);
        }
        this.releaseChunkTicket();
        this.discard();
    }

    /**
     * Applies the "pulled back" penalty and puts the player at the stored return position. Also used
     * when a pending return is applied after a login.
     */
    public static void returnPlayer(ServerPlayer player, DimensionalPos pos) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            ServerLevel dest = server.getLevel(pos.dimension());
            if (dest != null) {
                player.teleportTo(dest, pos.x(), pos.y(), pos.z(), pos.yRot(), pos.xRot());
            }
        }

        player.getFoodData().setFoodLevel(0);
        player.getFoodData().setSaturation(0.0F);
        player.removeEffect(MobEffects.HUNGER);
        // End the closing warning exactly when the player is yanked back.
        player.removeEffect(MobEffects.CONFUSION);
        player.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                GraveRiftConfig.RETURN_WEAKNESS_TICKS.get(),
                0,
                false,
                true,
                true));

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 1.0F, 0.55F);
    }

    /** Removes a player from this rift without teleporting them. Used when they die. */
    public void removeMember(UUID player) {
        this.members.remove(player);
        MinecraftServer server = this.getServer();
        if (server != null) {
            GraveRiftSavedData.get(server).removeActiveRift(player, this.getUUID());
        }
    }

    public void playSpawnSound() {
        if (!this.level().isClientSide) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 1.0F, 0.4F);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------------------------------

    /** Remaining lifetime in ticks, synced to the client for rendering. */
    public int getLifetime() {
        return this.entityData.get(DATA_LIFETIME);
    }

    public void setRiftOwner(UUID owner) {
        this.riftOwner = owner;
    }

    @Nullable
    public UUID getRiftOwner() {
        return this.riftOwner;
    }

    public void setDestination(DimensionalPos destination) {
        this.destination = destination;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    // ------------------------------------------------------------------------------------------
    // Synced data / persistence
    // ------------------------------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LIFETIME, GraveRiftConfig.RIFT_LIFETIME_TICKS.get());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("CloseTime", Tag.TAG_LONG)) {
            this.closeTime = tag.getLong("CloseTime");
        } else {
            this.closeTime = this.level().getGameTime() + GraveRiftConfig.RIFT_LIFETIME_TICKS.get();
        }
        this.warned = tag.getBoolean("Warned");
        this.riftOwner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.destination = tag.contains("Destination") ? DimensionalPos.fromNbt(tag.getCompound("Destination")) : null;
        this.entityData.set(DATA_LIFETIME, this.getRemainingTicks());

        this.members.clear();
        ListTag members = tag.getList("Members", Tag.TAG_COMPOUND);
        for (int i = 0; i < members.size(); i++) {
            CompoundTag entry = members.getCompound(i);
            this.members.put(entry.getUUID("Id"), DimensionalPos.fromNbt(entry.getCompound("Return")));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("CloseTime", this.closeTime);
        tag.putBoolean("Warned", this.warned);
        if (this.riftOwner != null) {
            tag.putUUID("Owner", this.riftOwner);
        }
        if (this.destination != null) {
            tag.put("Destination", this.destination.toNbt());
        }

        ListTag members = new ListTag();
        this.members.forEach((uuid, pos) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", uuid);
            entry.put("Return", pos.toNbt());
            members.add(entry);
        });
        tag.put("Members", members);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }
}
