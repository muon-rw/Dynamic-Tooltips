package dev.muon.dynamictooltips;

import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.client.BetterCombatClientMod;
import net.bettercombat.logic.WeaponRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EquipmentSlotGroup;
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
import java.util.Optional;
import java.util.function.Consumer;
import java.util.Set;
import net.minecraft.core.Holder;

/**
 * Handles adding a dynamic Attack Range tooltip, integrating with Better Combat
 * and merging Entity Interaction Range modifiers.
 */
public class AttackRangeTooltipHandler {

    private static final boolean IS_BETTER_COMBAT_LOADED = FabricLoader.getInstance().isModLoaded("bettercombat");
    private static final Logger LOGGER = LoggerFactory.getLogger("DynamicTooltips-AttackRange");
    private static final DecimalFormat FORMAT = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT));

    public static void appendAttackRangeLines(ItemStack stack, Consumer<Component> tooltipConsumer, @Nullable Player player, Set<Holder<Attribute>> handledAttributes) {
        if (!IS_BETTER_COMBAT_LOADED || !BetterCombatClientMod.config.isTooltipAttackRangeEnabled) {
            return;
        }
        if (!(player instanceof LocalPlayer localPlayer)) {
            return;
        }

        Optional<WeaponAttributes> attributesOpt = Optional.ofNullable(WeaponRegistry.getAttributes(stack));
        if (attributesOpt.isEmpty()) {
            return;
        }
        WeaponAttributes attributes = attributesOpt.get();

        AttributeInstance entityRangeAttr = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (entityRangeAttr == null) {
            LOGGER.warn("Player {} has no entity interaction range attribute! Cannot calculate attack range.", player.getName().getString());
            return;
        }
        double baseEntityRange = entityRangeAttr.getBaseValue();
        ModifierTracker tracker = new ModifierTracker(baseEntityRange);
        double totalCalculatedRange = calculateTotalRange(stack, localPlayer, attributes, tracker);
        double baseWeaponRange = Attributes.ENTITY_INTERACTION_RANGE.value().getDefaultValue() + attributes.rangeBonus();
        boolean hasModifications = Math.abs(totalCalculatedRange - baseWeaponRange) > 1e-4;

        if (Screen.hasShiftDown() && hasModifications) {
            // Add expanded lines directly
            tooltipConsumer.accept(createTotalRangeComponent(totalCalculatedRange, ChatFormatting.GOLD));
            tooltipConsumer.accept(createBaseWeaponRangeComponent(baseWeaponRange, ChatFormatting.DARK_GREEN));
            tracker.applicableModifiers.sort(AttributeTooltipHandler.ATTRIBUTE_MODIFIER_COMPARATOR);
            for (AttributeModifier modifier : tracker.applicableModifiers) {
                if(modifier.amount() != 0) {
                    tooltipConsumer.accept(createModifierComponent(modifier));
                }
            }
        } else {
            tooltipConsumer.accept(createTotalRangeComponent(totalCalculatedRange,
                    hasModifications ? ChatFormatting.GOLD : ChatFormatting.DARK_GREEN));
        }

        // Mark Entity Interaction Range as handled so it doesn't get displayed again by the main handler
        handledAttributes.add(Attributes.ENTITY_INTERACTION_RANGE);
    }

    private static class ModifierTracker {
        double currentTrackedValue;
        final List<AttributeModifier> applicableModifiers = new ArrayList<>();
        final double initialBaseReach;

        ModifierTracker(double playerBaseReach) {
            this.initialBaseReach = playerBaseReach;
            this.currentTrackedValue = playerBaseReach;
        }

        void addModifier(AttributeModifier modifier) {
            if (modifier.amount() == 0) return;
            if (applicableModifiers.stream().anyMatch(m -> m.id().equals(modifier.id()))) {
                return;
            }
            applicableModifiers.add(modifier);
            switch (modifier.operation()) {
                case ADD_VALUE -> currentTrackedValue += modifier.amount();
                case ADD_MULTIPLIED_BASE -> currentTrackedValue += initialBaseReach * modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> currentTrackedValue *= (1.0 + modifier.amount());
                default -> LOGGER.warn("Unknown modifier operation: {}", modifier.operation());
            }
        }

        void subtractModifier(AttributeModifier modifierToRemove) {
            if (modifierToRemove.amount() == 0) return;
            boolean removed = applicableModifiers.removeIf(m -> m.id().equals(modifierToRemove.id()));
             if (removed) {
                 currentTrackedValue = initialBaseReach;
                 List<AttributeModifier> remaining = new ArrayList<>(applicableModifiers);
                 applicableModifiers.clear();
                 remaining.forEach(this::addModifier);
             } else {
                  LOGGER.warn("Tried to subtract modifier not present in tracker: {}", modifierToRemove);
             }
        }
    }

    private static double calculateTotalRange(ItemStack stack, LocalPlayer player, WeaponAttributes attributes, ModifierTracker tracker) {
        AttributeInstance reachAttr = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (reachAttr == null) {
             return Attributes.ENTITY_INTERACTION_RANGE.value().getDefaultValue() + attributes.rangeBonus();
        }
        for (AttributeModifier modifier : reachAttr.getModifiers()) {
            tracker.addModifier(modifier);
        }
        ItemStack equippedStack = player.getMainHandItem();
        boolean isViewingEquipped = ItemStack.matches(stack, equippedStack);
        if (!isViewingEquipped) {
            deduplicateHeldItemModifiers(equippedStack, tracker);
            addViewedItemModifiers(stack, tracker);
        }
        double modifierContribution = tracker.currentTrackedValue - tracker.initialBaseReach;
        double finalRange = Attributes.ENTITY_INTERACTION_RANGE.value().getDefaultValue() + attributes.rangeBonus() + modifierContribution;
        return finalRange;
    }

    private static void addViewedItemModifiers(ItemStack stack, ModifierTracker tracker) {
        stack.forEachModifier(EquipmentSlotGroup.MAINHAND, (attribute, modifier) -> {
            if (attribute.value() == Attributes.ENTITY_INTERACTION_RANGE.value()) {
                tracker.addModifier(modifier);
            }
        });
    }

    private static void deduplicateHeldItemModifiers(ItemStack equippedStack, ModifierTracker tracker) {
         if (equippedStack.isEmpty()) return;
        equippedStack.forEachModifier(EquipmentSlotGroup.MAINHAND, (attribute, modifier) -> {
            if (attribute.value() == Attributes.ENTITY_INTERACTION_RANGE.value()) {
                tracker.subtractModifier(modifier);
            }
        });
    }

    private static MutableComponent createTotalRangeComponent(double range, ChatFormatting color) {
        String rangeAttrName = "attribute.name.generic.attack_range";
        return Component.literal(" ").append(Component.translatable("attribute.modifier.equals.0", FORMAT.format(range), Component.translatable(rangeAttrName))).withStyle(color);
    }
    private static MutableComponent createBaseWeaponRangeComponent(double value, ChatFormatting color) {
        String rangeAttrName = "attribute.name.generic.attack_range";
        return AttributeTooltipHandler.listHeader().append(Component.translatable("attribute.modifier.equals.0", FORMAT.format(value), Component.translatable(rangeAttrName)).withStyle(color));
    }
    private static MutableComponent createModifierComponent(AttributeModifier modifier) {
        Attribute attribute = Attributes.ENTITY_INTERACTION_RANGE.value();
        String attrNameKey = attribute.getDescriptionId();
        double value = modifier.amount();
        boolean isPositive = value > 0;
        AttributeModifier.Operation operation = modifier.operation();
        String key = isPositive ? "attribute.modifier.plus." + operation.id() : "attribute.modifier.take." + operation.id();
        String formattedValue = formatRangeValue(value, operation);
        ChatFormatting color = isPositive ? ChatFormatting.BLUE : ChatFormatting.RED;
        return AttributeTooltipHandler.listHeader().append(Component.translatable(key, formattedValue, Component.translatable(attrNameKey)).withStyle(color));
    }
    private static String formatRangeValue(double value, AttributeModifier.Operation operation) {
        double absValue = Math.abs(value);
        if (operation == AttributeModifier.Operation.ADD_VALUE) {
            return FORMAT.format(absValue);
        } else {
            return (value >= 0 ? "+" : "") + FORMAT.format(value * 100) + "%";
        }
    }
} 