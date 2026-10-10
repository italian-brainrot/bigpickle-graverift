package com.bigpickle.graverift.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The first stage of the Grave Rift crafting chain. Purely a crafting ingredient, so the only
 * behaviour it adds is its flavour tooltip.
 */
public class PiercedEnderPearlItem extends Item {
    public PiercedEnderPearlItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.bigpickle_graverift.pierced_ender_pearl")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
