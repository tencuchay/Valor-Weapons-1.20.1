package com.valor.weapons.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class ModKeys {
    public static final String CATEGORY = "key.categories.valor_weapons";

    /** Cast Thunderstep. Rebind in Options > Controls. */
    public static final KeyMapping THUNDERSTEP = new KeyMapping("key.valor_weapons.thunderstep",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    /** Spend 10 experience levels to upgrade Thunderstep. */
    public static final KeyMapping THUNDERSTEP_UPGRADE = new KeyMapping("key.valor_weapons.thunderstep_upgrade",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    private ModKeys() {}
}
