package com.valor.weapons.client;

import com.valor.weapons.combat.ThunderstepSkill;
import com.valor.weapons.network.ModNetwork;
import com.valor.weapons.network.SkillActionMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Thunderstep upgrade screen. Opened with the upgrade key while holding the fist.
 * Layout: icon + name + level + level bar / stats / special effect / next level / cost + Upgrade button.
 * The level shown always comes from ClientSkillData, so it updates by itself when the server confirms an upgrade.
 */
public class SkillUpgradeScreen extends Screen {
    private static final int PANEL_WIDTH = 250;
    private static final int PAD = 14;
    private static final int LINE = 11;

    // Colours (ARGB)
    private static final int PANEL_BORDER = 0xFF6B3FD4;
    private static final int PANEL_FILL = 0xFF1B1030;
    private static final int SEPARATOR = 0xFF3B3566;
    private static final int BAR_FILLED = 0xFFF5B83D;
    private static final int BAR_EMPTY = 0xFF3B3566;
    private static final int TITLE = 0xFF55FF;
    private static final int GRAY = 0xAAAAAA;
    private static final int GOLD = 0xFFAA00;
    private static final int GREEN = 0x55FF55;
    private static final int RED = 0xFF5555;
    private static final int CYAN = 0x55FFFF;
    private static final int TEXT = 0xE6E6E6;

    private static final int BUTTON_WIDTH = 110;
    private static final int BUTTON_HEIGHT = 20;

    private UpgradeButton upgradeButton;
    /** Button position relative to the panel's top left, set by paint(). */
    private int buttonRelY;

    public SkillUpgradeScreen() {
        super(Component.translatable("skill.valor_weapons.thunderstep.name"));
    }

    @Override
    protected void init() {
        this.upgradeButton = this.addRenderableWidget(new UpgradeButton(
                Component.translatable("screen.valor_weapons.thunderstep.upgrade"),
                button -> ModNetwork.CHANNEL.sendToServer(new SkillActionMessage(SkillActionMessage.UPGRADE))));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ModKeys.THUNDERSTEP_UPGRADE.matches(keyCode, scanCode)) {
            this.onClose(); // pressing the key again closes the screen
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static int level() {
        return Mth.clamp(ClientSkillData.level, 1, ThunderstepSkill.MAX_LEVEL);
    }

    private boolean canAfford() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null
                && (mc.player.getAbilities().instabuild || mc.player.experienceLevel >= ThunderstepSkill.UPGRADE_COST_LEVELS);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int height = this.paint(g, 0, 0, false); // measure first
        int x0 = (this.width - PANEL_WIDTH) / 2;
        int y0 = Math.max(4, (this.height - height) / 2);
        g.fill(x0 - 2, y0 - 2, x0 + PANEL_WIDTH + 2, y0 + height + 2, PANEL_BORDER);
        g.fill(x0, y0, x0 + PANEL_WIDTH, y0 + height, PANEL_FILL);
        this.paint(g, x0, y0, true);

        boolean maxed = level() >= ThunderstepSkill.MAX_LEVEL;
        this.upgradeButton.visible = !maxed;
        this.upgradeButton.active = !maxed && this.canAfford();
        this.upgradeButton.setPosition(x0 + (PANEL_WIDTH - BUTTON_WIDTH) / 2, y0 + this.buttonRelY);

        super.render(g, mouseX, mouseY, partialTick); // widgets on top of the panel
    }

