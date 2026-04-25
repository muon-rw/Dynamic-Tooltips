package dev.muon.dynamictooltips.handlers;

import dev.muon.dynamictooltips.Keybindings;
import dev.muon.dynamictooltips.config.DynamicTooltipsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.Tool;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import dev.muon.dynamictooltips.handlers.AttributeTooltipHandler.TooltipApplyResult;

/**
 * Displays a merged "Mining Speed" line on mining-tool tooltips.
 *
 * <p>Tool speed (the per-rule value baked into the {@link Tool} data component by
 * {@link net.minecraft.world.item.ToolMaterial#applyToolProperties}) is an item
 * property, not an attribute. {@link Attributes#MINING_EFFICIENCY} is a player
 * attribute fed by the Efficiency enchant. The vanilla formula adds them:
 * {@code Player#getDestroySpeed} computes {@code toolSpeed + MINING_EFFICIENCY}
 * whenever {@code toolSpeed > 1}.
 *
 * <p>This handler mirrors that combination into a single green/gold base line
 * (mirroring the Attack Range / Entity Interaction Range merge pattern), with
 * the held-vs-hovered modifier swap so hovering an off-inventory tool reflects
 * its own enchant bonuses rather than the equipped tool's.
 */
public class MiningSpeedTooltipHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("DynamicTooltips-MiningSpeed");
    private static final DecimalFormat FORMAT = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT));
    private static final Holder<Attribute> MINING_EFFICIENCY_ATTR_HOLDER = Attributes.MINING_EFFICIENCY;
    private static final String MINING_SPEED_NAME_KEY = "attribute.name.generic.mining_speed";

    public static void appendMiningSpeedLines(ItemStack stack, Consumer<Component> tooltipConsumer, @Nullable Player player, TooltipApplyResult result) {
        if (!DynamicTooltipsConfig.INSTANCE.appendMiningSpeedTooltip.get()) {
            return;
        }

        if (!(player instanceof LocalPlayer localPlayer)) {
            return;
        }

        boolean isRelevantTool = DynamicTooltipsConfig.INSTANCE.miningSpeedItemTags.get().stream()
                .anyMatch(entry -> DynamicTooltipsConfig.matchesItemOrTag(stack, entry));
        if (!isRelevantTool) {
            return;
        }

        Tool tool = stack.get(DataComponents.TOOL);
        if (tool == null) {
            return;
        }

        float toolSpeed = computeBaseToolSpeed(tool);
        // Per Player#getDestroySpeed, MINING_EFFICIENCY is only added when toolSpeed > 1.
        // For configured tags this should hold for every reasonable mining tool.
        if (toolSpeed <= 1.0F) {
            return;
        }

        // --- Held-vs-hovered MINING_EFFICIENCY merge (same pattern as BlockRangeTooltipHandler) ---
        AttributeInstance miningEffInstance = localPlayer.getAttribute(MINING_EFFICIENCY_ATTR_HOLDER);
        double baseAttrValue = miningEffInstance != null ? miningEffInstance.getBaseValue() : 0.0;
        List<AttributeModifier> relevantModifiers = new ArrayList<>();

        if (miningEffInstance != null) {
            List<AttributeModifier> allCurrentModifiers = new ArrayList<>(miningEffInstance.getModifiers());

            List<AttributeModifier> viewedItemModifiers = new ArrayList<>();
            stack.forEachModifier(EquipmentSlotGroup.MAINHAND, (attrHolder, modifier, display) -> {
                if (attrHolder == MINING_EFFICIENCY_ATTR_HOLDER) {
                    viewedItemModifiers.add(modifier);
                }
            });

            ItemStack equippedStack = localPlayer.getMainHandItem();
            List<AttributeModifier> equippedItemModifiers = new ArrayList<>();
            if (!equippedStack.isEmpty()) {
                equippedStack.forEachModifier(EquipmentSlotGroup.MAINHAND, (attrHolder, modifier, display) -> {
                    if (attrHolder == MINING_EFFICIENCY_ATTR_HOLDER) {
                        equippedItemModifiers.add(modifier);
                    }
                });
            }

            Set<Identifier> equippedIds = equippedItemModifiers.stream()
                    .map(AttributeModifier::id)
                    .collect(Collectors.toSet());

            for (AttributeModifier mod : allCurrentModifiers) {
                if (!equippedIds.contains(mod.id())) {
                    relevantModifiers.add(mod);
                }
            }
            relevantModifiers.addAll(viewedItemModifiers);
            relevantModifiers.sort(AttributeTooltipHandler.ATTRIBUTE_MODIFIER_COMPARATOR);
        }

        double miningEfficiencyValue = calculateMergedValue(baseAttrValue, relevantModifiers);
        double mergedSpeed = toolSpeed + miningEfficiencyValue;
        boolean hasModifications = Math.abs(miningEfficiencyValue) > 1e-4;

        result.needsShiftPrompt |= hasModifications;

        if (Keybindings.isDetailedView() && hasModifications) {
            // Top: merged gold line
            tooltipConsumer.accept(createMiningSpeedLine(mergedSpeed, true));
            // Detailed: green tool-side base line (the item property)
            tooltipConsumer.accept(AttributeTooltipHandler.listHeader()
                    .append(createMiningSpeedLine(toolSpeed, false).withStyle(AttributeTooltipHandler.BASE_COLOR)));
            // Detailed: blue MINING_EFFICIENCY modifier breakdown
            for (AttributeModifier modifier : relevantModifiers) {
                if (modifier.amount() != 0) {
                    tooltipConsumer.accept(AttributeTooltipHandler.listHeader().append(
                            AttributeTooltipHandler.createModifierComponent(MINING_EFFICIENCY_ATTR_HOLDER.value(), modifier)
                    ));
                }
            }
        } else {
            tooltipConsumer.accept(createMiningSpeedLine(mergedSpeed, hasModifications));
        }

        result.handledAttributes.add(MINING_EFFICIENCY_ATTR_HOLDER);
    }

    /**
     * Highest finite per-rule mining speed in the Tool component. Corresponds to the
     * material's primary "minesEfficiently" rule for pickaxes/axes/shovels/hoes.
     * Sword-style {@code Float.MAX_VALUE} instant-mine overrides are filtered out
     * defensively even though the configured tag should already exclude swords.
     */
    private static float computeBaseToolSpeed(Tool tool) {
        float max = tool.defaultMiningSpeed();
        for (Tool.Rule rule : tool.rules()) {
            if (rule.speed().isPresent()) {
                float s = rule.speed().get();
                if (Float.isFinite(s) && s > max && s < 1.0e6F) {
                    max = s;
                }
            }
        }
        return max;
    }

    private static double calculateMergedValue(double baseValue, List<AttributeModifier> sortedModifiers) {
        double finalValue = baseValue;
        for (AttributeModifier modifier : sortedModifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                finalValue += modifier.amount();
            }
        }
        double valueToAddFromBase = 0;
        for (AttributeModifier modifier : sortedModifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                valueToAddFromBase += baseValue * modifier.amount();
            }
        }
        finalValue += valueToAddFromBase;
        for (AttributeModifier modifier : sortedModifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                finalValue *= (1.0 + modifier.amount());
            }
        }
        return finalValue;
    }

    private static MutableComponent createMiningSpeedLine(double value, boolean isModified) {
        MutableComponent text = Component.translatable("attribute.modifier.equals.0",
                FORMAT.format(value),
                Component.translatable(MINING_SPEED_NAME_KEY));

        ChatFormatting baseColor = isModified ? null : AttributeTooltipHandler.BASE_COLOR;
        Integer customColor = isModified ? AttributeTooltipHandler.MERGE_BASE_MODIFIER_COLOR : null;

        return Component.literal(" ").append(text.withStyle(style -> {
            if (customColor != null) return style.withColor(customColor);
            if (baseColor != null) return style.applyFormat(baseColor);
            return style;
        }));
    }
}
