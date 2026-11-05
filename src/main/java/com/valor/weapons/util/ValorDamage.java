package com.valor.weapons.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.ForgeRegistries;

public final class ValorDamage {
    /** Magic attack attribute, registered by the valor_items mod. */
    public static final ResourceLocation MAGIC_DAMAGE_ID = new ResourceLocation("valor_items", "magic_damage");

    /** Thunder Lord's Fist scaling: 100% attack damage + 25% magic damage. Change here (tooltip follows). */
    public static final double PHYSICAL_SCALE = 1.0D;
    public static final double MAGIC_SCALE = 0.25D;

    /** Lightning skill: each strike = 150% attack damage + 50% magic damage, heals 10% of the player's magic damage. */
    public static final double SKILL_PHYSICAL_SCALE = 1.5D;
    public static final double SKILL_MAGIC_SCALE = 0.5D;
    public static final double SKILL_HEAL_FRACTION = 0.10D;

    private ValorDamage() {}

    /** Current value of valor_items:magic_damage, or 0 if that attribute is missing (mod not installed / entity lacks it). */
    public static double getMagicDamage(LivingEntity entity) {
        if (!ForgeRegistries.ATTRIBUTES.containsKey(MAGIC_DAMAGE_ID)) {
            return 0.0D;
        }
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(MAGIC_DAMAGE_ID);
        if (attribute == null) {
            return 0.0D;
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? 0.0D : instance.getValue();
    }

    /** minecraft:generic.attack_damage * 100% + valor_items:magic_damage * 25% */
    public static float thunderFistDamage(LivingEntity attacker) {
        double physical = attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double magic = getMagicDamage(attacker);
        return (float) (physical * PHYSICAL_SCALE + magic * MAGIC_SCALE);
    }

    /** Damage of one lightning strike of the skill. */
    public static float skillDamage(LivingEntity attacker) {
        double physical = attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        return (float) (physical * SKILL_PHYSICAL_SCALE + getMagicDamage(attacker) * SKILL_MAGIC_SCALE);
    }

    /** Health restored to the caster per strike that hits something. */
    public static float skillHeal(LivingEntity attacker) {
        return (float) (getMagicDamage(attacker) * SKILL_HEAL_FRACTION);
    }

    public static String percent(double scale) {
        return Math.round(scale * 100.0D) + "%";
    }
}
