package dev.muon.dynamictooltips;

import net.minecraft.world.item.ItemStack;

/**
 * Interface for mixins to hold the ItemStack context during tooltip generation.
 */
public interface EnchantmentContext {

    ItemStack dynamictooltips$getStack();

    void dynamictooltips$setStack(ItemStack stack);
} 