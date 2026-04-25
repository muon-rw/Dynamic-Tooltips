package dev.muon.dynamictooltips.config;

import dev.muon.dynamictooltips.DynamicTooltips;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedList;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Client-only FzzyConfig. Holds all Dynamic Tooltips display preferences.
 *
 * <p>File: <code>config/dynamictooltips/dynamictooltips-client.toml</code>
 */
public class DynamicTooltipsConfig extends Config {

    public static DynamicTooltipsConfig INSTANCE;

    // Hex color validation pattern (#RRGGBB)
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#([a-fA-F0-9]{6})$");

    public enum ColorLogic {
        INVERTED, FIXED
    }

    public DynamicTooltipsConfig() {
        super(Identifier.fromNamespaceAndPath(DynamicTooltips.MODID, "client"));
    }

    public static void register() {
        INSTANCE = ConfigApi.registerAndLoadConfig(
                (Supplier<DynamicTooltipsConfig>) DynamicTooltipsConfig::new,
                RegisterType.CLIENT);
    }

    @Override
    public void onUpdateClient() {
        super.onUpdateClient();
        dev.muon.dynamictooltips.handlers.AttributeTooltipHandler.invalidateCaches();
    }

    @Comment("Append Block Interaction Range attribute line to relevant tooltips (pickaxes, shovels, etc.)")
    public ValidatedBoolean appendBlockInteractionRangeTooltip = new ValidatedBoolean(true);

    @Comment("Items or tags that should display Block Interaction Range tooltips.\n" +
            "Format: \"namespace:path\" for item IDs, or \"#namespace:path\" for tags.\n" +
            "Default includes common mining tools (pickaxes, axes, shovels, hoes, shears).")
    public ValidatedList<String> blockInteractionRangeItemTags = ValidatedList.ofString(
            List.of("#minecraft:enchantable/mining"));

    @Comment("Append Entity Interaction Range attribute line to relevant tooltips (swords, etc.)")
    public ValidatedBoolean appendEntityInteractionRangeTooltip = new ValidatedBoolean(true);

    @Comment("Items or tags that should display Entity Interaction Range tooltips.\n" +
            "Format: \"namespace:path\" for item IDs, or \"#namespace:path\" for tags.\n" +
            "Default includes weapon-related enchantable tags.")
    public ValidatedList<String> entityInteractionRangeItemTags = ValidatedList.ofString(
            List.of(
                    "#minecraft:enchantable/weapon",
                    "#minecraft:enchantable/trident",
                    "#minecraft:enchantable/fire_aspect",
                    "#minecraft:enchantable/sharp_weapon"));

    @Comment("Show the 'Hold [Shift] to expand...' hint in tooltips that have merged attributes or collapsed enchantments.")
    public ValidatedBoolean showUsabilityHint = new ValidatedBoolean(false);

    @Comment("Collapse enchantment descriptions on gear, requiring Shift to be held to view them.\nEnchanted Books always show descriptions.")
    public ValidatedBoolean collapseEnchantmentTooltipsOnGear = new ValidatedBoolean(true);

    @Comment("Enable custom coloring of enchantments in tooltips.")
    public ValidatedBoolean colorEnchantmentNames = new ValidatedBoolean(true);

    @Comment("Hex color code (#RRGGBB) for regular enchantment names (if colorEnchantmentNames is true). Curses are always red.")
    public ValidatedString enchantmentNameColor = new ValidatedString("#AAAAAA");

    @Comment("Hex color code (#RRGGBB) for enchantment description text.")
    public ValidatedString enchantmentDescriptionColor = new ValidatedString("#808080");

    @Comment("Hex color code (#RRGGBB) for enchantments above their max level (excluding curses) if colorEnchantmentNames is true.")
    public ValidatedString superLeveledEnchantmentColor = new ValidatedString("#FF55FF");

    @Comment("Custom color rules for specific attributes in tooltips.\n" +
            "Format: \"attribute_id:LOGIC[:#HEXCOLOR]\"\n" +
            "  attribute_id: identifier (e.g. minecraft:generic.movement_speed)\n" +
            "  LOGIC: INVERTED (flip default sentiment coloring) or FIXED (always use #HEXCOLOR)\n" +
            "  :#HEXCOLOR: required for FIXED\n" +
            "Unspecified attributes use vanilla coloring.")
    public ValidatedList<String> attributeColorOverrides = ValidatedList.ofString(List.of(
            "additionalentityattributes:generic.hitbox_height:FIXED:#808080",
            "additionalentityattributes:generic.hitbox_width:FIXED:#808080",
            "additionalentityattributes:generic.model_height:FIXED:#808080",
            "additionalentityattributes:generic.model_width:FIXED:#808080",
            "additionalentityattributes:generic.height:FIXED:#808080",
            "additionalentityattributes:generic.width:FIXED:#808080",
            "additionalentityattributes:generic.model_scale:FIXED:#808080",
            "additionalentityattributes:generic.mob_detection_range:INVERTED",
            "ranged_weapon:pull_time:INVERTED"));

