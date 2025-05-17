package dev.muon.dynamictooltips.handlers;

import dev.muon.dynamictooltips.EnchantmentContext;
import dev.muon.dynamictooltips.Keybindings;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import dev.muon.dynamictooltips.config.DynamicTooltipsConfig;

public class EnchantmentTooltipHandler {

    private static final String[] KEY_TYPES = {"desc", "description", "info"};
    private static EnchantmentTooltipHandler instance;

    public static EnchantmentTooltipHandler getInstance() {
        if (instance == null) {
            instance = new EnchantmentTooltipHandler();
        }
        return instance;
    }

    public EnchantmentTooltipHandler() {
    }

    public void setupContext(ItemStack stack) {
        if (shouldDisplayDescription(stack)) {
            if (stack.getEnchantments() instanceof EnchantmentContext provider) {
                provider.dynamictooltips$setStack(stack);
            }
            if (stack.get(DataComponents.STORED_ENCHANTMENTS) instanceof EnchantmentContext provider) {
                provider.dynamictooltips$setStack(stack);
            }
        }
    }

    public void revertContext(ItemStack stack) {
        if (stack.getEnchantments() instanceof EnchantmentContext provider) {
            provider.dynamictooltips$setStack(ItemStack.EMPTY);
        }
        if (stack.get(DataComponents.STORED_ENCHANTMENTS) instanceof EnchantmentContext provider) {
            provider.dynamictooltips$setStack(ItemStack.EMPTY);
        }
    }

    public static boolean hasEnchantments(ItemStack stack) {
        return !stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty() ||
               !stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }

    public static boolean itemHasExpandableEnchantments(ItemStack stack) {
        return DynamicTooltipsConfig.CLIENT.collapseEnchantmentTooltipsOnGear.get() &&
               hasEnchantments(stack) &&
               !(stack.getItem() instanceof EnchantedBookItem);
    }

    public boolean shouldDisplayDescription(ItemStack stack) {
        if (!hasEnchantments(stack)) {
            return false;
        }
        if (stack.getItem() instanceof EnchantedBookItem) {
            return true;
        }
        return !DynamicTooltipsConfig.CLIENT.collapseEnchantmentTooltipsOnGear.get() || Keybindings.isDetailedView();
    }

    public void insertDescriptions(Holder<Enchantment> enchantment, int level, Consumer<Component> lines) {
        final Component description = getDescription(enchantment, enchantment.unwrapKey().orElseThrow().location(), level);
        if (description != null) {
            String hexColor = DynamicTooltipsConfig.CLIENT.enchantmentDescriptionColor.get();
            int color = Integer.parseInt(hexColor.substring(1), 16);
            Style descriptionStyle = Style.EMPTY
                    .withColor(color)
                    .withItalic(true);

            MutableComponent styledDescription = description.copy().withStyle(descriptionStyle);

            lines.accept(Component.literal(" ").append(styledDescription));
        }
    }

    @Nullable
    private Component getDescription(Holder<Enchantment> enchantment, ResourceLocation id, int level) {
        Component description = findTranslation("enchantment." + id.getNamespace() + "." + id.getPath() + ".", level);

        if (description == null && enchantment.value().description().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatable) {
           description = findTranslation(translatable.getKey() + ".", level);
        }
        return description;
    }

    @Nullable
    private Component findTranslation(String baseKey, int level) {
        for (String keyType : KEY_TYPES) {
            // Check for base key (e.g., enchantment.minecraft.sharpness.desc)
            String key = baseKey + keyType;
            if (I18n.exists(key)) {
                return Component.translatable(key);
            }
            // Check for level-specific key (e.g., enchantment.minecraft.sharpness.desc.5)
            key = key + "." + level;
            if (I18n.exists(key)) {
                return Component.translatable(key);
            }
        }
        return null;
    }
} 