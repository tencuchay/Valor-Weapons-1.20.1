package com.valor.weapons.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.valor.weapons.ValorWeapons;
import com.valor.weapons.combat.SunBowCombat;
import com.valor.weapons.item.SunBowItem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Infinite Arrow stacks of Sun's Bow, shown above the middle of the hotbar while the bow is held.
 * textures/gui/infinite_arrow_stack_N.png (32x16) is drawn for N stacks (1..4); no stacks = nothing.
 */
public final class ArrowStackHud {
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[SunBowCombat.STACKS_NEEDED];

    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = new ResourceLocation(ValorWeapons.MODID, "textures/gui/infinite_arrow_stack_" + (i + 1) + ".png");
        }
    }

    private static final int TEXTURE_WIDTH = 32;
    private static final int TEXTURE_HEIGHT = 16;
    /** Drawn size relative to the texture. */
    private static final float SCALE = 1.5F;
    /** Distance from the bottom of the screen to the bottom of the icon. Raise it to move the icon up. */
    private static final int BOTTOM_OFFSET = 32;

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, width, height) -> {
        Minecraft mc = gui.getMinecraft();
        int stacks = ClientSunBowData.stacks;
        if (mc.player == null || mc.options.hideGui || stacks < 1
                || !(mc.player.getMainHandItem().getItem() instanceof SunBowItem)) {
            return;
        }
        ResourceLocation texture = TEXTURES[Math.min(stacks, TEXTURES.length) - 1];
        int drawWidth = Math.round(TEXTURE_WIDTH * SCALE);
        int drawHeight = Math.round(TEXTURE_HEIGHT * SCALE);
        int x = (width - drawWidth) / 2;
        int y = height - BOTTOM_OFFSET - drawHeight;

        RenderSystem.enableBlend();
        graphics.blit(texture, x, y, drawWidth, drawHeight, 0.0F, 0.0F, TEXTURE_WIDTH, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    };

    private ArrowStackHud() {}
}
