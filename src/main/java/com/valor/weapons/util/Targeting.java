package com.valor.weapons.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

public final class Targeting {
    private Targeting() {}

    /** Who the weapon's lightning may hurt. Edit here to change it. */
    public static boolean isValidTarget(@Nullable Entity owner, LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator() || !entity.isAttackable() || entity == owner) {
            return false;
        }
        if (entity instanceof ArmorStand) {
            return false;
        }
        if (owner instanceof Player attacker && entity instanceof Player other && !attacker.canHarmPlayer(other)) {
            return false;
        }
        return !(owner != null && entity instanceof OwnableEntity pet && owner.getUUID().equals(pet.getOwnerUUID()));
    }
}
