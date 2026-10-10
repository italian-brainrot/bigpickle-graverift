package com.bigpickle.graverift.registry;

import com.bigpickle.graverift.GraveRiftMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GraveRiftMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GRAVE_RIFT_TAB =
            CREATIVE_MODE_TABS.register("grave_rift", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.bigpickle_graverift"))
                    .icon(() -> new ItemStack(ModItems.GRAVE_RIFT.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.PIERCED_ENDER_PEARL.get());
                        output.accept(ModItems.GRAVE_RIFT.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
