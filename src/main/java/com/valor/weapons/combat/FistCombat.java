package com.valor.weapons.combat;

import com.valor.weapons.Config;
import com.valor.weapons.ValorWeapons;
import com.valor.weapons.entity.LightningStrikeEntity;
import com.valor.weapons.entity.ThunderBoltProjectile;
import com.valor.weapons.item.ThunderLordFistItem;
import com.valor.weapons.network.ModNetwork;
import com.valor.weapons.network.PlayAnimationMessage;
import com.valor.weapons.util.ValorDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side swing handling for Thunder Lord's Fist.
 *
 * Every swing fires ONE homing bolt in the look direction, unless that swing connected with a melee hit
 * (Better Combat -> ThunderLordFistItem#onLeftClickEntity), in which case only the melee hit happens.
 *
 * Timing is modelled on Better Combat's own rhythm:
 *  - a new swing can start once the attack cooldown (20 / attack speed ticks) has passed since the previous one,
 *    which is what Better Combat does while the attack key is held;
 *  - the punch lands after the wind-up (Config.SWING_DELAY_FRACTION of the cooldown). The bolt is released then,
 *    and that is also the window in which a melee hit can still cancel it.
 * Bolts always deal full damage; the cooldown gate above already enforces the attack speed.
 */
@Mod.EventBusSubscriber(modid = ValorWeapons.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FistCombat {
    /** Played on the player when the Lightning Strike comes from Thunderstep (Better Combat attack animation #2). */
    private static final ResourceLocation THUNDERSTEP_STRIKE_ANIMATION =
            new ResourceLocation(ValorWeapons.MODID, "thunderlord_projectile_2");

    private enum SwingKind { NORMAL, SEVENTH, EMPOWERED }

    /** Normal attacks before the skill: the 7th attack unleashes the lightning. */
    public static final int HITS_BEFORE_SKILL = 6;
    /** Lightning skill layout: strikes in a straight line along the look direction. */
    private static final int STRIKE_COUNT = 3;
    /** Strikes land 3, 6 and 9 blocks in front of the player (the 4x4 squares overlap by 1 block). */
    private static final double FIRST_STRIKE_DISTANCE = 4.0D;
    /** Distance between strike centres. */
    private static final double STRIKE_SPACING = 4.0D;
    /** Ticks before the first strike, and between strikes. */
    /** After Thunderstep, the next attack within this many ticks is a Lightning Strike. */
    private static final int EMPOWER_TICKS = 5 * 20;
    private static final int FIRST_STRIKE_DELAY = 2;
    private static final int STRIKE_INTERVAL = 5;

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final class State {
        long pendingTick = -1;
        int pendingWait = 0;
        long lastAttackTick = -1000; // start tick of the previous swing
        long lastMeleeTick = -1000;
        long lastBlockClickTick = -1000;
        boolean meleeSkillSwing = false;
        long empoweredUntil = -1; // Thunderstep: the next attack before this tick is a Lightning Strike
        int swingCount = 0;
    }

    private FistCombat() {}

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    private static double cooldownTicks(ServerPlayer player) {
        return 20.0D / Math.max(0.1D, player.getAttributeValue(Attributes.ATTACK_SPEED));
    }

    /** Ticks between the swing starting and the punch landing (+1 tick margin to catch a late melee hit). */
    private static int swingWaitTicks(ServerPlayer player) {
        int upswing = (int) Math.round(cooldownTicks(player) * Config.SWING_DELAY_FRACTION.get());
        return Mth.clamp(upswing + 1, 1, 20);
    }

    /** Called every tick the client reports the attack key held with the fist. */
    public static void requestBolt(ServerPlayer player) {
        if (!(player.getMainHandItem().getItem() instanceof ThunderLordFistItem) || player.isSpectator()) {
            return;
        }
        State s = state(player);
        long now = player.level().getGameTime();
        if (s.pendingTick >= 0 || now == s.lastBlockClickTick) {
            return; // already waiting on this swing or clicking a block
        }
        // One tick of slack so rounding never makes us skip a swing Better Combat does perform.
        if (now - s.lastAttackTick < cooldownTicks(player) - 1.0D) {
            return;
        }
        s.pendingTick = now;
        s.pendingWait = swingWaitTicks(player);
    }

    /**
     * Called by the item for every target a melee swing hits (Better Combat sweeps several targets in one tick).
     * The swing is only counted once.
     *
     * @return true if this is the skill swing: the lightning is cast and the item must NOT deal its normal damage.
     */
    public static boolean beginMelee(ServerPlayer player) {
        State s = state(player);
        long now = player.level().getGameTime();
        if (s.lastMeleeTick == now) {
            return s.meleeSkillSwing; // another target of the same swing
        }
        s.lastMeleeTick = now;
        // Keep the cadence anchored to when the swing STARTED, not when it landed.
        s.lastAttackTick = s.pendingTick >= 0 ? s.pendingTick : now - (swingWaitTicks(player) - 1);
        s.pendingTick = -1;

        SwingKind kind = countSwing(s, now);
        s.meleeSkillSwing = kind != SwingKind.NORMAL;
        if (s.meleeSkillSwing) {
            castLightning(player, kind == SwingKind.EMPOWERED);
        } else {
            float damage = ValorDamage.thunderFistDamage(player);
            if (damage > 0.0F) {
                fireBolt(player, damage);
            }
        }
        return s.meleeSkillSwing;
    }

    /**
     * @return true when this swing must cast the Lightning Strike: it is the 7th swing (counter resets),
     *         or the player earned a Lightning Strike from Thunderstep (consumed here).
     */
    private static SwingKind countSwing(State s, long now) {
        boolean seventh = ++s.swingCount > HITS_BEFORE_SKILL;
        if (seventh) {
            s.swingCount = 0;
        }
        boolean empowered = s.empoweredUntil > now;
        if (empowered) {
            s.empoweredUntil = -1;
        }
        return empowered ? SwingKind.EMPOWERED : seventh ? SwingKind.SEVENTH : SwingKind.NORMAL;
    }

    /** Thunderstep ended: the player's next attack becomes a Lightning Strike. */
    public static void grantLightningStrike(ServerPlayer player) {
        state(player).empoweredUntil = player.level().getGameTime() + EMPOWER_TICKS;
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

        if (s.lastMeleeTick >= swingTick - 1) {
            return; // this swing hit something in melee
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ThunderLordFistItem)) {
            return;
        }

        s.lastAttackTick = swingTick;

        SwingKind kind = countSwing(s, now);
        if (kind != SwingKind.NORMAL) {
            castLightning(player, kind == SwingKind.EMPOWERED); // lightning instead of the bolt
            return;
        }

        float damage = ValorDamage.thunderFistDamage(player);
        if (damage > 0.0F) {
            fireBolt(player, damage);
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            State s = state(player);
            s.pendingTick = -1;
            s.lastBlockClickTick = player.level().getGameTime();
        }
    }

    /** 3 lightning strikes in a straight line along the (horizontal) look direction, one after another. */
    public static void castLightning(ServerPlayer player) {
        castLightning(player, false);
    }

    /**
     * @param fromThunderstep true when the strike was earned by Thunderstep: the caster also plays attack animation #2
     */
    public static void castLightning(ServerPlayer player, boolean fromThunderstep) {
        ServerLevel level = player.serverLevel();
        Vec3 dir = Vec3.directionFromRotation(0.0F, player.getYRot()); // horizontal, unit length
        float damage = ValorDamage.skillDamage(player);
        float heal = ValorDamage.skillHeal(player);
        if (damage <= 0.0F) {
            return;
        }
        LightningStrikeEntity.HealToken healToken = new LightningStrikeEntity.HealToken(); // one heal for the whole volley
        for (int i = 0; i < STRIKE_COUNT; i++) {
            double distance = FIRST_STRIKE_DISTANCE + i * STRIKE_SPACING;
            double x = player.getX() + dir.x * distance;
            double z = player.getZ() + dir.z * distance;
            double y = groundY(level, x, player.getY(), z);
            int delay = FIRST_STRIKE_DELAY + i * STRIKE_INTERVAL;
            level.addFreshEntity(new LightningStrikeEntity(level, player, x, y, z, dir.x, dir.z, damage, heal, delay, healToken));
        }

        if (fromThunderstep) {
            ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    new PlayAnimationMessage(player.getId(), THUNDERSTEP_STRIKE_ANIMATION));
        }
    }

    /** Top of the first solid block found, searching down from 2 blocks above the player's height (up to 14 blocks). */
    private static double groundY(ServerLevel level, double x, double playerY, double z) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, playerY + 2.0D, z).mutable();
        for (int i = 0; i < 14; i++) {
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty()) {
                return pos.getY() + 1.0D;
            }
            pos.move(Direction.DOWN);
        }
        return playerY;
    }

    private static void fireBolt(ServerPlayer player, float damage) {
        ServerLevel level = player.serverLevel();
        ThunderBoltProjectile bolt = new ThunderBoltProjectile(level, player, damage);

        Vec3 look = player.getLookAngle();
        Vec3 start = player.getEyePosition().add(look.scale(0.8D)).subtract(0.0D, 0.25D, 0.0D);
        bolt.setPos(start.x, start.y, start.z);
        bolt.launch(look.scale(Config.PROJECTILE_SPEED.get()));

        level.addFreshEntity(bolt);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.5F, 1.8F);
    }
}
