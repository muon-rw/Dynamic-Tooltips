package dev.muon.dynamictooltips;

import com.mojang.blaze3d.platform.InputConstants;
import dev.muon.dynamictooltips.mixin.accessor.KeyMappingAccessor;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class Keybindings {
    public static final String KEY_CATEGORY_DYNAMIC_TOOLTIPS = "key.category.dynamictooltips"; 
    public static final String KEY_SHOW_DETAILS = "key.dynamictooltips.show_details";

    public static KeyMapping SHOW_DETAILS_KEY;

    public static void register() {
        SHOW_DETAILS_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                KEY_SHOW_DETAILS,
                GLFW.GLFW_KEY_LEFT_SHIFT,
                KEY_CATEGORY_DYNAMIC_TOOLTIPS 
        ));
    }

    public static boolean isDetailedView() {
        KeyMapping mapping = Keybindings.SHOW_DETAILS_KEY;
        InputConstants.Key boundKey = ((KeyMappingAccessor) (Object) mapping).dynamicTooltips$getKey();

        if (boundKey == null || boundKey.equals(InputConstants.UNKNOWN)) {
            return false;
        }

        long windowHandle = Minecraft.getInstance().getWindow().getWindow();

        if (boundKey.getType() == InputConstants.Type.KEYSYM || boundKey.getType() == InputConstants.Type.SCANCODE) {
            return InputConstants.isKeyDown(windowHandle, boundKey.getValue());
        }
        if (boundKey.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(windowHandle, boundKey.getValue()) == GLFW.GLFW_PRESS;
        }
        return false;
    }
} 