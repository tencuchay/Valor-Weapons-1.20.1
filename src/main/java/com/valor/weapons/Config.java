package com.valor.weapons;

import net.minecraftforge.common.ForgeConfigSpec;

/** Balance knobs for Thunder Lord's Fist. File: config/valor_weapons-common.toml */
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.DoubleValue PROJECTILE_SPEED = BUILDER
            .comment("Projectile speed in blocks per tick")
            .defineInRange("thunderLordFist.projectileSpeed", 1.2D, 0.2D, 4.0D);

    public static final ForgeConfigSpec.DoubleValue PROJECTILE_RANGE = BUILDER
            .comment("Blocks the projectile flies (without a target) before it disappears")
            .defineInRange("thunderLordFist.projectileRange", 8.0D, 1.0D, 64.0D);

    public static final ForgeConfigSpec.DoubleValue HOMING_RADIUS = BUILDER
            .comment("When the projectile is near the end of its range it locks onto the nearest target within this many blocks of itself")
            .defineInRange("thunderLordFist.homingRadius", 4.0D, 0.5D, 32.0D);

    public static final ForgeConfigSpec.DoubleValue HOMING_START_FRACTION = BUILDER
            .comment("The projectile flies straight and only starts looking for a target after this fraction of its range",
                    "(0.75 = the last quarter of the flight). 1.0 = only when it reaches the very end.")
            .defineInRange("thunderLordFist.homingStartFraction", 0.75D, 0.0D, 1.0D);

    public static final ForgeConfigSpec.IntValue PROJECTILE_MAX_AGE = BUILDER
            .comment("Hard lifetime limit of a projectile in ticks (also limits how long it can chase a locked target). 20 ticks = 1 second")
            .defineInRange("thunderLordFist.projectileMaxAge", 60, 5, 400);

    public static final ForgeConfigSpec.DoubleValue SWING_DELAY_FRACTION = BUILDER
            .comment("How long after the swing starts the bolt is released, as a fraction of the attack cooldown.",
                    "Match it to Better Combat's wind-up (upswing) so the bolt leaves at the moment of the punch.",
                    "Raise it if the bolt appears before the fist lands, lower it if it appears after.")
            .defineInRange("thunderLordFist.swingDelayFraction", 0.25D, 0.0D, 1.0D);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
