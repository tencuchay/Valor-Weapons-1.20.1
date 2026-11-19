package com.valor.weapons.client;

import com.valor.weapons.ValorWeapons;
import com.valor.weapons.item.SunBowItem;
import com.valor.weapons.item.ThunderLordFistItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Sun's Bow and Thunder Lord's Fist have no sweep: the sweep sound (entity.player.attack.sweep) and the sweep particle
 * (minecraft:sweep_attack, see ClientEvents) are dropped when they appear next to a player holding one of them.
 * This works whatever makes them (vanilla, Better Combat, another mod), because it is only done on the client.
 * The side effect: a sweep of another player's sword standing within RADIUS blocks of such a player is dropped too.
 */
@Mod.EventBusSubscriber(modid = ValorWeapons.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SweepSuppression {
    private static final ResourceLocation SWEEP_SOUND = new ResourceLocation("minecraft", "entity.player.attack.sweep");
    private static final double RADIUS = 4.0D;

    private SweepSuppression() {}

    /** True when a player holding Sun's Bow or Thunder Lord's Fist is within RADIUS blocks of the point. */
    public static boolean nearWeaponHolder(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        for (Player player : mc.level.players()) {
            Item item = player.getMainHandItem().getItem();
            if ((item instanceof SunBowItem || item instanceof ThunderLordFistItem)
                    && player.distanceToSqr(x, y, z) <= RADIUS * RADIUS) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound != null && SWEEP_SOUND.equals(sound.getLocation())
                && nearWeaponHolder(sound.getX(), sound.getY(), sound.getZ())) {
            event.setSound(null);
        }
    }
}
