package com.valor.weapons.util;

import com.valor.weapons.ValorWeapons;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** Damage of Sun's Bow projectiles: 100% of minecraft:generic.attack_damage. */
public final class SunBowDamage {
    /** Projectile damage = this fraction of the attack damage stat. */
    public static final double SCALE = 1.0D;

    /** Damage type of the Infinite Arrow burst hits: valor_weapons:sun_arrow (data/valor_weapons/damage_type/sun_arrow.json). */
    public static final ResourceKey<DamageType> SUN_ARROW_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(ValorWeapons.MODID, "sun_arrow"));

    private SunBowDamage() {}

    public static float projectileDamage(LivingEntity attacker) {
        return (float) (attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) * SCALE);
    }

    /** A valor_weapons:sun_arrow damage source caused (and dealt directly) by the attacker. */
    public static DamageSource sunArrowSource(Level level, LivingEntity attacker) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SUN_ARROW_TYPE),
                attacker, attacker);
    }
}
