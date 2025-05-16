package dev.muon.dynamictooltips;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public class TooltipPromptHandler {

    public static boolean promptAddedThisTick = false;

    public static Component getExpandPrompt() {
        MutableComponent keyName = Keybindings.SHOW_DETAILS_KEY.getTranslatedKeyMessage().copy();
        Style keyStyle = Style.EMPTY.withColor(ChatFormatting.DARK_GRAY).withItalic(false);
        keyName.withStyle(keyStyle);

        return Component.empty()
                .append(Component.literal("[").withStyle(keyStyle))
                .append(keyName)
                .append(Component.literal("]").withStyle(keyStyle))
                .append(Component.translatable("tooltip.dynamictooltips.expand_text_part")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY).withItalic(true)));
    }
} 