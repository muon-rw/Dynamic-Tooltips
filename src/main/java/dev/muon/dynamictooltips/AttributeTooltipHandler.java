package dev.muon.dynamictooltips;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import net.fabric_extras.ranged_weapon.api.AttributeModifierIDs;
import net.fabric_extras.ranged_weapon.api.CustomRangedWeapon;
import net.fabric_extras.ranged_weapon.api.EntityAttributes_RangedWeapon;
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

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
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
import net.fabricmc.loader.api.FabricLoader;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.fabric_extras.ranged_weapon.api.RangedConfig;
import net.minecraft.world.entity.ai.attributes.Attribute.Sentiment;


/**
 * Merged Attribute Modifier tooltips, inspired by NeoForge.
 */
public class AttributeTooltipHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("DynamicTooltips-Attributes");
    private static final DecimalFormat FORMAT = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT));
    private static final ResourceLocation FAKE_MERGED_ID = ResourceLocation.fromNamespaceAndPath(DynamicTooltips.MODID, "fake_merged_modifier");

    static final ChatFormatting BASE_COLOR = ChatFormatting.DARK_GREEN;
    public static final int MERGE_BASE_MODIFIER_COLOR = 16758784; // Gold
    public static final int MERGED_MODIFIER_COLOR = 7699710; // Light Blue

    private enum ColorLogic {
        DEFAULT, // Use the attribute's default sentiment coloring via getStyle()
        INVERTED, // Invert the attribute's default sentiment coloring
        FIXED // Always use a specific color, regardless of sign or sentiment
    }

    private record AttributeColorRule(ColorLogic logic, @Nullable ChatFormatting fixedColor) {}

    // Map for attribute color rule overrides
    // TODO: Populate this map if needed, or add user config
    private static final Map<ResourceLocation, AttributeColorRule> ATTRIBUTE_COLOR_RULES = Util.make(new HashMap<>(), map -> {
        map.put(ResourceLocation.fromNamespaceAndPath("additionalentityattributes", "generic.hitbox_height"), new AttributeColorRule(ColorLogic.FIXED, ChatFormatting.GRAY));
        map.put(ResourceLocation.fromNamespaceAndPath("additionalentityattributes", "generic.hitbox_width"), new AttributeColorRule(ColorLogic.FIXED, ChatFormatting.GRAY));
        map.put(ResourceLocation.fromNamespaceAndPath("additionalentityattributes", "generic.model_height"), new AttributeColorRule(ColorLogic.FIXED, ChatFormatting.GRAY));
        map.put(ResourceLocation.fromNamespaceAndPath("additionalentityattributes", "generic.model_width"), new AttributeColorRule(ColorLogic.FIXED, ChatFormatting.GRAY));
        map.put(ResourceLocation.fromNamespaceAndPath("additionalentityattributes", "generic.height"), new AttributeColorRule(ColorLogic.FIXED, ChatFormatting.GRAY));
        map.put(ResourceLocation.fromNamespaceAndPath("additionalentityattributes", "generic.width"), new AttributeColorRule(ColorLogic.FIXED, ChatFormatting.GRAY));
        map.put(ResourceLocation.fromNamespaceAndPath("additionalentityattributes", "generic.model_scale"), new AttributeColorRule(ColorLogic.FIXED, ChatFormatting.GRAY));

        map.put(ResourceLocation.fromNamespaceAndPath("additionalentityattributes", "generic.mob_detection_range"), new AttributeColorRule(ColorLogic.INVERTED, null));
        map.remove(null);
    });

    static final Comparator<AttributeModifier> ATTRIBUTE_MODIFIER_COMPARATOR =
            Comparator.comparing(AttributeModifier::operation)
                    .thenComparing((AttributeModifier a) -> -Math.abs(a.amount()))
                    .thenComparing(AttributeModifier::id);

    private static final Map<String, EquipmentSlotGroup> KEY_SLOT_MAP = Util.make(new HashMap<>(), map -> {
        map.put(Component.translatable("item.modifiers.mainhand").getString(), EquipmentSlotGroup.MAINHAND);
        map.put(Component.translatable("item.modifiers.offhand").getString(), EquipmentSlotGroup.OFFHAND);
        map.put(Component.translatable("item.modifiers.hand").getString(), EquipmentSlotGroup.HAND);
        map.put(Component.translatable("item.modifiers.head").getString(), EquipmentSlotGroup.HEAD);
        map.put(Component.translatable("item.modifiers.chest").getString(), EquipmentSlotGroup.CHEST);
        map.put(Component.translatable("item.modifiers.legs").getString(), EquipmentSlotGroup.LEGS);
        map.put(Component.translatable("item.modifiers.feet").getString(), EquipmentSlotGroup.FEET);
        map.put(Component.translatable("item.modifiers.body").getString(), EquipmentSlotGroup.BODY);
        map.put(Component.translatable("item.modifiers.armor").getString(), EquipmentSlotGroup.ARMOR);
        map.put(Component.translatable("tiered.slot.feet").getString(), EquipmentSlotGroup.FEET);
        map.put(Component.translatable("tiered.slot.head").getString(), EquipmentSlotGroup.HEAD);
        map.put(Component.translatable("tiered.slot.chest").getString(), EquipmentSlotGroup.CHEST);
        map.put(Component.translatable("tiered.slot.legs").getString(), EquipmentSlotGroup.LEGS);
        map.put(Component.translatable("tiered.slot.body").getString(), EquipmentSlotGroup.BODY);
        map.remove(null);
    });

    // Attributes that should be treated as "base" modifiers: Display a base value as green, gold when merged
    private static final Set<ResourceLocation> BASE_ATTRIBUTE_IDS = Util.make(new HashSet<>(), set -> {
        set.add(BuiltInRegistries.ATTRIBUTE.getKey(Attributes.ATTACK_DAMAGE.value()));
        set.add(BuiltInRegistries.ATTRIBUTE.getKey(Attributes.ATTACK_SPEED.value()));
        set.add(BuiltInRegistries.ATTRIBUTE.getKey(Attributes.ENTITY_INTERACTION_RANGE.value()));
        set.add(ResourceLocation.fromNamespaceAndPath("ranged_weapon", "damage"));
        set.add(ResourceLocation.fromNamespaceAndPath("ranged_weapon", "pull_time"));
        set.remove(null);
    });

    // TODO: Can these be inferred safely?
    private static final Map<ResourceLocation, ResourceLocation> BASE_MODIFIER_IDS = Util.make(new HashMap<>(), map -> {
        map.put(BuiltInRegistries.ATTRIBUTE.getKey(Attributes.ATTACK_DAMAGE.value()), Item.BASE_ATTACK_DAMAGE_ID);
        map.put(BuiltInRegistries.ATTRIBUTE.getKey(Attributes.ATTACK_SPEED.value()), Item.BASE_ATTACK_SPEED_ID);
        map.put(ResourceLocation.fromNamespaceAndPath("ranged_weapon", "damage"), ResourceLocation.fromNamespaceAndPath("ranged_weapon", "base_damage"));
        map.put(ResourceLocation.fromNamespaceAndPath("ranged_weapon", "pull_time"), ResourceLocation.fromNamespaceAndPath("ranged_weapon", "base_pull_time"));
        map.remove(null);
    });


    public static boolean isDetailedView() {
        return Screen.hasShiftDown();
    }


    public static ProcessingResult processTooltip(ItemStack stack, List<Component> tooltip, @Nullable Player player) {
        List<AttributeSection> sections = findAttributeSections(tooltip);
        if (sections.isEmpty()) {
            return ProcessingResult.NO_CHANGE;
        }

        EquipmentSlotGroup primarySlotGroup = null;
        Set<EquipmentSlotGroup> additionalSlotGroups = new HashSet<>();

        // Priority order for determining the primary slot group if multiple sections are found
        List<EquipmentSlotGroup> priorityOrder = List.of(
            EquipmentSlotGroup.HEAD, EquipmentSlotGroup.CHEST, EquipmentSlotGroup.LEGS, EquipmentSlotGroup.FEET, // Specific Armor
            EquipmentSlotGroup.MAINHAND,
            EquipmentSlotGroup.HAND,
            EquipmentSlotGroup.ARMOR,
            EquipmentSlotGroup.BODY
        );

        for (EquipmentSlotGroup potentialPrimary : priorityOrder) {
            for (AttributeSection section : sections) {
                if (section.slot == potentialPrimary) {
                    primarySlotGroup = potentialPrimary;
                    break;
                }
            }
            if (primarySlotGroup != null) break;
        }

        if (primarySlotGroup == null) {
             LOGGER.debug("No relevant primary slot group identified from tooltip sections for {}, skipping.", stack.getItem());
             return ProcessingResult.NO_CHANGE;
        }

        // Include generic ARMOR group if a specific armor slot was the primary
        if (primarySlotGroup == EquipmentSlotGroup.HEAD ||
            primarySlotGroup == EquipmentSlotGroup.CHEST ||
            primarySlotGroup == EquipmentSlotGroup.LEGS ||
            primarySlotGroup == EquipmentSlotGroup.FEET ||
            primarySlotGroup == EquipmentSlotGroup.BODY) {
             additionalSlotGroups.add(EquipmentSlotGroup.ARMOR);
        }

        Multimap<Holder<Attribute>, AttributeModifier> combinedModifiers = LinkedListMultimap.create();
        combinedModifiers.putAll(getSortedModifiers(stack, primarySlotGroup));

        for(EquipmentSlotGroup additionalGroup : additionalSlotGroups) {
             Multimap<Holder<Attribute>, AttributeModifier> additionalModifiers = getSortedModifiers(stack, additionalGroup);
             addNonDuplicateModifiers(combinedModifiers, additionalModifiers);
        }

        EquipmentSlotGroup groupForHeader = primarySlotGroup;

        if (combinedModifiers.isEmpty()) {
            return ProcessingResult.NO_CHANGE;
        }

        List<Component> newTooltip = new ArrayList<>();
        int currentOriginalIndex = 0;
        List<AttributeSection> sortedSections = new ArrayList<>(sections);
        sortedSections.sort(Comparator.comparingInt(s -> s.startIndex));
        AttributeSection firstSectionOverall = sortedSections.get(0);
        AttributeSection lastSectionOverall = sortedSections.get(sortedSections.size() - 1);
        int endOfLastSectionIndex = lastSectionOverall.startIndex + lastSectionOverall.lineCount;

        while (currentOriginalIndex < firstSectionOverall.startIndex) {
            newTooltip.add(tooltip.get(currentOriginalIndex++));
        }

        // --- Add "Two-Handed" line if applicable (Better Combat Integration) ---
        // Necessary because we cancel the original two-handed injection with a mixin
        if (FabricLoader.getInstance().isModLoaded("bettercombat")) {
            WeaponAttributes weaponAttributes = WeaponRegistry.getAttributes(stack);
            if (weaponAttributes != null && weaponAttributes.isTwoHanded()) {
                newTooltip.add(Component.translatable("item.held.two_handed").withStyle(ChatFormatting.GRAY));
            }
        }
        // --- End Better Combat Integration ---

        Component finalHeader = getHeaderForSlotGroup(groupForHeader);
        newTooltip.add(finalHeader);

        TooltipApplyResult applyResult = applyTextFor(stack, newTooltip::add, combinedModifiers, player);

        // Skip original attribute lines
        currentOriginalIndex = endOfLastSectionIndex + 1;
        while (currentOriginalIndex < tooltip.size()) {
            newTooltip.add(tooltip.get(currentOriginalIndex++));
        }

        tooltip.clear();
        tooltip.addAll(newTooltip);
        return new ProcessingResult(true, finalHeader, applyResult.needsShiftPrompt);
    }


    private static void addNonDuplicateModifiers(
            Multimap<Holder<Attribute>, AttributeModifier> target,
            Multimap<Holder<Attribute>, AttributeModifier> source) {

        Set<String> existingIds = new HashSet<>();
        target.forEach((attrHolder, mod) -> {
            ResourceLocation attrId = BuiltInRegistries.ATTRIBUTE.getKey(attrHolder.value());
            if (attrId != null) {
                existingIds.add(attrId + ":" + mod.id());
            }
        });

        source.forEach((attrHolder, mod) -> {
            ResourceLocation attrId = BuiltInRegistries.ATTRIBUTE.getKey(attrHolder.value());
            if (attrId != null) {
                String key = attrId + ":" + mod.id();
                if (!existingIds.contains(key)) {
                    target.put(attrHolder, mod);
                }
            }
        });
    }


    private static Component getHeaderForSlotGroup(EquipmentSlotGroup group) {
        // Construct the key dynamically based on the enum constant name
        String groupName = group.name().toLowerCase(Locale.ROOT);
        String key = "item.modifiers." + groupName;

        if (!key.startsWith("item.modifiers.")) { 
             LOGGER.warn("Generated invalid key for EquipmentSlotGroup header: {} from group {}", key, group);
             key = "item.modifiers.body"; // Fallback to "When Worn:"
        }

        return Component.translatable(key).withStyle(ChatFormatting.GRAY);
    }


    private static Multimap<Holder<Attribute>, AttributeModifier> getSortedModifiers(ItemStack stack, EquipmentSlotGroup slot) {
        Multimap<Holder<Attribute>, AttributeModifier> map = LinkedListMultimap.create();

        stack.forEachModifier(slot, (attributeHolder, modifier) -> {
            if (attributeHolder != null && modifier != null) {
                map.put(attributeHolder, modifier);
            }
        });

        // --- Ranged Weapon API Integration ---
        if (FabricLoader.getInstance().isModLoaded("ranged_weapon_api")) {
            Item item = stack.getItem();
            if (item instanceof CustomRangedWeapon customRangedWeapon && (slot == EquipmentSlotGroup.MAINHAND || slot == EquipmentSlotGroup.HAND)) {
                // Manually add RWA attrs to the tooltip of relevant weapons if not already present
                boolean hasBaseDamage = map.values().stream().anyMatch(mod -> mod.id().equals(AttributeModifierIDs.WEAPON_DAMAGE_ID));
                if (!hasBaseDamage) {
                    try {
                        RangedConfig config = customRangedWeapon.getTypeBaseline();
                        double baseDamage = config.damage();
                        if (baseDamage != 0) {
                            Holder<Attribute> damageAttr = EntityAttributes_RangedWeapon.DAMAGE.entry;
                            AttributeModifier baseDamageMod = new AttributeModifier(AttributeModifierIDs.WEAPON_DAMAGE_ID, baseDamage, Operation.ADD_VALUE);
                            map.put(damageAttr, baseDamageMod);
                        }
                    } catch (Exception e) {
                         LOGGER.warn("Failed to get RWA base damage for {}: {}", stack, e.getMessage());
                    }
                }

                boolean hasBasePullTime = map.values().stream().anyMatch(mod -> mod.id().equals(AttributeModifierIDs.WEAPON_PULL_TIME_ID));
                 if (!hasBasePullTime) {
                     try {
                         RangedConfig config = customRangedWeapon.getTypeBaseline();
                         double pullTimeBonus = config.pull_time_bonus();
                         Holder<Attribute> pullTimeAttr = EntityAttributes_RangedWeapon.PULL_TIME.entry;
                         AttributeModifier basePullTimeMod = new AttributeModifier(AttributeModifierIDs.WEAPON_PULL_TIME_ID, pullTimeBonus, Operation.ADD_VALUE);
                         map.put(pullTimeAttr, basePullTimeMod);
                     } catch (Exception e) {
                         LOGGER.warn("Failed to get RWA base pull time for {}: {}", stack, e.getMessage());
                     }
                 }
            }
        }
        // --- End RWA Integration ---

        return map;
    }

    // Helper class to track results of applyTextFor
    public static class TooltipApplyResult {
        boolean needsShiftPrompt = false;
        Set<Holder<Attribute>> handledAttributes = new HashSet<>();
    }


    private static TooltipApplyResult applyTextFor(
            ItemStack stack,
            Consumer<Component> tooltip,
            Multimap<Holder<Attribute>, AttributeModifier> modifierMap,
            @Nullable Player player) {

        TooltipApplyResult result = new TooltipApplyResult();
        if (modifierMap.isEmpty()) {
            return result;
        }

        Map<Holder<Attribute>, BaseModifier> baseModifiers = new Reference2ReferenceLinkedOpenHashMap<>();
        Multimap<Holder<Attribute>, AttributeModifier> remainingModifiers = LinkedListMultimap.create();

        separateBaseModifiers(modifierMap, baseModifiers, remainingModifiers);
        processBaseModifiers(stack, tooltip, player, baseModifiers, result);
        processRemainingModifiers(stack, tooltip, player, modifierMap, remainingModifiers, baseModifiers.keySet(), result);

        // Block Interaction Range tooltip is appended separately as it depends on player state
        appendBlockInteractionRangeTooltip(stack, tooltip, player, EquipmentSlotGroup.MAINHAND, modifierMap, result);

        return result;
    }


    private static void separateBaseModifiers(
            Multimap<Holder<Attribute>, AttributeModifier> modifierMap,
            Map<Holder<Attribute>, BaseModifier> baseModifiersOutput,
            Multimap<Holder<Attribute>, AttributeModifier> remainingModifiersOutput) {

        remainingModifiersOutput.putAll(modifierMap);
        var it = remainingModifiersOutput.entries().iterator();

        while (it.hasNext()) {
            var entry = it.next();
            Holder<Attribute> attr = entry.getKey();
            AttributeModifier modifier = entry.getValue();

            if (isBaseModifier(attr.value(), modifier)) {
                baseModifiersOutput.put(attr, new BaseModifier(modifier, new ArrayList<>()));
                it.remove();
            }
        }

        it = remainingModifiersOutput.entries().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Holder<Attribute> attr = entry.getKey();
            AttributeModifier modifier = entry.getValue();
            BaseModifier base = baseModifiersOutput.get(attr);

            if (base != null && isBaseAttribute(attr.value())) {
                 base.children.add(modifier);
                 it.remove();
            }
        }
    }


    private static void processBaseModifiers(
            ItemStack stack,
            Consumer<Component> tooltip,
            @Nullable Player player,
            Map<Holder<Attribute>, BaseModifier> baseModifiers,
            TooltipApplyResult result) {

        for (var entry : baseModifiers.entrySet()) {
            Holder<Attribute> attr = entry.getKey();
            BaseModifier baseModifier = entry.getValue();

            double entityBase = player == null ? 0 : player.getAttributeBaseValue(attr);
            double baseValueFromModifier = baseModifier.base.amount();
            double rawBaseValue = baseValueFromModifier + entityBase;
            double finalValue = rawBaseValue;

            for (AttributeModifier childModifier : baseModifier.children) {
                finalValue = applyModifier(finalValue, rawBaseValue, childModifier);
            }

            boolean isMerged = !baseModifier.children.isEmpty();
            result.needsShiftPrompt |= isMerged;

            MutableComponent text = createBaseComponent(attr.value(), finalValue, entityBase, isMerged);
            ChatFormatting color = isMerged ? null : BASE_COLOR;
            Integer intColor = isMerged ? MERGE_BASE_MODIFIER_COLOR : null;
            tooltip.accept(Component.literal(" ").append(text.withStyle(style -> {
                 if (intColor != null) return style.withColor(intColor);
                 if (color != null) return style.applyFormat(color);
                 return style;
             })));

            if (isDetailedView() && isMerged) {
                text = createBaseComponent(attr.value(), rawBaseValue, entityBase, false);
                tooltip.accept(listHeader().append(text.withStyle(BASE_COLOR)));

                baseModifier.children.sort(ATTRIBUTE_MODIFIER_COMPARATOR);
                for (AttributeModifier modifier : baseModifier.children) {
                    tooltip.accept(listHeader().append(createModifierComponent(attr.value(), modifier)));
                }
            }

            // --- INTEGRATION POINT for Attack Range ---
             if (attr.value() == Attributes.ATTACK_SPEED.value()) {
                 AttackRangeTooltipHandler.appendAttackRangeLines(stack, tooltip, player, result);
             }
             // --- END INTEGRATION POINT ---

            result.handledAttributes.add(attr);
        }
    }


    private static double applyModifier(double currentValue, double baseValue, AttributeModifier modifier) {
        return switch (modifier.operation()) {
            case ADD_VALUE -> currentValue + modifier.amount();
            case ADD_MULTIPLIED_BASE -> currentValue + modifier.amount() * baseValue;
            case ADD_MULTIPLIED_TOTAL -> currentValue * (1.0 + modifier.amount());
        };
    }


    private static void processRemainingModifiers(
            ItemStack stack,
            Consumer<Component> tooltip,
            @Nullable Player player,
            Multimap<Holder<Attribute>, AttributeModifier> originalModifierMap,
            Multimap<Holder<Attribute>, AttributeModifier> remainingModifiers,
            Set<Holder<Attribute>> processedBaseAttributes,
            TooltipApplyResult result) {

         Map<Holder<Attribute>, Collection<AttributeModifier>> sortedRemaining = new TreeMap<>(Comparator.comparing(
              h -> BuiltInRegistries.ATTRIBUTE.getKey(h.value()),
              Comparator.nullsLast(Comparator.naturalOrder())
         ));
         for (Holder<Attribute> attr : remainingModifiers.keySet()) {
             if (!processedBaseAttributes.contains(attr)) {
                 List<AttributeModifier> mods = new ArrayList<>(remainingModifiers.get(attr));
                 mods.sort(ATTRIBUTE_MODIFIER_COMPARATOR);
                 sortedRemaining.put(attr, mods);
             }
         }

        for (Map.Entry<Holder<Attribute>, Collection<AttributeModifier>> entry : sortedRemaining.entrySet()) {
            Holder<Attribute> attr = entry.getKey();
            Collection<AttributeModifier> modifiers = entry.getValue();

            if (result.handledAttributes.contains(attr)) {
                  continue;
            }
            if (modifiers.isEmpty()) continue;

            handleNonBaseMerging(attr, modifiers, tooltip, result);
            result.handledAttributes.add(attr);
        }
    }


    private static void handleNonBaseMerging(
        Holder<Attribute> attr,
        Collection<AttributeModifier> modifiers,
        Consumer<Component> tooltip,
        TooltipApplyResult result) {

        Map<Operation, MergedModifierData> mergeData = new EnumMap<>(Operation.class);
        List<AttributeModifier> nonMergeable = new ArrayList<>();

        for (AttributeModifier modifier : modifiers) {
            if (modifier.amount() == 0) continue;

            boolean canMerge = modifier.operation() == Operation.ADD_VALUE ||
                               modifier.operation() == Operation.ADD_MULTIPLIED_BASE ||
                               modifier.operation() == Operation.ADD_MULTIPLIED_TOTAL;

            // Prevent merging base modifiers again if they somehow ended up here
             if (isBaseModifier(attr.value(), modifier)) {
                  canMerge = false;
             }

            if (canMerge) {
                 MergedModifierData data = mergeData.computeIfAbsent(modifier.operation(), op -> new MergedModifierData());
                 if (!data.children.isEmpty()) {
                     data.isMerged = true;
                     result.needsShiftPrompt = true;
                 }
                 data.sum += modifier.amount();
                 data.children.add(modifier);
            } else {
                 nonMergeable.add(modifier);
            }
        }

        for (Operation op : Operation.values()) {
            MergedModifierData data = mergeData.get(op);
            if (data == null || data.sum == 0) continue;

            AttributeModifier fakeModifier = new AttributeModifier(FAKE_MERGED_ID, data.sum, op);
            MutableComponent modComponent = createModifierComponent(attr.value(), fakeModifier);

            if (data.isMerged) {
                // Use light blue for merged non-base attributes
                tooltip.accept(modComponent.withStyle(style -> style.withColor(MERGED_MODIFIER_COLOR)));

                if (isDetailedView()) {
                    data.children.sort(ATTRIBUTE_MODIFIER_COMPARATOR);
                    for (AttributeModifier mod : data.children) {
                        tooltip.accept(listHeader().append(createModifierComponent(attr.value(), mod)));
                    }
                }
            } else if (!data.children.isEmpty()) {
                tooltip.accept(createModifierComponent(attr.value(), data.children.get(0)));
            }
        }

        nonMergeable.sort(ATTRIBUTE_MODIFIER_COMPARATOR);
        for (AttributeModifier modifier : nonMergeable) {
            tooltip.accept(createModifierComponent(attr.value(), modifier));
        }
    }


    private static void appendBlockInteractionRangeTooltip(
            ItemStack stack,
            Consumer<Component> tooltip,
            @Nullable Player player,
            EquipmentSlotGroup slot,
            Multimap<Holder<Attribute>, AttributeModifier> itemModifiers,
            TooltipApplyResult result) {

        if (!(player instanceof LocalPlayer localPlayer)) return;
        Holder<Attribute> blockRangeAttributeHolder = Attributes.BLOCK_INTERACTION_RANGE;

        if (result.handledAttributes.contains(blockRangeAttributeHolder)) {
            return;
        }

        AttributeInstance blockRangeInstance = localPlayer.getAttribute(blockRangeAttributeHolder);
        if (blockRangeInstance == null) {
            LOGGER.warn("Player {} missing attribute instance for {}", localPlayer.getName().getString(), BuiltInRegistries.ATTRIBUTE.getKey(blockRangeAttributeHolder.value()));
            return;
        }

        boolean shouldShowRange = false;

        if (FabricLoader.getInstance().isModLoaded("bettercombat")) {
            boolean itemHasAttackSpeed = itemModifiers.keySet().stream()
                    .anyMatch(attrHolder -> attrHolder.value() == Attributes.ATTACK_SPEED.value());
            WeaponAttributes weaponAttributes = WeaponRegistry.getAttributes(stack);
            if (itemHasAttackSpeed && weaponAttributes == null) {
                shouldShowRange = true;
            }
        } else {
            if (stack.getItem() instanceof DiggerItem) {
                shouldShowRange = true;
            }
        }

        if (!shouldShowRange) return;

        // --- Calculate Final Range & Collect Modifiers ---
        double playerBaseValue = blockRangeInstance.getBaseValue();
        List<AttributeModifier> relevantModifiers = new ArrayList<>();
        Collection<AttributeModifier> allAppliedModifiers = blockRangeInstance.getModifiers();

        // Recalculate the value considering all applied modifiers
        double finalCalculatedValue = playerBaseValue;

        List<AttributeModifier> addValueMods = new ArrayList<>();
        for (AttributeModifier mod : allAppliedModifiers) {
            if (mod.operation() == Operation.ADD_VALUE) {
                finalCalculatedValue += mod.amount();
                if (mod.amount() != 0) addValueMods.add(mod);
            }
        }

        List<AttributeModifier> multBaseMods = new ArrayList<>();
        for (AttributeModifier mod : allAppliedModifiers) {
            if (mod.operation() == Operation.ADD_MULTIPLIED_BASE) {
                finalCalculatedValue += playerBaseValue * mod.amount();
                 if (mod.amount() != 0) multBaseMods.add(mod);
            }
        }

        List<AttributeModifier> multTotalMods = new ArrayList<>();
        for (AttributeModifier mod : allAppliedModifiers) {
            if (mod.operation() == Operation.ADD_MULTIPLIED_TOTAL) {
                finalCalculatedValue *= (1.0 + mod.amount());
                 if (mod.amount() != 0) multTotalMods.add(mod);
            }
        }

        relevantModifiers.addAll(addValueMods);
        relevantModifiers.addAll(multBaseMods);
        relevantModifiers.addAll(multTotalMods);

        boolean isModified = Math.abs(finalCalculatedValue - playerBaseValue) > 1e-4;
        result.needsShiftPrompt |= isModified;

        MutableComponent mainLine = createBaseComponent(blockRangeAttributeHolder.value(), finalCalculatedValue, playerBaseValue, isModified);
         ChatFormatting color = isModified ? null : BASE_COLOR;
         Integer intColor = isModified ? MERGE_BASE_MODIFIER_COLOR : null;
         tooltip.accept(Component.literal(" ").append(mainLine.withStyle(style -> {
              if (intColor != null) return style.withColor(intColor);
             return style.applyFormat(color);
         })));

        if (isDetailedView() && isModified) {
            MutableComponent baseLine = createBaseComponent(blockRangeAttributeHolder.value(), playerBaseValue, playerBaseValue, false);
            tooltip.accept(listHeader().append(baseLine.withStyle(BASE_COLOR)));

            for (AttributeModifier mod : relevantModifiers) {
                 tooltip.accept(listHeader().append(createModifierComponent(blockRangeAttributeHolder.value(), mod)));
            }
        }

        result.handledAttributes.add(blockRangeAttributeHolder);
    }


    private static MutableComponent createBaseComponent(Attribute attribute, double value, double entityBase, boolean merged) {
        return Component.translatable("attribute.modifier.equals.0",
                FORMAT.format(value),
                Component.translatable(attribute.getDescriptionId()));
    }


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

        // Merged Non-base modifiers always light blue
        if (!isBaseAttribute(attribute) && modifier.id().equals(FAKE_MERGED_ID)) {
            return component.withStyle(style -> style.withColor(MERGED_MODIFIER_COLOR));
        }

        // Determine color based on override map, attribute sentiment, or default logic
        ChatFormatting color = ChatFormatting.WHITE; // Default fallback
        ResourceLocation attrId = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
        boolean handledByRule = false;

        // 1. Check override map first
        if (attrId != null) {
            AttributeColorRule rule = ATTRIBUTE_COLOR_RULES.get(attrId);
            if (rule != null) {
                handledByRule = true;
                switch (rule.logic()) {
                    case FIXED:
                        color = rule.fixedColor() != null ? rule.fixedColor() : ChatFormatting.GRAY;
                        break;
                    case INVERTED:
                        color = attribute.getStyle(!isPositive);
                        break;
                    case DEFAULT:
                    default:
                         handledByRule = false;
                         break;
                }
            }
        }

        // 2. If not handled by a specific rule (or rule was DEFAULT), use the attribute's own style method
        if (!handledByRule) {
            color = attribute.getStyle(isPositive);
        }

        return component.withStyle(color);
    }


    private static String formatValue(Attribute attribute, double value, Operation operation) {
        double absValue = Math.abs(value);

        if (operation == Operation.ADD_VALUE) {
            // Special formatting for knockback resistance (display as percentage)
            if (attribute == Attributes.KNOCKBACK_RESISTANCE.value()) {
                return FORMAT.format(absValue * 100) + "%" ;
                
            } else {
                return FORMAT.format(absValue);
            }
        } else {
            return FORMAT.format(absValue * 100);
        }
    }


    private static boolean isBaseAttribute(Attribute attribute) {
        ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
        return id != null && BASE_ATTRIBUTE_IDS.contains(id);
    }

    private static boolean isBaseModifier(Attribute attribute, AttributeModifier modifier) {
        ResourceLocation baseId = getBaseModifierId(attribute);
        return modifier.id().equals(baseId);
    }


    @Nullable
    private static ResourceLocation getBaseModifierId(Attribute attribute) {
        ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
        return id != null ? BASE_MODIFIER_IDS.get(id) : null;
    }


    static MutableComponent listHeader() {
        return Component.literal(" \u2507 ").withStyle(ChatFormatting.GRAY);
    }


    private static List<AttributeSection> findAttributeSections(List<Component> tooltip) {
        List<AttributeSection> result = new ArrayList<>();
        for (int i = 0; i < tooltip.size(); i++) {
            Component line = tooltip.get(i);
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


    public static EquipmentSlotGroup getSlotFromText(Component text) {
        String content = text.getString();
        return KEY_SLOT_MAP.get(content);
    }


    private static int countAttributeLines(List<Component> tooltip, int startIndex) {
        int count = 0;
        for (int i = startIndex; i < tooltip.size(); i++) {
            Component lineComp = tooltip.get(i);
            String line = lineComp.getString();

            // Stop if we hit an empty line or another slot header
            if (line.isEmpty() || getSlotFromText(lineComp) != null) {
                break;
            }

            // Basic check if the line looks like a modifier (starts with space, +, -, digit, or parens)
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


    public static class BaseModifier {
        final AttributeModifier base;
        final List<AttributeModifier> children;

        BaseModifier(AttributeModifier base, List<AttributeModifier> children) {
            this.base = base;
            this.children = children;
        }
    }


    private static class MergedModifierData {
        double sum = 0;
        boolean isMerged = false;
        List<AttributeModifier> children = new ArrayList<>();
    }


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


    public record ProcessingResult(boolean modified, @Nullable Component finalHeader, boolean needsShiftPrompt) {
        static final ProcessingResult NO_CHANGE = new ProcessingResult(false, null, false);
    }

}