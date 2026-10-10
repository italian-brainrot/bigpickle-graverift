package com.bigpickle.graverift.item;

import java.util.List;

import com.bigpickle.graverift.config.GraveRiftConfig;
import com.bigpickle.graverift.data.DimensionalPos;
import com.bigpickle.graverift.data.GraveRiftSavedData;
import com.bigpickle.graverift.entity.GraveRiftEntity;
import com.bigpickle.graverift.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The finished item. Using it on any living entity tears open a {@link GraveRiftEntity} at that
 * entity's position, dealing damage and consuming the item.
 *
 * <p>All the "does it count" logic runs on the logical server only; the client simply predicts a
 * successful swing. That keeps the two sides from drifting out of sync, which is important in
 * mod packs where other mods may also intercept entity interaction.</p>
 */
public class GraveRiftItem extends Item {
    public GraveRiftItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.bigpickle_graverift.grave_rift")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand usedHand) {
        // Client side: let the player swing, the server will decide what actually happens.
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        if (target == player || !target.isAlive()) {
            return InteractionResult.PASS;
        }

        MinecraftServer server = serverPlayer.getServer();
        if (server == null) {
            return InteractionResult.PASS;
        }

        GraveRiftSavedData data = GraveRiftSavedData.get(server);
        DimensionalPos destination = data.getLastDeath(serverPlayer.getUUID());
        if (destination == null) {
            // No recorded death means there is nowhere to travel to, so keep the item.
            serverPlayer.displayClientMessage(
                    Component.translatable("message.bigpickle_graverift.no_last_death").withStyle(ChatFormatting.RED),
                    true);
            return InteractionResult.FAIL;
        }

        GraveRiftEntity rift = ModEntities.GRAVE_RIFT.get().create(serverPlayer.serverLevel());
        if (rift == null) {
            return InteractionResult.FAIL;
        }

        rift.moveTo(target.getX(), target.getY(), target.getZ(), serverPlayer.getYRot(), 0.0F);
        rift.setRiftOwner(serverPlayer.getUUID());
        rift.setDestination(destination);

        // Damage the creature the rift is being torn out of. Even if it is immune (for example an
        // armour stand in some packs) the rift still opens.
        target.hurt(serverPlayer.damageSources().playerAttack(serverPlayer),
                (float) GraveRiftConfig.MOB_DAMAGE.get().doubleValue());

        serverPlayer.serverLevel().addFreshEntity(rift);
        rift.playSpawnSound();

        stack.shrink(1);
        return InteractionResult.CONSUME;
    }
}
