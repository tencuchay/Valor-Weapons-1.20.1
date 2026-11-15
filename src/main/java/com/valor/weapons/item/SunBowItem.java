package com.valor.weapons.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.valor.weapons.Config;
import com.valor.weapons.combat.SunBowCombat;
import com.valor.weapons.util.SunBowDamage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Sun's Bow (valor_weapons:sun_bow)
 *
 * A melee-class weapon (Better Combat swings, no real melee damage) that attacks at range: every swing fires one
 * sun bolt along the look direction (see SunBowCombat / SunBoltProjectile).
 */
public class SunBowItem extends SwordItem {
    /**
     * Attack damage the weapon grants. Vanilla tooltips add the player's base 1.0 to the main-hand weapon modifier,
     * so 29 is shown as "30 Attack Damage".
     */
    private static final double WEAPON_ATTACK_DAMAGE = 29.0D;
    /** Wanted total attack speed. */
    private static final double TOTAL_ATTACK_SPEED = 1.5D;
    /** The player's base attack speed is 4.0, so the modifier is (wanted - 4.0) = -2.5. */
    private static final double WEAPON_ATTACK_SPEED = TOTAL_ATTACK_SPEED - 4.0D;

    public SunBowItem(Properties properties) {
        super(Tiers.NETHERITE, (int) (WEAPON_ATTACK_DAMAGE - Tiers.NETHERITE.getAttackDamageBonus()), (float) WEAPON_ATTACK_SPEED, properties);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND) {
            return super.getDefaultAttributeModifiers(slot);
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", WEAPON_ATTACK_DAMAGE, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED,
                new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", WEAPON_ATTACK_SPEED, AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    /**
     * Better Combat (attack_range 12) hits land here. The bow never deals melee damage: the hit only releases the
     * bolt (once per swing, even if several targets were swept).
     */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        if (player.level().isClientSide) {
            player.resetAttackStrengthTicker();
            return true;
        }
        player.resetAttackStrengthTicker();
        if (player instanceof ServerPlayer serverPlayer) {
            SunBowCombat.onSwingHit(serverPlayer);
        }
        return true; // vanilla damage cancelled
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.valor_weapons.sun_bow.scaling",
                percent(SunBowDamage.SCALE)).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.valor_weapons.sun_bow.ranged",
                Math.round(Config.SUN_BOW_RANGE.get())).withStyle(ChatFormatting.GRAY));
    }

    private static String percent(double scale) {
        return Math.round(scale * 100.0D) + "%";
    }
}
