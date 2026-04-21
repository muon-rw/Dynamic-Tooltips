package dev.muon.dynamictooltips.mixin.compat.bettercombat;

// Better Combat is NOT YET UPDATED for Minecraft 26.1.2. When BC updates, uncomment the
// mixin below and re-add "compat.bettercombat.WeaponAttributeTooltipMixin" to
// src/main/resources/dynamictooltips.mixins.json (the MixinConfigPlugin already gates
// compat.* mixins on the corresponding mod being loaded).

/*
import net.bettercombat.client.WeaponAttributeTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(WeaponAttributeTooltip.class)
public class WeaponAttributeTooltipMixin {
    // Cancel the original logic; AttackRangeTooltipHandler owns the replacement rendering.
    @Inject(method = "modifyTooltip", at = @At("HEAD"), cancellable = true)
    private static void cancelTooltip(ItemStack itemStack, List<Component> lines, CallbackInfo ci) {
        ci.cancel();
    }
}
*/
