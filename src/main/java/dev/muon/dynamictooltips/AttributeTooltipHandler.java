package dev.muon.dynamictooltips;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.registry.Registries;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.function.Consumer;

/**
 * Merged Attribute Modifier tooltips, inspired by NeoForge.
 */
public class AttributeTooltipHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("DynamicTooltips-Attributes");
    private static final DecimalFormat FORMAT = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT));
    private static final Identifier FAKE_MERGED_ID = Identifier.of(DynamicTooltips.MODID, "fake_merged_modifier");

    private static final Formatting BASE_COLOR = Formatting.DARK_GREEN;
    private static final Formatting MERGED_BASE_COLOR = Formatting.GOLD;
    private static final int MERGED_MODIFIER_COLOR = 7699710; // Light Blue
    private static final Formatting POSITIVE_COLOR = Formatting.BLUE;
    private static final Formatting NEGATIVE_COLOR = Formatting.RED;

    private static final Comparator<EntityAttributeModifier> ATTRIBUTE_MODIFIER_COMPARATOR =
            Comparator.comparing(EntityAttributeModifier::operation)
                    .thenComparing((EntityAttributeModifier a) -> -Math.abs(a.value()))
                    .thenComparing(EntityAttributeModifier::id);

    private static final Map<String, AttributeModifierSlot> KEY_SLOT_MAP = Util.make(new HashMap<>(), map -> {
        map.put(Text.translatable("item.modifiers.mainhand").getString(), AttributeModifierSlot.MAINHAND);
        map.put(Text.translatable("item.modifiers.offhand").getString(), AttributeModifierSlot.OFFHAND);
        map.put(Text.translatable("item.modifiers.head").getString(), AttributeModifierSlot.HEAD);
        map.put(Text.translatable("item.modifiers.chest").getString(), AttributeModifierSlot.CHEST);
        map.put(Text.translatable("item.modifiers.legs").getString(), AttributeModifierSlot.LEGS);
        map.put(Text.translatable("item.modifiers.feet").getString(), AttributeModifierSlot.FEET);
        map.put(Text.translatable("item.modifiers.body").getString(), AttributeModifierSlot.BODY);
        map.put(Text.translatable("tiered.slot.feet").getString(), AttributeModifierSlot.FEET);
        map.put(Text.translatable("tiered.slot.head").getString(), AttributeModifierSlot.HEAD);
        map.put(Text.translatable("tiered.slot.chest").getString(), AttributeModifierSlot.CHEST);
        map.put(Text.translatable("tiered.slot.legs").getString(), AttributeModifierSlot.LEGS);
        map.put(Text.translatable("tiered.slot.body").getString(), AttributeModifierSlot.BODY);
    });

    private static final Set<Identifier> BASE_ATTRIBUTE_IDS = Util.make(new HashSet<>(), set -> {
        set.add(Registries.ATTRIBUTE.getId(EntityAttributes.GENERIC_ATTACK_DAMAGE.value()));
        set.add(Registries.ATTRIBUTE.getId(EntityAttributes.GENERIC_ATTACK_SPEED.value()));
        set.add(Registries.ATTRIBUTE.getId(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE.value()));
        set.add(Identifier.of("ranged_weapon", "damage"));
        set.add(Identifier.of("ranged_weapon", "pull_time"));
        set.remove(null);
    });

    private static final Map<Identifier, Identifier> BASE_MODIFIER_IDS = Util.make(new HashMap<>(), map -> {
        map.put(Registries.ATTRIBUTE.getId(EntityAttributes.GENERIC_ATTACK_DAMAGE.value()), Item.BASE_ATTACK_DAMAGE_MODIFIER_ID);
        map.put(Registries.ATTRIBUTE.getId(EntityAttributes.GENERIC_ATTACK_SPEED.value()), Item.BASE_ATTACK_SPEED_MODIFIER_ID);
        map.put(Registries.ATTRIBUTE.getId(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE.value()), Identifier.ofVanilla("base_entity_reach"));
        map.put(Identifier.of("ranged_weapon", "damage"), Identifier.of("ranged_weapon", "base_damage"));
        map.put(Identifier.of("ranged_weapon", "pull_time"), Identifier.of("ranged_weapon", "base_pull_time"));
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
    public static void processTooltip(ItemStack stack, List<Text> tooltip, @Nullable PlayerEntity player) {
        // Check if the item has any attribute sections
        List<AttributeSection> sections = findAttributeSections(tooltip);
        if (sections.isEmpty()) {
            return;
        }

        // Process the tooltip
        List<Text> newTooltip = new ArrayList<>();
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
            Multimap<RegistryEntry<EntityAttribute>, EntityAttributeModifier> modifiers = getSortedModifiers(stack, section.slot);
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
    private static Multimap<RegistryEntry<EntityAttribute>, EntityAttributeModifier> getSortedModifiers(ItemStack stack, AttributeModifierSlot slot) {
        Multimap<RegistryEntry<EntityAttribute>, EntityAttributeModifier> map = LinkedListMultimap.create();

        stack.applyAttributeModifier(slot, (attributeHolder, modifier) -> {
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
            Consumer<Text> tooltip,
            Multimap<RegistryEntry<EntityAttribute>, EntityAttributeModifier> modifierMap,
            @Nullable PlayerEntity player) {

        if (modifierMap.isEmpty()) {
            return;
        }

        Map<RegistryEntry<EntityAttribute>, BaseModifier> baseModifiers = new Reference2ReferenceLinkedOpenHashMap<>();

        Multimap<RegistryEntry<EntityAttribute>, EntityAttributeModifier> remainingModifiers = LinkedListMultimap.create();
        remainingModifiers.putAll(modifierMap);

        var it = remainingModifiers.entries().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            RegistryEntry<EntityAttribute> attr = entry.getKey();
            EntityAttributeModifier modifier = entry.getValue();

            if (isBaseModifier(attr.value(), modifier)) {
                baseModifiers.put(attr, new BaseModifier(modifier, new ArrayList<>()));
                it.remove();
            }
        }

        it = remainingModifiers.entries().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            RegistryEntry<EntityAttribute> attr = entry.getKey();
            EntityAttributeModifier modifier = entry.getValue();

            if (isBaseAttribute(attr.value())) {
                BaseModifier base = baseModifiers.get(attr);
                if (base != null) {
                    base.children.add(modifier);
                    it.remove();
                }
            }
        }

        for (var entry : baseModifiers.entrySet()) {
            RegistryEntry<EntityAttribute> attr = entry.getKey();
            BaseModifier baseModifier = entry.getValue();

            double entityBase = player == null ? 0 : player.getAttributeBaseValue(attr);
            double base = baseModifier.base.value() + entityBase;
            final double rawBase = base;
            double amount = base;

            for (EntityAttributeModifier modifier : baseModifier.children) {
                switch (modifier.operation()) {
                    case ADD_VALUE:
                        base = amount = amount + modifier.value();
                        break;
                    case ADD_MULTIPLIED_BASE:
                        amount += modifier.value() * rawBase;
                        break;
                    case ADD_MULTIPLIED_TOTAL:
                        amount *= 1.0 + modifier.value();
                        break;
                }
            }

            boolean isMerged = !baseModifier.children.isEmpty();

            MutableText text = createBaseComponent(attr.value(), amount, entityBase, isMerged);
            tooltip.accept(Text.literal(" ").append(text.formatted(isMerged ? MERGED_BASE_COLOR : BASE_COLOR)));

            if (isDetailedView() && isMerged) {
                text = createBaseComponent(attr.value(), rawBase, entityBase, false);
                tooltip.accept(listHeader().append(text.formatted(BASE_COLOR)));

                for (EntityAttributeModifier modifier : baseModifier.children) {
                    tooltip.accept(listHeader().append(createModifierComponent(attr.value(), modifier)));
                }
            }
        }

        for (RegistryEntry<EntityAttribute> attr : remainingModifiers.keySet()) {
            if (baseModifiers.containsKey(attr)) {
                continue;
            }
            Collection<EntityAttributeModifier> modifiers = remainingModifiers.get(attr);
            boolean isBaseAttr = isBaseAttribute(attr.value());
            if (modifiers.size() > 1) {
                Map<Operation, MergedModifierData> mergeData = new EnumMap<>(Operation.class);

                for (EntityAttributeModifier modifier : modifiers) {
                    if (modifier.value() == 0) {
                        continue;
                    }

                    MergedModifierData data = mergeData.computeIfAbsent(modifier.operation(), op -> new MergedModifierData());
                    if (data.sum != 0) {
                        data.isMerged = true;
                    }
                    data.sum += modifier.value();
                    data.children.add(modifier);
                }
                for (Operation op : Operation.values()) {
                    MergedModifierData data = mergeData.get(op);
                    if (data == null || data.sum == 0) {
                        continue;
                    }
                    if (data.isMerged) {
                        EntityAttributeModifier fakeModifier = new EntityAttributeModifier(
                                FAKE_MERGED_ID, data.sum, op);

                        MutableText modComponent = createModifierComponent(attr.value(), fakeModifier);
                        if (isBaseAttr) {
                            tooltip.accept(modComponent.formatted(MERGED_BASE_COLOR));
                        } else {
                            tooltip.accept(modComponent.styled(style -> style.withColor(MERGED_MODIFIER_COLOR)));
                        }

                        if (isDetailedView()) {
                            for (EntityAttributeModifier mod : data.children) {
                                tooltip.accept(listHeader().append(createModifierComponent(attr.value(), mod)));
                            }
                        }
                    } else {
                        EntityAttributeModifier fakeModifier = new EntityAttributeModifier(
                                FAKE_MERGED_ID, data.sum, op);

                        tooltip.accept(createModifierComponent(attr.value(), fakeModifier));
                    }
                }
            } else {
                for (EntityAttributeModifier modifier : modifiers) {
                    if (modifier.value() != 0) {
                        tooltip.accept(createModifierComponent(attr.value(), modifier));
                    }
                }
            }
        }
    }

    /**
     * Creates a text component for a base attribute
     */
    private static MutableText createBaseComponent(EntityAttribute attribute, double value, double entityBase, boolean merged) {
        return Text.translatable("attribute.modifier.equals.0",
                FORMAT.format(value),
                Text.translatable(attribute.getTranslationKey()));
    }

    /**
     * Creates a text component for an attribute modifier
     */
    private static MutableText createModifierComponent(EntityAttribute attribute, EntityAttributeModifier modifier) {
        double value = modifier.value();
        boolean isPositive = value > 0;

        String key = isPositive ?
                "attribute.modifier.plus." + modifier.operation().getId() :
                "attribute.modifier.take." + modifier.operation().getId();
        String formattedValue = formatValue(attribute, value, modifier.operation());
        MutableText component = Text.translatable(key,
                formattedValue,
                Text.translatable(attribute.getTranslationKey()));
        if (!isBaseAttribute(attribute) && modifier.id().equals(FAKE_MERGED_ID)) {
            return component.styled(style -> style.withColor(MERGED_MODIFIER_COLOR));
        }
        Formatting color = getModifierFormatting(attribute, modifier, isPositive);
        return component.formatted(color);
    }

    /**
     * Formats the attribute value based on attribute type and operation
     */
    private static String formatValue(EntityAttribute attribute, double value, Operation operation) {
        double absValue = Math.abs(value);

        if (operation == Operation.ADD_VALUE) {
            if (attribute == EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE) {
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
    private static Formatting getModifierFormatting(EntityAttribute attribute, EntityAttributeModifier modifier, boolean isPositive) {
        if (isBaseModifier(attribute, modifier)) {
            return BASE_COLOR;
        }
        if (isBaseAttribute(attribute) && modifier.id().equals(FAKE_MERGED_ID)) {
            return MERGED_BASE_COLOR;
        }
        return isPositive ? POSITIVE_COLOR : NEGATIVE_COLOR;
    }

    /**
     * Determines if an attribute is a base attribute (to be displayed differently)
     */
    private static boolean isBaseAttribute(EntityAttribute attribute) {
        Identifier id = Registries.ATTRIBUTE.getId(attribute);
        return id != null && BASE_ATTRIBUTE_IDS.contains(id);
    }

    /**
     * Checks if the modifier is a base modifier
     */
    private static boolean isBaseModifier(EntityAttribute attribute, EntityAttributeModifier modifier) {
        Identifier baseId = getBaseModifierId(attribute);
        return modifier.id().equals(baseId);
    }

    /**
     * Gets the base modifier ID for an attribute
     */
    @Nullable
    private static Identifier getBaseModifierId(EntityAttribute attribute) {
        Identifier id = Registries.ATTRIBUTE.getId(attribute);
        return id != null ? BASE_MODIFIER_IDS.get(id) : null;
    }

    /**
     * Creates the indentation symbol for nested attributes
     */
    private static MutableText listHeader() {
        return Text.literal(" \u2507 ").formatted(Formatting.GRAY);
    }

    /**
     * Finds all attribute sections in a tooltip
     */
    private static List<AttributeSection> findAttributeSections(List<Text> tooltip) {
        List<AttributeSection> result = new ArrayList<>();
        for (int i = 0; i < tooltip.size(); i++) {
            Text line = tooltip.get(i);
            String content = line.getString();
            AttributeModifierSlot slot = getSlotFromText(line);

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
    @Nullable
    private static AttributeModifierSlot getSlotFromText(Text text) {
        String content = text.getString();
        return KEY_SLOT_MAP.get(content);
    }

    /**
     * Counts the number of attribute lines in a section
     */
    private static int countAttributeLines(List<Text> tooltip, int startIndex) {
        int count = 0;
        for (int i = startIndex; i < tooltip.size(); i++) {
            String line = tooltip.get(i).getString();

            // Stop if we hit an empty line or another section header
            if (line.isEmpty() || getSlotFromText(Text.literal(line)) != null) {
                break;
            }

            // This looks like an attribute line
            count++;
        }

        return count;
    }

    /**
     * Determines if an item has attribute modifiers to process
     */
    private static boolean hasEnhanceableAttributeModifiers(ItemStack stack) {
        // First check the component for fast rejection
        AttributeModifiersComponent component = stack.getOrDefault(
                DataComponentTypes.ATTRIBUTE_MODIFIERS,
                AttributeModifiersComponent.DEFAULT
        );
        
        if (!component.showInTooltip()) {
            return false;
        }
        
        // Even if the component's modifiers list is empty, check for direct attribute access
        for (AttributeModifierSlot slot : AttributeModifierSlot.values()) {
            final boolean[] hasModifiers = {false};
            stack.applyAttributeModifier(slot, (attributeHolder, modifier) -> {
                if (attributeHolder != null && modifier != null) {
                    hasModifiers[0] = true;
                }
            });
            
            if (hasModifiers[0]) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Stores a single base modifier and its children
     */
    private static class BaseModifier {
        final EntityAttributeModifier base;
        final List<EntityAttributeModifier> children;

        BaseModifier(EntityAttributeModifier base, List<EntityAttributeModifier> children) {
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
        List<EntityAttributeModifier> children = new ArrayList<>();
    }

    /**
     * Represents a section of attribute modifiers in the tooltip
     */
    private static class AttributeSection {
        final int startIndex;
        final int lineCount;
        final AttributeModifierSlot slot;

        AttributeSection(int startIndex, int lineCount, AttributeModifierSlot slot) {
            this.startIndex = startIndex;
            this.lineCount = lineCount;
            this.slot = slot;
        }
    }
}