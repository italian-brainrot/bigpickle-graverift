package com.bigpickle.graverift.registry;

import com.bigpickle.graverift.GraveRiftMod;
import com.bigpickle.graverift.item.GraveRiftItem;
import com.bigpickle.graverift.item.PiercedEnderPearlItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GraveRiftMod.MODID);

    public static final DeferredItem<Item> PIERCED_ENDER_PEARL = ITEMS.registerItem(
            "pierced_ender_pearl",
            PiercedEnderPearlItem::new,
            new Item.Properties());

    public static final DeferredItem<Item> GRAVE_RIFT = ITEMS.registerItem(
            "grave_rift",
            GraveRiftItem::new,
            new Item.Properties().stacksTo(16));

    private ModItems() {
    }
}
