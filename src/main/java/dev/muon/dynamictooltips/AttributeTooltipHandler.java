package dev.muon.dynamictooltips;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.DiggerItem;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.fabricmc.loader.api.FabricLoader;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;

/**
 * Merged Attribute Modifier tooltips, inspired by NeoForge.
 */
public class AttributeTooltipHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("DynamicTooltips-Attributes");
    private static final DecimalFormat FORMAT = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT));
    private static final ResourceLocation FAKE_MERGED_ID = ResourceLocation.fromNamespaceAndPath(DynamicTooltips.MODID, "fake_merged_modifier");

    static final ChatFormatting BASE_COLOR = ChatFormatting.DARK_GREEN;
    public static final ChatFormatting MERGED_BASE_COLOR = ChatFormatting.GOLD;
    public static final int MERGED_MODIFIER_COLOR = 7699710; // Light Blue
    static final ChatFormatting POSITIVE_COLOR = ChatFormatting.BLUE;
    static final ChatFormatting NEGATIVE_COLOR = ChatFormatting.RED;

    static final Comparator<AttributeModifier> ATTRIBUTE_MODIFIER_COMPARATOR =
            Comparator.comparing(AttributeModifier::operation)
                    .thenComparing((AttributeModifier a) -> -Math.abs(a.amount()))
                    .thenComparing(AttributeModifier::id);

    private static final Map<String, EquipmentSlotGroup> KEY_SLOT_MAP = Util.make(new HashMap<>(), map -> {
        map.put(Component.translatable("item.modifiers.mainhand").getString(), EquipmentSlotGroup.MAINHAND);
        map.put(Component.translatable("item.modifiers.offhand").getString(), EquipmentSlotGroup.OFFHAND);
        map.put(Component.translatable("item.modifiers.head").getString(), EquipmentSlotGroup.HEAD);
        map.put(Component.translatable("item.modifiers.chest").getString(), EquipmentSlotGroup.CHEST);
        map.put(Component.translatable("item.modifiers.legs").getString(), EquipmentSlotGroup.LEGS);
        map.put(Component.translatable("item.modifiers.feet").getString(), EquipmentSlotGroup.FEET);
        map.put(Component.translatable("item.modifiers.body").getString(), EquipmentSlotGroup.BODY);
        map.put(Component.translatable("tiered.slot.feet").getString(), EquipmentSlotGroup.FEET);
        map.put(Component.translatable("tiered.slot.head").getString(), EquipmentSlotGroup.HEAD);
        map.put(Component.translatable("tiered.slot.chest").getString(), EquipmentSlotGroup.CHEST);
        map.put(Component.translatable("tiered.slot.legs").getString(), EquipmentSlotGroup.LEGS);
        map.put(Component.translatable("tiered.slot.body").getString(), EquipmentSlotGroup.BODY);
    });

    // Block Interaction Range/Attack Range/Entity Interaction Range are special cases
    private static final Set<ResourceLocation> BASE_ATTRIBUTE_IDS = Util.make(new HashSet<>(), set -> {
        set.add(BuiltInRegistries.ATTRIBUTE.getKey(Attributes.ATTACK_DAMAGE.value()));
        set.add(BuiltInRegistries.ATTRIBUTE.getKey(Attributes.ATTACK_SPEED.value()));
        set.add(BuiltInRegistries.ATTRIBUTE.getKey(Attributes.ENTITY_INTERACTION_RANGE.value()));
        set.add(ResourceLocation.fromNamespaceAndPath("ranged_weapon", "damage"));
        set.add(ResourceLocation.fromNamespaceAndPath("ranged_weapon", "pull_time"));
        set.remove(null);
    });

    // Block Interaction Range/Attack Range/Entity Interaction Range are special cases
    private static final Map<ResourceLocation, ResourceLocation> BASE_MODIFIER_IDS = Util.make(new HashMap<>(), map -> {
        map.put(BuiltInRegistries.ATTRIBUTE.getKey(Attributes.ATTACK_DAMAGE.value()), Item.BASE_ATTACK_DAMAGE_ID);
        map.put(BuiltInRegistries.ATTRIBUTE.getKey(Attributes.ATTACK_SPEED.value()), Item.BASE_ATTACK_SPEED_ID);
        map.put(BuiltInRegistries.ATTRIBUTE.getKey(Attributes.ENTITY_INTERACTION_RANGE.value()), ResourceLocation.withDefaultNamespace("base_entity_reach"));
        map.put(ResourceLocation.fromNamespaceAndPath("ranged_weapon", "damage"), ResourceLocation.fromNamespaceAndPath("ranged_weapon", "base_damage"));
        map.put(ResourceLocation.fromNamespaceAndPath("ranged_weapon", "pull_time"), ResourceLocation.fromNamespaceAndPath("ranged_weapon", "base_pull_time"));
        map.remove(null);
    });

    /**
     * Determines if the detailed tooltip view is enabled (Shift key)
     */
    public static boolean isDetailedView() {
        return Screen.hasShiftDown();
    }

    /**
     * Main entry point for tooltip processing
     */
    public static void processTooltip(ItemStack stack, List<Component> tooltip, @Nullable Player player) {
        // Check if the item has any attribute sections
        List<AttributeSection> sections = findAttributeSections(tooltip);
        if (sections.isEmpty()) {
            return;
        }

        // Process the tooltip
        List<Component> newTooltip = new ArrayList<>();
        int currentIndex = 0;
        for (int sectionIdx = 0; sectionIdx < sections.size(); sectionIdx++) {
            AttributeSection section = sections.get(sectionIdx);
            int sectionStart = section.startIndex;
            
            // Add lines before this section
            while (currentIndex < sectionStart) {
                newTooltip.add(tooltip.get(currentIndex++));
            }
            
            // Add the section header
            newTooltip.add(tooltip.get(currentIndex++));
            
            // Process the section content
            Multimap<Holder<Attribute>, AttributeModifier> modifiers = getSortedModifiers(stack, section.slot);
            if (!modifiers.isEmpty()) {
                applyTextFor(stack, newTooltip::add, modifiers, player);
            } else {
                for (int i = 0; i < section.lineCount; i++) {
                    newTooltip.add(tooltip.get(currentIndex + i));
                }
            }
            
            currentIndex += section.lineCount;
        }
        
        // Add remaining lines
        while (currentIndex < tooltip.size()) {
            newTooltip.add(tooltip.get(currentIndex++));
        }
        
        tooltip.clear();
        tooltip.addAll(newTooltip);
    }

    /**
     * Gets modifiers for a specific slot, sorted for consistent display
     */
    private static Multimap<Holder<Attribute>, AttributeModifier> getSortedModifiers(ItemStack stack, EquipmentSlotGroup slot) {
        Multimap<Holder<Attribute>, AttributeModifier> map = LinkedListMultimap.create();

        stack.forEachModifier(slot, (attributeHolder, modifier) -> {
            if (attributeHolder != null && modifier != null) {
                map.put(attributeHolder, modifier);
            }
        });

        return map;
    }

    /**
     * Generates tooltip text for attribute modifiers
     */
    private static void applyTextFor(
            ItemStack stack,
            Consumer<Component> tooltip,
            Multimap<Holder<Attribute>, AttributeModifier> modifierMap,
            @Nullable Player player) {

        if (modifierMap.isEmpty()) {
            return;
        }

        // Set to track attributes handled by special logic (Attack Range, Block Interaction Range)
        Set<Holder<Attribute>> handledAttributes = new HashSet<>();

        Map<Holder<Attribute>, BaseModifier> baseModifiers = new Reference2ReferenceLinkedOpenHashMap<>();

        Multimap<Holder<Attribute>, AttributeModifier> remainingModifiers = LinkedListMultimap.create();
        remainingModifiers.putAll(modifierMap);

        var it = remainingModifiers.entries().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Holder<Attribute> attr = entry.getKey();
            AttributeModifier modifier = entry.getValue();

            if (isBaseModifier(attr.value(), modifier)) {
                baseModifiers.put(attr, new BaseModifier(modifier, new ArrayList<>()));
                it.remove();
            }
        }

        it = remainingModifiers.entries().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Holder<Attribute> attr = entry.getKey();
            AttributeModifier modifier = entry.getValue();

            if (isBaseAttribute(attr.value())) {
                BaseModifier base = baseModifiers.get(attr);
                if (base != null) {
                    base.children.add(modifier);
                    it.remove();
                }
            }
        }

        for (var entry : baseModifiers.entrySet()) {
            Holder<Attribute> attr = entry.getKey();
            BaseModifier baseModifier = entry.getValue();

            double entityBase = player == null ? 0 : player.getAttributeBaseValue(attr);
            double base = baseModifier.base.amount() + entityBase;
            final double rawBase = base;
            double amount = base;

            for (AttributeModifier modifier : baseModifier.children) {
                switch (modifier.operation()) {
                    case ADD_VALUE:
                        base = amount = amount + modifier.amount();
                        break;
                    case ADD_MULTIPLIED_BASE:
                        amount += modifier.amount() * rawBase;
                        break;
                    case ADD_MULTIPLIED_TOTAL:
                        amount *= 1.0 + modifier.amount();
                        break;
                }
            }

            boolean isMerged = !baseModifier.children.isEmpty();

            MutableComponent text = createBaseComponent(attr.value(), amount, entityBase, isMerged);
            tooltip.accept(Component.literal(" ").append(text.withStyle(isMerged ? MERGED_BASE_COLOR : BASE_COLOR)));

            if (isDetailedView() && isMerged) {
                text = createBaseComponent(attr.value(), rawBase, entityBase, false);
                tooltip.accept(listHeader().append(text.withStyle(BASE_COLOR)));

                for (AttributeModifier modifier : baseModifier.children) {
                    tooltip.accept(listHeader().append(createModifierComponent(attr.value(), modifier)));
                }
            }

            // --- INTEGRATION POINT for Attack Range ---
            if (attr.value() == Attributes.ATTACK_SPEED.value()) {
                AttackRangeTooltipHandler.appendAttackRangeLines(stack, tooltip, player, handledAttributes);
                // --- INTEGRATION POINT for Block Interaction Range ---
                appendBlockInteractionRangeTooltip(stack, tooltip, player, EquipmentSlotGroup.MAINHAND, modifierMap, handledAttributes);
            }
            // --- END INTEGRATION POINT ---
        }

        // --- Process remaining modifiers (non-base) ---
        Map<Holder<Attribute>, Collection<AttributeModifier>> sortedRemaining = new TreeMap<>(Comparator.comparing(
             h -> BuiltInRegistries.ATTRIBUTE.getKey(h.value()),
             Comparator.nullsLast(Comparator.naturalOrder())
        ));
         for (Holder<Attribute> attr : remainingModifiers.keySet()) {
             if (!baseModifiers.containsKey(attr)) {
                List<AttributeModifier> mods = new ArrayList<>(remainingModifiers.get(attr));
                mods.sort(ATTRIBUTE_MODIFIER_COMPARATOR);
                sortedRemaining.put(attr, mods);
             }
         }

        // Iterate over the sorted remaining attributes
        for (Holder<Attribute> attr : sortedRemaining.keySet()) {
             if (handledAttributes.contains(attr)) {
                 continue;
             }

            Collection<AttributeModifier> modifiers = sortedRemaining.get(attr);
            if (modifiers == null || modifiers.isEmpty()) continue;

            // Logic for merging/displaying non-base attributes
            Map<Operation, MergedModifierData> mergeData = new EnumMap<>(Operation.class);
            List<AttributeModifier> nonMergeable = new ArrayList<>(); // Initialize list for non-mergeable modifiers

            // --- Step 1: Separate mergeable and non-mergeable modifiers ---
            for (AttributeModifier modifier : modifiers) {
                if (modifier.amount() == 0) continue; // Skip zero-amount modifiers

                // Determine if this modifier type should be merged (e.g., standard +/-/x)
                boolean canMerge = modifier.operation() == Operation.ADD_VALUE || 
                                   modifier.operation() == Operation.ADD_MULTIPLIED_BASE || 
                                   modifier.operation() == Operation.ADD_MULTIPLIED_TOTAL;

                // Don't merge base modifiers within this loop (should already be handled)
                if (isBaseModifier(attr.value(), modifier)) {
                     canMerge = false;
                }

                // Add specific conditions here later if needed to prevent merging certain modifiers by ID/name

                if (canMerge) {
                     MergedModifierData data = mergeData.computeIfAbsent(modifier.operation(), op -> new MergedModifierData());
                     if (!data.children.isEmpty()) { // Mark as merged if it's not the first one for this operation
                         data.isMerged = true;
                     }
                     data.sum += modifier.amount();
                     data.children.add(modifier);
                } else {
                     nonMergeable.add(modifier);
                }
            }

            // --- Step 2: Process and display merged modifiers ---
            for (Operation op : Operation.values()) {
                MergedModifierData data = mergeData.get(op);
                if (data == null || data.sum == 0) continue;

                AttributeModifier fakeModifier = new AttributeModifier(FAKE_MERGED_ID, data.sum, op);
                MutableComponent modComponent = createModifierComponent(attr.value(), fakeModifier);

                if (data.isMerged) {
                    if (isBaseAttribute(attr.value())) {
                        tooltip.accept(modComponent.withStyle(MERGED_BASE_COLOR)); // Gold
                    } else {
                        tooltip.accept(modComponent.withStyle(style -> style.withColor(MERGED_MODIFIER_COLOR))); // Light Blue
                    }

                    if (isDetailedView()) {
                        data.children.sort(ATTRIBUTE_MODIFIER_COMPARATOR);
                        for (AttributeModifier mod : data.children) {
                            tooltip.accept(listHeader().append(createModifierComponent(attr.value(), mod)));
                        }
                    }
                } else {
                    // Only one modifier contributed, display it normally
                    tooltip.accept(createModifierComponent(attr.value(), data.children.get(0)));
                }
            }

            // --- Step 3: Process and display non-mergeable modifiers ---
            nonMergeable.sort(ATTRIBUTE_MODIFIER_COMPARATOR);
            for (AttributeModifier modifier : nonMergeable) {
                tooltip.accept(createModifierComponent(attr.value(), modifier));
            }
        }
    }

    private static void appendBlockInteractionRangeTooltip(ItemStack stack, Consumer<Component> tooltip, @Nullable Player player, EquipmentSlotGroup slot, Multimap<Holder<Attribute>, AttributeModifier> itemModifiers, Set<Holder<Attribute>> handledAttributes) {
        if (!(player instanceof LocalPlayer localPlayer)) return;
        Holder<Attribute> blockRangeAttributeHolder = Attributes.BLOCK_INTERACTION_RANGE;
        AttributeInstance blockRangeInstance = localPlayer.getAttribute(blockRangeAttributeHolder);
        if (blockRangeInstance == null) {
            LOGGER.warn("Player {} missing attribute instance for {}", localPlayer.getName().getString(), BuiltInRegistries.ATTRIBUTE.getKey(blockRangeAttributeHolder.value()));
            return;
        }

        boolean shouldShowRange = false;
        boolean hasAttackSpeedModifier = itemModifiers.keySet().stream()
            .anyMatch(attrHolder -> attrHolder.value() == Attributes.ATTACK_SPEED.value());

        if (FabricLoader.getInstance().isModLoaded("bettercombat")) {
            WeaponAttributes weaponAttributes = WeaponRegistry.getAttributes(stack);
            // Show as a base attr if item has AS but NO BetterCombat attributes (it's not a weapon)
            if (hasAttackSpeedModifier && weaponAttributes == null) {
                shouldShowRange = true;
            }
        } else {
            // Show as a base attr if item is a DiggerItem
            if (stack.getItem() instanceof DiggerItem) {
                shouldShowRange = true;
            }
        }

        if (!shouldShowRange) return;

        // --- Calculate Final Range & Collect Modifiers ---
        double playerBaseValue = blockRangeInstance.getBaseValue();
        List<AttributeModifier> relevantModifiers = new ArrayList<>();

        // We need all modifiers affecting the player's *current* value for this attribute
        // This includes armor, buffs, AND the held item.
        // AttributeInstance.getValue() calculates this, but we want the breakdown.

        // Get all modifiers currently applied to the player for this attribute
        // This list INCLUDES the item's modifiers if it's held.
        Collection<AttributeModifier> allAppliedModifiers = blockRangeInstance.getModifiers(); 

        // Recalculate the value considering all applied modifiers
        double calculatedValue = playerBaseValue;
        // Apply ADD_VALUE first
        for (AttributeModifier mod : allAppliedModifiers) {
            if (mod.operation() == Operation.ADD_VALUE) {
                calculatedValue += mod.amount();
                relevantModifiers.add(mod); // Track for detailed view
            }
        }
        double valueAfterAdd = calculatedValue;
        // Apply ADD_MULTIPLIED_BASE next
        for (AttributeModifier mod : allAppliedModifiers) {
            if (mod.operation() == Operation.ADD_MULTIPLIED_BASE) {
                calculatedValue += playerBaseValue * mod.amount();
                 relevantModifiers.add(mod); // Track for detailed view
            }
        }
        // Apply ADD_MULTIPLIED_TOTAL last
        for (AttributeModifier mod : allAppliedModifiers) {
            if (mod.operation() == Operation.ADD_MULTIPLIED_TOTAL) {
                calculatedValue *= (1.0 + mod.amount());
                 relevantModifiers.add(mod); // Track for detailed view
            }
        }

        boolean isModified = Math.abs(calculatedValue - playerBaseValue) > 1e-4;

        // --- Generate Tooltip Lines & Mark as Handled ---
        MutableComponent mainLine = createBaseComponent(blockRangeAttributeHolder.value(), calculatedValue, playerBaseValue, isModified);
        tooltip.accept(Component.literal(" ").append(mainLine.withStyle(isModified ? MERGED_BASE_COLOR : BASE_COLOR)));

        if (isDetailedView() && isModified) {
            MutableComponent baseLine = createBaseComponent(blockRangeAttributeHolder.value(), playerBaseValue, playerBaseValue, false);
            tooltip.accept(listHeader().append(baseLine.withStyle(BASE_COLOR)));

            relevantModifiers.sort(ATTRIBUTE_MODIFIER_COMPARATOR);
            for (AttributeModifier mod : relevantModifiers) {
                if (mod.amount() != 0) {
                    tooltip.accept(listHeader().append(createModifierComponent(blockRangeAttributeHolder.value(), mod)));
                }
            }
        }

        handledAttributes.add(blockRangeAttributeHolder);
    }

    /**
     * Creates a text component for a base attribute
     */
    private static MutableComponent createBaseComponent(Attribute attribute, double value, double entityBase, boolean merged) {
        return Component.translatable("attribute.modifier.equals.0",
                FORMAT.format(value),
                Component.translatable(attribute.getDescriptionId()));
    }

    /**
     * Creates a text component for an attribute modifier
     */
    private static MutableComponent createModifierComponent(Attribute attribute, AttributeModifier modifier) {
        double value = modifier.amount();
        boolean isPositive = value > 0;

        String key = isPositive ?
                "attribute.modifier.plus." + modifier.operation().id() :
                "attribute.modifier.take." + modifier.operation().id();
        String formattedValue = formatValue(attribute, value, modifier.operation());
        MutableComponent component = Component.translatable(key,
                formattedValue,
                Component.translatable(attribute.getDescriptionId()));
        if (!isBaseAttribute(attribute) && modifier.id().equals(FAKE_MERGED_ID)) {
            return component.withStyle(style -> style.withColor(MERGED_MODIFIER_COLOR));
        }
        ChatFormatting color = getModifierFormatting(attribute, modifier, isPositive);
        return component.withStyle(color);
    }

    /**
     * Formats the attribute value based on attribute type and operation
     */
    private static String formatValue(Attribute attribute, double value, Operation operation) {
        double absValue = Math.abs(value);

        if (operation == Operation.ADD_VALUE) {
            if (attribute == Attributes.KNOCKBACK_RESISTANCE) {
                return FORMAT.format(absValue * 10); // Display as percentage x10
            } else {
                return FORMAT.format(absValue);
            }
        } else {
            return FORMAT.format(absValue * 100);
        }
    }

    /**
     * Determines the text color for a modifier
     */
    private static ChatFormatting getModifierFormatting(Attribute attribute, AttributeModifier modifier, boolean isPositive) {
        // Simplified: Always return Positive/Negative color. Caller handles special cases.
        // TODO: Clean this up
        return isPositive ? POSITIVE_COLOR : NEGATIVE_COLOR;
    }

    /**
     * Determines if an attribute is a base attribute (to be displayed differently)
     */
    private static boolean isBaseAttribute(Attribute attribute) {
        ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
        return id != null && BASE_ATTRIBUTE_IDS.contains(id);
    }

    /**
     * Checks if the modifier is a base modifier
     */
    private static boolean isBaseModifier(Attribute attribute, AttributeModifier modifier) {
        ResourceLocation baseId = getBaseModifierId(attribute);
        return modifier.id().equals(baseId);
    }

    /**
     * Gets the base modifier ID for an attribute
     */
    @Nullable
    private static ResourceLocation getBaseModifierId(Attribute attribute) {
        ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
        return id != null ? BASE_MODIFIER_IDS.get(id) : null;
    }

    /**
     * Creates the indentation symbol for nested attributes
     */
    static MutableComponent listHeader() {
        return Component.literal(" \u2507 ").withStyle(ChatFormatting.GRAY);
    }

    /**
     * Finds all attribute sections in a tooltip
     */
    private static List<AttributeSection> findAttributeSections(List<Component> tooltip) {
        List<AttributeSection> result = new ArrayList<>();
        for (int i = 0; i < tooltip.size(); i++) {
            Component line = tooltip.get(i);
            String content = line.getString();
            EquipmentSlotGroup slot = getSlotFromText(line);

            if (slot != null) {
                int numLines = countAttributeLines(tooltip, i + 1);
                if (numLines > 0) {
                    result.add(new AttributeSection(i, numLines, slot));
                }
            }
        }
        return result;
    }

    /**
     * Maps tooltip text to an attribute modifier slot
     */
    public static EquipmentSlotGroup getSlotFromText(Component text) {
        String content = text.getString();
        return KEY_SLOT_MAP.get(content);
    }

    /**
     * Counts the number of attribute lines in a section
     */
    private static int countAttributeLines(List<Component> tooltip, int startIndex) {
        int count = 0;
        for (int i = startIndex; i < tooltip.size(); i++) {
            Component lineComp = tooltip.get(i);
            String line = lineComp.getString();

            if (line.isEmpty() || getSlotFromText(lineComp) != null) {
                break;
            }

            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                 break;
            }
            char firstChar = trimmedLine.charAt(0);
            boolean looksLikeModifier = line.startsWith(" ") || firstChar == '+' || firstChar == '-' || Character.isDigit(firstChar) || firstChar == '(';

            if (!looksLikeModifier) {
                 break;
            }

            count++;
        }

        return count;
    }

    // Make this public static so it can be accessed by itemHasExpandableAttributes
    public static class BaseModifier {
        final AttributeModifier base;
        final List<AttributeModifier> children;

        BaseModifier(AttributeModifier base, List<AttributeModifier> children) {
            this.base = base;
            this.children = children;
        }
    }

    /**
     * Stores merged modifier data for a specific operation
     */
    private static class MergedModifierData {
        double sum = 0;
        boolean isMerged = false;
        List<AttributeModifier> children = new ArrayList<>();
    }

    /**
     * Represents a section of attribute modifiers in the tooltip
     */
    private static class AttributeSection {
        final int startIndex;
        final int lineCount;
        final EquipmentSlotGroup slot;

        AttributeSection(int startIndex, int lineCount, EquipmentSlotGroup slot) {
            this.startIndex = startIndex;
            this.lineCount = lineCount;
            this.slot = slot;
        }
    }
}