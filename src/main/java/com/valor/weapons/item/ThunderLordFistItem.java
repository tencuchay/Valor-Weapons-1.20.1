package com.valor.weapons.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.valor.weapons.combat.FistCombat;
import com.valor.weapons.util.ValorDamage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Thunder Lord's Fist (valor_weapons:thunder_lord_fist)
 *
 * - Damage = 100% minecraft:generic.attack_damage + 25% valor_items:magic_damage (see ValorDamage), dealt as MAGIC damage.
 * - Melee: Better Combat (attack_range 2.5 in the weapon_attributes JSON) calls Player#attack for targets in reach,
 *   which lands in onLeftClickEntity below.
 * - Otherwise a swing fires one homing thunder bolt (see FistCombat).
 */
public class ThunderLordFistItem extends SwordItem {
    /**
     * Attack damage the weapon grants. Vanilla tooltips add the player's base 1.0 to the main-hand weapon modifier,
     * so 32 is shown as "33 Attack Damage".
     */
    private static final double WEAPON_ATTACK_DAMAGE = 32.0D;
    /** Magic damage (valor_items:magic_damage) the weapon grants while held in the main hand. */
    private static final double WEAPON_MAGIC_DAMAGE = 15.0D;
    /** Wanted total attack speed. */
    private static final double TOTAL_ATTACK_SPEED = 1.3D;
    /** The player's base attack speed is 4.0, so the modifier is (wanted - 4.0) = -2.7. */
    private static final double WEAPON_ATTACK_SPEED = TOTAL_ATTACK_SPEED - 4.0D;
    private static final UUID MAGIC_DAMAGE_MODIFIER_UUID = UUID.fromString("5c1f8f0e-3b7a-4d5e-9a64-2f7d8b1c0a15");

    public ThunderLordFistItem(Properties properties) {
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
        // valor_items:magic_damage, only if that mod is present.
        if (ForgeRegistries.ATTRIBUTES.containsKey(ValorDamage.MAGIC_DAMAGE_ID)) {
            Attribute magic = ForgeRegistries.ATTRIBUTES.getValue(ValorDamage.MAGIC_DAMAGE_ID);
            if (magic != null) {
                builder.put(magic,
                        new AttributeModifier(MAGIC_DAMAGE_MODIFIER_UUID, "Weapon modifier", WEAPON_MAGIC_DAMAGE, AttributeModifier.Operation.ADDITION));
            }
        }
        return builder.build();
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (!(entity instanceof LivingEntity target)) {
            return false; // item frames, boats, ... -> vanilla handling
        }

        if (player.level().isClientSide) {
            // Server does the real work; keep the client cooldown bar in sync and skip vanilla's client-side attack.
            player.resetAttackStrengthTicker();
            return true;
        }

        // Same cooldown scaling vanilla applies in Player#attack.
        float strength = player.getAttackStrengthScale(0.5F);
        float cooldownScale = 0.2F + strength * strength * 0.8F;
        player.resetAttackStrengthTicker();

        // Counts this swing for the skill. On the 7th swing the lightning replaces the punch.
        if (player instanceof ServerPlayer serverPlayer && FistCombat.beginMelee(serverPlayer)) {
            return true;
        }

        float damage = ValorDamage.thunderFistDamage(player) * cooldownScale;
        if (damage > 0.0F) {
            DamageSource source = player.level().damageSources().indirectMagic(player, player);
            target.hurt(source, damage);

            if (player instanceof ServerPlayer serverPlayer) {
                com.valor.weapons.combat.ThunderstepSkill.addStack(serverPlayer);
            }

            if (player.level() instanceof ServerLevel level) {
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.4F, 1.6F);
            }
        }

        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true; // we handled the attack, cancel vanilla's
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.valor_weapons.thunder_lord_fist.scaling",
                ValorDamage.percent(ValorDamage.PHYSICAL_SCALE), ValorDamage.percent(ValorDamage.MAGIC_SCALE))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.valor_weapons.thunder_lord_fist.melee").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.valor_weapons.thunder_lord_fist.ranged").withStyle(ChatFormatting.GRAY));
    }
}
