package com.valor.weapons.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.valor.weapons.ValorWeapons;
import com.valor.weapons.combat.ThunderstepSkill;
import com.valor.weapons.item.ThunderLordFistItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Skill slot of Thunderstep, drawn bottom right while the fist is held.
 * Layers: frame (32x32, opaque dark centre) -> icon (16x16) -> cooldown sweep + seconds -> key and level text.
 * Frame follows the skill level: unupgraded (0), 1..5 upgraded, fully upgraded (max). Level 0 = locked, icon darkened.
 */
public final class SkillHud {
    /** Frame by skill level: 0 = unupgraded, 1..5 = "N_upgraded", 6 (max) = fully upgraded. */
    private static final ResourceLocation[] FRAMES = {
            tex("unupgraded_skill_frame"),
            tex("1_upgraded_skill_frame"),
            tex("2_upgraded_skill_frame"),
            tex("3_upgraded_skill_frame"),
            tex("4_upgraded_skill_frame"),
            tex("5_upgraded_skill_frame"),
            tex("fully_upgraded_skill_frame")
    };
    // Ready for ultimate skills later: unupgraded_ultimate_skill_frame, 1_upgraded_ultimate_skill_frame,
    // 2_upgraded_ultimate_skill_frame, fully_upgraded_ultimate_skill_frame
    private static final ResourceLocation ICON = tex("thunderstep_icon");

    private static final int FRAME_SIZE = 32;
    private static final int ICON_SIZE = 16;
    /** Distance from the right and bottom screen edges to the frame. Change these to move the HUD. */
    private static final int MARGIN_RIGHT = 48;
    private static final int MARGIN_BOTTOM = 36;

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, width, height) -> {
        Minecraft mc = gui.getMinecraft();
        if (mc.player == null || mc.options.hideGui
                || !(mc.player.getMainHandItem().getItem() instanceof ThunderLordFistItem)) {
            return;
        }
        render(mc, graphics, partialTick, width, height);
    };

    private SkillHud() {}

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(ValorWeapons.MODID, "textures/gui/" + name + ".png");
    }

    private static void render(Minecraft mc, GuiGraphics g, float partialTick, int width, int height) {
        Font font = mc.font;
        int x = width - MARGIN_RIGHT - FRAME_SIZE;
        int y = height - MARGIN_BOTTOM - FRAME_SIZE;
        int iconX = x + (FRAME_SIZE - ICON_SIZE) / 2;
        int iconY = y + (FRAME_SIZE - ICON_SIZE) / 2;

        RenderSystem.enableBlend();
        int level = Math.max(0, Math.min(ClientSkillData.level, ThunderstepSkill.MAX_LEVEL));
        g.blit(FRAMES[Math.min(level, FRAMES.length - 1)], x, y, 0, 0, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);
        g.blit(ICON, iconX, iconY, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        if (level < 1) {
            g.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0xA0000000); // not learned yet: darkened icon
        }

        // Cooldown: darkens the icon from the top, shrinking as the cooldown runs out.
        float fraction = ClientSkillData.remainingFraction(partialTick);
        if (fraction > 0.0F) {
            int covered = Math.round(ICON_SIZE * fraction);
            g.fill(iconX, iconY, iconX + ICON_SIZE, iconY + covered, 0xB0101010);
            float seconds = ClientSkillData.remainingTicks(partialTick) / 20.0F;
            String text = seconds >= 10.0F ? String.valueOf((int) Math.ceil(seconds)) : String.format("%.1f", seconds);
            g.drawCenteredString(font, text, x + FRAME_SIZE / 2, y + FRAME_SIZE / 2 - 4, 0xFFFFFF);
        }

        String label = "[" + ModKeys.THUNDERSTEP.getTranslatedKeyMessage().getString() + "]  Lv." + level;
        g.drawCenteredString(font, label, x + FRAME_SIZE / 2, y + FRAME_SIZE + 2, 0xE0E0E0);
    }
}
