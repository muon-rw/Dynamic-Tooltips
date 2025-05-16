package dev.muon.dynamictooltips;

import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.client.BetterCombatClientMod;
import net.bettercombat.logic.WeaponRegistry;
import net.bettercombat.logic.EntityAttributeHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import dev.muon.dynamictooltips.AttributeTooltipHandler.TooltipApplyResult;
import dev.muon.dynamictooltips.Keybindings;

/**
 * Handles adding a dynamic Attack Range tooltip, integrating with Better Combat
 * and merging Entity Interaction Range modifiers.
 */
public class AttackRangeTooltipHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("DynamicTooltips-AttackRange");
    private static final DecimalFormat FORMAT = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT));

    public static void appendAttackRangeLines(ItemStack stack, Consumer<Component> tooltipConsumer, @Nullable Player player, TooltipApplyResult result) {
        if (!(FabricLoader.getInstance().isModLoaded("bettercombat")) || !BetterCombatClientMod.config.isTooltipAttackRangeEnabled) {
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

        AttributeInstance entityRangeAttrInstance = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (entityRangeAttrInstance == null) {
            LOGGER.warn("Player {} has no entity interaction range attribute! Cannot calculate attack range.", player.getName().getString());
            return;
        }
        double baseEntityRange = entityRangeAttrInstance.getBaseValue();
        ModifierTracker tracker = new ModifierTracker(baseEntityRange);

        calculateTotalRange(stack, localPlayer, attributes, tracker, entityRangeAttrInstance);
        double totalCalculatedRange = tracker.getFinalCalculatedValue();

        boolean itemHasEir = EntityAttributeHelper.itemHasRangeAttribute(stack);

        boolean hasModifications = !tracker.applicableModifiers.isEmpty();

        result.needsShiftPrompt |= hasModifications;

        if (Keybindings.isDetailedView() && hasModifications) {
            tooltipConsumer.accept(createTotalRangeComponent(totalCalculatedRange).withStyle(style -> style.withColor(AttributeTooltipHandler.MERGE_BASE_MODIFIER_COLOR)));
            
            double displayedLine2Base = itemHasEir ? tracker.initialBaseReach : (tracker.initialBaseReach + attributes.rangeBonus());
            tooltipConsumer.accept(createBaseWeaponRangeComponent(displayedLine2Base, ChatFormatting.DARK_GREEN));

            for (AttributeModifier modifier : tracker.applicableModifiers) { 
                if(modifier.amount() != 0) {
                    tooltipConsumer.accept(createModifierComponent(modifier));
                }
            }
        } else {
            ChatFormatting baseColor = hasModifications ? null : ChatFormatting.DARK_GREEN;
            Integer customColor = hasModifications ? AttributeTooltipHandler.MERGE_BASE_MODIFIER_COLOR : null;
            tooltipConsumer.accept(createTotalRangeComponent(totalCalculatedRange).withStyle(style -> {
                if (customColor != null) return style.withColor(customColor);
                if (baseColor != null) return style.withColor(baseColor);
                return style; 
            }));
        }
        result.handledAttributes.add(Attributes.ENTITY_INTERACTION_RANGE);
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
            if (applicableModifiers.stream().noneMatch(m -> m.id().equals(modifier.id()))) {
                applicableModifiers.add(modifier);
            }
        }

        void removeModifierById(ResourceLocation id) {
            applicableModifiers.removeIf(m -> m.id().equals(id));
        }

        void calculateValue() {
            applicableModifiers.sort(AttributeTooltipHandler.ATTRIBUTE_MODIFIER_COMPARATOR);

            double calculatedValue = initialBaseReach; 

            for (AttributeModifier modifier : applicableModifiers) {
                if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                    calculatedValue += modifier.amount();
                }
            }

            double totalFromOp1 = 0;
            for (AttributeModifier modifier : applicableModifiers) {
                if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                    totalFromOp1 += initialBaseReach * modifier.amount();
                }
            }
            calculatedValue += totalFromOp1;

            for (AttributeModifier modifier : applicableModifiers) {
                if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                    calculatedValue *= (1.0 + modifier.amount());
                }
            }
            this.currentTrackedValue = calculatedValue;
        }

        double getFinalCalculatedValue() {
            return this.currentTrackedValue;
        }
    }

    private static void calculateTotalRange(ItemStack stack, LocalPlayer player, WeaponAttributes attributes, ModifierTracker tracker, AttributeInstance reachAttrInstance) {
        if (reachAttrInstance != null) {
            for (AttributeModifier modifier : reachAttrInstance.getModifiers()) {
                tracker.addModifier(modifier);
            }
        }

        ItemStack equippedStack = player.getMainHandItem();
        boolean isViewingEquipped = ItemStack.matches(stack, equippedStack);

        if (!isViewingEquipped) {
            removeEquippedItemModifiers(equippedStack, tracker);
            addViewedItemModifiers(stack, tracker);
        }

        tracker.calculateValue();

        if (!EntityAttributeHelper.itemHasRangeAttribute(stack)) {
            tracker.currentTrackedValue += attributes.rangeBonus();
        }
    }

    private static void addViewedItemModifiers(ItemStack stack, ModifierTracker tracker) {
        stack.forEachModifier(EquipmentSlotGroup.MAINHAND, (attribute, modifier) -> {
            if (attribute.value() == Attributes.ENTITY_INTERACTION_RANGE.value()) {
                tracker.addModifier(modifier);
            }
        });
    }

    private static void removeEquippedItemModifiers(ItemStack equippedStack, ModifierTracker tracker) {
         if (equippedStack.isEmpty()) return;
        equippedStack.forEachModifier(EquipmentSlotGroup.MAINHAND, (attribute, modifier) -> {
            if (attribute.value() == Attributes.ENTITY_INTERACTION_RANGE.value()) {
                tracker.removeModifierById(modifier.id());
            }
        });
    }

    private static MutableComponent createTotalRangeComponent(double range) {
        String rangeAttrName = "attribute.name.generic.attack_range";
        return Component.literal(" ").append(Component.translatable("attribute.modifier.equals.0", FORMAT.format(range), Component.translatable(rangeAttrName)));
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