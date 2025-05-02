package dev.muon.dynamictooltips;

import dev.muon.dynamictooltips.config.DynamicTooltipsConfig;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import dev.muon.dynamictooltips.AttributeTooltipHandler.TooltipApplyResult;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.Set;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.resources.ResourceLocation;


public class BlockRangeTooltipHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("DynamicTooltips-BlockRange");
    private static final DecimalFormat FORMAT = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT));
    private static final Holder<Attribute> BLOCK_RANGE_ATTR_HOLDER = Attributes.BLOCK_INTERACTION_RANGE;

    public static void appendBlockRangeLines(ItemStack stack, Consumer<Component> tooltipConsumer, @Nullable Player player, TooltipApplyResult result) {
        if (!DynamicTooltipsConfig.CLIENT.appendBlockInteractionRangeTooltip.get()) {
            return;
        }

        if (!(player instanceof LocalPlayer localPlayer)) {
            return;
        }

        // Check if item is a relevant tool type
        boolean isRelevantTool = (stack.getItem() instanceof DiggerItem)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES);

        if (!isRelevantTool) {
            return;
        }

        // Check Better Combat override
        if (FabricLoader.getInstance().isModLoaded("bettercombat")) {
            WeaponAttributes weaponAttributes = WeaponRegistry.getAttributes(stack);
            if (weaponAttributes != null) {
                // If Better Combat handles it as a weapon, don't add our tooltip
                return;
            }
        }

        AttributeInstance blockRangeInstance = localPlayer.getAttribute(BLOCK_RANGE_ATTR_HOLDER);
        if (blockRangeInstance == null) {
            // Use description ID for logging
            LOGGER.warn("Player {} missing attribute instance for {}", localPlayer.getName().getString(), BLOCK_RANGE_ATTR_HOLDER.value().getDescriptionId());
            return;
        }

        // --- Calculate the hypothetical range if holding this item --- 
        double baseValue = blockRangeInstance.getBaseValue();
        List<AttributeModifier> relevantModifiers = new ArrayList<>();

        // Get all modifiers currently affecting the player
        List<AttributeModifier> allCurrentModifiers = new ArrayList<>(blockRangeInstance.getModifiers());

        // Get modifiers specifically from the item being viewed
        List<AttributeModifier> viewedItemModifiers = new ArrayList<>();
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attrHolder, modifier) -> {
            if (attrHolder == BLOCK_RANGE_ATTR_HOLDER) {
                viewedItemModifiers.add(modifier);
            }
        });

        // Get modifiers NOT from the currently equipped mainhand item
        ItemStack equippedStack = localPlayer.getMainHandItem();
        List<AttributeModifier> equippedItemModifiers = new ArrayList<>();
        if (!equippedStack.isEmpty()) {
            equippedStack.forEachModifier(EquipmentSlot.MAINHAND, (attrHolder, modifier) -> {
                 if (attrHolder == BLOCK_RANGE_ATTR_HOLDER) {
                     equippedItemModifiers.add(modifier);
                 }
            });
        }
        
        Set<ResourceLocation> equippedIds = equippedItemModifiers.stream().map(AttributeModifier::id).collect(Collectors.toSet());
        
        // Add modifiers from the player instance that are NOT from the currently equipped mainhand item
        for(AttributeModifier mod : allCurrentModifiers) {
            if (!equippedIds.contains(mod.id())) {
                 relevantModifiers.add(mod);
            }
        }
        
        // Add modifiers from the item being viewed
        relevantModifiers.addAll(viewedItemModifiers);

        // Calculate the final value by applying relevant modifiers to the base value
        relevantModifiers.sort(AttributeTooltipHandler.ATTRIBUTE_MODIFIER_COMPARATOR); // Ensure correct application order
        double calculatedFinalValue = baseValue;
        // Apply ADD_VALUE first
        for (AttributeModifier modifier : relevantModifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                calculatedFinalValue += modifier.amount();
            }
        }
        double valueToAddFromBase = 0;
        // Apply ADD_MULTIPLIED_BASE second
        for (AttributeModifier modifier : relevantModifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                valueToAddFromBase += baseValue * modifier.amount();
            }
        }
        calculatedFinalValue += valueToAddFromBase;
        // Apply ADD_MULTIPLIED_TOTAL last
        for (AttributeModifier modifier : relevantModifiers) {
             if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                 calculatedFinalValue *= (1.0 + modifier.amount());
             }
        }
        
        double finalValue = calculatedFinalValue; 
        boolean hasModifications = Math.abs(finalValue - baseValue) > 1e-4;

        result.needsShiftPrompt |= hasModifications;

        if (AttributeTooltipHandler.isDetailedView() && hasModifications) {
             tooltipConsumer.accept(createRangeLine(finalValue, true));
             tooltipConsumer.accept(AttributeTooltipHandler.listHeader().append(createRangeLine(baseValue, false).withStyle(AttributeTooltipHandler.BASE_COLOR)));

             List<AttributeModifier> sortedModifiers = relevantModifiers; // Already sorted
             for (AttributeModifier modifier : sortedModifiers) {
                 if (modifier.amount() != 0) { 
                      tooltipConsumer.accept(AttributeTooltipHandler.listHeader().append(
                           AttributeTooltipHandler.createModifierComponent(BLOCK_RANGE_ATTR_HOLDER.value(), modifier)
                      ));
                 }
             }
        } else {
             tooltipConsumer.accept(createRangeLine(finalValue, hasModifications));
        }

        // Mark Block Interaction Range as handled
        result.handledAttributes.add(BLOCK_RANGE_ATTR_HOLDER);
    }


    private static MutableComponent createRangeLine(double value, boolean isModified) {
        MutableComponent text = Component.translatable("attribute.modifier.equals.0",
                FORMAT.format(value),
                Component.translatable(BLOCK_RANGE_ATTR_HOLDER.value().getDescriptionId()));

        ChatFormatting baseColor = isModified ? null : AttributeTooltipHandler.BASE_COLOR;
        Integer customColor = isModified ? AttributeTooltipHandler.MERGE_BASE_MODIFIER_COLOR : null;

        return Component.literal(" ").append(text.withStyle(style -> {
            if (customColor != null) return style.withColor(customColor);
            if (baseColor != null) return style.applyFormat(baseColor); // Use applyFormat for ChatFormatting
            return style;
        }));
    }
} 