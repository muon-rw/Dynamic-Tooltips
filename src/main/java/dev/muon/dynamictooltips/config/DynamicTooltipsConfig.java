package dev.muon.dynamictooltips.config;

import com.google.common.collect.Lists;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

public class DynamicTooltipsConfig {

    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    // Hex color validation pattern (#RRGGBB)
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#([a-fA-F0-9]{6})$");

    public enum ColorLogic {
        INVERTED, FIXED
    }

    static {
        ModConfigSpec.Builder clientBuilder = new ModConfigSpec.Builder();
        CLIENT = new Client(clientBuilder);
        CLIENT_SPEC = clientBuilder.build();
    }


    public static class Client {
        public final ModConfigSpec.BooleanValue appendBlockInteractionRangeTooltip;
        public final ModConfigSpec.BooleanValue showUsabilityHint;
        public final ModConfigSpec.BooleanValue collapseEnchantmentTooltipsOnGear;
        public final ModConfigSpec.ConfigValue<String> enchantmentDescriptionColor;
        public final ModConfigSpec.ConfigValue<String> superLeveledEnchantmentColor;
        public final ModConfigSpec.BooleanValue colorEnchantmentNames;
        public final ModConfigSpec.ConfigValue<String> enchantmentNameColor;
        public final ModConfigSpec.ConfigValue<List<? extends String>> attributeColorOverrides;

        Client(ModConfigSpec.Builder builder) {
            builder.push("Client Settings");

            appendBlockInteractionRangeTooltip = builder
                .comment("Append Block Interaction Range attribute line to relevant tooltips (e.g., tools)")
                .define("appendBlockInteractionRangeTooltip", true);

            showUsabilityHint = builder
                .comment("Show the 'Hold [Shift] to expand...' hint when tooltips have hidden details (merged attributes or collapsed enchantments).")
                .define("showUsabilityHint", true);

            collapseEnchantmentTooltipsOnGear = builder
                .comment("Collapse enchantment descriptions on gear by default, requiring Shift to be held to view them. Enchanted Books always show descriptions.")
                .define("collapseEnchantmentTooltipsOnGear", true);

            builder.pop();
            builder.push("Color Settings");

            colorEnchantmentNames = builder
                .comment("Enable coloring for enchantment names in tooltips.")
                .define("colorEnchantmentNames", true);

            enchantmentNameColor = builder
                .comment("Hex color code (#RRGGBB) for regular enchantment names (if colorEnchantmentNames is true). Curses are always red.")
                .define("enchantmentNameColor", "#AAAAAA", Client::validateHexColor);

            enchantmentDescriptionColor = builder
                .comment("Hex color code (#RRGGBB) for enchantment description text.")
                .define("enchantmentDescriptionColor", "#808080", Client::validateHexColor);

            superLeveledEnchantmentColor = builder
                .comment("Hex color code (#RRGGBB) for enchantments above their max level (excluding curses) if colorEnchantmentNames is true.")
                .define("superLeveledEnchantmentColor", "#FF55FF", Client::validateHexColor);

            attributeColorOverrides = builder
                .comment(
                    "Define custom color rules for specific attributes in tooltips.",
                    "Format: \"attribute_id:LOGIC[:#HEXCOLOR]\"",
                    "  attribute_id: The ResourceLocation of the attribute (e.g., minecraft:generic.movement_speed).",
                    "  LOGIC: How to color the modifier value. Options: INVERTED, FIXED.",
                    "    INVERTED: Use the opposite of the attribute's default sentiment coloring (e.g., positive value = red).",
                    "    FIXED: Always use the specified hex color, regardless of value.",
                    "  :#HEXCOLOR: Required only if LOGIC is FIXED. The hex color code (e.g., #RRGGBB).",
                    "  (If no rule is specified for an attribute, vanilla default coloring is used)."
                )
                .defineListAllowEmpty("attributeColorOverrides",
                    Lists.newArrayList(
                        "additionalentityattributes:generic.hitbox_height:FIXED:#808080", // Gray
                        "additionalentityattributes:generic.hitbox_width:FIXED:#808080",  // Gray
                        "additionalentityattributes:generic.model_height:FIXED:#808080", // Gray
                        "additionalentityattributes:generic.model_width:FIXED:#808080",  // Gray
                        "additionalentityattributes:generic.height:FIXED:#808080",      // Gray
                        "additionalentityattributes:generic.width:FIXED:#808080",       // Gray
                        "additionalentityattributes:generic.model_scale:FIXED:#808080",  // Gray
                        "additionalentityattributes:generic.mob_detection_range:INVERTED",
                        "ranged_weapon:pull_time:INVERTED"
                    ),
                    Client::validateAttributeColorRule
                );

            builder.pop();
        }

        public record AttributeColorRule(ResourceLocation attributeId, ColorLogic logic, @Nullable ChatFormatting fixedColor, @Nullable String hexColor) {}

        private static boolean validateHexColor(Object obj) {
            if (!(obj instanceof String str)) return false;
            return HEX_COLOR_PATTERN.matcher(str).matches();
        }

        private static boolean validateAttributeColorRule(Object obj) {
            if (!(obj instanceof String rule)) return false;

            String[] parts = rule.split(":");
            // Expect format: namespace:path:LOGIC[:#HEXCOLOR]
            if (parts.length < 3 || parts.length > 4) return false;

            // Reconstruct potential ResourceLocation string
            String potentialId = parts[0] + ":" + parts[1];
            if (ResourceLocation.tryParse(potentialId) == null) {
                return false;
            }

            String logicStr = parts[2].toUpperCase();
            ColorLogic parsedLogic;
            try {
                if (logicStr.equals("DEFAULT")) return false;
                parsedLogic = ColorLogic.valueOf(logicStr);
            } catch (IllegalArgumentException e) {
                return false;
            }

            // Validate based on logic
            if (parsedLogic == ColorLogic.FIXED) {
                // FIXED requires 4 parts and a valid hex color in the last part
                return parts.length == 4 && validateHexColor(parts[3]);
            } else {
                // INVERTED requires exactly 3 parts
                return parts.length == 3;
            }
        }

        @Nullable
        public static AttributeColorRule parseRuleString(String rule) {
             String[] parts = rule.split(":");
             if (parts.length < 3 || parts.length > 4) return null;

             ResourceLocation attributeId = ResourceLocation.tryParse(parts[0] + ":" + parts[1]);
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
                 // We'll convert hex to ChatFormatting later in the handler
             } else {
                 if (parts.length != 3) return null;
             }
             return new AttributeColorRule(attributeId, parsedLogic, null, hexColor);
        }
    }
} 