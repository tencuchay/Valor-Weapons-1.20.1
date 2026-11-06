package com.valor.weapons.client;

/** Client-side copy of the Thunderstep state, for the HUD. Contains no Minecraft client classes. */
public final class ClientSkillData {
    /** Counts client ticks; the cooldown is measured against it. */
    public static long clientTicks = 0;

    public static int level = 1;
    public static int stacks = 0;
    public static boolean hasFreeCast = false;
    private static long cooldownEndTick = 0;
    private static int cooldownTotal = 1;

    private ClientSkillData() {}

    public static void update(int newLevel, int cooldownRemaining, int newCooldownTotal, int newStacks, boolean freeCast) {
        level = newLevel;
        stacks = newStacks;
        hasFreeCast = freeCast;
        cooldownTotal = Math.max(1, newCooldownTotal);
        cooldownEndTick = clientTicks + cooldownRemaining;
    }

    /** Remaining cooldown in ticks (fractional with partialTick), 0 when ready. */
    public static float remainingTicks(float partialTick) {
        return Math.max(0.0F, cooldownEndTick - (clientTicks + partialTick));
    }

    public static float remainingFraction(float partialTick) {
        return Math.min(1.0F, remainingTicks(partialTick) / cooldownTotal);
    }
}
