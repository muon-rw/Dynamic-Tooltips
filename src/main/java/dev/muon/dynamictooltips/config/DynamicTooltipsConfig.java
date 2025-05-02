package dev.muon.dynamictooltips.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.regex.Pattern;

// Config definition using FCAP
public class DynamicTooltipsConfig {

    // Client Config (for client-side/rendering settings)
    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    // Hex color validation pattern (#RRGGBB)
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#([a-fA-F0-9]{6})$");

    static {
        // Client Builder
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

            builder.pop();
        }

        private static boolean validateHexColor(Object obj) {
            if (!(obj instanceof String str)) return false;
            return HEX_COLOR_PATTERN.matcher(str).matches();
        }
    }
} 