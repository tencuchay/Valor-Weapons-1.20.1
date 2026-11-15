package com.valor.weapons.combat;

import com.valor.weapons.Config;
import com.valor.weapons.ValorWeapons;
import com.valor.weapons.entity.SunBoltProjectile;
import com.valor.weapons.item.SunBowItem;
import com.valor.weapons.util.SunBowDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side swing handling for Sun's Bow. Same rhythm as Thunder Lord's Fist:
 *  - the client reports "attack key held with the bow" every tick (FistSwingMessage);
 *  - a swing can start once the attack cooldown (20 / attack speed ticks) has passed;
 *  - the bolt is released after the wind-up (Config.SUN_BOW_SWING_DELAY_FRACTION of the cooldown),
 *    unless a Better Combat hit already released it for that swing (SunBowItem#onLeftClickEntity).
 * Exactly one bolt per swing, always full damage.
 */
@Mod.EventBusSubscriber(modid = ValorWeapons.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SunBowCombat {
    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final class State {
        long pendingTick = -1;
        int pendingWait = 0;
        long lastAttackTick = -1000; // start tick of the previous swing
        long lastHitTick = -1000;
        long lastBlockClickTick = -1000;
    }

    private SunBowCombat() {}

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    private static double cooldownTicks(ServerPlayer player) {
        return 20.0D / Math.max(0.1D, player.getAttributeValue(Attributes.ATTACK_SPEED));
    }

    private static int swingWaitTicks(ServerPlayer player) {
        int upswing = (int) Math.round(cooldownTicks(player) * Config.SUN_BOW_SWING_DELAY_FRACTION.get());
        return Mth.clamp(upswing + 1, 1, 20);
    }

    /** Called every tick the client reports the attack key held with the bow. */
    public static void requestShot(ServerPlayer player) {
        if (!(player.getMainHandItem().getItem() instanceof SunBowItem) || player.isSpectator()) {
            return;
        }
        State s = state(player);
        long now = player.level().getGameTime();
        if (s.pendingTick >= 0 || now == s.lastBlockClickTick) {
            return;
        }
        if (now - s.lastAttackTick < cooldownTicks(player) - 1.0D) {
            return; // one tick of slack so rounding never skips a swing Better Combat performs
        }
        s.pendingTick = now;
        s.pendingWait = swingWaitTicks(player);
    }

    /** Called by the item for every target Better Combat sweeps; the swing is only handled once. */
    public static void onSwingHit(ServerPlayer player) {
        State s = state(player);
        long now = player.level().getGameTime();
        if (s.lastHitTick == now) {
            return; // another target of the same swing
        }
        s.lastHitTick = now;
        s.lastAttackTick = s.pendingTick >= 0 ? s.pendingTick : now - (swingWaitTicks(player) - 1);
        s.pendingTick = -1;
        shoot(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        State s = STATES.get(player.getUUID());
        if (s == null || s.pendingTick < 0) {
            return;
        }
        long now = player.level().getGameTime();
        if (now - s.pendingTick < s.pendingWait) {
            return;
        }

        long swingTick = s.pendingTick;
        s.pendingTick = -1;
        if (s.lastHitTick >= swingTick - 1) {
            return; // Better Combat already released the bolt for this swing
        }
        if (!(player.getMainHandItem().getItem() instanceof SunBowItem)) {
            return;
        }
        s.lastAttackTick = swingTick;
        shoot(player);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            State s = state(player);
            s.pendingTick = -1;
            s.lastBlockClickTick = player.level().getGameTime();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    private static void shoot(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof SunBowItem)) {
            return;
        }
        float damage = SunBowDamage.projectileDamage(player);
        if (damage <= 0.0F) {
            return;
        }
        ServerLevel level = player.serverLevel();
        SunBoltProjectile bolt = new SunBoltProjectile(level, player, damage);

        Vec3 look = player.getLookAngle();
        Vec3 start = player.getEyePosition().add(look.scale(0.8D)).subtract(0.0D, 0.25D, 0.0D);
        bolt.setPos(start.x, start.y, start.z);
        bolt.launch(look.scale(Config.SUN_BOW_PROJECTILE_SPEED.get()));
        level.addFreshEntity(bolt);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.5F, 1.5F);
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
    }
}
