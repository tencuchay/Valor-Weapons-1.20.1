package com.valor.weapons.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Damage of Sun's Bow projectiles: 100% of minecraft:generic.attack_damage. */
public final class SunBowDamage {
    /** Projectile damage = this fraction of the attack damage stat. */
    public static final double SCALE = 1.0D;

    private SunBowDamage() {}

    public static float projectileDamage(LivingEntity attacker) {
        return (float) (attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) * SCALE);
    }
}