    @Comment("Attributes whose flat (ADD_VALUE) modifiers should be rendered as percentages.\n" +
            "Format: \"attribute_id[:scaleFactor]\" (default scale = 100)\n" +
            "  attribute_id: identifier (e.g. minecraft:knockback_resistance)\n" +
            "  scaleFactor: multiplier applied to the raw value before the % sign (e.g. 100 means 0.1 -> 10%).\n" +
            "Mods can also register percent attributes via DynamicTooltipsAPI; this list overrides any API entries on conflict.")
    public ValidatedList<String> percentAttributes = ValidatedList.ofString(List.of(
            "minecraft:knockback_resistance:100"));

    @Comment("Attributes whose modifiers should be merged with the item's base modifier (rendered green/gold).\n" +
            "Format: \"attribute_id\"\n" +
            "Pair each with an entry in baseModifierMappings to teach the handler which modifier ID is the base.\n" +
            "Mods can also register base attributes via DynamicTooltipsAPI; this list overrides any API entries on conflict.")
    public ValidatedList<String> baseAttributes = ValidatedList.ofString(List.of(
            "minecraft:attack_damage",
            "minecraft:attack_speed",
            "minecraft:entity_interaction_range",
            "ranged_weapon:damage",
            "ranged_weapon:pull_time"));

    @Comment("Mapping from attribute ID to the modifier ID that represents its base value (used for merge rendering).\n" +
            "Format: \"attribute_id=base_modifier_id\"\n" +
            "Mods can also register mappings via DynamicTooltipsAPI; this list overrides any API entries on conflict.")
    public ValidatedList<String> baseModifierMappings = ValidatedList.ofString(List.of(
            "minecraft:attack_damage=minecraft:base_attack_damage",
            "minecraft:attack_speed=minecraft:base_attack_speed",
            "ranged_weapon:damage=ranged_weapon:base_damage",
            "ranged_weapon:pull_time=ranged_weapon:base_pull_time"));

    // === Helpers preserved from the old FCAP-based config ===

    public record AttributeColorRule(Identifier attributeId, ColorLogic logic, @Nullable ChatFormatting fixedColor, @Nullable String hexColor) {}

    public static boolean validateHexColor(String str) {
        return str != null && HEX_COLOR_PATTERN.matcher(str).matches();
    }

    /**
     * Checks if an ItemStack matches an item/tag specification string.
     *
     * @param stack The ItemStack to check
     * @param entry Format: "namespace:path" for items, "#namespace:path" for tags
     * @return true if the stack matches the specification
     */
    public static boolean matchesItemOrTag(ItemStack stack, String entry) {
        if (entry.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(entry.substring(1));
            if (tagId != null) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
                return stack.is(tag);
            }
        } else {
            Identifier itemId = Identifier.tryParse(entry);
            if (itemId != null) {
                var holderOptional = BuiltInRegistries.ITEM.get(itemId);
                if (holderOptional.isPresent()) {
                    return stack.is(holderOptional.get());
                }
            }
        }
        return false;
    }

    @Nullable
    public static AttributeColorRule parseRuleString(String rule) {
        String[] parts = rule.split(":");
        if (parts.length < 3 || parts.length > 4) return null;

        Identifier attributeId = Identifier.tryParse(parts[0] + ":" + parts[1]);
        if (attributeId == null) return null;

        String logicStr = parts[2].toUpperCase();
        ColorLogic parsedLogic;
        try {
            if (logicStr.equals("DEFAULT")) return null;
            parsedLogic = ColorLogic.valueOf(logicStr);
        } catch (IllegalArgumentException e) {
            return null;
        }

        String hexColor = null;
        if (parsedLogic == ColorLogic.FIXED) {
            if (parts.length != 4) return null;
            hexColor = parts[3];
            if (!validateHexColor(hexColor)) return null;
        } else {
            if (parts.length != 3) return null;
        }
        return new AttributeColorRule(attributeId, parsedLogic, null, hexColor);
    }

    public record PercentEntry(Identifier attributeId, double scaleFactor) {}

    public record BaseModifierEntry(Identifier attributeId, Identifier baseModifierId) {}

    public static final double DEFAULT_PERCENT_SCALE = 100.0;

    @Nullable
    public static PercentEntry parsePercentEntry(String entry) {
        String[] parts = entry.split(":");
        if (parts.length < 2 || parts.length > 3) return null;

        Identifier attributeId = Identifier.tryParse(parts[0] + ":" + parts[1]);
        if (attributeId == null) return null;

        double scale = DEFAULT_PERCENT_SCALE;
        if (parts.length == 3) {
            try {
                scale = Double.parseDouble(parts[2]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return new PercentEntry(attributeId, scale);
    }

    @Nullable
    public static Identifier parseBaseAttributeEntry(String entry) {
        return Identifier.tryParse(entry);
    }

    @Nullable
    public static BaseModifierEntry parseBaseModifierEntry(String entry) {
        String[] sides = entry.split("=", 2);
        if (sides.length != 2) return null;
        Identifier attr = Identifier.tryParse(sides[0]);
        Identifier base = Identifier.tryParse(sides[1]);
        if (attr == null || base == null) return null;
        return new BaseModifierEntry(attr, base);
    }
}
