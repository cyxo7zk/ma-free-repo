package com.shipwright.ship;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Engine fuel values (ticks). Hard-coded on purpose: vanilla's fuel API changed between
 * 26.x versions (fuel is now an item component), this table never breaks.
 */
public final class FuelTable {
    private FuelTable() {}

    public static int burnTime(ItemStack s) {
        if (s.isEmpty()) return 0;
        if (s.is(Items.LAVA_BUCKET)) return 20000;
        if (s.is(Items.COAL_BLOCK)) return 16000;
        if (s.is(Items.DRIED_KELP_BLOCK)) return 4000;
        if (s.is(Items.BLAZE_ROD)) return 2400;
        if (s.is(Items.COAL) || s.is(Items.CHARCOAL)) return 1600;
        if (s.is(ItemTags.LOGS_THAT_BURN)) return 300;
        if (s.is(ItemTags.PLANKS)) return 300;
        if (s.is(ItemTags.WOODEN_SLABS)) return 150;
        if (s.is(Items.STICK)) return 100;
        return 0;
    }
}
