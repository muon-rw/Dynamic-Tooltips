package dev.muon.dynamictooltips.handlers;

import dev.muon.dynamictooltips.Keybindings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.EquipmentSlotGroup;

import java.util.List;

public class TooltipPromptHandler {

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

    /**
     * Find where to splice the expand prompt so it sits directly below the enchantment section.
     * Vanilla emits <code>EMPTY, slotHeader, modifiers...</code> to start the attribute block, so
     * we target that preceding empty line — inserting there gives us
     * <code>...enchants, PROMPT, EMPTY, slotHeader, modifiers...</code>, which renders as the
     * prompt hugging the enchants with the usual gap still separating it from attributes.
     *
     * @return the insertion index, or -1 if no attribute section exists (caller should append).
     */
    public static int findEnchantSectionEnd(List<Component> tooltip) {
        for (int i = 0; i < tooltip.size(); i++) {
            EquipmentSlotGroup slot = AttributeTooltipHandler.getSlotFromText(tooltip.get(i));
            if (slot != null) {
                if (i > 0 && tooltip.get(i - 1).getString().isEmpty()) {
                    return i - 1;
                }
                return i;
            }
        }
        return -1;
    }
}
