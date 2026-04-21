package dev.muon.dynamictooltips.handlers;

import dev.muon.dynamictooltips.Keybindings;
import dev.muon.dynamictooltips.config.DynamicTooltipsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Handles entity interaction range tooltips for weapons (swords, etc.)
 * Similar to BlockRangeTooltipHandler but for entity/attack range.
 */
public class EntityRangeTooltipHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("DynamicTooltips-EntityRange");
    private static final DecimalFormat FORMAT = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT));
    private static final Holder<Attribute> ENTITY_RANGE_ATTR_HOLDER = Attributes.ENTITY_INTERACTION_RANGE;

    public static void appendEntityRangeLines(
            ItemStack stack, 
            Consumer<Component> tooltipConsumer, 
            @Nullable Player player, 
            AttributeTooltipHandler.TooltipApplyResult result) {
        
        if (!DynamicTooltipsConfig.INSTANCE.appendEntityInteractionRangeTooltip.get()) {
            return;
        }

        if (!(player instanceof LocalPlayer localPlayer)) {
            return;
        }

        // Check if item matches any of the configured items or tags
        boolean isRelevantWeapon = DynamicTooltipsConfig.INSTANCE.entityInteractionRangeItemTags.get().stream()
            .anyMatch(entry -> DynamicTooltipsConfig.matchesItemOrTag(stack, entry));

        if (!isRelevantWeapon) {
            return;
        }

        AttributeInstance entityRangeInstance = localPlayer.getAttribute(ENTITY_RANGE_ATTR_HOLDER);
        if (entityRangeInstance == null) {
            LOGGER.warn("Player {} missing attribute instance for {}", 
                localPlayer.getName().getString(), 
                ENTITY_RANGE_ATTR_HOLDER.value().getDescriptionId());
            return;
        }

        // Calculate the hypothetical range if holding this item
        double baseValue = entityRangeInstance.getBaseValue();
        List<AttributeModifier> relevantModifiers = new ArrayList<>();

        // Get all modifiers currently affecting the player
        List<AttributeModifier> allCurrentModifiers = new ArrayList<>(entityRangeInstance.getModifiers());

        // Get modifiers specifically from the item being viewed
        List<AttributeModifier> viewedItemModifiers = new ArrayList<>();
        stack.forEachModifier(EquipmentSlotGroup.MAINHAND, (attrHolder, modifier, display) -> {
            if (attrHolder == ENTITY_RANGE_ATTR_HOLDER) {
                viewedItemModifiers.add(modifier);
            }
        });

        // Get modifiers from the currently equipped mainhand item
        ItemStack equippedStack = localPlayer.getMainHandItem();
        List<AttributeModifier> equippedItemModifiers = new ArrayList<>();
        if (!equippedStack.isEmpty()) {
            equippedStack.forEachModifier(EquipmentSlotGroup.MAINHAND, (attrHolder, modifier, display) -> {
                if (attrHolder == ENTITY_RANGE_ATTR_HOLDER) {
                    equippedItemModifiers.add(modifier);
                }
            });
        }

        Set<Identifier> equippedIds = equippedItemModifiers.stream()
            .map(AttributeModifier::id)
            .collect(Collectors.toSet());

        // Add modifiers from the player instance that are NOT from the currently equipped mainhand item
        for (AttributeModifier mod : allCurrentModifiers) {
            if (!equippedIds.contains(mod.id())) {
                relevantModifiers.add(mod);
            }
        }

        // Add modifiers from the viewed item
        relevantModifiers.addAll(viewedItemModifiers);

        // Calculate final value applying modifiers in correct order
        relevantModifiers.sort(AttributeTooltipHandler.ATTRIBUTE_MODIFIER_COMPARATOR);
        double finalValue = getMergedValue(baseValue, relevantModifiers);
        boolean hasModifications = Math.abs(finalValue - baseValue) > 1e-4;

        result.needsShiftPrompt |= hasModifications;

        // Display logic
        if (Keybindings.isDetailedView() && hasModifications) {
            // Show merged value
            tooltipConsumer.accept(createRangeLine(finalValue, true));
            // Show base value
            tooltipConsumer.accept(AttributeTooltipHandler.listHeader()
                .append(createRangeLine(baseValue, false).withStyle(AttributeTooltipHandler.BASE_COLOR)));

            // Show individual modifiers
            for (AttributeModifier modifier : relevantModifiers) {
                if (modifier.amount() != 0) {
                    tooltipConsumer.accept(AttributeTooltipHandler.listHeader().append(
                        AttributeTooltipHandler.createModifierComponent(ENTITY_RANGE_ATTR_HOLDER.value(), modifier)
                    ));
                }
            }
        } else {
            // Show just the final value
            tooltipConsumer.accept(createRangeLine(finalValue, hasModifications));
        }

        result.handledAttributes.add(ENTITY_RANGE_ATTR_HOLDER);
    }

    private static double getMergedValue(double baseValue, List<AttributeModifier> relevantModifiers) {
        double finalValue = baseValue;

        // ADD_VALUE operations
        for (AttributeModifier modifier : relevantModifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                finalValue += modifier.amount();
            }
        }

        // ADD_MULTIPLIED_BASE operations
        double valueToAddFromBase = 0;
        for (AttributeModifier modifier : relevantModifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                valueToAddFromBase += baseValue * modifier.amount();
            }
        }
        finalValue += valueToAddFromBase;

        // ADD_MULTIPLIED_TOTAL operations
        for (AttributeModifier modifier : relevantModifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                finalValue *= (1.0 + modifier.amount());
            }
        }

        return finalValue;
    }

    private static MutableComponent createRangeLine(double value, boolean isModified) {
        MutableComponent text = Component.translatable("attribute.modifier.equals.0",
            FORMAT.format(value),
            Component.translatable(ENTITY_RANGE_ATTR_HOLDER.value().getDescriptionId()));

        ChatFormatting baseColor = isModified ? null : AttributeTooltipHandler.BASE_COLOR;
        Integer customColor = isModified ? AttributeTooltipHandler.MERGE_BASE_MODIFIER_COLOR : null;

        return Component.literal(" ").append(text.withStyle(style -> {
            if (customColor != null) return style.withColor(customColor);
            if (baseColor != null) return style.applyFormat(baseColor);
            return style;
        }));
    }
}

