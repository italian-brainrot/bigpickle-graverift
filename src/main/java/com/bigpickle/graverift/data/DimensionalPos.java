package com.bigpickle.graverift.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * A precise, serializable location in a specific dimension.
 *
 * <p>Unlike {@link net.minecraft.core.GlobalPos} this keeps the full floating point position and
 * the entity rotation, which matters when we send a player back exactly where they stood.</p>
 */
public record DimensionalPos(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot) {

    public static DimensionalPos of(Entity entity) {
        return new DimensionalPos(
                entity.level().dimension(),
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                entity.getYRot(),
                entity.getXRot());
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", this.dimension.location().toString());
        tag.putDouble("X", this.x);
        tag.putDouble("Y", this.y);
        tag.putDouble("Z", this.z);
        tag.putFloat("YRot", this.yRot);
        tag.putFloat("XRot", this.xRot);
        return tag;
    }

    public static DimensionalPos fromNbt(CompoundTag tag) {
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION,
                ResourceLocation.parse(tag.getString("Dimension")));
        return new DimensionalPos(
                dimension,
                tag.getDouble("X"),
                tag.getDouble("Y"),
                tag.getDouble("Z"),
                tag.getFloat("YRot"),
                tag.getFloat("XRot"));
    }
}