    /**
     * Lays the panel content out from (x0, y0). With draw == false nothing is drawn: it only measures.
     *
     * @return panel height
     */
    private int paint(GuiGraphics g, int x0, int y0, boolean draw) {
        int level = level();
        int max = ThunderstepSkill.MAX_LEVEL;
        int x = x0 + PAD;
        int innerWidth = PANEL_WIDTH - 2 * PAD;
        int y = y0 + PAD;

        // ---- header: frame + icon, name, slot, level
        if (draw) {
            g.blit(SkillHud.frameTexture(level), x, y, 40, 40, 0, 0, 32, 32, 32, 32);
            g.blit(SkillHud.iconTexture(), x + 10, y + 10, 20, 20, 0, 0, 16, 16, 16, 16);
        }
        int tx = x + 48;
        text(g, draw, Component.translatable("skill.valor_weapons.thunderstep.name"), tx, y + 3, TITLE);
        text(g, draw, Component.translatable("screen.valor_weapons.thunderstep.slot"), tx, y + 15, GRAY);
        text(g, draw, Component.translatable("screen.valor_weapons.thunderstep.level", level, max), tx, y + 27, GOLD);
        y += 48;

        // ---- level bar
        if (draw) {
            int segment = (innerWidth - (max - 1) * 3) / max;
            for (int i = 0; i < max; i++) {
                int sx = x + i * (segment + 3);
                g.fill(sx, y, sx + segment, y + 4, i < level ? BAR_FILLED : BAR_EMPTY);
            }
        }
        y += 12;
        separator(g, draw, x, y, innerWidth);
        y += 8;

        // ---- current stats
        text(g, draw, Component.translatable("screen.valor_weapons.thunderstep.damage",
                num(ThunderstepSkill.damageAtLevel(level)), percent(ThunderstepSkill.MAGIC_DAMAGE_SCALE)), x, y, GREEN);
        y += LINE;
        text(g, draw, Component.translatable("screen.valor_weapons.thunderstep.shield",
                num(ThunderstepSkill.shieldAtLevel(level)), percent(ThunderstepSkill.SHIELD_MAX_HEALTH_FRACTION),
                num(ThunderstepSkill.SHIELD_TICKS / 20.0D)), x, y, GREEN);
        y += LINE;
        text(g, draw, Component.translatable("screen.valor_weapons.thunderstep.cooldown",
                num(ThunderstepSkill.COOLDOWN_TICKS / 20.0D)), x, y, GREEN);
        y += LINE;
        text(g, draw, Component.translatable("screen.valor_weapons.thunderstep.distance",
                num(ThunderstepSkill.DASH_DISTANCE)), x, y, GREEN);
        y += LINE + 6;

        // ---- special effect (wrapped text)
        text(g, draw, Component.translatable("screen.valor_weapons.thunderstep.special_title"), x, y, CYAN);
        y += LINE;
        List<FormattedCharSequence> lines = this.font.split(
                Component.translatable("screen.valor_weapons.thunderstep.special_text"), innerWidth);
        for (FormattedCharSequence line : lines) {
            if (draw) {
                g.drawString(this.font, line, x, y, TEXT, true);
            }
            y += 10;
        }
        y += 6;

        // ---- next level
        if (level < max) {
            text(g, draw, Component.translatable("screen.valor_weapons.thunderstep.next"), x, y, GREEN);
            y += LINE;
            text(g, draw, Component.translatable("screen.valor_weapons.thunderstep.next_damage",
                    num(ThunderstepSkill.damageAtLevel(level)), num(ThunderstepSkill.damageAtLevel(level + 1))), x + 8, y, GREEN);
            y += LINE;
            text(g, draw, Component.translatable("screen.valor_weapons.thunderstep.next_shield",
                    num(ThunderstepSkill.shieldAtLevel(level)), num(ThunderstepSkill.shieldAtLevel(level + 1))), x + 8, y, GREEN);
            y += LINE;
        } else {
            text(g, draw, Component.translatable("screen.valor_weapons.thunderstep.maxed"), x, y, GOLD);
            y += LINE;
        }
        y += 6;
        separator(g, draw, x, y, innerWidth);
        y += 9;

        // ---- cost + button
        if (level < max) {
            Component cost = Component.translatable("screen.valor_weapons.thunderstep.cost", ThunderstepSkill.UPGRADE_COST_LEVELS);
            if (draw) {
                g.drawCenteredString(this.font, cost, x0 + PANEL_WIDTH / 2, y, this.canAfford() ? GREEN : RED);
            }
            y += 15;
            this.buttonRelY = y - y0;
            y += BUTTON_HEIGHT + 4;
        }
        return y + PAD - y0;
    }

    private void text(GuiGraphics g, boolean draw, Component text, int x, int y, int color) {
        if (draw) {
            g.drawString(this.font, text, x, y, color, true);
        }
    }

    private static void separator(GuiGraphics g, boolean draw, int x, int y, int width) {
        if (draw) {
            g.fill(x, y, x + width, y + 1, SEPARATOR);
        }
    }

    /** 2.5 -> "2.5", 3.0 -> "3" */
    private static String num(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static String percent(double scale) {
        return Math.round(scale * 100.0D) + "%";
    }

    /** Gold button like the reference screenshot. */
    private static final class UpgradeButton extends Button {
        UpgradeButton(Component label, OnPress onPress) {
            super(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, label, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int fill = !this.active ? 0xFF6B6350 : this.isHoveredOrFocused() ? 0xFFF5D28C : 0xFFE9BE6A;
            g.fill(this.getX() - 1, this.getY() - 1, this.getX() + this.width + 1, this.getY() + this.height + 1, 0xFF000000);
            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, fill);
            g.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                    this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, this.active ? 0xFFFFFF : 0xA0A0A0);
        }
    }
}
